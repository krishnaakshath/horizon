package dev.tvlauncher.panel

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.widget.TextView
import dev.tvlauncher.type.FontWeight
import dev.tvlauncher.type.Fonts
import dev.tvlauncher.ui.Motion
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.dp

/**
 * The one visual vocabulary of every overlay: a near-opaque glass surface (at least 95 % so the
 * layout auditor treats it as opaque and nothing shows through), an 8 dp grid, a white focus fill
 * (the same as the top-bar pills), one accent colour for "on" states and no borders or dividers.
 * Text never goes below 14 sp.
 */
object PanelStyle {
    // 8 dp grid.
    const val G1 = 8
    const val G2 = 16
    const val G3 = 24
    const val G4 = 32

    /** Surface fill: SURFACE at 96 % (LITE 98 %). Both are >= the auditor's 0.95 opacity bar. */
    const val SURFACE = 0xF5151922.toInt()
    const val SURFACE_LITE = 0xFA151922.toInt()
    /** Full-screen dim behind a panel; tagged audit:scrim. */
    const val SCRIM = 0x8C05070B.toInt()
    /** The single accent: switches that are on, the current choice. */
    const val ACCENT = 0xFF8AB4FF.toInt()
    const val TEXT = Theme.TEXT
    const val TEXT_2 = Theme.TEXT_2
    const val ON_FOCUS = Theme.TEXT_DARK
    const val ON_FOCUS_2 = 0xB30B0D12.toInt()
    const val FOCUS_FILL = Theme.TEXT
    const val SWITCH_OFF = 0x40FFFFFF
    const val SWITCH_OFF_FOCUSED = 0x400B0D12
    const val TRANSPARENT = 0x00FFFFFF
    /** Quiet fill for secondary buttons / chips inside a panel (no stroke). */
    const val QUIET_FILL = 0x14FFFFFF

    const val CARD_RADIUS = 24
    const val ROW_RADIUS = 14
    const val ICON_DP = 24
    const val HEADER_ICON_DP = 28

    // Type scale (sp); 14 is the floor.
    const val TITLE_SP = 22f
    const val ROW_SP = 17f
    const val VALUE_SP = 15f
    const val MESSAGE_SP = 16f
    const val DETAIL_SP = 14f
    const val SECTION_SP = 14f

    /** Width of a panel anchored under the top bar, and of a centred menu. */
    const val SIDE_WIDTH = 480
    const val MENU_WIDTH = 440

    fun surface(context: Context, lite: Boolean, radiusDp: Int = CARD_RADIUS) = GradientDrawable().apply {
        setColor(if (lite) SURFACE_LITE else SURFACE)
        cornerRadius = context.dp(radiusDp).toFloat()
    }

    fun rounded(context: Context, fill: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = context.dp(radiusDp).toFloat()
    }

    /** Inter (bundled) at [weight]; falls back to the system family inside [Fonts]. */
    fun TextView.panelText(sp: Float, weight: FontWeight, color: Int = TEXT, singleLine: Boolean = true) {
        typeface = Fonts.typeface(context, weight)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        includeFontPadding = false
        if (singleLine) {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    /** System animator scale (1 when unreadable). */
    fun animatorScale(context: Context): Float = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (e: RuntimeException) { 1f }

    fun openMs(context: Context, lite: Boolean) = PanelMotion.duration(lite, animatorScale(context), Motion.enabled)
    fun closeMs(context: Context, lite: Boolean) = PanelMotion.closeDuration(lite, animatorScale(context), Motion.enabled)
}
