package dev.yomu.app.speech

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

data class ModelInstallProgress(val receivedBytes: Long, val totalBytes: Long, val stage: String) {
    val fraction: Float get() = if (totalBytes > 0) (receivedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/** Downloads the pinned, freely licensed Kokoro model without putting 320 MB in the APK. */
class KokoroModelInstaller(context: Context) {
    private val app = context.applicationContext
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .callTimeout(45, TimeUnit.MINUTES)
        .build()

    val modelDirectory: File get() = File(app.filesDir, "speech/kokoro-en-v0_19")
    fun isInstalled(): Boolean = File(modelDirectory, "INSTALL_OK").readTextOrNull() == MODEL_VERSION &&
        expectedFiles.all { (name, spec) -> File(modelDirectory, name).let { it.isFile && it.length() == spec.first } } &&
        File(modelDirectory, "espeak-ng-data").walkTopDown().filter { it.isFile }.sumOf { it.length() } == 838_291L

    fun install(onProgress: (ModelInstallProgress) -> Unit = {}): File {
        if (isInstalled()) return modelDirectory
        val parent = modelDirectory.parentFile!!.apply { mkdirs() }
        val requiredBytes = 380_000_000L + if (modelDirectory.exists()) 360_000_000L else 0L
        check(parent.usableSpace >= requiredBytes) { "Free at least ${requiredBytes / 1_000_000} MB before installing the offline voice." }
        val staging = File(parent, "kokoro-en-v0_19.installing").apply { deleteRecursively(); mkdirs() }
        val request = Request.Builder().url(ARCHIVE_URL).header("User-Agent", "Yomu-Android/1.0").build()
        try {
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Model download returned HTTP ${response.code}." }
                val body = response.body ?: error("The model download returned an empty response.")
                val total = body.contentLength().takeIf { it > 0 } ?: ARCHIVE_BYTES
                if (total > 0) check(total == ARCHIVE_BYTES) { "The model archive size changed; install was stopped to protect model integrity." }
                val archiveDigest = MessageDigest.getInstance("SHA-256")
                val counted = CountingInputStream(body.byteStream()) { received -> onProgress(ModelInstallProgress(received, total, "Downloading and unpacking the offline voice…")) }
                val checked = DigestInputStream(counted, archiveDigest)
                val bz = BZip2CompressorInputStream(checked, false)
                val tar = TarArchiveInputStream(bz)
                val found = mutableSetOf<String>()
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    if (!entry.isFile) continue
                    val prefix = "kokoro-en-v0_19/"
                    val relative = entry.name.removePrefix(prefix)
                    val spec = expectedFiles[relative]
                    if (entry.name.startsWith(prefix) && spec != null) {
                        check(entry.size == spec.first) { "Unexpected size for model file $relative." }
                        val target = safeChild(staging, relative)
                        target.parentFile?.mkdirs()
                        val fileDigest = MessageDigest.getInstance("SHA-256")
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var copied = 0L
                            while (copied < entry.size) {
                                val count = tar.read(buffer, 0, minOf(buffer.size.toLong(), entry.size - copied).toInt())
                                check(count > 0) { "Model archive ended during $relative." }
                                output.write(buffer, 0, count)
                                fileDigest.update(buffer, 0, count)
                                copied += count
                            }
                        }
                        check(hex(fileDigest.digest()) == spec.second) { "Integrity check failed for $relative." }
                        found += relative
                    }
                    onProgress(ModelInstallProgress(counted.count, total, "Unpacking ${relative.ifBlank { "offline voice" }}…"))
                }
                check(found.containsAll(expectedFiles.keys)) { "The archive did not contain every required Kokoro file." }
                val drain = ByteArray(64 * 1024)
                while (bz.read(drain) >= 0) Unit
                check(hex(archiveDigest.digest()) == ARCHIVE_SHA256) { "The model archive failed its SHA-256 integrity check." }
                tar.close()
                onProgress(ModelInstallProgress(total, total, "Installing English pronunciation data…"))
                copyAssetTree("kokoro/espeak-ng-data", File(staging, "espeak-ng-data"))
                File(staging, "INSTALL_OK").writeText(MODEL_VERSION)
                val old = File(parent, "kokoro-en-v0_19.previous")
                old.deleteRecursively()
                if (modelDirectory.exists()) check(modelDirectory.renameTo(old)) { "Could not replace the previous offline voice files." }
                if (!staging.renameTo(modelDirectory)) {
                    if (old.exists()) old.renameTo(modelDirectory)
                    error("Could not finish installing the offline voice files.")
                }
                old.deleteRecursively()
                check(isInstalled()) { "The offline voice installation did not pass its final check." }
                onProgress(ModelInstallProgress(total, total, "Offline voice is ready."))
            }
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
        return modelDirectory
    }

    private fun copyAssetTree(assetPath: String, target: File) {
        val children = app.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            app.assets.open(assetPath).use { input -> target.outputStream().buffered().use { output -> input.copyTo(output) } }
        } else {
            target.mkdirs()
            children.forEach { child -> copyAssetTree("$assetPath/$child", File(target, child)) }
        }
    }

    private fun safeChild(root: File, relative: String): File {
        require(relative.isNotBlank() && !relative.startsWith('/') && relative.split('/').none { it == ".." }) { "Unsafe model archive path." }
        val result = File(root, relative)
        require(result.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "Unsafe model archive path." }
        return result
    }

    private class CountingInputStream(input: InputStream, private val onRead: (Long) -> Unit) : FilterInputStream(input) {
        var count = 0L; private set
        override fun read(): Int = super.read().also { if (it >= 0) { count++; onRead(count) } }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, length).also { if (it > 0) { count += it; onRead(count) } }
    }

    private fun File.readTextOrNull(): String? = runCatching { readText() }.getOrNull()
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    companion object {
        private const val MODEL_VERSION = "kokoro-en-v0.19-d0acca3"
        private const val ARCHIVE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2"
        private const val ARCHIVE_BYTES = 319_625_534L
        private const val ARCHIVE_SHA256 = "912804855a04745fa77a30be545b3f9a5d15c4d66db00b88cbcd4921df605ac7"
        private val expectedFiles = mapOf(
            "model.onnx" to (345_555_491L to "10ff414106a038ce7e9e0126c6461e4dc8a86efaa89dc91d2009d69fe635e339"),
            "voices.bin" to (5_755_904L to "a372c67b056ef0b695c375d39b99630d23fb07ad4c8d87aa32a19a62fca523ad"),
            "tokens.txt" to (1_078L to "4f31c71282d14af4e926cd12462078fe9d20d00c589e63fe2750a8f56d6d7f7b"),
            "LICENSE" to (11_358L to "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30"),
            "README.md" to (135L to "5a6302820ea002390e8da7219b7b03d18fdf8cfd1179440ca739a1504cef95d5"),
        )
    }
}
