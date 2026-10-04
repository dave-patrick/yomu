package dev.yomu.app.speech

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.io.File
import java.time.Duration

class AzureRestSpeechProvider(
    private val credentials: SpeechCredentials,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(60)).build(),
    endpointBaseUrl: String? = null
) : CloudSpeechProvider {
    override val id = "azure-rest"
    override val capabilities = setOf(SpeechCapability.VOICE_CATALOG, SpeechCapability.SYNTHESIS)
    private val baseUrl = endpointBaseUrl?.trimEnd('/') ?: "https://${credentials.region}.tts.speech.microsoft.com/cognitiveservices"

    override fun listVoices(): List<SpeechVoice> {
        val request = Request.Builder().url("$baseUrl/voices/list")
            .header("Ocp-Apim-Subscription-Key", credentials.key).get().build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Azure REST voice list HTTP ${response.code}" }
            val json = JSONArray(response.body?.string() ?: "[]")
            return (0 until json.length()).map { index ->
                val item = json.getJSONObject(index)
                SpeechVoice(
                    id = item.optString("ShortName"),
                    name = item.optString("DisplayName", item.optString("ShortName")),
                    locale = item.optString("Locale"),
                    gender = item.optString("Gender").takeIf(String::isNotBlank),
                    localeName = item.optString("LocaleName").takeIf(String::isNotBlank),
                    voiceType = item.optString("VoiceType").takeIf(String::isNotBlank),
                    sampleRateHertz = item.optInt("SampleRateHertz").takeIf { it > 0 },
                    wordsPerMinute = item.optInt("WordsPerMinute").takeIf { it > 0 },
                    secondaryLocales = item.optJSONArray("SecondaryLocaleList").asStringList(),
                    styles = item.optJSONArray("StyleList").asStringList(),
                    roles = item.optJSONArray("RolePlayList").asStringList(),
                    personalities = item.optJSONArray("VoicePersonalities").asStringList(),
                    contentCategories = item.optJSONArray("ContentCategories").asStringList()
                )
            }
        }
    }

    override fun synthesize(request: SpeechSynthesisRequest): SpeechSynthesisResult {
        val locale = request.voiceId.split('-').take(2).joinToString("-").ifBlank { "en-US" }
        val ssml = """<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="$locale"><voice name="${xml(request.voiceId)}">${xml(request.text)}</voice></speak>"""
        val httpRequest = Request.Builder().url("$baseUrl/v1")
            .header("Ocp-Apim-Subscription-Key", credentials.key)
            .header("X-Microsoft-OutputFormat", "riff-24khz-16bit-mono-pcm")
            .post(ssml.toRequestBody("application/ssml+xml; charset=utf-8".toMediaType())).build()
        client.newCall(httpRequest).execute().use { response ->
            check(response.isSuccessful) { "Azure REST synthesis HTTP ${response.code}" }
            request.outputFile.parentFile?.mkdirs()
            request.outputFile.outputStream().use { output ->
                response.body?.byteStream()?.use { input -> input.copyTo(output) }
                    ?: error("Azure REST response had no body")
            }
            return SpeechSynthesisResult(
                outputFile = request.outputFile,
                mediaType = response.header("Content-Type") ?: "application/octet-stream",
                responseMetadata = mapOf(
                    "request_id_present" to (response.header("X-Microsoft-RequestId") != null).toString(),
                    "content_duration" to response.header("X-Content-Duration")
                )
            )
        }
    }

    private fun xml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun org.json.JSONArray?.asStringList(): List<String> = this?.let { array ->
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.orEmpty()
}
