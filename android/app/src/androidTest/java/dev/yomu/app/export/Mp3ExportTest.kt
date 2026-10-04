package dev.yomu.app.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class Mp3ExportTest {
    @Test fun nativeLameEncoderAndId3ChapterWriterProduceChapterTaggedMp3() {
        val raw = File.createTempFile("yomu-raw", ".mp3")
        val tagged = File.createTempFile("yomu-tagged", ".mp3")
        try {
            LameEncoder(24_000, 1, 96).use { encoder ->
                raw.outputStream().buffered().use { output ->
                    val pcm = ShortArray(24_000) { i -> (sin(2.0 * PI * 440 * i / 24_000) * 8_000).toInt().toShort() }
                    output.write(encoder.encode(pcm))
                    output.write(encoder.flush())
                }
            }
            Id3ChapterWriter.write(raw, tagged, "Test export", listOf(
                AudioChapter("Part 1", 0, 500), AudioChapter("Part 2", 500, 1_000),
            ))
            val bytes = tagged.readBytes()
            val latin = bytes.toString(Charsets.ISO_8859_1)
            assertEquals("ID3", latin.substring(0, 3))
            assertEquals(4, bytes[3].toInt())
            assertTrue(latin.contains("CHAP"))
            assertTrue(latin.contains("CTOC"))
            assertTrue(bytes.size > raw.length())
        } finally { raw.delete(); tagged.delete() }
    }
}
