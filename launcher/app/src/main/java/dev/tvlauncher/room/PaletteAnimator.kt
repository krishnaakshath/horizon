package dev.tvlauncher.room

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator

/**
 * Crossfades between two [Palette]s into one pre-allocated [frame]. One reusable [ValueAnimator];
 * per frame it only writes ints/floats into [frame] (Palettes.blendInto) - no allocation.
 *
 * Colours are blended in linear light by [Palettes.color] rather than with ArgbEvaluator, so the
 * result is identical on API 24/25 (ArgbEvaluator blends gamma-encoded there) and on 26+, and
 * matches the unit-tested blend exactly.
 *
 * A duration <= 0 (system animator scale 0, transition "Off") snaps to the target. Starting a new
 * crossfade mid-way continues from the colours currently on screen. Main thread only.
 */
class PaletteAnimator(
    initial: Palette,
    private val onFrame: (frame: PaletteFrame, settled: Boolean) -> Unit
) {
    val frame = PaletteFrame().set(initial)
    private var from: Palette = initial
    private var to: Palette = initial
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        interpolator = DecelerateInterpolator(1.5f)
        addUpdateListener { a ->
            // animatedFraction is the interpolated fraction and avoids boxing animatedValue.
            Palettes.blendInto(from, to, a.animatedFraction, frame)
            onFrame(frame, false)
        }
        addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationStart(animation: Animator) { cancelled = false }
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) {
                if (cancelled) return
                frame.set(to)
                onFrame(frame, true)
            }
        })
    }

    val isRunning: Boolean get() = animator.isRunning

    /** The palette the animator is heading to (or showing). */
    val target: Palette get() = to

    fun animateTo(target: Palette, durationMs: Long) {
        if (target == to && !animator.isRunning) return
        val running = animator.isRunning
        if (running) animator.cancel()
        from = if (running) frame.toPalette() else to
        to = target
        if (durationMs <= 0L) {
            frame.set(target)
            onFrame(frame, true)
            return
        }
        animator.duration = durationMs
        animator.start()
    }

    /** Jumps to [target] immediately. */
    fun snapTo(target: Palette) = animateTo(target, 0L)

    fun cancel() {
        if (animator.isRunning) animator.cancel()
        frame.set(to)
    }
}
