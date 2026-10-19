package dev.tvlauncher.browser

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import dev.tvlauncher.browser.data.ClearPlan
import dev.tvlauncher.browser.data.ClearPlan.Item

/**
 * Executes the WebView-side part of a [ClearPlan] on the main thread. The HTTP cache and form
 * data belong to a WebView, so when none is live (start page) a temporary one is created from the
 * application context, used, and destroyed. App-side items (history, icons, thumbnails, session,
 * bookmarks) are handled by the activity, which owns those stores.
 */
class BrowsingDataCleaner(private val context: Context) {
    fun clearWebData(plan: Set<Item>, live: WebView?) {
        var temp: WebView? = null
        try {
            val w = live ?: if (ClearPlan.needsTemporaryWebView(false) && (Item.HTTP_CACHE in plan || Item.FORM_DATA in plan)) {
                WebView(context.applicationContext).also { temp = it }
            } else null
            if (Item.HTTP_CACHE in plan) w?.clearCache(true)
            if (Item.FORM_DATA in plan) w?.clearFormData()
            if (Item.TAB_STATES in plan) live?.clearHistory()
        } catch (t: Throwable) {
            Log.w(TAG, "clear WebView cache", t)
        } finally {
            temp?.destroy()
        }
        try {
            if (Item.WEB_STORAGE in plan) WebStorage.getInstance().deleteAllData()
            if (Item.COOKIES in plan) CookieManager.getInstance().apply { removeAllCookies(null); flush() }
            if (Item.HTTP_AUTH in plan) WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
        } catch (t: Throwable) {
            Log.w(TAG, "clear web storage", t)
        }
    }

    private companion object { const val TAG = "BrowserClear" }
}
