package dev.yomu.app

import android.app.Application
import dev.yomu.app.data.LibraryDatabase
import dev.yomu.app.data.UrlImportWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class YomuApplication : Application() {
    val database by lazy { LibraryDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val directory = java.io.File(filesDir, "tesseract/tessdata").apply { mkdirs() }
            val destination = java.io.File(directory, "eng.traineddata")
            if (!destination.isFile) {
                val staging = java.io.File(directory, "eng.traineddata.part")
                assets.open("tesseract/tessdata/eng.traineddata").use { source ->
                    staging.outputStream().buffered().use { sink -> source.copyTo(sink) }
                }
                check(staging.renameTo(destination)) { "Could not stage the local OCR language data." }
            }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            database.libraryDao().getPendingUrlImportIds().forEach { UrlImportWork.enqueue(this@YomuApplication, it) }
            database.libraryDao().observeAllItems().first().filter { it.sourceType != "url" && it.importState in setOf("pending", "extracting") && it.originalFilePath?.let { path -> java.io.File(path).isFile } == true }
                .forEach { dev.yomu.app.data.DocumentImportWork.enqueue(this@YomuApplication, it.id) }
        }
    }
}
