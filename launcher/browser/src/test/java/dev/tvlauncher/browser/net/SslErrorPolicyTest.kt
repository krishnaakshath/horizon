package dev.tvlauncher.browser.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SslErrorPolicyTest {
    @Test fun subresourceMismatch_ignored() {
        assertFalse(SslErrorPolicy.shouldShowPageError("https://ads.bad.com/pixel.gif", "https://news.com/", "https://news.com/"))
    }

    @Test fun mainFrame_shown() {
        assertTrue(SslErrorPolicy.shouldShowPageError("https://news.com/", "https://news.com/", null))
    }

    @Test fun redirectTarget_matchesLoadingUrl() {
        assertTrue(SslErrorPolicy.shouldShowPageError("https://www.news.com/home", "https://news.com/", "https://www.news.com/home"))
    }

    @Test fun normalization_fragmentTrailingSlashCase() {
        assertTrue(SslErrorPolicy.shouldShowPageError("https://News.com/a/#top", "https://news.com/a", null))
        assertTrue(SslErrorPolicy.shouldShowPageError("https://news.com", "https://news.com/", null))
        assertFalse(SslErrorPolicy.shouldShowPageError("https://news.com/b", "https://news.com/a", "https://news.com/a"))
    }

    @Test fun unknownUrls_failSafe_show() {
        assertTrue(SslErrorPolicy.shouldShowPageError("https://x.com/", null, null))
        assertTrue(SslErrorPolicy.shouldShowPageError(null, "https://x.com/", null))
        assertTrue(SslErrorPolicy.shouldShowPageError("::::", "https://x.com/", null))
    }

    @Test fun message_perPrimaryError() {
        assertTrue(SslErrorPolicy.reason(1).contains("expired"))
        assertTrue(SslErrorPolicy.reason(2).contains("name"))
        assertTrue(SslErrorPolicy.reason(3).contains("trusted"))
        assertTrue(SslErrorPolicy.reason(99).isNotEmpty())
    }

    @Test fun defaultPorts_normalized() {
        assertTrue(SslErrorPolicy.shouldShowPageError("https://a.com:443/p", "https://a.com/p", null))
        assertTrue(SslErrorPolicy.shouldShowPageError("http://a.com:80/", "http://a.com", null))
        assertFalse(SslErrorPolicy.shouldShowPageError("https://a.com:8443/p", "https://a.com/p", "https://a.com/p"))
    }

    @Test fun schemeMismatch_staysDistinct() {
        assertFalse(SslErrorPolicy.shouldShowPageError("http://a.com/p", "https://a.com/p", "https://a.com/p"))
        assertFalse(SslErrorPolicy.shouldShowPageError("https://a.com:80/p", "http://a.com/p", null))
    }

    @Test fun fallback_showsSuppressedErrorAfter8sWithoutCommit() {
        val f = SslFallback(8000)
        f.onSuppressed("https://cdn.x.com/a.js", 3, now = 1000)
        assertEquals(null, f.due(8999))
        val p = f.due(9000)!!
        assertEquals("https://cdn.x.com/a.js", p.url)
        assertEquals(3, p.primaryError)
        assertEquals(null, f.due(20000)) // reported once
    }

    @Test fun fallback_cancelledByCommit() {
        val f = SslFallback(8000)
        f.onSuppressed("https://cdn.x.com/a.js", 3, now = 0)
        f.onCommitted()
        assertEquals(null, f.due(60000))
    }

    @Test fun fallback_failedFinish_showsImmediately() {
        val f = SslFallback(8000)
        f.onSuppressed("https://cdn.x.com/a.js", 1, now = 0)
        assertEquals(1, f.onFinished(failed = true)!!.primaryError)
        assertEquals(null, f.due(60000))
        f.onSuppressed("https://cdn.x.com/b.js", 1, now = 0)
        assertEquals(null, f.onFinished(failed = false))
    }
}
