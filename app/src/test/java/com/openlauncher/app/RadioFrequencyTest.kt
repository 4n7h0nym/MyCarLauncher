package com.openlauncher.app

import com.openlauncher.app.data.RadioFrequency
import org.junit.Assert.*
import org.junit.Test

class RadioFrequencyTest {
    @Test fun fmUsesKhzWithoutFloatTruncation() {
        assertEquals(107300, RadioFrequency.toKhz("FM1", 107.3f))
        assertEquals(89100, RadioFrequency.toKhz("FM2", 89.1f))
    }
    @Test fun amRemainsKhz() { assertEquals(810, RadioFrequency.toKhz("AM", 810f)) }
    @Test fun transientMixedBandAndFrequencyAreNotPersisted() {
        assertFalse(RadioFrequency.matchesBand(0, 107300))
        assertFalse(RadioFrequency.matchesBand(1, 810))
        assertFalse(RadioFrequency.matchesBand(null, 107300))
        assertTrue(RadioFrequency.matchesBand(0, 810))
        assertTrue(RadioFrequency.matchesBand(1, 107300))
    }
    @Test fun invalidOrOverflowFrequencyIsRejected() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f, Float.MAX_VALUE).forEach {
            assertNull(RadioFrequency.toKhz("FM1", it))
        }
    }
}
