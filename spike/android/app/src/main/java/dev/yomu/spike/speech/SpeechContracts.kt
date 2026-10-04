package dev.yomu.spike.speech

import java.io.File

enum class SpeechCapability {
    VOICE_CATALOG,
    SYNTHESIS,
    WORD_AND_SENTENCE_TIMING
}

data class SpeechVoice(
    val id: String,
    val name: String,
    val locale: String,
    val gender: String? = null
)

class SpeechCredentials(internal val key: String, val region: String)

data class SpeechSynthesisRequest(
    val text: String,
    val voiceId: String,
    val outputFile: File
)

data class SpeechBoundary(
    val audioOffsetTicks100ns: Long,
    val textOffset: Int,
    val wordLength: Int,
    val type: String,
    val callbackElapsedRealtimeNanos: Long? = null
) {
    val audioOffsetMs: Double get() = audioOffsetTicks100ns / 10_000.0
}

data class SpeechSynthesisResult(
    val outputFile: File,
    val mediaType: String,
    val boundaries: List<SpeechBoundary> = emptyList(),
    val responseMetadata: Map<String, String?> = emptyMap()
)

/** Capabilities are explicit because provider catalogs and timing APIs differ. */
interface CloudSpeechProvider {
    val id: String
    val capabilities: Set<SpeechCapability>
    fun listVoices(): List<SpeechVoice>
    fun synthesize(request: SpeechSynthesisRequest): SpeechSynthesisResult
}

data class PhonemizerInfo(
    val id: String,
    val license: String,
    val bundledDataDirectory: File?
)

class OfflineSpeechAudio internal constructor(private val generated: com.k2fsa.sherpa.onnx.GeneratedAudio) {
    val samples: FloatArray get() = generated.samples
    val sampleRate: Int get() = generated.sampleRate
    fun durationMs(): Double = samples.size * 1000.0 / sampleRate
    fun save(file: File) { check(generated.save(file.path)) { "Failed to write generated WAV" } }
}

/**
 * Offline synthesis boundary. sherpa-onnx currently performs Kokoro text
 * front-end work internally; the descriptor records that choice without
 * pretending its current API accepts arbitrary external phoneme tokens.
 */
interface OfflineSpeechProvider : AutoCloseable {
    val id: String
    val phonemizer: PhonemizerInfo
    fun generate(text: String, voiceId: Int, speed: Float): OfflineSpeechAudio
}
