package dev.yomu.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UrlImportTest {
    private lateinit var database: LibraryDatabase
    private lateinit var importer: ContentImporter
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
        importer = ContentImporter(context, database.libraryDao())
        server = MockWebServer().also { it.start() }
    }

    @After fun tearDown() {
        server.shutdown()
        database.close()
    }

    @Test fun savesLinkBeforeExtractionAndThenMarksArticleReady() = runBlocking {
        val articleText = "A test article with enough useful words to become readable. ".repeat(8)
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=utf-8")
            .setBody("<html><head><title>Article title</title></head><body><article><h1>Article title</h1><p>$articleText</p></article></body></html>"))

        val id = importer.saveUrlForLater(server.url("/article").toString())
        val saved = database.libraryDao().getItem(id)!!
        assertEquals("pending", saved.importState)
        assertTrue(saved.inToRead)

        importer.retryUrl(id)

        val ready = database.libraryDao().getItem(id)!!
        assertEquals("ready", ready.importState)
        assertEquals("Article title", ready.title)
        assertTrue(ready.normalizedText.contains("test article"))
    }

    @Test fun transientServerFailureStaysPendingForBackgroundRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        val id = importer.saveUrlForLater(server.url("/temporary").toString())

        val failure = runCatching { importer.retryUrl(id) }.exceptionOrNull()

        assertTrue(failure is ContentImporter.RetryableUrlImportException)
        val item = database.libraryDao().getItem(id)!!
        assertEquals("pending", item.importState)
        assertTrue(item.warning.orEmpty().contains("retry automatically"))
    }

    @Test fun permanentHttpFailureIsRetainedForManualRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val id = importer.saveUrlForLater(server.url("/missing").toString())

        val failure = runCatching { importer.retryUrl(id) }.exceptionOrNull()

        assertNotNull(failure)
        val item = database.libraryDao().getItem(id)!!
        assertEquals("failed", item.importState)
        assertTrue(item.warning.orEmpty().contains("404"))
    }
}
