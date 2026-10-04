package dev.yomu.app.speech

import okio.Buffer
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ElevenLabsRestSpeechProviderTest {
    @Test fun synthesisConvertsPcmToPlayableWaveAndUsesSelectedVoice() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 0, 2, 0))))
            val output = File.createTempFile("yomu-e11-test", ".wav")
            try {
                val provider = ElevenLabsRestSpeechProvider("mock-key", OkHttpClient(), server.url("/").toString().trimEnd('/'))
                provider.synthesize(SpeechSynthesisRequest("Test sentence.", "voiceABC123", output))

                val request = server.takeRequest()
                assertEquals("POST", request.method)
                assertEquals("/v1/text-to-speech/voiceABC123", request.requestUrl?.encodedPath)
                assertEquals("pcm_24000", request.requestUrl?.queryParameter("output_format"))
                assertEquals("mock-key", request.getHeader("xi-api-key"))
                assertTrue(request.body.readUtf8().contains("eleven_multilingual_v2"))
                val wave = output.readBytes()
                assertEquals("RIFF", String(wave, 0, 4, Charsets.US_ASCII))
                assertEquals(24_000, littleEndianInt(wave, 24))
                assertEquals(4, littleEndianInt(wave, 40))
                assertEquals(48, wave.size)
            } finally { output.delete() }
        }
    }

    @Test fun incompatibleRecommendedModelRetriesWithMultilingualModel() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(422).setBody("{}"))
            server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 0, 2, 0))))
            val output = File.createTempFile("yomu-e11-fallback", ".wav")
            try {
                val provider = ElevenLabsRestSpeechProvider("mock-key", OkHttpClient(), server.url("/").toString().trimEnd('/'))
                provider.synthesize(SpeechSynthesisRequest("Test sentence.", "voiceABC123", output, "eleven_flash_v2_5"))

                assertTrue(server.takeRequest().body.readUtf8().contains("eleven_flash_v2_5"))
                assertTrue(server.takeRequest().body.readUtf8().contains("eleven_multilingual_v2"))
            } finally { output.delete() }
        }
    }

    @Test fun voiceCatalogFollowsPaginationAndPreservesUserFacingAttributes() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"voices":[{"voice_id":"v1","name":"Maeve","description":"A measured Irish storyteller.","available_for_tiers":["creator"],"labels":{"accent":"Irish","gender":"female","age":"middle-aged"},"preview_url":"https://storage.googleapis.com/sample.mp3","verified_languages":[{"language":"en","locale":"en-IE","model_id":"eleven_flash_v2_5"}]}],"has_more":true,"next_page_token":"next"}"""))
            server.enqueue(MockResponse().setBody("""{"voices":[{"voice_id":"v2","name":"Owen","labels":{"accent":"Australian"}}],"has_more":false}"""))
            val provider = ElevenLabsRestSpeechProvider("mock-key", OkHttpClient(), server.url("/").toString().trimEnd('/'))

            val voices = provider.listVoices()

            assertEquals(listOf("v1", "v2"), voices.map { it.id })
            assertEquals("en-IE", voices.first().locale)
            assertEquals("Irish", voices.first().accent)
            assertEquals("female", voices.first().gender)
            assertEquals("middle-aged", voices.first().age)
            assertEquals("A measured Irish storyteller.", voices.first().description)
            assertEquals("creator", voices.first().availableForTiers.single())
            assertEquals("eleven_flash_v2_5", voices.first().recommendedModelId)
            server.takeRequest()
            assertEquals("next", server.takeRequest().requestUrl?.queryParameter("next_page_token"))
        }
    }

    @Test fun accountUsageReportsFreeTierAndRemainingCharacters() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"tier":"free","character_count":2500,"character_limit":10000}"""))
            val usage = ElevenLabsRestSpeechProvider("mock-key", OkHttpClient(), server.url("/").toString().trimEnd('/')).usage()
            assertTrue(usage.isFree)
            assertEquals(7_500, usage.remainingCharacters)
        }
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
