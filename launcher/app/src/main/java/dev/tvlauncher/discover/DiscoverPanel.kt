package dev.tvlauncher.discover

import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.ui.BrandColors
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.panel.PanelHeader
import dev.tvlauncher.panel.PanelHeaderView
import dev.tvlauncher.panel.PanelStyle
import dev.tvlauncher.panel.PanelStyle.panelText
import dev.tvlauncher.type.FontWeight
import java.util.Locale

/**
 * "Get more apps": a full-screen glass panel listing [PopularApps] the TV does not have yet, by
 * section ("Recommended for you", then categories), each a two-row grid that scrolls sideways.
 * Cards are icon-less brand-colour tiles (the launcher has no INTERNET, so no store icons).
 * OK runs the card's action through [StoreLauncher]; MENU hides a suggestion; BACK closes.
 * Focus is trapped inside while open and the scroll views follow focus.
 *
 * Host: `panel.show(rootFrame)`; route BACK to [onBack]; call [release] from onDestroy.
 */
class DiscoverPanel(
    private val context: Context,
    profile: DeviceProfile,
    private val store: DiscoverStore,
    /** Installed packages; defaults to a launch-intent check of each catalog package. */
    private val installedPackages: () -> Set<String> = { StoreLauncher.installedCatalogPackages(context) },
    /** Detected region before the user's override; defaults to the system locale. */
    private val detectedRegion: () -> String? = { RegionGuess.fromLocale(Locale.getDefault(), null) },
    private val onDismissed: () -> Unit = {}
) {
    private val lite = profile.effectsMode == EffectsMode.LITE
    private val density = context.resources.displayMetrics.density
    private var host: ViewGroup? = null
    private var root: FrameLayout? = null
    private var body: LinearLayout? = null
    private var settings = DiscoverSettings()
    private var lastFocusedId: String? = null
    /** Card ids in on-screen order (sections, then columns), for focusing a neighbour after a hide. */
    private var shownIds: List<String> = emptyList()

    val isShowing: Boolean get() = root != null

    private fun px(dp: Int) = (dp * density + 0.5f).toInt()

    fun show(host: ViewGroup) {
        if (root != null) return
        this.host = host
        val r = TrapLayout(context).apply {
            setBackgroundColor(if (lite) 0xE6000000.toInt() else 0xB3000000.toInt())
            isClickable = true
            auditTag(AuditTags.SCRIM)
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(PanelStyle.G3), px(PanelStyle.G2), px(PanelStyle.G3), px(PanelStyle.G2))
            background = PanelStyle.surface(context, lite)
        }
        panel.addView(PanelHeaderView(context).apply {
            bind(PanelHeader(R.drawable.ic_disc_bag, "Get more apps", "Popular on TV. Installs open the Play Store."))
            setPadding(0, 0, 0, 0)
        })
        val b = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            clipToPadding = false
            setPadding(0, px(8), 0, px(8))
            addView(b, ViewGroup.LayoutParams(-1, -2))
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = px(PanelStyle.G1) })
        panel.addView(TextView(context).apply {
            panelText(PanelStyle.DETAIL_SP, FontWeight.REGULAR, PanelStyle.TEXT_2)
            text = "OK opens the store page · MENU hides a suggestion · BACK closes"
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(PanelStyle.G1) })
        r.addView(panel, FrameLayout.LayoutParams(-1, -1).apply {
            setMargins(px(Theme.MARGIN_H), px(Theme.MARGIN_V), px(Theme.MARGIN_H), px(Theme.MARGIN_V))
        })
        host.addView(r, ViewGroup.LayoutParams(-1, -1))
        root = r
        body = b
        val ms = PanelStyle.openMs(context, lite)
        if (ms > 0) {
            panel.alpha = 0f; panel.translationY = px(PanelStyle.G1).toFloat()
            panel.animate().alpha(1f).translationY(0f).setDuration(ms).setInterpolator(DecelerateInterpolator(2f)).start()
        }
        rebuild()
    }

    fun dismiss() {
        val r = root ?: return
        root = null
        r.animate().cancel()
        host?.removeView(r)
        host = null
        body = null
        onDismissed()
    }

    /** True when the panel consumed BACK. */
    fun onBack(): Boolean {
        if (root == null) return false
        dismiss()
        return true
    }

    /** Releases view references (call from the host's onDestroy). */
    fun release() {
        dismiss()
        lastFocusedId = null
    }

    /** Re-reads installed apps and settings (e.g. from the host's onResume after an install). */
    fun refresh() {
        if (root != null) rebuild()
    }

    // --- content ----------------------------------------------------------------------------------

    private fun rebuild() {
        val b = body ?: return
        settings = store.load()
        val region = settings.region ?: detectedRegion()
        val installed = try { installedPackages() } catch (e: Exception) { emptySet() }
        val state = DiscoverModel.build(installed, region, settings.hiddenCatalogIds, StoreLauncher.isGetAppsInstalled(context))
        b.removeAllViews()
        shownIds = state.sections.flatMap { s -> s.items.map { it.app.id } }
        if (state.isEmpty) {
            b.addView(emptyState(state), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(48) })
            return
        }
        var first: View? = null
        var restore: View? = null
        state.sections.forEach { sec ->
            b.addView(TextView(context).apply {
                panelText(PanelStyle.ROW_SP - 1f, FontWeight.SEMIBOLD, PanelStyle.TEXT_2)
                text = sec.title
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(if (b.childCount == 0) 0 else PanelStyle.G3); bottomMargin = px(PanelStyle.G1) })
            val grid = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
                setPadding(px(8), px(8), px(8), px(8))
            }
            var column: LinearLayout? = null
            sec.items.forEachIndexed { i, item ->
                val (_, row) = DiscoverGrid.cell(i)
                if (row == 0) {
                    column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
                    grid.addView(column, LinearLayout.LayoutParams(-2, -2).apply { if (grid.childCount > 0) leftMargin = px(DiscoverGrid.GAP_DP) })
                }
                val card = DiscoverCard(item)
                column!!.addView(card, LinearLayout.LayoutParams(px(DiscoverGrid.CARD_W_DP), px(DiscoverGrid.CARD_H_DP)).apply {
                    if (row > 0) topMargin = px(DiscoverGrid.GAP_DP)
                })
                if (first == null) first = card
                if (item.app.id == lastFocusedId) restore = card
            }
            b.addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                clipChildren = false
                clipToPadding = false
                addView(grid)
            }, LinearLayout.LayoutParams(-1, -2))
        }
        val target = restore ?: first
        target?.post { target.requestFocus() }
    }

    private fun emptyState(state: DiscoverState): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(ImageView(context).apply { setImageResource(R.drawable.ic_disc_bag); alpha = 0.6f }, LinearLayout.LayoutParams(px(48), px(48)))
        addView(TextView(context).apply {
            panelText(PanelStyle.TITLE_SP - 2f, FontWeight.SEMIBOLD, singleLine = false)
            gravity = Gravity.CENTER
            text = "You have everything popular here"
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(PanelStyle.G2) })
        addView(TextView(context).apply {
            panelText(PanelStyle.MESSAGE_SP, FontWeight.REGULAR, PanelStyle.TEXT_2, singleLine = false)
            gravity = Gravity.CENTER
            text = if (settings.hiddenCatalogIds.isNotEmpty()) {
                "${settings.hiddenCatalogIds.size} hidden suggestion(s) can be shown again in Settings."
            } else {
                "${state.installed.size} popular apps are already on this TV."
            }
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(6) })
        // Something must hold focus so BACK and the focus trap keep working.
        isFocusable = true
        post { requestFocus() }
    }

    private fun hide(item: DiscoverItem) {
        val next = DiscoverSettingsRows.hide(store.load(), item.app.id)
        store.save(next)
        lastFocusedId = DiscoverGrid.neighbourAfterRemoval(shownIds, item.app.id)
        rebuild()
    }

    /** Big brand-colour card: initials, name, one-line category and the single action label. */
    private inner class DiscoverCard(private val item: DiscoverItem) : FrameLayout(context) {
        private val color = BrandColors.backdropAccent(item.app.brandColor)
        private val light = BrandColors.onColorTextIsLight(color)
        private val fg = if (light) Theme.TEXT else Theme.TEXT_DARK
        private val fg2 = if (light) 0xCCFFFFFF.toInt() else Theme.TEXT_DARK_2
        // The resting dim is baked into the colours (no view alpha, so no offscreen layer).
        private val restBg = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(BrandColors.blend(color, Theme.BASE, 0.12f), BrandColors.blend(color, Theme.BASE, 0.42f)))
            .apply { cornerRadius = px(18).toFloat() }
        private val focusBg = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(color, BrandColors.blend(color, Theme.BASE, 0.35f))).apply { cornerRadius = px(18).toFloat() }
        private val card = LinearLayout(context)
        private val ring = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = px(22).toFloat()
                setStroke(px(2), Theme.TEXT)
            }
            alpha = 0f
            auditTag(AuditTags.IGNORE) // focus decoration
        }

        init {
            isFocusable = true
            isClickable = true
            clipChildren = false
            auditTag(AuditTags.LAYER)
            contentDescription = "${item.app.label}, ${DiscoverModel.subtitle(item.app)}, ${DiscoverActions.label(item.action)}"
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(px(16), px(14), px(16), px(12))
            card.background = restBg
            card.addView(TextView(context).apply {
                panelText(30f, FontWeight.SEMIBOLD, fg)
                text = DiscoverGrid.initials(item.app.label)
            }, LinearLayout.LayoutParams(-2, 0, 1f))
            card.addView(TextView(context).apply {
                panelText(PanelStyle.ROW_SP, FontWeight.SEMIBOLD, fg)
                text = item.app.label
            })
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            line.addView(TextView(context).apply {
                panelText(PanelStyle.DETAIL_SP, FontWeight.REGULAR, fg2)
                text = DiscoverModel.subtitle(item.app)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(TextView(context).apply {
                panelText(PanelStyle.DETAIL_SP, FontWeight.SEMIBOLD, if (light) Theme.TEXT_DARK else Theme.TEXT)
                text = DiscoverActions.label(item.action)
                setPadding(px(10), px(3), px(10), px(3))
                background = GradientDrawable().apply {
                    cornerRadius = px(Theme.RADIUS_PILL).toFloat()
                    setColor(if (light) 0xE6FFFFFF.toInt() else 0xE60B0D12.toInt())
                }
            }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = px(8) })
            card.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(4) })
            addView(card, LayoutParams(-1, -1))
            val ro = px(4)
            addView(ring, LayoutParams(-1, -1).apply { setMargins(-ro, -ro, -ro, -ro) })
            setOnClickListener { StoreLauncher.performWithToast(context, item) }
        }

        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            if (gainFocus) lastFocusedId = item.app.id
            val ms = if (lite) 0L else PanelStyle.openMs(context, lite = true)
            val s = if (gainFocus) 1.06f else 1f
            animate().cancel()
            card.background = if (gainFocus) focusBg else restBg
            animate().scaleX(s).scaleY(s).setDuration(ms).start()
            ring.animate().cancel()
            ring.animate().alpha(if (gainFocus) 1f else 0f).setDuration(ms).start()
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            if (keyCode == KeyEvent.KEYCODE_MENU && item.action != DiscoverActions.Action.OPEN) {
                hide(item)
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        override fun onDetachedFromWindow() {
            animate().cancel()
            ring.animate().cancel()
            super.onDetachedFromWindow()
        }
    }

    /** Keeps D-pad focus inside the panel and routes BACK to [onBack]. */
    private inner class TrapLayout(context: Context) : FrameLayout(context) {
        override fun focusSearch(focused: View?, direction: Int): View? {
            val next = super.focusSearch(focused, direction)
            return if (next != null && isDescendant(next)) next else focused
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        private fun isDescendant(v: View): Boolean {
            var p: Any? = v
            while (p is View) {
                if (p === this) return true
                p = p.parent
            }
            return false
        }
    }
}
