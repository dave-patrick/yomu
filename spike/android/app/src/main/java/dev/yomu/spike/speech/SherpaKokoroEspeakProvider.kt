package dev.yomu.spike.speech

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File

class SherpaKokoroEspeakProvider(
    private val modelDirectory: File,
    threads: Int,
    phonemizerDataDirectory: File = File(modelDirectory, "espeak-ng-data")
) : OfflineSpeechProvider {
    private val espeakData = phonemizerDataDirectory
    private val engine = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
        numThreads = threads,
        kokoro = OfflineTtsKokoroModelConfig(
            model = File(modelDirectory, "model.onnx").path,
            voices = File(modelDirectory, "voices.bin").path,
            tokens = File(modelDirectory, "tokens.txt").path,
            dataDir = espeakData.path
        )
    )))
    override val id = "sherpa-onnx-kokoro-v0.19"
    override val phonemizer = PhonemizerInfo("espeak-ng", "GPL-3.0-or-later", espeakData)

    override fun generate(text: String, voiceId: Int, speed: Float): OfflineSpeechAudio {
        return OfflineSpeechAudio(engine.generate(text, sid = voiceId, speed = speed))
    }

    override fun close() = engine.release()
}
