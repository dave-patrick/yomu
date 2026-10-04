package dev.yomu.app

import android.content.Context

/** Small, on-device diagnostic ring buffer. Entries intentionally exclude user content and credentials. */
object AppDiagnostics {
    data class Entry(val timestampMillis: Long, val message: String)

    private const val PREFS = "yomu_diagnostics"
    private const val EVENTS = "events"
    private const val MAX_ENTRIES = 100

    @Synchronized
    fun record(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val entries = read(context).toMutableList()
        entries += Entry(System.currentTimeMillis(), message.replace('\n', ' ').replace('\t', ' ').take(180))
        prefs.edit().putString(EVENTS, entries.takeLast(MAX_ENTRIES).joinToString("\n") { "${it.timestampMillis}\t${it.message}" }).apply()
    }

    fun read(context: Context): List<Entry> = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(EVENTS, "").orEmpty().lineSequence().mapNotNull { line ->
            val split = line.split('\t', limit = 2)
            split.getOrNull(0)?.toLongOrNull()?.let { timestamp -> Entry(timestamp, split.getOrElse(1) { "" }) }
        }.toList()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(EVENTS).apply()
    }
}
