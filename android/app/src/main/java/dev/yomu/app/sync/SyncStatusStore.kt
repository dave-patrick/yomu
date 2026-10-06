package dev.yomu.app.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

class SyncHttpException(val code: Int, message: String) : IOException(message)
data class SyncFailure(val message: String, val retryable: Boolean)
fun classifySyncFailure(error: Exception): SyncFailure = when {
    error is SyncHttpException && error.code in listOf(401, 403) -> SyncFailure("Access key rejected. Disconnect and reconnect with your website key.", false)
    error is SyncHttpException && error.code == 409 -> SyncFailure("The website library changed. Disconnect and reconnect to review the new library.", false)
    error is SyncHttpException -> SyncFailure(error.message ?: "Website sync failed.", error.code >= 500 || error.code in listOf(408, 425, 429))
    error is IOException -> SyncFailure("Could not reach your website. Pending edits are kept; sync will retry.", true)
    error is IllegalArgumentException -> SyncFailure(error.message ?: "Check your sync settings.", false)
    else -> SyncFailure(error.message ?: "Sync failed. Pending edits are kept.", true)
}
data class SyncUiState(val message: String, val lastSuccess: Long, val lastAttempt: Long, val error: String?, val retryable: Boolean, val inProgress: Boolean) {
    fun canRetryAutomatically() = error == null || retryable
}
class SyncStatusStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)
    @Volatile private var working = false
    private fun read() = SyncUiState(prefs.getString("status", "Not connected").orEmpty(), prefs.getLong("lastSync", 0), prefs.getLong("lastAttempt", 0), prefs.getString("syncError", null), prefs.getBoolean("retryable", true), working)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mutable.value = read() }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }
    fun canRetryAutomatically() = state.value.canRetryAutomatically()
    fun started() { working = true; prefs.edit().putLong("lastAttempt", System.currentTimeMillis()).putString("status", "Syncing…").remove("syncError").apply(); mutable.value = read() }
    fun succeeded(message: String) { working = false; prefs.edit().putString("status", message.removeSuffix(" just now")).putLong("lastSync", System.currentTimeMillis()).remove("syncError").putBoolean("retryable", true).apply(); mutable.value = read() }
    fun failed(error: Exception) { val failure = classifySyncFailure(error); working = false; prefs.edit().putString("status", "Sync needs attention").putString("syncError", failure.message).putBoolean("retryable", failure.retryable).apply(); mutable.value = read() }
    fun paused() { working = false; prefs.edit().putString("status", "Sync paused. Pending edits are kept.").apply(); mutable.value = read() }
    fun reconnected() { working = false; prefs.edit().putString("status", "Connected; ready to sync").remove("syncError").putBoolean("retryable", true).apply(); mutable.value = read() }
    fun reset() { working = false; mutable.value = read() }
    companion object {
        @Volatile private var instance: SyncStatusStore? = null
        fun get(context: Context): SyncStatusStore = instance ?: synchronized(this) { instance ?: SyncStatusStore(context.applicationContext).also { instance = it } }
    }
}
