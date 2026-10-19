package dev.tvlauncher.appsettings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.discover.DiscoverStore
import dev.tvlauncher.home.HomeAppearance
import dev.tvlauncher.homelayout.HomeLayoutPrefs
import dev.tvlauncher.homelayout.HomeLayoutSettings
import dev.tvlauncher.homelayout.HomeStyle
import dev.tvlauncher.homelayout.LabelMode
import dev.tvlauncher.homelayout.LayoutPlan
import dev.tvlauncher.homelayout.LayoutStyle
import dev.tvlauncher.homelayout.ListKind
import dev.tvlauncher.homelayout.PreviewSchematic
import dev.tvlauncher.homelayout.ScreenDp
import dev.tvlauncher.nowplaying.NowPlayingAccess
import dev.tvlauncher.settings.AppearancePrefs
import dev.tvlauncher.settings.TileSize
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/**
 * Settings > Home layout > Layout style: the style, tile size, app names, hero subtitle,
 * quick-strip, "Get more apps" tile and Now Playing card, with a small drawn preview of the home
 * screen beside the rows. Every change is saved at once; the home screen relays itself out when
 * the owner goes back to it. Reset (under Advanced) restores the single row.
 */
object HomeLayoutStylePage {
    const val ID = "home_layout_style"
    val spec = PageSpec(
        ID,
        listOf(
            "layout.style", "layout.tileSize", "layout.labels", "layout.heroSubtitle",
            "layout.quickStrip", "layout.discover", "layout.nowPlaying"
        ),
        listOf("layout.reset")
    )
    const val TITLE = "Layout style"

    /** Plain words under the preview, e.g. "3 rows of 5 tiles per page". */
    fun caption(plan: LayoutPlan): String = when (plan.kind) {
        ListKind.ROW -> "${plan.columns} tiles across"
        ListKind.BANDS -> "2 rows of ${plan.columns} tiles"
        ListKind.GRID -> "${plan.visibleRows} rows of ${plan.columns} tiles per page"
    }

    fun build(context: Context, nav: SettingsNav): View {
        val app = context.applicationContext
        val layoutPrefs = HomeLayoutPrefs(app)
        val appearance = AppearancePrefs(app)
        val discover = DiscoverStore(app)
        val s = layoutPrefs.load()
        val a = appearance.load()
        val m = context.resources.displayMetrics
        val screen = ScreenDp.of(m.widthPixels, m.heightPixels, m.density)
        fun planNow(): LayoutPlan =
            LayoutStyle.plan(layoutPrefs.load(), HomeAppearance.tileScale(appearance.load().rowTileSize), screen)

        val preview = LayoutPreview(context, nav.style.accent, screen)
        val caption = TextView(context).apply {
            style(14f, Theme.regular, Theme.TEXT_2)
            gravity = Gravity.CENTER_HORIZONTAL
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        fun showPlan(animate: Boolean) {
            val p = planNow()
            preview.show(p, if (animate) nav.style.focusMs * 2 else 0L)
            caption.text = caption(p)
        }

        val page = SettingsPage(context, nav.style, spec)
        page.preferredFocus = "layout.style"
        page.row("layout.style", "Style",
            RowKind.Choice(HomeStyle.values().map { it.label }, s.style.ordinal) { i ->
                layoutPrefs.update { it.copy(style = HomeStyle.values()[i]) }
                showPlan(true)
            }, subtitle = "Single row, two rows, grid, big hero or minimal", iconRes = R.drawable.ic_set_home)
        page.row("layout.tileSize", "Tile size",
            RowKind.Choice(listOf("Small", "Medium", "Large"), a.rowTileSize.ordinal) { i ->
                appearance.update { it.copy(rowTileSize = TileSize.values()[i]) }
                showPlan(true)
            }, iconRes = R.drawable.ic_set_tiles)
        page.row("layout.labels", "App names",
            RowKind.Choice(LabelMode.values().map { it.label }, s.labels.ordinal) { i ->
                layoutPrefs.update { it.copy(labels = LabelMode.values()[i]) }
            }, subtitle = "Under the tiles", iconRes = R.drawable.ic_set_eye)
        page.row("layout.heroSubtitle", "Hero subtitle",
            RowKind.Toggle(s.heroSubtitle) { on ->
                layoutPrefs.update { it.copy(heroSubtitle = on) }
                showPlan(true)
            }, subtitle = "The line under the app name", iconRes = R.drawable.ic_set_appearance)
        page.row("layout.quickStrip", "Show quick-strip",
            RowKind.Toggle(s.quickStrip) { on ->
                layoutPrefs.update { it.copy(quickStrip = on) }
                showPlan(true)
            }, subtitle = "Favorite, hide and info under a tile after a pause", iconRes = R.drawable.ic_set_tune)
        page.row("layout.discover", "Show Discover tile",
            RowKind.Toggle(discover.load().showGetMoreAppsTile) { on ->
                discover.save(discover.load().copy(showGetMoreAppsTile = on))
            }, subtitle = "\"Get more apps\" at the end of your apps", iconRes = R.drawable.ic_set_store)
        val granted = try { NowPlayingAccess.isGranted(app) } catch (e: RuntimeException) { false }
        page.row("layout.nowPlaying", "Show Now Playing card",
            RowKind.Toggle(discover.loadNowPlaying().showOnHome) { on ->
                discover.saveNowPlaying(discover.loadNowPlaying().copy(showOnHome = on))
            }, subtitle = if (granted) "What is playing, beside the hero" else "Shows once notification access is allowed",
            iconRes = R.drawable.ic_set_sounds)
        page.advanced(nav, "layout.advanced", "Reset") { block ->
            block.row("layout.reset", "Reset to default", RowKind.Action(chevron = false) {
                layoutPrefs.save(HomeLayoutSettings.DEFAULT)
                appearance.update { it.copy(rowTileSize = TileSize.MEDIUM) }
                discover.save(discover.load().copy(showGetMoreAppsTile = true))
                discover.saveNowPlaying(discover.loadNowPlaying().copy(showOnHome = true))
                nav.notify("Home layout is back to a single row")
                nav.refresh()
            }, subtitle = "Single row, medium tiles, names on focus", iconRes = R.drawable.ic_set_reset)
        }
        showPlan(false)

        val side = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = false
            addView(preview, LinearLayout.LayoutParams(context.dp(PREVIEW_W_DP), context.dp(PREVIEW_W_DP * 9 / 16)))
            addView(caption, LinearLayout.LayoutParams(context.dp(PREVIEW_W_DP), -2).apply { topMargin = context.dp(8) })
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            addView(page, LinearLayout.LayoutParams(0, -1, 1f))
            addView(side, LinearLayout.LayoutParams(-2, -2).apply {
                topMargin = context.dp(10); marginEnd = context.dp(SettingsLayout.PANE_PAD_DP)
            })
        }
    }

    const val PREVIEW_W_DP = 168
}

/**
 * The home screen drawn as blocks ([PreviewSchematic]): the top bar, hero, tiles (the first one in
 * the accent, as if focused) and the quick-strip. Blocks are computed when the choice changes;
 * drawing only scales them (no allocation per frame).
 */
@android.annotation.SuppressLint("ViewConstructor") // built in code only
internal class LayoutPreview(context: Context, private val accent: Int, private val screen: ScreenDp) : View(context) {
    private var blocks: List<PreviewSchematic.Block> = emptyList()
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B0D12.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Theme.STROKE
        strokeWidth = context.dp(1).toFloat()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val radius = context.dp(8).toFloat()
    private val small = context.dp(2).toFloat()

    init {
        isFocusable = false
        contentDescription = "Preview of the home screen"
        auditTag(AuditTags.CONTENT)
    }

    fun show(plan: LayoutPlan, fadeMs: Long) {
        blocks = PreviewSchematic.blocks(plan, screen)
        if (fadeMs > 0) {
            animate().cancel()
            alpha = 0.3f
            animate().alpha(1f).setDuration(fadeMs).start()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, frame)
        for (b in blocks) {
            fill.color = when (b.part) {
                PreviewSchematic.Part.TILE_FOCUSED -> accent
                PreviewSchematic.Part.TILE -> 0x55FFFFFF
                PreviewSchematic.Part.HERO_ART -> 0x88FFFFFF.toInt()
                PreviewSchematic.Part.HERO_TITLE, PreviewSchematic.Part.TOP_CLOCK -> 0xCCFFFFFF.toInt()
                PreviewSchematic.Part.HERO_SUBTITLE -> 0x66FFFFFF
                PreviewSchematic.Part.TOP_PILL, PreviewSchematic.Part.STRIP -> 0x40FFFFFF
            }
            rect.set(b.left * w, b.top * h, b.right * w, b.bottom * h)
            canvas.drawRoundRect(rect, small, small, fill)
        }
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, stroke)
    }
}
