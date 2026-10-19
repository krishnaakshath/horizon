package dev.tvlauncher.browser.net

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchEnginesTest {
    @Test fun urlFor_eachEngine() {
        assertEquals("https://duckduckgo.com/?q=tv", SearchEngines.urlFor(SearchEngine.DUCKDUCKGO, "tv"))
        assertEquals("https://www.google.com/search?q=tv", SearchEngines.urlFor(SearchEngine.GOOGLE, "tv"))
        assertEquals("https://www.bing.com/search?q=tv", SearchEngines.urlFor(SearchEngine.BING, "tv"))
        assertEquals("https://search.brave.com/search?q=tv", SearchEngines.urlFor(SearchEngine.BRAVE, "tv"))
    }

    @Test fun urlFor_percentEncodesProperly() {
        assertEquals("https://duckduckgo.com/?q=tv%20shows", SearchEngines.urlFor(SearchEngine.DUCKDUCKGO, "tv shows"))
        assertEquals("https://duckduckgo.com/?q=c%2B%2B%20%26%20more%3F%23", SearchEngines.urlFor(SearchEngine.DUCKDUCKGO, "c++ & more?#"))
        assertEquals("https://duckduckgo.com/?q=caf%C3%A9", SearchEngines.urlFor(SearchEngine.DUCKDUCKGO, "café"))
        assertEquals("https://duckduckgo.com/?q=a%2Fb%3Dc", SearchEngines.urlFor(SearchEngine.DUCKDUCKGO, "a/b=c"))
        assertEquals("https://duckduckgo.com/?q=-_.~", SearchEngines.urlFor(SearchEngine.DUCKDUCKGO, "-_.~"))
    }

    @Test fun fromId_unknownFallsBackToDuckDuckGo() {
        assertEquals(SearchEngine.GOOGLE, SearchEngines.fromId("GOOGLE"))
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngines.fromId("nope"))
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngines.fromId(null))
    }

    @Test fun suggestUrl_usesDuckDuckGoListApi() {
        assertEquals("https://duckduckgo.com/ac/?q=tv%20s&type=list", SearchEngines.suggestUrl("tv s"))
    }
}
