package dev.yomu.app.export

import java.io.Closeable
import java.io.RandomAccessFile

/** Reads uncompressed 16-bit PCM WAV chunks without loading a whole article into memory. */
internal class PcmWavReader(file: java.io.File) : Closeable {
    private val input = RandomAccessFile(file, "r")
    val sampleRate: Int
    val channels: Int
    private val dataOffset: Long
    private val dataLength: Long
    private var remaining: Long

    init {
        require(readAscii(4) == "RIFF") { "TTS generated a non-WAV audio file." }
        input.readIntLE()
        require(readAscii(4) == "WAVE") { "TTS generated an invalid WAV file." }
        var rate = 0
        var channelCount = 0
        var bits = 0
        var format = 0
        var dataStart = -1L
        var dataSize = -1L
        while (input.filePointer + 8 <= input.length() && dataStart < 0) {
            val id = readAscii(4)
            val size = input.readIntLE().toLong() and 0xffffffffL
            val start = input.filePointer
            when (id) {
                "fmt " -> {
                    format = input.readUnsignedShortLE()
                    channelCount = input.readUnsignedShortLE()
                    rate = input.readIntLE()
                    input.readIntLE(); input.readUnsignedShortLE()
                    bits = input.readUnsignedShortLE()
                }
                "data" -> { dataStart = start; dataSize = size }
            }
            input.seek(start + size + (size and 1L))
        }
        require(format == 1 && bits == 16 && channelCount in 1..2 && dataStart >= 0) { "MP3 export currently requires 16-bit PCM voice audio." }
        sampleRate = rate
        channels = channelCount
        dataOffset = dataStart
        dataLength = dataSize
        remaining = dataSize
        input.seek(dataOffset)
    }

    fun readSamples(maxSamples: Int = 8192): ShortArray {
        val count = minOf(maxSamples.toLong(), remaining / 2).toInt()
        if (count == 0) return ShortArray(0)
        val bytes = ByteArray(count * 2)
        input.readFully(bytes)
        remaining -= bytes.size
        return ShortArray(count) { i -> (((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 255))).toShort() }
    }

    override fun close() = input.close()
    private fun readAscii(length: Int) = ByteArray(length).also(input::readFully).toString(Charsets.US_ASCII)
    private fun RandomAccessFile.readUnsignedShortLE() = readUnsignedByte() or (readUnsignedByte() shl 8)
    private fun RandomAccessFile.readIntLE() = readUnsignedByte() or (readUnsignedByte() shl 8) or (readUnsignedByte() shl 16) or (readUnsignedByte() shl 24)
}
