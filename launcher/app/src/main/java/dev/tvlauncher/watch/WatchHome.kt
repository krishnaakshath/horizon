package dev.tvlauncher.watch

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.Toast
import dev.tvlauncher.ui.HeroBox
import dev.tvlauncher.ui.Motion
import dev.tvlauncher.ui.dp
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Continue watching on the home screen. While a shelf tile stays focused for
 * [DwellTimer.DEFAULT_MS], the hero area crossfades into a [ContinueRow] with that app's titles
 * (or every app's, Settings > Home > Continue watching). UP from the tile reaches the cards, OK
 * resumes the title in its app, a long press hides it, DOWN goes back to the tile.
 *
 * Reading happens on one background thread ([WatchReader]); a [ContentObserver] refreshes the
 * list while Home is started. Programs of hidden apps never show. Nothing leaves the TV.
 */
class WatchHome(
    private val activity: Activity,
    private val heroBox: HeroBox,
    private val lite: Boolean,
    private val iconFor: (String) -> Drawable?,
    private val accentFor: (String) -> Int,
    private val labelFor: (String) -> String?,
    /** DOWN on a card: focus the tile the row belongs to; false when there is none. */
    private val backToShelf: () -> Boolean,
    /** Shelf list: focus inside it is a tile. */
    private val shelf: View
) {
    private val main = Handler(Looper.getMainLooper())
    private val prefs = WatchPrefs(activity)
    private val reader = WatchReader(activity)
    private val thumbs = ThumbCache(activity, lite)
    private var exec: ExecutorService? = null
    private var settings = WatchSettings.DEFAULT
    private var raw: List<WatchProgram> = emptyList()
    /** Programs the row and Spotlight may show, best first. Main thread. */
    var programs: List<WatchProgram> = emptyList()
        private set
    var access: WatchAccess = WatchAccess.UNAVAILABLE
        private set
    private var hiddenPackages: Set<String> = emptySet()
    private var installed: Set<String>? = null
    private var started = false
    private var gen = 0
    private val dwell = DwellTimer()
    private var focusedPkg: String? = null
    private var rowPkg: String? = null
    private var row: ContinueRow? = null
    private var faded: List<View> = emptyList()
    private val max get() = if (lite) WatchModel.MAX_CARDS_LITE else WatchModel.MAX_CARDS
    /** Called when [programs] changed (Spotlight's Everything search uses them). */
    var onProgramsChanged: (() -> Unit)? = null

    private val dwellRunnable = Runnable { onDwell() }
    private val refreshRunnable = Runnable { refresh() }
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            main.removeCallbacks(refreshRunnable)
            main.postDelayed(refreshRunnable, OBSERVER_DEBOUNCE_MS)
        }
    }
    private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, now -> onGlobalFocus(now) }
    private val memory = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) { if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) thumbs.trim() }
        override fun onConfigurationChanged(newConfig: Configuration) {}
        @Deprecated("Deprecated in Java") override fun onLowMemory() = thumbs.trim()
    }

    init {
        heroBox.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
        activity.registerComponentCallbacks(memory)
    }

    /** Home started: re-read settings (edited in Settings), watch the provider, refresh. */
    fun start() {
        started = true
        settings = prefs.load()
        if (!settings.showRow) hide(animate = false)
        try {
            activity.contentResolver.registerContentObserver(WatchReader.WATCH_NEXT_URI, true, observer)
            activity.contentResolver.registerContentObserver(WatchReader.PREVIEW_URI, true, observer)
        } catch (e: RuntimeException) {
            // No provider to watch: the list is read on each start only.
        }
        refresh()
    }

    fun stop() {
        started = false
        main.removeCallbacks(refreshRunnable)
        main.removeCallbacks(dwellRunnable)
        dwell.clear()
        try { activity.contentResolver.unregisterContentObserver(observer) } catch (e: RuntimeException) { }
        hide(animate = false)
    }

    /** The installed launchable apps and the ones hidden from Home (their titles never show). */
    fun setApps(installedPackages: Set<String>, hidden: Set<String>) {
        val changed = installedPackages != installed || hidden != hiddenPackages
        installed = installedPackages
        hiddenPackages = hidden
        if (changed) recompute()
    }

    /** A shelf tile took focus ([pkg]), or a non-app tile (null). */
    fun onTileFocused(pkg: String?) {
        focusedPkg = pkg
        val now = SystemClock.uptimeMillis()
        main.removeCallbacks(dwellRunnable)
        if (pkg == null || !settings.showRow) { dwell.clear(); hide(animate = true); return }
        // All apps in one row: the same row for every tile, so it stays while moving along the shelf.
        if (row?.visibility == View.VISIBLE && (settings.scope == WatchScope.ALL_APPS || rowPkg == pkg)) { dwell.focus(pkg, now); return }
        hide(animate = true)
        dwell.focus(pkg, now)
        main.postDelayed(dwellRunnable, dwell.remaining(now).coerceAtLeast(0))
    }

    /** UP from a tile: into the row when it is shown. */
    fun focusRow(): Boolean = row?.takeIf { it.visibility == View.VISIBLE && it.alpha > 0f }?.focusFirst() ?: false

    fun destroy() {
        stop()
        gen++
        exec?.shutdownNow()
        exec = null
        try { heroBox.viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener) } catch (e: RuntimeException) { }
        try { activity.unregisterComponentCallbacks(memory) } catch (e: RuntimeException) { }
        thumbs.trim()
    }

    private fun background(): ExecutorService = exec ?: Executors.newSingleThreadExecutor().also { exec = it }

    private fun refresh() {
        if (!started || activity.isDestroyed) return
        val g = ++gen
        val preview = settings.includeSuggestions
        background().execute {
            val r = try { reader.read(preview) } catch (e: RuntimeException) { WatchReader.Result(emptyList(), WatchAccess.UNAVAILABLE) }
            main.post {
                if (g != gen || activity.isDestroyed) return@post
                raw = r.programs
                access = r.access
                recompute()
            }
        }
    }

    private fun recompute() {
        val inst = installed
        val list = WatchModel.clean(raw, hiddenPackages, settings.blocked.toHashSet(), settings.includeSuggestions)
            .filter { inst == null || it.packageName in inst }
        if (list == programs) return
        programs = list
        onProgramsChanged?.invoke()
        if (row?.visibility == View.VISIBLE) {
            val cards = cardsFor(rowPkg)
            if (cards.isEmpty()) hide(animate = true) else bindRow(cards, rowPkg)
        } else if (dwell.key != null && dwell.key == focusedPkg) {
            main.removeCallbacks(dwellRunnable)
            main.post(dwellRunnable)
        }
    }

    private fun cardsFor(pkg: String?): List<WatchProgram> =
        WatchModel.rowFor(programs, pkg, settings.scope == WatchScope.ALL_APPS, max)

    private fun onDwell() {
        val now = SystemClock.uptimeMillis()
        val r = dwell.remaining(now)
        if (r > 0) { main.postDelayed(dwellRunnable, r); return }
        val pkg = dwell.fire(now) ?: return
        if (!started || pkg != focusedPkg || !shelf.hasFocus()) return
        val cards = cardsFor(pkg)
        if (cards.isEmpty()) return
        show(cards, pkg)
    }

    private fun ensureRow(): ContinueRow = row ?: ContinueRow(activity).also { r ->
        r.visibility = View.GONE
        r.listener = object : ContinueRow.Listener {
            override fun onOpen(p: WatchProgram) {
                if (!WatchLauncher.launch(activity, p)) toast("Couldn't open ${labelFor(p.packageName) ?: "the app"}")
            }

            override fun onLongPress(p: WatchProgram) {
                if (!settings.longPressHides) return
                settings = prefs.update { it.block(p.key) }
                toast("Hidden from Continue watching")
                recompute()
                if (row?.visibility == View.VISIBLE && row?.hasCards() == true) row?.focusFirst() else backToShelf()
            }

            override fun onDown(): Boolean = backToShelf()
        }
        heroBox.addView(r, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        row = r
    }

    private fun bindRow(cards: List<WatchProgram>, pkg: String?) {
        val r = ensureRow()
        val title = if (settings.scope == WatchScope.ALL_APPS || pkg == null) "Continue watching"
        else labelFor(pkg)?.let { "Continue in $it" } ?: "Continue watching"
        val cardW = activity.dp(ContinueRow.CARD_W_DP)
        r.bind(title, cards, iconFor, accentFor) { p -> if (settings.showPosters) p.posterUri?.let { thumbs.peek(it) } else null }
        if (!settings.showPosters) return
        val g = gen
        for (p in cards) {
            val uri = p.posterUri ?: continue
            if (!ThumbRules.canOpen(uri) || thumbs.peek(uri) != null) continue
            background().execute {
                val b = thumbs.load(uri, cardW) ?: return@execute
                main.post { if (g == gen) row?.setThumb(uri, b) }
            }
        }
    }

    private fun show(cards: List<WatchProgram>, pkg: String) {
        // The hero area must hold the row (e.g. not the grid's spare strip, not an empty state).
        val needed = activity.dp(ContinueRow.HEIGHT_DP)
        if (heroBox.visibility != View.VISIBLE || heroBox.height < needed || !heroBox.hero.isShown) return
        val r = ensureRow()
        // Never under the Now Playing card: the row stops where the hero text stops.
        (r.layoutParams as FrameLayout.LayoutParams).let { lp ->
            val end = (heroBox.hero.layoutParams as ViewGroup.MarginLayoutParams).marginEnd
            if (lp.marginEnd != end) { lp.marginEnd = end; r.layoutParams = lp }
        }
        rowPkg = pkg
        bindRow(cards, pkg)
        val ms = Motion.ms(CROSSFADE_MS)
        faded = (0 until heroBox.childCount).map { heroBox.getChildAt(it) }
            .filter { it !== r && it.visibility == View.VISIBLE && it.alpha > 0f && it !== sideCard() }
        faded.forEach { it.animate().cancel(); it.animate().alpha(0f).setDuration(ms).start() }
        r.animate().cancel()
        r.alpha = 0f
        r.visibility = View.VISIBLE
        r.animate().alpha(1f).setDuration(ms).start()
    }

    /** The Now Playing card at the end of the hero row stays as it is. */
    private fun sideCard(): View? = (0 until heroBox.childCount).map { heroBox.getChildAt(it) }
        .firstOrNull { (it.layoutParams as? FrameLayout.LayoutParams)?.gravity?.and(Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.END }

    private fun hide(animate: Boolean) {
        val r = row ?: return
        if (r.visibility != View.VISIBLE) return
        rowPkg = null
        val ms = if (animate) Motion.ms(CROSSFADE_MS) else 0L
        faded.forEach { it.animate().cancel(); if (ms == 0L) it.alpha = 1f else it.animate().alpha(1f).setDuration(ms).start() }
        faded = emptyList()
        r.animate().cancel()
        if (ms == 0L) { r.visibility = View.GONE; r.alpha = 1f; return }
        r.animate().alpha(0f).setDuration(ms).withEndAction { if (rowPkg == null) r.visibility = View.GONE }.start()
    }

    /** Focus left both the shelf and the row (top bar, Spotlight, a panel): back to the hero text. */
    private fun onGlobalFocus(now: View?) {
        val r = row ?: return
        if (r.visibility != View.VISIBLE || now == null) return
        if (isInside(now, r) || isInside(now, shelf)) return
        main.removeCallbacks(dwellRunnable)
        dwell.clear()
        hide(animate = true)
    }

    private fun isInside(v: View, parent: View): Boolean {
        var p: Any? = v
        while (p is View) { if (p === parent) return true; p = p.parent }
        return false
    }

    private fun toast(msg: String) = try { Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show() } catch (e: RuntimeException) { }

    private companion object {
        const val CROSSFADE_MS = 220L
        const val OBSERVER_DEBOUNCE_MS = 800L
    }
}
