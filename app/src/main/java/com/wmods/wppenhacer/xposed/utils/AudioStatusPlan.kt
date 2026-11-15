package com.wmods.wppenhacer.xposed.utils

/** Millisecond ranges, end exclusive. Shared by the editor and encoder. */
object AudioStatusPlan {
    const val PART_MS = 30_000L
    const val MAX_MS = 600_000L
    data class Range(val startMs: Long, val endMs: Long) {
        val durationMs get() = endMs - startMs
    }

    fun selected(durationMs: Long, startMs: Long, endMs: Long): List<Range> {
        require(durationMs in 1_000..MAX_MS) { "Gunakan audio berdurasi 1 detik sampai 10 menit." }
        require(startMs >= 0 && endMs <= durationMs && endMs - startMs in 1_000..PART_MS) {
            "Pilih bagian di dalam audio dengan durasi 1 sampai 30 detik."
        }
        return listOf(Range(startMs, endMs))
    }

    fun automatic(durationMs: Long): List<Range> {
        require(durationMs in 1_000..MAX_MS) { "Gunakan audio berdurasi 1 detik sampai 10 menit." }
        val ranges = ArrayList<Range>()
        var start = 0L
        while (start < durationMs) {
            var end = minOf(start + PART_MS, durationMs)
            // Keep a tiny tail playable without dropping or duplicating any source interval.
            if (durationMs - end in 1..999) end = durationMs - 1_000
            ranges.add(Range(start, end))
            start = end
        }
        return ranges
    }
}
