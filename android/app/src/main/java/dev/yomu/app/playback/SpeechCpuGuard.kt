package dev.yomu.app.playback

/** A bounded CPU lease for speech preparation, including suspended network work. */
internal class SpeechCpuGuard(private val acquire: (Long) -> Unit, private val release: () -> Unit) {
    suspend fun <T> run(block: suspend () -> T): T {
        acquire(TIMEOUT_MS)
        try { return block() } finally { release() }
    }
    companion object { const val TIMEOUT_MS = 5 * 60 * 1000L }
}
