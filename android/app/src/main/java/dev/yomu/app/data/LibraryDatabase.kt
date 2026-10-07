package dev.yomu.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reading_items", indices = [Index("updatedAt"), Index("sourceUrl")])
data class ReadingItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceType: String,
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    val originalFilePath: String? = null,
    val normalizedText: String = "",
    val importState: String = "ready",
    val warning: String? = null,
    val inToRead: Boolean = true,
    val importedAt: Long,
    val updatedAt: Long = importedAt,
    val estimatedSecondsAtOneX: Int = 0,
    val contentVersion: Int = 1,
    @ColumnInfo(defaultValue = "0") val manualOrder: Long = importedAt,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
)

@Entity(tableName = "playlists", indices = [Index("name", unique = true)])
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val sortOrder: Int,
)

@Entity(
    tableName = "playlist_memberships",
    primaryKeys = ["playlistId", "itemId"],
    indices = [Index("itemId"), Index(value = ["playlistId", "sortOrder"])],
)
data class PlaylistMembershipEntity(
    val playlistId: String,
    val itemId: String,
    val sortOrder: Int,
    val addedAt: Long,
)

@Entity(tableName = "up_next", indices = [Index(value = ["position"], unique = true)])
data class QueueEntryEntity(
    @PrimaryKey val itemId: String,
    val position: Int,
    val addedAt: Long,
)

@Entity(tableName = "reading_cursors")
data class ReadingCursorEntity(
    @PrimaryKey val itemId: String,
    val anchor: String,
    val updatedAt: Long,
    val deviceLabel: String,
)

@Entity(tableName = "reader_defaults")
data class ReaderDefaultsEntity(
    @PrimaryKey val id: String = "global",
    val font: String = "serif",
    val size: Int = 20,
    val spacing: Double = 1.7,
    val schemaVersion: Int = 1,
)

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val itemId: String,
    val sentenceIndex: Int = 0,
    val characterOffset: Int = 0,
    val playbackSpeed: Float = 1f,
    val completed: Boolean = false,
    val lastListenedAt: Long = 0L,
)

@Entity(tableName = "read_history", indices = [Index("completedAt")])
data class ReadHistoryEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val completedAt: Long,
)

data class HistoryRow(
    val id: String,
    val itemId: String,
    val title: String,
    val sourceName: String?,
    val completedAt: Long,
    val estimatedSecondsAtOneX: Int,
)

data class ReadingListRow(
    val id: String,
    val title: String,
    val sourceType: String,
    val sourceName: String?,
    val importState: String,
    val warning: String?,
    val estimatedSecondsAtOneX: Int,
    val sentenceIndex: Int,
    val characterOffset: Int,
    val completed: Boolean,
    val importedAt: Long,
    val manualOrder: Long,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM reading_items WHERE inToRead = 1 ORDER BY importedAt DESC")
    fun observeToRead(): Flow<List<ReadingItemEntity>>

    @Query("SELECT reading_items.id, reading_items.title, reading_items.sourceType, reading_items.sourceName, reading_items.importState, reading_items.warning, reading_items.estimatedSecondsAtOneX, COALESCE(reading_progress.sentenceIndex, 0) AS sentenceIndex, COALESCE(reading_progress.characterOffset, 0) AS characterOffset, COALESCE(reading_progress.completed, 0) AS completed, reading_items.importedAt, reading_items.manualOrder FROM reading_items LEFT JOIN reading_progress ON reading_items.id = reading_progress.itemId WHERE reading_items.inToRead = 1 ORDER BY reading_items.importedAt DESC")
    fun observeToReadRows(): Flow<List<ReadingListRow>>

    @Query("SELECT reading_items.id, reading_items.title, reading_items.sourceType, reading_items.sourceName, reading_items.importState, reading_items.warning, reading_items.estimatedSecondsAtOneX, COALESCE(reading_progress.sentenceIndex, 0) AS sentenceIndex, COALESCE(reading_progress.characterOffset, 0) AS characterOffset, COALESCE(reading_progress.completed, 0) AS completed, reading_items.importedAt, reading_items.manualOrder FROM reading_items LEFT JOIN reading_progress ON reading_items.id = reading_progress.itemId WHERE reading_items.archived = 1 ORDER BY reading_items.updatedAt DESC")
    fun observeArchivedRows(): Flow<List<ReadingListRow>>

    @Query("SELECT * FROM reading_items ORDER BY updatedAt DESC")
    fun observeAllItems(): Flow<List<ReadingItemEntity>>

    @Query("SELECT * FROM reading_items WHERE id = :itemId LIMIT 1")
    fun observeItem(itemId: String): Flow<ReadingItemEntity?>

    @Query("SELECT * FROM reading_items WHERE id = :itemId LIMIT 1")
    suspend fun getItem(itemId: String): ReadingItemEntity?

    @Query("SELECT id FROM reading_items WHERE sourceType = 'url' AND importState IN ('pending', 'fetching')")
    suspend fun getPendingUrlImportIds(): List<String>

    @Upsert
    suspend fun upsertItem(item: ReadingItemEntity)

    @Query("DELETE FROM reading_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query("DELETE FROM reading_cursors WHERE itemId = :itemId")
    suspend fun deleteReadingCursor(itemId: String)

    @Query("SELECT * FROM up_next ORDER BY position ASC")
    fun observeUpNext(): Flow<List<QueueEntryEntity>>

    @Query("SELECT * FROM up_next ORDER BY position ASC")
    suspend fun getUpNext(): List<QueueEntryEntity>

    @Query("SELECT reading_items.* FROM reading_items INNER JOIN up_next ON reading_items.id = up_next.itemId ORDER BY up_next.position ASC")
    fun observeUpNextItems(): Flow<List<ReadingItemEntity>>

    @Query("SELECT reading_items.id, reading_items.title, reading_items.sourceType, reading_items.sourceName, reading_items.importState, reading_items.warning, reading_items.estimatedSecondsAtOneX, COALESCE(reading_progress.sentenceIndex, 0) AS sentenceIndex, COALESCE(reading_progress.characterOffset, 0) AS characterOffset, COALESCE(reading_progress.completed, 0) AS completed, reading_items.importedAt, reading_items.manualOrder FROM reading_items INNER JOIN up_next ON reading_items.id = up_next.itemId LEFT JOIN reading_progress ON reading_items.id = reading_progress.itemId ORDER BY up_next.position ASC")
    fun observeUpNextRows(): Flow<List<ReadingListRow>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM up_next")
    suspend fun lastQueuePosition(): Int

    @Upsert
    suspend fun upsertQueueEntries(entries: List<QueueEntryEntity>)

    @Insert
    suspend fun insertQueueEntries(entries: List<QueueEntryEntity>)

    @Query("DELETE FROM up_next WHERE itemId = :itemId")
    suspend fun removeFromUpNext(itemId: String)

    @Query("DELETE FROM up_next WHERE itemId = :itemId")
    suspend fun deleteQueueItem(itemId: String)

    @Query("DELETE FROM up_next")
    suspend fun clearUpNext()

    @Query("SELECT * FROM reading_progress WHERE itemId = :itemId LIMIT 1")
    fun observeProgress(itemId: String): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE itemId = :itemId LIMIT 1")
    suspend fun getProgress(itemId: String): ReadingProgressEntity?

    @Query("SELECT * FROM reading_cursors WHERE itemId = :itemId LIMIT 1")
    fun observeReadingCursor(itemId: String): Flow<ReadingCursorEntity?>

    @Upsert
    suspend fun upsertReadingCursor(cursor: ReadingCursorEntity)

    @Query("SELECT * FROM reader_defaults WHERE id = 'global' LIMIT 1")
    fun observeReaderDefaults(): Flow<ReaderDefaultsEntity?>

    @Upsert
    suspend fun upsertReaderDefaults(defaults: ReaderDefaultsEntity)

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM read_history ORDER BY completedAt DESC")
    fun observeHistory(): Flow<List<ReadHistoryEntity>>

    @Upsert
    suspend fun addHistory(entry: ReadHistoryEntity)

    @Query("DELETE FROM read_history WHERE id = :historyId")
    suspend fun removeHistory(historyId: String)

    @Query("DELETE FROM read_history")
    suspend fun clearHistory()

    @Query("SELECT read_history.id AS id, reading_items.id AS itemId, reading_items.title AS title, reading_items.sourceName AS sourceName, read_history.completedAt AS completedAt, reading_items.estimatedSecondsAtOneX AS estimatedSecondsAtOneX FROM read_history INNER JOIN reading_items ON reading_items.id = read_history.itemId ORDER BY read_history.completedAt DESC")
    fun observeHistoryRows(): Flow<List<HistoryRow>>

    @Query("SELECT * FROM playlists ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylist(playlistId: String): PlaylistEntity?

    @Query("SELECT * FROM playlist_memberships WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    fun observePlaylistMemberships(playlistId: String): Flow<List<PlaylistMembershipEntity>>

    @Query("SELECT reading_items.id, reading_items.title, reading_items.sourceType, reading_items.sourceName, reading_items.importState, reading_items.warning, reading_items.estimatedSecondsAtOneX, COALESCE(reading_progress.sentenceIndex, 0) AS sentenceIndex, COALESCE(reading_progress.characterOffset, 0) AS characterOffset, COALESCE(reading_progress.completed, 0) AS completed, reading_items.importedAt, reading_items.manualOrder FROM playlist_memberships INNER JOIN reading_items ON reading_items.id = playlist_memberships.itemId LEFT JOIN reading_progress ON reading_items.id = reading_progress.itemId WHERE playlist_memberships.playlistId = :playlistId ORDER BY playlist_memberships.sortOrder ASC")
    fun observePlaylistRows(playlistId: String): Flow<List<ReadingListRow>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM playlist_memberships WHERE playlistId = :playlistId")
    suspend fun nextMembershipOrder(playlistId: String): Int

    @Upsert
    suspend fun upsertMembership(membership: PlaylistMembershipEntity)

    @Query("DELETE FROM playlist_memberships WHERE playlistId = :playlistId AND itemId = :itemId")
    suspend fun removeMembership(playlistId: String, itemId: String)

    @Query("DELETE FROM playlist_memberships WHERE itemId = :itemId")
    suspend fun removeItemFromAllPlaylists(itemId: String)

    @Query("DELETE FROM read_history WHERE itemId = :itemId")
    suspend fun removeItemFromHistory(itemId: String)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM playlists")
    suspend fun nextPlaylistOrder(): Int

    @Query("SELECT * FROM reading_items WHERE inToRead = 1 ORDER BY manualOrder DESC, importedAt DESC")
    suspend fun getToReadInManualOrder(): List<ReadingItemEntity>

    @Query("SELECT reading_items.* FROM reading_items INNER JOIN up_next ON reading_items.id = up_next.itemId ORDER BY up_next.position ASC")
    suspend fun getUpNextItems(): List<ReadingItemEntity>

    @Query("SELECT * FROM playlists ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun getPlaylists(): List<PlaylistEntity>

    @Query("SELECT reading_items.* FROM playlist_memberships INNER JOIN reading_items ON reading_items.id = playlist_memberships.itemId WHERE playlist_memberships.playlistId = :playlistId ORDER BY playlist_memberships.sortOrder ASC")
    suspend fun getPlaylistItems(playlistId: String): List<ReadingItemEntity>

    @Query("UPDATE reading_items SET manualOrder = :order WHERE id = :itemId")
    suspend fun setManualOrder(itemId: String, order: Long)

    @Query("SELECT COALESCE(MAX(manualOrder), 0) + 1024 FROM reading_items")
    suspend fun nextManualOrder(): Long
}

@Entity(tableName = "sync_baselines")
data class SyncBaselineEntity(@PrimaryKey val key: String, val payload: String)

@Database(
    entities = [
        ReadingItemEntity::class,
        PlaylistEntity::class,
        PlaylistMembershipEntity::class,
        QueueEntryEntity::class,
        ReadingProgressEntity::class,
        ReadingCursorEntity::class,
        ReaderDefaultsEntity::class,
        ReadHistoryEntity::class,
        SyncBaselineEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile private var instance: LibraryDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reading_items ADD COLUMN manualOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE reading_items SET manualOrder = importedAt")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reading_items ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_baselines (`key` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`key`))")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reading_cursors (itemId TEXT NOT NULL PRIMARY KEY, anchor TEXT NOT NULL, updatedAt INTEGER NOT NULL, deviceLabel TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS reader_defaults (id TEXT NOT NULL PRIMARY KEY, font TEXT NOT NULL, size INTEGER NOT NULL, spacing REAL NOT NULL, schemaVersion INTEGER NOT NULL)")
            }
        }

        fun get(context: Context): LibraryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LibraryDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
        }

        const val DATABASE_NAME = "yomu-library.db"
    }
}
