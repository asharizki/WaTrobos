# WaTrobos

<p><img src="docs/watrobos-icon.svg" alt="Ikon WaTrobos" width="112"></p>

**WaTrobos** adalah modul Xposed untuk WhatsApp Messenger di Android, dikelola
oleh **asHDEV**. Berbasis [Dev4Mod/WaEnhancer](https://github.com/Dev4Mod/WaEnhancer)
dengan antarmuka bahasa Indonesia.

[Unduh APK dan source](https://github.com/asharizki/WaTrobos/releases/latest)
· [Komunitas Telegram](https://t.me/WaTrobosIndonesia)
· [Riwayat perubahan](CHANGELOG.md)
· [Laporkan masalah](https://github.com/asharizki/WaTrobos/issues)

## Fitur

| Fitur | Keterangan |
| --- | --- |
| Beranda | Tab Chat dan Grup terpisah di WhatsApp. |
| [Status suara](docs/audio-status.md) | Impor audio, pilih rentang, atau bagi otomatis menjadi klip 30 detik. |
| [Pemotong video](docs/video-status.md) | Potong manual atau bagi otomatis menjadi bagian 30, 60, atau 90 detik. |
| [Pesan terjadwal](docs/pesan-terjadwal.md) | Buat dan kelola antrean pesan dari WaTrobos. |
| Pengaturan | Atur fitur beranda, privasi, media, tampilan, dan rekaman. |

## Kompatibilitas

| Seri WhatsApp Messenger | Cakupan |
| --- | --- |
| 2.26.34.xx sampai 2.26.41.xx | Seri dalam daftar kompatibilitas |
| 2.26.42.xx | Build 74 |
| 2.26.43.xx | Build 74 dan 76 |
| 2.26.44.xx | Build 90 |

Pengujian WhatsApp Messenger **2.26.44.90** memakai Xiaomi 13T, Android 16,
dan Vector. Tab Chat/Grup, pembukaan editor video, dan pratinjau tiga klip
audio diperiksa. Penjadwal berhasil dimuat. Pengiriman pesan terjadwal dan
publikasi status belum diuji.

## Instalasi

1. Gunakan HP rooted dengan framework Xposed yang kompatibel. Setup uji memakai Vector.
2. Unduh dan pasang APK WaTrobos dari halaman rilis.
3. Di Vector, aktifkan WaTrobos dan pilih WhatsApp Messenger (com.whatsapp)
   sebagai cakupannya. Nonaktifkan modul WaEnhancer lain di cakupan WhatsApp.
4. Buka WaTrobos untuk mengatur fitur, lalu mulai ulang WhatsApp.

## Build

Gunakan JDK 21, Android SDK Platform 37, NDK 28.2.13676358, dan CMake 3.22.1.

    ./gradlew :app:assembleWhatsappRelease :app:testWhatsappDebugUnitTest

Pada Windows, pakai gradlew.bat. Source native disertakan. Build tanpa kunci
rilis lokal memakai debug signing.

## Pelaporan masalah

Sertakan versi lengkap WaTrobos, WhatsApp, Android, dan framework, beserta
langkah reproduksi. Bersihkan isi chat, nomor telepon, dan data akun dari log
atau tangkapan layar sebelum mengunggahnya.

## Lisensi dan kredit

WaTrobos adalah karya turunan dari [Dev4Mod/WaEnhancer](https://github.com/Dev4Mod/WaEnhancer)
dengan lisensi [GPL-3.0](LICENSE). Lihat [kontributor](CONTRIBUTORS.md),
[pemberitahuan modifikasi](NOTICE.md), dan [dokumentasi upstream](docs/UPSTREAM_README.md).
