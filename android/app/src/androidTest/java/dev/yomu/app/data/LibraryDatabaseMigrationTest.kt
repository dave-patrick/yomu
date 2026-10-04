package dev.yomu.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDatabaseMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LibraryDatabase::class.java)

    @Test fun migrationAddsManualOrderAndPreservesExistingLibraryItems() {
        val name = "yomu-migration-test.db"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO reading_items (id, title, sourceType, sourceUrl, sourceName, originalFilePath, normalizedText, importState, warning, inToRead, importedAt, updatedAt, estimatedSecondsAtOneX, contentVersion) VALUES ('existing', 'Saved article', 'url', 'https://example.test', NULL, NULL, 'Text stays', 'ready', NULL, 1, 12345, 12345, 60, 1)")
            close()
        }

        helper.runMigrationsAndValidate(name, 2, true, LibraryDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT title, normalizedText, manualOrder FROM reading_items WHERE id='existing'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("Saved article", cursor.getString(0))
                assertEquals("Text stays", cursor.getString(1))
                assertEquals(12345L, cursor.getLong(2))
            }
        }
    }
}
