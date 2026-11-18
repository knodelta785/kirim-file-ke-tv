# Kirim file ke TV — by Ongarand

Install APK yang sama di HP dan Android TV (satu WiFi).
TV menampilkan IP, HP memasukkan IP itu lalu memilih file.

## Push ke GitHub dari Termux
```
pkg install git
cd KirimFileKeTV
git init && git add . && git commit -m "first commit"
git branch -M main
git remote add origin https://github.com/USERNAME/REPO.git
git push -u origin main
```
(password = Personal Access Token GitHub)

Setelah push: tab **Actions** → **Build APK** → unduh artifact `KirimFileKeTV-apk`.
