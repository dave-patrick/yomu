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
class LegacyWordImportTest {
    private lateinit var database: LibraryDatabase
    private lateinit var importer: ContentImporter
    private var importedFile: File? = null

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries().build()
        importer = ContentImporter(context, database.libraryDao())
    }

    @After fun tearDown() {
        importedFile?.delete()
        database.close()
    }

    @Test fun binaryWordDocumentIsRetainedAndExtracted() = runBlocking {
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets.open("sample-legacy.doc")
        val itemId = importer.importDocument("sample-legacy.doc", "application/msword") { fixture }
        val item = database.libraryDao().getItem(itemId)!!
        importedFile = item.originalFilePath?.let(::File)

        assertEquals("doc", item.sourceType)
        assertEquals("ready", item.importState)
        assertTrue(item.normalizedText.contains("I am a test document"))
        assertTrue(item.normalizedText.contains("This is page two"))
        assertTrue(importedFile?.isFile == true)
    }
}
