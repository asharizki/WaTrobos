package com.wmods.wppenhacer.xposed.core.components

import java.io.File
import java.lang.reflect.Modifier
import java.net.URI
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

internal object StatusMediaFile {
    private val extensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "heic", "avif",
        "mp4", "m4v", "webm", "3gp", "mov",
        "opus", "ogg", "m4a", "aac", "amr", "mp3", "wav"
    )

    fun resolve(preferred: List<File>, roots: List<Any>): File? {
        val direct = preferred.filter(::isCandidate)
        val candidates = direct.ifEmpty { collect(roots) }.distinctBy { it.absolutePath }
        val snapshots = candidates.map { Snapshot(it, it.length(), it.lastModified()) }
        if (snapshots.isEmpty()) return null
        Thread.sleep(150)
        val stable = snapshots.filter { snapshot ->
            val (file, size, modified) = snapshot
            size > 0 && file.isFile && file.length() == size && file.lastModified() == modified
        }
        if (direct.isNotEmpty()) return stable.firstOrNull()?.file
        return stable.maxWithOrNull(
            compareBy<Snapshot> { it.file.parentFile?.name.equals(".Statuses", ignoreCase = true) }
                .thenBy { it.size }
        )?.file
    }

    private data class Snapshot(val file: File, val size: Long, val modified: Long)

    private fun isCandidate(file: File): Boolean {
        val name = file.name.lowercase(Locale.ROOT)
        return file.extension.lowercase(Locale.ROOT) in extensions &&
            !name.startsWith("thumb") && !name.contains(".thumb.") &&
            file.isFile && file.canRead() && file.length() > 0
    }

    // Limit traversal to the selected status and its host-owned objects.
    private fun collect(roots: List<Any>): List<File> {
        val loaders = roots.mapNotNull { it.javaClass.classLoader }.toSet()
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val found = mutableListOf<File>()
        val queue = ArrayDeque<Pair<Any, Int>>()
        roots.forEach { queue.addLast(it to 0) }
        while (queue.isNotEmpty() && visited.size < 192) {
            val (value, depth) = queue.removeFirst()
            if (!visited.add(value)) continue
            val file = when (value) {
                is File -> value
                is String -> pathAsFile(value)
                else -> if (isAndroidUri(value.javaClass)) pathAsFile(value.toString()) else null
            }
            if (file != null && isCandidate(file)) found.add(file)
            if (file != null || depth >= 6) continue

            fun enqueue(child: Any?) {
                if (child != null && queue.size < 192) queue.addLast(child to depth + 1)
            }
            val type = value.javaClass
            when {
                value is AtomicReference<*> -> enqueue(value.get())
                value is Array<*> -> value.take(12).forEach(::enqueue)
                value is Collection<*> ->
                    runCatching { value.asSequence().take(12).forEach(::enqueue) }
                type.classLoader?.let(loaders::contains) == true -> {
                    var owner: Class<*>? = type
                    while (owner != null && owner.classLoader?.let(loaders::contains) == true) {
                        owner.declaredFields.filterNot {
                            Modifier.isStatic(it.modifiers) || it.type.isPrimitive || it.isSynthetic
                        }.forEach { field ->
                            runCatching {
                                field.isAccessible = true
                                enqueue(field.get(value))
                            }
                        }
                        owner.declaredMethods.filter {
                            !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 &&
                                it.returnType == File::class.java
                        }.forEach { method ->
                            runCatching {
                                method.isAccessible = true
                                enqueue(method.invoke(value))
                            }
                        }
                        owner = owner.superclass
                    }
                }
            }
        }
        return found
    }

    private fun isAndroidUri(type: Class<*>): Boolean =
        generateSequence(type) { it.superclass }.any { it.name == "android.net.Uri" }

    private fun pathAsFile(path: String): File? = when {
        path.startsWith("/") -> File(path)
        path.startsWith("file://") -> runCatching { File(URI(path)) }.getOrNull()
        else -> null
    }
}
