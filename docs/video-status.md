# Pemotong video status — WaTrobos 1.0.8

## Membuka fitur

- WaTrobos > Media > Pemotong video status > Buka pemotong video status.
- Atau menu tiga titik beranda WhatsApp > Potong video status.
- Sakelar Pemotong video status aktif secara bawaan. Mulai ulang WhatsApp
  setelah mengubah sakelar agar menu mengikuti pengaturan.

## Pemakaian

1. Ketuk Pilih video dari galeri, lalu pilih thumbnail video dari galeri Android.
2. Tentukan durasi maksimal tiap bagian: 30, 60, atau 90 detik.
3. Pilih mode:
   - Pilih bagian: tentukan waktu mulai dan selesai dalam detik, atau gunakan
     dua penanda geser. Panjang pilihan 1 detik sampai batas bagian terpilih.
   - Bagi otomatis seluruh video: buat rangkaian yang menutupi seluruh sumber,
     dengan urutan mulai dari awal sampai akhir.
4. Putar pilihan sumber untuk memeriksa rentang, lalu Siapkan potongan video.
5. Gunakan tombol Putar bagian untuk memeriksa tiap hasil.
6. Ketuk Lanjut ke status WhatsApp. Semua berkas dipilih sekaligus dalam urutan
   bagian pada pratinjau status native. Periksa audiens dan tekan Kirim di
   WhatsApp untuk menerbitkan batch.

Contoh sumber 185 detik:

| Durasi bagian | Jumlah | Rentang |
| --- | --- | --- |
| 30 detik | 7 | 0–30, 30–60, 60–90, 90–120, 120–150, 150–180, 180–185 |
| 60 detik | 4 | 0–60, 60–120, 120–180, 180–185 |
| 90 detik | 3 | 0–90, 90–180, 180–185 |

Mengubah durasi, mode, atau rentang membatalkan hasil persiapan lama. Siapkan
ulang sebelum meneruskan ke WhatsApp. Pemilihan video dan ekspor tidak
menerbitkan status; tombol kirim tetap berada pada pratinjau native WhatsApp.

## Batas dan format

- Sumber 1 detik sampai 10 menit, maksimal 500 MB. Maksimal 20 bagian otomatis.
- Jika sisa akhir kurang dari satu detik, batas dua bagian terakhir digeser
  sehingga bagian terakhir berdurasi satu detik; interval sumber tetap utuh.
- Keluaran MP4 H.264/AAC, tinggi maksimal 720 piksel dengan rasio aspek sumber.
- Video dienkode ulang untuk memotong pada batas frame, termasuk ketika
  titik mulai bukan keyframe. Metadata durasi MP4 bisa berselisih beberapa
  milidetik karena frame dan paket AAC. WhatsApp dapat menormalisasi batasnya.
- Format input dan keberhasilan encoding mengikuti decoder/encoder perangkat.
- Tetap buka halaman selama pemrosesan. Tombol Batalkan proses membatalkan
  pekerjaan dan membersihkan hasil yang belum diserahkan ke WhatsApp.
- Sumber dibaca dengan izin URI dari pemilih galeri Android lalu disalin ke cache privat.
  Salinan input dihapus ketika diganti atau halaman dihancurkan.
- Hasil yang belum dibagikan dihapus saat diganti/halaman dihancurkan. Hasil
  yang sudah dibuka di WhatsApp dipertahankan agar izin baca tetap berfungsi,
  lalu dibersihkan saat fitur dibuka kembali setelah melewati 24 jam.
- Sesi editor tidak dipulihkan setelah proses mati. Bagian yang telah dibuka
  di WhatsApp tetap tersedia sementara melalui FileProvider dengan izin baca.
- Tujuan integrasi saat ini WhatsApp Messenger com.whatsapp. Tidak mengubah
  batas server atau menjanjikan dukungan semua akun/versi WhatsApp.

## Implementasi

Pemotongan memakai Media3 Transformer 1.8.0, clipping timestamp, encoding video
H.264 dan audio AAC. Lihat [panduan transformasi Android](https://developer.android.com/media/media3/transformer/transformations).
Media3 berlisensi Apache-2.0 dari Android Open Source Project; lisensi fork
WaTrobos dan pemberitahuan upstream tetap disertakan.

Penyerahan memakai ACTION_SEND_MULTIPLE, FileProvider, daftar URI berurutan,
dan tujuan status@broadcast. Proses pemilihan audiens, pratinjau, dan tombol
kirim memakai WhatsApp. Tidak menambahkan hook untuk menonaktifkan pemeriksaan
batas durasi host atau mengirim tanpa tindakan pengguna.

## Verifikasi perangkat

Xiaomi 13T, Android 16, Vector 2.2 (3080), WhatsApp Messenger 2.26.43.74:

- Tiga pengujian rentang video dan 13 pengujian proyek sebelumnya lulus.
- WAV/audio status dan pesan terjadwal tidak diubah oleh fitur video.
- MP4 sintetis 185 detik, H.264/AAC, 10 fps, dengan keyframe setiap 4,7 detik
  dan cap waktu sumber, menghasilkan 7/4/3 bagian untuk mode 30/60/90.
- Seluruh 14 potongan berhasil didekode. Durasi sesuai rentang dengan
  selisih metadata maksimal 0,02 detik pada sumber uji.
- Frame pada 0,5 detik tiap hasil cocok dengan posisi sumber yang diminta,
  memastikan awal potongan kedua dan seterusnya tidak mengulang awal video.
- Pilihan manual 45,5–55,5 detik menghasilkan video sekitar 10 detik,
  dengan frame yang cocok terhadap posisi sumber 46 detik.
- Tiga hasil mode 90 detik dibuka sekaligus pada pratinjau status WhatsApp.
  Timeline menampilkan 1:30; bagian kedua memperlihatkan cap waktu 1:30,
  dan bagian sisa berdurasi 5 detik.
- Menu Potong video status membuka editor. Perubahan durasi menonaktifkan
  tombol lanjut sampai hasil baru disiapkan.
- Tidak menekan Kirim atau menerbitkan status uji. Upload server dan penerimaan
  di perangkat lain belum diuji.

## Pembaruan 1.0.8 — pemilih galeri

Tombol pemilihan video membuka galeri sistem dengan filter video. Android 13
ke atas memakai Photo Picker; perangkat lama memakai pemilih galeri ACTION_PICK.
Tidak memakai ACTION_OPEN_DOCUMENT atau membuka pengelola berkas sebagai fallback.
Jika galeri tidak tersedia, aplikasi meminta pengguna mengaktifkan galeri di HP.
Alur pemotongan dan penyerahan ke WhatsApp tetap memakai implementasi sebelumnya.

Diuji pada Android 16: galeri thumbnail dengan tab Video/Koleksi berhasil
dibuka, video sintetis 6 detik berhasil dipilih dan diimpor, pembatalan
mempertahankan sumber yang ada, dan ekspor satu potongan berhasil. Fallback
galeri pada Android lama belum diuji di perangkat. Tidak menerbitkan status uji.
