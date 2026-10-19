package dev.tvlauncher.browser.net

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
}
