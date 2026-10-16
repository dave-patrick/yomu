package dev.yomu.app.data

import org.junit.Assert.*
import org.junit.Test

class AutoArchiveTest {
    @Test fun archivesOnlyAtThreshold() {
        assertFalse(reachedArchiveThreshold(979, 1000))
        assertTrue(reachedArchiveThreshold(980, 1000))
        assertTrue(reachedArchiveThreshold(1000, 1000))
    }
    @Test fun handlesEmptyAndLargeDocuments() {
        assertFalse(reachedArchiveThreshold(0, 0))
        assertFalse(reachedArchiveThreshold(-1, 100))
        assertTrue(reachedArchiveThreshold(Int.MAX_VALUE, Int.MAX_VALUE))
        assertFalse(reachedArchiveThreshold(1_900_000_000, Int.MAX_VALUE))
    }
}
