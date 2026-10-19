package dev.yomu.app.speech

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VoicePreviewCacheTest {
    private fun tempDir(): File = Files.createTempDirectory("yomu-previews").toFile()

    @Test fun cachedSampleIsReusedWithoutFetchingAgain() = runBlocking {
        val cache = tempDir()
        var fetches = 0
        val file = VoicePreviewCache.resolve(cache, VoicePreviewCache.AZURE, "en-GB-SoniaNeural", "wav") { target ->
            fetches += 1
            target.writeBytes(ByteArray(512) { 'a'.code.toByte() })
        }

        assertEquals(1, fetches)
        assertTrue(file.isFile)
        assertEquals(VoicePreviewCache.fileName(VoicePreviewCache.AZURE, "en-GB-SoniaNeural", "wav"), file.name)

        val again = VoicePreviewCache.resolve(cache, VoicePreviewCache.AZURE, "en-GB-SoniaNeural", "wav") { target ->
            fetches += 1
            target.writeBytes(ByteArray(512) { 'b'.code.toByte() })
        }

        assertEquals("A cached sample must not be fetched again.", 1, fetches)
        assertEquals(file.absolutePath, again.absolutePath)
    }

    @Test fun differentVoicesAreCachedSeparately() = runBlocking {
        val cache = tempDir()
        var fetches = 0
        val first = VoicePreviewCache.resolve(cache, VoicePreviewCache.AZURE, "voice-one", "wav") { target ->
            fetches += 1
            target.writeBytes(ByteArray(512))
        }
        val second = VoicePreviewCache.resolve(cache, VoicePreviewCache.AZURE, "voice-two", "wav") { target ->
            fetches += 1
            target.writeBytes(ByteArray(512))
        }

        assertEquals(2, fetches)
        assertFalse(first.absolutePath == second.absolutePath)
    }

    @Test fun providersDoNotShareCachedSamplesForTheSameVoiceId() = runBlocking {
        val cache = tempDir()
        val azure = VoicePreviewCache.resolve(cache, VoicePreviewCache.AZURE, "shared-id", "wav") { it.writeBytes(ByteArray(512)) }
        val fish = VoicePreviewCache.resolve(cache, VoicePreviewCache.FISH_AUDIO, "shared-id", "wav") { it.writeBytes(ByteArray(512)) }

        assertFalse(azure.absolutePath == fish.absolutePath)
    }

    @Test fun interruptedFetchLeavesNoUsableSample() = runBlocking {
        val cache = tempDir()
        val failed = runCatching {
            VoicePreviewCache.resolve(cache, VoicePreviewCache.AZURE, "broken-voice", "wav") { target ->
                target.writeBytes(ByteArray(8))
                throw IllegalStateException("network dropped")
            }
        }
        assertTrue(failed.isFailure)

        val partial = File(VoicePreviewCache.directory(cache), "${VoicePreviewCache.fileName(VoicePreviewCache.AZURE, "broken-voice", "wav")}.part")
        assertFalse("A partial download must not be left behind.", partial.exists())
        assertFalse(VoicePreviewCache.isUsable(VoicePreviewCache.file(cache, VoicePreviewCache.AZURE, "broken-voice", "wav")))
    }

    @Test fun truncatedSampleIsRefetched() = runBlocking {
        val cache = tempDir()
        val target = VoicePreviewCache.file(cache, VoicePreviewCache.AZURE, "short-voice", "wav")
        target.parentFile?.mkdirs()
        target.writeBytes(ByteArray(4))

        var fetches = 0
        VoicePreviewCache.resolve(cache, VoicePreviewCache.AZURE, "short-voice", "wav") { output ->
            fetches += 1
            output.writeBytes(ByteArray(512))
        }

        assertEquals("A truncated sample must be fetched again.", 1, fetches)
    }

    @Test fun clearingOneProviderKeepsTheOthers() = runBlocking {
        val cache = tempDir()
        VoicePreviewCache.resolve(cache, VoicePreviewCache.AZURE, "voice", "wav") { it.writeBytes(ByteArray(512)) }
        val fish = VoicePreviewCache.resolve(cache, VoicePreviewCache.FISH_AUDIO, "voice", "wav") { it.writeBytes(ByteArray(512)) }

        VoicePreviewCache.clearProvider(cache, VoicePreviewCache.AZURE)

        assertFalse(VoicePreviewCache.file(cache, VoicePreviewCache.AZURE, "voice", "wav").exists())
        assertTrue(fish.isFile)
    }

    @Test fun legacyTimestampedSamplesAreRemovedButCurrentOnesSurvive() = runBlocking {
        val cache = tempDir()
        val directory = VoicePreviewCache.directory(cache)
        directory.mkdirs()
        val legacy = File(directory, "fish-07d6ff793f4b40f59f47d602c54a4e8a-1792434696101.wav")
        val legacyMp3 = File(directory, "eleven-voiceABC-1792434696102.mp3")
        val current = File(directory, VoicePreviewCache.fileName(VoicePreviewCache.FISH_AUDIO, "voice-one", "wav"))
        listOf(legacy, legacyMp3, current).forEach { it.writeBytes(ByteArray(512)) }

        VoicePreviewCache.clearLegacySamples(cache)

        assertFalse("A timestamped leftover must be removed.", legacy.exists())
        assertFalse(legacyMp3.exists())
        assertTrue("A current sample must survive the cleanup.", current.exists())
    }

    @Test fun fetchRunsOffTheCallingThread() = runBlocking {
        val cache = tempDir()
        var fetchThread: String? = null
        var callerThread: String? = null
        VoicePreviewCache.resolve(cache, VoicePreviewCache.AZURE, "threaded-voice", "wav") { target ->
            fetchThread = Thread.currentThread().name
            target.writeBytes(ByteArray(512))
        }
        callerThread = Thread.currentThread().name

        assertFalse("The fetch must not run on the calling thread.", fetchThread == callerThread)
    }
}
