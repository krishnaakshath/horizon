package dev.tvlauncher.browser.data

import dev.tvlauncher.browser.data.ClearPlan.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearPlanTest {
    private val always = setOf(
        Item.HTTP_CACHE, Item.FORM_DATA, Item.WEB_STORAGE, Item.COOKIES, Item.HTTP_AUTH, Item.HISTORY,
        Item.FAVICONS, Item.TAB_THUMBNAILS, Item.TAB_STATES, Item.SESSION_FILE, Item.INACTIVE_TABS
    )

    @Test fun default_clearsEverythingButBookmarks() {
        val p = ClearPlan.forOption(includeBookmarks = false)
        assertEquals(always, p)
        assertFalse(Item.BOOKMARKS in p)
    }

    @Test fun everything_alsoResetsBookmarks() {
        val p = ClearPlan.forOption(includeBookmarks = true)
        assertEquals(always + Item.BOOKMARKS, p)
    }

    @Test fun httpCache_isAlwaysIncluded_evenWithoutLiveWebView() {
        assertTrue(Item.HTTP_CACHE in ClearPlan.forOption(false))
        assertTrue(ClearPlan.needsTemporaryWebView(liveWebView = false))
        assertFalse(ClearPlan.needsTemporaryWebView(liveWebView = true))
    }

    @Test fun describe_mentionsBookmarksOnlyWhenIncluded() {
        assertFalse(ClearPlan.describe(ClearPlan.forOption(false)).contains("bookmarks"))
        assertTrue(ClearPlan.describe(ClearPlan.forOption(true)).contains("bookmarks"))
    }
}
