package dev.tvlauncher.helper

import android.view.KeyEvent

enum class KeyAction { DOWN, UP }

enum class Decision { PASS_THROUGH, CONSUME }

data class KeyInput(
    val keyCode: Int,
    val action: KeyAction,
    val repeatCount: Int,
    val canceled: Boolean,
    val foreground: String?,
)

/**
 * Pure key logic for the accessibility service. Fails open: on any exception
 * all state is reset and PASS_THROUGH is returned.
 */
class KeyStateMachine(
    private val now: () -> Long,
    private val settings: () -> HelperSettings,
    private val goHome: () -> Boolean,
    private val openSpotlight: () -> Unit,
) {
    private var detector: DoubleBackDetector? = null
    private var detectorWindow = -1L
    private var lastForeground: String? = null
    private var swallowBackUp = false
    private var backTainted = false
    private var shortcutDownKey = 0
    private var lastTapAt = -1L
    private var tapHeld = false

    fun reset() {
        detector = null
        detectorWindow = -1L
        lastForeground = null
        swallowBackUp = false
        backTainted = false
        shortcutDownKey = 0
        lastTapAt = -1L
        tapHeld = false
    }

    fun handle(input: KeyInput): Decision = try {
        handleInternal(input)
    } catch (e: Throwable) {
        reset()
        Decision.PASS_THROUGH
    }

    private fun handleInternal(input: KeyInput): Decision {
        if (input.foreground != lastForeground) {
            detector?.reset()
            backTainted = false
            lastForeground = input.foreground
        }
        val s = settings()
        return if (input.keyCode == KeyEvent.KEYCODE_BACK) {
            handleBack(input, s)
        } else {
            handleShortcut(input, s)
        }
    }

    private fun detectorFor(windowMs: Long): DoubleBackDetector {
        val existing = detector
        if (existing != null && detectorWindow == windowMs) return existing
        detectorWindow = windowMs
        return DoubleBackDetector(windowMs, now).also { detector = it }
    }

    private fun handleBack(i: KeyInput, s: HelperSettings): Decision {
        val enabled = s.doubleBackEnabled &&
            !ExclusionList.isExcluded(i.foreground, s.exclusions + ExclusionList.DEFAULTS)
        if (i.action == KeyAction.DOWN) {
            if (i.repeatCount == 0) {
                swallowBackUp = false // clear any stale swallow
                backTainted = false
                if (enabled && detectorFor(s.windowMs).isArmed()) {
                    swallowBackUp = true
                    return Decision.CONSUME
                }
                if (!enabled) detector?.reset()
                return Decision.PASS_THROUGH
            }
            if (swallowBackUp) return Decision.CONSUME
            backTainted = true
            return Decision.PASS_THROUGH
        }
        // UP
        if (swallowBackUp) {
            swallowBackUp = false
            detector?.reset()
            if (enabled && !i.canceled) goHome()
            return Decision.CONSUME
        }
        if (!enabled) {
            detector?.reset()
            return Decision.PASS_THROUGH
        }
        if (i.canceled) return Decision.PASS_THROUGH
        if (backTainted) {
            backTainted = false
            detector?.reset()
            return Decision.PASS_THROUGH
        }
        if (detectorFor(s.windowMs).onBackUp()) {
            // Cannot normally happen (DOWN would have been swallowed); do not
            // consume an UP whose DOWN the app already saw.
            goHome()
        }
        return Decision.PASS_THROUGH
    }

    /**
     * The Spotlight key ([HelperSettings.effectiveKey]). Single-press mode: DOWN and UP are
     * consumed and Spotlight opens on UP. Double-press mode (the launcher's key only): the first
     * press reaches the app as usual; a second DOWN within the window is consumed, and its UP
     * opens Spotlight. The launcher handles its own key on the home screen, so the launcher's
     * key passes through while the launcher is in front.
     */
    private fun handleShortcut(i: KeyInput, s: HelperSettings): Decision {
        val key = s.effectiveKey
        val pending = shortcutDownKey
        val eligible = key != 0 && i.keyCode == key && ShortcutKeys.isAllowed(key) &&
            i.foreground != HELPER_PACKAGE && !(s.launcherKey != 0 && i.foreground == LAUNCHER_PACKAGE)
        if (i.action == KeyAction.DOWN) {
            if (!eligible) {
                if (i.repeatCount == 0 && i.keyCode != key) clearTap()
                return Decision.PASS_THROUGH
            }
            if (!s.effectiveDouble) {
                if (i.repeatCount == 0) shortcutDownKey = key
                return Decision.CONSUME
            }
            if (pending == key) return Decision.CONSUME // repeats of the swallowed second press
            if (i.repeatCount > 0) {
                tapHeld = true
                return Decision.PASS_THROUGH
            }
            val t = now()
            if (lastTapAt >= 0 && t - lastTapAt <= s.launcherWindowMs) {
                clearTap()
                shortcutDownKey = key
                return Decision.CONSUME
            }
            clearTap()
            return Decision.PASS_THROUGH
        }
        if (pending != 0 && i.keyCode == pending) {
            shortcutDownKey = 0
            if (key == pending && !i.canceled) openSpotlight()
            return Decision.CONSUME
        }
        if (eligible && s.effectiveDouble) {
            // First press done: arm for a second one (not after a long press or a cancel).
            if (!i.canceled && !tapHeld) lastTapAt = now() else clearTap()
            tapHeld = false
        }
        return Decision.PASS_THROUGH
    }

    private fun clearTap() {
        lastTapAt = -1L
        tapHeld = false
    }

    companion object {
        const val HELPER_PACKAGE = "dev.tvlauncher.helper"
        const val LAUNCHER_PACKAGE = "dev.tvlauncher"
    }
}
