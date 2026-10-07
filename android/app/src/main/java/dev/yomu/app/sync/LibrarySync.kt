package dev.yomu.app.sync

import android.content.Context
import android.content.ContentValues
import androidx.room.withTransaction
import dev.yomu.app.data.ContentImporter
import dev.yomu.app.data.LibraryDatabase
import dev.yomu.app.data.UrlImportWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Field deltas + transactionally acknowledged baselines preserve offline edits and prevent echo. */
class LibrarySync(private val context: Context) {
    private val database = LibraryDatabase.get(context)
    private val prefs = context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)
    private val vault = SyncVault(context)
    private val client = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).followRedirects(false).build()
    private val kinds = listOf("reading_items", "playlists", "playlist_memberships", "up_next", "reading_progress", "read_history", "reading_cursors", "reader_defaults")
    private fun id(kind: String, row: JSONObject) = when(kind) {
        "playlist_memberships" -> "${row.getString("playlistId")}:${row.getString("itemId")}"
        "reading_items", "playlists", "read_history", "reader_defaults" -> row.getString("id")
        else -> row.getString("itemId")
    }
    private fun equal(a: Any?, b: Any?): Boolean = canonical(a) == canonical(b)
    private fun canonical(value: Any?): String = when(value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().sorted().joinToString(prefix="{", postfix="}") { "$it:${canonical(value.opt(it))}" }
        else -> value.toString()
    }
    private fun snapshot(): Map<String, JSONObject> {
        val result = linkedMapOf<String, JSONObject>()
        val db = database.openHelper.writableDatabase
        for(kind in kinds) db.query("SELECT * FROM $kind").use { cursor ->
            while(cursor.moveToNext()) {
                val row = JSONObject()
                for(index in 0 until cursor.columnCount) {
                    val name = cursor.getColumnName(index)
                    if(name == "originalFilePath") continue
                    val value: Any = when {
                        cursor.isNull(index) -> JSONObject.NULL
                        name in setOf("inToRead", "archived", "completed") -> cursor.getInt(index) != 0
                        cursor.getType(index) == android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        cursor.getType(index) == android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                        else -> cursor.getString(index)
                    }
                    row.put(name, value)
                }
                if(kind == "reading_items") prefs.getString("file:${row.getString("id")}", null)?.let { row.put("fileId", it) }
                result["$kind/${id(kind,row)}"] = row
            }
        }
        return result
    }
    private fun baselines(): Map<String, JSONObject> {
        val result = linkedMapOf<String, JSONObject>()
        database.openHelper.writableDatabase.query("SELECT * FROM sync_baselines WHERE `key` != '__instance'").use { cursor ->
            while(cursor.moveToNext()) result[cursor.getString(0)] = JSONObject(cursor.getString(1))
        }
        return result
    }
    private fun request(path: String, body: String? = null): JSONObject {
        val builder = Request.Builder().url(vault.server() + "/api/" + path).header("Authorization", "Bearer ${vault.token()}")
        if(body != null) builder.post(body.toRequestBody("application/json".toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body!!.string()
            if (!response.isSuccessful) throw SyncHttpException(response.code, runCatching { JSONObject(responseBody).getString("detail") }.getOrDefault("Yomu sync returned HTTP ${response.code}."))
            return JSONObject(responseBody)
        }
    }
    suspend fun configure(server: String, token: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val oldServer = vault.server()
        require(oldServer.isBlank() || oldServer == server.trimEnd('/')) { "Disconnect before pairing with another library." }
        require(server.startsWith("https://")) { "Use your HTTPS Yomu website." }
        client.newCall(Request.Builder().url(server.trimEnd('/')+"/api/state").header("Authorization","Bearer ${token.trim()}").build()).execute().use {
            require(it.isSuccessful) { "Could not connect. Check the website and access key." }
        }
        vault.save(server, token)
        SyncWork.schedule(context)
        SyncStatusStore.get(context).reconnected()
    } }
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        mutex.withLock {
            vault.clear()
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("yomu-library-sync")
            database.openHelper.writableDatabase.execSQL("DELETE FROM sync_baselines")
            prefs.edit().clear().commit()
            SyncStatusStore.get(context).reset()
        }
    }
    suspend fun setOptions(direction: SyncDirection, minutes: Int) = withContext(Dispatchers.IO) { mutex.withLock {
        SyncOptions(context).save(direction, minutes)
        if (vault.token() != null) SyncWork.schedule(context)
    } }
    suspend fun sync(): String = withContext(Dispatchers.IO) { mutex.withLock {
        if(vault.token() == null) return@withLock "Not connected"
        val status = SyncStatusStore.get(context)
        status.started()
        try {
        val direction = SyncOptions(context).direction()
        val db = database.openHelper.writableDatabase
        // Upload originals once, before advertising the fileId to another device.
        val uploads = mutableListOf<Pair<String,String>>()
        db.query("SELECT id, originalFilePath FROM reading_items WHERE originalFilePath IS NOT NULL").use { cursor ->
            while(cursor.moveToNext()) uploads += cursor.getString(0) to cursor.getString(1)
        }
        for((itemId,path) in uploads) if(direction.uploads && !prefs.contains("file:$itemId")) {
            val file = File(path)
            if(!file.isFile) continue
            require(file.length() <= 20L*1024*1024) { "${file.name} exceeds the 20 MB sync limit." }
            val fileId = runCatching { UUID.fromString(itemId).toString() }.getOrElse { UUID.randomUUID().toString() }
            val req = Request.Builder().url(vault.server()+"/api/files/$fileId").header("Authorization", "Bearer ${vault.token()}")
                .header("X-File-Name", java.net.URLEncoder.encode(file.name, "UTF-8").replace("+", "%20"))
                .put(file.asRequestBody("application/octet-stream".toMediaType())).build()
            client.newCall(req).execute().use { if (!it.isSuccessful) throw SyncHttpException(it.code, "Could not sync ${file.name}: HTTP ${it.code}.") }
            check(prefs.edit().putString("file:$itemId",fileId).commit())
        }
        lateinit var sent: Map<String,JSONObject>
        lateinit var base: Map<String,JSONObject>
        database.withTransaction { sent = snapshot(); base = baselines() }
        val changes = JSONArray()
        for(key in sent.keys + base.keys) if(direction.uploads && !equal(sent[key],base[key])) {
            changes.put(JSONObject().put("kind",key.substringBefore('/')).put("id",key.substringAfter('/'))
                .put("data",sent[key] ?: JSONObject.NULL).put("base",base[key] ?: JSONObject.NULL))
        }
        val instance = db.query("SELECT payload FROM sync_baselines WHERE `key`='__instance'").use { if(it.moveToFirst()) it.getString(0) else "" }
        val clientId = prefs.getString("client",null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("client",it).commit() }
        val response = request("sync",JSONObject().put("client","phone-$clientId").put("instance",instance).put("changes",changes).toString())
        if (!direction.downloads) {
            // Acknowledge only accepted uploads. Keep conflicting edits dirty for a later retry.
            val conflicts = response.optJSONArray("conflicts") ?: JSONArray()
            val conflicted = (0 until conflicts.length()).map { conflicts.getString(it).substringBefore(": ").split('/').take(2).joinToString("/") }.toSet()
            database.withTransaction {
                for (key in sent.keys + base.keys) if (key !in conflicted) {
                    sent[key]?.let { baseline(key, it) } ?: db.execSQL("DELETE FROM sync_baselines WHERE `key`=?", arrayOf(key))
                }
                db.execSQL("INSERT OR REPLACE INTO sync_baselines VALUES (?,?)", arrayOf("__instance",response.getString("instance")))
            }
            val message = if (conflicts.length() > 0) "Uploaded. ${conflicts.length()} conflicts need two-way sync to resolve." else "Uploaded just now"
            status.succeeded(message)
            return@withLock message
        }
        val records = response.getJSONArray("records")
        database.withTransaction {
            val current = snapshot()
            val queue = current.filterKeys { it.startsWith("up_next/") }.toMutableMap()
            for(index in 0 until records.length()) {
                val record = records.getJSONObject(index)
                val kind = record.getString("kind")
                if(kind !in kinds) continue
                val ident = record.getString("id")
                val key = "$kind/$ident"
                val remote = record.optJSONObject("data")
                val now = current[key]
                val before = sent[key]
                if (kind in setOf("reading_cursors", "reader_defaults") && now != null) {
                    val conflicts = response.optJSONArray("conflicts") ?: JSONArray()
                    if ((0 until conflicts.length()).any { conflicts.optString(it).startsWith("$key/") || conflicts.optString(it).startsWith("$key:") }) {
                        prefs.edit().putString("resume_conflict:$key", now.toString()).commit()
                    }
                }
                if(remote == null) {
                    if(equal(now,before)) {
                        if(kind == "up_next") queue.remove(key) else delete(kind,ident)
                    }
                    db.execSQL("DELETE FROM sync_baselines WHERE `key`=?", arrayOf(key))
                    continue
                }
                // A delete or edit made while the request was in flight stays dirty for the next pass.
                if(now == null && before != null) {
                    baseline(key,remote)
                    continue
                }
                val merged = JSONObject(remote.toString())
                if(now != null && before != null) now.keys().forEach { field ->
                    if(!equal(now.opt(field),before.opt(field))) merged.put(field,now.opt(field))
                }
                remote.optString("fileId").takeIf(String::isNotBlank)?.let { prefs.edit().putString("file:$ident",it).commit() }
                if(kind == "up_next") queue[key] = merged else upsert(kind,ident,merged)
                baseline(key,remote)
            }
            db.execSQL("DELETE FROM up_next")
            queue.values.sortedBy { it.optInt("position") }.forEachIndexed { index, row ->
                upsert("up_next",row.getString("itemId"),JSONObject(row.toString()).put("position",index))
            }
            db.execSQL("INSERT OR REPLACE INTO sync_baselines VALUES (?,?)", arrayOf("__instance",response.getString("instance")))
        }
        val dao = database.libraryDao()
        dao.getPendingUrlImportIds().forEach { UrlImportWork.enqueue(context,it) }
        // Let the existing Android document importer handle PDF/OCR and Word extraction.
        val pending = mutableListOf<String>()
        db.query("SELECT id FROM reading_items WHERE sourceType != 'url' AND normalizedText = '' AND importState='pending'").use { c -> while(c.moveToNext()) pending += c.getString(0) }
        for(itemId in pending) {
            val fileId = prefs.getString("file:$itemId",null) ?: continue
            val item = dao.getItem(itemId) ?: continue
            val req = Request.Builder().url(vault.server()+"/api/files/$fileId").header("Authorization","Bearer ${vault.token()}").build()
            client.newCall(req).execute().use { responseFile ->
                if (!responseFile.isSuccessful) throw SyncHttpException(responseFile.code, "Could not download ${item.title}.")
                val newId = ContentImporter(context,dao).importDocument(item.sourceName ?: item.title,null) { responseFile.body!!.byteStream() }
                val extracted = dao.getItem(newId) ?: error("Document import did not finish.")
                database.withTransaction {
                    dao.getItem(itemId)?.let { currentItem -> dao.upsertItem(currentItem.copy(normalizedText=extracted.normalizedText,
                        originalFilePath=extracted.originalFilePath,importState=extracted.importState,warning=extracted.warning,
                        estimatedSecondsAtOneX=extracted.estimatedSecondsAtOneX,updatedAt=System.currentTimeMillis())) }
                    dao.deleteItem(newId)
                }
            }
        }
        val conflicts = response.optJSONArray("conflicts")?.length() ?: 0
        val message = if(conflicts > 0) "Synced. $conflicts conflicting fields kept the server value." else "Synced just now"
        status.succeeded(message)
        message
        } catch (error: kotlinx.coroutines.CancellationException) { status.paused(); throw error }
        catch (error: Exception) { status.failed(error); throw error }
    } }
    private fun baseline(key: String,row: JSONObject) { database.openHelper.writableDatabase.execSQL("INSERT OR REPLACE INTO sync_baselines VALUES (?,?)",arrayOf(key,row.toString())) }
    private fun delete(kind: String,ident: String) {
        val (where,args) = identity(kind,ident)
        database.openHelper.writableDatabase.execSQL("DELETE FROM $kind WHERE $where",args)
        if(kind == "reading_items") for(table in listOf("up_next","playlist_memberships","read_history","reading_progress","reading_cursors"))
            database.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE itemId=?",arrayOf(ident))
    }
    private fun identity(kind: String,ident: String): Pair<String,Array<Any?>> = when(kind) {
        "playlist_memberships" -> "playlistId=? AND itemId=?" to arrayOf(ident.substringBefore(':'),ident.substringAfter(':'))
        "reading_items", "playlists", "read_history", "reader_defaults" -> "id=?" to arrayOf(ident)
        else -> "itemId=?" to arrayOf(ident)
    }
    private fun upsert(kind: String,ident: String,row: JSONObject) {
        val db = database.openHelper.writableDatabase
        val columns = db.query("SELECT * FROM $kind LIMIT 0").use { it.columnNames.toSet() }
        val values = ContentValues()
        row.keys().forEach { field -> if(field in columns && field != "originalFilePath") {
            when(val value=row.opt(field)) {
                null, JSONObject.NULL -> values.putNull(field)
                is Boolean -> values.put(field,if(value) 1 else 0)
                is Number -> if(value is Double || value is Float) values.put(field,value.toDouble()) else values.put(field,value.toLong())
                else -> values.put(field,value.toString())
            }
        } }
        val (where,args) = identity(kind,ident)
        val exists = db.query("SELECT 1 FROM $kind WHERE $where",args).use { it.moveToFirst() }
        if(exists) db.update(kind,android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,values,where,args)
        else db.insert(kind,android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,values)
    }
    companion object { private val mutex = Mutex() }
}
