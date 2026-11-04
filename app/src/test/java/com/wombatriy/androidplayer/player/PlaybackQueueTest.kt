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

    @Test fun resyncingBpmOrderPreservesCurrentSelection() {
        val q = makeQueue()
        q.updateOrder(listOf("three", "one", "two")) { a, b -> a == b }
        assertEquals("two", q.current)
        assertEquals(2, q.position)
        assertEquals("one", q.previous())
    }

    @Test fun missingCurrentTrackDoesNotDiscardExistingQueue() {
        val q = makeQueue()
        q.updateOrder(listOf("unrelated")) { a, b -> a == b }
        assertEquals("two", q.current)
        assertEquals(3, q.size)
    }

    @Test fun tappingQueueRowChangesSelectionWithoutResorting() {
        val q = makeQueue()
        assertEquals("three", q.selectAt(2))
        assertEquals(2, q.position)
        assertNull(q.next())
        assertEquals("two", q.previous())
    }

    @Test fun selectingAnInvalidQueueIndexDoesNothing() {
        val q = makeQueue()
        assertNull(q.selectAt(-1))
        assertNull(q.selectAt(42))
        assertEquals("two", q.current)
    }

    @Test fun selectingDoesNotMutateOriginalList() {
        val original = mutableListOf("one", "two")
        val q = PlaybackQueue<String>()
        q.select("one", original)
        original.clear()
        assertEquals("two", q.next())
    }
    @Test fun deletingNonCurrentTrackKeepsSelectionAndOrder() {
        val q = makeQueue()
        assertTrue(q.removeReference("one"))
        assertEquals(listOf("two", "three"), q.snapshot())
        assertEquals(0, q.position)
        assertEquals("two", q.current)
        assertEquals("three", q.next())
    }

    @Test fun deletingSelectedTrackClearsSelectionButPreservesOtherEntries() {
        val q = makeQueue()
        assertTrue(q.removeReference("two"))
        assertEquals(-1, q.position)
        assertNull(q.current)
        assertEquals(listOf("one", "three"), q.snapshot())
        assertNull(q.next())
        assertEquals("three", q.selectAt(1))
    }

    @Test fun deletionMatchesStaleEntryByIdentityRatherThanLabel() {
        data class Item(val uri: Int, val name: String)
        val q = PlaybackQueue<Item>()
        q.select(Item(2, "old"), listOf(Item(1, "a"), Item(2, "old"), Item(3, "c")))
        assertTrue(q.removeReference(Item(2, "renamed")) { a, b -> a.uri == b.uri })
        assertNull(q.current)
        assertEquals(listOf(1, 3), q.snapshot().map { it.uri })
    }

    @Test fun deletingMissingReferenceIsNoOp() {
        val q = makeQueue()
        assertFalse(q.removeReference("nonexistent"))
        assertEquals("two", q.current)
        assertEquals(3, q.size)
    }

    @Test fun queueCanBeRefreshedAfterCurrentTrackWasDeleted() {
        val q = makeQueue()
        q.removeReference("two")
        q.updateOrder(listOf("three", "one")) { a, b -> a == b }
        assertEquals(listOf("three", "one"), q.snapshot())
        assertNull(q.current)
        assertEquals(-1, q.position)
        assertNull(q.next())
        assertEquals("three", q.selectAt(0))
    }

}
