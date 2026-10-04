package dev.yomu.app.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

internal object UrlImportWork {
    private const val ITEM_ID = "item_id"

    fun enqueue(context: Context, itemId: String) {
        val request = OneTimeWorkRequestBuilder<UrlImportWorker>()
            .setInputData(workDataOf(ITEM_ID to itemId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("yomu-url-import:$itemId", ExistingWorkPolicy.KEEP, request)
    }

}

class UrlImportWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val itemId = inputData.getString("item_id") ?: return Result.failure()
        val dao = LibraryDatabase.get(applicationContext).libraryDao()
        return try {
            ContentImporter(applicationContext, dao).retryUrl(itemId)
            Result.success()
        } catch (_: ContentImporter.RetryableUrlImportException) {
            Result.retry()
        } catch (error: Exception) {
            Result.failure(workDataOf("error" to (error.message ?: "Article extraction failed.")))
        }
    }
}
