package dev.yomu.app.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SpeechCpuGuardTest {
    @Test fun leaseIsBoundedAndReleasedAfterPreparation() = runBlocking {
        var held = false
        var timeout = 0L
        val guard = SpeechCpuGuard({ timeout = it; held = true }, { held = false })
        assertEquals("audio", guard.run { assertTrue(held); "audio" })
        assertFalse(held)
        assertEquals(300_000L, timeout)
    }
    @Test fun failureAndCancellationReleaseTheLease() = runBlocking {
        for (error in listOf(IllegalStateException("Synthesis failed"), CancellationException("Stopped"))) {
            var held = false
            val guard = SpeechCpuGuard({ held = true }, { held = false })
            try { guard.run { throw error }; fail("Expected failure") }
            catch (actual: Exception) { assertSame(error, actual) }
            assertFalse(held)
        }
    }
}
