package com.ongarand.kirimfile

import android.app.Activity
import android.app.UiModeManager
import android.content.ContentValues
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.*
import java.io.*
import java.net.*

class MainActivity : Activity() {
    private val port = 8988
    private val pickCode = 1
    private lateinit var status: TextView
    private lateinit var ipInput: EditText
    private var server: ServerSocket? = null

    private val isTv: Boolean
        get() = (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType ==
                Configuration.UI_MODE_TYPE_TELEVISION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = isTv
        val big = if (tv) 1.6f else 1f
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 120, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "Kirim file ke TV"; textSize = 28f * big
            paint.isFakeBoldText = true; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "by Ongarand"; textSize = 16f * big; gravity = Gravity.CENTER
            setPadding(0, 8, 0, 48)
        })
        status = TextView(this).apply { textSize = 18f * big; gravity = Gravity.CENTER }

        if (tv) {
            root.addView(TextView(this).apply {
                text = "Masukkan IP ini di HP:\n${localIp()}"
                textSize = 26f * big; gravity = Gravity.CENTER
            })
            status.text = "\nMenunggu file..."
            startServer()
        } else {
            ipInput = EditText(this).apply {
                hint = "IP TV, mis. 192.168.1.20"
                setText(getPreferences(MODE_PRIVATE).getString("ip", ""))
            }
            root.addView(ipInput)
            root.addView(Button(this).apply {
                text = "Pilih file & kirim"
                setOnClickListener {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                    }, pickCode)
                }
            })
            status.text = "\nBuka aplikasi ini di TV untuk melihat IP-nya."
        }
        root.addView(status)
        setContentView(root)
    }

    private fun say(msg: String) = runOnUiThread { status.text = "\n$msg" }

    private fun localIp(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress ?: "tidak ada (cek WiFi)"
    } catch (e: Exception) { "tidak diketahui" }

    // ---------- TV: terima ----------
    private fun startServer() = Thread {
        try {
            server = ServerSocket(port)
            while (true) {
                val c = server!!.accept()
                Thread { receive(c) }.start()
            }
        } catch (e: Exception) { say("Server berhenti: ${e.message}") }
    }.start()

    private fun receive(client: Socket) {
        try {
            client.use { s ->
                val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                val name = File(input.readUTF()).name
                val size = input.readLong()
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/KirimFileKeTV")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                contentResolver.openOutputStream(uri)!!.use { out ->
                    val buf = ByteArray(64 * 1024)
                    var left = size
                    var done = 0L
                    while (left > 0) {
                        val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n < 0) break
                        out.write(buf, 0, n); left -= n; done += n
                        say("Menerima $name\n${done * 100 / size}%")
                    }
                }
                say("Selesai: $name\nTersimpan di Download/KirimFileKeTV")
            }
        } catch (e: Exception) { say("Gagal menerima: ${e.message}") }
    }

    // ---------- HP: kirim ----------
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != pickCode || resultCode != RESULT_OK) return
        val host = ipInput.text.toString().trim()
        if (host.isEmpty()) { say("Isi IP TV dulu"); return }
        getPreferences(MODE_PRIVATE).edit().putString("ip", host).apply()
        Thread { send(host, uri) }.start()
    }

    private fun send(host: String, uri: Uri) {
        try {
            var name = "file"; var size = 0L
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    size = c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE))
                }
            }
            say("Menghubungi TV...")
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), 5000)
                val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                out.writeUTF(name); out.writeLong(size)
                contentResolver.openInputStream(uri)!!.use { inp ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (size > 0) say("Mengirim $name\n${done * 100 / size}%")
                    }
                }
                out.flush()
            }
            say("Terkirim: $name")
        } catch (e: Exception) { say("Gagal kirim: ${e.message}") }
    }

    override fun onDestroy() { super.onDestroy(); server?.close() }
}
