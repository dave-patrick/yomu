package dev.yomu.spike

import android.app.Activity
import android.os.Bundle
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.webkit.WebView
import android.widget.*
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import net.dankito.readability4j.Readability4J
import dev.yomu.spike.speech.AzureRestSpeechProvider
import dev.yomu.spike.speech.AzureRestSentenceChunkSynthesizer
import dev.yomu.spike.speech.AzureSpeechProvider
import dev.yomu.spike.speech.AzureSdkSpeechProvider
import dev.yomu.spike.speech.CloudSpeechProvider
import dev.yomu.spike.speech.OfflineSpeechProvider
import dev.yomu.spike.speech.SherpaKokoroEspeakProvider
import dev.yomu.spike.speech.SpeechCredentials
import dev.yomu.spike.speech.SpeechSynthesisRequest
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Diagnostics only. No production UI or navigation. Credentials are read from private files. */
class SpikeActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var output: TextView
    private lateinit var web: WebView
    private var controller: MediaController? = null
    private var incrementalSentences: List<String> = emptyList()
    private val incrementalChunkProviders = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private var incrementalCurrentFile: File? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        output = TextView(this).apply { text = "Yomu spike — diagnostic actions only" }
        fun button(label: String, action: () -> Unit) { layout.addView(Button(this).apply { text = label; setOnClickListener { action() } }) }
        button("Benchmark Kokoro: 1 / 2 / 4 threads") { runWork { benchmarkTts() } }
        button("Fetch model from local spike host") { runWork { fetchModelArchive() } }
        button("Compare fixture extraction") { extraction(0) }
        button("Generate Azure alignment fixture") { runWork { azureFixture() } }
        button("Trace Azure SDK native libraries") { runWork { azureNativeLoadTrace() } }
        button("Generate Azure REST comparison") { runWork { azureRestFixture() } }
        button("Generate REST sentence-chunk prototype") { runWork { azureRestSentenceChunkFixture() } }
        button("Play REST chunks + sentence timing") { play(File(filesDir, "azure-rest-chunks.wav")); traceAzureRestChunks() }
        button("Generate longer article REST chunks") { runWork { azureRestLongArticleFixture() } }
        button("Play longer article + sentence timing") { play(File(filesDir, "azure-rest-long-article.wav")); traceAzureRestLongArticle() }
        button("Stream longer article REST chunks") { runWork { azureRestIncrementalPlayback() } }
        button("Simulate REST loss + Kokoro fallback") { runWork { azureRestIncrementalPlayback(simulateOutageAtSentence = 3) } }
        button("Test automatic return to online voice") { runWork { azureRestIncrementalPlayback(autoSwitchBack = true) } }
        button("Test PDF text + OCR extraction") { runWork { pdfOcrProbe() } }
        button("Test DOCX paragraphs + tables") { runWork { docxProbe() } }
        button("Run local Kokoro source-audit smoke") { runWork { offlineProviderSmokeTest() } }
        button("Test English-only eSpeak data subset") { runWork { englishEspeakSubsetSmokeTest() } }
        button("Test English subset across all Kokoro voices") { runWork { englishSubsetVoiceRegression() } }
        button("Play local Kokoro source-audit WAV") { play(File(filesDir, "kokoro-provider-smoke.wav")) }
        button("Play local benchmark WAV") { play(File(filesDir, "benchmark.wav")) }
        button("Play Azure fixture + timing trace") { play(File(filesDir, "azure.wav")); traceAzure() }
        button("Play 15-minute MP3 fixture") { play(File(filesDir, "chapters-v24.mp3")) }
        button("Generate 15-minute Android LAME MP3") { runWork { encodeLameFixture() } }
        button("Enable call-pause detection") {
            if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                enableCallMonitor()
            } else requestPermissions(arrayOf(android.Manifest.permission.READ_PHONE_STATE), 2)
        }
        button("Pause") { controller?.pause() }
        button("Resume") { controller?.play() }
        button("Set speed 1.5x") { controller?.setPlaybackSpeed(1.5f); Evidence.write(this, "playback_speed", mapOf("speed" to 1.5)) }
        button("Set speed 1x") { controller?.setPlaybackSpeed(1f); Evidence.write(this, "playback_speed", mapOf("speed" to 1.0)) }
        layout.addView(output)
        web = WebView(this).apply { settings.javaScriptEnabled = true; settings.allowFileAccess = false; settings.allowContentAccess = false; settings.blockNetworkLoads = true }
        web.loadDataWithBaseURL("https://yomu.invalid/", "<html><body>Local extraction harness</body></html>", "text/html", "UTF-8", null)
        setContentView(ScrollView(this).apply { addView(layout) })
        runProbeFromIntent(intent)
        val future = MediaController.Builder(this, SessionToken(this, ComponentName(this, SpikePlaybackService::class.java))).buildAsync()
        future.addListener({
            controller = future.get()
            controller?.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (mediaItem?.mediaId == "offline-switch-announcement") {
                        incrementalCurrentFile?.delete()
                        incrementalCurrentFile = mediaItem.localConfiguration?.uri?.path?.let(::File)
                        Evidence.write(this@SpikeActivity, "azure_rest_incremental_offline_announcement_started", mapOf(
                            "player_position_ms" to (controller?.currentPosition ?: 0L),
                        "uptime_ms" to SystemClock.elapsedRealtime(), "reason" to "provider_failover"))
                        output.append("\nOffline voice switch announcement")
                        return
                    }
                    val index = mediaItem?.mediaId?.substringAfterLast('-')?.toIntOrNull() ?: return
                    val now = SystemClock.elapsedRealtime()
                    val position = controller?.currentPosition ?: 0L
                    incrementalCurrentFile?.delete()
                    incrementalCurrentFile = mediaItem.localConfiguration?.uri?.path?.let(::File)
                    Evidence.write(this@SpikeActivity, "azure_rest_incremental_sentence_start", mapOf(
                        "sentence_index" to index, "player_position_ms" to position,
                        "uptime_ms" to now, "transition_reason" to reason,
                        "provider" to incrementalChunkProviders[index],
                        "sentence" to incrementalSentences.getOrNull(index).orEmpty()))
                    output.append("\nStreaming sentence ${index + 1}")
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && controller?.currentMediaItem?.mediaId?.startsWith("rest-sentence-") == true) {
                        Evidence.write(this@SpikeActivity, "azure_rest_incremental_playback_ended", mapOf(
                            "position_ms" to (controller?.currentPosition ?: 0L)))
                        File(cacheDir, "azure-rest-incremental").deleteRecursively()
                    }
                }
            })
            output.append("\nMedia session connected")
        }, mainExecutor)
        if (android.os.Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
    }
    private fun runWork(action: () -> Unit) { worker.execute { try { action(); runOnUiThread { output.append("\nDone; evidence.jsonl saved") } } catch (e: Exception) { Evidence.write(this, "failure", mapOf("type" to e.javaClass.simpleName)); runOnUiThread { output.append("\nFailed: ${e.javaClass.simpleName}; inspect logcat") } } } }
    private fun runProbeFromIntent(probeIntent: android.content.Intent) {
        when {
            probeIntent.getBooleanExtra("runKokoroBenchmark", false) -> runWork { benchmarkTts() }
            probeIntent.getBooleanExtra("runAzureSdkProbe", false) -> runWork { azureFixture() }
            probeIntent.getBooleanExtra("runAzureSdkNativeTrace", false) -> runWork { azureNativeLoadTrace() }
            probeIntent.getBooleanExtra("runAzureRestProbe", false) -> runWork { azureRestFixture() }
            probeIntent.getBooleanExtra("runAzureRestChunkProbe", false) -> runWork { azureRestSentenceChunkFixture() }
            probeIntent.getBooleanExtra("runAzureRestLongArticleProbe", false) -> runWork { azureRestLongArticleFixture() }
            probeIntent.getBooleanExtra("runAzureRestIncrementalProbe", false) -> runWork { azureRestIncrementalPlayback() }
            probeIntent.getBooleanExtra("runAzureRestFailoverProbe", false) -> runWork { azureRestIncrementalPlayback(simulateOutageAtSentence = 3) }
            probeIntent.getBooleanExtra("runAzureRestRecoveryProbe", false) -> runWork { azureRestIncrementalPlayback(autoSwitchBack = true) }
            probeIntent.getBooleanExtra("runPdfOcrProbe", false) -> runWork { pdfOcrProbe() }
            probeIntent.getBooleanExtra("runDocxProbe", false) -> runWork { docxProbe() }
            probeIntent.getBooleanExtra("runKokoroProviderProbe", false) -> runWork { offlineProviderSmokeTest() }
            probeIntent.getBooleanExtra("runEnglishEspeakSubsetProbe", false) -> runWork { englishEspeakSubsetSmokeTest() }
            probeIntent.getBooleanExtra("runEnglishSubsetVoiceRegression", false) -> runWork { englishSubsetVoiceRegression() }
        }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        runProbeFromIntent(intent)
    }
    @Deprecated("Permission callback used by this diagnostic harness")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) enableCallMonitor()
            else output.append("\nCall-pause monitor not enabled; media audio-focus handling remains active")
        }
    }
    private fun enableCallMonitor() {
        sendBroadcast(Intent(SpikePlaybackService.ACTION_ENABLE_CALL_MONITOR).setPackage(packageName))
        output.append("\nCall-state pause monitoring enabled")
    }
    private fun play(file: File) {
        if (!file.exists()) { output.append("\nMissing ${file.name}"); return }
        controller?.apply { setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file))); prepare(); play() }
    }
    private fun encodeLameFixture() {
        val wav = assets.open("lame-seed.wav").use { it.readBytes() }
        require(wav.size >= 44 && String(wav, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(wav, 8, 4, Charsets.US_ASCII) == "WAVE") { "LAME PCM seed is not a WAV file" }
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        var sampleRate = 0
        var channels = 0
        var bits = 0
        var pcmOffset = -1
        var pcmBytes = 0
        while (offset + 8 <= wav.size) {
            val chunkId = String(wav, offset, 4, Charsets.US_ASCII)
            val chunkSize = header.getInt(offset + 4).toLong() and 0xffffffffL
            val start = offset + 8
            require(chunkSize <= wav.size - start) { "Malformed WAV chunk" }
            if (chunkId == "fmt ") {
                require(header.getShort(start).toInt() == 1) { "Only PCM WAV is supported by this Spike" }
                channels = header.getShort(start + 2).toInt()
                sampleRate = header.getInt(start + 4)
                bits = header.getShort(start + 14).toInt()
            } else if (chunkId == "data") {
                pcmOffset = start
                pcmBytes = chunkSize.toInt()
                break
            }
            offset = start + chunkSize.toInt() + (chunkSize.toInt() and 1)
        }
        require(sampleRate > 0 && channels == 1 && bits == 16 && pcmOffset >= 0 && pcmBytes >= 2) {
            "Expected mono 16-bit PCM in the LAME seed"
        }
        val seed = ShortArray(pcmBytes / 2)
        ByteBuffer.wrap(wav, pcmOffset, pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(seed)
        val totalSamples = sampleRate * 900
        val chunk = ShortArray(8192)
        val destination = File(getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "lame-15-minute-base.mp3")
        destination.parentFile?.mkdirs()
        val startNs = SystemClock.elapsedRealtimeNanos()
        LameEncoder(sampleRate, channels, 96).use { encoder ->
            FileOutputStream(destination).buffered().use { stream ->
                var position = 0
                while (position < totalSamples) {
                    val count = minOf(chunk.size, totalSamples - position)
                    for (i in 0 until count) chunk[i] = seed[(position + i) % seed.size]
                    stream.write(encoder.encode(chunk, count))
                    position += count
                }
                stream.write(encoder.flush())
            }
        }
        Evidence.write(this, "android_lame_encode", mapOf("version" to "3.100", "license" to "LGPL-2.0-or-later",
            "decoder_included" to false, "sample_rate" to sampleRate, "channels" to channels, "bitrate_kbps" to 96,
            "duration_s" to 900, "input_seed_s" to seed.size.toDouble() / sampleRate,
            "encode_ms" to (SystemClock.elapsedRealtimeNanos() - startNs) / 1e6, "output_bytes" to destination.length(),
            "output_file" to destination.absolutePath))
        runOnUiThread { output.append("\\nAndroid LAME export ready: ${destination.absolutePath}") }
    }
    private fun fetchModelArchive() {
        val connection = (URL("http://127.0.0.1:8130/kokoro.tar.bz2").openConnection() as HttpURLConnection)
        connection.connectTimeout = 15000; connection.readTimeout = 120000
        try {
            connection.connect()
            check(connection.responseCode == 200) { "Model host returned HTTP ${connection.responseCode}" }
            val expected = connection.contentLengthLong
            val target = File(filesDir, "kokoro.tar.bz2.partial")
            var count = 0L
            connection.inputStream.use { input -> FileOutputStream(target).use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) { val n=input.read(buffer); if(n<0) break; output.write(buffer,0,n); count+=n }
            } }
            check(expected < 0 || count == expected) { "Model archive size mismatch" }
            val complete = File(filesDir, "kokoro.tar.bz2")
            check(target.renameTo(complete)) { "Could not finalize private model archive" }
            Evidence.write(this,"model_archive_download",mapOf("bytes" to count,"private_app_storage" to true,"sha256" to java.security.MessageDigest.getInstance("SHA-256").digest(complete.readBytes()).joinToString("") { "%02x".format(it) }))
        } finally { connection.disconnect() }
    }
    private fun benchmarkTts() {
        val root = File(filesDir, "kokoro-en-v0_19")
        val phonemes = File(root, "espeak-ng-data").walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val text = "Yomu lets us listen to documents while following their sentences. This benchmark measures local speech generation on this phone, including pronunciation and audio synthesis. Numbers, punctuation, and ordinary English prose are included. The quick brown fox jumps over the lazy dog."
        for (threads in listOf(1, 2, 4)) {
            val initStart = SystemClock.elapsedRealtimeNanos()
            val tts: OfflineSpeechProvider = SherpaKokoroEspeakProvider(root, threads)
            Evidence.write(this, "tts_init", mapOf("threads" to threads, "init_ms" to (SystemClock.elapsedRealtimeNanos()-initStart)/1e6,
                "phonemizer" to tts.phonemizer.id, "phonemizer_license" to tts.phonemizer.license,
                "phonemizer_data_bytes" to phonemes))
            try {
                for (iteration in 0..3) {
                    val active = AtomicBoolean(true)
                    var peakPss = 0; var peakRssKb = 0L
                    val sampler = Thread {
                        while (active.get()) {
                            val memory = Debug.MemoryInfo(); Debug.getMemoryInfo(memory)
                            peakPss = maxOf(peakPss, memory.totalPss)
                            val rss = File("/proc/self/status").readLines().firstOrNull { it.startsWith("VmRSS:") }?.trim()?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull() ?: 0
                            peakRssKb = maxOf(peakRssKb, rss); Thread.sleep(100)
                        }
                    }.apply { start() }
                    val cpuStart = Process.getElapsedCpuTime(); val start = SystemClock.elapsedRealtimeNanos()
                    var wallMs = 0.0; var cpuMs = 0L
                    val audio = try {
                        tts.generate(text, voiceId = 0, speed = 1f).also {
                            wallMs = (SystemClock.elapsedRealtimeNanos()-start)/1e6
                            cpuMs = Process.getElapsedCpuTime()-cpuStart
                        }
                    } finally { active.set(false); sampler.join() }
                    val durationMs = audio.samples.size.toDouble()/audio.sampleRate*1000
                    if (durationMs <= 0) error("Empty generation")
                    audio.save(File(filesDir, "benchmark.wav"))
                    Evidence.write(this, "tts_benchmark", mapOf("threads" to threads, "iteration" to iteration, "warmup" to (iteration==0),
                        "wall_ms" to wallMs, "cpu_ms" to cpuMs, "cpu_percent_one_core_basis" to 100*cpuMs/wallMs,
                        "audio_ms" to durationMs, "rtf" to wallMs/durationMs, "peak_pss_kb" to peakPss, "peak_rss_kb" to peakRssKb,
                        "model_version" to "Kokoro-82M-v0.19", "voice_id" to 0))
                }
            } finally { tts.close() }
        }
    }
    private fun pdfOcrProbe() {
        val fixtureNames = listOf("pdf-selectable.pdf", "pdf-scanned.pdf", "pdf-mixed.pdf")
        val missing = fixtureNames.filterNot { File(filesDir, it).isFile }
        check(missing.isEmpty()) { "Stage the PDF fixtures first: ${missing.joinToString()}" }
        check(File(filesDir, "pdf-password.pdf").isFile) { "Stage pdf-password.pdf first" }
        val tessRoot = File(filesDir, "tesseract")
        check(File(tessRoot, "tessdata/eng.traineddata").isFile) { "Stage Tesseract English trained data first" }
        val tess = com.googlecode.tesseract.android.TessBaseAPI()
        check(tess.init(tessRoot.absolutePath, "eng")) { "Tesseract could not initialize the English model" }
        val started = SystemClock.elapsedRealtime()
        val reports = org.json.JSONArray()
        try {
            for (name in fixtureNames) {
                val file = File(filesDir, name)
                val pageReports = org.json.JSONArray()
                android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                        for (index in 0 until renderer.pageCount) {
                            renderer.openPage(index).use { page ->
                                val pageStart = SystemClock.elapsedRealtime()
                                val selectable = if (android.os.Build.VERSION.SDK_INT >= 35) {
                                    page.textContents.joinToString("\n") { it.text }.trim()
                                } else ""
                                var source = "pdf-renderer-text"
                                var text = selectable
                                var ocrMs = 0L
                                var renderWidth = 0
                                var renderHeight = 0
                                if (text.length < 20) {
                                    source = "tesseract-ocr"
                                    renderWidth = (page.width * 3).coerceAtMost(3600)
                                    renderHeight = (page.height * 3).coerceAtMost(4800)
                                    val bitmap = android.graphics.Bitmap.createBitmap(renderWidth, renderHeight, android.graphics.Bitmap.Config.ARGB_8888)
                                    try {
                                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                        tess.setImage(bitmap)
                                        val ocrStart = SystemClock.elapsedRealtime()
                                        text = tess.getUTF8Text()?.trim().orEmpty()
                                        ocrMs = SystemClock.elapsedRealtime() - ocrStart
                                    } finally { bitmap.recycle() }
                                }
                                pageReports.put(org.json.JSONObject().put("page_index", index).put("source", source)
                                    .put("text_chars", text.length).put("text", text).put("ocr_ms", ocrMs)
                                    .put("render_width", renderWidth).put("render_height", renderHeight)
                                    .put("total_page_ms", SystemClock.elapsedRealtime() - pageStart))
                            }
                        }
                    }
                }
                reports.put(org.json.JSONObject().put("file", name).put("pages", pageReports))
            }
        } finally { tess.recycle() }
        fun readPasswordPdf(password: String): String {
            check(android.os.Build.VERSION.SDK_INT >= 35) { "Password-protected PDF support requires Android API 35+" }
            val protected = File(filesDir, "pdf-password.pdf")
            val descriptor = android.os.ParcelFileDescriptor.open(protected, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            return android.graphics.pdf.PdfRenderer(descriptor,
                android.graphics.pdf.LoadParams.Builder().setPassword(password).build()).use { renderer ->
                renderer.openPage(0).use { page -> page.textContents.joinToString("\n") { it.text }.trim() }
            }
        }
        val noPasswordResult = runCatching { readPasswordPdf("") }
        val wrongPasswordResult = runCatching { readPasswordPdf("incorrect") }
        val correctPasswordResult = runCatching { readPasswordPdf("YomuPass42") }
        val noPassword = noPasswordResult.exceptionOrNull()?.javaClass?.simpleName
        val wrongPassword = wrongPasswordResult.exceptionOrNull()?.javaClass?.simpleName
        val unlockedText = correctPasswordResult.getOrNull().orEmpty()
        val result = org.json.JSONObject().put("device", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("elapsed_ms", SystemClock.elapsedRealtime() - started).put("ocr_engine", "Tesseract4Android-4.9.0 / Tesseract 5.5.1")
            .put("pdf_text_engine", "Android PdfRenderer.Page.getTextContents when API >= 35")
            .put("fixtures", reports).put("password_test", org.json.JSONObject().put("no_password_failure", noPassword)
                .put("wrong_password_failure", wrongPassword).put("correct_password_text", unlockedText)
                .put("correct_password_failure", correctPasswordResult.exceptionOrNull()?.javaClass?.simpleName)
                .put("correct_password_error", correctPasswordResult.exceptionOrNull()?.message))
        File(filesDir, "pdf-ocr-probe.json").writeText(result.toString(2))
        Evidence.write(this, "pdf_ocr_probe_complete", mapOf("elapsed_ms" to result.getLong("elapsed_ms"), "fixtures" to fixtureNames))
        runOnUiThread { output.append("\nPDF/OCR extraction complete: ${result.getLong("elapsed_ms")} ms") }
    }

    private fun docxProbe() {
        val file = File(filesDir, "docx-fixture.docx")
        check(file.isFile) { "Stage docx-fixture.docx first" }
        require(file.length() <= 10L * 1024 * 1024) { "DOCX exceeds the Spike's 10 MB archive safety limit" }
        val started = SystemClock.elapsedRealtime()
        val paragraphs = mutableListOf<String>()
        var expandedBytes = 0
        java.util.zip.ZipFile(file).use { zip ->
            require(zip.size() <= 2048) { "DOCX has too many ZIP entries" }
            val entry = zip.getEntry("word/document.xml") ?: error("DOCX is missing word/document.xml")
            val bytes = zip.getInputStream(entry).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    expandedBytes += count
                    require(expandedBytes <= 20 * 1024 * 1024) { "DOCX expanded XML exceeds the 20 MB Spike limit" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
            parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            try { parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_DOCDECL, false) } catch (_: Exception) { }
            parser.setInput(java.io.ByteArrayInputStream(bytes), "UTF-8")
            var insideParagraph = false
            var current = StringBuilder()
            while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                        "p" -> { insideParagraph = true; current = StringBuilder() }
                        "t" -> if (insideParagraph) current.append(parser.nextText())
                        "tab" -> if (insideParagraph) current.append('\t')
                        "br" -> if (insideParagraph) current.append('\n')
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name == "p" && insideParagraph) {
                        current.toString().trim().takeIf(String::isNotEmpty)?.let(paragraphs::add)
                        insideParagraph = false
                    }
                }
                parser.next()
            }
        }
        val result = org.json.JSONObject().put("file", file.name).put("engine", "Android XmlPullParser + java.util.zip.ZipFile")
            .put("archive_bytes", file.length()).put("expanded_document_xml_bytes", expandedBytes)
            .put("elapsed_ms", SystemClock.elapsedRealtime() - started).put("paragraph_count", paragraphs.size)
            .put("paragraphs", org.json.JSONArray(paragraphs)).put("reading_text", paragraphs.joinToString("\n\n"))
            .put("limits", "DOCX document.xml paragraphs/tables only; no legacy DOC, footnotes, comments, tracked-change semantics, or embedded-object OCR")
        File(filesDir, "docx-probe.json").writeText(result.toString(2))
        Evidence.write(this, "docx_probe_complete", mapOf("elapsed_ms" to result.getLong("elapsed_ms"),
            "paragraph_count" to paragraphs.size, "archive_bytes" to file.length()))
        runOnUiThread { output.append("\nDOCX extraction complete: ${paragraphs.size} paragraphs") }
    }

    private fun offlineProviderSmokeTest() {
        val provider: OfflineSpeechProvider = SherpaKokoroEspeakProvider(File(filesDir, "kokoro-en-v0_19"), 2)
        try {
            val audio = provider.generate("Yomu offline provider check.", voiceId = 0, speed = 1f)
            check(audio.samples.isNotEmpty() && audio.durationMs() > 0)
            val outputFile = File(filesDir, "kokoro-provider-smoke.wav")
            audio.save(outputFile)
            Evidence.write(this, "offline_provider_smoke", mapOf(
                "provider" to provider.id,
                "phonemizer" to provider.phonemizer.id,
                "phonemizer_license" to provider.phonemizer.license,
                "audio_ms" to audio.durationMs(),
                "sample_rate" to audio.sampleRate,
                "output_bytes" to outputFile.length()
            ))
        } finally { provider.close() }
    }
    private fun englishEspeakSubsetSmokeTest() {
        val modelRoot = File(filesDir, "kokoro-en-v0_19")
        val full = File(modelRoot, "espeak-ng-data")
        val subset = File(modelRoot, "espeak-ng-data-english-subset")
        subset.deleteRecursively()
        subset.mkdirs()
        val topLevelFiles = listOf("intonations", "phondata", "phondata-manifest", "phonindex", "phontab", "en_dict")
        topLevelFiles.forEach { name -> File(full, name).copyTo(File(subset, name), overwrite = true) }
        listOf("lang/gmw", "voices/!v").forEach { relative ->
            val source = File(full, relative)
            source.walkTopDown().filter { it.isFile }.forEach { file ->
                val target = File(subset, file.relativeTo(full).path)
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = true)
            }
        }
        val subsetBytes = subset.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val provider = SherpaKokoroEspeakProvider(modelRoot, 2, subset)
        try {
            val audio = provider.generate("English-only phonemizer subset. We can test British, Scottish, Irish, Australian, and American text with the Kokoro voice selected separately.", voiceId = 0, speed = 1f)
            check(audio.samples.isNotEmpty() && audio.durationMs() > 0)
            val outputFile = File(filesDir, "kokoro-english-subset-smoke.wav")
            audio.save(outputFile)
            Evidence.write(this, "english_espeak_subset_smoke", mapOf(
                "provider" to provider.id,
                "subset_data_bytes" to subsetBytes,
                "full_data_bytes" to full.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                "reduction_percent" to (1.0 - subsetBytes.toDouble() / full.walkTopDown().filter { it.isFile }.sumOf { it.length() }) * 100.0,
                "audio_ms" to audio.durationMs(),
                "sample_rate" to audio.sampleRate,
                "output_bytes" to outputFile.length()
            ))
        } finally { provider.close() }
        val noVoiceFiles = File(modelRoot, "espeak-ng-data-english-no-voices")
        noVoiceFiles.deleteRecursively()
        noVoiceFiles.mkdirs()
        topLevelFiles.forEach { name -> File(full, name).copyTo(File(noVoiceFiles, name), overwrite = true) }
        val gmwSource = File(full, "lang/gmw")
        gmwSource.walkTopDown().filter { it.isFile }.forEach { file ->
            val target = File(noVoiceFiles, file.relativeTo(full).path)
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = true)
        }
        val noVoiceBytes = noVoiceFiles.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val minimalProvider = SherpaKokoroEspeakProvider(modelRoot, 2, noVoiceFiles)
        try {
            val audio = minimalProvider.generate("English pronunciation still works without eSpeak voice variants.", voiceId = 0, speed = 1f)
            check(audio.samples.isNotEmpty() && audio.durationMs() > 0)
            Evidence.write(this, "english_espeak_minimal_subset_smoke", mapOf(
                "subset_data_bytes" to noVoiceBytes,
                "audio_ms" to audio.durationMs(),
                "sample_rate" to audio.sampleRate,
                "voice_variant_files_included" to false
            ))
        } finally { minimalProvider.close() }
    }
    private fun englishSubsetVoiceRegression() {
        val modelRoot = File(filesDir, "kokoro-en-v0_19")
        val full = File(modelRoot, "espeak-ng-data")
        val subset = File(modelRoot, "espeak-ng-data-english-regression")
        subset.deleteRecursively()
        subset.mkdirs()
        listOf("intonations", "phondata", "phondata-manifest", "phonindex", "phontab", "en_dict").forEach { name ->
            File(full, name).copyTo(File(subset, name), overwrite = true)
        }
        File(full, "lang/gmw").walkTopDown().filter { it.isFile }.forEach { file ->
            val target = File(subset, file.relativeTo(full).path)
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = true)
        }
        val corpus = "Dr. O'Neill paid \$12.50 on 03/04/2026. The U.S. and U.K. teams met in St. John's; the 2nd item was ready at 7:45 p.m. “Well,” she said, “we're testing English numbers, abbreviations, punctuation—and quoted speech.”"
        for (voiceId in 0..10) {
            val provider = SherpaKokoroEspeakProvider(modelRoot, 2, subset)
            try {
                val audio = provider.generate(corpus, voiceId = voiceId, speed = 1f)
                check(audio.samples.isNotEmpty() && audio.durationMs() > 0) { "Kokoro voice $voiceId returned no audio" }
                Evidence.write(this, "english_subset_voice_regression", mapOf(
                    "voice_id" to voiceId,
                    "subset_data_bytes" to subset.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                    "audio_ms" to audio.durationMs(),
                    "sample_rate" to audio.sampleRate,
                    "corpus_chars" to corpus.length,
                    "espeak_voice_variants_included" to false,
                    "result" to "pass"
                ))
            } finally { provider.close() }
        }
    }
    private val fixtureNames = listOf("twz", "9to5google")
    private val fixtureUrls = listOf("https://www.twz.com/air/full-f-47-production-line-not-too-far-away-usaf-general", "https://9to5google.com/2026/09/30/facer-wear-os-library-update-timex/")
    private fun extraction(index: Int) {
        if (index >= fixtureNames.size) { output.append("\nExtraction comparison finished"); return }
        worker.execute {
            try {
                val html = assets.open("${fixtureNames[index]}.html").bufferedReader().use { it.readText() }
                for (run in 0..5) {
                    val start = SystemClock.elapsedRealtimeNanos()
                    val article = Readability4J(fixtureUrls[index], html).parse()
                    val extracted = article.textContent ?: ""
                    val cleaned = ArticleTextCleaner.clean(extracted)
                    Evidence.write(this, "extraction", mapOf("engine" to "Readability4J-1.0.8", "fixture" to fixtureNames[index], "run" to run, "warmup" to (run==0),
                        "parse_ms" to (SystemClock.elapsedRealtimeNanos()-start)/1e6, "characters" to extracted.length,
                        "cleaned_characters" to cleaned.length))
                    File(filesDir, "${fixtureNames[index]}-readability4j.txt").writeText(extracted)
                    File(filesDir, "${fixtureNames[index]}-readability4j-clean.txt").writeText(cleaned)
                }
                val js = assets.open("Readability.js").bufferedReader().use { it.readText() }
                runOnUiThread {
                    val script = "(function(){ $js; const html=${JSONObject.quote(html)}; const rows=[]; let text=''; for(let n=0;n<6;n++){let doc=new DOMParser().parseFromString(html,'text/html'); let t=performance.now(); let a=new Readability(doc).parse(); text=a?a.textContent:''; rows.push({run:n,warmup:n===0,parse_ms:performance.now()-t,characters:text.length});}return JSON.stringify({rows:rows,text:text});})()"
                    web.evaluateJavascript(script) { raw ->
                        try {
                            val decoded = org.json.JSONTokener(raw).nextValue() as String
                            val data = JSONObject(decoded); val rows = data.getJSONArray("rows")
                            val extracted = data.getString("text")
                            val cleaned = ArticleTextCleaner.clean(extracted)
                            File(filesDir, "${fixtureNames[index]}-mozilla.txt").writeText(extracted)
                            File(filesDir, "${fixtureNames[index]}-mozilla-clean.txt").writeText(cleaned)
                            for (n in 0 until rows.length()) { val row=rows.getJSONObject(n); Evidence.write(this, "extraction", mapOf("engine" to "Mozilla-WebView", "fixture" to fixtureNames[index], "run" to n, "warmup" to (n==0), "parse_ms" to row.getDouble("parse_ms"), "characters" to row.getInt("characters"), "cleaned_characters" to cleaned.length)) }
                            extraction(index+1)
                        } catch (e: Exception) { output.append("\nMozilla extraction failed: ${e.javaClass.simpleName}") }
                    }
                }
            } catch (e: Exception) { runOnUiThread { output.append("\nExtraction failed: ${e.javaClass.simpleName}") } }
        }
    }
    private fun azureCredentials(): SpeechCredentials {
        val json = JSONObject(File(filesDir, "azure-credentials.json").readText())
        require(json.optString("tier") == "F0") { "Confirm F0 resource" }
        return SpeechCredentials(json.getString("key"), json.getString("region"))
    }

    private fun azureFixture() {
        val provider: CloudSpeechProvider = AzureSpeechProvider(azureCredentials())
        check(dev.yomu.spike.speech.SpeechCapability.WORD_AND_SENTENCE_TIMING in provider.capabilities)
        val voices = provider.listVoices()
        check(voices.any { it.id == "en-GB-SoniaNeural" }) { "Selected Azure voice missing from catalog" }
        val outputFile = File(filesDir, "azure.wav")
        val result = provider.synthesize(SpeechSynthesisRequest(
            text = "Yomu reads the first sentence. Now it reads a second sentence, with a short pause. Finally, we verify timing against the audio playback position.",
            voiceId = "en-GB-SoniaNeural",
            outputFile = outputFile
        ))
        val boundaries = org.json.JSONArray()
        result.boundaries.forEach { boundary ->
            boundaries.put(JSONObject().put("audio_ms", boundary.audioOffsetMs)
                .put("callback_wall_ms", (boundary.callbackElapsedRealtimeNanos ?: 0L) / 1e6)
                .put("text_offset", boundary.textOffset).put("word_length", boundary.wordLength)
                .put("boundary_type", boundary.type))
        }
        File(filesDir, "azure-boundaries.json").writeText(boundaries.toString(2))
        Evidence.write(this, "azure_synthesis", mapOf("provider" to provider.id, "voice_count" to voices.size,
            "selected_voice_available" to true, "boundaries" to boundaries.length(),
            "wav_bytes" to result.outputFile.length(), "tier_claim" to "F0-user-configured"))
    }

    /** Captures this process' loaded Azure/GStreamer native modules around a PCM WAV synthesis. */
    private fun azureNativeLoadTrace() {
        fun relevantMappings(): List<String> = File("/proc/self/maps").readLines()
            .mapNotNull { it.substringAfterLast(' ').takeIf { path -> path.endsWith(".so", ignoreCase = true) } }
            .distinct()
        val before = relevantMappings()
        val phases = linkedMapOf<String, List<String>>()
        val provider: CloudSpeechProvider = AzureSdkSpeechProvider(azureCredentials()) { phase, mappings ->
            phases[phase] = mappings
        }
        val result = provider.synthesize(SpeechSynthesisRequest(
            text = "This diagnostic checks which native SDK modules load for WAV speech output.",
            voiceId = "en-GB-SoniaNeural",
            outputFile = File(filesDir, "azure-native-trace.wav")
        ))
        val after = relevantMappings()
        val during = phases["synthesis-completed-before-close"] ?: emptyList()
        File(filesDir, "azure-native-maps-before.txt").writeText(before.joinToString("\n"))
        File(filesDir, "azure-native-maps-after.txt").writeText(after.joinToString("\n"))
        val report = JSONObject()
            .put("provider", provider.id)
            .put("operation", "PCM WAV synthesis")
            .put("voice_id", "en-GB-SoniaNeural")
            .put("wav_bytes", result.outputFile.length())
            .put("before", org.json.JSONArray(before))
            .put("synthesizer_created", org.json.JSONArray(phases["synthesizer-created"] ?: emptyList<String>()))
            .put("during_synthesis", org.json.JSONArray(during))
            .put("after", org.json.JSONArray(after))
            .put("new_mappings", org.json.JSONArray(during.filterNot { it in before }))
            .put("codec_extension_loaded", during.any { it.contains("extension.codec", ignoreCase = true) })
            .put("gstreamer_loaded", during.any { it.contains("gstreamer", ignoreCase = true) })
        File(filesDir, "azure-native-load-trace.json").writeText(report.toString(2))
        Evidence.write(this, "azure_sdk_native_load_trace", mapOf(
            "provider" to provider.id,
            "wav_bytes" to result.outputFile.length(),
            "azure_mapping_count_before" to before.size,
            "azure_mapping_count_during_synthesis" to during.size,
            "codec_extension_loaded" to (report.getBoolean("codec_extension_loaded")),
            "gstreamer_loaded" to report.getBoolean("gstreamer_loaded")))
        runOnUiThread { output.append("\nAzure SDK native trace ready; review azure-native-load-trace.json") }
    }

    /** REST comparator: confirms voice discovery and synthesis through the provider contract. */
    private fun azureRestFixture() {
        val provider: CloudSpeechProvider = AzureRestSpeechProvider(azureCredentials())
        check(dev.yomu.spike.speech.SpeechCapability.WORD_AND_SENTENCE_TIMING !in provider.capabilities)
        val voicesStart = SystemClock.elapsedRealtimeNanos()
        val voices = provider.listVoices()
        val voiceListMs = (SystemClock.elapsedRealtimeNanos() - voicesStart) / 1e6
        val synthStart = SystemClock.elapsedRealtimeNanos()
        val result = provider.synthesize(SpeechSynthesisRequest(
            text = "Yomu REST comparison. This fixture checks audio output and playback highlighting metadata.",
            voiceId = "en-GB-SoniaNeural",
            outputFile = File(filesDir, "azure-rest.wav")
        ))
        Evidence.write(this, "azure_rest_synthesis", mapOf(
            "provider" to provider.id,
            "voice_count" to voices.size,
            "has_en_GB_SoniaNeural" to voices.any { it.id == "en-GB-SoniaNeural" },
            "voice_list_ms" to voiceListMs,
            "synthesis_ms" to (SystemClock.elapsedRealtimeNanos() - synthStart) / 1e6,
            "wav_bytes" to result.outputFile.length(),
            "response" to JSONObject(result.responseMetadata).put("content_type", result.mediaType).toString(),
            "boundary_callbacks" to result.boundaries.size,
            "capabilities" to provider.capabilities.map { it.name }
        ))
    }
    private fun azureRestSentenceChunkFixture() {
        val provider: CloudSpeechProvider = AzureRestSpeechProvider(azureCredentials())
        check(dev.yomu.spike.speech.SpeechCapability.WORD_AND_SENTENCE_TIMING !in provider.capabilities)
        val voiceId = "en-GB-SoniaNeural"
        val text = "Yomu can align REST speech one sentence at a time. This second sentence checks the natural pause between requests. Finally, the combined waveform should follow each sentence in order."
        val sentences = AzureRestSentenceChunkSynthesizer.splitSentences(text)
        check(sentences.size == 3) { "Expected 3 test sentences; parser returned ${sentences.size}" }

        val singleFile = File(filesDir, "azure-rest-single.wav")
        val singleStart = SystemClock.elapsedRealtimeNanos()
        provider.synthesize(SpeechSynthesisRequest(text, voiceId, singleFile))
        val singleElapsedMs = (SystemClock.elapsedRealtimeNanos() - singleStart) / 1e6
        val singleDurationMs = AzureRestSentenceChunkSynthesizer.durationMs(singleFile)

        val chunked = AzureRestSentenceChunkSynthesizer(provider).synthesize(
            text = text,
            voiceId = voiceId,
            outputFile = File(filesDir, "azure-rest-chunks.wav"),
            scratchDir = File(cacheDir, "azure-rest-sentence-chunks"),
            elapsedRealtimeNanos = SystemClock::elapsedRealtimeNanos
        )
        val chunksJson = org.json.JSONArray()
        chunked.chunks.forEach { chunk ->
            chunksJson.put(JSONObject()
                .put("index", chunk.index)
                .put("text", chunk.text)
                .put("start_ms", chunk.startMs)
                .put("end_ms", chunk.endMs)
                .put("duration_ms", chunk.endMs - chunk.startMs)
                .put("pcm_bytes", chunk.pcmBytes))
        }
        val report = JSONObject()
            .put("provider", provider.id)
            .put("alignment", "PCM chunk byte offsets in concatenated WAV; ExoPlayer content position drives active sentence")
            .put("voice_id", voiceId)
            .put("text", text)
            .put("sentences", chunksJson)
            .put("sentence_count", chunked.chunks.size)
            .put("rest_requests", chunked.chunks.size + 1)
            .put("single_request_ms", singleElapsedMs)
            .put("single_request_duration_ms", singleDurationMs)
            .put("chunked_requests_ms", chunked.synthesisMs)
            .put("chunked_duration_ms", chunked.durationMs)
            .put("chunked_wav_bytes", chunked.outputFile.length())
            .put("sample_rate", chunked.sampleRate)
            .put("channels", chunked.channels)
            .put("bits_per_sample", chunked.bitsPerSample)
            .put("sdk_timing_callbacks", false)
        File(filesDir, "azure-rest-sentence-chunks.json").writeText(report.toString(2))
        Evidence.write(this, "azure_rest_sentence_chunks", mapOf(
            "voice_id" to voiceId,
            "sentence_count" to chunked.chunks.size,
            "rest_requests" to chunked.chunks.size + 1,
            "single_request_ms" to singleElapsedMs,
            "single_duration_ms" to singleDurationMs,
            "chunked_requests_ms" to chunked.synthesisMs,
            "chunked_duration_ms" to chunked.durationMs,
            "wav_bytes" to chunked.outputFile.length(),
            "sample_rate" to chunked.sampleRate
        ))
        runOnUiThread {
            output.append("\nREST chunk prototype ready: ${chunked.chunks.size} sentences, ${chunked.durationMs.toLong()} ms. Use Play REST chunks + sentence timing.")
        }
    }

    private fun traceAzureRestChunks() {
        val mapFile = File(filesDir, "azure-rest-sentence-chunks.json")
        if (!mapFile.exists()) { output.append("\nGenerate the REST sentence chunks first"); return }
        val chunks = org.json.JSONArray(JSONObject(mapFile.readText()).getJSONArray("sentences").toString())
        var cursor = -1
        val handler = android.os.Handler(mainLooper)
        val started = SystemClock.elapsedRealtime()
        handler.post(object : Runnable {
            override fun run() {
                val player = controller ?: return
                if (player.currentMediaItem?.localConfiguration?.uri?.lastPathSegment != "azure-rest-chunks.wav") return
                val position = player.currentPosition.toDouble()
                if (player.isPlaying) {
                    var active = cursor
                    for (i in 0 until chunks.length()) {
                        if (chunks.getJSONObject(i).getDouble("start_ms") <= position) active = i else break
                    }
                    if (active != cursor && active >= 0) {
                        cursor = active
                        val chunk = chunks.getJSONObject(active)
                        val startMs = chunk.getDouble("start_ms")
                        val row = mapOf(
                            "sentence_index" to active,
                            "expected_chunk_start_ms" to startMs,
                            "observed_player_ms" to position,
                            "highlight_lateness_ms" to (position - startMs),
                            "playback_speed" to player.playbackParameters.speed,
                            "sentence" to chunk.getString("text")
                        )
                        Evidence.write(this@SpikeActivity, "azure_rest_sentence_highlight", row)
                        runOnUiThread { output.append("\nSentence ${active + 1}: ${chunk.getString("text")}") }
                    }
                }
                if (player.playbackState != androidx.media3.common.Player.STATE_ENDED &&
                    SystemClock.elapsedRealtime() - started < 180_000) handler.postDelayed(this, 20)
            }
        })
    }
    private fun azureRestLongArticleFixture() {
        val provider: CloudSpeechProvider = AzureRestSpeechProvider(azureCredentials())
        check(dev.yomu.spike.speech.SpeechCapability.SYNTHESIS in provider.capabilities)
        val source = File(filesDir, "azure-rest-long-article.txt")
        val text = source.readText().trim()
        require(text.length >= 500) { "Long-article fixture is missing or unexpectedly short" }
        val sentences = AzureRestSentenceChunkSynthesizer.splitSentences(text)
        require(sentences.size >= 8) { "Expected a longer article with at least eight sentence chunks" }
        val voiceId = "en-GB-SoniaNeural"
        val chunked = AzureRestSentenceChunkSynthesizer(provider).synthesize(
            text = text,
            voiceId = voiceId,
            outputFile = File(filesDir, "azure-rest-long-article.wav"),
            scratchDir = File(cacheDir, "azure-rest-long-article-sentences"),
            elapsedRealtimeNanos = SystemClock::elapsedRealtimeNanos
        )
        val chunksJson = org.json.JSONArray()
        chunked.chunks.forEach { chunk ->
            chunksJson.put(JSONObject().put("index", chunk.index).put("text", chunk.text)
                .put("start_ms", chunk.startMs).put("end_ms", chunk.endMs)
                .put("duration_ms", chunk.endMs - chunk.startMs).put("pcm_bytes", chunk.pcmBytes))
        }
        val report = JSONObject().put("provider", provider.id)
            .put("fixture", "9to5google-cleaned-extraction")
            .put("alignment", "PCM sentence chunk offsets in concatenated WAV; ExoPlayer content position drives active sentence")
            .put("voice_id", voiceId).put("text", text).put("sentences", chunksJson)
            .put("sentence_count", chunked.chunks.size).put("rest_requests", chunked.chunks.size)
            .put("chunked_requests_ms", chunked.synthesisMs).put("chunked_duration_ms", chunked.durationMs)
            .put("chunked_wav_bytes", chunked.outputFile.length()).put("sample_rate", chunked.sampleRate)
            .put("channels", chunked.channels).put("bits_per_sample", chunked.bitsPerSample)
            .put("sdk_timing_callbacks", false)
        File(filesDir, "azure-rest-long-article.json").writeText(report.toString(2))
        Evidence.write(this, "azure_rest_long_article", mapOf(
            "fixture" to "9to5google-cleaned-extraction", "voice_id" to voiceId,
            "characters" to text.length, "sentence_count" to chunked.chunks.size,
            "rest_requests" to chunked.chunks.size, "chunked_requests_ms" to chunked.synthesisMs,
            "chunked_duration_ms" to chunked.durationMs, "wav_bytes" to chunked.outputFile.length(),
            "sample_rate" to chunked.sampleRate))
        runOnUiThread { output.append("\nLong article ready: ${text.length} characters, ${chunked.chunks.size} sentences, ${chunked.durationMs.toLong()} ms") }
    }

    private fun traceAzureRestLongArticle() {
        val mapFile = File(filesDir, "azure-rest-long-article.json")
        if (!mapFile.exists()) { output.append("\nGenerate the longer article first"); return }
        val chunks = JSONObject(mapFile.readText()).getJSONArray("sentences")
        var cursor = -1
        val handler = android.os.Handler(mainLooper)
        val started = SystemClock.elapsedRealtime()
        handler.post(object : Runnable {
            override fun run() {
                val player = controller ?: return
                if (player.currentMediaItem?.localConfiguration?.uri?.lastPathSegment != "azure-rest-long-article.wav") return
                val position = player.currentPosition.toDouble()
                if (player.isPlaying) {
                    var active = cursor
                    for (i in 0 until chunks.length()) {
                        if (chunks.getJSONObject(i).getDouble("start_ms") <= position) active = i else break
                    }
                    if (active != cursor && active >= 0) {
                        cursor = active
                        val chunk = chunks.getJSONObject(active)
                        Evidence.write(this@SpikeActivity, "azure_rest_long_article_highlight", mapOf(
                            "sentence_index" to active,
                            "expected_chunk_start_ms" to chunk.getDouble("start_ms"),
                            "observed_player_ms" to position,
                            "highlight_lateness_ms" to position - chunk.getDouble("start_ms"),
                            "playback_speed" to player.playbackParameters.speed,
                            "sentence" to chunk.getString("text")))
                        runOnUiThread { output.append("\nArticle sentence ${active + 1}/${chunks.length()}: ${chunk.getString("text")}") }
                    }
                }
                if (player.playbackState != androidx.media3.common.Player.STATE_ENDED &&
                    SystemClock.elapsedRealtime() - started < 300_000) handler.postDelayed(this, 20)
            }
        })
    }

    /** Streams a long article by synthesizing a two-sentence head start, then appending each sentence. */
    private fun azureRestIncrementalPlayback(simulateOutageAtSentence: Int? = null, autoSwitchBack: Boolean = false) {
        val article = File(filesDir, "azure-rest-long-article.txt").readText().trim()
        require(article.length >= 500) { "Long-article fixture is missing or unexpectedly short" }
        val sentences = AzureRestSentenceChunkSynthesizer.splitSentences(article)
        require(sentences.size >= 3) { "Incremental playback requires at least three sentences" }
        val provider = AzureRestSpeechProvider(azureCredentials())
        val voiceId = "en-GB-SoniaNeural"
        val chunkDir = File(cacheDir, "azure-rest-incremental")
        chunkDir.deleteRecursively()
        chunkDir.mkdirs()
        val startedAt = SystemClock.elapsedRealtime()
        val rows = org.json.JSONArray()
        incrementalChunkProviders.clear()
        incrementalCurrentFile = null
        var offlineProvider: OfflineSpeechProvider? = null
        var switchedOffline = false
        var offlineAnnouncementQueued = false
        val modes = mutableListOf<String>()

        fun hasValidatedInternet(): Boolean {
            val connectivity = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivity.activeNetwork ?: return false
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        fun makeItem(id: String, file: File) = MediaItem.Builder().setMediaId(id)
            .setUri(android.net.Uri.fromFile(file)).build()

        fun synthesizeOffline(text: String, file: File): Double {
            val fallback = offlineProvider ?: SherpaKokoroEspeakProvider(File(filesDir, "kokoro-en-v0_19"), 2)
                .also { offlineProvider = it }
            val start = SystemClock.elapsedRealtimeNanos()
            val generated = fallback.generate(text, voiceId = 0, speed = 1f)
            generated.save(file)
            return (SystemClock.elapsedRealtimeNanos() - start) / 1e6
        }

        fun synthesizeChunk(index: Int): List<Pair<File, MediaItem>> {
            val outputItems = mutableListOf<Pair<File, MediaItem>>()
            if (switchedOffline && autoSwitchBack && hasValidatedInternet()) {
                switchedOffline = false
                Evidence.write(this, "speech_provider_recovery_attempt", mapOf(
                    "from_provider" to "sherpa-onnx-kokoro-v0.19", "to_provider" to "azure-rest",
                    "at_sentence_index" to index, "policy" to "switch_back_at_sentence_boundary"))
            }
            if (!switchedOffline) {
                try {
                    if (index == simulateOutageAtSentence) throw java.io.IOException("Injected Azure REST outage")
                    val file = File(chunkDir, "chunk-$index.wav")
                    val synthStarted = SystemClock.elapsedRealtimeNanos()
                    provider.synthesize(SpeechSynthesisRequest(sentences[index], voiceId, file))
                    val readyAt = SystemClock.elapsedRealtime()
                    val synthMs = (SystemClock.elapsedRealtimeNanos() - synthStarted) / 1e6
                    val durationMs = AzureRestSentenceChunkSynthesizer.durationMs(file)
                    incrementalChunkProviders[index] = "azure-rest"
                    synchronized(modes) { modes += "azure-rest" }
                    val row = JSONObject().put("index", index).put("text", sentences[index])
                        .put("provider", "azure-rest").put("synthesis_ms", synthMs).put("ready_uptime_ms", readyAt)
                        .put("ready_after_start_ms", readyAt - startedAt)
                        .put("audio_duration_ms", durationMs).put("wav_bytes", file.length())
                    synchronized(rows) { rows.put(row) }
                    Evidence.write(this, "azure_rest_incremental_chunk_ready", mapOf(
                        "sentence_index" to index, "provider" to "azure-rest", "synthesis_ms" to synthMs,
                        "ready_after_start_ms" to readyAt - startedAt,
                        "audio_duration_ms" to durationMs, "wav_bytes" to file.length()))
                    if (offlineAnnouncementQueued) {
                        Evidence.write(this, "speech_provider_recovered", mapOf(
                            "provider" to "azure-rest", "at_sentence_index" to index,
                            "policy" to "switch_back_at_sentence_boundary"))
                        offlineAnnouncementQueued = false
                    }
                    outputItems += file to makeItem("rest-sentence-$index", file)
                } catch (failure: Exception) {
                    switchedOffline = true
                    Evidence.write(this, "speech_provider_failover", mapOf(
                        "from_provider" to "azure-rest", "to_provider" to "sherpa-onnx-kokoro-v0.19",
                        "at_sentence_index" to index, "simulated_outage" to (index == simulateOutageAtSentence),
                        "failure_type" to failure.javaClass.simpleName,
                        "policy" to if (autoSwitchBack) "switch_back_at_sentence_boundary" else "remain_offline_for_item"))
                    if (!offlineAnnouncementQueued) {
                        val announcementFile = File(chunkDir, "offline-switch-announcement.wav")
                        val announcement = "Online speech is unavailable. Continuing with the offline voice."
                        val announcementMs = synthesizeOffline(announcement, announcementFile)
                        val announcementDurationMs = AzureRestSentenceChunkSynthesizer.durationMs(announcementFile)
                        Evidence.write(this, "speech_failover_announcement_ready", mapOf(
                            "text" to announcement, "provider" to "sherpa-onnx-kokoro-v0.19",
                            "synthesis_ms" to announcementMs, "audio_duration_ms" to announcementDurationMs,
                            "wav_bytes" to announcementFile.length()))
                        outputItems += announcementFile to makeItem("offline-switch-announcement", announcementFile)
                        offlineAnnouncementQueued = true
                    }
                }
            }
            if (switchedOffline) {
                val file = File(chunkDir, "chunk-$index.wav")
                val synthMs = synthesizeOffline(sentences[index], file)
                val readyAt = SystemClock.elapsedRealtime()
                val durationMs = AzureRestSentenceChunkSynthesizer.durationMs(file)
                incrementalChunkProviders[index] = "sherpa-onnx-kokoro-v0.19"
                synchronized(modes) { modes += "sherpa-onnx-kokoro-v0.19" }
                val row = JSONObject().put("index", index).put("text", sentences[index])
                    .put("provider", "sherpa-onnx-kokoro-v0.19").put("synthesis_ms", synthMs)
                    .put("ready_uptime_ms", readyAt).put("ready_after_start_ms", readyAt - startedAt)
                    .put("audio_duration_ms", durationMs).put("wav_bytes", file.length())
                synchronized(rows) { rows.put(row) }
                Evidence.write(this, "azure_rest_incremental_chunk_ready", mapOf(
                    "sentence_index" to index, "provider" to "sherpa-onnx-kokoro-v0.19", "synthesis_ms" to synthMs,
                    "ready_after_start_ms" to readyAt - startedAt,
                    "audio_duration_ms" to durationMs, "wav_bytes" to file.length()))
                outputItems += file to makeItem("rest-sentence-$index", file)
            }
            return outputItems
        }

        val initial = (0 until minOf(2, sentences.size)).flatMap(::synthesizeChunk)
        val playerReady = CountDownLatch(1)
        val startFailure = AtomicReference<Throwable?>(null)
        val queuedAt = SystemClock.elapsedRealtime()
        runOnUiThread {
            try {
                val player = controller ?: error("Media controller is not connected")
                incrementalSentences = sentences
                player.setMediaItems(initial.map { it.second }, true)
                player.prepare()
                player.play()
                Evidence.write(this, "azure_rest_incremental_playback_queued", mapOf(
                    "prebuffered_sentences" to initial.count { it.second.mediaId.startsWith("rest-sentence-") },
                    "time_to_playable_head_ms" to queuedAt - startedAt,
                    "time_to_first_chunk_ms" to ((rows.getJSONObject(0).getLong("ready_uptime_ms")) - startedAt),
                    "queued_uptime_ms" to queuedAt))
            } catch (failure: Throwable) {
                startFailure.set(failure)
            } finally { playerReady.countDown() }
        }
        check(playerReady.await(15, TimeUnit.SECONDS)) { "Timed out queuing the initial speech chunks" }
        startFailure.get()?.let { throw IllegalStateException("Could not start incremental speech", it) }

        for (index in initial.size until sentences.size) {
            synthesizeChunk(index).forEach { (_, item) -> runOnUiThread { controller?.addMediaItem(item) } }
        }
        val totalMs = synchronized(rows) { (0 until rows.length()).sumOf { rows.getJSONObject(it).getDouble("audio_duration_ms") } }
        offlineProvider?.close()
        val report = JSONObject().put("provider", provider.id).put("voice_id", voiceId)
            .put("fixture", "9to5google-cleaned-extraction")
            .put("article_characters", article.length).put("sentence_count", sentences.size)
            .put("prebuffered_sentences", initial.size).put("total_audio_duration_ms", totalMs)
            .put("generation_wall_ms", SystemClock.elapsedRealtime() - startedAt)
            .put("simulated_outage_at_sentence_index", simulateOutageAtSentence ?: JSONObject.NULL)
            .put("recovery_policy", if (autoSwitchBack) "switch_back_at_sentence_boundary" else "remain_offline_for_item")
            .put("provider_sequence", org.json.JSONArray(synchronized(modes) { modes.toSet().toList() }))
            .put("generated_wav_bytes", (0 until rows.length()).sumOf { rows.getJSONObject(it).getLong("wav_bytes") })
            .put("chunk_cache_directory", "cache/azure-rest-incremental")
            .put("chunks", synchronized(rows) { rows })
            .put("scope_note", "First two sentence files are prepared before playback; later sentences are synthesized and appended while playback continues. In failover mode, the injected outage causes a local spoken announcement and Kokoro is used for the rest of the item.")
        File(filesDir, "azure-rest-incremental.json").writeText(report.toString(2))
        Evidence.write(this, "azure_rest_incremental_generation_complete", mapOf(
            "sentence_count" to sentences.size, "generation_wall_ms" to report.getLong("generation_wall_ms"),
            "audio_duration_ms" to totalMs, "generated_wav_bytes" to report.getLong("generated_wav_bytes"),
            "provider_sequence" to synchronized(modes) { modes.toSet().toList() }))
        runOnUiThread { output.append("\nIncremental REST speech queued: ${sentences.size} sentences, ${(queuedAt - startedAt)} ms to initial playback queue") }
    }

    private fun traceAzure() {
        val boundariesFile=File(filesDir,"azure-boundaries.json")
        if (!boundariesFile.exists()) return
        val unsorted=org.json.JSONArray(boundariesFile.readText())
        val ordered=(0 until unsorted.length()).map { unsorted.getJSONObject(it) }
            .sortedBy { it.getDouble("audio_ms") }
        val rows=org.json.JSONArray(ordered); var cursor=0
        val handler=android.os.Handler(mainLooper)
        val started=SystemClock.elapsedRealtime()
        handler.post(object: Runnable {
            override fun run() {
                val p=controller ?: return
                if (p.currentMediaItem?.localConfiguration?.uri?.lastPathSegment != "azure.wav") return
                val position=p.currentPosition
                while(cursor<rows.length() && rows.getJSONObject(cursor).getDouble("audio_ms")<=position && p.isPlaying) {
                    val row=rows.getJSONObject(cursor++)
                    Evidence.write(this@SpikeActivity,"azure_playback_boundary",mapOf("expected_audio_ms" to row.getDouble("audio_ms"),"observed_player_ms" to position,"poll_lateness_ms" to position-row.getDouble("audio_ms"),"speed" to p.playbackParameters.speed,"boundary_type" to row.getString("boundary_type"),"callback_wall_ms" to row.getDouble("callback_wall_ms")))
                }
                if(cursor<rows.length() && SystemClock.elapsedRealtime()-started<120000) handler.postDelayed(this,20)
            }
        })
    }
    override fun onDestroy() { web.destroy(); controller?.release(); worker.shutdown(); super.onDestroy() }
}
