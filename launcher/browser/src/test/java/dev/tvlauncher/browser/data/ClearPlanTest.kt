package dev.tvlauncher.browser.data

import dev.tvlauncher.browser.data.ClearPlan.CacheStep
import dev.tvlauncher.browser.data.ClearPlan.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearPlanTest {
    private val always = setOf(
        Item.HTTP_CACHE, Item.FORM_DATA, Item.WEB_STORAGE, Item.COOKIES, Item.HTTP_AUTH, Item.HISTORY,
        Item.FAVICONS, Item.TAB_THUMBNAILS, Item.TAB_STATES, Item.SESSION_FILE, Item.OPEN_TABS
    )

    @Test fun default_clearsEverythingButBookmarks() {
        assertEquals(always, ClearPlan.forOption(includeBookmarks = false))
    }

    @Test fun everything_alsoResetsBookmarks() {
        assertEquals(always + Item.BOOKMARKS, ClearPlan.forOption(includeBookmarks = true))
    }

    @Test fun cacheStep_usesTemporaryWebViewOnlyWithoutLiveOne() {
        assertEquals(CacheStep.TEMP_WEBVIEW, ClearPlan.cacheStep(liveWebView = false))
        assertEquals(CacheStep.DIRECT, ClearPlan.cacheStep(liveWebView = true))
    }

    @Test fun confirmText_listsWhatIsClearedAndWhatStays() {
        val t = ClearPlan.confirmText(includeBookmarks = false)
        listOf("history", "cookies", "cache", "open tabs", "page previews").forEach { assertTrue(it, t.contains(it)) }
        assertTrue(t.contains("Bookmarks and settings stay"))
        val e = ClearPlan.confirmText(includeBookmarks = true)
        assertTrue(e.contains("bookmarks"))
        assertTrue(e.contains("Settings stay"))
        assertFalse(e.contains("Bookmarks and settings stay"))
    }
}
