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
    private lateinit var slideshowRow: TextView
    private lateinit var intervalRow: TextView
    private lateinit var dimRow: TextView
    private lateinit var textRow: TextView
    private lateinit var textHint: TextView
    private lateinit var notifRow: TextView
    private lateinit var notifHint: TextView
    private lateinit var accessCard: NotificationAccessCard
    private lateinit var emptyText: TextView
    private lateinit var accessRow: TextView

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
        columns = WallpaperPlan.tileColumns(w - px(28) * 2 - px(6) * 2, px(TILE_W_DP) + px(12), COLUMNS)
        r.addView(panel, FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        host.addView(r, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root = r
        if (!lite) {
            panel.alpha = 0f; panel.translationY = px(16).toFloat()
            panel.animate().alpha(1f).translationY(0f).setDuration(180L).start()
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
        // Notification rows follow AmbientSettingsRows (same keys, visibility and hints).
        notifRow = controlRow { commit(AmbientSettingsRows.toggle(settings, "notificationsEnabled")) }
        notifHint = hintText()
        accessCard = NotificationAccessCard(context)
        textRow = controlRow { commit(AmbientSettingsRows.toggle(settings, "showNotificationText")) }
        textHint = hintText()
        panel.addView(notifRow, lp(top = 16))
        panel.addView(notifHint, lp(top = 4))
        panel.addView(accessCard, lp(top = 6))
        panel.addView(textRow, lp(top = 6))
        panel.addView(textHint, lp(top = 4))
        accessRow = controlRow { askForPictures() }
        panel.addView(accessRow, lp(top = 16))
        panel.addView(TextView(context).apply {
            style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2)
            text = "Add your own: each folder in Pictures/TVLauncher (or TVLauncher on a USB stick) is a collection, " +
                "named after the folder. JPG, PNG or WebP up to 8 MB, ${WallpaperPlan.MAX_FILES} per folder."
            maxLines = 8
        }, lp(top = 8))
        return panel
    }

    private fun hintText() = TextView(context).apply { style(Theme.SP_LABEL, Theme.regular, Theme.TEXT_2) }

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
            style(Theme.SP_LABEL, Theme.medium, Theme.TEXT_2)
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
        if (!::slideshowRow.isInitialized) return
        val on = AmbientSettingsRows.slideInterval(settings.source) != null
        slideshowRow.text = "Slideshow:  ${if (on) "On" else "Off"}"
        val granted = UserWallpapers.canReadShared(context)
        accessRow.visibility = if (granted) View.GONE else View.VISIBLE
        accessRow.text = "Pictures folder and USB sticks:  Allow access"
        intervalRow.visibility = if (on) View.VISIBLE else View.GONE
        intervalRow.text = "Change every   ‹  ${formatInterval(currentInterval())}  ›"
        dimRow.text = "Dim   ‹  ${settings.dimPercent} %  ›"
        val rows = AmbientSettingsRows.rows(settings, NotificationAccess.isEnabled(context)).associateBy { it.key }
        fun bind(row: TextView, hint: TextView, key: String) {
            val r = rows.getValue(key)
            row.visibility = if (r.visible) View.VISIBLE else View.GONE
            row.text = "${r.title}:  ${r.value}"
            hint.visibility = if (r.visible && r.hint != null) View.VISIBLE else View.GONE
            hint.text = r.hint ?: ""
        }
        bind(notifRow, notifHint, "notificationsEnabled")
        bind(textRow, textHint, "showNotificationText")
        accessCard.visibility = if (rows.getValue("notificationAccess").visible) View.VISIBLE else View.GONE
        accessCard.refresh()
        emptyText.visibility = if (collections.isEmpty()) View.VISIBLE else View.GONE
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
        const val TILE_W_DP = 160
        const val MAX_THUMBS = 8
        const val REQ_PICTURES = 4711
    }
}
