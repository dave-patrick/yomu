package dev.yomu.app.export

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

internal data class AudioChapter(val title: String, val startMs: Long, val endMs: Long)

/** Prepends an ID3v2.4 tag containing a title plus Pocket Casts-readable CHAP/CTOC frames. */
internal object Id3ChapterWriter {
    fun write(input: File, output: File, title: String, chapters: List<AudioChapter>) {
        require(chapters.isNotEmpty())
        val frames = ByteArrayOutputStream()
        frames.write(textFrame("TIT2", title))
        frames.write(textFrame("TPE1", "Yomu"))
        chapters.forEachIndexed { index, chapter ->
            val payload = ByteArrayOutputStream()
            payload.write("ch$index\u0000".toByteArray(Charsets.ISO_8859_1))
            payload.write(int32(chapter.startMs.coerceIn(0, 0xffffffffL)))
            payload.write(int32(chapter.endMs.coerceIn(chapter.startMs, 0xffffffffL)))
            payload.write(byteArrayOf(-1, -1, -1, -1, -1, -1, -1, -1)) // no byte offsets
            payload.write(textFrame("TIT2", chapter.title))
            frames.write(frame("CHAP", payload.toByteArray()))
        }
        val toc = ByteArrayOutputStream()
        toc.write("toc\u0000".toByteArray(Charsets.ISO_8859_1))
        toc.write(0x03) // top-level and ordered
        toc.write(chapters.size.coerceAtMost(255))
        chapters.take(255).forEachIndexed { index, _ -> toc.write("ch$index\u0000".toByteArray(Charsets.ISO_8859_1)) }
        toc.write(textFrame("TIT2", title))
        frames.write(frame("CTOC", toc.toByteArray()))

        val body = frames.toByteArray()
        val header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0) + synchsafe(body.size)
        output.parentFile?.mkdirs()
        FileOutputStream(output).buffered().use { out ->
            out.write(header); out.write(body)
            input.inputStream().buffered().use { it.copyTo(out) }
        }
    }

    private fun textFrame(id: String, text: String): ByteArray = frame(id, byteArrayOf(3) + text.toByteArray(Charsets.UTF_8))
    private fun frame(id: String, payload: ByteArray): ByteArray = id.toByteArray(Charsets.US_ASCII) + synchsafe(payload.size) + byteArrayOf(0, 0) + payload
    private fun synchsafe(value: Int): ByteArray {
        require(value in 0..0x0fffffff)
        return byteArrayOf(((value shr 21) and 0x7f).toByte(), ((value shr 14) and 0x7f).toByte(), ((value shr 7) and 0x7f).toByte(), (value and 0x7f).toByte())
    }
    private fun int32(value: Long) = byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())
}
