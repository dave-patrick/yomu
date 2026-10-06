package dev.yomu.app.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class SyncWork(context: Context, parameters: WorkerParameters) : CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result = try {
        val lastSync = applicationContext.getSharedPreferences("sync_state",0).getLong("lastSync",0)
        if (SyncOptions(applicationContext).isDue(lastSync)) LibrarySync(applicationContext).sync()
        Result.success()
    }
    catch(error: kotlinx.coroutines.CancellationException) { throw error }
    catch(error: Exception) { Result.retry() }
    companion object {
        fun schedule(context: Context) {
            val minutes = SyncOptions(context).minutes()
            if (minutes == 0) {
                WorkManager.getInstance(context).cancelUniqueWork("yomu-library-sync")
                return
            }
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("yomu-library-sync",ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SyncWork>(minutes.coerceAtLeast(15).toLong(),TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
