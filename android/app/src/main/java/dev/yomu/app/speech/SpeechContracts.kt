package dev.yomu.app.speech

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
    val gender: String? = null,
    val accent: String? = null,
    val age: String? = null,
    val description: String? = null,
    val previewUrl: String? = null,
    val localeName: String? = null,
    val voiceType: String? = null,
    val sampleRateHertz: Int? = null,
    val wordsPerMinute: Int? = null,
    val secondaryLocales: List<String> = emptyList(),
    val styles: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
    val personalities: List<String> = emptyList(),
    val contentCategories: List<String> = emptyList(),
    val recommendedModelId: String? = null,
    val availableForTiers: List<String> = emptyList(),
) {
    /** Azure REST does not publish a numeric quality score, so expose its model family. */
    fun azureQualityCategory(): String = when {
        id.contains("DragonHD", ignoreCase = true) || id.contains("HDLatestNeural", ignoreCase = true) -> "Neural HD"
        id.contains("Neural", ignoreCase = true) || voiceType.equals("Neural", ignoreCase = true) -> "Neural"
        else -> "Standard"
    }

    fun azureDescription(): String = buildList {
        add(localeName ?: locale)
        gender?.takeIf(String::isNotBlank)?.let(::add)
        add(azureQualityCategory())
    }.joinToString(" · ")
}

class SpeechCredentials(internal val key: String, val region: String)

/**
 * Starred voices are provider-scoped so an identical id published by two
 * different providers never collapses into one starred entry.
 */
internal fun starredVoiceKey(provider: String, voiceId: String): String = "$provider:$voiceId"

data class SpeechSynthesisRequest(
    val text: String,
    val voiceId: String,
    val outputFile: File,
    val modelId: String? = null,
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
