package dev.tvlauncher.input

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Game controllers for one Activity, shared by every screen (one field and two one-line
 * overrides per Activity):
 *
 * ```
 * private val pad = GamepadInput(this)
 * override fun dispatchKeyEvent(event: KeyEvent) = pad.dispatch(event) { super.dispatchKeyEvent(it) }
 * override fun dispatchGenericMotionEvent(ev: MotionEvent) = pad.motion(ev) || super.dispatchGenericMotionEvent(ev)
 * ```
 *
 * Buttons go through [GamepadMapper] (A = OK, B = BACK, commands for the rest); the hat and the
 * left stick become D-pad presses with key repeat ([StickNavigator]), focus moving exactly as a
 * remote's arrow would; the right stick scrolls; analog triggers act as L2/R2. Remote and
 * keyboard input is never changed. While a text field has focus, A/B and the sticks are left to
 * the system so the on-screen keyboard keeps working with the controller.
 *
 * Screens customise commands through [Host]; whatever a host does not handle gets the default
 * (Spotlight, Settings, Home, page jumps, the focused item's long-press). Everything is stopped
 * when the Activity pauses.
 */
class GamepadInput(private val activity: Activity, private val host: Host = object : Host {}) {
    interface Host {
        /** Runs [command]; false = use the default. [repeat] > 0 while L1/R1/L2/R2 is held. */
        fun onPadCommand(command: PadCommand, repeat: Int): Boolean = false

        /** Analog stick ([right] or left); true = the screen used it (e.g. the browser pointer). */
        fun onPadStick(right: Boolean, x: Float, y: Float): Boolean = false
    }

    // Lazy: an Activity builds its fields before it has a Context.
    private val settings by lazy { GamepadSettingsSource.get(activity) }
    private val mapper = GamepadMapper({ settings.current() })
    private val main = Handler(Looper.getMainLooper())
    private val left = StickNavigator { settings.current().stick }
    private val hat = StickNavigator { HAT_TUNING }
    private val lt = AxisButton()
    private val rt = AxisButton()
    private val downAt = HashMap<Int, Long>()
    private val centred = HashSet<Long>()
    private var navDevice = -1
    private var injecting = false
    private var lastPadAt = 0L
    private var lastPadDevice = -1
    private var lastJumpAt = 0L
    private var lastBuzzAt = 0L
    private var focusHooked = false
    private val density by lazy { activity.resources.displayMetrics.density }
    private var hooked = false

    private val tick = Runnable { onTick() }

    private fun ensureHooked() {
        if (hooked) return
        hooked = true
        hookLifecycle()
        settings.refresh() // companion apps: the launcher's choices (defaults until read)
    }

    // ------------------------------------------------------------------ keys

    /**
     * Runs [event] through the controller rules and hands the result to [deliver] (the Activity's
     * own handling, ending in super.dispatchKeyEvent). A swallowed or command key is consumed.
     */
    fun dispatch(event: KeyEvent, deliver: (KeyEvent) -> Boolean): Boolean {
        val e = key(event) ?: return true
        val handled = deliver(e)
        return handled || e !== event
    }

    /** The event to deliver (the same, or translated), or null when consumed. */
    fun key(event: KeyEvent): KeyEvent? {
        if (injecting || event.action == KeyEvent.ACTION_MULTIPLE) return event
        val fromPad = PadSource.isPad(event.source)
        if (!fromPad && !GamepadMapper.isButton(event.keyCode)) return event // the remote: untouched
        ensureHooked()
        val down = event.action == KeyEvent.ACTION_DOWN
        PadCapture.listener?.let { l ->
            l.onPadKey(event.keyCode, down, event.deviceId, nameOf(event.deviceId))
            return null
        }
        notePad(event.deviceId)
        val r = mapper.onKey(event.keyCode, down, event.repeatCount, event.source, event.deviceId, textEditing())
        return when (r) {
            PadKeyResult.Pass -> event
            PadKeyResult.Swallow -> null
            is PadKeyResult.Translate -> KeyEvent(
                event.downTime, event.eventTime, event.action, r.keyCode, event.repeatCount,
                event.metaState, event.deviceId, event.scanCode, event.flags, event.source
            )
            is PadKeyResult.Command -> {
                if (down) runCommand(r.command, r.repeat)
                null
            }
        }
    }

    // ------------------------------------------------------------------ motion

    /** Controller sticks, hat and triggers; true when consumed. */
    fun motion(ev: MotionEvent): Boolean {
        if (injecting || !PadSource.isPad(ev.source) || ev.actionMasked != MotionEvent.ACTION_MOVE) return false
        ensureHooked()
        val dev = ev.deviceId
        val a = readAxes(ev)
        PadCapture.listener?.let { it.onPadAxes(a, nameOf(dev)); return true }
        if (textEditing()) { releaseAll(); return false } // the system turns it into D-pad keys for the keyboard
        val now = SystemClock.uptimeMillis()
        val dz = StickTuning.DEADZONE
        val leftLive = live(dev, 0, a.lx, a.ly)
        val rightLive = live(dev, 1, a.rx, a.ry)
        var acted = false

        val hatActive = a.hatX != 0f || a.hatY != 0f
        if (mapper.arbiter.useAxes(dev, InputArbiter.Channel.DPAD, hatActive)) {
            acted = acted or hatActive
            inject(hat.update(a.hatX, a.hatY, now), dev)
        }
        val lx = if (leftLive) a.lx else 0f
        val ly = if (leftLive) a.ly else 0f
        if (host.onPadStick(false, lx, ly)) {
            inject(left.reset(), dev)
        } else {
            inject(left.update(lx, ly, now), dev)
        }
        acted = acted or (abs(lx) >= dz || abs(ly) >= dz)
        val rx = if (rightLive) a.rx else 0f
        val ry = if (rightLive) a.ry else 0f
        if (!host.onPadStick(true, rx, ry)) setScroll(ry)
        acted = acted or (abs(rx) >= dz || abs(ry) >= dz)

        val trig = a.lt > AxisButton.RELEASE || a.rt > AxisButton.RELEASE
        if (mapper.arbiter.useAxes(dev, InputArbiter.Channel.TRIGGER, trig)) {
            lt.update(a.lt, now)?.let { runCommand(PadCommand.SCROLL_UP, it) }
            rt.update(a.rt, now)?.let { runCommand(PadCommand.SCROLL_DOWN, it) }
            acted = acted or trig
        }
        if (acted) {
            notePad(dev)
            activity.onUserInteraction()
        }
        schedule()
        return true
    }

    /**
     * A stick only counts once it has been seen at rest: some pads report triggers on the
     * right-stick axes, resting at -1, which would otherwise scroll forever.
     */
    private fun live(dev: Int, stick: Int, x: Float, y: Float): Boolean {
        val k = (dev.toLong() shl 1) or stick.toLong()
        if (k in centred) return true
        if (abs(x) < StickTuning.DEADZONE && abs(y) < StickTuning.DEADZONE) { centred += k; return true }
        return false
    }

    /** A deliberate controller push in [ev] (ambient wakes on it; drift does not). */
    fun isDeliberate(ev: MotionEvent): Boolean =
        PadSource.isPad(ev.source) && ev.actionMasked == MotionEvent.ACTION_MOVE && PadWake.deliberate(readAxes(ev))

    private fun readAxes(ev: MotionEvent): PadAxes {
        val d = ev.device
        fun has(axis: Int) = d?.getMotionRange(axis, ev.source) != null
        val useRxRy = d != null && !has(MotionEvent.AXIS_Z) && has(MotionEvent.AXIS_RX)
        val rx = ev.getAxisValue(if (useRxRy) MotionEvent.AXIS_RX else MotionEvent.AXIS_Z)
        val ry = ev.getAxisValue(if (useRxRy) MotionEvent.AXIS_RY else MotionEvent.AXIS_RZ)
        val lt = maxOf(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = maxOf(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS))
        return PadAxes(
            ev.getAxisValue(MotionEvent.AXIS_X), ev.getAxisValue(MotionEvent.AXIS_Y), rx, ry,
            lt, rt, ev.getAxisValue(MotionEvent.AXIS_HAT_X), ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
        )
    }

    // ------------------------------------------------------------------ synthetic D-pad

    private fun inject(steps: List<NavStep>, dev: Int) {
        for (s in steps) {
            val code = GamepadMapper.keyCodeOf(s.dir)
            val now = SystemClock.uptimeMillis()
            if (s.kind == NavStep.Kind.DOWN) { downAt[code] = now; navDevice = dev }
            val down = downAt[code] ?: now
            val up = s.kind == NavStep.Kind.UP
            if (up) downAt.remove(code)
            val ev = KeyEvent(
                down, now, if (up) KeyEvent.ACTION_UP else KeyEvent.ACTION_DOWN, code,
                if (up) 0 else s.repeat, 0, dev, 0, 0, InputDevice.SOURCE_DPAD
            )
            val handled = deliver(ev)
            if (!handled && !up) navigate(dirOf(code), s.repeat > 0)
        }
    }

    private fun deliver(ev: KeyEvent): Boolean {
        injecting = true
        return try { activity.dispatchKeyEvent(ev) } catch (e: RuntimeException) { true } finally { injecting = false }
    }

    /** What the system does with an unhandled arrow key: move focus the same way. */
    private fun navigate(direction: Int, fast: Boolean): Boolean {
        val root = activity.window?.decorView as? ViewGroup ?: return false
        val focused = root.findFocus()
        if (focused == null) {
            return if (Build.VERSION.SDK_INT >= 26) root.restoreDefaultFocus() else root.requestFocus()
        }
        val v = focused.focusSearch(direction)
        if (v != null && v !== focused) {
            val r = Rect()
            try {
                focused.getFocusedRect(r)
                root.offsetDescendantRectToMyCoords(focused, r)
                root.offsetRectIntoDescendantCoords(v, r)
            } catch (e: IllegalArgumentException) {
                r.setEmpty()
            }
            if (v.requestFocus(direction, if (r.isEmpty) null else r)) {
                if (!fast) root.playSoundEffect(SoundEffectConstants.getContantForFocusDirection(direction))
                return true
            }
        }
        return root.dispatchUnhandledMove(focused, direction)
    }

    private fun dirOf(code: Int): Int = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
        else -> View.FOCUS_RIGHT
    }

    private fun onTick() {
        val now = SystemClock.uptimeMillis()
        inject(hat.tick(now), navDevice)
        inject(left.tick(now), navDevice)
        lt.tick(now)?.let { runCommand(PadCommand.SCROLL_UP, it) }
        rt.tick(now)?.let { runCommand(PadCommand.SCROLL_DOWN, it) }
        schedule()
    }

    private fun schedule() {
        main.removeCallbacks(tick)
        val due = listOfNotNull(hat.nextDueAt(), left.nextDueAt(), lt.nextDueAt(), rt.nextDueAt()).minOrNull() ?: return
        main.postAtTime(tick, due)
    }

    // ------------------------------------------------------------------ right-stick scroll

    private var scrollVelocity = 0f
    private var scrollTarget: View? = null
    private var scrollLast = 0L
    private var scrollCarry = 0f
    private val scrollFrame = object : Runnable {
        override fun run() {
            val v = scrollVelocity
            if (v == 0f) return
            val now = SystemClock.uptimeMillis()
            val dt = (now - scrollLast).coerceIn(0L, 50L)
            scrollLast = now
            val dir = if (v > 0) 1 else -1
            val t = scrollTarget?.takeIf { it.isAttachedToWindow && it.canScrollVertically(dir) }
                ?: FocusJumper.scrollable(activity, dir).also { scrollTarget = it }
            if (t != null) {
                scrollCarry += v * dt / 1000f
                val dy = scrollCarry.roundToInt()
                if (dy != 0) { scrollCarry -= dy; t.scrollBy(0, dy) }
            }
            main.postDelayed(this, FRAME_MS)
        }
    }

    private fun setScroll(ry: Float) {
        val v = AnalogScroll.velocity(ry) * density
        val was = scrollVelocity
        scrollVelocity = v
        if (v == 0f) { main.removeCallbacks(scrollFrame); scrollTarget = null; scrollCarry = 0f; return }
        if (was == 0f) { scrollLast = SystemClock.uptimeMillis(); main.post(scrollFrame) }
    }

    // ------------------------------------------------------------------ commands

    private fun runCommand(c: PadCommand, repeat: Int) {
        val jump = c == PadCommand.PAGE_PREV || c == PadCommand.PAGE_NEXT || c == PadCommand.SCROLL_UP || c == PadCommand.SCROLL_DOWN
        val now = SystemClock.uptimeMillis()
        if (repeat > 0 && (!jump || !FocusJump.repeatAllowed(lastJumpAt, now))) return
        if (jump) lastJumpAt = now
        try {
            if (host.onPadCommand(c, repeat)) return
            when (c) {
                PadCommand.OPTIONS -> activity.currentFocus?.takeIf { it.isLongClickable && it.isEnabled }?.performLongClick()
                PadCommand.SEARCH, PadCommand.START -> PadIntents.spotlight(activity)
                PadCommand.SELECT -> PadIntents.settings(activity)
                PadCommand.HOME -> PadIntents.home(activity)
                PadCommand.PAGE_PREV -> FocusJumper.page(activity, forward = false)
                PadCommand.PAGE_NEXT -> FocusJumper.page(activity, forward = true)
                PadCommand.SCROLL_UP -> FocusJumper.scroll(activity, forward = false)
                PadCommand.SCROLL_DOWN -> FocusJumper.scroll(activity, forward = true)
            }
        } catch (e: RuntimeException) {
            // A command never takes the screen down.
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun textEditing(): Boolean = activity.currentFocus?.onCheckIsTextEditor() == true

    private fun nameOf(id: Int): String? = try { InputDevice.getDevice(id)?.name } catch (e: RuntimeException) { null }

    private fun notePad(dev: Int) {
        lastPadAt = SystemClock.uptimeMillis()
        lastPadDevice = dev
        if (!focusHooked) hookFocus()
    }

    /** "Vibration feedback": one light pulse per focus move made with a controller. */
    private fun hookFocus() {
        focusHooked = true
        val root = activity.window?.decorView ?: return
        root.viewTreeObserver.addOnGlobalFocusChangeListener(ViewTreeObserver.OnGlobalFocusChangeListener { old, now ->
            if (old == null || now == null) return@OnGlobalFocusChangeListener
            val t = SystemClock.uptimeMillis()
            if (t - lastPadAt > BUZZ_WINDOW_MS || t - lastBuzzAt < BUZZ_GAP_MS) return@OnGlobalFocusChangeListener
            if (!settings.current().vibrate) return@OnGlobalFocusChangeListener
            lastBuzzAt = t
            PadVibration.pulse(lastPadDevice)
        })
    }

    /** Lets go of everything held (the Activity paused or lost the controller). */
    fun releaseAll() {
        main.removeCallbacks(tick)
        main.removeCallbacks(scrollFrame)
        scrollVelocity = 0f
        scrollTarget = null
        val ups = hat.reset() + left.reset()
        lt.reset(); rt.reset()
        if (activity.hasWindowFocus()) inject(ups, navDevice) else downAt.clear()
    }

    private fun hookLifecycle() {
        val app = activity.application ?: return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { if (a === activity) settings.refresh() }
            override fun onActivityPaused(a: Activity) { if (a === activity) releaseAll() }
            override fun onActivityDestroyed(a: Activity) {
                if (a !== activity) return
                releaseAll()
                main.removeCallbacksAndMessages(null)
                app.unregisterActivityLifecycleCallbacks(this)
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        })
    }

    private companion object {
        /** The hat is digital (-1, 0, 1): any non-zero value presses. */
        val HAT_TUNING = StickTuning(0.35f, 0.5f, 0.4f)
        const val FRAME_MS = 16L
        const val BUZZ_WINDOW_MS = 1200L
        const val BUZZ_GAP_MS = 45L
    }
}

/** The launcher screens a controller button opens from anywhere. */
object PadIntents {
    const val ACTION_SPOTLIGHT = "dev.tvlauncher.action.SPOTLIGHT"
    const val ACTION_SETTINGS = "dev.tvlauncher.action.SETTINGS"

    fun spotlight(a: Activity): Boolean = start(
        a, Intent(ACTION_SPOTLIGHT).setPackage(GamepadSettingsSource.LAUNCHER_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    )

    /** The launcher's own Settings; only reachable from inside the launcher (it is not exported). */
    fun settings(a: Activity): Boolean {
        if (a.packageName != GamepadSettingsSource.LAUNCHER_PACKAGE) return false
        return start(a, Intent(ACTION_SETTINGS).setPackage(a.packageName))
    }

    fun home(a: Activity): Boolean = start(
        a, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    private fun start(a: Activity, i: Intent): Boolean = try {
        a.startActivity(i); true
    } catch (e: RuntimeException) {
        false
    }
}

/** A subtle buzz on the controller that made the move, if it has a motor. */
object PadVibration {
    private const val MS = 14L
    private const val AMPLITUDE = 50

    fun canVibrate(deviceId: Int): Boolean = try {
        InputDevice.getDevice(deviceId)?.vibrator?.hasVibrator() == true
    } catch (t: Throwable) {
        false
    }

    @Suppress("DEPRECATION")
    fun pulse(deviceId: Int) {
        try {
            val v = InputDevice.getDevice(deviceId)?.vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                val amp = if (v.hasAmplitudeControl()) AMPLITUDE else android.os.VibrationEffect.DEFAULT_AMPLITUDE
                v.vibrate(android.os.VibrationEffect.createOneShot(MS, amp))
            } else {
                v.vibrate(MS)
            }
        } catch (t: Throwable) {
            // No motor, no permission, or the pad just went away.
        }
    }
}
