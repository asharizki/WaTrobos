package com.wmods.wppenhacer.video

object VideoStatusPlan {
    const val MAX_SOURCE_MS = 600_000L
    const val MAX_PARTS = 20
    val lengths = listOf(30_000L, 60_000L, 90_000L)
    data class Range(val startMs: Long, val endMs: Long) {
        val durationMs get() = endMs - startMs
    }
    fun selected(duration: Long, start: Long, end: Long, limit: Long): List<Range> {
        require(limit in lengths)
        require(duration in 1_000..MAX_SOURCE_MS) { "Gunakan video 1 detik sampai 10 menit." }
        require(start >= 0 && end <= duration && end - start in 1_000..limit) {
            "Pilih rentang 1 sampai ${limit / 1000} detik di dalam video."
        }
        return listOf(Range(start, end))
    }
    fun automatic(duration: Long, limit: Long): List<Range> {
        require(limit in lengths)
        require(duration in 1_000..MAX_SOURCE_MS) { "Gunakan video 1 detik sampai 10 menit." }
        val parts = ArrayList<Range>()
        var start = 0L
        while (start < duration) {
            var end = minOf(start + limit, duration)
            if (duration - end in 1..999) end = duration - 1000
            parts.add(Range(start, end)); start = end
        }
        require(parts.size <= MAX_PARTS)
        return parts
    }
}
