package dev.yomu.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProtectedPdfImportTest {
    private lateinit var database: LibraryDatabase
    private lateinit var importer: ContentImporter
    private var importedFile: File? = null

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
        importer = ContentImporter(context, database.libraryDao())
    }

    @After fun tearDown() {
        importedFile?.delete()
        database.close()
    }

    @Test fun protectedPdfCanBeRetriedWithPasswordWithoutResharing() = runBlocking {
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets.open("protected-fixture.pdf")
        val itemId = importer.importDocument("protected.pdf", "application/pdf") { fixture }
        val failed = database.libraryDao().getItem(itemId)!!
        importedFile = failed.originalFilePath?.let(::File)

        assertEquals("failed", failed.importState)
        assertTrue(failed.warning.orEmpty().contains("password protected"))
        assertTrue(importedFile?.isFile == true)

        importer.retryPdfPassword(itemId, "yomu-test-password")

        val ready = database.libraryDao().getItem(itemId)!!
        assertEquals("ready", ready.importState)
        assertTrue(ready.normalizedText.contains("password unlock and text extraction"))
        assertTrue(ready.originalFilePath == failed.originalFilePath)
    }
}
