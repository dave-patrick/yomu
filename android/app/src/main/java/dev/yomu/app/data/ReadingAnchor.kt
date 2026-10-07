package dev.yomu.app.data

import org.json.JSONObject
import java.security.MessageDigest

/** UTF-16 offsets into unchanged normalizedText, shared with the browser. */
object ReadingAnchor {
    fun fingerprint(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun create(text: String, offset: Int): String {
        val safe = offset.coerceIn(0, text.length)
        return JSONObject().put("version", 1).put("textHash", fingerprint(text)).put("offset", safe)
            .put("quote", text.substring(safe, (safe + 80).coerceAtMost(text.length)))
            .put("prefix", text.substring((safe - 32).coerceAtLeast(0), safe)).toString()
    }
    fun resolve(text: String, raw: String): Int? = runCatching {
        val anchor = JSONObject(raw)
        require(anchor.getInt("version") == 1)
        val offset = anchor.getInt("offset")
        if (anchor.getString("textHash") == fingerprint(text)) return@runCatching offset.takeIf { it in 0..text.length }
        val quote = anchor.getString("quote")
        val prefix = anchor.getString("prefix")
        if (quote.isEmpty()) return@runCatching null
        val needle = prefix + quote
        val found = text.indexOf(needle)
        if (found < 0 || text.indexOf(needle, found + 1) >= 0) null else found + prefix.length
    }.getOrNull()
    /** Finds the original offsets for the exact sentence strings already used by Android. */
    fun offsets(text: String, sentences: List<String>): List<Int> {
        var cursor = 0
        return sentences.map { sentence ->
            val found = text.indexOf(sentence, cursor).takeIf { it >= 0 } ?: cursor
            cursor = (found + sentence.length).coerceAtMost(text.length)
            found
        }
    }
}
