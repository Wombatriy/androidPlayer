package com.wombatriy.androidplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LoopRangeTest {
    private val loop = LoopRange(startMs = 30_000L, endMs = 40_000L)

    @Test fun beforeASeeksToA() = assertEquals(30_000L, loop.constrainSeek(20_000L))
    @Test fun atAKeepsA() = assertEquals(30_000L, loop.constrainSeek(30_000L))
    @Test fun insideLoopKeepsPosition() = assertEquals(35_000L, loop.constrainSeek(35_000L))
    @Test fun atBRestartsA() = assertEquals(30_000L, loop.constrainSeek(40_000L))
    @Test fun afterBRestartsA() = assertEquals(30_000L, loop.constrainSeek(50_000L))
    @Test fun invertedMarkersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { LoopRange(40L, 30L) }
    }
}
