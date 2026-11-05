<div align="center">
<img src="docs/nfx-logo.svg" width="132" alt="NFX Location PRO">

# NFX Location PRO

Fork lengkap [Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next), dikelola di repositori seandyadryan.

[Unduh APK](https://github.com/seandyadryan/GPS-changer-Shizuku-Pro/releases) · [Build manual](https://github.com/seandyadryan/GPS-changer-Shizuku-Pro/actions/workflows/build.yml) · [Laporkan bug](https://github.com/seandyadryan/GPS-changer-Shizuku-Pro/issues)
</div>

Aplikasi Android untuk memberikan akses API sistem kepada aplikasi yang mendukung Shizuku melalui ADB atau root. Seluruh modul dan riwayat kode upstream dipertahankan, termasuk Home, Apps, Modules, Labs, Settings, shell, file manager, installer, pengelolaan izin, dan metode startup upstream.

Pengguna masuk dengan nomor HP dan kata sandi. Admin masuk sebagai `admin` untuk membuat akun, menambah hari langganan, dan meninjau permintaan pindah perangkat. Server mengikat akun ke perangkat pertama yang berhasil masuk. Pengguna dapat membuka **Settings → Account** untuk melihat akun dan keluar.

## Persyaratan

- Android 7.0 atau lebih baru.
- Wireless debugging memerlukan Android 11 atau lebih baru.
- Startup USB memerlukan komputer dengan ADB; startup root memerlukan perangkat root.
- Fitur Samsung system UID, no-Wi-Fi, modul, dan fitur eksperimental bergantung perangkat/ROM.

Package ID tetap `moe.shizuku.privileged.api` agar kompatibel dengan klien Shizuku. Karena tanda tangan fork ini berbeda, hapus instalasi Shizuku lain sebelum memasang APK ini. Hentikan server lama atau reboot terlebih dahulu.

## Build dan runner hemat

Buka **Actions → Build NFX Location PRO → Run workflow**. Aktifkan **Publish the signed APK to GitHub Releases** untuk menerbitkan APK. Workflow berjalan secara manual.

Workflow memakai satu runner Ubuntu, Java 21, cache Gradle, unit test, lint kompatibilitas Android, dan satu build release. APK memakai kunci penandatanganan tetap dari Actions secrets. Artefak disimpan tujuh hari; APK yang diterbitkan tersedia di Releases.

Instruksi lokal, signing, dan pemutakhiran upstream: [docs/BUILDING.md](docs/BUILDING.md).

Petunjuk database, deployment API, dan pemulihan akses admin tersedia di [backend/README.md](backend/README.md).

## Lisensi dan kredit

[Apache License 2.0](LICENSE). Fork ini berasal dari [rushiranpise/Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next), yang berasal dari [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) dan [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku).

Dokumentasi, tangkapan layar, penjelasan fitur, dan kredit lengkap upstream dipertahankan di [docs/UPSTREAM_README.md](docs/UPSTREAM_README.md). Screenshot di dokumentasi upstream menggambarkan versi upstream.
