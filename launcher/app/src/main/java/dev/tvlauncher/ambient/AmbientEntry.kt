package dev.tvlauncher.ambient

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.device.EffectsMode

/**
 * Full-screen ambient. Keeps the screen on, hides system UI, plays nothing. Any key (including
 * BACK) or touch exits with a quick fade; the key itself is consumed so it does not leak to Home.
 */
class AmbientActivity : Activity() {
    private var view: AmbientView? = null
    private var exiting = false
    private var fadeMs = 200L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val profile = DeviceProfileReader.read(this)
        fadeMs = if (profile.effectsMode == EffectsMode.LITE) 120L else 200L
        val v = AmbientView(this, profile, WallpaperStore(this).load())
        view = v
        setContentView(v)
        hideSystemUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Suppress("DEPRECATION") // WindowInsetsController is API 30+; minSdk is 24.
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Let volume/mute keys work normally while ambient is showing.
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE ->
                return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) exit()
        return true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) exit()
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = exit()

    private fun exit() {
        if (exiting) return
        exiting = true
        val root = window.decorView
        root.animate().alpha(0f).setDuration(fadeMs).withEndAction {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }.start()
    }

    override fun onStop() {
        super.onStop()
        // Ambient is transient: leaving it (Home pressed, app launched) ends it.
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        view = null
        super.onDestroy()
    }

    companion object {
        const val ACTION = "dev.tvlauncher.action.AMBIENT"

        fun intent(context: Context): Intent =
            Intent(ACTION).setClass(context, AmbientActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)

        fun start(context: Context) {
            try { context.startActivity(intent(context)) } catch (e: Exception) { }
        }
    }
}

/**
 * System screensaver using the same [AmbientView]. Select it with:
 * adb shell settings put secure screensaver_components dev.tvlauncher/dev.tvlauncher.ambient.AmbientDream
 * adb shell settings put secure screensaver_enabled 1
 * adb shell settings put secure screensaver_activate_on_sleep 1
 */
class AmbientDream : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        setContentView(AmbientView(this, DeviceProfileReader.read(this), WallpaperStore(this).load()))
    }

    companion object {
        const val COMPONENT = "dev.tvlauncher/dev.tvlauncher.ambient.AmbientDream"
    }
}

/**
 * Handler wiring for [IdleTimer]: calls [onIdle] once after [timeoutMs] without user activity
 * while started (Home in the foreground) and not paused. The host forwards
 * onUserInteraction/dispatchKeyEvent to [onUserActivity] and toggles [setPaused] around
 * Spotlight, dialogs and panels.
 */
class AmbientIdle(timeoutMs: Long, private val onIdle: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val timer = IdleTimer(timeoutMs) { SystemClock.uptimeMillis() }.also { it.stop() }
    private var started = false
    private val check = Runnable { evaluate() }

    fun start() {
        started = true
        timer.start()
        reschedule()
    }

    fun stop() {
        started = false
        timer.stop()
        main.removeCallbacks(check)
    }

    fun onUserActivity() {
        if (!started) return
        timer.onActivity()
        reschedule()
    }

    fun setPaused(flag: Int, paused: Boolean) {
        timer.setPaused(flag, paused)
        reschedule()
    }

    fun setTimeoutMinutes(minutes: Int) {
        timer.setTimeout(minutes.coerceIn(WallpaperSettings.MIN_IDLE, WallpaperSettings.MAX_IDLE) * 60_000L)
        reschedule()
    }

    private fun reschedule() {
        main.removeCallbacks(check)
        if (!started) return
        val left = timer.remainingMs()
        if (left >= 0) main.postDelayed(check, left + 20)
    }

    private fun evaluate() {
        if (timer.consumeIdle()) onIdle() else reschedule()
    }
}
