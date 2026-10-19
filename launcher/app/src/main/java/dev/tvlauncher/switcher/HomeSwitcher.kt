package dev.tvlauncher.switcher

import android.app.Activity
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.setup.SetupStatus
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The home screen's Recent apps strip: opening it (a quick second Home press, the Spotlight
 * action, the controller's Y when chosen), loading its cards off the main thread, opening an app
 * through the home screen's own launch path, and closing apps. HomeActivity forwards keys with
 * [onKey] while [isOpen] and Home presses with [onHomePress].
 */
class HomeSwitcher(
    private val activity: Activity,
    root: FrameLayout,
    full: Boolean,
    /** The home screen's content: blocked for focus while the strip is open. */
    private val content: ViewGroup,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        /** Starts [app] the way a tile does (sounds, launch motion); true when it started. */
        fun launch(app: AppEntry, from: View): Boolean
        fun sound(ok: Boolean)
        fun moved()
        /** The strip opened (true) or closed: the idle timer pauses meanwhile. */
        fun shown(open: Boolean)
    }

    val store = SwitcherStore(activity.applicationContext)
    private val gesture = HomePressGesture()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "switcher").apply { isDaemon = true } }
    private val gen = AtomicInteger()
    private var restore: View? = null

    private val strip: RecentsStrip = RecentsStrip(activity, full, object : RecentsStrip.Host {
        override fun onOpen(app: AppEntry, from: View) {
            if (callbacks.launch(app, from)) close(animate = false)
        }

        override fun onCloseApp(card: RecentCard) {
            val app = activity.applicationContext
            val pkg = card.app.packageName
            worker.execute {
                val ok = SwitcherSystem.close(app, pkg)
                if (ok) store.markClosed(pkg)
                activity.runOnUiThread {
                    if (activity.isDestroyed) return@runOnUiThread
                    callbacks.sound(ok)
                    if (ok) { stripView.remove(pkg); stripView.setStatus(SwitcherText.closed(card.app.label)) }
                }
            }
        }

        override fun onCloseRefused(message: String) {
            callbacks.sound(false)
            stripView.setStatus(message)
        }

        override fun onOpenSetup() {
            close(animate = false)
            callbacks.sound(SetupStatus.openPermissions(activity))
        }

        override fun onLeave() {
            callbacks.sound(true)
            close()
        }

        override fun onMove() = callbacks.moved()
    })
    private val stripView: RecentsStrip get() = strip

    init {
        root.addView(strip, FrameLayout.LayoutParams(-1, -1))
    }

    val isOpen: Boolean get() = strip.isOpen

    /** Records a launch for the fallback list (no usage access) and the warm-start motion. */
    fun recordLaunch(pkg: String) {
        worker.execute { store.record(pkg) }
        lastLaunch.remove(pkg)
        lastLaunch[pkg] = System.currentTimeMillis()
        if (lastLaunch.size > 32) lastLaunch.keys.firstOrNull()?.let { lastLaunch.remove(it) }
    }

    private val lastLaunch = LinkedHashMap<String, Long>()

    fun lastLaunchOf(pkg: String): Long? = lastLaunch[pkg]

    /** A Home press reached the home screen. True when it opened the strip (the second of a quick pair). */
    fun onHomePress(): Boolean {
        if (!gesture.onHome(SystemClock.uptimeMillis(), store.doubleHome)) {
            close(animate = false)
            return false
        }
        open()
        return true
    }

    fun toggle() = if (isOpen) close() else open()

    fun open() {
        if (isOpen || activity.isDestroyed) return
        restore = activity.currentFocus
        content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        strip.open()
        strip.bringToFront()
        callbacks.shown(true)
        callbacks.sound(true)
        val g = gen.incrementAndGet()
        val app = activity.applicationContext
        worker.execute {
            val r = try { SwitcherSystem.load(app, store) } catch (e: Throwable) { RecentsResult(emptyList(), false) }
            activity.runOnUiThread {
                if (g != gen.get() || activity.isDestroyed || !strip.isOpen) return@runOnUiThread
                strip.show(r.cards, accessHint = !r.usageAccess)
            }
        }
    }

    fun close(animate: Boolean = true) {
        if (!isOpen) return
        gen.incrementAndGet()
        strip.close(animate)
        content.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        val back = restore?.takeIf { it.isAttachedToWindow && it.visibility == View.VISIBLE }?.requestFocus() ?: false
        restore = null
        callbacks.shown(false)
        if (!back) content.requestFocus()
    }

    fun onKey(e: KeyEvent): Boolean = strip.onKey(e)

    fun release() {
        gen.incrementAndGet()
        worker.shutdownNow()
    }
}
