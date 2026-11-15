package com.wmods.wppenhacer.utils

import com.wmods.wppenhacer.xposed.utils.AudioStatusPlan
import org.junit.Assert.*
import org.junit.Test

class AudioStatusPlanTest {
    @Test fun ninetySecondsCreatesThreeConsecutiveStatuses() {
        assertEquals(listOf(AudioStatusPlan.Range(0, 30_000), AudioStatusPlan.Range(30_000, 60_000),
            AudioStatusPlan.Range(60_000, 90_000)), AudioStatusPlan.automatic(90_000))
    }

    @Test fun automaticCoverageHasNoGapsOverlapsOrUnplayableTail() {
        for (duration in listOf(1000L, 29_999, 30_000, 30_001, 30_500, 31_000, 59_999, 90_001, 599_999, 600_000)) {
            val parts = AudioStatusPlan.automatic(duration)
            assertEquals(0L, parts.first().startMs)
            assertEquals(duration, parts.last().endMs)
            assertEquals(duration, parts.sumOf { it.durationMs })
            assertTrue(parts.size <= 20)
            assertTrue(parts.all { it.durationMs in 1_000..30_000 })
            assertTrue(parts.zipWithNext().all { (a, b) -> a.endMs == b.startMs })
        }
    }

    @Test fun selectedRangeRetainsTheRequestedOffset() {
        assertEquals(listOf(AudioStatusPlan.Range(45_500, 70_250)), AudioStatusPlan.selected(90_000, 45_500, 70_250))
    }

    @Test fun invalidRangesAndOversizePlansAreRejected() {
        for ((duration, start, end) in listOf(Triple(90_000L, -1L, 1000L), Triple(90_000L, 0L, 30_001L),
            Triple(90_000L, 89_000L, 90_001L), Triple(90_000L, 1000L, 1000L), Triple(90_000L, 0L, 999L))) {
            assertTrue(runCatching { AudioStatusPlan.selected(duration, start, end) }.isFailure)
        }
        assertTrue(runCatching { AudioStatusPlan.automatic(999) }.isFailure)
        assertTrue(runCatching { AudioStatusPlan.automatic(600_001) }.isFailure)
    }
}
