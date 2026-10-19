package dev.tvlauncher.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlResolverTest {
    private val home = UrlResolver.HOME_PAGE

    @Test fun bareWord_searches() {
        assertEquals(UrlResolver.SEARCH_PREFIX + "weather", UrlResolver.resolve("weather"))
        assertEquals(UrlResolver.SEARCH_PREFIX + "tv%20shows%202026", UrlResolver.resolve("  tv shows 2026 "))
        assertEquals(UrlResolver.SEARCH_PREFIX + "c%2B%2B%20%26%20more", UrlResolver.resolve("c++ & more"))
        // A dot with spaces is still a search.
        assertEquals(UrlResolver.SEARCH_PREFIX + "node.js%20tutorial", UrlResolver.resolve("node.js tutorial"))
    }

    @Test fun searchEngine_selectable() {
        assertEquals("https://www.google.com/search?q=tv", UrlResolver.resolve("tv", dev.tvlauncher.browser.net.SearchEngine.GOOGLE))
    }

    @Test fun searchEngine_isDuckDuckGo() {
        assertEquals("https://duckduckgo.com/?q=", UrlResolver.SEARCH_PREFIX)
    }

    @Test fun domain_getsHttps() {
        assertEquals("https://example.com", UrlResolver.resolve("example.com"))
        assertEquals("https://en.wikipedia.org/wiki/TV", UrlResolver.resolve("en.wikipedia.org/wiki/TV"))
        assertEquals("https://example.com:8443/a", UrlResolver.resolve("example.com:8443/a"))
        assertEquals("https://example.com/x", UrlResolver.resolve("https://example.com/x"))
        assertEquals("https://Example.com/X", UrlResolver.resolve("HTTPS://Example.com/X"))
    }

    @Test fun httpUrl_upgradedToHttps() {
        assertEquals("https://example.com/page?q=1", UrlResolver.resolve("http://example.com/page?q=1"))
        assertEquals("https://example.com", UrlResolver.resolve("HTTP://example.com"))
    }

    @Test fun blank_returnsHomePage() {
        assertEquals(home, UrlResolver.resolve(""))
        assertEquals(home, UrlResolver.resolve("   \n\t"))
    }

    @Test fun javascriptScheme_rejected() {
        assertEquals(home, UrlResolver.resolve("javascript:alert(1)"))
        assertEquals(home, UrlResolver.resolve("  JavaScript:alert(document.cookie)"))
        assertEquals(home, UrlResolver.resolve("file:///sdcard/secret.txt"))
        assertEquals(home, UrlResolver.resolve("content://com.android.contacts/contacts"))
        assertEquals(home, UrlResolver.resolve("data:text/html,<script>alert(1)</script>"))
    }

    @Test fun intentScheme_rejected() {
        assertEquals(home, UrlResolver.resolve("intent://scan/#Intent;scheme=zxing;package=com.x;end"))
        assertEquals(home, UrlResolver.resolve("market://details?id=com.x"))
    }

    @Test fun navigation_allowsHttps_upgradesHttp_blocksOthers() {
        assertEquals("https://a.com/", UrlResolver.navigationTarget("https://a.com/"))
        assertEquals("https://a.com/", UrlResolver.navigationTarget("http://a.com/"))
        assertNull(UrlResolver.navigationTarget("intent://x#Intent;end"))
        assertNull(UrlResolver.navigationTarget("javascript:alert(1)"))
        assertNull(UrlResolver.navigationTarget("file:///etc/hosts"))
        assertNull(UrlResolver.navigationTarget("tel:123"))
    }
}
