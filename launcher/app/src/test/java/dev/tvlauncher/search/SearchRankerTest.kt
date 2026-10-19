package dev.tvlauncher.search

import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.ui.QuickActions
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.watch.WatchProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankerTest {
    private fun app(label: String, recency: Int = -1) = SearchItem(SearchSection.APPS, "app:$label", label, recency = recency)
    private fun titles(r: SearchResults) = r.flat().map { it.item.title }

    @Test fun prefixBeatsWordBeatsFuzzy() {
        val items = listOf(app("My Netflix Guide"), app("Netlfux"), app("Netflix"), app("Netflix Kids"))
        assertEquals(listOf("Netflix", "Netflix Kids", "My Netflix Guide"), titles(SearchRanker.search("netflix", items)).take(3))
        assertTrue("Netlfux" in titles(SearchRanker.search("netflux", items))) // one typo: still found
    }

    @Test fun recencyBoostWithinATier() {
        val items = listOf(app("Plex"), app("Plex Arcade", recency = 0))
        assertEquals(listOf("Plex", "Plex Arcade"), titles(SearchRanker.search("plex", items))) // exact still first
        val two = listOf(app("Prime Video"), app("Pluto TV", recency = 1))
        assertEquals("Pluto TV", titles(SearchRanker.search("p", two)).first())
    }

    @Test fun diacriticsAndCaseInsensitive() {
        assertEquals(listOf("Télé Loisirs"), titles(SearchRanker.search("TELE", listOf(app("Télé Loisirs")))))
        assertEquals(listOf("Crunchyroll"), titles(SearchRanker.search("crünchy", listOf(app("Crunchyroll")))))
    }

    @Test fun typoTolerance_onlyForWordsOfFourOrMore() {
        assertTrue(SearchMatch.typo("youtbe", "youtube"))
        assertTrue(SearchMatch.typo("nteflix", "netflix")) // swapped letters
        assertTrue(SearchMatch.typo("spotfy", "spotify"))
        assertTrue(SearchMatch.typo("yuot", "youtube")) // still typing
        assertFalse(SearchMatch.typo("yut", "youtube"))
        assertFalse(SearchMatch.typo("abcd", "wxyz"))
        assertEquals(0, SearchMatch.osa("abc", "abc"))
        assertEquals(1, SearchMatch.osa("abcd", "abdc"))
        assertEquals(2, SearchMatch.osa("abcd", "badc"))
        assertTrue(SearchRanker.search("hbo", listOf(app("Disney+"))).isEmpty)
    }

    @Test fun multiWordAndDetailMatches() {
        val s = SearchItem(SearchSection.SETTINGS, "s", "Spotlight", subtitle = "The search and quick-actions panel")
        assertEquals(SearchMatch.DETAIL, SearchMatch.tier("panel", listOf("panel"), s))
        assertEquals(SearchMatch.WORD, SearchMatch.tier("tv kid", listOf("tv", "kid"), SearchItem(SearchSection.APPS, "x", "Kids TV")))
    }

    @Test fun groupsAreCappedAndOrderedByBestHit() {
        val items = (1..10).map { app("Tube $it") } + listOf(
            SearchItem(SearchSection.SETTINGS, "s1", "Tube settings"),
            SearchItem(SearchSection.ACTIONS, "a1", "Tuner"),
            SearchItem(SearchSection.DISCOVER, "d1", "TubeTV"),
            SearchItem(SearchSection.CONTINUE, "c1", "Tube story", recency = 0)
        )
        val web = listOf(SearchIndex.webItem(WebKind.WEB, "tube"), SearchIndex.webItem(WebKind.YOUTUBE, "tube"))
        val r = SearchRanker.search("tube", items, null, web)
        assertTrue(r.rows <= SearchRanker.MAX_ROWS)
        assertTrue(r.groups.size <= SearchRanker.MAX_GROUPS)
        assertEquals(SearchSection.WEB, r.groups.last().section)
        assertTrue(r.groups.all { it.hits.size <= SearchRanker.cap(it.section) })
        assertEquals(r.groups.size, r.groups.map { it.section }.toSet().size)
    }

    @Test fun answerFirst_andIdleShowsRecent() {
        val r = SearchRanker.search("2+3", listOf(app("2 Player Games")), SearchIndex.answerItem("2+3"))
        assertEquals(SearchSection.ANSWER, r.groups.first().section)
        assertEquals("= 5", r.groups.first().hits.single().item.title)
        val idle = SearchRanker.search(" ", listOf(app("A", 1), app("B", 0), app("C"), SearchItem(SearchSection.CONTINUE, "c", "Dark", recency = 0)))
        assertEquals(listOf(SearchSection.RECENT, SearchSection.CONTINUE), idle.groups.map { it.section })
        assertEquals(listOf("B", "A", "Dark"), titles(idle))
        assertTrue(idle.rows <= SearchRanker.MAX_ROWS_IDLE)
    }

    @Test fun layoutAlwaysFits() {
        val full = SearchResults((0 until 4).map { g -> SearchGroup(SearchSection.values()[g], List(2) { SearchHit(app("x$g$it"), 0) }) })
        assertEquals(8, full.rows)
        assertTrue(SearchLayout.fits(full, actions = false, hint = false, bottomMarginDp = Theme.MARGIN_V))
        val idle = SearchResults(listOf(SearchGroup(SearchSection.RECENT, List(2) { SearchHit(app("r$it"), 0) }), SearchGroup(SearchSection.CONTINUE, List(2) { SearchHit(app("c$it"), 0) })))
        assertTrue(SearchLayout.fits(idle, actions = true, hint = true, bottomMarginDp = Theme.MARGIN_V))
    }

    @Test fun calculator() {
        fun v(q: String) = QuickAnswer.answer(q)?.value
        assertEquals("7", v("1+2*3"))
        assertEquals("9", v("(1+2)*3"))
        assertEquals("0.3", v("0.1 + 0.2"))
        assertEquals("2.5", v("5 ÷ 2"))
        assertEquals("12", v("3x4"))
        assertEquals("12", v("3 × 4 ="))
        assertEquals("-1", v("-3+2"))
        assertEquals("3.5", v("1,5+2"))
        assertEquals("0.3333333333", v("1/3"))
        assertNull(v("1/0"))
        assertNull(v("42"))
        assertNull(v("-5"))
        assertNull(v("netflix"))
        assertNull(v("2+"))
        assertNull(v("(2+3"))
        assertNull(v("1,000+1"))
        assertNull(v("x3"))
        assertNull(v("2^3"))
        assertNull(v("1".repeat(70) + "+1"))
    }

    @Test fun indexAndWebRows() {
        val apps = listOf(AppEntry("com.netflix.ninja", "Netflix", "A"), AppEntry("com.netflix.ninja", "Netflix", "B"))
        val items = SearchIndex.build(apps, listOf("com.netflix.ninja"), listOf(SettingsEntry("home_layout", "Home layout", "How it looks", 0)),
            listOf(QuickActions.Item.NETWORK), listOf(WatchProgram(1, "com.netflix.ninja", "Dark", season = "1", episode = "2")),
            emptyList(), setOf("com.netflix.ninja"))
        assertEquals(1, items.count { it.section == SearchSection.APPS })
        assertEquals(0, items.first { it.section == SearchSection.APPS }.recency)
        assertEquals("S1 · E2 · Netflix", items.first { it.section == SearchSection.CONTINUE }.subtitle)
        assertEquals("Network", titles(SearchRanker.search("wifi", items)).single())
        assertEquals(listOf(WebKind.WEB, WebKind.YOUTUBE), SearchIndex.webRows("x", browser = true, youtube = false, store = true))
        assertEquals(listOf(WebKind.YOUTUBE, WebKind.GET_BROWSER), SearchIndex.webRows("x", browser = false, youtube = true, store = true))
        assertTrue(SearchIndex.webRows(" ", true, true, true).isEmpty())
        assertEquals("https://www.youtube.com/results?search_query=lo-fi+beats+%C3%A9", SearchIndex.youtubeUrl(" lo-fi beats é "))
    }
}
