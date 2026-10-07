package dev.yomu.app.speech

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class AzureRestSpeechProviderTest {
    @Test fun successiveSentencesReuseConnectionAndSendChangedVoice() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("audio-one"))
            server.enqueue(MockResponse().setBody("audio-two"))
            val client = OkHttpClient()
            val output = java.io.File.createTempFile("yomu-speech", ".wav")
            try {
                for (voice in listOf("en-AU-William:DragonHDLatestNeural", "en-US-Ava:DragonHDLatestNeural")) {
                    AzureRestSpeechProvider(SpeechCredentials("test-key", "eastus"), client,
                        server.url("/cognitiveservices").toString().trimEnd('/'))
                        .synthesize(SpeechSynthesisRequest("Test sentence.", voice, output))
                }
                val first = server.takeRequest()
                val second = server.takeRequest()
                assertEquals(0, first.sequenceNumber)
                assertEquals(1, second.sequenceNumber)
                org.junit.Assert.assertTrue(first.body.readUtf8().contains("en-AU-William:DragonHDLatestNeural"))
                org.junit.Assert.assertTrue(second.body.readUtf8().contains("en-US-Ava:DragonHDLatestNeural"))
            } finally { output.delete(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
        }
    }

    @Test fun voiceCatalogKeepsAttributesForVoiceDescriptionsAndFilters() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""[{"ShortName":"en-AU-William:DragonHDLatestNeural","DisplayName":"William","Locale":"en-AU","LocaleName":"English (Australia)","Gender":"Male","VoiceType":"Neural","SampleRateHertz":"24000","WordsPerMinute":"150","StyleList":["calm","friendly"],"SecondaryLocaleList":["en-NZ"]}]"""))
            val provider = AzureRestSpeechProvider(SpeechCredentials("test-key", "eastus"), OkHttpClient(), server.url("/cognitiveservices").toString().trimEnd('/'))

            val voice = provider.listVoices().single()

            assertEquals("en-AU-William:DragonHDLatestNeural", voice.id)
            assertEquals("English (Australia)", voice.localeName)
            assertEquals("Neural", voice.voiceType)
            assertEquals(24_000, voice.sampleRateHertz)
            assertEquals(150, voice.wordsPerMinute)
            assertEquals(listOf("calm", "friendly"), voice.styles)
            assertEquals(listOf("en-NZ"), voice.secondaryLocales)
            assertEquals("Neural HD", voice.azureQualityCategory())
            assertEquals("English (Australia) · Male · Neural HD", voice.azureDescription())
            assertEquals("test-key", server.takeRequest().getHeader("Ocp-Apim-Subscription-Key"))
        }
    }

    @Test fun azureQualityFilterDoesNotCallStandardVoicesHd() {
        assertEquals("Neural HD", SpeechVoice("en-US-Ava:DragonHDLatestNeural", "Ava", "en-US").azureQualityCategory())
        assertEquals("Neural", SpeechVoice("en-GB-SoniaNeural", "Sonia", "en-GB").azureQualityCategory())
        assertEquals("Standard", SpeechVoice("en-US-StandardA", "Standard A", "en-US", voiceType = "Standard").azureQualityCategory())
    }
}
