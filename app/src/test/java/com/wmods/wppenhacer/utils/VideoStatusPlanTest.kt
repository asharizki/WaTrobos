package com.wmods.wppenhacer.utils

import com.wmods.wppenhacer.video.VideoStatusPlan as Plan
import org.junit.Assert.*
import org.junit.Test

class VideoStatusPlanTest {
    @Test fun threeMinuteSourceSupportsAllRequestedLengths() {
        for ((limit, count) in listOf(30_000L to 6, 60_000L to 3, 90_000L to 2)) {
            val parts = Plan.automatic(180_000, limit)
            assertEquals(count, parts.size)
            assertTrue(parts.all { it.durationMs == limit })
        }
    }
    @Test fun unevenTailsCoverTheEntireSourceExactlyOnce() {
        for (limit in Plan.lengths) for (duration in listOf(1000L, 30_001, 60_500, 90_001, 185_000, 599_999, 600_000)) {
            val parts = Plan.automatic(duration, limit)
            assertEquals(0L, parts.first().startMs)
            assertEquals(duration, parts.last().endMs)
            assertEquals(duration, parts.sumOf { it.durationMs })
            assertTrue(parts.all { it.durationMs in 1000..limit })
            assertTrue(parts.zipWithNext().all { (a, b) -> a.endMs == b.startMs })
            assertTrue(parts.size <= 20)
        }
    }
    @Test fun manualOffsetsArePreservedAndInvalidRequestsRejected() {
        assertEquals(listOf(Plan.Range(45_500, 105_500)), Plan.selected(180_000, 45_500, 105_500, 60_000))
        assertTrue(runCatching { Plan.selected(180_000, 0, 60_001, 60_000) }.isFailure)
        assertTrue(runCatching { Plan.selected(180_000, 60_000, 30_000, 30_000) }.isFailure)
        assertTrue(runCatching { Plan.selected(180_000, -1, 30_000, 30_000) }.isFailure)
        assertTrue(runCatching { Plan.selected(180_000, 179_500, 180_500, 30_000) }.isFailure)
        assertTrue(runCatching { Plan.automatic(600_001, 30_000) }.isFailure)
        assertTrue(runCatching { Plan.automatic(90_000, 45_000) }.isFailure)
    }
}
