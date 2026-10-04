package dev.yomu.app.playback

internal data class ResumePoint(val itemId: String, val sentenceIndex: Int)

/** Tracks the single item interrupted by Play now, preserving it across service recreation. */
internal class PlayNowResumeState(initial: ResumePoint? = null) {
    var point: ResumePoint? = initial
        private set

    fun onStart(currentItemId: String?, currentSentence: Int, requestedItemId: String, isReturning: Boolean = false) {
        if (isReturning) return
        if (requestedItemId == point?.itemId) {
            point = null
            return
        }
        if (point == null && currentItemId != null && currentItemId != requestedItemId) {
            point = ResumePoint(currentItemId, currentSentence.coerceAtLeast(0))
        }
    }

    fun takeAfterCompletion(completedItemId: String): ResumePoint? {
        val saved = point
        point = null
        return saved?.takeUnless { it.itemId == completedItemId }
    }
}
