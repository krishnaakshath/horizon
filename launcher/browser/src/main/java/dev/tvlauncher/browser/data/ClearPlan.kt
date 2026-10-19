package dev.tvlauncher.browser.data

/** What "Clear browsing data" removes. Bookmarks are kept unless the "everything" option is chosen. */
object ClearPlan {
    enum class Item(val label: String) {
        HTTP_CACHE("cached pages"), FORM_DATA("form data"), WEB_STORAGE("site storage"), COOKIES("cookies"),
        HTTP_AUTH("saved sign-ins"), HISTORY("history"), FAVICONS("site icons"), TAB_THUMBNAILS("tab previews"),
        TAB_STATES("tab back/forward lists"), SESSION_FILE("saved session"), INACTIVE_TABS("other tabs' pages"),
        BOOKMARKS("bookmarks (reset to defaults)")
    }

    fun forOption(includeBookmarks: Boolean): Set<Item> {
        val all = Item.values().toMutableSet()
        if (!includeBookmarks) all.remove(Item.BOOKMARKS)
        return all
    }

    /** The HTTP cache and form data live in the WebView; without a live one a temporary WebView clears them. */
    fun needsTemporaryWebView(liveWebView: Boolean): Boolean = !liveWebView

    fun describe(items: Set<Item>): String = Item.values().filter { it in items }.joinToString(", ") { it.label }
}
