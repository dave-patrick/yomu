package dev.yomu.app.playback

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test

class SentenceLookAheadTest {
    @Test fun preparesAheadWithBoundedConcurrencyAndReusesRequests() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val buffer = SentenceLookAhead<String>(scope, 2)
            val started = Channel<Int>(Channel.UNLIMITED)
            val gates = List(3) { CompletableDeferred<Unit>() }
            val requests = (0..2).map { i -> buffer.get(i) { started.send(i); gates[i].await(); "sentence$i" } }
            withTimeout(5000) { started.receive(); started.receive() }
            assertTrue(started.tryReceive().isFailure)
            assertSame(requests[0], buffer.get(0) { error("Duplicate synthesis") })
            gates[1].complete(Unit)
            assertEquals("sentence1", requests[1].await())
            assertEquals(2, withTimeout(5000) { started.receive() })
            gates[0].complete(Unit); gates[2].complete(Unit)
            assertEquals(listOf("sentence0", "sentence1", "sentence2"), requests.map { it.await() })
        } finally { scope.cancel() }
    }

    @Test fun voiceChangeCancelsOldAudioAndCreatesFreshRequests() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val buffer = SentenceLookAhead<String>(scope)
            val started = CompletableDeferred<Unit>()
            val old = buffer.get(3) { started.complete(Unit); awaitCancellation() }
            withTimeout(5000) { started.await() }
            val epoch = buffer.epoch
            buffer.reset()
            assertTrue(old.isCancelled)
            assertNotEquals(epoch, buffer.epoch)
            assertEquals("new voice", buffer.get(3) { "new voice" }.await())
        } finally { scope.cancel() }
    }

    @Test fun playedSentencesAreDiscardedWithoutLosingFutureAudio() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val buffer = SentenceLookAhead<String>(scope)
            val previous = buffer.get(0) { "old" }
            val future = buffer.get(1) { "future" }
            buffer.discardBefore(1)
            assertSame(future, buffer.get(1) { error("Future lost") })
            assertNotSame(previous, buffer.get(0) { "fresh" })
        } finally { scope.cancel() }
    }
}
