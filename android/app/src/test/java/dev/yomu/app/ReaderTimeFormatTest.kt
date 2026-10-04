package dev.yomu.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTimeFormatTest {
    @Test fun formatsElapsedAndRemainingTimeAtSeekBarEndpoints() {
        assertEquals("00:00", formatClockDuration(0))
        assertEquals("01:05", formatClockDuration(65))
        assertEquals("1:01:01", formatClockDuration(3_661))
        assertEquals("00:00", formatClockDuration(-4))
    }
}
