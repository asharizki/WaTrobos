package com.wmods.wppenhacer.video

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.content.ActivityNotFoundException
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import com.wmods.wppenhacer.BuildConfig
import java.io.File
import java.util.Locale
import java.util.UUID

@androidx.annotation.OptIn(UnstableApi::class)
class VideoStatusActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var body: LinearLayout
    private lateinit var info: TextView
    private lateinit var status: TextView
    private lateinit var sourceButton: Button
    private lateinit var limits: Spinner
    private lateinit var modes: RadioGroup
    private lateinit var manual: RadioButton
    private lateinit var automatic: RadioButton
    private lateinit var start: EditText
    private lateinit var end: EditText
    private lateinit var startSeek: SeekBar
    private lateinit var endSeek: SeekBar
    private lateinit var rangeBox: LinearLayout
    private lateinit var prepare: Button
    private lateinit var share: Button
    private lateinit var cancel: Button
    private lateinit var progress: ProgressBar
    private lateinit var video: VideoView
    private lateinit var partsBox: LinearLayout
    private var source: File? = null
    private var duration = 0L
    private var sourceHeight = 720
    private var results = emptyList<File>()
    private var session: File? = null
    private var retained = false
    private var busy = false
    @Volatile private var ticket = 0
    private var transformer: Transformer? = null
    private var previewStop: Runnable? = null
    private val fg = Color.rgb(239, 243, 250)
    private val accent = Color.rgb(255, 138, 61)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun time(ms: Long) = "%d:%02d.%01d".format(Locale.US, ms / 60_000, ms / 1000 % 60, ms % 1000 / 100)
    private fun text(value: String, size: Float = 15f, target: LinearLayout = body) = TextView(this).apply {
        text = value; textSize = size; setTextColor(fg); setPadding(0, dp(8), 0, dp(6)); target.addView(this)
    }
    private fun button(value: String, target: LinearLayout = body, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false
        val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
        setTextColor(android.content.res.ColorStateList(states, intArrayOf(0xffa3aec0.toInt(), Color.rgb(20, 34, 56))))
        backgroundTintList = android.content.res.ColorStateList(states, intArrayOf(0xff28364b.toInt(), accent))
        target.addView(this, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(6) }); setOnClickListener { action() }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.rgb(11, 21, 37); window.navigationBarColor = Color.rgb(11, 21, 37)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24)); setBackgroundColor(Color.rgb(11, 21, 37)) }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(body) })
        text("Pemotong video status", 25f).setTypeface(null, Typeface.BOLD)
        text("WaTrobos • asHDEV", 13f)
        text("Pilih bagian atau bagi seluruh video berurutan. Video disiapkan dahulu; publikasi tetap melalui tombol kirim WhatsApp.")
        sourceButton = button("Pilih video dari galeri") { choose() }
        info = text("Belum ada video. Sumber maksimal 10 menit / 500 MB.")
        video = VideoView(this).apply { setBackgroundColor(Color.BLACK) }
        body.addView(video, LinearLayout.LayoutParams(-1, dp(200)))
        text("Durasi maksimal tiap status")
        limits = Spinner(this).apply {
            adapter = ArrayAdapter(this@VideoStatusActivity, android.R.layout.simple_spinner_dropdown_item, listOf("30 detik", "60 detik", "90 detik"))
            backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        }; body.addView(limits)
        modes = RadioGroup(this)
        manual = RadioButton(this).apply { id = View.generateViewId(); text = "Pilih bagian"; setTextColor(fg) }
        automatic = RadioButton(this).apply { id = View.generateViewId(); text = "Bagi otomatis seluruh video"; setTextColor(fg) }
        modes.addView(manual); modes.addView(automatic); modes.check(automatic.id); body.addView(modes)
        rangeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(rangeBox)
        text("Mulai (detik)", target = rangeBox)
        fun number(value: String) = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(); setTextColor(fg); setText(value); rangeBox.addView(this)
        }
        start = number("0.0")
        startSeek = SeekBar(this); rangeBox.addView(startSeek)
        text("Selesai (detik)", target = rangeBox); end = number("30.0")
        endSeek = SeekBar(this); rangeBox.addView(endSeek)
        button("Putar pilihan sumber") {
            runCatching { val range = plan().first(); preview(source ?: error("Pilih video dahulu."), range.startMs, range.endMs) }
                .onFailure { message(it.message ?: "Rentang belum valid.") }
        }
        button("Hentikan pratinjau") { stopPreview() }
        status = text("Pilih video untuk mulai.")
        prepare = button("Siapkan potongan video") { export() }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }; body.addView(progress)
        cancel = button("Batalkan proses") { cancelExport() }.apply { visibility = View.GONE }
        partsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(partsBox)
        share = button("Lanjut ke status WhatsApp") { shareAll() }.apply { isEnabled = false }
        text("Video keluaran MP4 H.264/AAC, hingga tinggi 720 piksel. Pemotongan mengikuti frame video. Maksimal 20 bagian. Tetap buka halaman ini selama proses.", 13f)
        button("Kembali") { onBackPressed() }
        modes.setOnCheckedChangeListener { _, _ -> if (!busy) discardResults(); updatePlan() }
        limits.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!busy) discardResults()
                if (duration > 0) {
                    val from = ((start.text.toString().toDoubleOrNull() ?: 0.0) * 1000).toLong().coerceIn(0, duration - 1000)
                    end.setText(String.format(Locale.US, "%.1f", minOf(duration, from + limit()) / 1000.0))
                }
                updatePlan()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        fun sync(field: EditText, seek: SeekBar) {
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun afterTextChanged(s: Editable?) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    stopPreview()
                    val seconds = s?.toString()?.replace(',', '.')?.toDoubleOrNull()
                    if (seconds != null) seek.progress = (seconds * 10).toInt().coerceIn(0, seek.max)
                    if (!busy) { discardResults(); updatePlan() }
                }
            })
        }
        sync(start, startSeek); sync(end, endSeek)
        fun seekListener(action: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, user: Boolean) { if (user && duration > 0 && !busy) action(value) }
        }
        startSeek.setOnSeekBarChangeListener(seekListener { value ->
            val from = (value * 100L).coerceAtMost(duration - 1000)
            start.setText(String.format(Locale.US, "%.1f", from / 1000.0))
            end.setText(String.format(Locale.US, "%.1f", minOf(duration, from + limit()) / 1000.0))
        })
        endSeek.setOnSeekBarChangeListener(seekListener { value ->
            val from = ((start.text.toString().toDoubleOrNull() ?: 0.0) * 1000).toLong().coerceIn(0, duration - 1000)
            end.setText(String.format(Locale.US, "%.1f", (value * 100L).coerceIn(from + 1000, minOf(duration, from + limit())) / 1000.0))
        })
        body.isFocusableInTouchMode = true; body.requestFocus()
        updatePlan(); cleanExpired()
    }

    private fun limit() = VideoStatusPlan.lengths[limits.selectedItemPosition.coerceIn(0, 2)]
    private fun updatePlan() {
        rangeBox.visibility = if (modes.checkedRadioButtonId == manual.id) View.VISIBLE else View.GONE
        if (!busy && duration > 0) status.text = if (modes.checkedRadioButtonId == automatic.id)
            "${VideoStatusPlan.automatic(duration, limit()).size} bagian berurutan, masing-masing maksimal ${limit() / 1000} detik."
            else "Isi waktu mulai dan selesai dalam detik, lalu putar untuk memeriksa."
    }
    private fun plan(): List<VideoStatusPlan.Range> {
        require(source != null) { "Pilih video dahulu." }
        if (modes.checkedRadioButtonId == automatic.id) return VideoStatusPlan.automatic(duration, limit())
        fun read(field: EditText) = field.text.toString().replace(',', '.').toDoubleOrNull()?.times(1000)?.toLong()
            ?: throw IllegalArgumentException("Isi waktu mulai dan selesai dalam detik.")
        return VideoStatusPlan.selected(duration, read(start), read(end), limit())
    }
    private fun choose() {
        if (busy) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                startActivityForResult(Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                    type = "video/*"; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, 701)
                return
            } catch (_: ActivityNotFoundException) { /* Older/custom ROM gallery below. */ }
        }
        try {
            startActivityForResult(Intent(Intent.ACTION_PICK).apply {
                setDataAndType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, 701)
        } catch (_: ActivityNotFoundException) {
            message("Galeri video tidak tersedia. Aktifkan aplikasi galeri di HP.")
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 701 && resultCode == RESULT_OK) data?.data?.let { readSource(it) }
    }
    private fun readSource(uri: Uri) {
        stopPreview(); setBusy(true); status.text = "Membaca video..."
        val generation = ++ticket
        Thread({
            var copied: File? = null
            try {
                val file = File.createTempFile("video-status-source-", ".media", cacheDir); copied = file
                contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024); var size = 0L
                    while (true) {
                        check(ticket == generation && !isDestroyed)
                        val n = input.read(buffer); if (n < 0) break
                        size += n; require(size <= 500L * 1024 * 1024) { "Video melebihi 500 MB." }
                        output.write(buffer, 0, n)
                    }
                } } ?: error("Video tidak dapat dibaca.")
                val meta = MediaMetadataRetriever()
                val details = try {
                    meta.setDataSource(file.absolutePath)
                    require(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "Berkas tidak memiliki video." }
                    val length = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                    require(length in 1000..VideoStatusPlan.MAX_SOURCE_MS) { "Gunakan video 1 detik sampai 10 menit." }
                    length to (meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720)
                } finally { meta.release() }
                runOnUiThread {
                    if (ticket != generation || isDestroyed) { file.delete(); return@runOnUiThread }
                    discardResults(); source?.delete(); source = file; duration = details.first; sourceHeight = details.second
                    startSeek.max = (duration / 100).toInt(); endSeek.max = (duration / 100).toInt()
                    start.setText("0.0"); end.setText(String.format(Locale.US, "%.1f", minOf(duration, limit()) / 1000.0))
                    info.text = "Video ${time(duration)} • %.1f MB".format(Locale.US, file.length() / 1048576.0)
                    setBusy(false); updatePlan()
                }
            } catch (failure: Throwable) {
                copied?.delete()
                runOnUiThread { if (ticket == generation && !isDestroyed) { setBusy(false); message(failure.message ?: "Video tidak dapat dibaca.") } }
            }
        }, "watrobos-video-read").start()
    }
    private fun stopPreview() {
        previewStop?.let { handler.removeCallbacks(it) }; previewStop = null
        runCatching { video.stopPlayback() }
    }
    private fun preview(file: File, from: Long, to: Long) {
        if (busy) return
        stopPreview(); video.setVideoURI(Uri.fromFile(file))
        video.setOnErrorListener { _, _, _ -> message("Video belum dapat diputar pada perangkat ini."); true }
        video.setOnPreparedListener { player ->
            fun play() {
                video.start(); previewStop = Runnable { stopPreview() }.also { handler.postDelayed(it, to - from) }
            }
            if (from == 0L) play() else {
                player.setOnSeekCompleteListener { play() }
                player.seekTo(from, android.media.MediaPlayer.SEEK_CLOSEST)
            }
        }
        status.text = "Pratinjau ${time(from)} – ${time(to)}"
    }
    private fun setBusy(value: Boolean) {
        busy = value; sourceButton.isEnabled = !value; prepare.isEnabled = !value
        limits.isEnabled = !value; manual.isEnabled = !value; automatic.isEnabled = !value
        start.isEnabled = !value; end.isEnabled = !value; share.isEnabled = !value && results.isNotEmpty()
        startSeek.isEnabled = !value; endSeek.isEnabled = !value
        progress.visibility = if (value) View.VISIBLE else View.GONE; cancel.visibility = if (value) View.VISIBLE else View.GONE
        if (value) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun export() {
        if (busy) return
        val ranges = runCatching { plan() }.getOrElse { message(it.message ?: "Rentang tidak valid."); return }
        stopPreview(); discardResults()
        val folder = File(cacheDir, "video_status/${UUID.randomUUID()}").apply { check(mkdirs()) }
        session = folder; setBusy(true); progress.progress = 0
        val generation = ++ticket
        val files = ranges.indices.map { File(folder, "WaTrobos-Status-%02d.mp4".format(Locale.US, it + 1)) }
        fun next(index: Int) {
            if (ticket != generation || isDestroyed) return
            if (index == ranges.size) {
                transformer = null; results = files; setBusy(false)
                status.text = "${files.size} video siap. Periksa bagian dan audiens sebelum kirim."
                partsBox.removeAllViews()
                files.forEachIndexed { i, file -> button("Putar bagian ${i + 1} • ${time(ranges[i].startMs)} – ${time(ranges[i].endMs)}", partsBox) {
                    preview(file, 0, ranges[i].durationMs)
                } }
                share.text = "Lanjut ke status WhatsApp (${files.size} video)"
                return
            }
            try {
                val range = ranges[index]
                status.text = "Memotong bagian ${index + 1}/${ranges.size}: ${time(range.startMs)} – ${time(range.endMs)}"
                val item = MediaItem.Builder().setUri(Uri.fromFile(source!!)).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(range.startMs).setEndPositionMs(range.endMs).build()).build()
                val edited = EditedMediaItem.Builder(item).setEffects(Effects(emptyList(),
                    listOf(Presentation.createForHeight(minOf(720, sourceHeight).coerceAtLeast(2))))).build()
                val delegate = DefaultEncoderFactory.Builder(this).setEnableFallback(true).build()
                val encoder = object : Codec.EncoderFactory by delegate {
                    override fun videoNeedsEncoding() = true
                }
                val current = Transformer.Builder(this).setEncoderFactory(encoder)
                    .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (ticket != generation) return
                            progress.progress = (index + 1) * 100 / ranges.size
                            handler.post { next(index + 1) }
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (ticket != generation) return
                            cancelExport(); message("Video gagal diproses (kode ${exportException.errorCode}). Coba video MP4 lain.")
                        }
                    }).build()
                transformer = current; current.start(edited, files[index].absolutePath)
            } catch (_: Throwable) { cancelExport(); message("Video tidak dapat diproses pada perangkat ini.") }
        }
        next(0)
    }
    private fun cancelExport() {
        ticket++; transformer?.cancel(); transformer = null
        stopPreview(); discardResults(); setBusy(false); status.text = "Proses dibatalkan. Tidak ada status diterbitkan."
    }
    private fun shareAll() {
        if (busy || results.isEmpty() || results.any { !it.isFile }) { message("Siapkan potongan dahulu."); return }
        try {
            val uris = ArrayList(results.map { FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", it) })
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "video/mp4"; setPackage("com.whatsapp")
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra("jid", "status@broadcast")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(contentResolver, "Potongan video status", uris.first()).apply {
                    uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
            }
            startActivity(intent); retained = true
        } catch (_: Exception) { message("WhatsApp tidak dapat membuka potongan. Pastikan WhatsApp Messenger terpasang.") }
    }
    private fun discardResults() {
        if (!retained) session?.deleteRecursively()
        session = null; retained = false; results = emptyList()
        if (::partsBox.isInitialized) partsBox.removeAllViews()
        if (::share.isInitialized) { share.isEnabled = false; share.text = "Lanjut ke status WhatsApp" }
    }
    private fun cleanExpired() {
        val root = File(cacheDir, "video_status")
        root.listFiles()?.filter { it.isDirectory && it.lastModified() < System.currentTimeMillis() - 86_400_000 }
            ?.forEach { if (it.canonicalPath.startsWith(root.canonicalPath + File.separator)) it.deleteRecursively() }
    }
    private fun message(value: String) { status.text = value; Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
    override fun onBackPressed() {
        if (busy) AlertDialog.Builder(this).setTitle("Batalkan proses video?").setMessage("Potongan yang belum selesai akan dihapus.")
            .setPositiveButton("Batalkan") { _, _ -> cancelExport(); finish() }.setNegativeButton("Lanjutkan", null).show()
        else super.onBackPressed()
    }
    override fun onStop() { stopPreview(); super.onStop() }
    override fun onDestroy() {
        ticket++; transformer?.cancel(); transformer = null; handler.removeCallbacksAndMessages(null)
        stopPreview(); source?.delete(); discardResults(); super.onDestroy()
    }
}
