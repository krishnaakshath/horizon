package dev.tvlauncher.ambient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.R
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.panel.PanelHeader
import dev.tvlauncher.panel.PanelHeaderView
import dev.tvlauncher.panel.PanelList
import dev.tvlauncher.panel.PanelRowView
import dev.tvlauncher.panel.PanelMotion
import dev.tvlauncher.panel.PanelStyle
import dev.tvlauncher.panel.PanelStyle.panelText
import dev.tvlauncher.type.FontWeight
import dev.tvlauncher.ui.Motion
import java.io.File
import kotlin.math.min

/**
 * Glass wallpaper picker (D-pad first). Thumbnail grid of the six built-ins and the user's
 * pictures (rendered at most 320x180, off the main thread), a ring on the current choice,
 * a Slideshow toggle with an interval stepper, a dim stepper and the honest "how to add
 * pictures" text. Focus is trapped inside while open; [onBack] closes it.
 */
class WallpaperPickerPanel(
    private val context: Context,
    profile: DeviceProfile,
    private val store: WallpaperStore,
    private val onChange: (WallpaperSettings) -> Unit
) {
    private val lite = profile.effectsMode == EffectsMode.LITE
    private val density = context.resources.displayMetrics.density
    private var host: ViewGroup? = null
    private var root: FrameLayout? = null
    private var settings = store.load()
    private var folder: File? = null
    private var userFiles: List<File> = emptyList()
    private val thumbs = ArrayList<Bitmap>()
    private val jobs = ArrayList<WallpaperLoader.Job>()
    private val tiles = ArrayList<Pair<WallpaperSource, View>>()
    private lateinit var grid: LinearLayout
    private lateinit var controls: PanelList
    private var listed = false
    private var accessNote: String? = null

    val isShowing: Boolean get() = root != null

    private fun px(dp: Int) = (dp * density + 0.5f).toInt()

    fun show(host: ViewGroup) {
        if (root != null) return
        this.host = host
        settings = store.load()
        val r = TrapLayout(context)
        r.setBackgroundColor(PanelStyle.SCRIM)
        r.auditTag(AuditTags.SCRIM)
        r.isClickable = true
        listed = false
        accessNote = null
        val panel = buildPanel()
        val w = min(px(760), (host.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels) - px(96))
        r.addView(panel, FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        host.addView(r, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root = r
        val ms = PanelStyle.openMs(context, lite)
        if (ms > 0) {
            panel.alpha = 0f; panel.translationY = px(PanelStyle.G1).toFloat()
            panel.animate().alpha(1f).translationY(0f).setDuration(ms).setInterpolator(DecelerateInterpolator(2f)).start()
        }
        addBuiltInTiles()
        folder = UserWallpapers.folder(context)
        val f = folder
        WallpaperLoader.background({ UserWallpapers.list(f) }) { files ->
            if (root !== r) return@background
            userFiles = files
            listed = true
            addUserTiles()
            refreshControls()
        }
        refreshControls()
        grid.post { tiles.firstOrNull { it.first == settings.source }?.second?.requestFocus() ?: tiles.firstOrNull()?.second?.requestFocus() }
    }

    fun dismiss() {
        val r = root ?: return
        root = null
        jobs.forEach { it.cancel() }; jobs.clear()
        // Detach every thumbnail from its ImageView before the bitmaps are (deferred-)recycled.
        tiles.forEach { (_, v) -> ((v as ViewGroup).getChildAt(0) as ImageView).setImageDrawable(null) }
        host?.removeView(r)
        host = null
        tiles.clear()
        thumbs.forEach { BitmapRecycler.defer(it) }; thumbs.clear()
    }

    /** True when the panel consumed BACK. */
    fun onBack(): Boolean {
        if (root == null) return false
        dismiss()
        return true
    }

    // --- building ---------------------------------------------------------------------------------

    private fun buildPanel(): View {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(PanelStyle.G1), px(PanelStyle.G2), px(PanelStyle.G1), px(PanelStyle.G1))
            background = PanelStyle.surface(context, lite)
        }
        panel.addView(PanelHeaderView(context).apply {
            bind(PanelHeader(R.drawable.ic_qa_ambient, "Wallpaper", "For the ambient screen and screensaver"))
        })
        grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(px(PanelStyle.G1), 0, px(PanelStyle.G1), 0)
            clipToPadding = false
            addView(grid)
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, min(px(220), (context.resources.displayMetrics.heightPixels * 0.4f).toInt())).apply { topMargin = px(PanelStyle.G1) })
        controls = PanelList(context).apply {
            onRowClick = { row -> onControl(row.key, +1) }
            onRowKey = { row, code ->
                if (row.key in WallpaperPanelModel.STEPPERS) {
                    onControl(row.key, if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else +1); true
                } else false
            }
        }
        panel.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(PanelStyle.G1) })
        return panel
    }

    private fun onControl(key: String, delta: Int) {
        when (key) {
            WallpaperPanelModel.SLIDESHOW -> toggleSlideshow()
            WallpaperPanelModel.INTERVAL -> stepInterval(delta)
            WallpaperPanelModel.DIM -> stepDim(delta)
            WallpaperPanelModel.NOTIFICATIONS, WallpaperPanelModel.TEXT -> commit(AmbientSettingsRows.toggle(settings, key))
            WallpaperPanelModel.ACCESS -> {
                accessNote = NotificationAccess.instructions(NotificationAccess.openSettings(context))
                refreshControls()
            }
        }
    }

    private fun addBuiltInTiles() {
        for (m in BuiltInWallpapers.ALL) {
            val tile = addTile(WallpaperSource.BuiltIn(m.id), m.title, m.baseColor)
            val thumb = WallpaperPlan.thumbSize()
            jobs += WallpaperLoader.builtIn(m.id, thumb, config = thumbConfig()) { b -> setThumb(tile, b) }
        }
    }

    private fun addUserTiles() {
        val thumb = WallpaperPlan.thumbSize()
        for (f in userFiles) {
            val tile = addTile(WallpaperSource.UserImage(f), f.nameWithoutExtension, Theme.SURFACE)
            jobs += WallpaperLoader.file(f, thumb, thumbConfig(), cover = true) { b -> setThumb(tile, b) }
        }
    }

    private fun thumbConfig() = if (lite) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888

    private fun setThumb(tile: View, b: Bitmap?) {
        if (b == null) return
        if (root == null) { BitmapRecycler.defer(b); return }
        thumbs += b
        ((tile as ViewGroup).getChildAt(0) as ImageView).setImageBitmap(b)
    }

    private fun addTile(source: WallpaperSource, title: String, base: Int): View {
        val tw = px(160); val th = px(90)
        val tile = FrameLayout(context).apply {
            isFocusable = true
            clipToOutline = true
            background = GradientDrawable().apply { cornerRadius = px(12).toFloat(); setColor(base) }
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        tile.addView(ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1))
        tile.addView(TextView(context).apply {
            panelText(PanelStyle.DETAIL_SP, FontWeight.SEMIBOLD)
            text = title
            setShadowLayer(px(6).toFloat(), 0f, 0f, 0xCC000000.toInt())
            setPadding(px(8), 0, px(8), px(6))
        }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        val ring = View(context)
        tile.addView(ring, FrameLayout.LayoutParams(-1, -1))
        tile.setOnFocusChangeListener { v, f ->
            updateRing(v, source)
            if (!lite) v.animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f).setDuration(Motion.ms(PanelMotion.LITE_MS)).start()
        }
        tile.setOnClickListener { select(source) }
        tiles += source to tile
        // 4 per row.
        val rowIndex = (tiles.size - 1) / COLUMNS
        while (grid.childCount <= rowIndex) {
            grid.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                setPadding(px(6), px(6), px(6), px(6))
            })
        }
        (grid.getChildAt(rowIndex) as LinearLayout).addView(tile, LinearLayout.LayoutParams(tw, th).apply { marginEnd = px(12) })
        updateRing(tile, source)
        return tile
    }

    private fun isCurrent(source: WallpaperSource): Boolean = settings.source == source

    private fun updateRing(tile: View, source: WallpaperSource) {
        val ring = (tile as ViewGroup).getChildAt(2)
        ring.background = when {
            tile.isFocused -> GradientDrawable().apply { cornerRadius = px(12).toFloat(); setStroke(px(3), Theme.TEXT) }
            isCurrent(source) -> GradientDrawable().apply { cornerRadius = px(12).toFloat(); setStroke(px(2), PanelStyle.ACCENT) }
            else -> null
        }
    }

    // --- actions ---------------------------------------------------------------------------------

    private fun commit(s: WallpaperSettings) {
        settings = s.normalized()
        store.save(settings)
        tiles.forEach { (src, v) -> updateRing(v, src) }
        refreshControls()
        onChange(settings)
    }

    private fun select(source: WallpaperSource) = commit(settings.copy(source = source))

    private fun toggleSlideshow() {
        val f = folder ?: return
        if (settings.source is WallpaperSource.Slideshow) {
            commit(settings.copy(source = userFiles.firstOrNull()?.let { WallpaperSource.UserImage(it) } ?: WallpaperSource.DEFAULT))
        } else if (userFiles.isNotEmpty()) {
            commit(settings.copy(source = WallpaperSource.Slideshow(f, currentInterval())))
        }
    }

    private fun currentInterval(): Int =
        (settings.source as? WallpaperSource.Slideshow)?.intervalSec ?: WallpaperPlan.DEFAULT_INTERVAL_SEC

    private fun stepInterval(delta: Int) {
        val s = settings.source as? WallpaperSource.Slideshow ?: return
        val i = INTERVALS.indexOfFirst { it >= s.intervalSec }.let { if (it < 0) INTERVALS.size - 1 else it }
        val next = INTERVALS[(i + delta).coerceIn(0, INTERVALS.size - 1)]
        commit(settings.copy(source = s.copy(intervalSec = next)))
    }

    private fun stepDim(delta: Int) {
        commit(settings.copy(dimPercent = (settings.dimPercent + delta * 10).coerceIn(0, WallpaperSettings.MAX_DIM)))
    }

    private fun refreshControls() {
        if (!::controls.isInitialized) return
        val path = folder?.path ?: UserWallpapers.folder(context)?.path ?: UserWallpapers.DEVICE_PATH
        val entries = WallpaperPanelModel.entries(settings, NotificationAccess.isEnabled(context),
            hasPictures = userFiles.isNotEmpty() || !listed, accessNote = accessNote, folderPath = path)
        val focusedKey = (controls.findFocus() as? PanelRowView)?.key
        if (controls.render(entries) && focusedKey != null) {
            // Rebuilt (a row appeared or went away): focus stays on the same control.
            val rows = controls.rowViews()
            (rows.firstOrNull { it.key == focusedKey } ?: rows.firstOrNull())?.requestFocus()
        }
    }

    /** Keeps D-pad focus inside the panel while it is open. */
    private class TrapLayout(context: Context) : FrameLayout(context) {
        override fun focusSearch(focused: View?, direction: Int): View? {
            val next = super.focusSearch(focused, direction)
            return if (next != null && isDescendant(next)) next else focused
        }

        private fun isDescendant(v: View): Boolean {
            var p: Any? = v.parent
            while (p is View) { if (p === this) return true; p = p.parent }
            return false
        }
        // Key dispatch is not overridden: returning true here would also stop the framework's
        // D-pad focus navigation, which runs only for unhandled keys.
    }

    private companion object {
        const val COLUMNS = 4
        val INTERVALS = intArrayOf(30, 60, 120, 300, 600)
    }
}
