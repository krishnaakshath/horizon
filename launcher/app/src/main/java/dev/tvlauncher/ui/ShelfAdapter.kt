package dev.tvlauncher.ui

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.device.EffectsMode

class ShelfAdapter(
    private val pm: PackageManager,
    private val profile: DeviceProfile,
    private val onFocused: (Int, AppEntry) -> Unit,
    private val onClick: (AppEntry, View) -> Unit,
    private val onLongPress: (AppEntry) -> Unit,
    private val onDown: () -> Boolean
) : RecyclerView.Adapter<ShelfAdapter.Holder>() {
    class Holder(val tile: TileView) : RecyclerView.ViewHolder(tile)

    private var items: List<AppEntry> = emptyList()
    private val artCache = LruCache<String, TileArt<Drawable>>(
        if (profile.effectsMode == EffectsMode.FULL) 96 else 40
    )

    /** Position of the most recently focused tile, used to return from the dock. */
    var lastFocused = 0
        private set

    fun getItems(): List<AppEntry> = items

    fun submit(list: List<AppEntry>) {
        items = list
        lastFocused = lastFocused.coerceIn(0, maxOf(0, list.size - 1))
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val tile = TileView(parent.context)
        tile.layoutParams = RecyclerView.LayoutParams(
            parent.context.dp(TileSpec.WIDTH_DP), parent.context.dp(TileSpec.HEIGHT_DP)
        )
        return Holder(tile)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = items[position]
        val art = artCache.get(app.packageName)
            ?: TileArtResolver.resolve(pm, app).also { artCache.put(app.packageName, it) }
        holder.tile.bind(app, art, profile)
        holder.tile.setOnClickListener { onClick(app, holder.tile) }
        // Covers touch long-press and DPAD_CENTER long-press (framework maps it to a long click).
        holder.tile.setOnLongClickListener { onLongPress(app); true }
        holder.tile.setOnFocusChangeListener { _, has ->
            val pos = holder.bindingAdapterPosition
            if (has && pos != RecyclerView.NO_POSITION) {
                lastFocused = pos
                onFocused(pos, items[pos])
            }
        }
        holder.tile.setOnKeyListener { _, code, ev ->
            code == KeyEvent.KEYCODE_DPAD_DOWN && ev.action == KeyEvent.ACTION_DOWN && onDown()
        }
    }
}
