package dev.tvlauncher.browser.net

import dev.tvlauncher.browser.net.SuggestionMerger.Kind
import dev.tvlauncher.browser.net.SuggestionMerger.Local
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    @Test fun parse_ddgListForm() {
        assertEquals(listOf("tv shows", "tv guide"), SuggestParser.parse("""["tv",["tv shows","tv guide"]]"""))
    }

    @Test fun parse_ddgObjectForm() {
        assertEquals(listOf("tv shows", "tv guide"), SuggestParser.parse("""[{"phrase":"tv shows"},{"phrase":"tv guide"}]"""))
    }

    @Test fun parse_hostileJson_neverThrows() {
        listOf(
            null, "", "null", "{}", "[", "]]]", "[\"q\", 5]", "[\"q\", [1, null, {}, [], \"ok\"]]",
            "\u0000￿", "[" .repeat(10_000), "{\"phrase\":", "[{\"phrase\":7}]", "true"
        ).forEach { SuggestParser.parse(it) }
        assertEquals(listOf("ok"), SuggestParser.parse("[\"q\", [1, null, {}, [], \"ok\", \"  \"]]"))
    }

    @Test fun parse_capsCountAndLength() {
        val many = (1..30).joinToString(",", "[\"q\",[", "]]") { "\"s$it\"" }
        assertEquals(8, SuggestParser.parse(many).size)
        val long = "[\"q\",[\"" + "x".repeat(500) + "\"]]"
        assertTrue(SuggestParser.parse(long).isEmpty())
    }

    @Test fun merge_localFirst_thenRemote_dedupedAndCapped() {
        val bookmarks = listOf(Local("GitHub", "https://github.com/"), Local("Google", "https://www.google.com/"))
        val history = listOf(Local("GitHub", "https://github.com/"), Local("Git docs", "https://git-scm.com/doc"), Local("Weather", "https://weather.com/"))
        val remote = listOf("git", "github", "GitHub", "git tutorial", "gitlab", "git bash", "git log", "git rebase")
        val r = SuggestionMerger.merge("git", bookmarks, history, remote, max = 6)
        assertEquals(Kind.BOOKMARK, r[0].kind)
        assertEquals("https://github.com/", r[0].url)
        assertEquals(Kind.HISTORY, r[1].kind)
        assertEquals("https://git-scm.com/doc", r[1].url)
        assertEquals(listOf("git", "github", "git tutorial", "gitlab"), r.drop(2).map { it.text })
        assertTrue(r.drop(2).all { it.kind == Kind.SEARCH })
        assertEquals(6, r.size)
    }

    @Test fun merge_blankQuery_isEmpty() {
        assertTrue(SuggestionMerger.merge("  ", listOf(Local("a", "https://a.com")), emptyList(), listOf("a")).isEmpty())
    }
}
