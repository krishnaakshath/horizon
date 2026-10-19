package dev.tvlauncher.ambient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
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
    private lateinit var slideshowRow: TextView
    private lateinit var intervalRow: TextView
    private lateinit var dimRow: TextView
    private lateinit var emptyText: TextView

    val isShowing: Boolean get() = root != null

    private fun px(dp: Int) = (dp * density + 0.5f).toInt()

    fun show(host: ViewGroup) {
        if (root != null) return
        this.host = host
        settings = store.load()
        val r = TrapLayout(context)
        r.setBackgroundColor(0x99000000.toInt())
        r.isClickable = true
        val panel = buildPanel()
        val w = min(px(760), (host.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels) - px(96))
        r.addView(panel, FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        host.addView(r, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root = r
        if (!lite) {
            panel.alpha = 0f; panel.translationY = px(16).toFloat()
            panel.animate().alpha(1f).translationY(0f).setDuration(180L).start()
        }
        addBuiltInTiles()
        folder = UserWallpapers.folder(context)
        val f = folder
        WallpaperLoader.background({ UserWallpapers.list(f) }) { files ->
            if (root !== r) return@background
            userFiles = files
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
        host?.removeView(r)
        host = null
        tiles.clear()
        thumbs.forEach { it.recycle() }; thumbs.clear()
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
            setPadding(px(28), px(24), px(28), px(24))
            background = GradientDrawable().apply {
                cornerRadius = px(24).toFloat()
                setColor(0xF2151922.toInt())
                setStroke(px(1), Theme.STROKE)
            }
        }
        panel.addView(TextView(context).apply { style(22f, Theme.medium); text = "Wallpaper" })
        panel.addView(TextView(context).apply {
            style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2)
            text = "Used by the ambient screen and screensaver."
        }, lp(top = 4))
        grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
            addView(grid)
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, min(px(300), (context.resources.displayMetrics.heightPixels * 0.45f).toInt())).apply { topMargin = px(16) })
        emptyText = TextView(context).apply { style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2); visibility = View.GONE }
        panel.addView(emptyText, lp(top = 8))
        slideshowRow = controlRow { toggleSlideshow() }
        intervalRow = stepperRow { delta -> stepInterval(delta) }
        dimRow = stepperRow { delta -> stepDim(delta) }
        panel.addView(slideshowRow, lp(top = 16))
        panel.addView(intervalRow, lp(top = 6))
        panel.addView(dimRow, lp(top = 6))
        val path = UserWallpapers.folder(context)?.path ?: UserWallpapers.DEVICE_PATH
        panel.addView(TextView(context).apply {
            style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2)
            text = "Put pictures in $path\nFrom a computer: ${UserWallpapers.ADB_PUSH}\n" +
                "JPG, PNG or WebP, up to 8 MB each, ${WallpaperPlan.MAX_FILES} pictures max. Reopen this panel after copying."
            maxLines = 4
        }, lp(top = 16))
        return panel
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(top) }

    private fun rowBackground(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = px(12).toFloat()
        setColor(if (focused) 0x26FFFFFF else 0x0FFFFFFF)
        if (focused) setStroke(px(2), Theme.TEXT)
    }

    private fun controlRow(onClick: () -> Unit) = TextView(context).apply {
        style(Theme.SP_BUTTON, Theme.medium)
        isFocusable = true
        setPadding(px(16), px(10), px(16), px(10))
        background = rowBackground(false)
        setOnFocusChangeListener { v, f -> v.background = rowBackground(f) }
        setOnClickListener { onClick() }
    }

    private fun stepperRow(onStep: (Int) -> Unit) = controlRow { onStep(+1) }.apply {
        setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { onStep(-1); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { onStep(+1); true }
                else -> false
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
            jobs += WallpaperLoader.file(f, thumb, thumbConfig()) { b -> setThumb(tile, b) }
        }
    }

    private fun thumbConfig() = if (lite) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888

    private fun setThumb(tile: View, b: Bitmap?) {
        if (b == null) return
        if (root == null) { b.recycle(); return }
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
            style(12f, Theme.medium)
            text = title
            maxLines = 1
            setShadowLayer(px(6).toFloat(), 0f, 0f, 0xCC000000.toInt())
            setPadding(px(8), 0, px(8), px(6))
        }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        val ring = View(context)
        tile.addView(ring, FrameLayout.LayoutParams(-1, -1))
        tile.setOnFocusChangeListener { v, f ->
            updateRing(v, source)
            if (!lite) v.animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f).setDuration(120L).start()
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
            isCurrent(source) -> GradientDrawable().apply { cornerRadius = px(12).toFloat(); setStroke(px(2), 0xFF7FA8FF.toInt()) }
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
        if (!::slideshowRow.isInitialized) return
        val on = settings.source is WallpaperSource.Slideshow
        val canSlideshow = userFiles.isNotEmpty()
        slideshowRow.text = "Slideshow of my pictures:  ${if (on) "On" else "Off"}"
        slideshowRow.alpha = if (canSlideshow || on) 1f else 0.45f
        intervalRow.visibility = if (on) View.VISIBLE else View.GONE
        intervalRow.text = "Change every   ‹  ${formatInterval(currentInterval())}  ›"
        dimRow.text = "Dim   ‹  ${settings.dimPercent} %  ›"
        emptyText.visibility = if (userFiles.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = "No pictures of your own yet. Built-in wallpapers are shown above."
    }

    private fun formatInterval(sec: Int) = if (sec < 60) "$sec s" else "${sec / 60} min"

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
