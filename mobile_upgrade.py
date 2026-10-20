from pathlib import Path

root=Path('android/app/src/main/java/dev/yomu/app')
def edit(name, old, new):
    p=root/name
    s=p.read_text(encoding='utf-8')
    assert old in s, (name,old[:80])
    p.write_text(s.replace(old,new),encoding='utf-8')

# SQL updates touch extraction-owned fields only and never recreate deleted rows.
edit('data/LibraryDatabase.kt','    suspend fun upsertItem(item: ReadingItemEntity)', '''    suspend fun upsertItem(item: ReadingItemEntity)

    @Query("UPDATE reading_items SET importState=:state, warning=:warning WHERE id=:id")
    suspend fun setImportState(id: String, state: String, warning: String?)

    @Query("UPDATE reading_items SET normalizedText=:text, importState='ready', warning=NULL, estimatedSecondsAtOneX=:seconds, contentVersion=contentVersion+1, updatedAt=:now WHERE id=:id")
    suspend fun finishExtraction(id: String, text: String, seconds: Int, now: Long): Int

    @Query("UPDATE reading_items SET title=:title WHERE id=:id AND title=:previous")
    suspend fun setExtractedTitle(id: String, title: String, previous: String)

    @Query("UPDATE reading_items SET sourceUrl=:url, sourceName=:host WHERE id=:id")
    suspend fun setArticleSource(id: String, url: String, host: String)

    @Query("SELECT * FROM playlist_memberships")
    fun observeAllMemberships(): Flow<List<PlaylistMembershipEntity>>

    @Query("SELECT * FROM playlist_memberships WHERE playlistId=:id ORDER BY sortOrder")
    suspend fun getMemberships(id: String): List<PlaylistMembershipEntity>

    @Query("DELETE FROM playlist_memberships WHERE playlistId=:id")
    suspend fun clearPlaylistMemberships(id: String)

    @Query("DELETE FROM reading_progress WHERE itemId=:id")
    suspend fun deleteProgress(id: String)
''')
edit('data/ContentImporter.kt','dao.upsertItem(saved.copy(importState = "fetching", warning = null))','dao.setImportState(itemId, "fetching", null)')
edit('data/ContentImporter.kt','''                dao.upsertItem(saved.copy(title = title, sourceUrl = finalUrl, sourceName = host(finalUrl),
                    normalizedText = text, importState = "ready", warning = null,
                    estimatedSecondsAtOneX = estimateSeconds(text), updatedAt = System.currentTimeMillis()))''','''                dao.finishExtraction(itemId, text, estimateSeconds(text), System.currentTimeMillis())
                dao.setExtractedTitle(itemId, title, saved.title)
                dao.setArticleSource(itemId, finalUrl, host(finalUrl))''')
edit('data/ContentImporter.kt','''            dao.getItem(itemId)?.let { dao.upsertItem(it.copy(
                importState = if (retryable) "pending" else "failed",
                warning = if (retryable) "Waiting for an internet connection; Yomu will retry automatically." else message,
            )) }''','''            if (error is kotlinx.coroutines.CancellationException) throw error
            dao.setImportState(itemId, if (retryable) "pending" else "failed",
                if (retryable) "Waiting for an internet connection; Yomu will retry automatically." else message)''')
edit('data/ContentImporter.kt','''            val text = when {''','''            extractSavedDocument(id)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            dao.setImportState(id, "failed", error.message ?: "Document import failed")
        }
        id
    }

    suspend fun extractSavedDocument(id: String, password: String? = null) = withContext(Dispatchers.IO) {
        val saved = dao.getItem(id) ?: return@withContext
        val stored = saved.originalFilePath?.let(::File)?.takeIf { it.isFile }
            ?: error("The original is unavailable. Download it or share it again.")
        val safeName = saved.sourceName ?: saved.title
        dao.setImportState(id, "extracting", null)
        try {
            val mimeType: String? = null
            val text = when {''')
edit('data/ContentImporter.kt','extractPdf(stored, null)','extractPdf(stored, password)')
edit('data/ContentImporter.kt','''            dao.upsertItem(dao.getItem(id)!!.copy(normalizedText = clean(text), importState = "ready",
                estimatedSecondsAtOneX = estimateSeconds(clean(text))))''','''            dao.finishExtraction(id, clean(text), estimateSeconds(clean(text)), System.currentTimeMillis())''')
edit('data/ContentImporter.kt','''            dao.getItem(id)?.let { dao.upsertItem(it.copy(originalFilePath = stored.absolutePath, importState = "failed", warning = if (needsPassword) "This PDF is password protected or uses an unsupported security setting. Tap to try its password." else error.message ?: "Document import failed")) }
        }
        id''','''            if (error is kotlinx.coroutines.CancellationException) throw error
            dao.setImportState(id, "failed", if (needsPassword) "This PDF is password protected or uses an unsupported security setting. Tap to try its password." else error.message ?: "Document import failed")
            throw error
        }''')
edit('data/ContentImporter.kt','dao.upsertItem(saved.copy(importState = "extracting", warning = null))','dao.setImportState(itemId, "extracting", null)')
edit('data/ContentImporter.kt','''            dao.upsertItem(saved.copy(normalizedText = text, importState = "ready", warning = null,
                estimatedSecondsAtOneX = estimateSeconds(text), updatedAt = System.currentTimeMillis()))''','''            dao.finishExtraction(itemId, text, estimateSeconds(text), System.currentTimeMillis())''')
edit('data/ContentImporter.kt','dao.upsertItem(saved.copy(importState = "failed", warning = hint))','dao.setImportState(itemId, "failed", hint)')

edit('data/LibraryRepository.kt','''    suspend fun addToPlaylist(playlistId: String, itemId: String) {
        val current = dao.getPlaylist(playlistId) ?: return
        dao.upsertMembership(PlaylistMembershipEntity(playlistId, itemId,
            dao.nextMembershipOrder(current.id), System.currentTimeMillis()))
    }''','''    suspend fun addToPlaylist(playlistId: String, itemId: String) = database.withTransaction {
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
    }''')
edit('data/LibraryRepository.kt','''        dao.deleteReadingCursor(itemId)''','''        dao.deleteProgress(itemId)
        dao.deleteReadingCursor(itemId)''')

edit('LibraryViewModel.kt','''    fun retryImport(itemId: String) = work("Retrying import…") { UrlImportWork.enqueue(getApplication(), itemId) }''','''    fun retryImport(itemId: String) = work("Retrying import…") {
        val item = repository.dao.getItem(itemId) ?: return@work
        if (item.sourceType == "url") UrlImportWork.enqueue(getApplication(), itemId)
        else dev.yomu.app.data.DocumentImportWork.enqueue(getApplication(), itemId)
    }
    fun cancelImport(itemId: String) {
        androidx.work.WorkManager.getInstance(app).cancelUniqueWork("yomu-url-import:$itemId")
        androidx.work.WorkManager.getInstance(app).cancelUniqueWork("yomu-document-import:$itemId")
        work("Cancelling import…") { repository.dao.setImportState(itemId, "failed", "Import cancelled. Tap Retry to continue from the saved original.") }
    }''')
edit('LibraryViewModel.kt','''    fun createPlaylist(name: String)''','''    val allItems = repository.dao.observeAllItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val memberships = repository.dao.observeAllMemberships().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun renamePlaylist(id: String, name: String) = work("Renaming playlist…") { repository.renamePlaylist(id, name) }
    fun deletePlaylist(id: String) = work("Deleting playlist…") { repository.deletePlaylist(id) }
    fun movePlaylistItem(id: String, item: String, direction: Int) = work("Reordering playlist…") { repository.movePlaylistItem(id, item, direction) }
    fun createPlaylist(name: String)''')

# Keep healthy metadata sync moving if one original cannot be transferred.
edit('sync/LibrarySync.kt','''            require(file.length() <= 20L*1024*1024) { "${file.name} exceeds the 20 MB sync limit." }''','''            if(file.length() > 20L*1024*1024) {
                prefs.edit().putString("file_error:$itemId", "Original exceeds the 20 MB sync limit; text and metadata still sync.").commit()
                continue
            }''')
edit('sync/LibrarySync.kt','''            client.newCall(req).execute().use { if (!it.isSuccessful) throw SyncHttpException(it.code, "Could not sync ${file.name}: HTTP ${it.code}.") }
            check(prefs.edit().putString("file:$itemId",fileId).commit())''','''            try {
                client.newCall(req).execute().use { if (!it.isSuccessful) throw SyncHttpException(it.code, "Original upload: HTTP ${it.code}.") }
                check(prefs.edit().putString("file:$itemId",fileId).remove("file_error:$itemId").commit())
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { prefs.edit().putString("file_error:$itemId", error.message ?: "Original upload failed; retry sync.").commit() }''')

# Expose sleep and preparation state, stop-at-end and safe resume rewind.
edit('playback/PlaybackEvents.kt','''        val sentenceCount: Int,''','''        val sentenceCount: Int,
        val phase: String = if (isPlaying) "Playing" else "Paused",
        val sleepUntil: Long = 0,
        val stopAtEnd: Boolean = false,''')
edit('playback/YomuPlaybackService.kt','''    private var sleepTimer: Job? = null''','''    private var sleepTimer: Job? = null
    private var sleepUntil = 0L
    private var stopAtEnd = false
    private var playbackPhase = "Paused"''')
edit('playback/YomuPlaybackService.kt','''            ACTION_SLEEP -> {
                sleepTimer?.cancel()
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
                if (minutes > 0) sleepTimer = scope.launch { delay(minutes * 60_000L); player.pause() }
            }''','''            ACTION_SLEEP -> {
                sleepTimer?.cancel()
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
                stopAtEnd = minutes == -1
                sleepUntil = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
                publishSnapshot()
                if (minutes > 0) sleepTimer = scope.launch {
                    delay(minutes * 60_000L)
                    invalidatePreparedSpeech()
                    player.pause()
                    sleepUntil = 0L
                    publishSnapshot()
                }
            }''')
edit('playback/YomuPlaybackService.kt','''            ACTION_TOGGLE -> if (player.isPlaying) player.pause() else if (player.currentMediaItem == null) generateAndPlayNext() else player.play()''','''            ACTION_TOGGLE -> if (player.isPlaying) player.pause() else {
                val rewind = speechPreferences.getInt("resume_rewind_sentences", 0).coerceIn(0, 3)
                if (rewind > 0 && sentenceIndex > 0) { sentenceIndex = (sentenceIndex - rewind).coerceAtLeast(0); player.stop(); generateAndPlayNext() }
                else if (player.currentMediaItem == null) generateAndPlayNext() else player.play()
            }''')
edit('playback/YomuPlaybackService.kt','''                val interrupted = playNowResume.takeAfterCompletion(id)''','''                if (stopAtEnd) { stopAtEnd = false; itemId = null; return@withSpeechCpuAwake }
                val interrupted = playNowResume.takeAfterCompletion(id)''')
edit('playback/YomuPlaybackService.kt','''        isGenerating = true
        val index''','''        isGenerating = true
        playbackPhase = "Preparing"
        publishSnapshot()
        val index''')
edit('playback/YomuPlaybackService.kt','''                    PlaybackEvents.report("Could not start speech.''','''                    playbackPhase = "Failed"
                    publishSnapshot()
                    PlaybackEvents.report("Could not start speech.''')
edit('playback/YomuPlaybackService.kt','''PlaybackEvents.Snapshot(id, title, player.isPlaying, sentenceIndex, sentences.size)''','''PlaybackEvents.Snapshot(id, title, player.isPlaying, sentenceIndex, sentences.size,
            if (player.isPlaying) "Playing" else if (isGenerating) "Preparing" else playbackPhase.takeIf { it == "Failed" } ?: "Paused", sleepUntil, stopAtEnd)''')

print('Mobile reliability and playlist changes applied')
