package dev.yomu.spike

/** Conservative post-extraction cleanup for clearly separate web boilerplate. */
internal object ArticleTextCleaner {
    private val paragraphBreak = Regex("\\n\\s*\\n+")
    private val whitespace = Regex("[\\t\\r ]+")
    private val adMarker = Regex("(?i)\\bAdvertisement\\s*-\\s*scroll for more content\\b")
    private val embeddedSocialPost = Regex(
        "(?is)Progress you can see\\..*?pic\\.twitter\\.com/\\S+.*?—\\s*Boeing Defense \\(@BoeingDefense\\) July 6, 2026"
    )
    private val newsletterPreface = Regex(
        "(?is)^The TWZ Newsletter\\s+Weekly insights and analysis on the latest developments in military technology, strategy, and foreign policy\\.\\s*(?=A top U\\.S\\. Air Force acquisition officer)"
    )
    private val relatedLinksHeading = Regex("(?i)^More on [^:]{2,70}:$")
    private val socialOrPromoFooter = Regex("(?i)^(Follow [^:]{1,70}:|FTC:|Contact the author:).*")
    private val collapsedTailHeadings = listOf(
        Regex("(?i)\\bMore on [A-Z][^:]{1,70}:\\s*"),
        Regex("(?i)\\bFollow [A-Z][^:]{1,70}:\\s*"),
        Regex("(?i)\\bFTC:\\s*"),
        Regex("(?i)\\bContact the author:\\s*"),
    )

    fun clean(text: String): String {
        var source = text
            .replace("\u00a0", " ")
            .replace(adMarker, " ")
            .replace(embeddedSocialPost, " ")
            .replace(newsletterPreface, "")
        val tailStart = collapsedTailHeadings.mapNotNull { it.find(source) }
            .filter { it.range.first >= source.length * 0.55 }
            .minByOrNull { it.range.first }
        if (tailStart != null) source = source.substring(0, tailStart.range.first)
        val result = ArrayList<String>()
        for (raw in source.split(paragraphBreak)) {
            val paragraph = raw.replace(whitespace, " ").trim()
            if (paragraph.isEmpty()) continue
            if (relatedLinksHeading.matches(paragraph)) break
            if (socialOrPromoFooter.containsMatchIn(paragraph)) continue
            result += paragraph
        }
        return result.joinToString("\n\n")
    }
}
