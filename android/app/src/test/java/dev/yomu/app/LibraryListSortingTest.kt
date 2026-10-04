package dev.yomu.app

import dev.yomu.app.data.ReadingListRow
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryListSortingTest {
    private val rows = listOf(
        row("oldest", 10, 100, 300),
        row("newest", 30, 300, 100),
        row("middle", 20, 200, 200),
    )

    @Test fun dateAndDurationSortsUseTheirDisplayedAttributes() {
        assertEquals(listOf("newest", "middle", "oldest"), sortLibraryRows(rows, "Newest", false).map { it.id })
        assertEquals(listOf("oldest", "middle", "newest"), sortLibraryRows(rows, "Oldest", false).map { it.id })
        assertEquals(listOf("oldest", "middle", "newest"), sortLibraryRows(rows, "Shortest", false).map { it.id })
        assertEquals(listOf("newest", "middle", "oldest"), sortLibraryRows(rows, "Longest", false).map { it.id })
    }

    @Test fun manualSortUsesSavedOrderAndLeavesQueueOrderAlone() {
        assertEquals(listOf("oldest", "middle", "newest"), sortLibraryRows(rows, "Manual", false).map { it.id })
        assertEquals(listOf("oldest", "newest", "middle"), sortLibraryRows(rows, "Manual", true).map { it.id })
    }

    private fun row(id: String, seconds: Int, importedAt: Long, manualOrder: Long) = ReadingListRow(
        id = id, title = id, sourceType = "url", sourceName = null, importState = "ready", warning = null,
        estimatedSecondsAtOneX = seconds, sentenceIndex = 0, characterOffset = 0, completed = false,
        importedAt = importedAt, manualOrder = manualOrder,
    )
}
