package dev.yomu.app.data

/** Normalize extracted reading bodies; never apply to originals or source metadata. */
internal object ReadingTextCleaner {
    private val markdownLink = Regex("\\[([^]\\n]+)]\\([^\\s)]+\\)")
    private val url = Regex("(?i)\\b(?:[a-z][a-z0-9+.-]*://|www\\.)[^\\s<>\\\"\\[\\]{}()]+")
    // Plain-text email renderers sometimes wrap a parenthesized tracking URL.
    // Stay inside its wrapper; never consume the surrounding article paragraphs.
    private val wrappedUrl = Regex("(?i)\\((?:[a-z][a-z0-9+.-]*://|www\\.)[^\\s()]+(?:(?:[ \\t]*\\n)+[ \\t]*[^\\s()]+)*[ \\t]*\\)")
    private val nonReading = Regex("[^\\p{L}\\p{M}\\p{N}\\s.,!?;:'\"’‘“”()\\-–—…]+")

    fun clean(text: String): String = text
        .replace("\r\n", "\n").replace('\r', '\n').replace('\u00a0', ' ')
        .replace(wrappedUrl, " ")
        .replace(markdownLink, "$1")
        .replace(url) { match -> match.value.takeLastWhile { it in ".,!?;:" } }
        .replace(Regex("[\\u00ad\\u200b-\\u200f\\u202a-\\u202e\\u2060-\\u206f\\ufeff]"), "")
        .replace(nonReading, " ")
        .replace(Regex("\\(\\s*\\)"), " ")
        .replace(Regex("(?m)^[^\\p{L}\\p{N}\\n]+$"), "")
        .replace(Regex("[\\t ]+"), " ")
        .replace(Regex(" +([.,!?;:])"), "$1")
        .replace(Regex(" *\\n *"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n").trim()
}
