package dev.yomu.app.speech

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Voice samples are expensive to obtain: Azure and Fish Audio synthesize them
 * on demand and ElevenLabs downloads them from the provider. Samples are
 * therefore cached on disk under a stable per-voice name, so previewing the
 * same voice again costs no API call and no network time.
 *
 * Cached files live in the app cache directory, which the system may purge at
 * any time. A missing file simply means the sample is fetched again.
 */
object VoicePreviewCache {
    const val AZURE = "azure"
    const val ELEVENLABS = "eleven"
    const val FISH_AUDIO = "fish"
    const val KOKORO = "kokoro"

    const val AZURE_EXTENSION = "wav"
    const val ELEVENLABS_EXTENSION = "mp3"
    const val FISH_AUDIO_EXTENSION = "wav"
    const val KOKORO_EXTENSION = "wav"

    private const val DIRECTORY = "voice-previews"
    private const val MIN_VALID_BYTES = 64L

    /** Keeps the cache bounded so previewing a whole catalogue cannot fill storage. */
    private const val MAX_TOTAL_BYTES = 32L * 1024L * 1024L

    fun directory(cacheDir: File): File = File(cacheDir, DIRECTORY)

    /** Stable name for one provider's sample of one voice. */
    fun fileName(provider: String, voiceId: String, extension: String): String =
        "$provider-${sanitize(voiceId)}.$extension"

    fun file(cacheDir: File, provider: String, voiceId: String, extension: String): File =
        File(directory(cacheDir), fileName(provider, voiceId, extension))

    fun sanitize(voiceId: String): String = voiceId.replace(Regex("[^A-Za-z0-9_-]"), "_")

    /** A sample is only replayable when the previous fetch wrote a complete file. */
    fun isUsable(file: File): Boolean = file.isFile && file.length() >= MIN_VALID_BYTES

    /**
     * Fetches the sample unless a usable copy is already cached, and returns
     * the file to play either way.
     *
     * The provider writes to a temporary file that is renamed into place only
     * once the bytes are complete, so an interrupted fetch is never mistaken
     * for a cached sample on the next attempt.
     */
    suspend fun resolve(
        cacheDir: File,
        provider: String,
        voiceId: String,
        extension: String,
        fetch: suspend (File) -> Unit,
    ): File {
        val target = file(cacheDir, provider, voiceId, extension)
        if (isUsable(target)) {
            touch(target)
            return target
        }
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "${target.name}.part")
        try {
            // Callers run on the main dispatcher, so the fetch must leave it before
            // touching the network or the file system.
            withContext(Dispatchers.IO) { fetch(partial) }
            if (!isUsable(partial)) {
                partial.delete()
                error("The voice provider returned an unusable sample.")
            }
            if (!partial.renameTo(target)) {
                target.delete()
                if (!partial.renameTo(target)) {
                    partial.delete()
                    error("Yomu could not save the voice sample.")
                }
            }
        } catch (error: Exception) {
            partial.delete()
            throw error
        }
        touch(target)
        prune(cacheDir)
        return target
    }

    /** Marks a sample as recently used so pruning keeps the ones still in demand. */
    private fun touch(file: File) {
        runCatching { file.setLastModified(System.currentTimeMillis()) }
    }

    /** Drops the cached samples of one provider, used when its key changes. */
    suspend fun clearProvider(cacheDir: File, provider: String) {
        withContext(Dispatchers.IO) {
            directory(cacheDir).listFiles()?.forEach { file ->
                if (file.name.startsWith("$provider-")) runCatching { file.delete() }
            }
        }
    }

    /** Removes the least recently used samples until the cache fits its budget. */
    fun prune(cacheDir: File) {
        val files = directory(cacheDir).listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_TOTAL_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= MAX_TOTAL_BYTES) return@forEach
            val size = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) total -= size
        }
    }

    /**
     * Removes samples written by the earlier scheme, which appended a
     * millisecond timestamp and therefore kept a separate copy of every
     * preview that was ever played. A thirteen digit run is not a real voice
     * id, so it identifies those leftovers unambiguously.
     */
    suspend fun clearLegacySamples(cacheDir: File) {
        withContext(Dispatchers.IO) {
            directory(cacheDir).listFiles()?.forEach { file ->
                if (file.isFile && LEGACY_NAME.matches(file.name)) runCatching { file.delete() }
            }
        }
    }

    private val LEGACY_NAME = Regex(".*-\\d{13}\\.(wav|mp3)$")
}
