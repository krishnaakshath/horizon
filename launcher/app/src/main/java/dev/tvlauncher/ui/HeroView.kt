package dev.tvlauncher.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.ui.Theme.style

/** Large focused-app title with a subtitle; text color follows the backdrop for contrast. */
class HeroView(context: Context, private val pm: PackageManager) : LinearLayout(context) {
    val title = TextView(context).apply {
        style(Theme.SP_HERO, Theme.semibold)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        letterSpacing = -0.01f
        setShadowLayer(context.dp(12).toFloat(), 0f, 0f, 0x40000000)
    }
    val subtitle = TextView(context).apply {
        style(Theme.SP_SUB, Theme.regular, Theme.TEXT_2)
        maxLines = 1
    }
    /** "Watching · Video" etc. (see [dev.tvlauncher.home.RecentMode]); hidden unless a mode was detected. */
    val modeChip = TextView(context).apply {
        style(Theme.SP_LABEL, Theme.medium)
        maxLines = 1
        setPadding(context.dp(10), context.dp(2), context.dp(10), context.dp(2))
        background = GradientDrawable().apply {
            cornerRadius = context.dp(Theme.RADIUS_PILL).toFloat()
            setColor(0x26FFFFFF)
        }
        visibility = View.GONE
    }
    private val categories = HashMap<String, String>()

    init {
        orientation = VERTICAL
        addView(title, LayoutParams(-1, -2))
        val line = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        line.addView(modeChip, LayoutParams(-2, -2).apply { marginEnd = context.dp(Theme.S3) })
        line.addView(subtitle, LayoutParams(0, -2, 1f))
        addView(line, LayoutParams(-1, -2).apply { topMargin = context.dp(Theme.S2) })
    }

    fun setModeChip(text: String?) {
        modeChip.text = text
        modeChip.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    fun show(app: AppEntry, backdrop: Int) {
        title.text = app.label
        subtitle.text = HeroText.subtitle(category(app.packageName))
        val light = BrandColors.onColorTextIsLight(backdrop)
        title.setTextColor(if (light) Theme.TEXT else Theme.TEXT_DARK)
        subtitle.setTextColor(if (light) Theme.TEXT_2 else Theme.TEXT_DARK_2)
        modeChip.setTextColor(if (light) Theme.TEXT else Theme.TEXT_DARK)
    }

    /** A non-app entry (the "Get more apps" tile): plain title and subtitle, no mode chip. */
    fun showText(titleText: String, subtitleText: String, backdrop: Int) {
        title.text = titleText
        subtitle.text = subtitleText
        val light = BrandColors.onColorTextIsLight(backdrop)
        title.setTextColor(if (light) Theme.TEXT else Theme.TEXT_DARK)
        subtitle.setTextColor(if (light) Theme.TEXT_2 else Theme.TEXT_DARK_2)
    }

    /** Category word for media apps only (see [HeroText]); null for system/store/utility apps. */
    private fun category(pkg: String): String? = categories.getOrPut(pkg) {
        if (Build.VERSION.SDK_INT < 26) return@getOrPut NONE
        val c = try { pm.getApplicationInfo(pkg, 0).category } catch (e: PackageManager.NameNotFoundException) { -1 }
        HeroText.categoryWord(c) ?: NONE
    }.takeIf { it != NONE }

    private companion object {
        const val NONE = ""
    }
}

/** Hero area: the [hero] centered vertically, or an empty-state message when there are no apps. */
class HeroBox(context: Context, pm: PackageManager) : FrameLayout(context) {
    val hero = HeroView(context, pm)
    private val emptyText = TextView(context).apply {
        text = "No apps found — open the Play Store"
        style(22f, Theme.regular, Theme.TEXT_2)
        gravity = Gravity.CENTER
        visibility = View.GONE
    }

    private var side: View? = null
    private var sideWidth = 0

    init {
        addView(hero, LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
        addView(emptyText, LayoutParams(-1, -1))
    }

    /**
     * A card at the end of the hero row (Now Playing). While it is shown the hero text stops
     * [HeroLayout.GAP_DP] before it, so a long app name never runs under the card.
     */
    fun setSideCard(card: View, widthDp: Int) {
        side?.let { removeView(it) }
        side = card
        sideWidth = context.dp(widthDp)
        addView(card, LayoutParams(sideWidth, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        sideCardChanged()
    }

    /** Call after the side card's visibility changed. */
    fun sideCardChanged() {
        val shown = side?.visibility == View.VISIBLE
        val lp = hero.layoutParams as LayoutParams
        val end = HeroLayout.heroEndMarginPx(shown, sideWidth, context.dp(HeroLayout.GAP_DP))
        if (lp.marginEnd != end) { lp.marginEnd = end; hero.layoutParams = lp }
    }

    fun setEmpty(empty: Boolean) {
        emptyText.visibility = if (empty) View.VISIBLE else View.GONE
        hero.visibility = if (empty) View.GONE else View.VISIBLE
    }
}

/** Pure layout rule for the hero row with an optional side card. */
object HeroLayout {
    const val GAP_DP = 32

    fun heroEndMarginPx(sideShown: Boolean, sideWidthPx: Int, gapPx: Int): Int = if (sideShown && sideWidthPx > 0) sideWidthPx + gapPx else 0
}
