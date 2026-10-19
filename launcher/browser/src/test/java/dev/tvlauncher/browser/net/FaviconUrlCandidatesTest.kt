package dev.tvlauncher.browser.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaviconUrlCandidatesTest {
    @Test fun withoutHead_triesAppleTouchThenFavicon() {
        assertEquals(
            listOf("https://github.com/apple-touch-icon.png", "https://github.com/favicon.ico"),
            FaviconUrlCandidates.build("https://github.com/some/repo?x=1", null)
        )
    }

    @Test fun withHead_insertsParsedIconsBetween_andDedupes() {
        val head = """<link rel="icon" href="/favicon.ico"><link rel="apple-touch-icon" href="/apple-touch-icon.png">
            <link rel="icon" sizes="192x192" href="/android-192.png">"""
        assertEquals(
            listOf("https://a.com/apple-touch-icon.png", "https://a.com/android-192.png", "https://a.com/favicon.ico"),
            FaviconUrlCandidates.build("https://a.com/", head)
        )
    }

    @Test fun httpPage_usesHttpsOrigin_keepsPort() {
        assertEquals("https://a.com:8443/apple-touch-icon.png", FaviconUrlCandidates.build("http://a.com:8443/x", null)[0])
    }

    @Test fun invalidPage_givesNothing() {
        assertTrue(FaviconUrlCandidates.build("javascript:alert(1)", null).isEmpty())
        assertTrue(FaviconUrlCandidates.build("", "<link rel=icon href=/x.png>").isEmpty())
        assertTrue(FaviconUrlCandidates.build("file:///x", null).isEmpty())
    }
}
