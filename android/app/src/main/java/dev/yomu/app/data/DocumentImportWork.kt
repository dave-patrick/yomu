package dev.yomu.app.data

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException

/** Saved originals survive process death; passwords are never persisted in WorkManager. */
object DocumentImportWork {
    fun enqueue(context: Context, id: String) {
        val prefs = context.getSharedPreferences("content_preferences", 0)
        val request = OneTimeWorkRequestBuilder<DocumentImportWorker>()
            .setInputData(workDataOf("item_id" to id))
            .addTag("yomu-import")
            .setConstraints(Constraints.Builder().setRequiresCharging(prefs.getBoolean("charging_only", false)).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("yomu-document-import:$id", ExistingWorkPolicy.KEEP, request)
    }
}

class DocumentImportWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("item_id") ?: return Result.failure()
        return try {
            ContentImporter(applicationContext, LibraryDatabase.get(applicationContext).libraryDao()).extractSavedDocument(id)
            Result.success()
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { Result.failure(workDataOf("error" to (error.message ?: "Extraction failed"))) }
    }
}
