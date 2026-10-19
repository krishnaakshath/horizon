package dev.tvlauncher.homelayout

import android.app.Activity
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.home.HomeLook
import dev.tvlauncher.ui.HeroBox
import dev.tvlauncher.ui.HomeContent
import dev.tvlauncher.ui.Motion
import dev.tvlauncher.ui.Shelf
import dev.tvlauncher.ui.ShelfAdapter
import dev.tvlauncher.ui.TileDims
import dev.tvlauncher.ui.TileView
import dev.tvlauncher.ui.dp

/**
 * Applies Settings > Home layout to the home screen: the [LayoutPlan] for the screen in dp, the
 * list's layout manager ([Shelf.apply]), row heights ([HomeContent.applyPlan]), the hero (text,
 * subtitle, big art or none) and the D-pad rules ([ShelfNav]). [reload] in onStart re-reads the
 * choices; a change relayouts in place with a short fade, no activity rebuild.
 */
class HomeLayoutController(
    private val activity: Activity,
    private val look: HomeLook,
    private val adapter: ShelfAdapter,
    private val shelf: Shelf,
    private val content: HomeContent,
    private val heroBox: HeroBox
) {
    private val prefs = HomeLayoutPrefs(activity)
    var settings: HomeLayoutSettings = prefs.load()
        private set
    var plan: LayoutPlan = makePlan()
        private set
    /** Big hero art (built only for that style). */
    private var art: TileView? = null
    private var artDims: TileDims? = null
    private var artPackage: String? = null
    private var empty = false
    /** Grid paging: the adapter position at the top of the list now (-1 = unknown). */
    private var gridAnchor = -1

    private fun screen(): ScreenDp {
        val m = activity.resources.displayMetrics
        return ScreenDp.of(m.widthPixels, m.heightPixels, m.density)
    }

    private fun makePlan() = LayoutStyle.plan(settings, look.profile.tileScale, screen())

    /** First layout (onCreate, before any app is shown). */
    fun applyInitial() = apply(fade = false)

    /** onStart: re-reads the layout choices and the tile size; true when the home screen was relaid out. */
    fun reload(): Boolean {
        settings = prefs.load()
        val p = makePlan()
        if (p == plan) return false
        plan = p
        apply(fade = look.profile.animate)
        return true
    }

    private fun apply(fade: Boolean) {
        val resized = adapter.configure(plan)
        shelf.apply(plan, resized)
        content.applyPlan(plan)
        gridAnchor = -1
        applyHero()
        if (fade) {
            for (v in listOf<View>(shelf, heroBox)) {
                v.animate().cancel()
                v.alpha = 0f
                v.animate().alpha(1f).setDuration(Motion.ms(FADE_MS)).start()
            }
        }
    }

    /** The app list was resubmitted: the grid's page is found again on the next focus. */
    fun listChanged() {
        gridAnchor = -1
    }

    /** Call instead of [HeroBox.setEmpty]: the empty-state message wins over every style. */
    fun setEmpty(isEmpty: Boolean) {
        empty = isEmpty
        heroBox.setEmpty(isEmpty)
        applyHero()
    }

    private fun applyHero() {
        val hero = heroBox.hero
        hero.visibility = if (empty || plan.hero == HeroMode.NONE) View.GONE else View.VISIBLE
        hero.subtitle.visibility = if (plan.heroSubtitle) View.VISIBLE else View.GONE
        // A grid fills the screen under the top bar; its spare strip of hero area stays empty.
        heroBox.visibility = if (plan.kind == ListKind.GRID && !empty) View.INVISIBLE else View.VISIBLE
        val lp = hero.layoutParams as FrameLayout.LayoutParams
        if (plan.hero == HeroMode.ART && plan.artWidthDp > 0) {
            val dims = TileDims(plan.artWidthDp, plan.artHeightDp, plan.artHeightDp * 55 / 100)
            if (art == null || artDims != dims) {
                art?.let { heroBox.removeView(it) }
                art = TileView(activity, dims).apply {
                    showcase()
                    auditTag(AuditTags.LAYER)
                    heroBox.addView(this, FrameLayout.LayoutParams(activity.dp(dims.viewWidthDp), activity.dp(dims.viewHeightDp),
                        Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = -activity.dp(dev.tvlauncher.ui.TileSpec.PAD_DP) })
                }
                artDims = dims
                artPackage = null
            }
            art?.visibility = if (empty) View.GONE else View.VISIBLE
            val start = activity.dp(plan.artWidthDp + LayoutStyle.ART_GAP_DP)
            if (lp.marginStart != start) { lp.marginStart = start; hero.layoutParams = lp }
        } else {
            art?.let { heroBox.removeView(it) }
            art = null
            artDims = null
            artPackage = null
            if (lp.marginStart != 0) { lp.marginStart = 0; hero.layoutParams = lp }
        }
    }

    /** The focused app changed: the big hero shows its art. */
    fun onAppFocused(app: AppEntry, accent: Int) {
        val a = art ?: return
        if (artPackage == app.packageName) return
        artPackage = app.packageName
        a.bind(app, adapter.artCopy(app, activity.resources), look.profile, accent)
    }

    /** Whether the quick-strip may appear under the tile at adapter [position] (the bottom row only). */
    fun stripAllowed(position: Int): Boolean {
        if (!plan.quickStrip) return false
        val g = adapter.geometry
        return g.kind == ListKind.ROW || g.rowOf(position) == g.rows.size - 1
    }

    /** A remote or controller key on the tile at [position], as a [NavResult] (null = not a layout key). */
    fun navKey(position: Int, keyCode: Int): NavResult? {
        val key = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> NavKey.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> NavKey.RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> NavKey.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> NavKey.DOWN
            KeyEvent.KEYCODE_PAGE_UP -> NavKey.PAGE_PREV
            KeyEvent.KEYCODE_PAGE_DOWN -> NavKey.PAGE_NEXT
            else -> return null
        }
        return ShelfNav.move(adapter.geometry, position, key)
    }

    /** L1/R1 on a controller while a tile at [position] has focus. */
    fun pageKey(position: Int, forward: Boolean): NavResult =
        ShelfNav.move(adapter.geometry, position, if (forward) NavKey.PAGE_NEXT else NavKey.PAGE_PREV)

    /**
     * Grid paging before focusing [position]: flips the list to that page when it is not shown.
     * True when the list scrolled (the tile view appears after the next layout).
     */
    fun showPageOf(position: Int): Boolean {
        val anchor = ShelfNav.pageAnchor(adapter.geometry, position)
        if (anchor < 0 || anchor == gridAnchor) return false
        gridAnchor = anchor
        shelf.scrollToAnchor(anchor)
        return true
    }

    private companion object {
        const val FADE_MS = 200L
    }
}
