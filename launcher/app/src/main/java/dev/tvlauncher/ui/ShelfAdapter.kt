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
    class Holder(val tile: TileView) : RecyclerView.ViewHolder(tile)
    private class FooterHolder(view: DiscoverTileView) : RecyclerView.ViewHolder(view)

    private var items: List<AppEntry> = emptyList()
    /** Card size for the user's tile-size setting. */
    val dims = TileSpec.dims(profile.tileScale)
    private val artCache = LruCache<String, TileArt<Drawable>>(
        if (profile.device.effectsMode == EffectsMode.FULL) 96 else 40
    )
    private val accents = BrandColors.Cache()
    private var sampler: ArtSampler? = null
    private var bgSampler: ArtSampler? = null

    /** Position of the most recently focused tile, used to return from the quick-strip and Spotlight. */
    var lastFocused = 0
        private set

    fun getItems(): List<AppEntry> = items

    /** Whether the "Get more apps" tile follows the last app ([ShelfFooter]). */
    var footer = false
        private set

    /** App tiles only (the footer is not an app). */
    val appCount: Int get() = items.size

    /** Adapter position of the footer tile, or -1. */
    val footerPosition: Int get() = ShelfFooter.position(items.size, footer)

    fun submit(list: List<AppEntry>, showFooter: Boolean = footer) {
        items = list
        footer = ShelfFooter.show(showFooter, list.size)
        lastFocused = lastFocused.coerceIn(0, maxOf(0, list.size - 1))
        notifyDataSetChanged()
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

    private fun artOf(app: AppEntry): TileArt<Drawable> = artCache.get(app.packageName)
        ?: TileArtResolver.resolve(pm, app).also { artCache.put(app.packageName, it) }

    override fun getItemCount() = ShelfFooter.itemCount(items.size, footer)

    override fun getItemViewType(position: Int): Int =
        if (ShelfFooter.isFooter(position, items.size, footer)) TYPE_FOOTER else TYPE_APP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val lp = RecyclerView.LayoutParams(parent.context.dp(dims.viewWidthDp), parent.context.dp(dims.viewHeightDp))
        if (viewType == TYPE_FOOTER) {
            val v = DiscoverTileView(parent.context, dims) { onFooterClick() }
            v.layoutParams = lp
            v.setOnFocusChangeListener { _, has -> if (has) onFooterFocused() }
            v.setOnKeyListener { _, code, ev ->
                ev.action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN && onDown()
            }
            return FooterHolder(v)
        }
        val tile = TileView(parent.context, dims)
        tile.layoutParams = lp
        return Holder(tile)
    }

    override fun onBindViewHolder(vh: RecyclerView.ViewHolder, position: Int) {
        val holder = vh as? Holder ?: return // the footer has nothing to bind
        val app = items[position]
        val art = artOf(app)
        holder.tile.bind(app, art, profile, accentOf(app))
        holder.tile.setOnClickListener { onClick(app, holder.tile) }
        // Covers touch long-press and DPAD_CENTER long-press (framework maps it to a long click).
        holder.tile.setOnLongClickListener { onLongPress(app); true }
        holder.tile.setOnFocusChangeListener { _, has ->
            val pos = holder.bindingAdapterPosition
            if (has && pos != RecyclerView.NO_POSITION && pos < items.size) {
                lastFocused = pos
                onFocused(pos, items[pos], holder.tile)
            }
        }
        holder.tile.setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
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
    }
}
