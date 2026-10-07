package dev.yomu.app.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Main-thread-owned window, with bounded concurrent synthesis and epoch invalidation. */
internal class SentenceLookAhead<T>(private val scope: CoroutineScope, concurrency: Int = 2) {
    private val permits = Semaphore(concurrency)
    private val entries = linkedMapOf<Int, Deferred<T>>()
    var epoch = 0L
        private set
    fun get(index: Int, create: suspend () -> T): Deferred<T> = entries.getOrPut(index) {
        scope.async(Dispatchers.IO) { permits.withPermit { create() } }
    }
    fun discardBefore(index: Int) {
        entries.keys.filter { it < index }.forEach { entries.remove(it)?.cancel() }
    }
    fun reset() {
        epoch++
        entries.values.forEach { it.cancel() }
        entries.clear()
    }
}
