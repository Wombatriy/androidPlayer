package com.wombatriy.androidplayer.bpm

import org.junit.Assert.*
import org.junit.Test

class BatchBpmPolicyTest {
    @Test fun onlySavedDetectionsAreSkipped() {
        // A filename BPM by itself must not count as a completed detection.
        val inputs = listOf("filenameBpmOnly", "savedDetected", "missing", "savedManualOnly")
        val plan = BatchBpmPolicy.selectMissing(inputs) { it == "savedDetected" }
        assertEquals(1, plan.skipped)
        assertEquals(listOf("filenameBpmOnly", "missing", "savedManualOnly"), plan.pending)
    }

    @Test fun allAlreadyDetectedProducesEmptyBatch() {
        val plan = BatchBpmPolicy.selectMissing(listOf(1, 2, 3)) { true }
        assertEquals(3, plan.skipped)
        assertTrue(plan.pending.isEmpty())
    }

    @Test fun emptyListIsValid() {
        val plan = BatchBpmPolicy.selectMissing(emptyList<String>()) { false }
        assertEquals(0, plan.skipped)
        assertTrue(plan.pending.isEmpty())
    }

    @Test fun progressIsClampedAndMonotonic() {
        assertEquals(0, BatchBpmPolicy.progressThousand(3, 0))
        assertEquals(0, BatchBpmPolicy.progressThousand(-10, 100))
        assertEquals(250, BatchBpmPolicy.progressThousand(25, 100))
        assertEquals(1000, BatchBpmPolicy.progressThousand(100, 100))
        assertEquals(1000, BatchBpmPolicy.progressThousand(120, 100))
        assertEquals(333, BatchBpmPolicy.progressThousand(1, 3))
    }
}
