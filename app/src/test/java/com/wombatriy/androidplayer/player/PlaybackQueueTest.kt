package com.wombatriy.androidplayer.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackQueueTest {
    private fun makeQueue(): PlaybackQueue<String> = PlaybackQueue<String>().apply {
        select("two", listOf("one", "two", "three"))
    }

    @Test fun selectedTrackKeepsItsPosition() {
        val q = makeQueue()
        assertEquals(1, q.position)
        assertEquals("two", q.current)
        assertTrue(q.hasPrevious)
        assertTrue(q.hasNext)
    }

    @Test fun nextStopsAtEnd() {
        val q = makeQueue()
        assertEquals("three", q.next())
        assertNull(q.next())
        assertEquals("three", q.current)
        assertFalse(q.hasNext)
    }

    @Test fun previousStopsAtBeginning() {
        val q = makeQueue()
        assertEquals("one", q.previous())
        assertNull(q.previous())
        assertEquals("one", q.current)
        assertFalse(q.hasPrevious)
    }

    @Test fun missingSelectedTrackCreatesSingleItemQueue() {
        val q = PlaybackQueue<String>()
        q.select("missing", listOf("one", "two"))
        assertEquals(1, q.size)
        assertEquals("missing", q.current)
        assertFalse(q.hasNext)
    }

    @Test fun renamingOneReferencePreservesCurrentQueuePosition() {
        val q = makeQueue()
        q.replaceReference("three", "three-renamed")
        assertEquals(1, q.position)
        assertEquals("two", q.current)
        assertEquals("three-renamed", q.next())
    }

    @Test fun selectingDoesNotMutateOriginalList() {
        val original = mutableListOf("one", "two")
        val q = PlaybackQueue<String>()
        q.select("one", original)
        original.clear()
        assertEquals("two", q.next())
    }
}
