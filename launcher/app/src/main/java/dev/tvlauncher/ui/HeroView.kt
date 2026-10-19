package dev.tvlauncher.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.text.TextUtils
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.ui.Theme.style

/** Large focused-app title with a subtitle; text color follows the backdrop for contrast. */
class HeroView(context: Context, private val pm: PackageManager) : LinearLayout(context) {
    val title = TextView(context).apply {
        style(Theme.SP_HERO, Theme.medium)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        letterSpacing = -0.01f
        setShadowLayer(context.dp(12).toFloat(), 0f, 0f, 0x40000000)
    }
    val subtitle = TextView(context).apply {
        style(Theme.SP_SUB, Theme.regular, Theme.TEXT_2)
        maxLines = 1
    }
    private val categories = HashMap<String, String>()

    init {
        orientation = VERTICAL
        addView(title, LayoutParams(-1, -2))
        addView(subtitle, LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S2) })
    }

    fun show(app: AppEntry, backdrop: Int) {
        title.text = app.label
        val cat = category(app.packageName)
        subtitle.text = if (cat.isEmpty()) HINT else "$cat   ·   $HINT"
        val light = BrandColors.onColorTextIsLight(backdrop)
        title.setTextColor(if (light) Theme.TEXT else Theme.TEXT_DARK)
        subtitle.setTextColor(if (light) Theme.TEXT_2 else Theme.TEXT_DARK_2)
    }

    private fun category(pkg: String): String = categories.getOrPut(pkg) {
        if (Build.VERSION.SDK_INT < 26) return@getOrPut ""
        val c = try { pm.getApplicationInfo(pkg, 0).category } catch (e: PackageManager.NameNotFoundException) { -1 }
        when (c) {
            ApplicationInfo.CATEGORY_VIDEO -> "Movies & TV"
            ApplicationInfo.CATEGORY_AUDIO -> "Music & Audio"
            ApplicationInfo.CATEGORY_GAME -> "Games"
            ApplicationInfo.CATEGORY_IMAGE -> "Photos"
            ApplicationInfo.CATEGORY_NEWS -> "News"
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            ApplicationInfo.CATEGORY_MAPS -> "Maps"
            else -> ""
        }
    }

    private companion object {
        const val HINT = "Hold OK for options"
    }
}
