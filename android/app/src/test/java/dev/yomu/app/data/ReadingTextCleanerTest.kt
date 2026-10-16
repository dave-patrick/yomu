package dev.yomu.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingTextCleanerTest {
    @Test fun removesLinksAndSymbolsWithoutLosingWords() {
        assertEquals("Read this and café 日本語.\n\nDon't lose 42!",
            ReadingTextCleaner.clean("Read [this](https://example.test/a) and café 日本語.\n\nDon't ★ 😀 lose 42!"))
    }

    @Test fun stripsUrlsAndEmptyWrappers() {
        assertEquals("Visit. Next chapter.", ReadingTextCleaner.clean("Visit https://example.test/a?q=1. Next chapter. (www.example.test)"))
    }

    @Test fun onlyNoiseIsEmptyAndCleanupIsStable() {
        assertEquals("", ReadingTextCleaner.clean("https://example.test ★ 😀"))
        assertEquals("", ReadingTextCleaner.clean("--- *** !!!"))
        val text = "Café, don't stop—read 3.14!"
        assertEquals(text, ReadingTextCleaner.clean(ReadingTextCleaner.clean(text)))
    }
}
