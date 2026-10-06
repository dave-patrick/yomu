package dev.yomu.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncScheduleTest {
    @Test fun manualModeNeverRunsAutomatically() {
        assertFalse(automaticSyncDue(0, 0, Long.MAX_VALUE))
    }
    @Test fun manualSyncRestartsTheAutomaticInterval() {
        val manualSyncAt = 1_000_000L
        assertFalse(automaticSyncDue(15, manualSyncAt, manualSyncAt + 899_999))
        assertTrue(automaticSyncDue(15, manualSyncAt, manualSyncAt + 900_000))
    }
    @Test fun shorterForegroundIntervalsAndClockRollback() {
        assertTrue(automaticSyncDue(1, 0, 60_000))
        assertFalse(automaticSyncDue(1, 100_000, 90_000))
    }
}
