package dev.yomu.app.backup

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Process
import dev.yomu.app.YomuApplication
import dev.yomu.app.data.LibraryDatabase
import dev.yomu.app.playback.YomuPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

/** Phone-only library backup. Provider keys are intentionally omitted; local model files can be re-downloaded. */
internal class LibraryBackupManager(private val app: Application) {
    suspend fun export(destination: Uri, progress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val database = File(app.cacheDir, "backup-library.db").apply { delete() }
        val prefsDir = File(app.applicationInfo.dataDir, "shared_prefs")
        val zip = app.contentResolver.openOutputStream(destination, "w") ?: error("Could not open the backup destination.")
        try {
            progress("Preparing a consistent library snapshot…")
            (app as YomuApplication).database.openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf(database.absolutePath))
            ZipOutputStream(zip.buffered()).use { out ->
                out.putNextEntry(ZipEntry("manifest.json"))
                out.write(JSONObject().put("format", "yomu-backup").put("version", 1).put("createdAt", System.currentTimeMillis()).toString().toByteArray())
                out.closeEntry()
                addFile(out, database, "library.db")
                addDirectory(out, File(app.filesDir, "library/originals"), "library/originals")
                listOf("appearance.xml", "speech_preferences.xml", "content_preferences.xml", "library_preferences.xml").forEach { name ->
                    File(prefsDir, name).takeIf(File::isFile)?.let { addFile(out, it, "settings/$name") }
                }
            }
            progress("Backup complete. Provider API keys are not included.")
        } finally { database.delete() }
    }

    suspend fun restore(source: Uri, progress: (String) -> Unit): Nothing = withContext(Dispatchers.IO) {
        val staging = File(app.cacheDir, "restore-staging").apply { deleteRecursively(); mkdirs() }
        var total = 0L
        var entries = 0
        app.contentResolver.openInputStream(source)?.use { stream ->
            ZipInputStream(stream.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= 10_000) { "This backup contains too many files." }
                    require(!entry.isDirectory && entry.name.split('/').none { it == ".." || it.isBlank() }) { "The selected backup has an unsafe path." }
                    val accepted = entry.name == "manifest.json" || entry.name == "library.db" ||
                    entry.name.startsWith("library/originals/") || entry.name in setOf("settings/appearance.xml", "settings/speech_preferences.xml", "settings/content_preferences.xml", "settings/library_preferences.xml")
                    if (accepted) {
                        val target = File(staging, entry.name)
                        require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) { "The selected backup contains an unsafe file path." }
                        target.parentFile?.mkdirs()
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= MAX_BACKUP_BYTES) { "The backup is larger than the 2 GB safety limit." }
                                output.write(buffer, 0, count)
                            }
                        }
                    } else error("The selected archive contains an unsupported backup entry.")
                    zip.closeEntry()
                }
            }
        } ?: error("Android could not read the selected backup.")

        val manifest = File(staging, "manifest.json").takeIf(File::isFile)?.readText()?.let(::JSONObject)
            ?: error("This is not a Yomu backup.")
        require(manifest.optString("format") == "yomu-backup" && manifest.optInt("version") == 1) { "This Yomu backup format is not supported." }
        val stagedDb = File(staging, "library.db")
        require(stagedDb.isFile && stagedDb.length() > 0) { "The backup does not contain the library database." }
        SQLiteDatabase.openDatabase(stagedDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == "ok") { "The backup database failed its integrity check." }
            }
        }

        progress("Restoring the selected library. Yomu will close and restart…")
        val backupDb = app.getDatabasePath(LibraryDatabase.DATABASE_NAME)
        val dbParent = backupDb.parentFile!!
        val rollbackDb = File(dbParent, "${LibraryDatabase.DATABASE_NAME}.before-restore")
        val currentOriginals = File(app.filesDir, "library/originals")
        val stagedOriginals = File(staging, "library/originals")
        val previousOriginals = File(app.filesDir, "library/originals.before-restore")
        val databaseApp = app as YomuApplication
        app.stopService(android.content.Intent(app, YomuPlaybackService::class.java))
        databaseApp.database.close()
        File(dbParent, "${LibraryDatabase.DATABASE_NAME}-wal").delete()
        File(dbParent, "${LibraryDatabase.DATABASE_NAME}-shm").delete()
        rollbackDb.delete()
        if (backupDb.exists()) check(backupDb.renameTo(rollbackDb)) { "Could not preserve the current library database." }
        previousOriginals.deleteRecursively()
        if (currentOriginals.exists()) check(currentOriginals.renameTo(previousOriginals)) { "Could not preserve the current document files." }
        try {
            check(stagedDb.copyTo(backupDb, overwrite = true).exists()) { "Could not install the restored database." }
            currentOriginals.mkdirs()
            if (stagedOriginals.exists()) stagedOriginals.copyRecursively(currentOriginals, overwrite = true)
            File(app.applicationInfo.dataDir, "shared_prefs").mkdirs()
            listOf("appearance.xml", "speech_preferences.xml", "content_preferences.xml", "library_preferences.xml").forEach { name ->
                File(staging, "settings/$name").takeIf(File::isFile)?.copyTo(File(app.applicationInfo.dataDir, "shared_prefs/$name"), overwrite = true)
            }
        } catch (error: Exception) {
            backupDb.delete()
            rollbackDb.takeIf(File::exists)?.renameTo(backupDb)
            currentOriginals.deleteRecursively()
            previousOriginals.takeIf(File::exists)?.renameTo(currentOriginals)
            throw error
        }
        staging.deleteRecursively(); rollbackDb.delete(); previousOriginals.deleteRecursively()
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }

    private fun addDirectory(zip: ZipOutputStream, directory: File, prefix: String) {
        if (!directory.exists()) return
        directory.walkTopDown().filter(File::isFile).forEach { file -> addFile(zip, file, "$prefix/${file.relativeTo(directory).invariantSeparatorsPath}") }
    }

    private fun addFile(zip: ZipOutputStream, file: File, path: String) {
        zip.putNextEntry(ZipEntry(path))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    companion object { private const val MAX_BACKUP_BYTES = 2L * 1024 * 1024 * 1024 }
}
