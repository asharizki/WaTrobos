# Perbaikan jarak Chat/Grup — 1.0.17

Target: WhatsApp Messenger 2.26.46.82. Perubahan aktif hanya bila baris filter
dinonaktifkan (`filterseen=false`). Pengaturan pengguna tidak diubah.

## Penyebab dan perubahan

Pada host ini, `conversations_swipe_to_reveal_container` dan
`conversations_swipe_to_reveal_header_combo` tetap mengambil tinggi setelah isi
filter disembunyikan. Kedua wrapper filter sekarang ikut di-collapse pada attach,
dijaga tetap GONE pada perubahan visibility native, dan wrapper FrameLayout
dipastikan memiliki measured height nol. Pencarian, Diarsipkan, dan daftar chat
bukan target. Resolver versi lama tetap memakai jalur sebelumnya.

## Hasil uji perangkat

Xiaomi 13T, Android 16, Vector, WA 2.26.46.82, WaTrobos 1.0.17 (10022).
26 unit test lulus. Sertifikat APK sama dengan build sebelumnya.
Di Chat dan Grup, jarak bawah pencarian ke Diarsipkan berubah dari 216 menjadi
48 piksel: ruang tambahan 168 piksel hilang. Posisi pencarian dan tinggi baris
Diarsipkan/chat tetap. Perpindahan Chat -> Grup -> Chat mempertahankan proses,
tanpa dialog error atau exception modul yang teramati. Scroll dan kembali ke
atas tidak memunculkan wrapper kosong lagi. Ukuran piksel berlaku untuk perangkat uji.
Pengujian tidak mengirim pesan/status atau mengubah data percakapan.

Saat instalasi, Vector sempat menyimpan lokasi APK sebelumnya yang sudah tidak ada.
Disable/enable hanya modul WaTrobos melalui CLI Vector, lalu mulai ulang WhatsApp,
memuat APK baru; cakupan tetap com.whatsapp/user 0. Tidak memerlukan reboot.

## Pemulihan

Nonaktifkan WaTrobos di Vector dan mulai ulang WhatsApp bila terjadi masalah.
Backup APK/source 1.0.16 disimpan pada folder kerja lokal spacing-fix-1.0.17.
Jangan menghapus data WhatsApp. Android mungkin menolak downgrade versionCode.
