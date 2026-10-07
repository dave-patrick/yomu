package dev.yomu.app.data

import org.junit.Assert.*
import org.junit.Test

class ReadingAnchorTest {
    @Test fun offsetsPreserveWhitespaceAndUnicode() {
        val text = "  First.\n\n  Second 😀 sentence.  Third."
        val offsets = ReadingAnchor.offsets(text,listOf("First.","Second 😀 sentence.","Third."))
        assertEquals(listOf(2,12,33),offsets)
        for (offset in offsets) assertEquals(offset,ReadingAnchor.resolve(text,ReadingAnchor.create(text,offset)))
    }
    @Test fun unchangedTextRoundTripsExactCharacterIncludingSurrogatePairs() {
        val text = "😀 A readable sentence."
        assertEquals(3,ReadingAnchor.resolve(text,ReadingAnchor.create(text,3)))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",ReadingAnchor.fingerprint(""))
    }
    @Test fun changedTextRemapsOnlyUniqueContext() {
        val text = "First paragraph. Second paragraph."
        val saved = ReadingAnchor.create(text,17)
        assertEquals(23,ReadingAnchor.resolve("Intro."+text,saved))
        assertNull(ReadingAnchor.resolve(text+text,saved))
        assertNull(ReadingAnchor.resolve("Unrelated replacement",saved))
    }
    @Test fun malformedAndFutureAnchorsDoNotResume() {
        assertNull(ReadingAnchor.resolve("hello","not json"))
        assertNull(ReadingAnchor.resolve("hello",ReadingAnchor.create("hello",0).replace("\"version\":1","\"version\":2")))
    }
}
