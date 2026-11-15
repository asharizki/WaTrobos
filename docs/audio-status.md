# Audio lokal menjadi status suara

WaTrobos 1.0.6 mendukung pemilih rentang dan pembagian audio otomatis pada
WhatsApp Messenger 2.26.43.74 dan 2.26.43.76. Sakelar ada di WaTrobos > Media >
**Audio lokal ke status suara**, aktif secara bawaan. Mulai ulang WhatsApp
setelah mengubah sakelar.

## Pemakaian

1. Buka WhatsApp > Pembaruan > ikon pensil > Suara.
2. Ketuk tombol musik jingga, lalu pilih berkas audio dari HP.
3. Pilih salah satu mode:
   - **Pilih bagian**: isi waktu mulai dan selesai dalam detik atau gunakan
     penanda geser. Durasi pilihan 1–30 detik. Tombol Putar bagian memutar
     rentang yang dipilih, bukan seluruh berkas.
   - **Bagi otomatis seluruh audio**: semua audio dibagi berurutan menjadi
     potongan maksimal 30 detik. Contoh 90 detik menjadi 0–30, 30–60, 60–90.
     Pilih bagian di daftar untuk mendengarkan sebelum disiapkan.
4. Ketuk Siapkan bagian atau Siapkan N status.
5. Pada mode otomatis, label N status siap dapat diketuk untuk memeriksa
   tiap potongan melalui pratinjau native WhatsApp.
6. Periksa audiens dan warna latar, lalu tekan tombol kirim WhatsApp.
   Pada mode otomatis, satu tindakan kirim mengantrekan seluruh bagian
   berurutan menggunakan alur pengiriman native WhatsApp.

Impor dan tombol Siapkan tidak menerbitkan status. Pengiriman hanya dimulai
setelah pengguna menekan kirim WhatsApp dan melewati pemeriksaan audiens native.
Memilih bagian lain untuk pratinjau tidak mengubah urutan kirim seluruh bagian.

## Batas dan pengelolaan berkas

- Durasi sumber 1 detik sampai 10 menit, ukuran maksimal 50 MB.
- Maksimal 20 bagian otomatis. Jika sisa akhir kurang dari satu detik, batas
  dua bagian terakhir digeser agar sisanya satu detik; tidak ada interval
  sumber yang dibuang atau digandakan.
- Format input mengikuti decoder audio Android pada perangkat.
- Hasil Ogg Opus mono 48 kHz, 64 kbps, waveform dari amplitudo audio.
- Decoder mencari posisi sumber, membuang sampel sebelum titik mulai dan
  membatasi sampel pada titik akhir. Converter chat lama tidak diubah.
- Input sementara dihapus setelah konversi atau editor dibatalkan. Potongan
  disimpan pada direktori media WhatsApp. Potongan rencana lama dihapus saat
  diganti atau halaman ditutup tanpa upaya pengiriman.
- Adapter otomatis dibatasi tepat Messenger 2.26.43.74 dan 2.26.43.76. Pembaruan struktur
  WhatsApp memerlukan pemeriksaan ulang. Mode manual tetap memakai kontrak
  controller preview yang tersedia.
- Rencana otomatis hanya hidup pada sesi composer saat ini, tidak dipulihkan
  setelah proses mati. Antrean WhatsApp menangani upload setelah dikirim.
- Jika sebagian upaya antrean gagal, aplikasi tidak mengulang semua bagian
  secara otomatis untuk menghindari duplikasi. Periksa daftar status dahulu.

## Integrasi antrean

Adapter menangkap template dari jalur kirim status suara native. Setiap bagian
memiliki media, waveform dan durasi sendiri. Penerima, pilihan audiens, warna,
penyebutan dan konteks pengiriman diambil dari tindakan kirim pengguna.
Satu kelompok native hanya memuat salinan satu berkas untuk para penerimanya;
masing-masing potongan dibuat sebagai kelompok tersendiri, lalu diantrekan
berurutan. Semua kelompok dibangun dan divalidasi sebelum upaya antrean pertama.
Pemeriksaan persiapan juga membangun kelompok native di memori tanpa memanggil
fungsi enqueue atau menerbitkan status.

## Verifikasi

Diuji pada Xiaomi 13T, Android 16, Vector 2.2 (3080), WhatsApp 2.26.43.74:

- Empat pengujian rentang audio dan sembilan pengujian proyek sebelumnya lulus.
- Editor manual dan otomatis, pratinjau sumber, daftar bagian serta pratinjau
  native dapat dibuka di perangkat.
- Audio WAV 90 detik menghasilkan tiga Opus masing-masing tepat 30 detik.
  Nada 440, 660 dan 880 Hz pada interval sumber yang berbeda ditemukan pada
  hasil bagian 1, 2 dan 3, memastikan offset pemotongan benar.
- Pilihan manual 40–50 detik menghasilkan tepat 10 detik dengan nada 660 Hz,
  sesuai interval tengah sumber.
- Pemeriksaan persiapan berhasil membuat tiga kelompok status native di memori.
- Mengganti rencana otomatis dengan pilihan manual menghapus tiga berkas lama.
- Tidak menerbitkan status uji. Pengiriman sampai server dan penerimaan di
  perangkat lain belum diuji; jangan menyamakan pemeriksaan persiapan dengan
  konfirmasi bahwa status sudah berhasil terunggah.

Implementasi menggunakan converter Opus basis WaTrobos dan alur status native;
tidak memuat binary fitur Pro dari X. Seluruh 67 komponen sebelumnya tetap ada,
dan registri tetap berisi 68 komponen termasuk Audio Lokal ke Status Suara.
