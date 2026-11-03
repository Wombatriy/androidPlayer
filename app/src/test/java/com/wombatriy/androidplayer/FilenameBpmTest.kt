package com.wombatriy.androidplayer

import org.junit.Assert.*
import org.junit.Test

class FilenameBpmTest {
    @Test fun parsesTemposAboveOld300Limit() {
        assertEquals(312, FilenameBpm.parse("312 - Jazz Artist - Tune.flac"))
        assertEquals(236, FilenameBpm.parse("236 - Jazz.mp3"))
        assertEquals(800, FilenameBpm.parse("800 - Demo.wav"))
        assertEquals(20, FilenameBpm.parse("20 - Demo.wav"))
    }
    @Test fun rejectsInvalidOrMissingPrefix() {
        assertNull(FilenameBpm.parse("19 - Tune.mp3"))
        assertNull(FilenameBpm.parse("801 - Tune.mp3"))
        assertNull(FilenameBpm.parse("Track - 312.mp3"))
    }
    @Test fun renameReplacesExistingPrefix() {
        assertEquals("128 - Artist - Title.flac",
            FilenameBpm.propose("312 - Artist - Title.flac", 128.0))
        assertEquals("129 - Artist - Title.MP3",
            FilenameBpm.propose("Artist - Title.MP3", 128.6))
        assertEquals("312 - Jazz.mp3", FilenameBpm.propose("312 - Jazz.mp3", 312.0))
    }
    @Test fun refusesUnrelatedCollisionButAllowsSameFilename() {
        assertTrue(FilenameBpm.collides("312 - Another.mp3", "Old.mp3",
            listOf("312 - ANOTHER.MP3", "Old.mp3")))
        assertFalse(FilenameBpm.collides("312 - Tune.mp3", "312 - Tune.mp3",
            listOf("312 - Tune.mp3")))
    }
}
