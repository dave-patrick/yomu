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

    suspend fun addToPlaylist(playlistId: String, itemId: String) = database.withTransaction {
        val current = dao.getPlaylist(playlistId) ?: return@withTransaction
        if (dao.getItem(itemId) == null || dao.getMemberships(playlistId).any { it.itemId == itemId }) return@withTransaction
        dao.upsertMembership(PlaylistMembershipEntity(playlistId, itemId,
            dao.nextMembershipOrder(current.id), System.currentTimeMillis()))
    }

    suspend fun renamePlaylist(id: String, name: String) = database.withTransaction {
        val cleaned = name.trim()
        require(cleaned.isNotBlank()) { "Enter a playlist name." }
        require(dao.getPlaylists().none { it.id != id && it.name.equals(cleaned, true) }) { "A playlist with this name already exists." }
        dao.getPlaylist(id)?.let { dao.upsertPlaylist(it.copy(name = cleaned)) }
    }

    suspend fun movePlaylist(id: String, direction: Int) = database.withTransaction {
        val rows = dao.getPlaylists().sortedWith(compareBy<PlaylistEntity> { it.sortOrder }.thenBy { it.name }).toMutableList()
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) return@withTransaction
        val target = (index + direction).coerceIn(0, rows.lastIndex)
        rows.add(target, rows.removeAt(index))
        rows.forEachIndexed { order, row -> dao.upsertPlaylist(row.copy(sortOrder = order)) }
    }

    suspend fun deletePlaylist(id: String) = database.withTransaction {
        dao.clearPlaylistMemberships(id)
        dao.deletePlaylist(id)
    }

    suspend fun movePlaylistItem(id: String, itemId: String, direction: Int) = database.withTransaction {
        val rows = dao.getMemberships(id).toMutableList()
        val index = rows.indexOfFirst { it.itemId == itemId }
        if (index < 0) return@withTransaction
        val target = (index + direction).coerceIn(0, rows.lastIndex)
        rows.add(target, rows.removeAt(index))
        rows.forEachIndexed { order, row -> dao.upsertMembership(row.copy(sortOrder = order)) }
    }

    suspend fun removeFromPlaylist(playlistId: String, itemId: String) = dao.removeMembership(playlistId, itemId)

    suspend fun autoArchive(itemId: String, offset: Int, enabled: Boolean) = database.withTransaction {
        if (!enabled) return@withTransaction
        val item = dao.getItem(itemId) ?: return@withTransaction
        if (!item.archived && reachedArchiveThreshold(offset, item.normalizedText.length)) {
            // Keep the active queue entry until completion so narration is uninterrupted.
            dao.upsertItem(item.copy(archived = true, inToRead = false, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun completeItem(itemId: String, autoArchive: Boolean = false) = database.withTransaction {
        val item = dao.getItem(itemId) ?: return@withTransaction
        dao.addHistory(ReadHistoryEntity(UUID.randomUUID().toString(), itemId, System.currentTimeMillis()))
        dao.upsertItem(item.copy(inToRead = false, archived = item.archived || autoArchive, updatedAt = System.currentTimeMillis()))
        dao.deleteQueueItem(itemId)
        compactQueue()
    }

    suspend fun removeHistoryEntry(historyId: String) = dao.removeHistory(historyId)
    suspend fun clearHistory() = dao.clearHistory()

    suspend fun deleteItem(itemId: String) = database.withTransaction {
        val payload = org.json.JSONObject()
        for (table in listOf("reading_items", "reading_progress", "reading_cursors", "playlist_memberships", "up_next", "read_history", "annotations")) {
            val rows = org.json.JSONArray()
            database.openHelper.readableDatabase.query("SELECT * FROM $table WHERE ${if (table == "reading_items") "id" else "itemId"}=?", arrayOf(itemId)).use { cursor ->
                while (cursor.moveToNext()) {
                    val row = org.json.JSONObject()
                    cursor.columnNames.forEachIndexed { index, name -> row.put(name, when {
                        cursor.isNull(index) -> org.json.JSONObject.NULL
                        cursor.getType(index) == android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        cursor.getType(index) == android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                        else -> cursor.getString(index)
                    }) }
                    rows.put(row)
                }
            }
            payload.put(table, rows)
        }
        dao.saveDeleted(DeletedItemEntity(itemId, payload.toString(), System.currentTimeMillis()))
        dao.deleteItemAnnotations(itemId)
        dao.deleteProgress(itemId)
        dao.deleteReadingCursor(itemId)
        dao.removeItemFromAllPlaylists(itemId)
        dao.removeItemFromHistory(itemId)
        dao.deleteQueueItem(itemId)
        dao.deleteItem(itemId)
        compactQueue()
    }

    suspend fun restoreDeleted(id: String) = database.withTransaction {
        val deleted = dao.getDeleted(id) ?: return@withTransaction
        require(dao.getItem(id) == null) { "This item already exists." }
        val payload = org.json.JSONObject(deleted.payload)
        for (table in listOf("reading_items", "reading_progress", "reading_cursors", "playlist_memberships", "read_history", "annotations")) {
            val rows = payload.optJSONArray(table) ?: continue
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                if (table == "playlist_memberships" && dao.getPlaylist(row.getString("playlistId")) == null) continue
                val values = android.content.ContentValues()
                row.keys().forEach { key -> when (val value = row.opt(key)) {
                    org.json.JSONObject.NULL -> values.putNull(key)
                    is Number -> if (value is Double) values.put(key, value.toDouble()) else values.put(key, value.toLong())
                    else -> values.put(key, value.toString())
                } }
                database.openHelper.writableDatabase.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
            }
        }
        dao.removeDeleted(id)
    }

    suspend fun permanentlyDelete(id: String) = database.withTransaction {
        val row = dao.getDeleted(id) ?: return@withTransaction
        val path = org.json.JSONObject(row.payload).getJSONArray("reading_items").optJSONObject(0)?.optString("originalFilePath")
        path?.takeIf { it.isNotBlank() && it != "null" }?.let(::File)?.delete()
        dao.removeDeleted(id)
    }

    fun discardOriginal(item: ReadingItemEntity) {
        item.originalFilePath?.let(::File)?.delete()
    }
}
