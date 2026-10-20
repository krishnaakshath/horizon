package dev.tvlauncher.homesections

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.home.EffectiveProfile
import dev.tvlauncher.homelayout.ScreenDp
import dev.tvlauncher.ui.ShelfAdapter
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.TileSpec
import dev.tvlauncher.ui.TileView
import dev.tvlauncher.ui.dp

/**
 * The composed home's extra app rows (Favorites, Recent apps, custom groups) above and below the
 * main Apps shelf, which stays the existing [dev.tvlauncher.ui.Shelf] (so Classic is today's home
 * exactly: both slots stay empty and GONE). Rows are horizontal RecyclerViews sharing one
 * [RecyclerView.RecycledViewPool] and the shelf adapter's art cache, so LITE adds no art memory.
 * UP/DOWN between rows follows [SectionNav] (the column is kept).
 */
class SectionStack(
    private val activity: Activity,
    private val profileOf: () -> EffectiveProfile,
    private val shelfAdapter: ShelfAdapter,
    private val host: Host
) {
    interface Host {
        fun focused(app: AppEntry)
        fun launch(app: AppEntry, from: View)
        fun longPress(app: AppEntry)
        /** UP/DOWN leaves the stack toward the main shelf (column given) or past the ends. */
        fun toShelf(column: Int): Boolean
        fun toTopBar(): Boolean
        fun toSpotlight(): Boolean
    }

    val above = slot()
    val below = slot()
    private val pool = RecycledViewPool()
    private var profile: EffectiveProfile = profileOf()
    private var rows: List<Row> = emptyList()
    /** Index of the Apps shelf among the shown rows (-1 when Apps is off). */
    private var appsAt = -1
    var sections: HomeSections = HomeSections.CLASSIC
        private set

    private fun slot() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
        visibility = View.GONE
    }

    private class RecycledViewPool : RecyclerView.RecycledViewPool()

    private inner class Row(val section: HomeSection, var items: List<AppEntry>, val view: View, val list: RecyclerView) {
        val lines = SectionLayout.lines(section)
        var column = 0
    }

    /** Rebuilds the rows for [s] with app lists from [appsFor]; true when anything is shown besides Apps. */
    fun apply(s: HomeSections, appsFor: (HomeSection) -> List<AppEntry>): Boolean {
        sections = s
        profile = profileOf()
        val m = activity.resources.displayMetrics
        val screen = ScreenDp.of(m.widthPixels, m.heightPixels, m.density)
        val shown = if (s.isClassic) emptyList() else SectionRows.shown(s, profile.tileScale, screen, profile.device.effectsMode != dev.tvlauncher.device.EffectsMode.FULL)
        above.removeAllViews()
        below.removeAllViews()
        appsAt = shown.indexOfFirst { it.type == SectionType.APPS }
        val built = ArrayList<Row>()
        shown.forEachIndexed { i, sec ->
            if (sec.type == SectionType.APPS) return@forEachIndexed
            val items = appsFor(sec).take(sec.maxItems)
            val row = build(sec, items)
            built += row
            (if (appsAt in 0 until i) below else above).addView(row.view, LinearLayout.LayoutParams(-1, -2))
        }
        rows = built
        above.visibility = if (above.childCount > 0) View.VISIBLE else View.GONE
        below.visibility = if (below.childCount > 0) View.VISIBLE else View.GONE
        return built.isNotEmpty()
    }

    /** Refreshes the app lists in place (same sections). */
    fun refresh(appsFor: (HomeSection) -> List<AppEntry>) {
        for (r in rows) {
            r.items = appsFor(r.section).take(r.section.maxItems)
            r.list.adapter?.notifyDataSetChanged()
        }
    }

    private fun build(sec: HomeSection, items: List<AppEntry>): Row {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            auditTag(AuditTags.CONTENT)
        }
        val h = activity.dp(Theme.MARGIN_H)
        if (sec.showTitle) {
            box.addView(TextView(activity).apply {
                text = sec.title
                style(15f, Theme.medium, Theme.TEXT_2)
                maxLines = 1
            }, LinearLayout.LayoutParams(-2, activity.dp(SectionLayout.TITLE_DP)).apply { marginStart = h })
        }
        val dims = TileSpec.dims(SectionLayout.cardScale(sec, profile.tileScale))
        val lines = SectionLayout.lines(sec)
        val list = RecyclerView(activity).apply {
            setRecycledViewPool(pool)
            clipToPadding = false
            clipChildren = false
            setPadding(h - activity.dp(TileSpec.PAD_DP), 0, h, 0)
            itemAnimator = null
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            layoutManager = if (lines > 1) GridLayoutManager(activity, lines, GridLayoutManager.HORIZONTAL, false)
            else LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        }
        val row = Row(sec, items, box, list)
        list.adapter = Adapter(row, dims)
        box.addView(list, LinearLayout.LayoutParams(-1, lines * activity.dp(dims.viewHeightDp)))
        return row
    }

    private class Holder(val tile: TileView) : RecyclerView.ViewHolder(tile)

    private inner class Adapter(val row: Row, val dims: dev.tvlauncher.ui.TileDims) : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = row.items.size
        /** Card sizes differ per section: one view type per size keeps the shared pool safe. */
        override fun getItemViewType(position: Int) = dims.widthDp
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val t = TileView(parent.context, dims)
            t.layoutParams = RecyclerView.LayoutParams(parent.context.dp(dims.viewWidthDp), parent.context.dp(dims.viewHeightDp))
            return Holder(t)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val app = row.items.getOrNull(position) ?: return
            val tile = holder.tile
            tile.bind(app, shelfAdapter.sharedArt(app), profile, shelfAdapter.accentOf(app))
            tile.setOnClickListener { host.launch(app, tile) }
            tile.setOnLongClickListener { host.longPress(app); true }
            tile.setOnFocusChangeListener { _, has ->
                val p = holder.bindingAdapterPosition
                if (has && p != RecyclerView.NO_POSITION) {
                    row.column = p / row.lines
                    row.items.getOrNull(p)?.let(host::focused)
                }
            }
            tile.setOnKeyListener { _, code, ev -> ev.action == KeyEvent.ACTION_DOWN && key(row, holder.bindingAdapterPosition, code) }
        }
    }

    /** The shown rows top to bottom with the Apps shelf as null. */
    private fun order(): List<Row?> {
        val out = ArrayList<Row?>(rows)
        if (appsAt >= 0) out.add(appsAt.coerceAtMost(out.size), null)
        return out
    }

    private fun key(row: Row, pos: Int, code: Int): Boolean {
        if (pos == RecyclerView.NO_POSITION) return false
        val line = pos % row.lines
        val k = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> if (line > 0) return focusIn(row, pos - 1) else SectionNav.Key.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> if (line < row.lines - 1 && pos + 1 < row.items.size) return focusIn(row, pos + 1) else SectionNav.Key.DOWN
            else -> return false
        }
        val order = order()
        return go(order, order.indexOf(row), pos / row.lines, k)
    }

    private fun go(order: List<Row?>, from: Int, column: Int, k: SectionNav.Key): Boolean {
        val counts = order.map { r -> if (r == null) Int.MAX_VALUE else (r.items.size + r.lines - 1) / r.lines }
        return when (val mv = SectionNav.move(counts, from, column, k)) {
            is SectionMove.Focus -> {
                val target = order[mv.row]
                if (target == null) host.toShelf(mv.column)
                else focusIn(target, mv.column * target.lines + if (k == SectionNav.Key.UP) target.lines - 1 else 0)
            }
            SectionMove.TopBar -> host.toTopBar()
            SectionMove.Spotlight -> host.toSpotlight()
            SectionMove.Pass -> false
        }
    }

    /** UP (or DOWN) from the main shelf at [column]: the row above (below) it; false = none, default handling. */
    fun fromShelf(column: Int, up: Boolean): Boolean {
        if (appsAt < 0 || rows.isEmpty()) return false
        val order = order()
        val counts = order.map { r -> if (r == null) Int.MAX_VALUE else (r.items.size + r.lines - 1) / r.lines }
        val mv = SectionNav.move(counts, appsAt, column, if (up) SectionNav.Key.UP else SectionNav.Key.DOWN)
        if (mv !is SectionMove.Focus) return false
        val target = order[mv.row] ?: return false
        return focusIn(target, mv.column * target.lines + if (up) target.lines - 1 else 0)
    }

    /** Focus for a home without the Apps shelf: the first row's remembered column. */
    fun focusFirst(): Boolean = rows.firstOrNull()?.let { focusIn(it, it.column * it.lines) } ?: false

    val hasRows: Boolean get() = rows.isNotEmpty()

    private fun focusIn(row: Row, pos: Int): Boolean {
        if (row.items.isEmpty()) return false
        val p = pos.coerceIn(0, row.items.size - 1)
        val v = row.list.findViewHolderForAdapterPosition(p)?.itemView
        if (v != null && v.requestFocus()) return true
        row.list.scrollToPosition(p)
        row.list.post { row.list.findViewHolderForAdapterPosition(p)?.itemView?.requestFocus() }
        return true
    }
}
