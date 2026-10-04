package dev.yomu.spike.speech

import com.microsoft.cognitiveservices.speech.PropertyId
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import java.io.File
import android.os.SystemClock

class AzureSdkSpeechProvider(
    private val credentials: SpeechCredentials,
    private val onNativeMappings: ((String, List<String>) -> Unit)? = null
) : CloudSpeechProvider {
    override val id = "azure-speech-sdk"
    override val capabilities = setOf(
        SpeechCapability.SYNTHESIS,
        SpeechCapability.WORD_AND_SENTENCE_TIMING
    )

    override fun listVoices(): List<SpeechVoice> = error("Voice catalog is currently exercised through the REST comparator")

    override fun synthesize(request: SpeechSynthesisRequest): SpeechSynthesisResult {
        val config = SpeechConfig.fromSubscription(credentials.key, credentials.region)
        config.speechSynthesisVoiceName = request.voiceId
        config.setProperty(PropertyId.SpeechServiceResponse_RequestSentenceBoundary, "true")
        config.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Riff24Khz16BitMonoPcm)
        val audio = AudioConfig.fromWavFileOutput(request.outputFile.path)
        val synthesizer = SpeechSynthesizer(config, audio)
        onNativeMappings?.invoke("synthesizer-created", sharedObjectMappings())
        val started = SystemClock.elapsedRealtimeNanos()
        val boundaries = mutableListOf<SpeechBoundary>()
        synthesizer.WordBoundary.addEventListener { _, event ->
            synchronized(boundaries) {
                boundaries += SpeechBoundary(
                    audioOffsetTicks100ns = event.audioOffset,
                    textOffset = event.textOffset.toInt(),
                    wordLength = event.wordLength.toInt(),
                    type = event.boundaryType.toString(),
                    callbackElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - started
                )
            }
        }
        try {
            val result = synthesizer.SpeakTextAsync(request.text).get()
            try {
                check(result.reason == ResultReason.SynthesizingAudioCompleted) { "Azure SDK synthesis did not complete" }
            } finally { result.close() }
            onNativeMappings?.invoke("synthesis-completed-before-close", sharedObjectMappings())
            return SpeechSynthesisResult(request.outputFile, "audio/wav", synchronized(boundaries) { boundaries.toList() })
        } finally {
            synthesizer.close()
            audio.close()
            config.close()
        }
    }

    private fun sharedObjectMappings(): List<String> = File("/proc/self/maps").readLines()
        .mapNotNull { line -> line.substringAfterLast(' ').takeIf { it.endsWith(".so", ignoreCase = true) } }
        .distinct()
}
