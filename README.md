<div align="center">
<img src="docs/logo.png" width="132" alt="Shizuku Pro">

# Shizuku Pro

Fork lengkap [Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next), dikelola di repositori seandyadryan.

[Unduh APK](https://github.com/seandyadryan/GPS-changer-Shizuku-Pro/releases) · [Build manual](https://github.com/seandyadryan/GPS-changer-Shizuku-Pro/actions/workflows/build.yml) · [Laporkan bug](https://github.com/seandyadryan/GPS-changer-Shizuku-Pro/issues)
</div>

Aplikasi Android untuk memberikan akses API sistem kepada aplikasi yang mendukung Shizuku melalui ADB atau root. Seluruh modul dan riwayat kode upstream dipertahankan, termasuk Home, Apps, Modules, Labs, Settings, shell, file manager, installer, pengelolaan izin, dan metode startup upstream.

Sesuai pilihan cakupan proyek, ini adalah fork manajer Shizuku lengkap. Nama repositori `GPS-changer-Shizuku-Pro` tidak menambahkan fitur pengubah koordinat GPS.

## Persyaratan

- Android 7.0 atau lebih baru.
- Wireless debugging memerlukan Android 11 atau lebih baru.
- Startup USB memerlukan komputer dengan ADB; startup root memerlukan perangkat root.
- Fitur Samsung system UID, no-Wi-Fi, modul, dan fitur eksperimental bergantung perangkat/ROM.

Package ID tetap `moe.shizuku.privileged.api` agar kompatibel dengan klien Shizuku. Karena tanda tangan fork ini berbeda, hapus instalasi Shizuku lain sebelum memasang APK ini. Hentikan server lama atau reboot terlebih dahulu.

## Build dan runner hemat

Buka **Actions → Build Shizuku Pro → Run workflow**. Aktifkan **Publish the signed APK to GitHub Releases** untuk menerbitkan APK. Push biasa, pull request, dan jadwal tidak menjalankan runner.

Workflow memakai satu runner Ubuntu, Java 21, cache Gradle, unit test, lint kompatibilitas Android, dan satu build release. APK memakai kunci penandatanganan tetap dari Actions secrets. Artefak disimpan tujuh hari; APK yang diterbitkan tersedia di Releases.

Instruksi lokal, signing, dan pemutakhiran upstream: [docs/BUILDING.md](docs/BUILDING.md).

## Lisensi dan kredit

[Apache License 2.0](LICENSE). Fork ini berasal dari [rushiranpise/Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next), yang berasal dari [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) dan [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku).

Dokumentasi, tangkapan layar, penjelasan fitur, dan kredit lengkap upstream dipertahankan di [docs/UPSTREAM_README.md](docs/UPSTREAM_README.md). Screenshot di dokumentasi upstream menggambarkan versi upstream.
