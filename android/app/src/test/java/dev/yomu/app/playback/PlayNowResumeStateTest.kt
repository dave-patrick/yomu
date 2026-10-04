package dev.yomu.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayNowResumeStateTest {
    @Test fun playNowReturnsToInterruptedPositionBeforeQueue() {
        val state = PlayNowResumeState()
        state.onStart(currentItemId = "article-a", currentSentence = 12, requestedItemId = "article-b")

        assertEquals(ResumePoint("article-a", 12), state.takeAfterCompletion("article-b"))
        assertNull(state.point)
    }

    @Test fun startingSavedInterruptedItemClearsReturnPointInsteadOfLooping() {
        val state = PlayNowResumeState(ResumePoint("article-a", 12))
        state.onStart(currentItemId = "article-b", currentSentence = 4, requestedItemId = "article-a")

        assertNull(state.takeAfterCompletion("article-a"))
    }

    @Test fun repeatedPlayNowKeepsOriginalReturnTargetAndDoesNotCaptureReturnPlayback() {
        val state = PlayNowResumeState()
        state.onStart("article-a", 12, "article-b")
        state.onStart("article-b", 4, "article-c")
        state.onStart("article-c", 2, "article-a", isReturning = true)

        assertEquals(ResumePoint("article-a", 12), state.takeAfterCompletion("article-c"))
    }
}
