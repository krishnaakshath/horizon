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
import dev.tvlauncher.ambient.BuiltInWallpapers
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
import java.io.File

/**
 * Settings > Ambient > Wallpaper: a normal Settings sub-page in the same glass style as every other
 * page (no overlay panel). One horizontally scrolling strip of wallpaper thumbnails (built-ins,
 * then the user's pictures), a ring on focus and a check on the chosen one; then the wallpaper's
 * own rows ([SPEC]: slideshow, interval, dim, pictures folder). Notification chips, their text and
 * the access card live only in the Ambient page's Advanced block.
 *
 * The strip's content comes from the picker data model ([collections]: BuiltInWallpapers,
 * UserWallpapers, WallpaperSource), so new collections show up here as further strip entries.
 * Thumbnails are 320x180 (RGB_565 in LITE), loaded off the main thread and recycled when the page
 * leaves the screen.
 */
object WallpaperPage {
    const val ID = "ambient.wallpaper"

    /** Ambient model rows edited here; the Ambient page does not repeat them. */
    val MODEL_KEYS = listOf("slideshowInterval", "dim")

    val SPEC = PageSpec(
        ID,
        listOf("wallpaper.slideshow", "wallpaper.interval", "wallpaper.dim", "wallpaper.folder")
    )

    val INTERVALS = intArrayOf(30, 60, 120, 300, 600)

    /** One strip entry: what it selects, its label and the colour shown until the thumbnail loads. */
    data class Entry(val source: WallpaperSource, val title: String, val baseColor: Int, val key: String)

    /** A titled group of entries (Built-in, My pictures, ...), in strip order. */
    data class Collection(val title: String, val entries: List<Entry>)

    /** The strip's model: built-ins first, then the user's pictures (when there are any). Pure. */
    fun collections(userFiles: List<File>): List<Collection> {
        val builtIns = BuiltInWallpapers.ALL.map { Entry(WallpaperSource.BuiltIn(it.id), it.title, it.baseColor, "wallpaper.tile.${it.id}") }
        val mine = userFiles.map { Entry(WallpaperSource.UserImage(it), it.nameWithoutExtension, Theme.SURFACE, "wallpaper.tile.file.${it.name}") }
        return listOfNotNull(Collection("Built-in", builtIns), mine.takeIf { it.isNotEmpty() }?.let { Collection("My pictures", it) })
    }

    /** Slideshow interval step [delta] from [current] within [INTERVALS]. */
    fun stepInterval(current: Int, delta: Int): Int {
        val i = INTERVALS.indexOfFirst { it >= current }.let { if (it < 0) INTERVALS.size - 1 else it }
        return INTERVALS[(i + delta).coerceIn(0, INTERVALS.size - 1)]
    }

    fun intervalLabel(sec: Int) = if (sec < 60) "$sec s" else "${sec / 60} min"

    fun folderNote(path: String) =
        "Put pictures in $path\nFrom a computer: ${UserWallpapers.ADB_PUSH}\n" +
            "JPG, PNG or WebP, up to 8 MB each, ${WallpaperPlan.MAX_FILES} pictures max."

    fun build(context: Context, nav: SettingsNav): View {
        val store = WallpaperStore(context)
        var s = store.load()
        val page = SettingsPage(context, nav.style, SPEC)
        val lite = nav.profile.effectsMode == EffectsMode.LITE
        val strip = Strip(context, nav.style, lite) { src ->
            s = store.load().copy(source = src).normalized()
            store.save(s)
            nav.notify("Wallpaper chosen")
        }
        page.lead("Used by the ambient screen and the screensaver.")
        page.add(strip)
        strip.show(collections(emptyList()), s.source)
        page.preferredFocus = strip.keyOf(s.source)

        var userFiles: List<File> = emptyList()
        val folder = UserWallpapers.folder(context)
        lateinit var intervalRow: SettingsRow
        val slideshowRow = page.row("wallpaper.slideshow", "Slideshow of my pictures",
            RowKind.Toggle(s.source is WallpaperSource.Slideshow) { on ->
                val cur = store.load()
                val next = when {
                    on && folder != null && userFiles.isNotEmpty() ->
                        cur.copy(source = WallpaperSource.Slideshow(folder, WallpaperPlan.DEFAULT_INTERVAL_SEC))
                    on -> { nav.notify("Add pictures first (see Pictures folder)"); nav.refresh(); return@Toggle }
                    else -> cur.copy(source = userFiles.firstOrNull()?.let { WallpaperSource.UserImage(it) } ?: WallpaperSource.DEFAULT)
                }
                s = next.normalized()
                store.save(s)
                strip.setChosen(s.source)
                intervalRow.visibility = if (on) View.VISIBLE else View.GONE
            }, subtitle = "Changes picture on the ambient screen", iconRes = R.drawable.ic_set_motion)
        val interval = (s.source as? WallpaperSource.Slideshow)?.intervalSec ?: WallpaperPlan.DEFAULT_INTERVAL_SEC
        intervalRow = page.row("wallpaper.interval", "Change picture every",
            RowKind.Stepper(INTERVALS.indexOfFirst { it >= interval }.coerceAtLeast(0), 0, INTERVALS.size - 1,
                { intervalLabel(INTERVALS[it]) }) { i ->
                val cur = store.load()
                val sl = cur.source as? WallpaperSource.Slideshow ?: return@Stepper
                s = cur.copy(source = sl.copy(intervalSec = INTERVALS[i])).normalized()
                store.save(s)
            }, iconRes = R.drawable.ic_set_clock)
        intervalRow.visibility = if (s.source is WallpaperSource.Slideshow) View.VISIBLE else View.GONE
        page.row("wallpaper.dim", "Dim wallpaper",
            RowKind.Stepper(s.dimPercent / 10, 0, WallpaperSettings.MAX_DIM / 10, { "${it * 10} %" }, bar = true) { v ->
                s = store.load().copy(dimPercent = v * 10).normalized()
                store.save(s)
            }, iconRes = R.drawable.ic_set_eye)
        lateinit var help: TextView
        lateinit var folderRow: SettingsRow
        folderRow = page.row("wallpaper.folder", "Pictures folder", RowKind.Action {
            val open = help.visibility != View.VISIBLE
            help.visibility = if (open) View.VISIBLE else View.GONE
            folderRow.setExpanded(open)
        }, subtitle = "How to add your own pictures", iconRes = R.drawable.ic_set_import)
        help = page.body.note(folderNote(folder?.path ?: UserWallpapers.DEVICE_PATH), maxLines = 4)
        help.visibility = View.GONE
        folderRow.setExpanded(false)

        WallpaperLoader.background({ UserWallpapers.list(folder) }) { files ->
            if (!page.isAttachedToWindow) return@background
            userFiles = files
            strip.show(collections(files), store.load().source)
            slideshowRow.alpha = if (files.isNotEmpty() || s.source is WallpaperSource.Slideshow) 1f else 0.6f
        }
        page.doOnDetach { strip.release() }
        return page
    }

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

        fun show(collections: List<Collection>, current: WallpaperSource) {
            chosen = current
            val wanted = collections.flatMap { it.entries }
            for (e in wanted) if (e.key !in tiles) addTile(e)
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
                is WallpaperSource.Slideshow -> return
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
