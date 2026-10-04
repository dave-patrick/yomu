package dev.yomu.app.data

import androidx.room.withTransaction
import java.io.File
import java.util.UUID

class LibraryRepository(private val database: LibraryDatabase) {
    val dao get() = database.libraryDao()

    suspend fun addToUpNext(itemId: String) = database.withTransaction {
        if (dao.getItem(itemId) == null) return@withTransaction
        if (dao.getUpNext().any { it.itemId == itemId }) return@withTransaction
        dao.insertQueueEntries(listOf(QueueEntryEntity(itemId, dao.lastQueuePosition() + 1, System.currentTimeMillis())))
    }

    suspend fun removeFromUpNext(itemId: String) = database.withTransaction {
        dao.deleteQueueItem(itemId)
        compactQueue()
    }

    suspend fun archiveItem(itemId: String) = database.withTransaction {
        val item = dao.getItem(itemId) ?: return@withTransaction
        dao.upsertItem(item.copy(archived = true, inToRead = false, updatedAt = System.currentTimeMillis()))
        dao.deleteQueueItem(itemId)
        compactQueue()
    }

    suspend fun restoreArchivedItem(itemId: String) = database.withTransaction {
        val item = dao.getItem(itemId) ?: return@withTransaction
        dao.upsertItem(item.copy(archived = false, inToRead = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun markItemRead(itemId: String) = database.withTransaction {
        val item = dao.getItem(itemId) ?: return@withTransaction
        val now = System.currentTimeMillis()
        val progress = dao.getProgress(itemId) ?: ReadingProgressEntity(itemId)
        dao.upsertProgress(progress.copy(completed = true, lastListenedAt = now))
        dao.upsertItem(item.copy(inToRead = false, updatedAt = now))
        dao.removeItemFromHistory(itemId)
        dao.addHistory(ReadHistoryEntity(UUID.randomUUID().toString(), itemId, now))
        dao.deleteQueueItem(itemId)
        compactQueue()
    }

    suspend fun markItemUnread(itemId: String) = database.withTransaction {
        val item = dao.getItem(itemId) ?: return@withTransaction
        val progress = dao.getProgress(itemId) ?: ReadingProgressEntity(itemId)
        dao.upsertProgress(progress.copy(completed = false))
        dao.upsertItem(item.copy(inToRead = true, archived = false, updatedAt = System.currentTimeMillis()))
        dao.removeItemFromHistory(itemId)
    }

    suspend fun compactQueue() {
        val ids = dao.getUpNext().map { it.itemId }
        dao.clearUpNext()
        dao.insertQueueEntries(ids.mapIndexed { index, id -> QueueEntryEntity(id, index, System.currentTimeMillis()) })
    }

    suspend fun reorderQueue(itemIds: List<String>) = database.withTransaction {
        val existing = dao.getUpNext().map { it.itemId }.toSet()
        val ordered = itemIds.distinct().filter { it in existing } + existing.filterNot { it in itemIds }
        dao.clearUpNext()
        dao.insertQueueEntries(ordered.mapIndexed { index, id -> QueueEntryEntity(id, index, System.currentTimeMillis()) })
    }

    suspend fun moveQueueItem(itemId: String, direction: Int) = database.withTransaction {
        val items = dao.getUpNext().map { it.itemId }.toMutableList()
        val index = items.indexOf(itemId)
        val target = (index + direction).coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (index >= 0 && target != index) {
            val item = items.removeAt(index)
            items.add(target, item)
            dao.clearUpNext()
            dao.insertQueueEntries(items.mapIndexed { order, id -> QueueEntryEntity(id, order, System.currentTimeMillis()) })
        }
    }

    suspend fun reorderToRead(itemIds: List<String>) = database.withTransaction {
        val existing = dao.getToReadInManualOrder().map { it.id }.toSet()
        val ordered = itemIds.distinct().filter { it in existing } + existing.filterNot { it in itemIds }
        val high = dao.nextManualOrder() + ordered.size * 1024L
        ordered.forEachIndexed { index, id -> dao.setManualOrder(id, high - index * 1024L) }
    }

    suspend fun moveToReadItem(itemId: String, direction: Int) = database.withTransaction {
        val ids = dao.getToReadInManualOrder().map { it.id }.toMutableList()
        val index = ids.indexOf(itemId)
        val target = (index + direction).coerceIn(0, (ids.size - 1).coerceAtLeast(0))
        if (index >= 0 && target != index) {
            val item = ids.removeAt(index)
            ids.add(target, item)
            val high = dao.nextManualOrder() + ids.size * 1024L
            ids.forEachIndexed { order, id -> dao.setManualOrder(id, high - order * 1024L) }
        }
    }

    suspend fun createPlaylist(name: String): String {
        val cleaned = name.trim()
        require(cleaned.isNotBlank()) { "Playlist names cannot be empty." }
        val id = UUID.randomUUID().toString()
        dao.upsertPlaylist(PlaylistEntity(id, cleaned, System.currentTimeMillis(), dao.nextPlaylistOrder()))
        return id
    }

    suspend fun addToPlaylist(playlistId: String, itemId: String) {
        val current = dao.getPlaylist(playlistId) ?: return
        dao.upsertMembership(PlaylistMembershipEntity(playlistId, itemId,
            dao.nextMembershipOrder(current.id), System.currentTimeMillis()))
    }

    suspend fun removeFromPlaylist(playlistId: String, itemId: String) = dao.removeMembership(playlistId, itemId)

    suspend fun completeItem(itemId: String) = database.withTransaction {
        val item = dao.getItem(itemId) ?: return@withTransaction
        dao.addHistory(ReadHistoryEntity(UUID.randomUUID().toString(), itemId, System.currentTimeMillis()))
        dao.upsertItem(item.copy(inToRead = false, updatedAt = System.currentTimeMillis()))
        dao.deleteQueueItem(itemId)
        compactQueue()
    }

    suspend fun removeHistoryEntry(historyId: String) = dao.removeHistory(historyId)
    suspend fun clearHistory() = dao.clearHistory()

    suspend fun deleteItem(itemId: String) = database.withTransaction {
        dao.removeItemFromAllPlaylists(itemId)
        dao.removeItemFromHistory(itemId)
        dao.deleteQueueItem(itemId)
        dao.deleteItem(itemId)
        compactQueue()
    }

    fun discardOriginal(item: ReadingItemEntity) {
        item.originalFilePath?.let(::File)?.delete()
    }
}
