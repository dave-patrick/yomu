package dev.yomu.app.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Follow the playing article only while its reader is open. */
internal class ReaderSelection(initialPlaybackId: String? = null) {
    private var lastPlaybackId = initialPlaybackId
    private val mutableTarget = MutableStateFlow<String?>(null)
    val target = mutableTarget.asStateFlow()
    fun open(itemId: String) { mutableTarget.value = itemId }
    fun close() { mutableTarget.value = null }
    fun playbackChanged(itemId: String?) {
        // Completion briefly clears the snapshot before starting the next item.
        if (itemId == null) return
        if (mutableTarget.value != null && mutableTarget.value == lastPlaybackId) mutableTarget.value = itemId
        lastPlaybackId = itemId
    }
}
