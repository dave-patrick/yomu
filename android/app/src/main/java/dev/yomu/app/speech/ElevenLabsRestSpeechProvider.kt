package dev.yomu.app.speech

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

data class ElevenLabsUsage(val tier: String, val characterCount: Int, val characterLimit: Int) {
    val isFree: Boolean get() = tier.equals("free", ignoreCase = true) || tier.startsWith("free_", ignoreCase = true)
    val remainingCharacters: Int get() = (characterLimit - characterCount).coerceAtLeast(0)
}

/** REST adapter for the user's own ElevenLabs account; synthesis is normalized to 24 kHz mono WAV. */
class ElevenLabsRestSpeechProvider(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(75)).build(),
    baseUrl: String = "https://api.elevenlabs.io",
) : CloudSpeechProvider {
    override val id = "elevenlabs-rest"
    override val capabilities = setOf(SpeechCapability.VOICE_CATALOG, SpeechCapability.SYNTHESIS)
    private val base = baseUrl.trimEnd('/')

    init { require(apiKey.isNotBlank()) { "Enter the ElevenLabs API key." } }

    override fun listVoices(): List<SpeechVoice> {
        val result = mutableListOf<SpeechVoice>()
        var token: String? = null
        var page = 0
        do {
            val url = "$base/v2/voices".toHttpUrl().newBuilder()
                .addQueryParameter("page_size", "100")
                .addQueryParameter("include_total_count", "false")
                .apply { token?.let { addQueryParameter("next_page_token", it) } }
                .build()
            val request = Request.Builder().url(url).header("xi-api-key", apiKey).get().build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { errorMessage(response.code, "voice catalog") }
                val root = JSONObject(response.body?.string() ?: "{}")
                val voices = root.optJSONArray("voices") ?: org.json.JSONArray()
                for (index in 0 until voices.length()) {
                    val item = voices.optJSONObject(index) ?: continue
                    val id = item.optString("voice_id")
                    if (id.isBlank()) continue
                    val labels = item.optJSONObject("labels") ?: JSONObject()
                    val verified = item.optJSONArray("verified_languages")
                    val english = (0 until (verified?.length() ?: 0)).asSequence()
                        .mapNotNull { verified?.optJSONObject(it) }
                        .firstOrNull { it.optString("language").equals("en", true) }
                    val accent = labels.optString("accent").takeIf(String::isNotBlank)
                        ?: english?.optString("accent")?.takeIf(String::isNotBlank)
                    val locale = english?.optString("locale")?.takeIf(String::isNotBlank) ?: localeForAccent(accent)
                    val availableForTiers = item.optJSONArray("available_for_tiers")?.let { tiers ->
                        (0 until tiers.length()).mapNotNull { tiers.optString(it).takeIf(String::isNotBlank) }
                    }.orEmpty()
                    result += SpeechVoice(
                        id = id,
                        name = item.optString("name", id),
                        locale = locale,
                        gender = labels.optString("gender").takeIf(String::isNotBlank),
                        accent = accent,
                        age = labels.optString("age").takeIf(String::isNotBlank),
                        description = item.optString("description").takeIf(String::isNotBlank),
                        previewUrl = item.optString("preview_url").takeIf(String::isNotBlank),
                        voiceType = item.optString("category").takeIf(String::isNotBlank),
                        styles = labels.optString("use_case").split(',', ';').map(String::trim).filter(String::isNotBlank).distinct(),
                        recommendedModelId = english?.optString("model_id")?.takeIf(String::isNotBlank),
                        availableForTiers = availableForTiers,
                    )
                }
                token = root.optString("next_page_token").takeIf { root.optBoolean("has_more") && it.isNotBlank() }
            }
            page++
            check(page <= MAX_VOICE_PAGES) { "ElevenLabs returned too many voice pages." }
        } while (token != null)
        return result
    }

    fun usage(): ElevenLabsUsage {
        val request = Request.Builder().url("$base/v1/user/subscription").header("xi-api-key", apiKey).get().build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { errorMessage(response.code, "account usage") }
            val root = JSONObject(response.body?.string() ?: "{}")
            return ElevenLabsUsage(root.optString("tier", "unknown"), root.optInt("character_count"), root.optInt("character_limit"))
        }
    }

    fun downloadPreview(voice: SpeechVoice, file: File) {
        val preview = voice.previewUrl?.toHttpUrl() ?: error("This voice has no provider preview sample.")
        require(preview.isHttps && preview.host == "storage.googleapis.com") { "ElevenLabs returned an unsupported preview location." }
        val request = Request.Builder().url(preview).get().build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "ElevenLabs voice preview HTTP ${response.code}." }
            file.parentFile?.mkdirs()
            file.outputStream().buffered().use { output ->
                response.body?.byteStream()?.use { it.copyTo(output) } ?: error("The voice preview was empty.")
            }
        }
    }

    override fun synthesize(request: SpeechSynthesisRequest): SpeechSynthesisResult {
        val requestedModel = request.modelId ?: MODEL_ID
        return try {
            synthesizeUsingModel(request, requestedModel)
        } catch (error: ElevenLabsRequestException) {
            // Some account voices reject their preview model for synthesis; try the supported multilingual fallback.
            if (error.httpCode == 422 && requestedModel != MODEL_ID) synthesizeUsingModel(request, MODEL_ID) else throw error
        }
    }

    private fun synthesizeUsingModel(request: SpeechSynthesisRequest, modelId: String): SpeechSynthesisResult {
        require(request.text.length <= MAX_TEXT_CHARS) { "This sentence is too long for the selected provider's request limit." }
        val voiceId = URLEncoder.encode(request.voiceId, StandardCharsets.UTF_8.name()).replace("+", "%20")
        val url = "$base/v1/text-to-speech/$voiceId".toHttpUrl().newBuilder()
            .addQueryParameter("output_format", "pcm_24000")
            .build()
        val body = JSONObject().put("text", request.text).put("model_id", modelId)
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpRequest = Request.Builder().url(url).header("xi-api-key", apiKey)
            .header("Content-Type", "application/json").post(body).build()
        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) throw ElevenLabsRequestException(response.code, errorMessage(response.code, "speech synthesis"))
            val audio = response.body ?: error("ElevenLabs returned an empty audio response.")
            val length = audio.contentLength()
            if (length >= 0) check(length <= MAX_PCM_BYTES && length % 2L == 0L) { "ElevenLabs returned an unsupported audio size." }
            request.outputFile.parentFile?.mkdirs()
            RandomAccessFile(request.outputFile, "rw").use { output ->
                output.setLength(0)
                output.write(wavHeader(0))
                var size = 0L
                audio.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        size += read
                        check(size <= MAX_PCM_BYTES) { "ElevenLabs returned too much audio for one sentence." }
                        output.write(buffer, 0, read)
                    }
                }
                check(size > 0 && size % 2L == 0L) { "ElevenLabs returned an incomplete PCM sample." }
                output.seek(0)
                output.write(wavHeader(size.toInt()))
            }
            return SpeechSynthesisResult(request.outputFile, "audio/wav")
        }
    }

    private fun wavHeader(pcmBytes: Int): ByteArray {
        val header = ByteArray(44)
        fun ascii(at: Int, value: String) { value.toByteArray(StandardCharsets.US_ASCII).copyInto(header, at) }
        fun u16(at: Int, value: Int) { header[at] = value.toByte(); header[at + 1] = (value ushr 8).toByte() }
        fun u32(at: Int, value: Int) { repeat(4) { header[at + it] = (value ushr (8 * it)).toByte() } }
        ascii(0, "RIFF"); u32(4, 36 + pcmBytes); ascii(8, "WAVE"); ascii(12, "fmt ")
        u32(16, 16); u16(20, 1); u16(22, 1); u32(24, SAMPLE_RATE); u32(28, SAMPLE_RATE * 2)
        u16(32, 2); u16(34, 16); ascii(36, "data"); u32(40, pcmBytes)
        return header
    }

    private fun errorMessage(code: Int, action: String): String = when (code) {
        401 -> "ElevenLabs rejected the API key. Check it in Settings."
        403 -> "ElevenLabs denied this voice for the current account or plan. Choose a voice available to your tier."
        404 -> "ElevenLabs could not find this voice. Reload the voice catalog and select an available voice."
        422 -> "ElevenLabs rejected this voice and model combination. Yomu will try the voice's recommended model; if it still fails, choose another voice."
        429 -> "ElevenLabs rate limit or monthly allowance reached. Check account usage in Settings."
        else -> "ElevenLabs $action returned HTTP $code."
    }

    private class ElevenLabsRequestException(val httpCode: Int, message: String) : Exception(message)

    private fun localeForAccent(accent: String?): String = when (accent?.lowercase()) {
        "australian" -> "en-AU"
        "new zealand", "new zealander" -> "en-NZ"
        "british", "scottish", "irish", "english" -> "en-GB"
        "american", "us", "united states" -> "en-US"
        else -> "en"
    }

    companion object {
        const val MODEL_ID = "eleven_multilingual_v2"
        const val SAMPLE_RATE = 24_000
        const val MAX_TEXT_CHARS = 10_000
        const val MAX_PCM_BYTES = 40 * 1024 * 1024
        private const val MAX_VOICE_PAGES = 100
    }
}
