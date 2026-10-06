package dev.yomu.app.playback

import org.junit.Assert.*
import org.junit.Test

class ReaderSelectionTest {
    @Test fun completionGapStillAdvancesOpenReader() {
        val reader = ReaderSelection()
        reader.open("first")
        reader.playbackChanged("first")
        reader.playbackChanged(null)
        assertEquals("first", reader.target.value)
        reader.playbackChanged("second")
        assertEquals("second", reader.target.value)
        reader.playbackChanged(null)
        reader.playbackChanged("third")
        assertEquals("third", reader.target.value)
    }
    @Test fun closedReaderIsNotReopenedByQueueAdvance() {
        val reader = ReaderSelection("first")
        reader.open("first")
        reader.close()
        reader.playbackChanged(null)
        reader.playbackChanged("second")
        assertNull(reader.target.value)
    }
    @Test fun BrowsingAnotherArticleIsPreserved() {
        val reader = ReaderSelection("first")
        reader.open("other")
        reader.playbackChanged("second")
        assertEquals("other", reader.target.value)
    }
    @Test fun returnFromPlayNowFollowsTheResumedArticle() {
        val reader = ReaderSelection("queue")
        reader.open("queue")
        reader.playbackChanged("interruption")
        assertEquals("interruption", reader.target.value)
        reader.playbackChanged(null)
        reader.playbackChanged("queue")
        assertEquals("queue", reader.target.value)
    }
}
