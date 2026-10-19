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
import dev.tvlauncher.R
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.panel.PanelHeader
import dev.tvlauncher.panel.PanelHeaderView
import dev.tvlauncher.panel.PanelList
import dev.tvlauncher.panel.PanelMotion
import dev.tvlauncher.panel.PanelRowView
import dev.tvlauncher.panel.PanelStyle
import dev.tvlauncher.panel.PanelStyle.panelText
import dev.tvlauncher.type.FontWeight
import dev.tvlauncher.ui.Motion
import dev.tvlauncher.ui.Theme
import java.io.File
import kotlin.math.min

/**
 * Wallpaper picker (D-pad first) in the shared panel anatomy. Thumbnail grid of the built-in
 * collections and the owner's picture collections (rendered at most 320x180, off the main
 * thread), a ring on the current choice, then [WallpaperPanelModel] rows: Slideshow switch,
 * interval and dim steppers, notification rows, Pictures/USB access and the honest "how to add
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
    private var collections: List<UserCollection> = emptyList()
    private val thumbs = ArrayList<Bitmap>()
    /** Index in [grid] where the owner's collection sections start (rebuilt on rescan). */
    private var userSectionsStart = 0
    private var permissionAsked = false
    /** Tiles per row: as many as fit the panel (the Settings pane is narrower than the screen). */
    private var columns = COLUMNS
    private val jobs = ArrayList<WallpaperLoader.Job>()
    private val tiles = ArrayList<Pair<WallpaperSource, View>>()
    private lateinit var grid: LinearLayout
    private lateinit var controls: PanelList
    /** True once the first collection scan finished (no "no pictures" message before that). */
    private var scanned = false
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
        scanned = false
        accessNote = null
        val panel = buildPanel()
        val w = min(px(760), (host.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels) - px(96))
        // Panel padding G1 + scroll padding G1 on each side, then the tile row's own padding.
        columns = WallpaperPlan.tileColumns(w - px(PanelStyle.G1) * 4 - px(6) * 2, px(TILE_W_DP) + px(12), COLUMNS)
        r.addView(panel, FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        host.addView(r, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root = r
        val ms = PanelStyle.openMs(context, lite)
        if (ms > 0) {
            panel.alpha = 0f; panel.translationY = px(PanelStyle.G1).toFloat()
            panel.animate().alpha(1f).translationY(0f).setDuration(ms).setInterpolator(DecelerateInterpolator(2f)).start()
        }
        addBuiltInTiles()
        userSectionsStart = grid.childCount
        folder = UserWallpapers.folder(context)
        scanCollections()
        // Back from the permission dialog: pick up Pictures/USB collections.
        r.viewTreeObserver.addOnWindowFocusChangeListener { has ->
            if (has && permissionAsked && root === r) { permissionAsked = false; scanCollections() }
        }
        refreshControls()
        grid.post { tiles.firstOrNull { isCurrent(it.first) }?.second?.requestFocus() ?: tiles.firstOrNull()?.second?.requestFocus() }
    }

    private fun scanCollections() {
        val r = root ?: return
        val roots = UserWallpapers.roots(context)
        WallpaperLoader.background({ UserWallpapers.collections(roots) }) { found ->
            if (root !== r) return@background
            collections = found
            scanned = true
            rebuildUserSections()
            refreshControls()
        }
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
        panel.addView(scroll, LinearLayout.LayoutParams(-1, min(px(300), (context.resources.displayMetrics.heightPixels * 0.45f).toInt())).apply { topMargin = px(PanelStyle.G1) })
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
            WallpaperPanelModel.PICTURES -> askForPictures()
        }
    }

    private fun addBuiltInTiles() {
        val thumb = WallpaperPlan.thumbSize()
        for (name in WallpaperCatalog.COLLECTIONS) {
            val metas = WallpaperCatalog.inCollection(name)
            val section = section(name)
            if (metas.size > 1 && name != WallpaperCatalog.CLASSIC) {
                val first = metas[0]
                val tile = addTile(section, WallpaperSource.BuiltInSlideshow(name, currentInterval()), "Slideshow", first.baseColor)
                jobs += WallpaperLoader.builtIn(first.id, thumb, config = thumbConfig()) { b -> setThumb(tile, b) }
            }
            for (m in metas) {
                val tile = addTile(section, WallpaperSource.BuiltIn(m.id), m.title, m.baseColor)
                jobs += WallpaperLoader.builtIn(m.id, thumb, config = thumbConfig()) { b -> setThumb(tile, b) }
            }
        }
    }

    /** The owner's collections: a "Slideshow" tile, then up to [MAX_THUMBS] pictures each. */
    private fun rebuildUserSections() {
        val gone = tiles.filter { (src, _) -> src is WallpaperSource.UserImage || src is WallpaperSource.Slideshow }
        gone.forEach { (_, v) -> ((v as ViewGroup).getChildAt(0) as ImageView).setImageDrawable(null) }
        tiles.removeAll(gone)
        while (grid.childCount > userSectionsStart) grid.removeViewAt(grid.childCount - 1)
        val thumb = WallpaperPlan.thumbSize()
        for (c in collections) {
            val section = section("${c.name}  ·  ${c.files.size}")
            val slide = addTile(section, WallpaperSource.Slideshow(c.folder, currentInterval()), "Slideshow", Theme.SURFACE)
            jobs += WallpaperLoader.file(c.files[0], thumb, thumbConfig(), cover = true) { b -> setThumb(slide, b) }
            for (f in c.files.take(MAX_THUMBS)) {
                val tile = addTile(section, WallpaperSource.UserImage(f), f.nameWithoutExtension, Theme.SURFACE)
                jobs += WallpaperLoader.file(f, thumb, thumbConfig(), cover = true) { b -> setThumb(tile, b) }
            }
        }
    }

    /** A titled block of tile rows inside [grid]. */
    private fun section(title: String): LinearLayout {
        grid.addView(TextView(context).apply {
            panelText(PanelStyle.SECTION_SP, FontWeight.MEDIUM, PanelStyle.TEXT_2)
            text = title
            setPadding(px(6), px(10), px(6), 0)
        })
        val block = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        grid.addView(block)
        return block
    }

    private fun askForPictures() {
        if (UserWallpapers.canReadShared(context)) { scanCollections(); return }
        val activity = activityOf(context) ?: return
        permissionAsked = true
        try { activity.requestPermissions(arrayOf(UserWallpapers.PERMISSION), REQ_PICTURES) } catch (e: Exception) { permissionAsked = false }
    }

    private fun activityOf(c: Context): android.app.Activity? {
        var x: Context? = c
        while (x is android.content.ContextWrapper) {
            if (x is android.app.Activity) return x
            x = x.baseContext
        }
        return null
    }

    private fun thumbConfig() = if (lite) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888

    private fun setThumb(tile: View, b: Bitmap?) {
        if (b == null) return
        if (root == null) { BitmapRecycler.defer(b); return }
        thumbs += b
        ((tile as ViewGroup).getChildAt(0) as ImageView).setImageBitmap(b)
    }

    private fun addTile(section: LinearLayout, source: WallpaperSource, title: String, base: Int): View {
        val tw = px(TILE_W_DP); val th = px(TILE_W_DP * 9 / 16)
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
        // [columns] per row within the section.
        val last = (0 until section.childCount).lastOrNull()?.let { section.getChildAt(it) as LinearLayout }
        val row = if (last != null && last.childCount < columns) last else LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            setPadding(px(6), px(6), px(6), px(6))
            section.addView(this)
        }
        row.addView(tile, LinearLayout.LayoutParams(tw, th).apply { marginEnd = px(12) })
        updateRing(tile, source)
        return tile
    }

    /** Slideshow tiles match their folder/collection whatever the interval. */
    private fun isCurrent(source: WallpaperSource): Boolean = when (val cur = settings.source) {
        is WallpaperSource.Slideshow -> source is WallpaperSource.Slideshow && source.folder == cur.folder
        is WallpaperSource.BuiltInSlideshow -> source is WallpaperSource.BuiltInSlideshow && source.collection == cur.collection
        else -> cur == source
    }

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

    private fun select(source: WallpaperSource) = commit(settings.copy(source = when (source) {
        // Keep the chosen interval when switching between slideshows.
        is WallpaperSource.Slideshow -> source.copy(intervalSec = currentInterval())
        is WallpaperSource.BuiltInSlideshow -> source.copy(intervalSec = currentInterval())
        else -> source
    }))

    /** Off: the slideshow's first picture. On: a slideshow of the current picture's collection. */
    private fun toggleSlideshow() {
        val next: WallpaperSource? = when (val s = settings.source) {
            is WallpaperSource.Slideshow ->
                collections.firstOrNull { it.folder == s.folder }?.files?.firstOrNull()?.let { WallpaperSource.UserImage(it) } ?: WallpaperSource.DEFAULT
            is WallpaperSource.BuiltInSlideshow ->
                WallpaperSource.BuiltIn(WallpaperCatalog.inCollection(s.collection).firstOrNull()?.id ?: WallpaperSource.DEFAULT_ID)
            is WallpaperSource.UserImage -> s.file.parentFile?.let { WallpaperSource.Slideshow(it, currentInterval()) }
            is WallpaperSource.BuiltIn -> WallpaperSource.BuiltInSlideshow(WallpaperCatalog.meta(s.id).collection, currentInterval())
        }
        next?.let { commit(settings.copy(source = it)) }
    }

    private fun currentInterval(): Int =
        AmbientSettingsRows.slideInterval(settings.source) ?: WallpaperPlan.DEFAULT_INTERVAL_SEC

    private fun stepInterval(delta: Int) {
        if (AmbientSettingsRows.slideInterval(settings.source) == null) return
        commit(AmbientSettingsRows.step(settings, "slideshowInterval", delta))
    }

    private fun stepDim(delta: Int) {
        commit(settings.copy(dimPercent = (settings.dimPercent + delta * 10).coerceIn(0, WallpaperSettings.MAX_DIM)))
    }

    private fun refreshControls() {
        if (!::controls.isInitialized) return
        val entries = WallpaperPanelModel.entries(settings, NotificationAccess.isEnabled(context),
            hasPictures = collections.isNotEmpty() || !scanned, accessNote = accessNote,
            folderPath = "Pictures/${UserWallpapers.COLLECTIONS_DIR}",
            picturesGranted = UserWallpapers.canReadShared(context))
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
        const val TILE_W_DP = 160
        const val MAX_THUMBS = 8
        const val REQ_PICTURES = 4711
    }
}
