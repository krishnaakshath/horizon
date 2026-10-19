package dev.tvlauncher.system

import android.app.Activity
import android.content.Context

/**
 * Deep links into the launcher's own Settings: a section id (AppSettingsActivity.EXTRA_SECTION)
 * plus an optional sub-page opened once on arrival ([EXTRA_SUBPAGE]).
 */
object SystemLinks {
    const val EXTRA_SUBPAGE = "dev.tvlauncher.extra.SUBPAGE"
    const val APPS = SystemSpecs.APPS
    const val VOLUME = "sys.volume"
    const val POWER = "sys.power"
    private const val APP_PREFIX = "apps.info:"

    fun appPage(pkg: String) = APP_PREFIX + pkg

    /** The package of an app sub-page link, or null. */
    fun appOf(subpage: String?): String? = subpage?.takeIf { it.startsWith(APP_PREFIX) }?.removePrefix(APP_PREFIX)?.takeIf { it.isNotBlank() }

    /** Reads the sub-page requested by the activity's intent and clears it so a rebuild does not reopen it. */
    fun consume(context: Context): String? {
        val a = context as? Activity ?: return null
        val v = a.intent?.getStringExtra(EXTRA_SUBPAGE) ?: return null
        a.intent?.removeExtra(EXTRA_SUBPAGE)
        return v
    }
}
