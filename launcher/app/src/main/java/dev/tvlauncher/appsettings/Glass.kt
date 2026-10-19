package dev.tvlauncher.appsettings

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import dev.tvlauncher.ui.GlassTokens
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.dp
import dev.tvlauncher.ui.Glass as SharedGlass

/**
 * Settings' view of the launcher's one glass style (dev.tvlauncher.ui.Glass / GlassTokens): the
 * sidebar and the pane are frosted cards over the room backdrop; rows and entries are faint glass
 * at rest and accent-lit glass with an accent ring (white text) when focused
 * ([SettingsStyle.focusDrawable]): one focus style for rows, sidebar entries and group headers.
 */
internal object Glass {
    const val ROW = GlassTokens.ROW_REST
    const val CHIP = GlassTokens.CHIP
    const val SELECTED = GlassTokens.SELECTED
    const val FOCUS = GlassTokens.FOCUS_FILL
    const val ON_FOCUS = GlassTokens.ON_FOCUS_SUBTLE
    const val HAIRLINE = Theme.STROKE
    /** Toast pill: near-opaque so it reads over any row. */
    const val SCRIM = Theme.GLASS_SCRIM
    /** Thin scroll indicator thumb: TEXT at about 35%. */
    const val THUMB = (0x59 shl 24) or (Theme.TEXT and 0x00FFFFFF)

    const val RADIUS_PANEL = GlassTokens.RADIUS_PANEL
    const val RADIUS_ROW = GlassTokens.RADIUS_ITEM
    const val RADIUS_CHIP = GlassTokens.RADIUS_CHIP

    /** A frosted card (sidebar, pane). */
    fun frost(view: View, full: Boolean) = SharedGlass.frost(view, full, RADIUS_PANEL)

    fun rect(context: Context, fill: Int, radiusDp: Int, stroke: Int = 0, strokeDp: Int = 1) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = context.dp(radiusDp).toFloat()
            if (stroke != 0) setStroke(context.dp(strokeDp), stroke)
        }

    fun pill(context: Context, fill: Int, stroke: Int = 0) = rect(context, fill, 999, stroke)

    /**
     * Row/entry background: [focused] (default: lit white glass) when focused, [activated] (e.g. the
     * current page) when activated, [rest] glass otherwise. Cross-faded over [fadeMs].
     */
    fun states(
        context: Context, fadeMs: Long, radiusDp: Int, rest: Drawable,
        activated: Drawable? = null, focused: Drawable? = null
    ): StateListDrawable =
        StateListDrawable().apply {
            setEnterFadeDuration(fadeMs.toInt())
            setExitFadeDuration(fadeMs.toInt())
            addState(intArrayOf(android.R.attr.state_focused), focused ?: rect(context, FOCUS, radiusDp))
            if (activated != null) addState(intArrayOf(android.R.attr.state_activated), activated)
            addState(intArrayOf(), rest)
        }

    /** The faint glass of a resting row. */
    fun item(context: Context, radiusDp: Int = RADIUS_ROW, fill: Int = ROW) = SharedGlass.item(context, radiusDp, fill)
}
