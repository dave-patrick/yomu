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
    @Test fun readingCursorMigrationPreservesListeningAndLibrary() {
        val name = "yomu-reading-cursor-migration.db"
        helper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO reading_items (id,title,sourceType,normalizedText,importState,inToRead,importedAt,updatedAt,estimatedSecondsAtOneX,contentVersion,manualOrder,archived) VALUES ('saved','Keep me','txt','Original text','ready',1,123,123,60,1,123,0)")
            execSQL("INSERT INTO reading_progress (itemId,sentenceIndex,characterOffset,playbackSpeed,completed,lastListenedAt) VALUES ('saved',2,12,1.0,0,123)")
            close()
        }
        helper.runMigrationsAndValidate(name, 5, true, LibraryDatabase.MIGRATION_4_5).use { db ->
            db.query("SELECT sentenceIndex,characterOffset FROM reading_progress WHERE itemId='saved'").use { cursor ->
                assertEquals(true,cursor.moveToFirst());assertEquals(2,cursor.getInt(0));assertEquals(12,cursor.getInt(1))
            }
            db.execSQL("INSERT INTO reader_defaults VALUES ('global','serif',20,1.7,1)")
            db.execSQL("INSERT INTO reading_cursors VALUES ('saved','{}',123,'Phone')")
        }
    }

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

    @Test fun syncMigrationPreservesPhoneLibrary() {
        val name = "yomu-sync-migration-test.db"
        helper.createDatabase(name, 3).apply {
            execSQL("INSERT INTO reading_items (id,title,sourceType,normalizedText,importState,inToRead,importedAt,updatedAt,estimatedSecondsAtOneX,contentVersion,manualOrder,archived) VALUES ('saved','Keep me','txt','Original text','ready',1,123,123,60,1,123,0)")
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, LibraryDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT title,normalizedText FROM reading_items WHERE id='saved'").use { cursor ->
                assertEquals(true,cursor.moveToFirst())
                assertEquals("Keep me",cursor.getString(0));assertEquals("Original text",cursor.getString(1))
            }
            db.execSQL("INSERT INTO sync_baselines VALUES ('reading_items/saved','{}')")
        }
    }
}
