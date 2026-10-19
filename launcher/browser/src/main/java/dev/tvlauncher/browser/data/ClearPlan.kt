package dev.tvlauncher.browser.data

/**
 * What "Clear browsing data" removes. Bookmarks are kept unless the "everything" option is
 * chosen; settings (text size, search engine, cursor) always stay. Open tabs are closed (the
 * session becomes one blank start-page tab).
 */
object ClearPlan {
    enum class Item(val label: String) {
        HTTP_CACHE("cache"), FORM_DATA("form data"), WEB_STORAGE("site storage"), COOKIES("cookies"),
        HTTP_AUTH("saved sign-ins"), HISTORY("history"), FAVICONS("site icons"), TAB_THUMBNAILS("page previews"),
        TAB_STATES("back/forward lists"), SESSION_FILE("saved session"), OPEN_TABS("open tabs"),
        BOOKMARKS("bookmarks")
    }

    /** How the WebView-owned HTTP cache / form data are cleared. */
    enum class CacheStep { DIRECT, TEMP_WEBVIEW }

    fun forOption(includeBookmarks: Boolean): Set<Item> {
        val all = Item.values().toMutableSet()
        if (!includeBookmarks) all.remove(Item.BOOKMARKS)
        return all
    }

    fun cacheStep(liveWebView: Boolean): CacheStep = if (liveWebView) CacheStep.DIRECT else CacheStep.TEMP_WEBVIEW

    fun describe(items: Set<Item>): String = Item.values().filter { it in items }.joinToString(", ") { it.label }

    fun confirmText(includeBookmarks: Boolean): String =
        if (includeBookmarks) {
            "Clears history, cookies, cache, site data, open tabs and page previews, and resets bookmarks to the defaults. Settings stay."
        } else {
            "Clears history, cookies, cache, site data, open tabs and page previews. Bookmarks and settings stay."
        }
}
