package com.wmods.wppenhacer.xposed.features.media

import android.app.Activity
import android.app.AlertDialog
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import com.wmods.wppenhacer.xposed.utils.AudioStatusPlan
import java.io.File
import java.util.Locale

/** Uses host widgets, without requiring module resources inside WhatsApp. */
internal class AudioStatusEditor(
    private val activity: Activity,
    private val file: File,
    private val duration: Long,
    private val batchAvailable: Boolean,
    private val accepted: (List<AudioStatusPlan.Range>) -> Unit,
    private val cancelled: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var playButton: Button? = null
    private var chosen = false
    private fun time(ms: Long) = "%d:%02d.%01d".format(Locale.US, ms / 60_000, ms / 1000 % 60, ms % 1000 / 100)
    private fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        playButton?.text = "Putar bagian"
    }

    fun show() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        fun label(text: String) = TextView(activity).apply { this.text = text; layout.addView(this) }
        label("Durasi audio ${time(duration)}. Maksimal 30 detik per status.")
        val modes = RadioGroup(activity)
        val manual = RadioButton(activity).apply { id = View.generateViewId(); text = "Pilih bagian" }
        val auto = RadioButton(activity).apply {
            id = View.generateViewId(); text = "Bagi otomatis seluruh audio"; isEnabled = batchAvailable
        }
        modes.addView(manual); modes.addView(auto); modes.check(manual.id); layout.addView(modes)
        val startLabel = label("Mulai (detik)")
        fun field(value: Long) = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(); setText(String.format(Locale.US, "%.1f", value / 1000.0)); layout.addView(this)
        }
        val start = field(0)
        val startSeek = SeekBar(activity).apply { max = (duration / 100).toInt() }; layout.addView(startSeek)
        val endLabel = label("Selesai (detik)")
        val end = field(minOf(duration, 30_000))
        val endSeek = SeekBar(activity).apply { max = (duration / 100).toInt(); progress = minOf(max, 300) }; layout.addView(endSeek)
        val parts = Spinner(activity).apply { visibility = View.GONE }; layout.addView(parts)
        val summary = label("")
        val play = Button(activity).apply { text = "Putar bagian" }; layout.addView(play); playButton = play
        val error = label("").apply { setTextColor(android.graphics.Color.rgb(200, 55, 40)) }
        label("Hanya menyiapkan pratinjau. Setelah menekan kirim WhatsApp, semua bagian otomatis dikirim berurutan ke audiens yang dipilih.")
        if (!batchAvailable) label("Mode otomatis belum tersedia untuk versi WhatsApp ini.")
        val automatic = AudioStatusPlan.automatic(duration)
        parts.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
            automatic.mapIndexed { i, range -> "Bagian ${i + 1}: ${time(range.startMs)} – ${time(range.endMs)}" })
        fun selection(): List<AudioStatusPlan.Range> {
            if (modes.checkedRadioButtonId == auto.id) return automatic
            val from = (start.text.toString().replace(',', '.').toDoubleOrNull()?.times(1000))?.toLong()
                ?: throw IllegalArgumentException("Isi waktu mulai dalam detik.")
            val to = (end.text.toString().replace(',', '.').toDoubleOrNull()?.times(1000))?.toLong()
                ?: throw IllegalArgumentException("Isi waktu selesai dalam detik.")
            return AudioStatusPlan.selected(duration, from, to)
        }
        val dialog = AlertDialog.Builder(activity).setTitle("Potong audio status")
            .setView(ScrollView(activity).apply { addView(layout) })
            .setPositiveButton("Siapkan bagian", null).setNegativeButton("Batal", null).create()
        fun refresh() {
            stop(); error.text = ""
            val isAuto = modes.checkedRadioButtonId == auto.id
            listOf(startLabel, start, startSeek, endLabel, end, endSeek).forEach { it.visibility = if (isAuto) View.GONE else View.VISIBLE }
            parts.visibility = if (isAuto) View.VISIBLE else View.GONE
            summary.text = if (isAuto) "${automatic.size} status akan disiapkan berurutan. Pilih bagian di atas untuk mendengarkan."
                else "Geser penanda atau isi waktu mulai dan selesai."
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.text = if (isAuto) "Siapkan ${automatic.size} status" else "Siapkan bagian"
        }
        fun listener(callback: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) { stop(); callback(progress) } }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        startSeek.setOnSeekBarChangeListener(listener { progress ->
            val from = (progress * 100L).coerceAtMost(duration - 1000)
            val to = minOf(from + 30_000, duration)
            start.setText(String.format(Locale.US, "%.1f", from / 1000.0))
            end.setText(String.format(Locale.US, "%.1f", to / 1000.0)); endSeek.progress = (to / 100).toInt()
        })
        endSeek.setOnSeekBarChangeListener(listener { progress ->
            val from = ((start.text.toString().toDoubleOrNull() ?: 0.0) * 1000).toLong().coerceIn(0, duration - 1000)
            val to = (progress * 100L).coerceIn(from + 1000, minOf(duration, from + 30_000))
            end.setText(String.format(Locale.US, "%.1f", to / 1000.0))
        })
        fun sync(field: EditText, seek: SeekBar) {
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    stop()
                    error.text = ""
                    val seconds = s?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: return
                    seek.progress = (seconds * 10).toInt().coerceIn(0, seek.max)
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        sync(start, startSeek); sync(end, endSeek)
        modes.setOnCheckedChangeListener { _, _ -> refresh() }
        parts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = stop()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        play.setOnClickListener {
            if (player != null) { stop(); return@setOnClickListener }
            try {
                val ranges = selection()
                val range = ranges[if (ranges.size > 1) parts.selectedItemPosition.coerceAtLeast(0) else 0]
                error.text = ""; play.text = "Hentikan pratinjau"
                val media = MediaPlayer(); player = media
                media.setDataSource(file.absolutePath)
                media.setOnErrorListener { _, _, _ -> stop(); error.text = "Audio belum dapat diputar."; true }
                media.setOnCompletionListener { stop() }
                media.setOnPreparedListener {
                    if (player !== media) return@setOnPreparedListener
                    media.setOnSeekCompleteListener {
                        if (player === media) { media.start(); handler.postDelayed({ stop() }, range.durationMs) }
                    }
                    media.seekTo(range.startMs.toInt())
                }
                media.prepareAsync()
            } catch (failure: Exception) { stop(); error.text = failure.message ?: "Bagian tidak valid." }
        }
        dialog.setOnDismissListener { stop(); if (!chosen) cancelled() }
        dialog.setOnShowListener {
            refresh()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val ranges = selection(); stop(); chosen = true; dialog.dismiss(); accepted(ranges)
                } catch (failure: IllegalArgumentException) { error.text = failure.message }
            }
        }
        dialog.show()
    }
}
