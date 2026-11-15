package com.wmods.wppenhacer.xposed.features.media

import android.app.Activity
import android.net.Uri
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.AudioOpusConverter.StatusAudio
import java.io.File
import java.lang.ref.WeakReference
import java.util.IdentityHashMap

/** One native media group per audio part. Only expands a user-initiated native send.
 * Recipients, privacy checks, colour and status metadata remain native.
 * Class mappings for each supported WhatsApp Messenger build.
 */
internal class AudioStatusBatchBridge(
    private val loader: ClassLoader,
    private val pending: (Any, File) -> Batch?,
    private val notify: (Batch, Boolean) -> Unit
) : YukiBaseHooker() {
    override fun onHook() {}
    class Batch(val clips: List<StatusAudio>, activity: Activity? = null) {
        val owner = WeakReference(activity)
        var sending = false
        var queued = false
    }
    private data class Template(val receiver: Any, val args: Array<Any?>)
    private class Scope(val batch: Batch, val dryRun: Boolean = false) {
        val templates = IdentityHashMap<Any, Template>()
        var expanding = false
        var enqueuing = false
        var failed = false
        var attempted = false
        var queuedParts = 0
    }
    private val scope = ThreadLocal<Scope?>()
    var available = false
        private set
    private var prepareCheck: ((Any, List<StatusAudio>) -> Int)? = null

    /** Native in-memory job/group construction only. Never invokes the enqueue method. */
    fun checkPrepared(composer: Any, clips: List<StatusAudio>): Int =
        prepareCheck?.invoke(composer, clips) ?: error("Automatic status adapter unavailable")

    // Host DEX identifiers may begin with digits; they are not Java source type names.
    private fun hostClass(name: String) = loader.loadClass("X.$name")

    fun install() {
        val app = Utils.application
        val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName
        val (mediaTypes, statusTypes) = when (version) {
            "2.26.43.74" -> listOf("9XQ", "FTG", "8v0", "AqU", "AGj") to
                listOf("Sag", "AW1", "90y", "8FN", "A07")
            "2.26.43.76" -> listOf("9XG", "FT1", "8uq", "AqM", "AGb") to
                listOf("SZr", "AVt", "90p", "8FB", "A07")
            "2.26.44.90" -> listOf("9HF", "Md5", "8c8", "AfM", "A4A") to
                listOf("Sm7", "AIc", "L5W", "7xw", "A06")
            else -> return
        }
        val composer = loader.loadClass("com.whatsapp.status.composer.VoiceStatusComposerFragment")
        val (builder, metadata, media, group, enqueue) = mediaTypes.map(::hostClass)
        val (duration, audienceClass, privacy, audienceData) = statusTypes.take(4).map(::hostClass)
        val providerFieldName = statusTypes[4]
        val build = builder.declaredMethods.single { it.name == "A05" && it.parameterTypes.size == 9 && it.parameterTypes[0] == Uri::class.java }
        val enqueueMethod = enqueue.declaredMethods.single { it.name == "A02" && it.parameterTypes.size == 14 && it.parameterTypes[4] == group }
        val mediaFactory = media.getDeclaredMethod("A01", File::class.java)
        val durationMethod = duration.getDeclaredMethod("A00", File::class.java)
        val metaCtor = metadata.getDeclaredConstructor(String::class.java, ByteArray::class.java,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        val nativeFields = listOf("A05", "A00", "A04", "A02", "A03", "A01", "A07", "A08")
            .map { metadata.getDeclaredField(it).apply { isAccessible = true } }
        val groupCtor = group.getDeclaredConstructor(Long::class.javaObjectType, List::class.java)
        val groupedJobs = group.getDeclaredField("A07").apply { isAccessible = true }
        val groupToken = group.getDeclaredField("A05").apply { isAccessible = true }
        val controllerField = composer.getDeclaredField("A05").apply { isAccessible = true }
        val audioFileField = controllerField.type.getDeclaredField("A09").apply { isAccessible = true }
        composer.getDeclaredMethod("A09", audienceClass, composer, File::class.java)

        fun buildPart(template: Template, clip: StatusAudio): Any {
            val values = template.args.copyOf()
            val source = values[2] ?: error("Missing status metadata")
            val fields = nativeFields.map { it.get(source) }
            values[0] = Uri.parse(clip.file.absolutePath)
            values[2] = metaCtor.newInstance(fields[0], clip.waveform, fields[1], fields[2],
                fields[3], fields[4], fields[5], fields[6], fields[7])
            values[3] = mediaFactory.invoke(null, clip.file)
            values[5] = clip.file.name
            values[6] = durationMethod.invoke(null, clip.file)
            return build.invoke(template.receiver, *values) ?: error("Cannot build status part")
        }
        fun groupsFor(current: Scope, originals: List<*>, token: Any?): List<Any> {
            check(originals.isNotEmpty() && originals.all { current.templates.containsKey(it) })
            current.expanding = true
            try {
                return current.batch.clips.map { clip ->
                    check(clip.file.isFile && clip.file.length() > 0)
                    val jobs = originals.map { job -> buildPart(current.templates[job] ?: error("Missing template"), clip) }
                    // A native group is copies of ONE file for recipients, never different parts.
                    val nativeGroup = groupCtor.newInstance(token, jobs)
                    check((groupedJobs.get(nativeGroup) as Collection<*>).size == originals.size)
                    nativeGroup
                }
            } finally { current.expanding = false }
        }
        val recipientsMethod = composer.getDeclaredMethod("A04", composer)
        val delivery = composer.getDeclaredField("A0d").apply { isAccessible = true }
        val providerField = delivery.type.getDeclaredField(providerFieldName).apply { isAccessible = true }
        val providerGet = hostClass("00x").getMethod("get")
        val privacyProvider = composer.getDeclaredField("A0c").apply { isAccessible = true }
        val privacyGet = privacy.getDeclaredMethod("A01", privacyProvider.type)
        val setAudience = audienceClass.getDeclaredMethod("A08", audienceData)
        prepareCheck = { target, clips ->
            val receiver = providerGet.invoke(providerField.get(delivery.get(target))) ?: error("Status builder unavailable")
            val recipients = recipientsMethod.invoke(null, target) as List<*>
            check(recipients.isNotEmpty()) { "No status audience selected" }
            val audience = audienceClass.getDeclaredConstructor().newInstance()
            privacyGet.invoke(null, privacyProvider.get(target))?.let { setAudience.invoke(audience, it) }
            val meta = metaCtor.newInstance(null, clips.first().waveform, 0, 0, 0, 0, 0, false, false)
            val previous = scope.get()
            val probe = Scope(Batch(clips), dryRun = true)
            scope.set(probe)
            try {
                val originals = recipients.map { recipient ->
                    buildPart(Template(receiver, arrayOf(null, recipient, meta, null, audience, null, 0, 1, true)), clips.first())
                }
                val groups = groupsFor(probe, originals, null)
                check(groups.size == clips.size && !probe.attempted)
                groups.size
            } finally { if (previous == null) scope.remove() else scope.set(previous) }
        }

        composer.resolve().firstMethod { name = "A09"; parameters(audienceClass, composer, File::class.java) }.hook {
            before {
                val target = args[1] ?: return@before
                val file = args[2] as? File ?: return@before
                val batch = pending(target, file) ?: return@before
                if (batch.sending || batch.queued) { result = null; return@before }
                if ((audioFileField.get(controllerField.get(target)) as? File)?.absolutePath != file.absolutePath) return@before
                batch.sending = true
                scope.set(Scope(batch))
            }
            after {
                val current = scope.get() ?: return@after
                scope.remove()
                current.batch.sending = false
                // An uncertain/partial attempt must not be automatically replayed and duplicated.
                current.batch.queued = current.attempted
                if (current.attempted || current.failed) notify(current.batch,
                    current.queuedParts == current.batch.clips.size && !current.failed)
            }
        }
        builder.resolve().firstMethod { name = build.name; parameters(*build.parameterTypes) }.hook {
            after {
                val current = scope.get() ?: return@after
                if (current.expanding) return@after
                val uri = args[0] as? Uri ?: return@after
                if (current.batch.clips.none { it.file.absolutePath == uri.path || it.file.absolutePath == uri.toString() }) return@after
                val job = result ?: return@after
                current.templates[job] = Template(instance, args.copyOf())
            }
        }
        enqueue.resolve().firstMethod { name = enqueueMethod.name; parameters(*enqueueMethod.parameterTypes) }.hook {
            before {
                val current = scope.get() ?: return@before
                if (current.dryRun) { current.failed = true; result = null; return@before }
                if (current.enqueuing) return@before
                var firstResult: Any? = null
                try {
                    val nativeGroup = args[4] ?: error("Missing native group")
                    val originals = groupedJobs.get(nativeGroup) as List<*>
                    // Build/validate every group before the first enqueue call.
                    val groups = groupsFor(current, originals, groupToken.get(nativeGroup))
                    val values = args.copyOf()
                    current.enqueuing = true
                    for (part in groups) {
                        values[4] = part
                        current.attempted = true
                        val queued = enqueueMethod.invoke(instance, *values) ?: error("Native enqueue returned no task")
                        if (firstResult == null) firstResult = queued
                        current.queuedParts++
                    }
                } catch (_: Throwable) { current.failed = true }
                finally { current.enqueuing = false }
                result = firstResult
            }
        }
        available = true
    }
}

