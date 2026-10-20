package dev.tvlauncher.splash

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.ui.RevealTargets
import dev.tvlauncher.ui.dp

/**
 * Shared playback of a [StartupTimelines.Timeline]: subclasses animate the intro parts in [playIntro];
 * the hand-over (OUT and CONTENT) is the same for every style: the overlay fades and the whole home
 * content fades and rises once. ViewPropertyAnimator only, no hardware layers, no blur, nothing
 * allocated per frame. Never focusable, so it cannot take input.
 */
abstract class TimelineSplash(
    context: Context,
    protected val full: Boolean,
    protected val timeline: StartupTimelines.Timeline
) : FrameLayout(context), StartupPlayer {
    protected val decel = DecelerateInterpolator(2f)
    private var startedAt = 0L

    override val view: View get() = this
    override val totalMs: Long get() = timeline.totalMs

    override fun hasOverlappingRendering() = false

    init {
        auditTag(AuditTags.FULLBLEED, AuditTags.IGNORE) // transient start-up layer
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun prepare(t: RevealTargets) {
        t.content.alpha = 0f
        t.content.translationY = px(16)
    }

    override fun start() {
        startedAt = SystemClock.uptimeMillis()
        for (s in timeline.steps) playIntro(s)
    }

    /** Animate [s] if it is an intro part of this style (SWEEP, MARK, CAPTION). */
    protected abstract fun playIntro(s: StartupTimelines.Step)

    override fun reveal(t: RevealTargets) {
        val outStart = timeline.introEndMs
        val wait = maxOf(0L, outStart - (SystemClock.uptimeMillis() - startedAt))
        val shift = wait - outStart
        for (s in timeline.steps) when (s.part) {
            StartupTimelines.Part.OUT -> animate().alpha(0f).setInterpolator(decel).at(s, shift)
                .withEndAction { (parent as? ViewGroup)?.removeView(this) }
            StartupTimelines.Part.CONTENT -> t.content.animate().alpha(1f).translationY(0f)
                .setInterpolator(decel).at(s, shift)
            else -> Unit
        }
    }

    protected fun ViewPropertyAnimator.at(s: StartupTimelines.Step, shift: Long = 0L): ViewPropertyAnimator =
        setStartDelay(maxOf(0L, s.startMs + shift)).setDuration(s.durationMs).also { it.start() }

    protected fun px(v: Int) = context.dp(v).toFloat()
}
