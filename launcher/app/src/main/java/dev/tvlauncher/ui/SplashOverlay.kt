package dev.tvlauncher.ui

import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.brand.Brand
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.SplashReveal.Part

/** The views the cold-start reveal animates. [tiles] returns visible tiles' content, left to right. */
class RevealTargets(
    val content: View,
    val topBar: View,
    val hero: View,
    val shelf: View,
    val dock: View,
    val tiles: () -> List<View>
)

/**
 * Cold-start "sunrise" over the backdrop (see [SplashReveal]): a horizon line draws across, a sun-disc in
 * the [accent] rises on it, "H02" resolves above in the brand face with a soft glow (FULL only) and the
 * letter-spaced "HORIZON" caption fades in below; then the home rises. ViewPropertyAnimator only, no
 * hardware layers, no blur. Not created at all when animations are off ([enabled], HomeLook.showSplash).
 */
class SplashOverlay(
    context: Context,
    private val full: Boolean,
    accent: Int = SplashLook.DEFAULT_ACCENT,
) : FrameLayout(context) {
    private val decel = DecelerateInterpolator(2f)
    private val sunPx = context.dp(if (full) SplashLook.SUN_DP else SplashLook.SUN_DP_LITE)
    private val stageH = context.dp(SplashLook.STAGE_DP)

    /** "H02" in the brand face. */
    private val mark = TextView(context).apply {
        text = Brand.SHORT
        style(if (full) SplashLook.MARK_SP else SplashLook.MARK_SP_LITE, Theme.brand)
        letterSpacing = 0.04f
        // The glow: a text shadow tinted with the accent (FULL only; drawn with the glyphs, no layer).
        if (full) {
            val g = context.dp(SplashLook.GLOW_DP)
            setShadowLayer(g.toFloat(), 0f, 0f, SplashLook.glow(accent))
            setPadding(g, g / 2, g, g / 2) // the view bounds clip the glow; give it room
        }
    }

    /** The horizon: a hairline that draws out from the centre. */
    private val line = View(context).apply {
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0x00FFFFFF, SplashLook.LINE_CENTER, 0x00FFFFFF)
        )
    }

    /** The sun: a disc in the accent, lighter at its core; the stage clips its lower half below the line. */
    private val sun = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(SplashLook.sunCore(accent), accent or 0xFF000000.toInt())
            gradientRadius = sunPx / 2f
        }
    }

    /** FULL: a soft accent bloom around the sun, also clipped at the horizon. */
    private val bloom: View? = if (!full) null else View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(SplashLook.glow(accent), 0x00000000)
            gradientRadius = sunPx * 1.6f
        }
    }

    /** Line, sun and bloom; clips children, so whatever is below the line is hidden. */
    private val stage = FrameLayout(context).apply {
        clipChildren = true
        bloom?.let { addView(it, LayoutParams(sunPx * 4, sunPx * 4, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM)) }
        addView(sun, LayoutParams(sunPx, sunPx, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM))
        addView(line, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(SplashLook.LINE_DP).coerceAtLeast(1),
            Gravity.BOTTOM))
    }

    /** "HORIZON", small and letter-spaced. */
    private val caption = TextView(context).apply {
        text = Brand.CAPTION
        style(SplashLook.CAPTION_SP, Theme.brand, Theme.TEXT_2)
        letterSpacing = SplashLook.CAPTION_TRACKING
    }

    private val group = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(mark, LinearLayout.LayoutParams(-2, -2))
        addView(stage, LinearLayout.LayoutParams(context.dp(SplashLook.STAGE_W_DP), stageH)
            .apply { topMargin = if (full) 0 else context.dp(Theme.S2) })
        addView(caption, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(Theme.S4) })
    }

    override fun hasOverlappingRendering() = false

    init {
        auditTag(AuditTags.FULLBLEED, AuditTags.IGNORE) // transient cold-start layer
        // FULL: a soft halo in the accent behind the mark, so the first frame reads as a lit stage, not a void.
        if (full) background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(SplashLook.halo(accent), 0x00000000)
            gradientRadius = context.dp(SplashLook.HALO_RADIUS_DP).toFloat()
        }
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(group, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        // Start state of the intro.
        line.scaleX = 0f
        sun.translationY = sunPx.toFloat()
        bloom?.alpha = 0f
        bloom?.translationY = sunPx * 2.5f
        mark.alpha = 0f
        mark.scaleX = 0.94f; mark.scaleY = 0.94f
        mark.translationY = dp(8)
        caption.alpha = 0f
    }

    /** Hide everything the reveal will bring in. Call right after the layout is built. */
    fun prepare(t: RevealTargets) {
        if (full) {
            t.topBar.alpha = 0f; t.topBar.translationY = -dp(16)
            t.hero.alpha = 0f; t.hero.translationY = dp(24)
            t.shelf.alpha = 0f
            t.dock.alpha = 0f; t.dock.translationY = dp(32)
        } else {
            t.content.alpha = 0f; t.content.translationY = dp(20)
        }
    }

    private val timeline = if (full) null else SplashReveal.lite()
    private var startedAt = 0L

    /** Phase 1, at onCreate: the sunrise plays while apps load. */
    fun start() {
        startedAt = SystemClock.uptimeMillis()
        val tl = timeline ?: SplashReveal.full(0)
        for (s in tl.steps) {
            if (s.part !in SplashReveal.INTRO) continue
            when (s.part) {
                Part.LINE -> line.animate().scaleX(1f).setInterpolator(decel).at(s, 0L)
                Part.SUN -> {
                    // Half the disc stays below the horizon: a rising sun, not a ball.
                    sun.animate().translationY(sunPx / 2f).setInterpolator(decel).at(s, 0L)
                    bloom?.animate()?.alpha(1f)?.translationY(sunPx * 2f)?.setInterpolator(decel)?.at(s, 0L)
                }
                Part.MARK -> mark.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setInterpolator(decel).at(s, 0L)
                Part.CAPTION -> caption.animate().alpha(1f).setInterpolator(decel).at(s, 0L)
                else -> Unit
            }
        }
    }

    /**
     * Phase 2, once the first apps are laid out: everything after the intro runs on the [SplashReveal]
     * offsets, shifted so it never starts before the intro has finished. Removes itself when done.
     */
    fun reveal(t: RevealTargets) {
        val tiles = if (full) t.tiles().take(SplashReveal.MAX_STAGGERED_TILES) else emptyList()
        val tl = timeline ?: SplashReveal.full(tiles.size)
        val outStart = tl.introEndMs
        val elapsed = SystemClock.uptimeMillis() - startedAt
        val wait = maxOf(0L, outStart - elapsed)
        if (full) {
            // Start values must be set before animate() captures them. Tiles past the stagger
            // cap are off-screen and simply appear.
            for (v in tiles) { v.alpha = 0f; v.translationX = dp(40) }
            t.shelf.alpha = 1f
        }
        val shift = wait - outStart
        for (s in tl.steps) {
            when (s.part) {
                in SplashReveal.INTRO -> Unit
                Part.MARK_OUT -> {
                    group.animate().translationY(-dp(12)).setInterpolator(decel).at(s, shift)
                    animate().alpha(0f).setInterpolator(decel).at(s, shift)
                        .withEndAction { (parent as? ViewGroup)?.removeView(this) }
                }
                Part.TOP_BAR -> t.topBar.animate().alpha(1f).translationY(0f).setInterpolator(decel).at(s, shift)
                Part.HERO -> t.hero.animate().alpha(1f).translationY(0f).setInterpolator(decel).at(s, shift)
                Part.TILE -> tiles.getOrNull(s.index)?.animate()?.alpha(1f)?.translationX(0f)
                    ?.setInterpolator(decel)?.at(s, shift)
                Part.DOCK -> t.dock.animate().alpha(1f).translationY(0f).setInterpolator(decel).at(s, shift)
                Part.CONTENT -> t.content.animate().alpha(1f).translationY(0f).setInterpolator(decel).at(s, shift)
                else -> Unit
            }
        }
    }

    private fun ViewPropertyAnimator.at(s: SplashReveal.Step, shift: Long) =
        setStartDelay(maxOf(0L, s.startMs + shift)).setDuration(s.durationMs).also { it.start() }
    private fun dp(v: Int) = context.dp(v).toFloat()

    companion object {
        /** False when the user turned animations off (animator duration scale 0). */
        fun enabled(context: Context): Boolean = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        } catch (e: RuntimeException) {
            true
        }
    }
}

/** Cold-start look constants and colour math (JVM-testable). */
object SplashLook {
    /** AppearanceSettings.DEFAULT_ACCENT; the splash uses the user's fixed accent when one is set. */
    const val DEFAULT_ACCENT = 0xFF4C8DFF.toInt()
    /** Accent blue at ~16 % in the halo's centre (the default accent's [halo]). */
    const val HALO_CENTER = 0x294C8DFF
    const val HALO_ALPHA = 0x29
    const val HALO_RADIUS_DP = 360
    const val GLOW_ALPHA = 0x8C
    const val LINE_CENTER = 0xB3F5F7FA.toInt()

    const val MARK_SP = 76f
    const val MARK_SP_LITE = 64f
    const val CAPTION_SP = 13f
    const val CAPTION_TRACKING = 0.62f
    const val GLOW_DP = 18
    const val SUN_DP = 22
    const val SUN_DP_LITE = 18
    const val LINE_DP = 2
    const val STAGE_DP = 44
    const val STAGE_W_DP = 240

    /** [accent] at [HALO_ALPHA] for the backdrop halo. */
    fun halo(accent: Int): Int = (accent and 0x00FFFFFF) or (HALO_ALPHA shl 24)

    /** [accent] at [GLOW_ALPHA] for the mark's glow and the sun's bloom. */
    fun glow(accent: Int): Int = (accent and 0x00FFFFFF) or (GLOW_ALPHA shl 24)

    /** The sun's core: the accent mixed 60 % toward warm white, so even a dark accent reads as light. */
    fun sunCore(accent: Int): Int {
        fun mix(c: Int, w: Int) = c + ((w - c) * 6) / 10
        val r = mix((accent shr 16) and 0xFF, 0xFF)
        val g = mix((accent shr 8) and 0xFF, 0xF4)
        val b = mix(accent and 0xFF, 0xE0)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
