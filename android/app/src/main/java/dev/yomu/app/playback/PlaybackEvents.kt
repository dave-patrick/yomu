package dev.yomu.app.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-process service-to-reader status; never contains provider keys or document text. */
object PlaybackEvents {
    data class Snapshot(
        val itemId: String,
        val title: String,
        val isPlaying: Boolean,
        val sentenceIndex: Int,
        val sentenceCount: Int,
        val phase: String = if (isPlaying) "Playing" else "Paused",
        val sleepUntil: Long = 0,
        val stopAtEnd: Boolean = false,
    )

    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = mutableMessages.asSharedFlow()
    private val mutableSnapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot = mutableSnapshot.asStateFlow()

    fun report(message: String) { mutableMessages.tryEmit(message) }
    fun publish(snapshot: Snapshot?) { mutableSnapshot.value = snapshot }
}
