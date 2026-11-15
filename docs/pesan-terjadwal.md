# Pesan Terjadwal â€” WaTrobos 1.0.3

Buka tombol **Pesan Terjadwal** di dashboard, aktifkan penjadwal, lalu tambah jadwal.
Pilih kontak HP/chat terbaru atau masukkan nomor dengan kode negara, isi pesan,
lalu pilih tanggal, jam, dan detik. Jadwal menunggu dapat diubah atau dibatalkan.

Versi awal ini mendukung pesan teks sekali kirim ke satu nomor, pada WhatsApp
Messenger resmi 2.26.43.74 dan 2.26.43.76. Belum mendukung grup, lampiran, pengulangan, Business,
atau beberapa akun. Buka WhatsApp setelah menyimpan/mengubah jadwal dan setelah
HP dimulai ulang/WhatsApp dihentikan paksa. Penjadwal menggunakan alarm WhatsApp;
Android dapat menunda alarm saat penghematan daya. Memilih detik bukan jaminan
pesan tiba tepat pada detik tersebut.

Status **Diproses WA** berarti pesan keluar ditemukan di database WhatsApp,
bukan konfirmasi diterima penerima. Hasil yang belum pasti ditandai **Perlu dicek**
dan tidak dikirim ulang otomatis. Jadwal terlambat lebih dari 15 menit dilewati.

Antrean disimpan dalam database privat WaTrobos. Provider hanya menerima UID
WaTrobos dan WhatsApp terpasang; operasi edit/aktifkan/hapus hanya untuk WaTrobos.
Pencarian chat dilakukan atas permintaan, hasil kontak WhatsApp tidak disimpan ke
antrean. Pesan dan nomor tidak ditulis ke log diagnostik.

Dasar implementasi: kode penjadwal lokal ASHA RIZKI yang sebelumnya dipakai pada
WaEnhancerX lokal. Adaptasi ke WaTrobos memakai libxposed/YukiHookAPI, bukan
menambahkan runtime Xposed lama. Handler alarm diwarisi dari superclass pada WA
2.26.43.74 dan 2.26.43.76; hook disaring untuk receiver dan aksi penjadwal WaTrobos saja.

## Pemilih chat pada versi 1.0.4

Tab Chat terbaru menampilkan foto profil cache WhatsApp, nama, waktu chat,
dan cuplikan pesan terakhir. Foto yang tidak tersedia memakai inisial; tidak
ada permintaan foto ke server. Pesan media diberi label jenis media.
Pencarian tetap berdasarkan nama atau nomor dan memilih chat hanya mengisi
penerima di editor jadwal. Chat terkunci, tersembunyi, arsip, dan grup tidak
masuk daftar tujuan pribadi ini.

Thumbnail diperkecil menjadi 64 px, dibatasi ukuran per gambar dan total payload.
Preview pesan maksimal 180 karakter. Provider tetap membatasi akses berdasarkan
UID. Preview hanya ada di memori, dihapus saat pemilih ditutup/sumber berganti,
dan kedaluwarsa setelah satu menit; tidak disimpan ke database jadwal.
