package dev.yomu.app.speech

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

/** Fish Audio REST adapter pinned to its currently free S2.1 Pro developer model. */
class FishAudioRestSpeechProvider(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(120)).build(),
    baseUrl: String = "https://api.fish.audio",
) : CloudSpeechProvider {
    override val id = "fish-audio-rest"
    override val capabilities = setOf(SpeechCapability.VOICE_CATALOG, SpeechCapability.SYNTHESIS)
    private val base = baseUrl.trimEnd('/')

    init { require(apiKey.isNotBlank()) { "Enter the Fish Audio API key." } }

    /** Fetches licensed public English voices, not arbitrary unlicensed community clones. */
    override fun listVoices(): List<SpeechVoice> {
        val result = mutableListOf<SpeechVoice>()
        var page = 1
        do {
            val url = "$base/model".toHttpUrl().newBuilder()
                .addQueryParameter("page_size", "100")
                .addQueryParameter("page_number", page.toString())
                .addQueryParameter("language", "en")
                .addQueryParameter("licensed", "true")
                .addQueryParameter("sort_by", "score")
                .build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer $apiKey").get().build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { errorMessage(response.code, "voice catalog") }
                val root = JSONObject(response.body?.string() ?: "{}")
                val items = root.optJSONArray("items") ?: org.json.JSONArray()
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optString("_id")
                    if (id.isBlank() || item.optString("state") != "trained" || item.optBoolean("dmca_taken_down")) continue
                    val tags = item.optJSONArray("tags")
                    val tagList = (0 until (tags?.length() ?: 0)).mapNotNull { tags?.optString(it)?.takeIf(String::isNotBlank) }
                    val languages = item.optJSONArray("languages")
                    val languageList = (0 until (languages?.length() ?: 0)).mapNotNull { languages?.optString(it)?.takeIf(String::isNotBlank) }
                    result += SpeechVoice(
                        id = id,
                        name = item.optString("title", id),
                        locale = languageList.firstOrNull() ?: "en",
                        localeName = languageList.firstOrNull()?.let(::displayLanguage),
                        description = item.optString("description").takeIf(String::isNotBlank),
                        gender = tagList.firstOrNull { it.equals("female", true) || it.equals("male", true) },
                        accent = tagList.firstOrNull { it.contains("accent", true) || it.contains("british", true) || it.contains("australian", true) || it.contains("irish", true) || it.contains("scottish", true) || it.contains("american", true) },
                        voiceType = item.optString("type").takeIf(String::isNotBlank),
                        styles = tagList,
                    )
                }
                if (!root.optBoolean("has_more")) return result
            }
            page++
            check(page <= MAX_VOICE_PAGES) { "Fish Audio returned too many voice pages." }
        } while (true)
    }

    override fun synthesize(request: SpeechSynthesisRequest): SpeechSynthesisResult {
        require(request.text.isNotBlank()) { "Fish Audio needs non-empty text." }
        val payload = JSONObject()
            .put("text", request.text)
            .put("reference_id", request.voiceId)
            .put("format", "wav")
            .put("sample_rate", 24000)
            .put("prosody", JSONObject().put("speed", 1.0).put("volume", 0).put("normalize_loudness", true))
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpRequest = Request.Builder().url("$base/v1/tts")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("model", FREE_MODEL_ID)
            .post(payload).build()
        client.newCall(httpRequest).execute().use { response ->
            check(response.isSuccessful) { errorMessage(response.code, "speech synthesis") }
            val audio = response.body ?: error("Fish Audio returned an empty audio response.")
            val length = audio.contentLength()
            if (length >= 0) check(length in 1..MAX_AUDIO_BYTES) { "Fish Audio returned an unsupported audio size." }
            request.outputFile.parentFile?.mkdirs()
            var written = 0L
            FileOutputStream(request.outputFile).use { output ->
                audio.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        check(written <= MAX_AUDIO_BYTES) { "Fish Audio returned too much audio for one sentence." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(written > 44) { "Fish Audio returned an incomplete WAV file." }
            return SpeechSynthesisResult(request.outputFile, "audio/wav")
        }
    }

    private fun errorMessage(code: Int, action: String): String = when (code) {
        401 -> "Fish Audio rejected the API key. Check it in Settings."
        402 -> "Fish Audio account has no free-model access or available free quota."
        403 -> "Fish Audio denied access to this voice. Choose a licensed voice from the catalog."
        429 -> "Fish Audio concurrency limit reached. Try again shortly."
        else -> "Fish Audio $action returned HTTP $code."
    }

    private fun displayLanguage(code: String): String = when (code.lowercase()) {
        "en", "english" -> "English"
        else -> code
    }

    companion object {
        const val FREE_MODEL_ID = "s2.1-pro-free"
        private const val MAX_AUDIO_BYTES = 40L * 1024L * 1024L
        private const val MAX_VOICE_PAGES = 50
    }
}
