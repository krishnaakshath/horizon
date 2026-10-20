package dev.tvlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import dev.tvlauncher.type.FontWeight
import dev.tvlauncher.type.Fonts
import dev.tvlauncher.type.Tracking

/** Visual system: palette, type scale, spacing (dp) and radii (dp). */
object Theme {
    const val BASE = 0xFF0B0D12.toInt()
    const val SURFACE = 0xFF151922.toInt()
    const val TEXT = 0xFFF5F7FA.toInt()
    const val TEXT_2 = 0xFFA7B0C0.toInt()
    const val TEXT_DARK = 0xFF0B0D12.toInt()
    const val TEXT_DARK_2 = 0xCC0B0D12.toInt()
    const val STROKE = 0x1FFFFFFF

    // Glass layers (Settings screen): translucent white/near-black over BASE, no blur.
    const val GLASS_ROW_FOCUS = 0x26FFFFFF
    const val GLASS_CHIP = 0x14FFFFFF
    const val GLASS_SCRIM = 0xF2101319.toInt()
    /** Track of a switch that is off. */
    const val TOGGLE_OFF = 0x40FFFFFF
    /** Subtitle on a focused row: between TEXT_2 and TEXT. */
    const val TEXT_SUB_FOCUSED = 0xFFC9D0DC.toInt()
    /** Alpha (in the top byte) of the static accent glow behind Settings. */
    const val GLOW_ALPHA = 0x2E000000

    // Spacing scale (dp).
    const val S1 = 4
    const val S2 = 8
    const val S3 = 12
    const val S4 = 16
    const val S5 = 24
    const val S6 = 32

    // Overscan-safe margins (dp).
    const val MARGIN_H = 48
    const val MARGIN_V = 27

    // Radii (dp).
    const val RADIUS_TILE = 18
    const val RADIUS_PILL = 999

    // Type scale (sp).
    const val SP_CLOCK = 30f
    const val SP_DATE = 15f
    const val SP_HERO = 44f
    const val SP_SUB = 16f
    const val SP_LABEL = 13f
    const val SP_BUTTON = 15f
    /** Smallest focusable target on the TV (the layout auditor's TOO_SMALL_TARGET floor). */
    const val MIN_TARGET_DP = 40

    // Faces: the bundled Inter instances (dev.tvlauncher.type.Fonts) once [installFonts] ran at
    // process start, else the system sans. Fonts caches each weight, so these getters are cheap.
    @SuppressLint("StaticFieldLeak") // the application context only (see installFonts)
    @Volatile private var fontContext: Context? = null

    /** Process start (LauncherApp): every [medium]/[light]/[regular]/[semibold] after this is Inter. */
    fun installFonts(context: Context) {
        fontContext = context.applicationContext
        Fonts.warmUp(context)
    }

    private val sysMedium: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val sysLight: Typeface by lazy { Typeface.create("sans-serif-light", Typeface.NORMAL) }
    private val sysRegular: Typeface by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }

    private fun face(weight: FontWeight, fallback: () -> Typeface): Typeface =
        fontContext?.let { Fonts.typeface(it, weight) } ?: fallback()

    val medium: Typeface get() = face(FontWeight.MEDIUM) { sysMedium }
    val light: Typeface get() = face(FontWeight.LIGHT) { sysLight }
    val regular: Typeface get() = face(FontWeight.REGULAR) { sysRegular }
    /** Titles and the hero name. */
    val semibold: Typeface get() = face(FontWeight.SEMIBOLD) { sysMedium }
    /** The brand wordmark face (splash "H02" / "HORIZON", About header) only; never body text. */
    val brand: Typeface get() = fontContext?.let { dev.tvlauncher.brand.BrandFont.typeface(it) } ?: sysMedium

    fun TextView.style(sp: Float, face: Typeface, color: Int = TEXT) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = face
        setTextColor(color)
        includeFontPadding = false
        if (letterSpacing == 0f) letterSpacing = Tracking.forSp(sp) // explicit tracking set earlier (caps labels) wins
    }
}
