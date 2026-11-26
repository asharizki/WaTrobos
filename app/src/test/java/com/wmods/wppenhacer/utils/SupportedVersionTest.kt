package com.wmods.wppenhacer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedVersionTest {
    private val rules = listOf("2.26.41.xx", "2.26.42.74", "2.26.43.74", "2.26.43.76", "2.26.44.90", "2.26.44.92", "2.26.46.82")

    @Test fun displaysSpecificBuildsUnderTheirSeries() {
        assertEquals(
            "2.26.41.xx\n2.26.42.xx (build 74)\n2.26.43.xx (build 74, 76)\n2.26.44.xx (build 90, 92)\n2.26.46.xx (build 82)",
            SupportedVersion.displayList(rules)
        )
    }

    @Test fun acceptsOnlyVerified46Build() {
        assertTrue(SupportedVersion.matches("2.26.46.82", rules))
        assertFalse(SupportedVersion.matches("2.26.46.81", rules))
        assertFalse(SupportedVersion.matches("2.26.46.83", rules))
        assertFalse(SupportedVersion.matches("2.26.46.820", rules))
    }

    @Test fun acceptsOnlyTheExperimentalBuildAndExistingBranch() {
        assertTrue(SupportedVersion.matches("2.26.42.74", rules))
        assertTrue(SupportedVersion.matches("2.26.43.74", rules))
        assertTrue(SupportedVersion.matches("2.26.43.76", rules))
        assertFalse(SupportedVersion.matches("2.26.43.77", rules))
        assertTrue(SupportedVersion.matches("2.26.41.1", rules))
        assertFalse(SupportedVersion.matches("2.26.42.75", rules))
        assertFalse(SupportedVersion.matches("2.26.42.740", rules))
        assertFalse(SupportedVersion.matches("2.26.43.75", rules))
        assertFalse(SupportedVersion.matches("2.26.43.740", rules))
        assertTrue(SupportedVersion.matches("2.26.44.90", rules))
        assertTrue(SupportedVersion.matches("2.26.44.92", rules))
        assertFalse(SupportedVersion.matches("2.26.44.74", rules))
        assertFalse(SupportedVersion.matches("2.26.44.91", rules))
        assertFalse(SupportedVersion.matches("2.26.410.1", rules))
        assertFalse(SupportedVersion.matches(null, rules))
        assertFalse(SupportedVersion.matches("", rules))
    }
}
