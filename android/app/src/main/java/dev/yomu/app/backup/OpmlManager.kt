package dev.yomu.app.backup

import android.app.Application
import android.net.Uri
import android.util.Xml
import dev.yomu.app.data.LibraryDatabase
import dev.yomu.app.data.ContentImporter
import dev.yomu.app.data.UrlImportWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.Date

/** Supplemental OPML bridge for URL-only library outlines; full-content backups use the Yomu ZIP format. */
internal class OpmlManager(private val app: Application) {
    private val dao = LibraryDatabase.get(app).libraryDao()
    private val importer = ContentImporter(app, dao)

    suspend fun export(destination: Uri) = withContext(Dispatchers.IO) {
        val items = dao.observeToRead().first().filter { !it.sourceUrl.isNullOrBlank() }
        val playlists = dao.observePlaylists().first()
        val output = app.contentResolver.openOutputStream(destination, "w") ?: error("Could not open OPML destination.")
        output.use { stream ->
            val xml = Xml.newSerializer()
            xml.setOutput(stream, "UTF-8")
            xml.startDocument("UTF-8", true)
            xml.startTag(null, "opml").attribute(null, "version", "2.0")
            xml.startTag(null, "head").startTag(null, "title").text("Yomu URL lists").endTag(null, "title")
                .startTag(null, "dateCreated").text(Date().toString()).endTag(null, "dateCreated").endTag(null, "head")
            xml.startTag(null, "body")
            xml.startTag(null, "outline").attribute(null, "text", "To read").attribute(null, "title", "To read")
            items.forEach { item -> addOutline(xml, item.title, item.sourceUrl!!) }
            xml.endTag(null, "outline")
            playlists.forEach { playlist ->
                xml.startTag(null, "outline").attribute(null, "text", playlist.name).attribute(null, "title", playlist.name)
                dao.observePlaylistRows(playlist.id).first().forEach { row ->
                    dao.getItem(row.id)?.sourceUrl?.let { addOutline(xml, row.title, it) }
                }
                xml.endTag(null, "outline")
            }
            xml.endTag(null, "body").endTag(null, "opml")
            xml.endDocument(); xml.flush()
        }
    }

    suspend fun import(source: Uri, progress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val urls = linkedMapOf<String, String>()
        val input = app.contentResolver.openInputStream(source) ?: error("Android could not read this OPML file.")
        input.use { stream ->
            val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(stream, "UTF-8") }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name.equals("outline", true)) {
                    val title = parser.getAttributeValue(null, "text") ?: parser.getAttributeValue(null, "title") ?: "Saved link"
                    val address = parser.getAttributeValue(null, "htmlUrl") ?: parser.getAttributeValue(null, "url")
                    if (address?.startsWith("https://", true) == true || address?.startsWith("http://", true) == true) urls.putIfAbsent(address, title)
                }
                event = parser.next()
            }
        }
        require(urls.isNotEmpty()) { "This OPML file contains no article URLs Yomu can import." }
        urls.entries.forEachIndexed { index, (url, title) ->
            progress("Importing URL ${index + 1} of ${urls.size}…")
            val id = importer.saveUrlForLater(url)
            dao.getItem(id)?.takeIf { title.isNotBlank() }?.let { dao.upsertItem(it.copy(title = title)) }
            UrlImportWork.enqueue(app, id)
        }
        progress("Imported ${urls.size} article links to To read. Full text and playlists are restored with Yomu backups.")
    }

    private fun addOutline(xml: org.xmlpull.v1.XmlSerializer, title: String, url: String) {
        xml.startTag(null, "outline").attribute(null, "text", title).attribute(null, "title", title)
            .attribute(null, "type", "link").attribute(null, "htmlUrl", url).endTag(null, "outline")
    }
}
