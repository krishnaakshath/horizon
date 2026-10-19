package dev.tvlauncher.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.ambient.BitmapRecycler
import dev.tvlauncher.ambient.AmbientSettingsRows
import dev.tvlauncher.ambient.UserCollection
import dev.tvlauncher.ambient.WallpaperCatalog
import dev.tvlauncher.ambient.UserWallpapers
import dev.tvlauncher.ambient.WallpaperLoader
import dev.tvlauncher.ambient.WallpaperPlan
import dev.tvlauncher.ambient.WallpaperSettings
import dev.tvlauncher.ambient.WallpaperSource
import dev.tvlauncher.ambient.WallpaperStore
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRow
import dev.tvlauncher.appsettings.SettingsStyle
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.ui.GlassTokens
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style

/**
 * Settings > Ambient > Wallpaper: a normal Settings sub-page in the same glass style as every other
 * page (no overlay panel). One horizontally scrolling strip of wallpaper thumbnails (built-ins,
 * then the owner's folders), a ring on focus and a check on the chosen one; then the wallpaper's
 * own rows ([SPEC]: collection, slideshow, interval, dim, your own pictures). Notification chips, their text and
 * the access card live only in the Ambient page's Advanced block.
 *
 * The strip shows one collection at a time (the "Collection" row picks it) from the picker data
 * model ([collections]: WallpaperCatalog collections such as Classic and Anime, then the owner's
 * folders from CollectionScanner), so new collections appear here without code changes.
 * Thumbnails are 320x180 (RGB_565 in LITE), loaded off the main thread and recycled when the page
 * leaves the screen.
 */
object WallpaperPage {
    const val ID = "ambient.wallpaper"

    /** Ambient model rows edited here; the Ambient page does not repeat them. */
    val MODEL_KEYS = listOf("slideshowInterval", "dim")

    val SPEC = PageSpec(
        ID,
        listOf("wallpaper.collection", "wallpaper.slideshow", "wallpaper.interval", "wallpaper.dim", "wallpaper.pictures")
    )

    val INTERVALS = intArrayOf(30, 60, 120, 300, 600)

    /** Thumbnails per owner collection in the strip (memory: 320x180 each). */
    const val MAX_USER_THUMBS = 12

    /** One strip entry: what it selects, its label and the colour shown until the thumbnail loads. */
    data class Entry(val source: WallpaperSource, val title: String, val baseColor: Int, val key: String)

    /** A titled group of entries (Classic, Anime, an owner folder ...), in strip order. */
    data class Collection(val title: String, val entries: List<Entry>)

    /**
     * The picker data model as strip collections: the built-in catalog collections
     * ([WallpaperCatalog]), then the owner's folders ([UserCollection]). Pure.
     */
    fun collections(user: List<UserCollection>): List<Collection> {
        val builtIn = WallpaperCatalog.COLLECTIONS.map { name ->
            Collection(name, WallpaperCatalog.inCollection(name).map {
                Entry(WallpaperSource.BuiltIn(it.id), it.title, it.baseColor, "wallpaper.tile.${it.id}")
            })
        }
        val mine = user.map { c ->
            Collection(c.name, c.files.take(MAX_USER_THUMBS).map {
                Entry(WallpaperSource.UserImage(it), it.nameWithoutExtension, Theme.SURFACE, "wallpaper.tile.${c.name}/${it.name}")
            })
        }
        return builtIn + mine
    }

    /** The collection [src] belongs to (its index in [all]), 0 when unknown. */
    fun collectionOf(src: WallpaperSource, all: List<Collection>, user: List<UserCollection>): Int {
        val name = when (src) {
            is WallpaperSource.BuiltIn -> WallpaperCatalog.meta(src.id).collection
            is WallpaperSource.BuiltInSlideshow -> src.collection
            is WallpaperSource.UserImage -> user.firstOrNull { it.folder == src.file.parentFile }?.name
            is WallpaperSource.Slideshow -> user.firstOrNull { it.folder == src.folder }?.name
        }
        return all.indexOfFirst { it.title == name }.coerceAtLeast(0)
    }

    /** Slideshow on: of the current picture's collection. Off: that collection's first picture. Pure. */
    fun toggledSlideshow(src: WallpaperSource, user: List<UserCollection>, interval: Int): WallpaperSource = when (src) {
        is WallpaperSource.Slideshow ->
            user.firstOrNull { it.folder == src.folder }?.files?.firstOrNull()?.let { WallpaperSource.UserImage(it) } ?: WallpaperSource.DEFAULT
        is WallpaperSource.BuiltInSlideshow ->
            WallpaperSource.BuiltIn(WallpaperCatalog.inCollection(src.collection).firstOrNull()?.id ?: WallpaperSource.DEFAULT_ID)
        is WallpaperSource.UserImage -> src.file.parentFile?.let { WallpaperSource.Slideshow(it, interval) } ?: src
        is WallpaperSource.BuiltIn -> WallpaperSource.BuiltInSlideshow(WallpaperCatalog.meta(src.id).collection, interval)
    }

    fun isSlideshow(src: WallpaperSource) = AmbientSettingsRows.slideInterval(src) != null

    /** [src] with its slideshow interval set to [sec] (other sources unchanged). */
    fun withInterval(src: WallpaperSource, sec: Int): WallpaperSource = when (src) {
        is WallpaperSource.Slideshow -> src.copy(intervalSec = sec)
        is WallpaperSource.BuiltInSlideshow -> src.copy(intervalSec = sec)
        else -> src
    }

    /** Slideshow interval step [delta] from [current] within [INTERVALS]. */
    fun stepInterval(current: Int, delta: Int): Int {
        val i = INTERVALS.indexOfFirst { it >= current }.let { if (it < 0) INTERVALS.size - 1 else it }
        return INTERVALS[(i + delta).coerceIn(0, INTERVALS.size - 1)]
    }

    fun intervalLabel(sec: Int) = if (sec < 60) "$sec s" else "${sec / 60} min"

    /** Friendly, no developer commands: where collections go and what works. */
    const val FOLDER_NOTE =
        "Each folder in Pictures/TVLauncher on the TV, or TVLauncher on a USB stick, becomes a collection named after the folder. " +
            "JPG, PNG or WebP up to 8 MB."

    fun build(context: Context, nav: SettingsNav): View {
        val store = WallpaperStore(context)
        var s = store.load()
        val page = SettingsPage(context, nav.style, SPEC)
        val lite = nav.profile.effectsMode == EffectsMode.LITE
        var user: List<UserCollection> = lastScan
        var all = collections(user)
        var shown = collectionOf(s.source, all, user)
        val strip = Strip(context, nav.style, lite) { src ->
            s = store.load().copy(source = src).normalized()
            store.save(s)
        }
        page.lead("Used by the ambient screen and the screensaver.")
        lateinit var collectionRow: SettingsRow
        lateinit var slideshowRow: SettingsRow
        lateinit var intervalRow: SettingsRow
        fun showStrip() {
            strip.show(all.getOrNull(shown)?.entries.orEmpty(), s.source)
        }
        collectionRow = page.row("wallpaper.collection", "Collection",
            RowKind.Choice(all.map { it.title }, shown) { i -> shown = i; showStrip() }, iconRes = R.drawable.ic_set_palette)
        page.add(strip)
        showStrip()
        page.preferredFocus = strip.keyOf(s.source)

        slideshowRow = page.row("wallpaper.slideshow", "Slideshow", RowKind.Toggle(isSlideshow(s.source)) { on ->
            val cur = store.load()
            if (on == isSlideshow(cur.source)) return@Toggle
            s = cur.copy(source = toggledSlideshow(cur.source, user, AmbientSettingsRows.slideInterval(cur.source)
                ?: WallpaperPlan.DEFAULT_INTERVAL_SEC)).normalized()
            store.save(s)
            strip.setChosen(s.source)
            intervalRow.visibility = if (isSlideshow(s.source)) View.VISIBLE else View.GONE
        }, subtitle = "Cycles through the chosen collection", iconRes = R.drawable.ic_set_tiles)
        val interval = AmbientSettingsRows.slideInterval(s.source) ?: WallpaperPlan.DEFAULT_INTERVAL_SEC
        intervalRow = page.row("wallpaper.interval", "Change picture every",
            RowKind.Stepper(INTERVALS.indexOfFirst { it >= interval }.coerceAtLeast(0), 0, INTERVALS.size - 1,
                { intervalLabel(INTERVALS[it]) }) { i ->
                val cur = store.load()
                s = cur.copy(source = withInterval(cur.source, INTERVALS[i])).normalized()
                store.save(s)
            }, iconRes = R.drawable.ic_set_clock)
        intervalRow.visibility = if (isSlideshow(s.source)) View.VISIBLE else View.GONE
        page.row("wallpaper.dim", "Dim wallpaper",
            RowKind.Stepper(s.dimPercent / 10, 0, WallpaperSettings.MAX_DIM / 10, { "${it * 10} %" }, bar = true) { v ->
                s = store.load().copy(dimPercent = v * 10).normalized()
                store.save(s)
            }, iconRes = R.drawable.ic_set_eye)

        lateinit var help: TextView
        lateinit var picturesRow: SettingsRow
        val granted = UserWallpapers.canReadShared(context)
        picturesRow = page.row("wallpaper.pictures", "Your own pictures",
            RowKind.Action(if (granted) null else "Allow access") {
                if (!UserWallpapers.canReadShared(context) && requestPictures(context)) return@Action
                val open = help.visibility != View.VISIBLE
                help.visibility = if (open) View.VISIBLE else View.GONE
                picturesRow.setExpanded(open)
            }, subtitle = "Folders on the TV or a USB stick", iconRes = R.drawable.ic_set_import)
        help = page.body.note(FOLDER_NOTE, maxLines = 4)
        help.visibility = View.GONE
        if (granted) picturesRow.setExpanded(false)

        fun scan() {
            val roots = UserWallpapers.roots(context)
            WallpaperLoader.background({ UserWallpapers.collections(roots) }) { found ->
                if (!page.isAttachedToWindow || found == lastScan) return@background
                // New or changed folders: rebuild once with them (the next scan finds no change).
                lastScan = found
                nav.refresh()
            }
        }
        // Back from the permission dialog (no restart): rescan once access exists.
        var hadAccess = granted
        val focusWatch = android.view.ViewTreeObserver.OnWindowFocusChangeListener { has ->
            if (has && !hadAccess && UserWallpapers.canReadShared(context)) { hadAccess = true; nav.refresh() }
        }
        page.viewTreeObserver.addOnWindowFocusChangeListener(focusWatch)
        page.doOnDetach {
            strip.release()
            try { page.viewTreeObserver.removeOnWindowFocusChangeListener(focusWatch) } catch (e: RuntimeException) {}
        }
        page.post { scan() }
        return page
    }

    /** Asks for the "Photos and media" permission; false when it cannot be asked here. */
    private fun requestPictures(context: Context): Boolean {
        var x: Context? = context
        while (x is android.content.ContextWrapper) {
            if (x is android.app.Activity) {
                return try { x.requestPermissions(arrayOf(UserWallpapers.PERMISSION), REQ_PICTURES); true } catch (e: Exception) { false }
            }
            x = x.baseContext
        }
        return false
    }

    private const val REQ_PICTURES = 4711

    /** The last folder scan (small: names and file lists), so a rebuild starts complete. Main thread. */
    private var lastScan: List<UserCollection> = emptyList()

    /**
     * The thumbnail strip. Its own row of focusable tiles inside a HorizontalScrollView (horizontal
     * only, so it never fights the page's vertical scroll); padding leaves room for the focus scale.
     */
    private class Strip(
        context: Context,
        private val style: SettingsStyle,
        private val lite: Boolean,
        private val onPick: (WallpaperSource) -> Unit
    ) : HorizontalScrollView(context) {
        private val density = resources.displayMetrics.density
        private fun px(dp: Int) = (dp * density + 0.5f).toInt()
        private val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(px(PAD_DP), px(PAD_DP), px(PAD_DP), px(PAD_DP))
        }
        private val tiles = LinkedHashMap<String, Tile>()
        private val jobs = ArrayList<WallpaperLoader.Job>()
        private val thumbs = ArrayList<Bitmap>()
        private var chosen: WallpaperSource? = null

        init {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            isFocusable = false
            descendantFocusability = FOCUS_AFTER_DESCENDANTS
            addView(row, LayoutParams(-2, -2))
        }

        fun keyOf(src: WallpaperSource): String? = tiles.values.firstOrNull { it.entry.source == src }?.entry?.key

        /** Shows [entries] (one collection): the previous tiles and their thumbnails are released first. */
        fun show(entries: List<Entry>, current: WallpaperSource) {
            chosen = current
            if (entries.map { it.key } != tiles.keys.toList()) {
                val hadFocus = hasFocus()
                release()
                row.removeAllViews()
                tiles.clear()
                scrollTo(0, 0)
                for (e in entries) addTile(e)
                if (hadFocus) (tiles.values.firstOrNull { it.entry.source == chosen } ?: tiles.values.firstOrNull())?.requestFocus()
            }
            tiles.values.forEach { it.refresh() }
        }

        fun setChosen(src: WallpaperSource) {
            chosen = src
            tiles.values.forEach { it.refresh() }
        }

        private fun addTile(e: Entry) {
            val t = Tile(context, e)
            tiles[e.key] = t
            row.addView(t, LinearLayout.LayoutParams(px(TILE_W_DP), px(TILE_H_DP)).apply { marginEnd = px(GAP_DP) })
            val size = WallpaperPlan.thumbSize()
            val config = if (lite) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            val done: (Bitmap?) -> Unit = { b -> if (b != null) { if (t.isAttachedToWindow || isAttachedToWindow) { thumbs += b; t.image.setImageBitmap(b) } else BitmapRecycler.defer(b) } }
            jobs += when (val src = e.source) {
                is WallpaperSource.BuiltIn -> WallpaperLoader.builtIn(src.id, size, config = config, done = done)
                is WallpaperSource.UserImage -> WallpaperLoader.file(src.file, size, config, cover = true, done = done)
                is WallpaperSource.Slideshow, is WallpaperSource.BuiltInSlideshow -> return
            }
        }

        /** Thumbnails detached first, then recycled after the next frames (BitmapRecycler). */
        fun release() {
            jobs.forEach { it.cancel() }; jobs.clear()
            tiles.values.forEach { it.image.setImageDrawable(null) }
            thumbs.forEach { BitmapRecycler.defer(it) }; thumbs.clear()
        }

        /** Focus follows: the focused tile is scrolled fully into view with a margin. */
        override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
            if (childCount == 0) return 0
            val margin = px(PAD_DP + 8)
            val left = scrollX
            val right = scrollX + width
            val max = (row.width - width).coerceAtLeast(0)
            val d = when {
                rect.left - margin < left -> rect.left - margin - left
                rect.right + margin > right -> rect.right + margin - right
                else -> 0
            }
            return d.coerceIn(-scrollX, max - scrollX)
        }

        private inner class Tile(context: Context, val entry: Entry) : FrameLayout(context) {
            val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            private val ring = View(context).apply { auditTag(AuditTags.IGNORE) }
            private val check = ImageView(context).apply {
                setImageResource(R.drawable.ic_set_check)
                imageTintList = ColorStateList.valueOf(Theme.TEXT_DARK)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Theme.TEXT) }
                val p = px(3)
                setPadding(p, p, p, p)
            }
            private val label = TextView(context).apply {
                style(14f, Theme.medium)
                text = entry.title
                maxLines = 1
                setShadowLayer(px(6).toFloat(), 0f, 0f, 0xCC000000.toInt())
                setPadding(px(10), 0, px(10), px(6))
            }

            init {
                tag = entry.key
                isFocusable = true
                isClickable = true
                clipToOutline = true
                background = GradientDrawable().apply { cornerRadius = px(GlassTokens.RADIUS_ITEM).toFloat(); setColor(entry.baseColor) }
                outlineProvider = ViewOutlineProvider.BACKGROUND
                // Art, label, ring and check are stacked on purpose.
                auditTag(AuditTags.LAYER)
                addView(image, LayoutParams(-1, -1))
                addView(label, LayoutParams(-1, -2, Gravity.BOTTOM))
                addView(ring, LayoutParams(-1, -1))
                addView(check, LayoutParams(px(22), px(22), Gravity.TOP or Gravity.END).apply { topMargin = px(8); marginEnd = px(8) })
                setOnClickListener {
                    chosen = entry.source
                    tiles.values.forEach { it.refresh() }
                    onPick(entry.source)
                }
            }

            fun refresh() {
                val isChosen = entry.source == chosen
                check.visibility = if (isChosen) View.VISIBLE else View.GONE
                ring.background = when {
                    isFocused -> GradientDrawable().apply { cornerRadius = px(GlassTokens.RADIUS_ITEM).toFloat(); setStroke(px(3), Theme.TEXT) }
                    isChosen -> GradientDrawable().apply { cornerRadius = px(GlassTokens.RADIUS_ITEM).toFloat(); setStroke(px(2), style.accentOnDark) }
                    else -> null
                }
            }

            override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
                super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
                refresh()
                val sc = if (gainFocus) FOCUS_SCALE else 1f
                animate().cancel()
                if (style.focusMs > 0) animate().scaleX(sc).scaleY(sc).setDuration(style.focusMs).start()
                else { scaleX = sc; scaleY = sc }
            }
        }
    }

    const val TILE_W_DP = 160
    const val TILE_H_DP = 90
    const val GAP_DP = 14
    /** Strip padding: at least half the focus growth of a tile on every side. */
    const val PAD_DP = 8
    const val FOCUS_SCALE = 1.06f
}
