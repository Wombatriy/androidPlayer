package com.wombatriy.androidplayer.player

import org.junit.Assert.*
import org.junit.Test

class TempoMathTest {
    @Test fun normalPercentageInputs() {
        assertEquals(25, TempoMath.parseClampedPercent("25"))
        assertEquals(100, TempoMath.parseClampedPercent(" 100 "))
        assertEquals(150, TempoMath.parseClampedPercent("150"))
    }

    @Test fun outOfBoundsAreClamped() {
        assertEquals(25, TempoMath.parseClampedPercent("0"))
        assertEquals(25, TempoMath.parseClampedPercent("24"))
        assertEquals(150, TempoMath.parseClampedPercent("151"))
        assertEquals(150, TempoMath.parseClampedPercent("160"))
        assertEquals(150, TempoMath.parseClampedPercent("999999999999"))
        assertEquals(100, TempoMath.parseClampedPercent("000100"))
    }

    @Test fun emptyOrNonNumericMeansNoChange() {
        listOf("", "  ", "abc", "-1", "100.5").forEach {
            assertNull(TempoMath.parseClampedPercent(it))
        }
    }

    @Test fun speedConversion() {
        assertEquals(0.25f, TempoMath.toSpeed(25), 0.0001f)
        assertEquals(1.0f, TempoMath.toSpeed(100), 0.0001f)
        assertEquals(1.5f, TempoMath.toSpeed(150), 0.0001f)
        assertEquals(81, TempoMath.toPercent(0.81f))
    }

    @Test fun scaledBpmDisplaysOneDecimalWhenNecessary() {
        assertEquals("102.4", TempoMath.effectiveBpm(128, 80))
        assertEquals("160", TempoMath.effectiveBpm(128, 125))
        assertEquals("118.8", TempoMath.effectiveBpm(125, 95))
        assertEquals("32", TempoMath.effectiveBpm(128, 25))
        assertEquals("192", TempoMath.effectiveBpm(128, 150))
    }

    @Test fun outOfRangeSpeedCannotBeUsed() {
        assertThrows(IllegalArgumentException::class.java) { TempoMath.toSpeed(24) }
        assertThrows(IllegalArgumentException::class.java) { TempoMath.effectiveBpm(120, 151) }
    }
}
