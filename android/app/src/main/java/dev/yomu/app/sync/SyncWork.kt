package dev.yomu.app.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class SyncWork(context: Context, parameters: WorkerParameters) : CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result = try { LibrarySync(applicationContext).sync(); Result.success() }
    catch(error: kotlinx.coroutines.CancellationException) { throw error }
    catch(error: Exception) { Result.retry() }
    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("yomu-library-sync",ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWork>(15,TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
