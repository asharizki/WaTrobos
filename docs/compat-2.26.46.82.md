# Kompatibilitas WhatsApp 2.26.46.82

WaTrobos 1.0.16 (10021), 26 November 2026. Target Messenger, bukan Business.

## Perubahan berdasarkan DEX pada APK dari perangkat

- Builder: X.7vF.A05; metadata: X.1f6.
- Media: X.7Q2.A00() lalu A0A(File), menggantikan factory satu langkah.
- Durasi: X.8kK.A01(File).
- Grup media: X.9FY; enqueue: X.8iP.A02.
- Audiens: X.8x4.A07(X.4jZ); resolver privasi: X.7PF.A0I.
- Provider pembuat media: X.8i9.A05; pengendali audio: X.9E3.A08.
- Semua kontrak di atas diperiksa statis. Pengiriman memakai template native
  saat pengguna menekan kirim; tidak mengganti audiens atau mematikan pemeriksaan privasi.
- Scheduler tetap memakai DirectReplyService.onHandleIntent(Intent),
  RemoteInput direct_reply_input, dan AlarmBroadcastReceiver. Kontrak ada pada APK baru;
  kesamaan kontrak tidak membuktikan pengiriman end-to-end.

## Migrasi daftar percakapan dan key pesan

- ConversationsFragment tidak lagi mendeklarasikan getter daftar chat.
  Getter dipindah ke ConversationsFragmentKt.A31; BGF bukan getter yang dipakai.
- Semua resolver tab diperiksa sebelum penyisipan tab Grup, supaya kegagalan
  resolusi tidak menyisakan jumlah tab dan penyedia fragment yang tidak sinkron.
- Field pemilik filter sekarang dapat bertipe ConversationsFragmentKt.
- Helper key asli pesan pindah ke X.1BO.A00(X.1D4), bertipe static.
  Kontrak dan isi DEX diperiksa; panggilan reflective static menerima objek pesan.
- Getter versi lama dipertahankan; regresi JVM mencakup migrasi dan ambiguitas getter.

## Batas verifikasi

Uji pengiriman pesan dan publikasi status tidak dilakukan otomatis karena dapat
mengirim konten ke orang lain. Keberhasilan pemasangan/pemuatan dicatat terpisah
pada laporan build lokal. Tidak menyertakan APK WhatsApp, data akun, atau kunci signing.

## Hasil perangkat lokal — 26 November 2026

- APK 1.0.16 (10021) dipasang sebagai pembaruan, tanpa uninstall/clear data.
- Sertifikat signing sama dengan rilis sebelumnya; 23 unit test lulus.
- Xiaomi 13T / Android 16 / Vector / WhatsApp 2.26.46.82.
- Hook berhasil dimuat; tidak ada exception pada log modul proses pengujian terakhir.
- Chat -> Grup -> Chat: proses tetap hidup, tidak ada dialog error; masing-masing
  7 baris terlihat, tidak ada irisan nama yang terlihat antar tab, dan daftar Chat
  tetap sama saat kembali. Nama/isi chat tidak dimasukkan dalam laporan.
- Adapter penjadwal melaporkan ready. Tidak menjalankan pengiriman pesan terjadwal.
- Pengiriman status audio, unduh/bagikan status, dan publikasi status belum diuji end-to-end.
