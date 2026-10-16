package dev.yomu.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import org.apache.poi.hwpf.extractor.WordExtractor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

internal fun isTransientArticleHttpStatus(code: Int): Boolean =
    code == 408 || code == 425 || code == 429 || code in 500..599

/** Import/extraction boundary. Original files stay app-private so shares remain available offline. */
class ContentImporter(context: Context, private val dao: LibraryDao) {
    private val app = context.applicationContext
    private val originals = File(app.filesDir, "library/originals").apply { mkdirs() }
    private val readFootnotes: Boolean
        get() = app.getSharedPreferences("content_preferences", Context.MODE_PRIVATE).getBoolean("read_footnotes", true)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun saveUrlForLater(url: String): String = withContext(Dispatchers.IO) {
        val normalizedUrl = url.trim()
        require(normalizedUrl.startsWith("https://") || normalizedUrl.startsWith("http://")) { "Enter a complete http(s) URL." }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertItem(ReadingItemEntity(id, title = normalizedUrl, sourceType = "url", sourceUrl = normalizedUrl,
            importState = "pending", importedAt = now, manualOrder = dao.nextManualOrder(),
            warning = "Waiting for an internet connection; Yomu will retry automatically."))
        id
    }

    suspend fun retryUrl(itemId: String) = withContext(Dispatchers.IO) {
        val saved = dao.getItem(itemId) ?: error("This reading item is no longer available.")
        val url = saved.sourceUrl ?: error("Share this document to Yomu again to retry its import.")
        dao.setImportState(itemId, "fetching", null)
        try {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val message = "The page returned HTTP ${response.code}."
                    if (isTransientArticleHttpStatus(response.code))
                        throw RetryableUrlImportException(message)
                    throw IllegalStateException(message)
                }
                val html = response.body?.let(::readArticleHtml).orEmpty()
                require(html.isNotBlank()) { "The page was empty." }
                val finalUrl = response.request.url.toString()
                val (title, text) = extractArticle(finalUrl, html)
                require(text.length >= 80) { "Could not find enough readable article text on this page." }
                dao.finishExtraction(itemId, text, estimateSeconds(text), System.currentTimeMillis())
                dao.setExtractedTitle(itemId, title, saved.title)
                dao.setArticleSource(itemId, finalUrl, host(finalUrl).orEmpty())
            }
        } catch (error: Exception) {
            val retryable = error is IOException || (error is RetryableUrlImportException)
            val message = error.message ?: "Page import failed"
            if (error is kotlinx.coroutines.CancellationException) throw error
            dao.setImportState(itemId, if (retryable) "pending" else "failed",
                if (retryable) "Waiting for an internet connection; Yomu will retry automatically." else message)
            if (retryable) throw RetryableUrlImportException(message, error)
            throw error
        }
    }

    class RetryableUrlImportException(message: String, cause: Throwable? = null) : IOException(message, cause)

    suspend fun importDocument(displayName: String, mimeType: String?, input: () -> java.io.InputStream): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val safeName = displayName.substringAfterLast('/').substringAfterLast('\\').take(180).ifBlank { "Shared document" }
        val stored = File(originals, "$id-${safeName.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
        dao.upsertItem(ReadingItemEntity(id, safeName, fileKind(safeName, mimeType), sourceName = safeName,
            originalFilePath = stored.absolutePath, importState = "extracting", importedAt = now, manualOrder = dao.nextManualOrder()))
        try {
            input().use { source ->
                val staging = File(stored.absolutePath + ".partial")
                try {
                    staging.outputStream().buffered().use { sink ->
                        val buffer = ByteArray(64 * 1024); var total = 0L
                        while (true) { val count = source.read(buffer); if (count < 0) break
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            total += count; require(total <= 80L * 1024 * 1024) { "Documents must be 80 MB or smaller." }; sink.write(buffer, 0, count)
                        }
                    }
                    check(staging.renameTo(stored)) { "Could not save the document." }
                } finally { staging.delete() }
            }
            DocumentImportWork.enqueue(app, id)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            dao.setImportState(id, "failed", error.message ?: "Document import failed")
        }
        id
    }

    suspend fun extractSavedDocument(id: String, password: String? = null) = withContext(Dispatchers.IO) {
        val saved = dao.getItem(id) ?: return@withContext
        val stored = saved.originalFilePath?.let(::File)?.takeIf { it.isFile }
            ?: error("The original is unavailable. Download it or share it again.")
        val safeName = saved.sourceName ?: saved.title
        dao.setImportState(id, "extracting", null)
        try {
            val mimeType: String? = null
            val text = when {
                safeName.endsWith(".pdf", ignoreCase = true) || mimeType == "application/pdf" -> extractPdf(stored, password)
                safeName.endsWith(".docx", ignoreCase = true) || mimeType == DOCX -> extractDocx(stored, readFootnotes)
                safeName.endsWith(".doc", ignoreCase = true) || mimeType == DOC -> extractLegacyDoc(stored, readFootnotes)
                safeName.endsWith(".txt", ignoreCase = true) || mimeType?.startsWith("text/") == true -> stored.readText()
                else -> error("This file type is not supported yet.")
            }.let(::clean)
            require(text.isNotBlank()) { "No readable text was found in this document." }
            dao.finishExtraction(id, text, estimateSeconds(text), System.currentTimeMillis())
        } catch (error: Exception) {
            val needsPassword = error is SecurityException && saved.sourceType == "pdf"
            if (error is kotlinx.coroutines.CancellationException) throw error
            dao.setImportState(id, "failed", if (needsPassword) "This PDF is password protected or uses an unsupported security setting. Tap to try its password." else error.message ?: "Document import failed")
            throw error
        }
    }

    suspend fun retryPdfPassword(itemId: String, password: String) = withContext(Dispatchers.IO) {
        val saved = dao.getItem(itemId) ?: error("This reading item is no longer available.")
        require(saved.sourceType == "pdf") { "This item is not a PDF." }
        val file = saved.originalFilePath?.let(::File)?.takeIf { it.isFile } ?: error("The saved PDF is unavailable. Share it to Yomu again.")
        dao.setImportState(itemId, "extracting", null)
        try {
            val text = clean(extractPdf(file, password))
            require(text.isNotBlank()) { "No readable text was found in this PDF." }
            dao.finishExtraction(itemId, text, estimateSeconds(text), System.currentTimeMillis())
        } catch (error: Exception) {
            val hint = if (error is SecurityException) "The PDF password was incorrect, or its security method is unsupported. Try again or unlock it in a PDF reader first." else error.message ?: "Could not read this PDF."
            dao.setImportState(itemId, "failed", hint)
            throw IllegalArgumentException(hint)
        }
    }

    private fun extractDocx(file: File, includeFootnotes: Boolean): String {
        require(file.length() <= MAX_DOCX_BYTES) { "This Word document is too large to import safely." }
        ZipFile(file).use { zip ->
            fun readPart(path: String, required: Boolean): String {
                val entry = zip.getEntry(path) ?: if (required) error("This Word file has no main document text.") else return ""
                require(entry.size in 1..MAX_XML_BYTES) { "The Word document text exceeds the safe import limit." }
                val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
                zip.getInputStream(entry).buffered().use { parser.setInput(it, "UTF-8") }
                val paragraphs = mutableListOf<String>()
                val current = StringBuilder()
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "t") {
                        current.append(parser.nextText())
                    } else if (event == XmlPullParser.END_TAG && parser.name == "p") {
                        if (current.isNotBlank()) paragraphs += current.toString().trim()
                        current.clear()
                    }
                    event = parser.eventType
                    if (event != XmlPullParser.END_DOCUMENT) parser.next()
                }
                return paragraphs.joinToString("\n\n")
            }
            val main = readPart("word/document.xml", required = true)
            if (!includeFootnotes) return main
            return listOf(main, readPart("word/footnotes.xml", required = false), readPart("word/endnotes.xml", required = false))
                .filter(String::isNotBlank).joinToString("\n\n")
        }
    }

    private fun extractLegacyDoc(file: File, includeFootnotes: Boolean): String {
        require(file.length() <= MAX_DOC_BYTES) { "This Word document is too large to import safely." }
        return WordExtractor(file.inputStream().buffered()).use { extractor ->
            val mainText = extractor.paragraphText.asSequence()
                .map { it.trimEnd('\r', '\n', '\u0000') }
                .filter(String::isNotBlank)
                .joinToString("\n")
            val notes = if (includeFootnotes) {
                (extractor.footnoteText.asSequence() + extractor.endnoteText.asSequence())
                    .map(String::trim).filter(String::isNotBlank).joinToString("\n")
            } else ""
            listOf(mainText, notes).filter(String::isNotBlank).joinToString("\n\n")
        }
    }

    private fun extractPdf(file: File, password: String?): String {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            if (Build.VERSION.SDK_INT >= 35) PdfRenderer(fd, android.graphics.pdf.LoadParams.Builder().setPassword(password).build())
            else {
                require(password.isNullOrEmpty()) { "Password-protected PDFs require Android 15 or newer." }
                PdfRenderer(fd)
            }
        } catch (error: Exception) {
            runCatching { fd.close() }
            throw error
        }
        val tessRoot = File(app.filesDir, "tesseract")
        val hasOcr = File(tessRoot, "tessdata/eng.traineddata").isFile
        val tess = if (hasOcr) TessBaseAPI().also { check(it.init(tessRoot.absolutePath, "eng")) { "On-device OCR could not start." } } else null
        return try {
            val pages = ArrayList<String>(renderer.pageCount)
            for (index in 0 until renderer.pageCount) renderer.openPage(index).use { page ->
                var text = if (Build.VERSION.SDK_INT >= 35) page.textContents.flatMap { content ->
                    val lines = content.text.lines()
                    val bounds = content.bounds
                    if (readFootnotes || bounds.isEmpty() || bounds.size != lines.size) lines
                    else lines.zip(bounds).filter { (_, rect) -> rect.bottom <= page.height * FOOTNOTE_CUTOFF }.map { it.first }
                }.joinToString("\n").trim() else ""
                if (text.length < 20 && tess != null) {
                    val scale = minOf(3f, 3600f / page.width, 4800f / page.height).coerceAtLeast(1f)
                    val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                    try {
                        val clip = if (readFootnotes) null else android.graphics.Rect(0, 0, bitmap.width, (bitmap.height * FOOTNOTE_CUTOFF).toInt())
                        page.render(bitmap, clip, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        tess.setImage(bitmap)
                        text = tess.getUTF8Text()?.trim().orEmpty()
                    } finally { bitmap.recycle() }
                }
                if (text.length < 20 && tess == null) error("This PDF contains scanned pages. OCR data has not been installed yet.")
                pages += text
            }
            removeRepeatedMargins(pages).joinToString("\n\n") { it.trim() }
        } finally { tess?.recycle(); renderer.close() }
    }

    private fun removeRepeatedMargins(pages: List<String>): List<String> {
        if (pages.size < 3) return pages
        fun norm(value: String) = value.trim().replace(Regex("\\s+"), " ").lowercase()
        val lines = pages.map { it.lines().filter(String::isNotBlank) }
        val threshold = (pages.size * 0.6).toInt().coerceAtLeast(3)
        val repeated = (lines.mapNotNull { it.firstOrNull()?.let(::norm) } + lines.mapNotNull { it.lastOrNull()?.let(::norm) })
            .groupingBy { it }.eachCount().filterValues { it >= threshold }.keys
        return lines.map { pageLines -> pageLines.filterIndexed { index, line ->
            val margin = index == 0 || index == pageLines.lastIndex
            !margin || norm(line) !in repeated
        }.joinToString("\n") }
    }

    private fun clean(text: String): String = ReadingTextCleaner.clean(text)

    private fun readArticleHtml(body: ResponseBody): String {
        require(body.contentLength() < 0 || body.contentLength() <= MAX_ARTICLE_HTML_BYTES) {
            "This web page is too large to import safely."
        }
        val output = java.io.ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_ARTICLE_HTML_BYTES) { "This web page is too large to import safely." }
                output.write(buffer, 0, count)
            }
        }
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        return String(output.toByteArray(), charset)
    }

    private fun extractArticle(finalUrl: String, html: String): Pair<String, String> {
        val document = Jsoup.parse(html, finalUrl)
        if (!readFootnotes) document.select("[role=doc-footnote], [role=doc-endnote], [class*=footnote], [id*=footnote], [class*=endnote], [id*=endnote]").remove()
        val article = runCatching { Readability4J(finalUrl, document.outerHtml()).parse() }.getOrNull()
        val text = clean(article?.textContent?.takeIf(String::isNotBlank) ?: document.body().text())
        val title = article?.title?.takeIf(String::isNotBlank) ?: document.title().takeIf(String::isNotBlank) ?: finalUrl
        return title to text
    }

    private fun host(url: String) = runCatching { android.net.Uri.parse(url).host }.getOrNull()
    private fun fileKind(name: String, mime: String?) = when {
        name.endsWith(".pdf", true) || mime == "application/pdf" -> "pdf"
        name.endsWith(".docx", true) || mime == DOCX -> "docx"
        name.endsWith(".doc", true) || mime == DOC -> "doc"
        else -> "text"
    }

    private fun estimateSeconds(text: String): Int {
        val wordCount = Regex("\\b[\\p{L}\\p{N}]+(?:['’.-][\\p{L}\\p{N}]+)*\\b").findAll(text).count().coerceAtLeast(1)
        return ((wordCount / 230.0) * 60).toInt()
    }

    companion object {
        private const val MAX_DOCX_BYTES = 80L * 1024 * 1024
        private const val MAX_DOC_BYTES = 80L * 1024 * 1024
        private const val MAX_XML_BYTES = 24L * 1024 * 1024
        private const val MAX_ARTICLE_HTML_BYTES = 16 * 1024 * 1024
        private const val FOOTNOTE_CUTOFF = 0.86f
        private const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        private const val DOC = "application/msword"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 17) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36 Yomu/1.0"
    }
}
