package dev.yomu.app.sync

import android.content.Context

enum class SyncDirection(val label: String, val uploads: Boolean, val downloads: Boolean) {
    BOTH("Two-way", true, true),
    UPLOAD("Phone → website", true, false),
    DOWNLOAD("Website → phone", false, true)
}

class SyncOptions(context: Context) {
    private val prefs = context.getSharedPreferences("sync_options", Context.MODE_PRIVATE)
    fun direction() = runCatching { SyncDirection.valueOf(prefs.getString("direction", "BOTH")!!) }.getOrDefault(SyncDirection.BOTH)
    fun minutes() = prefs.getInt("minutes", 15)
    fun save(direction: SyncDirection, minutes: Int) {
        require(minutes in intervals)
        check(prefs.edit().putString("direction", direction.name).putInt("minutes", minutes).commit())
    }
    fun isDue(lastSync: Long, now: Long = System.currentTimeMillis()) = automaticSyncDue(minutes(), lastSync, now)
    companion object { val intervals = listOf(0, 1, 5, 15, 30, 60, 240) }
}

internal fun automaticSyncDue(minutes: Int, lastSync: Long, now: Long) = minutes > 0 && now - lastSync >= minutes * 60_000L
