# Pemberitahuan asal dan modifikasi

WaTrobos adalah karya turunan/fork dari Dev4Mod/WaEnhancer, GPL-3.0.
Baseline upstream: 36c22aeda030327704b5ee0b099f060fd42c1120.

Modifikasi WaTrobos oleh ASHA RIZKI, 5 November 2026: identitas aplikasi,
dashboard, ikon vector, halaman Tentang/kredit, paket dan signing rilis, serta
konfigurasi tautan pembaruan fork. Perbaikan kompatibilitas lokal sebelumnya
mencakup resolver HomeLegacyBottomNavigationView dan aturan versi tepat
WhatsApp Messenger 2.26.42.74.

Pemberitahuan hak cipta dan lisensi pihak asal dalam source dan submodule
dipertahankan. Nama ASHA RIZKI mengidentifikasi pengelola/modifikasi WaTrobos;
tidak menggantikan atribusi penulis kode upstream.

Pembaruan 1.0.1, 5 November 2026: kompatibilitas Messenger 2.26.43.74,
resolver judul tab berdasarkan kontrak String(int), pengurutan pemasangan
hook judul sebelum penyisipan tab, penjagaan batas indeks, dan penelusuran
superclass untuk resolver menu pencarian beranda. Tidak menyertakan source
atau binary WhatsApp dalam distribusi source WaTrobos.

Pembaruan 1.0.2, 5 November 2026: menyederhanakan bagian Kontributor aplikasi
menjadi ASHA RIZKI dengan tautan profil Telegram https://t.me/ashrizki. Daftar
kontributor upstream dan pemberitahuan hak cipta tetap disimpan dalam source.

Pembaruan 1.0.3: Pesan Terjadwal oleh ASHA RIZKI, diadaptasi dari pekerjaan lokal
sebelumnya. Antrean privat, pemilih kontak, halaman jadwal, dan adapter pengiriman
khusus WhatsApp 2.26.43.74. Lihat docs/pesan-terjadwal.md.

Pembaruan 1.0.4: pemilih chat terbaru dengan foto cache, nama, preview pesan,
waktu, dan placeholder inisial. Data preview dibatasi ukuran dan masa hidup.

Pembaruan 1.0.5: impor audio lokal menjadi draf status suara oleh ASHA RIZKI.
Menambahkan pemilih berkas, konversi Opus mono dengan batas 30 detik, waveform,
dan integrasi pratinjau native WhatsApp 2.26.43.74. Lihat docs/audio-status.md.
Converter chat lama dan source/library native tetap dipertahankan.

Pembaruan 1.0.6, 6 November 2026: editor rentang audio, pratinjau sumber,
pembagian otomatis berurutan dan adapter kelompok antrean status khusus WhatsApp
2.26.43.74 oleh ASHA RIZKI. Lihat docs/audio-status.md untuk batas dan verifikasi.

Pembaruan 1.0.7, 6 November 2026: Pemotong video status 30/60/90 detik oleh
ASHA RIZKI; editor rentang, ekspor berurutan dan penyerahan batch ke pratinjau
status WhatsApp. Menggunakan AndroidX Media3 1.8.0 (Android Open Source Project,
Apache-2.0). Lihat docs/video-status.md untuk batas dan verifikasi.

Pembaruan 1.0.8, 6 November 2026: pemilih video memakai galeri thumbnail
Android (Photo Picker/ACTION_PICK) oleh ASHA RIZKI. Alur pemotongan tetap sama.
