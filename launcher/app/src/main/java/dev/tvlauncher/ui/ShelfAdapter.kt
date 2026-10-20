package dev.tvlauncher.ui

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.discover.DiscoverTileView
import dev.tvlauncher.home.EffectiveProfile
import dev.tvlauncher.homelayout.LabelMode
import dev.tvlauncher.homelayout.LayoutPlan
import dev.tvlauncher.homelayout.ListKind
import dev.tvlauncher.homelayout.ShelfGeometry

class ShelfAdapter(
    private val pm: PackageManager,
    private val profile: EffectiveProfile,
    private val onFocused: (Int, AppEntry, View) -> Unit,
    private val onClick: (AppEntry, View) -> Unit,
    private val onLongPress: (AppEntry) -> Unit,
    private val onDown: () -> Boolean,
    /** Any other D-pad press on a tile (restarts the quick-strip dwell). */
    private val onDpad: () -> Unit,
    /** OK on the end-of-shelf "Get more apps" tile. */
    private val onFooterClick: () -> Unit = {},
    /** The footer tile gained focus. */
    private val onFooterFocused: () -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    /**
     * D-pad / page keys on a tile or the footer at an adapter position, before the default handling;
     * true = the layout moved focus itself (two rows and grid, see [dev.tvlauncher.homelayout.ShelfNav]).
     */
    var onNavKey: (position: Int, keyCode: Int) -> Boolean = { _, _ -> false }

    class Holder(val tile: TileView) : RecyclerView.ViewHolder(tile)
    private class FooterHolder(view: DiscoverTileView) : RecyclerView.ViewHolder(view)

    private class EmptyHolder(view: View) : RecyclerView.ViewHolder(view)

    private var items: List<AppEntry> = emptyList()
    /** Card size: the user's tile size times the layout style's scale ([configure]). */
    var dims = TileSpec.dims(profile.tileScale)
        private set
    private var labelMode = LabelMode.FOCUS
    private var kind = ListKind.ROW
    private var lanes = 1
    private var pageRows = 1
    private var pageColumns = 1
    private var favorites = 0
    private val artCache = LruCache<String, TileArt<Drawable>>(
        if (profile.device.effectsMode == EffectsMode.FULL) 96 else 40
    )
    private val accents = BrandColors.Cache()

    init {
        // Speed cleanup drops cached tile art while home is in the background (tiles reload on demand).
        dev.tvlauncher.speed.OwnCaches.register(this) { it.artCache.evictAll() }
    }

    private var sampler: ArtSampler? = null
    private var bgSampler: ArtSampler? = null

    /** Index (into [getItems]) of the most recently focused app, used to return from the quick-strip and Spotlight. */
    var lastFocused = 0
        private set

    fun getItems(): List<AppEntry> = items

    /** Whether the "Get more apps" tile follows the last app ([ShelfFooter]). */
    var footer = false
        private set

    /** Adapter positions for the current layout (apps, footer, padding slots) and its D-pad rows. */
    var geometry: ShelfGeometry = ShelfGeometry.build(ListKind.ROW, 0, 0, false, 1, 1, 1)
        private set

    /** App tiles only (the footer is not an app). */
    val appCount: Int get() = items.size

    /** Adapter position of the footer tile, or -1. */
    val footerPosition: Int get() = geometry.footerPosition

    /** Adapter position of app [index], or -1. */
    fun positionOf(index: Int): Int = geometry.positionOf(index)

    /**
     * The layout style's arrangement. Returns true when the card size changed: the caller must then
     * drop recycled views (they were built for the old size) before the list is laid out again.
     */
    @android.annotation.SuppressLint("NotifyDataSetChanged") // a new arrangement: every slot can change
    fun configure(plan: LayoutPlan): Boolean {
        val resized = plan.dims != dims
        dims = plan.dims
        labelMode = plan.labels
        kind = plan.kind
        lanes = plan.lanes
        pageRows = plan.visibleRows
        pageColumns = plan.columns
        rebuildGeometry()
        notifyDataSetChanged()
        return resized
    }

    /** [favoriteCount]: how many leading apps of [list] are favorites (the top row of "Two rows"). */
    fun submit(list: List<AppEntry>, showFooter: Boolean = footer, favoriteCount: Int = favorites) {
        items = list
        footer = ShelfFooter.show(showFooter, list.size)
        favorites = favoriteCount.coerceIn(0, list.size)
        lastFocused = lastFocused.coerceIn(0, maxOf(0, list.size - 1))
        rebuildGeometry()
        notifyDataSetChanged()
    }

    private fun rebuildGeometry() {
        geometry = ShelfGeometry.build(kind, items.size, favorites, footer, lanes, pageRows, pageColumns)
    }

    /** Brand accent for [app]; resolves art and samples it on first use (main thread). */
    fun accentOf(app: AppEntry): Int = accents.get(app.packageName) {
        val s = sampler ?: ArtSampler().also { sampler = it }
        s.pixels(artOf(app))
    }

    /**
     * Loader thread only: resolves art and samples accents for the first [count] apps so the
     * first bind on the main thread does no drawable loading or pixel work.
     */
    fun preload(apps: List<AppEntry>, count: Int) {
        val s = bgSampler ?: ArtSampler().also { bgSampler = it }
        for (app in apps.take(count)) {
            val art = artOf(app)
            accents.get(app.packageName) { s.pixels(art) }
        }
    }

    /** The cached art of [app] for another list (Home sections rows share this cache). */
    fun sharedArt(app: AppEntry): TileArt<Drawable> = artOf(app)

    /** Fresh drawables over the cached art (same bitmaps), for a second view such as the big hero. */
    fun artCopy(app: AppEntry, res: android.content.res.Resources): TileArt<Drawable> = when (val a = artOf(app)) {
        is TileArt.Banner -> TileArt.Banner(copy(a.d, res))
        is TileArt.Adaptive -> TileArt.Adaptive(copy(a.bg, res), copy(a.fg, res))
        is TileArt.Legacy -> TileArt.Legacy(copy(a.d, res))
        is TileArt.Letter -> a
    }

    private fun copy(d: Drawable, res: android.content.res.Resources): Drawable = d.constantState?.newDrawable(res) ?: d

    private fun artOf(app: AppEntry): TileArt<Drawable> = artCache.get(app.packageName)
        ?: TileArtResolver.resolve(pm, app).also { artCache.put(app.packageName, it) }

    override fun getItemCount() = geometry.itemCount

    override fun getItemViewType(position: Int): Int = when (geometry.slotAt(position)) {
        ShelfGeometry.FOOTER -> TYPE_FOOTER
        ShelfGeometry.EMPTY -> TYPE_EMPTY
        else -> TYPE_APP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val lp = RecyclerView.LayoutParams(parent.context.dp(dims.viewWidthDp), parent.context.dp(dims.viewHeightDp))
        if (viewType == TYPE_EMPTY) {
            // The shorter of the two rows is padded so columns line up; nothing to draw or focus.
            return EmptyHolder(View(parent.context).apply { layoutParams = lp; isFocusable = false })
        }
        if (viewType == TYPE_FOOTER) {
            lateinit var holder: FooterHolder
            val v = DiscoverTileView(parent.context, dims) { onFooterClick() }
            v.layoutParams = lp
            v.setOnFocusChangeListener { _, has -> if (has) onFooterFocused() }
            v.setOnKeyListener { _, code, ev ->
                ev.action == KeyEvent.ACTION_DOWN && (onNavKey(holder.bindingAdapterPosition, code) ||
                    (code == KeyEvent.KEYCODE_DPAD_DOWN && onDown()))
            }
            holder = FooterHolder(v)
            return holder
        }
        val tile = TileView(parent.context, dims)
        tile.layoutParams = lp
        return Holder(tile)
    }

    override fun onBindViewHolder(vh: RecyclerView.ViewHolder, position: Int) {
        (vh.itemView as? DiscoverTileView)?.labelMode = labelMode
        val holder = vh as? Holder ?: return // the footer and padding slots have nothing to bind
        val index = geometry.slotAt(position)
        val app = items.getOrNull(index) ?: return
        val art = artOf(app)
        holder.tile.labelMode = labelMode
        holder.tile.bind(app, art, profile, accentOf(app))
        holder.tile.setOnClickListener { onClick(app, holder.tile) }
        // Covers touch long-press and DPAD_CENTER long-press (framework maps it to a long click).
        holder.tile.setOnLongClickListener { onLongPress(app); true }
        holder.tile.setOnFocusChangeListener { _, has ->
            val pos = holder.bindingAdapterPosition
            val i = geometry.slotAt(pos)
            if (has && pos != RecyclerView.NO_POSITION && i in items.indices) {
                lastFocused = i
                onFocused(pos, items[i], holder.tile)
            }
        }
        holder.tile.setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (onNavKey(holder.bindingAdapterPosition, code)) return@setOnKeyListener true
            when (code) {
                KeyEvent.KEYCODE_DPAD_DOWN -> onDown()
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP -> { onDpad(); false }
                else -> false
            }
        }
    }

    companion object {
        /** Tiles whose art/accent are prepared off the main thread before the first bind. */
        const val PRELOAD = 8
        private const val TYPE_APP = 0
        private const val TYPE_FOOTER = 1
        private const val TYPE_EMPTY = 2
    }
}
