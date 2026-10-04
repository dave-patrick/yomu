package dev.yomu.app.speech

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.text.BreakIterator
import java.util.Locale

data class SentenceAudioChunk(
    val index: Int,
    val text: String,
    val startMs: Double,
    val endMs: Double,
    val pcmBytes: Long
)

data class SentenceChunkSynthesisResult(
    val outputFile: File,
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val chunks: List<SentenceAudioChunk>,
    val synthesisMs: Double
) {
    val durationMs: Double get() = chunks.lastOrNull()?.endMs ?: 0.0
}

/** Adds sentence alignment to a REST-only provider by synthesizing each sentence separately. */
class AzureRestSentenceChunkSynthesizer(private val provider: CloudSpeechProvider) {
    fun synthesize(
        text: String,
        voiceId: String,
        outputFile: File,
        scratchDir: File,
        elapsedRealtimeNanos: () -> Long
    ): SentenceChunkSynthesisResult {
        require(SpeechCapability.SYNTHESIS in provider.capabilities) { "Provider does not support synthesis" }
        val sentences = splitSentences(text)
        require(sentences.size >= 2) { "Sentence chunk test requires at least two sentences" }
        scratchDir.mkdirs()
        val scratchFiles = sentences.indices.map { File(scratchDir, "rest-sentence-${it + 1}.wav") }
        val synthStart = elapsedRealtimeNanos()
        try {
            sentences.forEachIndexed { index, sentence ->
                provider.synthesize(SpeechSynthesisRequest(sentence, voiceId, scratchFiles[index]))
            }
            val wavs = scratchFiles.map(::inspectWav)
            val format = wavs.first().format
            require(wavs.all { it.format == format }) { "Sentence audio formats differ" }
            val bytesPerSecond = format.sampleRate.toLong() * format.channels * (format.bitsPerSample / 8)
            val chunks = ArrayList<SentenceAudioChunk>(sentences.size)
            var totalBytes = 0L
            sentences.indices.forEach { index ->
                val wav = wavs[index]
                require(wav.dataBytes % (format.channels * (format.bitsPerSample / 8)) == 0L) {
                    "PCM chunk is not aligned to a complete sample frame"
                }
                val start = totalBytes.toDouble() * 1000.0 / bytesPerSecond
                totalBytes += wav.dataBytes
                val end = totalBytes.toDouble() * 1000.0 / bytesPerSecond
                chunks += SentenceAudioChunk(index, sentences[index], start, end, wav.dataBytes)
            }
            require(totalBytes <= 0xffff_ffffL - 36L) { "Combined WAV exceeds RIFF size limit" }
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).buffered().use { output ->
                writeWavHeader(output, format, totalBytes)
                wavs.forEach { wav -> copyPcm(wav, output) }
            }
            val elapsedMs = (elapsedRealtimeNanos() - synthStart) / 1e6
            return SentenceChunkSynthesisResult(outputFile, format.sampleRate, format.channels,
                format.bitsPerSample, chunks, elapsedMs)
        } finally {
            scratchFiles.forEach { it.delete() }
        }
    }

    companion object {
        fun durationMs(file: File): Double {
            val wav = inspectWav(file)
            val bytesPerSecond = wav.format.sampleRate.toLong() * wav.format.channels * (wav.format.bitsPerSample / 8)
            return wav.dataBytes.toDouble() * 1000.0 / bytesPerSecond
        }

        fun splitSentences(text: String, locale: Locale = Locale.UK): List<String> {
            val iterator = BreakIterator.getSentenceInstance(locale)
            iterator.setText(text)
            val sentences = ArrayList<String>()
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                val sentence = text.substring(start, end).trim()
                if (sentence.isNotEmpty()) sentences += sentence
                start = end
                end = iterator.next()
            }
            if (start < text.length) {
                val tail = text.substring(start).trim()
                if (tail.isNotEmpty()) sentences += tail
            }
            return sentences
        }

        private data class PcmFormat(val sampleRate: Int, val channels: Int, val bitsPerSample: Int)
        private data class WavData(val file: File, val dataOffset: Long, val dataBytes: Long, val format: PcmFormat)

        private fun inspectWav(file: File): WavData = RandomAccessFile(file, "r").use { wav ->
            require(file.length() >= 44) { "WAV is too short: ${file.name}" }
            require(readAscii(wav, 0, 4) == "RIFF" && readAscii(wav, 8, 4) == "WAVE") {
                "Expected a little-endian RIFF/WAVE file: ${file.name}"
            }
            var format: PcmFormat? = null
            var dataOffset = -1L
            var dataBytes = -1L
            var offset = 12L
            while (offset + 8L <= wav.length()) {
                wav.seek(offset)
                val id = ByteArray(4).also(wav::readFully).toString(StandardCharsets.US_ASCII)
                val size = readUInt32LE(wav)
                val payload = offset + 8L
                require(size <= wav.length() - payload) { "Malformed WAV chunk $id in ${file.name}" }
                when (id) {
                    "fmt " -> {
                        require(size >= 16L) { "WAV fmt chunk is too short" }
                        val encoding = readUInt16LE(wav)
                        val channels = readUInt16LE(wav)
                        val sampleRate = readUInt32LE(wav).toInt()
                        wav.seek(payload + 14L)
                        val bits = readUInt16LE(wav)
                        require(encoding == 1) { "Expected uncompressed PCM WAV" }
                        require(channels > 0 && sampleRate > 0 && bits in setOf(8, 16, 24, 32)) {
                            "Unsupported PCM format in ${file.name}"
                        }
                        format = PcmFormat(sampleRate, channels, bits)
                    }
                    "data" -> { dataOffset = payload; dataBytes = size; break }
                }
                offset = payload + size + (size and 1L)
            }
            val pcmFormat = format ?: error("Missing WAV fmt chunk in ${file.name}")
            require(dataOffset >= 0L && dataBytes >= 0L) { "Missing WAV data chunk in ${file.name}" }
            WavData(file, dataOffset, dataBytes, pcmFormat)
        }

        private fun readAscii(file: RandomAccessFile, offset: Long, length: Int): String {
            file.seek(offset)
            return ByteArray(length).also(file::readFully).toString(StandardCharsets.US_ASCII)
        }

        private fun readUInt16LE(file: RandomAccessFile): Int {
            val a = file.readUnsignedByte()
            val b = file.readUnsignedByte()
            return a or (b shl 8)
        }

        private fun readUInt32LE(file: RandomAccessFile): Long {
            val a = file.readUnsignedByte().toLong()
            val b = file.readUnsignedByte().toLong()
            val c = file.readUnsignedByte().toLong()
            val d = file.readUnsignedByte().toLong()
            return a or (b shl 8) or (c shl 16) or (d shl 24)
        }

        private fun writeWavHeader(output: OutputStream, format: PcmFormat, dataBytes: Long) {
            fun ascii(value: String) = output.write(value.toByteArray(StandardCharsets.US_ASCII))
            fun u16(value: Int) { output.write(value and 0xff); output.write((value ushr 8) and 0xff) }
            fun u32(value: Long) {
                repeat(4) { output.write(((value ushr (8 * it)) and 0xff).toInt()) }
            }
            val blockAlign = format.channels * format.bitsPerSample / 8
            ascii("RIFF"); u32(dataBytes + 36); ascii("WAVE")
            ascii("fmt "); u32(16); u16(1); u16(format.channels); u32(format.sampleRate.toLong())
            u32(format.sampleRate.toLong() * blockAlign); u16(blockAlign); u16(format.bitsPerSample)
            ascii("data"); u32(dataBytes)
        }

        private fun copyPcm(wav: WavData, output: OutputStream) {
            FileInputStream(wav.file).use { input ->
                var toSkip = wav.dataOffset
                while (toSkip > 0) {
                    val skipped = input.skip(toSkip)
                    if (skipped <= 0) error("Failed to seek PCM data in ${wav.file.name}")
                    toSkip -= skipped
                }
                val buffer = ByteArray(64 * 1024)
                var remaining = wav.dataBytes
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) error("Unexpected end of PCM data in ${wav.file.name}")
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
    }
}
