from pathlib import Path
root=Path('android/app/src/main/java/dev/yomu/app')
def edit(name, old, new):
    p=root/name;s=p.read_text(encoding='utf-8');assert old in s,(name,old[:80]);p.write_text(s.replace(old,new),encoding='utf-8')

edit('data/LibraryDatabase.kt','val archived: Boolean = false,','''val archived: Boolean = false,
    @ColumnInfo(defaultValue = "''") val tags: String = "",
''')
edit('data/LibraryDatabase.kt','@Dao\ninterface LibraryDao','''@Entity(tableName = "annotations", indices = [Index("itemId")])
data class AnnotationEntity(@PrimaryKey val id: String, val itemId: String, val anchor: String,
    val kind: String, val note: String, val createdAt: Long)

@Entity(tableName = "deleted_items")
data class DeletedItemEntity(@PrimaryKey val id: String, val payload: String, val deletedAt: Long)

@Dao
interface LibraryDao''')
edit('data/LibraryDatabase.kt','    suspend fun nextManualOrder(): Long','''    suspend fun nextManualOrder(): Long

    @Query("SELECT * FROM annotations WHERE itemId=:id ORDER BY createdAt DESC")
    fun observeAnnotations(id: String): Flow<List<AnnotationEntity>>
    @Upsert suspend fun saveAnnotation(row: AnnotationEntity)
    @Query("DELETE FROM annotations WHERE id=:id") suspend fun deleteAnnotation(id: String)
    @Query("DELETE FROM annotations WHERE itemId=:id") suspend fun deleteItemAnnotations(id: String)
    @Query("SELECT * FROM deleted_items ORDER BY deletedAt DESC") fun observeDeleted(): Flow<List<DeletedItemEntity>>
    @Query("SELECT * FROM deleted_items WHERE id=:id") suspend fun getDeleted(id: String): DeletedItemEntity?
    @Upsert suspend fun saveDeleted(row: DeletedItemEntity)
    @Query("DELETE FROM deleted_items WHERE id=:id") suspend fun removeDeleted(id: String)
    @Query("UPDATE reading_items SET tags=:tags, updatedAt=:now WHERE id=:id")
    suspend fun setTags(id: String, tags: String, now: Long)
''')
edit('data/LibraryDatabase.kt','        SyncBaselineEntity::class,','''        SyncBaselineEntity::class,
        AnnotationEntity::class,
        DeletedItemEntity::class,''')
edit('data/LibraryDatabase.kt','    version = 5,','    version = 6,')
edit('data/LibraryDatabase.kt','        fun get(context: Context): LibraryDatabase','''        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reading_items ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS annotations (id TEXT NOT NULL PRIMARY KEY, itemId TEXT NOT NULL, anchor TEXT NOT NULL, kind TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_annotations_itemId ON annotations (itemId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS deleted_items (id TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL, deletedAt INTEGER NOT NULL)")
            }
        }

        fun get(context: Context): LibraryDatabase''')
edit('data/LibraryDatabase.kt','MIGRATION_3_4, MIGRATION_4_5).build()', 'MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()')

# Retain a complete local snapshot for explicit recovery; sync still sends an ordinary tombstone.
edit('data/LibraryRepository.kt','''        dao.deleteProgress(itemId)''','''        val payload = org.json.JSONObject()
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
        dao.deleteProgress(itemId)''')
edit('data/LibraryRepository.kt','''    fun discardOriginal(item: ReadingItemEntity) {''','''    suspend fun restoreDeleted(id: String) = database.withTransaction {
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

    fun discardOriginal(item: ReadingItemEntity) {''')
edit('LibraryViewModel.kt','''            repository.discardOriginal(item)''','''            cancelImport(itemId)
            if (reading.value?.id == itemId) closeReader()
            if (playback.value?.itemId == itemId) playbackControl(YomuPlaybackService.ACTION_STOP)''')
edit('LibraryViewModel.kt','''    fun startNow(itemId: String) {''','''    private val _quietReading = MutableStateFlow(false)
    val quietReading = _quietReading.asStateFlow()
    fun readQuietly(itemId: String) { _quietReading.value = true; openReader(itemId) }
    fun startNow(itemId: String, startAt: Int? = null) {
        _quietReading.value = false''')
edit('LibraryViewModel.kt','''val resumeSentence = repository.dao.getProgress(itemId)?.sentenceIndex ?: 0''','''val resumeSentence = startAt ?: repository.dao.getProgress(itemId)?.sentenceIndex ?: 0''')
edit('LibraryViewModel.kt','''    fun createPlaylist(name: String)''','''    val deletedItems = repository.dao.observeDeleted().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun restoreDeleted(id: String) = work("Restoring deleted item…") { repository.restoreDeleted(id) }
    fun permanentlyDelete(id: String) = work("Permanently deleting item…") { repository.permanentlyDelete(id) }
    fun annotationsFor(id: String) = repository.dao.observeAnnotations(id)
    fun saveAnnotation(item: ReadingItemEntity, offset: Int, kind: String, note: String) = work("Saving $kind…") {
        require(repository.dao.getItem(item.id)?.normalizedText == item.normalizedText) { "Text changed. Reopen the article before bookmarking." }
        repository.dao.saveAnnotation(dev.yomu.app.data.AnnotationEntity(java.util.UUID.randomUUID().toString(), item.id,
            dev.yomu.app.data.ReadingAnchor.create(item.normalizedText, offset), kind, note.take(10_000), System.currentTimeMillis()))
    }
    fun deleteAnnotation(id: String) = work("Removing bookmark…") { repository.dao.deleteAnnotation(id) }
    fun setTags(id: String, text: String) = work("Saving tags…") {
        val tags = text.split(',').map { it.trim().lowercase().take(60) }.filter { it.isNotBlank() }.distinct().take(30).joinToString(", ")
        repository.dao.setTags(id, tags, System.currentTimeMillis())
    }
    fun bulkAction(ids: Set<String>, action: String, playlistId: String? = null) = work("Updating ${ids.size} items…") {
        app.database.withTransaction {
            ids.forEach { id -> when(action) {
                "Queue" -> repository.addToUpNext(id)
                "Archive" -> repository.archiveItem(id)
                "Read" -> repository.markItemRead(id)
                "Delete" -> repository.deleteItem(id)
                "Playlist" -> playlistId?.let { repository.addToPlaylist(it, id) }
            } }
        }
    }
    fun exportOriginal(item: ReadingItemEntity, destination: Uri) = work("Saving original…") {
        val file = item.originalFilePath?.let(java.io.File::invoke)?.takeIf { it.isFile } ?: error("Download the original first.")
        withContext(Dispatchers.IO) { file.inputStream().use { source -> app.contentResolver.openOutputStream(destination)?.use { source.copyTo(it) } ?: error("Could not save the original.") } }
    }
    fun exportNotes(item: ReadingItemEntity, destination: Uri) = work("Exporting notes…") {
        val rows = annotationsFor(item.id).first()
        withContext(Dispatchers.IO) { app.contentResolver.openOutputStream(destination)?.bufferedWriter()?.use { writer ->
            writer.appendLine("# ${item.title}")
            item.sourceUrl?.let { writer.appendLine(it) }
            rows.forEach { row -> writer.appendLine("\\n## ${row.kind}"); writer.appendLine(JSONObject(row.anchor).optString("quote")); writer.appendLine(row.note) }
        } ?: error("Could not write notes.") }
    }
    fun createPlaylist(name: String)''')

# Durable extraction after the original copy. Disk staging avoids re-copying on retries.
edit('data/ContentImporter.kt','''            extractSavedDocument(id)''','''            DocumentImportWork.enqueue(app, id)''')
edit('data/ContentImporter.kt','''input().use { source -> stored.outputStream().buffered().use { sink -> source.copyTo(sink) } }''','''input().use { source ->
                val staging = File(stored.absolutePath + ".partial")
                try {
                    staging.outputStream().buffered().use { sink ->
                        val buffer = ByteArray(64 * 1024); var total = 0L
                        while (true) { val count = source.read(buffer); if (count < 0) break
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            total += count; require(total <= 80L * 1024 * 1024) { "Documents must be 80 MB or smaller." }; sink.write(buffer, 0, count)
                        }
                    }
                    check(staging.renameTo(stored)) { "Could not save the document." }
                } finally { staging.delete() }
            }''')
edit('data/ContentImporter.kt','import kotlinx.coroutines.Dispatchers','import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.ensureActive')
edit('YomuApplication.kt','''            database.libraryDao().getPendingUrlImportIds().forEach { UrlImportWork.enqueue(this@YomuApplication, it) }''','''            database.libraryDao().getPendingUrlImportIds().forEach { UrlImportWork.enqueue(this@YomuApplication, it) }
            database.libraryDao().observeAllItems().first().filter { it.sourceType != "url" && it.importState in setOf("pending", "extracting") && it.originalFilePath?.let { path -> java.io.File(path).isFile } == true }
                .forEach { dev.yomu.app.data.DocumentImportWork.enqueue(this@YomuApplication, it.id) }''')
edit('YomuApplication.kt','import kotlinx.coroutines.launch','import kotlinx.coroutines.launch\nimport kotlinx.coroutines.flow.first')
edit('LibraryViewModel.kt','import kotlinx.coroutines.Dispatchers','import kotlinx.coroutines.Dispatchers\nimport androidx.room.withTransaction\nimport kotlinx.coroutines.flow.first')

# Additive protocol gated by server capabilities to keep existing deployed servers usable.
edit('sync/LibrarySync.kt','"reading_cursors", "reader_defaults")','"reading_cursors", "reader_defaults", "annotations")')
edit('sync/LibrarySync.kt','"read_history", "reader_defaults" ->','"read_history", "reader_defaults", "annotations" ->')
edit('sync/LibrarySync.kt','''        for(kind in kinds) db.query''','''        for(kind in kinds.filter { it != "annotations" || prefs.getBoolean("mobile_v2", false) }) db.query''')
edit('sync/LibrarySync.kt','''                    if(name == "originalFilePath") continue''','''                    if(name == "originalFilePath" || (name == "tags" && !prefs.getBoolean("mobile_v2", false))) continue''')
edit('sync/LibrarySync.kt','''        val direction = SyncOptions(context).direction()''','''        val capabilities = request("state").optJSONArray("capabilities")
        prefs.edit().putBoolean("mobile_v2", capabilities != null && (0 until capabilities.length()).any { capabilities.optString(it) == "mobile_v2" }).commit()
        val direction = SyncOptions(context).direction()''')
edit('sync/LibrarySync.kt','"reading_progress","reading_cursors"))','"reading_progress","reading_cursors","annotations"))')

p=Path('web/app.py');s=p.read_text();s=s.replace("'archived', 'fileId'", "'archived', 'fileId', 'tags'")
s=s.replace("    'reader_defaults':", "    'annotations': {'id', 'itemId', 'anchor', 'kind', 'note', 'createdAt'},\n    'reader_defaults':")
s=s.replace("'read_history','reader_defaults')", "'read_history','reader_defaults','annotations')")
s=s.replace("        if kind == 'reading_cursors':", "        if kind == 'annotations' and (data.get('kind') not in ('bookmark','highlight','note','listening bookmark') or not isinstance(data.get('note'), str) or len(data['note']) > 10000 or not isinstance(data.get('anchor'), str) or len(data['anchor']) > 4096):\n            raise HTTPException(400, 'Invalid annotation.')\n        if kind == 'reading_items' and 'tags' in data and (not isinstance(data['tags'], str) or len(data['tags']) > 2000):\n            raise HTTPException(400, 'Invalid tags.')\n        if kind == 'reading_cursors':")
s=s.replace("        result['clients'] =", "        result['capabilities'] = ['mobile_v2']\n        result['clients'] =")
p.write_text(s)
print('Mobile metadata, durable imports and recovery applied')
