package com.wmods.wppenhacer.xposed.core.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class StatusMediaFileTest {
    @get:Rule val temp = TemporaryFolder()

    private class Media(val cachedPath: String, val incomplete: File)
    private class Status(media: Media) {
        val cachedMedia = AtomicReference(listOf(media))
    }

    @Test fun findsCurrentStatusMediaWhenLegacyAccessorFails() {
        val expected = temp.newFile("status video.mp4").apply { writeText("complete") }
        val incomplete = temp.newFile("status.mp4.tmp").apply { writeText("partial") }
        val missing = File(temp.root, "missing.mp4")
        val root = Status(Media(expected.toPath().toUri().toString(), incomplete))

        assertEquals(expected, StatusMediaFile.resolve(listOf(missing), listOf(root)))
    }

    @Test fun rejectsEmptyAndPartialFiles() {
        val empty = temp.newFile("empty.jpg")
        val incomplete = temp.newFile("file.jpg.part").apply { writeText("partial") }
        assertNull(StatusMediaFile.resolve(listOf(empty), listOf(Status(Media("", incomplete)))))
    }
}
