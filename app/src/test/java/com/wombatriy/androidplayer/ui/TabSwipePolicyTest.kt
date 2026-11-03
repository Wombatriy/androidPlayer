package com.wombatriy.androidplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TabSwipePolicyTest {
    @Test fun swipeLeftAdvancesToNextTab() {
        assertEquals(1, TabSwipePolicy.direction(-120f, 8f, 210L, 80f))
    }

    @Test fun swipeRightReturnsToPreviousTab() {
        assertEquals(-1, TabSwipePolicy.direction(115f, -12f, 250L, 80f))
    }

    @Test fun scrollingVerticallyDoesNotChangeTabs() {
        assertEquals(0, TabSwipePolicy.direction(35f, 160f, 200L, 80f))
        assertEquals(0, TabSwipePolicy.direction(120f, 105f, 220L, 80f))
    }

    @Test fun shortAndLongPressGesturesAreIgnored() {
        assertEquals(0, TabSwipePolicy.direction(-60f, 0f, 180L, 80f))
        assertEquals(0, TabSwipePolicy.direction(-180f, 0f, 900L, 80f))
    }

    @Test fun invalidDurationIsIgnored() {
        assertEquals(0, TabSwipePolicy.direction(-110f, 0f, -1L, 80f))
    }
}
