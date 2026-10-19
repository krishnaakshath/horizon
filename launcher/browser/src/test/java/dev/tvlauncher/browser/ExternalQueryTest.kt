package dev.tvlauncher.browser

import dev.tvlauncher.browser.net.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalQueryTest {
    @Test fun cleansText() {
        assertEquals("a b", ExternalQuery.clean("  a\n\tb \u0007"))
        assertNull(ExternalQuery.clean("   "))
        assertNull(ExternalQuery.clean(null))
        assertEquals(ExternalQuery.MAX_LEN, ExternalQuery.clean("x".repeat(2000))!!.length)
    }

    @Test fun wordsSearch_addressesOpen_badSchemesDropped() {
        assertEquals(UrlResolver.resolve("cat videos", SearchEngine.DUCKDUCKGO), ExternalQuery.target("cat videos", SearchEngine.DUCKDUCKGO))
        assertEquals("https://example.com", ExternalQuery.target("example.com", SearchEngine.DUCKDUCKGO))
        assertNull(ExternalQuery.target("javascript:alert(1)", SearchEngine.DUCKDUCKGO))
    }
}
