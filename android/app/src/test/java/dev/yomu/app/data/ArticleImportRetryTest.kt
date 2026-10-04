package dev.yomu.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleImportRetryTest {
    @Test fun transientNetworkHttpStatusesAreRetried() {
        listOf(408, 425, 429, 500, 502, 503, 504).forEach { assertTrue("HTTP $it", isTransientArticleHttpStatus(it)) }
    }

    @Test fun permanentClientHttpStatusesAreShownAsImportFailures() {
        listOf(400, 401, 403, 404, 410).forEach { assertFalse("HTTP $it", isTransientArticleHttpStatus(it)) }
    }
}
