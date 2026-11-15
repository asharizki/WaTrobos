package com.wmods.wppenhacer.xposed.features.media

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.AudioOpusConverter
import com.wmods.wppenhacer.xposed.utils.AudioStatusPlan
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Imports into WhatsApp's own preview; the user initiates sending through WhatsApp. */
class AudioToVoiceStatus(loader: ClassLoader, preferences: SharedPreferences) : Feature(loader, preferences) {
    private data class Picker(val composer: WeakReference<Any>, val root: WeakReference<ViewGroup>, val activity: WeakReference<Activity>)
    private var picker: Picker? = null
    private val importing = AtomicBoolean()
    private val requestCode = 0x5755
    private val buttonTag = "watrobos.voice-status.audio-picker"
    private val partsTag = "watrobos.voice-status.parts"
    private val batches = WeakHashMap<Any, AudioStatusBatchBridge.Batch>()
    private lateinit var batchBridge: AudioStatusBatchBridge

    override fun getPluginName() = "Audio Lokal ke Status Suara"

    override fun doHook() {
        if (!xprefs.getBoolean("audio_to_voice_status", true)) return
        batchBridge = AudioStatusBatchBridge(classLoader, { target, file ->
            batches[target]?.takeIf { batch -> batch.clips.size > 1 && batch.clips.any { it.file.absolutePath == file.absolutePath } }
        }, { batch, ok ->
            batch.owner.get()?.let { owner -> owner.runOnUiThread {
                toast(owner, if (ok) "${batch.clips.size} bagian diserahkan ke antrean WhatsApp."
                    else "Semua bagian belum dapat diantrekan. Periksa status sebelum mencoba kembali.")
            } }
        })
        runCatching { loadHooker(batchBridge); batchBridge.install() }.onFailure { log("Audio batch adapter unavailable: ${it.javaClass.simpleName}") }
        val composer = runCatching { classLoader.loadClass("com.whatsapp.status.composer.VoiceStatusComposerFragment") }.getOrNull() ?: return
        composer.resolve().firstMethod {
            parameters(Bundle::class, View::class)
        }.hook {
            after {
                val root = args[1] as? ViewGroup ?: return@after
                addPicker(instance, root)
            }
        }
        Activity::class.java.resolve().firstMethod {
            name = "onActivityResult"
            parameters(Int::class, Int::class, Intent::class.java)
        }.hook {
            after {
                if (args[0] != requestCode) return@after
                val pending = picker ?: return@after
                if (pending.activity.get() !== instance) return@after
                picker = null
                if (args[1] != Activity.RESULT_OK) return@after
                val uri = (args[2] as? Intent)?.data ?: return@after
                val activity = pending.activity.get() ?: return@after
                val target = pending.composer.get() ?: return@after
                val root = pending.root.get()?.takeIf { it.isAttachedToWindow } ?: return@after
                prepare(activity, target, root, uri)
            }
        }
    }

    private fun activity(context: Context): Activity? {
        var current = context
        repeat(12) {
            if (current is Activity) return current as Activity
            current = (current as? ContextWrapper)?.baseContext ?: return null
        }
        return null
    }

    private fun addPicker(composer: Any, root: ViewGroup) {
        if (root.findViewWithTag<View>(buttonTag) != null) return
        val activity = activity(root.context) ?: return
        fun dp(value: Int) = (value * root.resources.displayMetrics.density).toInt()
        val button = TextView(root.context).apply {
            tag = buttonTag
            text = "\u266b"
            textSize = 26f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.rgb(20, 34, 56))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(255, 138, 61)) }
            contentDescription = "Pilih audio dari HP untuk status suara"
            tooltipText = "Audio dari HP"
            elevation = dp(3).toFloat()
            setOnClickListener {
                if (importing.get()) { toast(activity, "Audio masih disiapkan."); return@setOnClickListener }
                picker = Picker(WeakReference(composer), WeakReference(root), WeakReference(activity))
                try {
                    activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "audio/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, requestCode)
                } catch (_: Exception) {
                    picker = null
                    toast(activity, "Pemilih berkas audio tidak tersedia.")
                }
            }
        }
        root.addView(button, ViewGroup.LayoutParams(dp(48), dp(48)))
        fun position() {
            val anchorId = root.resources.getIdentifier("color_picker_btn", "id", activity.packageName)
            val anchor = if (anchorId != 0) root.findViewById<View>(anchorId) else null
            if (anchor != null && anchor.isShown) {
                val anchorAt = IntArray(2); val rootAt = IntArray(2)
                anchor.getLocationInWindow(anchorAt); root.getLocationInWindow(rootAt)
                button.x = (anchorAt[0] - rootAt[0] - dp(56)).coerceAtLeast(dp(8)).toFloat()
                button.y = (anchorAt[1] - rootAt[1] + (anchor.height - dp(48)) / 2).coerceAtLeast(dp(8)).toFloat()
            } else {
                button.x = (root.width - dp(112)).coerceAtLeast(dp(8)).toFloat()
                button.y = dp(12).toFloat()
            }
        }
        root.post { position() }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> position() }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                batches.remove(composer)?.let { batch ->
                    if (!batch.sending && !batch.queued) batch.clips.forEach { it.file.delete() }
                }
            }
        })
    }

    private fun controller(composer: Any): Pair<Any, Method>? {
        for (field in composer.javaClass.declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
            val value = runCatching { field.isAccessible = true; field.get(composer) }.getOrNull() ?: continue
            val method = value.javaClass.declaredMethods.singleOrNull {
                val types = it.parameterTypes
                types.size == 3 && types[0] == File::class.java && types[1] == ByteArray::class.java && types[2] == Long::class.javaPrimitiveType
            } ?: continue
            method.isAccessible = true
            return value to method
        }
        return null
    }

    private fun toast(activity: Activity, message: String) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()

    private fun active(activity: Activity, root: ViewGroup) = !activity.isFinishing && !activity.isDestroyed && root.isAttachedToWindow

    private fun reset(root: ViewGroup) {
        importing.set(false)
        root.findViewWithTag<View>(buttonTag)?.isEnabled = true
    }

    private fun prepare(activity: Activity, composer: Any, root: ViewGroup, uri: Uri) {
        if (!importing.compareAndSet(false, true)) return
        if (controller(composer) == null) {
            reset(root)
            toast(activity, "Pembuat status suara belum siap. Buka ulang halaman status suara.")
            return
        }
        root.findViewWithTag<View>(buttonTag)?.isEnabled = false
        toast(activity, "Membaca audio...")
        Thread({
            var input: File? = null
            try {
                val cache = File(activity.cacheDir, "watrobos_audio_import").apply { mkdirs() }
                val selected = File.createTempFile("selected-", ".audio", cache)
                input = selected
                activity.contentResolver.openInputStream(uri)?.use { source ->
                    selected.outputStream().use { target ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 50L * 1024 * 1024) { "Audio exceeds import limit" }
                            target.write(buffer, 0, count)
                        }
                    }
                } ?: error("Audio unavailable")
                val metadata = MediaMetadataRetriever()
                val duration = try {
                    metadata.setDataSource(selected.absolutePath)
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally { metadata.release() }
                require(duration in 1_000..AudioStatusPlan.MAX_MS)
                activity.runOnUiThread {
                    if (!active(activity, root)) { selected.delete(); reset(root); return@runOnUiThread }
                    AudioStatusEditor(activity, selected, duration, batchBridge.available,
                        accepted = { ranges -> convert(activity, composer, root, selected, ranges) },
                        cancelled = { selected.delete(); reset(root) }).show()
                }
            } catch (error: Throwable) {
                input?.delete()
                activity.runOnUiThread {
                    reset(root)
                    if (active(activity, root)) toast(activity, "Gunakan audio 1 detik sampai 10 menit, maksimal 50 MB, dengan format yang didukung HP.")
                }
                log("Audio status read failed: ${error.javaClass.simpleName}")
            }
        }, "watrobos-audio-read").start()
    }

    private fun convert(activity: Activity, composer: Any, root: ViewGroup, input: File, ranges: List<AudioStatusPlan.Range>) {
        toast(activity, "Menyiapkan ${ranges.size} bagian audio...")
        Thread({
            val outputs = ArrayList<File>()
            try {
                val base = activity.externalMediaDirs.firstOrNull() ?: error("Media directory unavailable")
                val directory = File(base, "WhatsApp/Media/WhatsApp Voice Notes/${SimpleDateFormat("yyyyww", Locale.US).format(Date())}")
                check(directory.isDirectory || directory.mkdirs())
                val audio = ranges.mapIndexed { index, range ->
                    check(!activity.isDestroyed && root.isAttachedToWindow)
                    val destination = File(directory, "PTT-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}-WATROBOS-${UUID.randomUUID().toString().take(8)}-${index + 1}.opus")
                    outputs.add(destination)
                    AudioOpusConverter.convertForStatus(input, destination, range.startMs, range.endMs) ?: error("Cannot decode audio range")
                }
                activity.runOnUiThread {
                    reset(root)
                    if (!active(activity, root)) { outputs.forEach { it.delete() }; return@runOnUiThread }
                    try {
                        if (audio.size > 1) log("Audio batch prepared: native groups=${batchBridge.checkPrepared(composer, audio)}")
                        val target = controller(composer) ?: error("Composer unavailable")
                        val first = audio.first()
                        target.second.invoke(target.first, first.file, first.waveform, first.durationMs)
                        val previous = batches.put(composer, AudioStatusBatchBridge.Batch(audio, activity))
                        if (previous != null && !previous.sending && !previous.queued) previous.clips.forEach { it.file.delete() }
                        root.findViewWithTag<View>(partsTag)?.let { root.removeView(it) }
                        if (audio.size > 1) {
                            val label = TextView(activity).apply {
                                tag = partsTag
                                text = "${audio.size} status siap • Ketuk untuk melihat bagian"
                                textSize = 14f
                                setTextColor(Color.WHITE)
                                setPadding(24, 16, 24, 16)
                                background = GradientDrawable().apply { cornerRadius = 20f; setColor(Color.rgb(20, 34, 56)) }
                                elevation = 10f
                                contentDescription = text
                                setOnClickListener {
                                    if (audio.any { !it.file.isFile }) {
                                        visibility = View.GONE
                                        batches.remove(composer)
                                        audio.forEach { it.file.delete() }
                                        toast(activity, "Draf sudah dihapus. Pilih audio lagi untuk menyiapkan ulang.")
                                        return@setOnClickListener
                                    }
                                    AlertDialog.Builder(activity).setTitle("${audio.size} status berurutan")
                                        .setItems(ranges.mapIndexed { i, range -> "Bagian ${i + 1}: ${range.startMs / 1000.0} – ${range.endMs / 1000.0} detik" }.toTypedArray()) { _, index ->
                                            runCatching {
                                                val clip = audio[index]
                                                val current = controller(composer) ?: error("Composer unavailable")
                                                current.second.invoke(current.first, clip.file, clip.waveform, clip.durationMs)
                                            }.onFailure { toast(activity, "Bagian belum dapat dipratinjau.") }
                                        }.setNegativeButton("Tutup", null).show()
                                }
                            }
                            root.addView(label, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                            label.x = 18 * root.resources.displayMetrics.density
                            label.y = 76 * root.resources.displayMetrics.density
                        }
                        toast(activity, if (audio.size > 1) "${audio.size} bagian siap. Periksa audiens; kirim WhatsApp akan mengantrekan semua bagian."
                            else "Bagian siap. Periksa pratinjau dan kirim lewat tombol WhatsApp.")
                    } catch (failure: Throwable) {
                        outputs.forEach { it.delete() }
                        log("Audio status preload failed: ${failure.javaClass.simpleName}")
                        toast(activity, "Audio belum dapat dimuat ke status suara.")
                    }
                }
            } catch (error: Throwable) {
                outputs.forEach { it.delete() }
                activity.runOnUiThread {
                    reset(root)
                    if (active(activity, root)) toast(activity, "Bagian audio tidak dapat disiapkan. Coba berkas atau rentang lain.")
                }
                log("Audio status conversion failed: ${error.javaClass.simpleName}")
            } finally { input.delete() }
        }, "watrobos-audio-status").start()
    }
}
