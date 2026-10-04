package dev.yomu.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryRepositoryTest {
    private lateinit var database: LibraryDatabase
    private lateinit var repository: LibraryRepository
    private val dao get() = repository.dao

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
        repository = LibraryRepository(database)
    }

    @After fun tearDown() { database.close() }

    @Test fun queueDeduplicatesAndKeepsRequestedOrder() = runBlocking {
        insert("one"); insert("two")
        repository.addToUpNext("one")
        repository.addToUpNext("one")
        repository.addToUpNext("two")
        assertEquals(listOf("one", "two"), dao.getUpNext().map { it.itemId })

        repository.reorderQueue(listOf("two", "one"))
        assertEquals(listOf("two", "one"), dao.getUpNext().map { it.itemId })
        repository.moveQueueItem("one", -1)
        assertEquals(listOf("one", "two"), dao.getUpNext().map { it.itemId })
        repository.removeFromUpNext("two")
        assertEquals(listOf("one"), dao.getUpNext().map { it.itemId })
    }

    @Test fun savedItemsCanBeManuallyReorderedWithoutChangingTheirContent() = runBlocking {
        insert("first"); insert("second"); insert("third")

        repository.reorderToRead(listOf("first", "second", "third"))
        repository.moveToReadItem("third", -1)

        assertEquals(listOf("first", "third", "second"), dao.getToReadInManualOrder().map { it.id })
        assertEquals("A test article.", dao.getItem("third")?.normalizedText)
    }

    @Test fun playlistsCanShareItemAndReadingPosition() = runBlocking {
        insert("shared")
        dao.upsertPlaylist(PlaylistEntity("later", "Later", 1, 0))
        dao.upsertPlaylist(PlaylistEntity("research", "Research", 2, 1))
        repository.addToPlaylist("later", "shared")
        repository.addToPlaylist("research", "shared")
        dao.upsertProgress(ReadingProgressEntity("shared", sentenceIndex = 9, characterOffset = 431, playbackSpeed = 1.2f))

        assertEquals(listOf("shared"), dao.observePlaylistRows("later").first().map { it.id })
        assertEquals(listOf("shared"), dao.observePlaylistRows("research").first().map { it.id })
        assertEquals(9, dao.getProgress("shared")?.sentenceIndex)
    }

    @Test fun completedItemsLeaveToReadAndCanBeRemovedFromHistory() = runBlocking {
        insert("done")
        repository.addToUpNext("done")
        repository.completeItem("done")
        assertTrue(dao.observeToRead().first().isEmpty())
        assertTrue(dao.observeUpNext().first().isEmpty())
        val history = dao.observeHistoryRows().first()
        assertEquals(1, history.size)
        repository.removeHistoryEntry(history.single().id)
        assertFalse(dao.observeHistoryRows().first().any { it.itemId == "done" })
        assertEquals("done", dao.getItem("done")?.id)
    }

    private suspend fun insert(id: String) {
        dao.upsertItem(ReadingItemEntity(id, id, "url", sourceUrl = "https://example.test/$id", normalizedText = "A test article.", importedAt = 1, estimatedSecondsAtOneX = 10))
    }
}
