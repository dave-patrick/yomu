package dev.yomu.app.export

import android.content.Context
import android.net.Uri
import dev.yomu.app.data.ReadingItemEntity
import dev.yomu.app.speech.AzureCredentialVault
import dev.yomu.app.speech.AzureRestSpeechProvider
import dev.yomu.app.speech.ElevenLabsCredentialVault
import dev.yomu.app.speech.ElevenLabsRestSpeechProvider
import dev.yomu.app.speech.FishAudioCredentialVault
import dev.yomu.app.speech.FishAudioRestSpeechProvider
import dev.yomu.app.speech.KokoroModelInstaller
import dev.yomu.app.speech.SherpaKokoroEspeakProvider
import dev.yomu.app.speech.SpeechSynthesisRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.BreakIterator
import java.util.Locale

/** Offline-capable, single-file spoken-word export with roughly five-minute ID3 chapters. */
internal class Mp3AudioExporter(private val context: Context) {
    private val vault = AzureCredentialVault(context)
    private val elevenLabsVault = ElevenLabsCredentialVault(context)
    private val fishAudioVault = FishAudioCredentialVault(context)
    private val installer = KokoroModelInstaller(context)

    suspend fun export(item: ReadingItemEntity, destination: Uri, progress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir, "exports/${item.id.hashCode()}").apply { mkdirs() }
        val taskContext = currentCoroutineContext()
        var destinationNeedsCleanup = true
        try {
        val rawMp3 = File(root, "audio.mp3")
        val taggedMp3 = File(root, "tagged.mp3")
        val sentences = splitSentences(item.normalizedText)
        require(sentences.isNotEmpty()) { "This item has no readable text to export." }
        val chunks = makeChapters(sentences)
        val prefs = context.getSharedPreferences("speech_preferences", Context.MODE_PRIVATE)
        val offlineVoice = prefs.getInt("kokoro_voice", 7)
        var offlineOnly = prefs.getBoolean("offline_only", false)
        val requestedOfflineOnly = offlineOnly
        var sampleRate = 0
        var channels = 0
        var samplesWritten = 0L
        val chapterTags = mutableListOf<AudioChapter>()
        rawMp3.outputStream().buffered().use { output ->
            LameEncoder(24_000, 1, 96).use { encoder ->
                fun encodeWav(file: File) {
                    PcmWavReader(file).use { wav ->
                        require(wav.channels == 1 && wav.sampleRate == 24_000) { "The selected voice returned an unsupported audio format." }
                        if (sampleRate == 0) { sampleRate = wav.sampleRate; channels = wav.channels }
                        while (true) {
                            val pcm = wav.readSamples()
                            if (pcm.isEmpty()) break
                            output.write(encoder.encode(pcm))
                            samplesWritten += pcm.size / wav.channels
                        }
                    }
                }
                chunks.forEachIndexed { index, chunk ->
                    taskContext.ensureActive()
                    progress("Creating chapter ${index + 1} of ${chunks.size}…")
                    val begin = samplesWritten * 1000 / 24_000
                    val audio = File(root, "part-$index.wav")
                    var onlineSucceeded = false
                    if (!offlineOnly) onlineSucceeded = synthesizeOnline(chunk.text, audio, prefs)
                    if (!onlineSucceeded) {
                        check(installer.isInstalled()) {
                            if (requestedOfflineOnly) "The Kokoro offline voice is not installed. Install it in Settings before exporting offline."
                            else "Online speech failed and the offline voice is not installed."
                        }
                        val fallingBack = !offlineOnly
                        offlineOnly = true
                        if (fallingBack && index == 0) {
                            val alert = File(root, "offline-notice.wav")
                            synthesizeOffline("Service unavailable. Continuing with the offline voice.", alert, offlineVoice)
                            encodeWav(alert)
                        }
                        synthesizeOffline(chunk.text, audio, offlineVoice)
                    }
                    encodeWav(audio)
                    val end = samplesWritten * 1000 / 24_000
                    chapterTags += AudioChapter("Chapter ${index + 1}", begin, end)
                    audio.delete()
                }
                output.write(encoder.flush())
            }
        }
        progress("Writing Pocket Casts chapter tags…")
        Id3ChapterWriter.write(rawMp3, taggedMp3, item.title, chapterTags)
        val target = context.contentResolver.openOutputStream(destination, "w") ?: error("Android could not open the selected export location.")
        target.use { sink ->
            taggedMp3.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    taskContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    sink.write(buffer, 0, count)
                }
            }
        }
        destinationNeedsCleanup = false
        progress("MP3 export complete.")
        } finally {
            if (destinationNeedsCleanup) runCatching { context.contentResolver.delete(destination, null, null) }
            root.deleteRecursively()
        }
    }

    private fun synthesizeOffline(text: String, target: File, voiceId: Int) {
        target.parentFile?.mkdirs()
        SherpaKokoroEspeakProvider(installer.modelDirectory, 2).use { it.generate(text, voiceId, 1f).save(target) }
    }

    private fun synthesizeOnline(text: String, output: File, prefs: android.content.SharedPreferences): Boolean {
        val primary = prefs.getString("online_provider", "azure") ?: "azure"
        val providers = if (prefs.getBoolean("online_fallback_enabled", true)) {
            val alternate = when (primary) {
                "azure" -> "elevenlabs"
                "elevenlabs" -> "azure"
                else -> "azure"
            }
            (listOf(primary, alternate) + listOf("fish-audio").filter { it != primary }).toList()
        } else listOf(primary)
        for (providerId in providers) {
            val succeeded = when (providerId) {
                "azure" -> vault.credentials()?.let { credentials -> runCatching {
                    AzureRestSpeechProvider(credentials).synthesize(SpeechSynthesisRequest(text, prefs.getString("azure_voice", "en-GB-SoniaNeural") ?: "en-GB-SoniaNeural", output))
                }.isSuccess } ?: false
                "elevenlabs" -> {
                    val tier = prefs.getString("elevenlabs_tier", "").orEmpty()
                    val mayUse = tier.equals("free", true) || tier.startsWith("free_", true) || prefs.getBoolean("allow_paid_elevenlabs", false)
                    val key = elevenLabsVault.key()
                    val voice = prefs.getString("elevenlabs_voice", "").orEmpty()
                    if (mayUse && tier.isNotBlank() && key != null && voice.isNotBlank()) runCatching {
                        ElevenLabsRestSpeechProvider(key).synthesize(SpeechSynthesisRequest(text, voice, output))
                    }.isSuccess else false
                }
                "fish-audio" -> {
                    val key = fishAudioVault.key()
                    val voice = prefs.getString("fish_audio_voice", "").orEmpty()
                    if (key != null && voice.isNotBlank()) runCatching {
                        FishAudioRestSpeechProvider(key).synthesize(SpeechSynthesisRequest(text, voice, output))
                    }.isSuccess else false
                }
                else -> false
            }
            if (succeeded) return true
        }
        return false
    }

    private fun makeChapters(sentences: List<String>): List<ChapterText> {
        val chunks = mutableListOf<ChapterText>()
        val text = StringBuilder()
        var words = 0
        for (sentence in sentences) {
            val sentenceWords = sentence.split(Regex("\\s+")).size
            if (words >= 1_000 && text.isNotEmpty()) {
                chunks += ChapterText(text.toString().trim())
                text.clear(); words = 0
            }
            text.append(sentence).append(' ')
            words += sentenceWords
        }
        if (text.isNotBlank()) chunks += ChapterText(text.toString().trim())
        return chunks
    }

    private fun splitSentences(text: String): List<String> {
        val iterator = BreakIterator.getSentenceInstance(Locale.US).apply { setText(text) }
        val result = mutableListOf<String>()
        var start = iterator.first(); var end = iterator.next()
        while (end != BreakIterator.DONE) {
            text.substring(start, end).trim().takeIf(String::isNotBlank)?.let(result::add)
            start = end; end = iterator.next()
        }
        if (result.isEmpty() && text.isNotBlank()) result += text.trim()
        return result
    }

    private data class ChapterText(val text: String)
}
