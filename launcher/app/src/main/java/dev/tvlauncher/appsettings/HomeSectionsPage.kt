package dev.tvlauncher.appsettings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import dev.tvlauncher.R
import dev.tvlauncher.apps.AppRepository
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.homesections.HomeSection
import dev.tvlauncher.homesections.HomeSections
import dev.tvlauncher.homesections.HomeSectionsPrefs
import dev.tvlauncher.homesections.SectionPreset
import dev.tvlauncher.homesections.SectionPreview
import dev.tvlauncher.homesections.SectionSize
import dev.tvlauncher.homesections.SectionStyle
import dev.tvlauncher.homesections.SectionType
import dev.tvlauncher.system.TextEntrySheet
import dev.tvlauncher.system.contentRoot
import dev.tvlauncher.system.lite
import dev.tvlauncher.ui.HorizonLoader
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.dp

/**
 * Settings > Home layout > Home sections: a preset, the list of sections (OK shows or hides one,
 * RIGHT opens its options, Move mode reorders), custom groups and a reset to Classic, with a drawn
 * preview of the stacked home beside the rows. Every change is saved at once; the home screen
 * rebuilds its rows when the owner goes back to it.
 */
object HomeSectionsPage {
    const val TITLE = "Home sections"
    val spec = PageSpec(
        "home_sections",
        listOf("sections.preset", "sections.list", "sections.add"),
        listOf("sections.reset")
    )
    val detailSpec = PageSpec(
        "home_sections_detail",
        listOf("section.show", "section.style", "section.size", "section.title", "section.max", "section.apps", "section.delete")
    )

    /** "Classic", or "Custom" when the sections match no preset. */
    fun presetLabel(s: HomeSections): String = s.matchingPreset()?.label ?: "Custom"

    /** The value beside a section row, e.g. "Row, Medium". */
    fun summary(s: HomeSection): String = "${s.style.label}, ${s.size.label}"

    fun build(context: Context, nav: SettingsNav): View {
        val prefs = HomeSectionsPrefs(context.applicationContext)
        val s = prefs.load()
        val preview = SectionsPreview(context, nav.style.accent)
        val page = SettingsPage(context, nav.style, spec)
        page.preferredFocus = "sections.preset"
        val presets = SectionPreset.values()
        val current = s.matchingPreset()
        val options = presets.map { it.label } + if (current == null) listOf("Custom") else emptyList()
        page.row("sections.preset", "Preset",
            RowKind.Choice(options, current?.ordinal ?: presets.size) { i ->
                if (i < presets.size) {
                    prefs.save(HomeSections.preset(presets[i], prefs.load().groups))
                    preview.show(prefs.load())
                }
            }, subtitle = "Classic is the single row of apps", iconRes = R.drawable.ic_set_home)
        page.row("sections.list", "Sections", RowKind.Action("${s.enabled.size} shown") {
            nav.push("Sections") { c, n -> listPage(c, n) }
        }, subtitle = "Show, hide, order and style each one", iconRes = R.drawable.ic_set_tiles)
        page.row("sections.add", "Add group", RowKind.Action(if (s.groups.size >= HomeSections.MAX_GROUPS) "4 of 4" else null) {
            addGroup(context, nav)
        }, subtitle = "Your own named row of apps, up to 4", iconRes = R.drawable.ic_set_star)
        page.advanced(nav, "sections.advanced", "Reset") { block ->
            block.row("sections.reset", "Reset to Classic", RowKind.Action(chevron = false) {
                prefs.save(HomeSections.preset(SectionPreset.CLASSIC, prefs.load().groups))
                nav.notify("Home is back to a single row of apps")
                nav.refresh()
            }, subtitle = "Your groups are kept, turned off", iconRes = R.drawable.ic_set_reset)
        }
        preview.show(s)
        return withPreview(context, page, preview)
    }

    private fun withPreview(context: Context, page: View, preview: View): View {
        val side = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = false
            addView(preview, LinearLayout.LayoutParams(context.dp(HomeLayoutStylePage.PREVIEW_W_DP), context.dp(HomeLayoutStylePage.PREVIEW_W_DP * 9 / 16)))
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

    /** Move mode: the section being carried (null = none), kept while Settings is open. */
    private var moving: String? = null
    private var moveMode = false

    /** Every section, top to bottom: OK shows or hides, RIGHT opens options; in Move mode OK picks and drops. */
    private fun listPage(context: Context, nav: SettingsNav): View {
        val prefs = HomeSectionsPrefs(context.applicationContext)
        val s = prefs.load()
        val preview = SectionsPreview(context, nav.style.accent)
        val page = SettingsPage(context, nav.style)
        page.row("sections.move", "Move sections", RowKind.Toggle(moveMode) { on ->
            moveMode = on
            moving = null
            nav.refresh()
        }, subtitle = if (moveMode) "OK picks a section, up and down move it, OK drops it" else "Change the order", iconRes = R.drawable.ic_set_tune)
        for (sec in s.sections.filter { it.type.drawn }) {
            val carried = moving == sec.id
            val row = page.row("sections.${sec.id}", sec.title,
                if (moveMode) RowKind.Action(if (carried) "Moving" else null, chevron = false) {
                    moving = if (carried) null else sec.id
                    nav.refresh()
                }
                else RowKind.Toggle(sec.enabled) { on ->
                    prefs.update { it.setEnabled(sec.id, on) }
                    preview.show(prefs.load())
                },
                subtitle = when {
                    carried -> "Up and down to move, OK to drop"
                    moveMode -> if (sec.enabled) "Shown" else "Hidden"
                    sec.type == SectionType.APPS -> "Its look is set in Layout style"
                    else -> summary(sec) + ". Right for options"
                },
                iconRes = iconOf(sec.type))
            row.setOnKeyListener { _, code, ev ->
                if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when {
                    carried && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) -> {
                        prefs.update { it.move(sec.id, if (code == KeyEvent.KEYCODE_DPAD_UP) -1 else 1) }
                        nav.refresh()
                        true
                    }
                    !moveMode && code == KeyEvent.KEYCODE_DPAD_RIGHT && sec.type != SectionType.APPS -> {
                        nav.push(sec.title) { c, n -> detailPage(c, n, sec.id) }
                        true
                    }
                    else -> false
                }
            }
        }
        preview.show(s)
        return withPreview(context, page, preview)
    }

    private fun iconOf(t: SectionType): Int = when (t) {
        SectionType.APPS -> R.drawable.ic_set_home
        SectionType.FAVORITES, SectionType.GROUP -> R.drawable.ic_set_star
        SectionType.RECENT, SectionType.CLOCK -> R.drawable.ic_set_clock
        SectionType.CONTINUE, SectionType.NOW_PLAYING -> R.drawable.ic_set_motion
        SectionType.WEATHER -> R.drawable.ic_set_weather
        SectionType.SMART_HOME -> R.drawable.ic_set_devices
        SectionType.QUICK_ACTIONS -> R.drawable.ic_set_tune
        SectionType.DISCOVER -> R.drawable.ic_set_store
    }

    /** One section's options; a group also picks its apps and can be deleted (OK twice). */
    private fun detailPage(context: Context, nav: SettingsNav, id: String): View {
        val prefs = HomeSectionsPrefs(context.applicationContext)
        val sec = prefs.load().byId(id) ?: return SettingsPage(context, nav.style)
        val preview = SectionsPreview(context, nav.style.accent)
        val page = SettingsPage(context, nav.style, detailSpec)
        fun save(f: (HomeSections) -> HomeSections) { prefs.update(f); preview.show(prefs.load()) }
        page.row("section.show", "Show on home", RowKind.Toggle(sec.enabled) { on -> save { it.setEnabled(id, on) } },
            iconRes = R.drawable.ic_set_eye)
        page.row("section.style", "Style", RowKind.Choice(SectionStyle.values().map { it.label }, sec.style.ordinal) { i ->
            save { it.setStyle(id, SectionStyle.values()[i]) }
        }, subtitle = "Row, two rows, grid, big cards or compact icons", iconRes = R.drawable.ic_set_home)
        page.row("section.size", "Tile size", RowKind.Choice(SectionSize.values().map { it.label }, sec.size.ordinal) { i ->
            save { it.setSize(id, SectionSize.values()[i]) }
        }, iconRes = R.drawable.ic_set_tiles)
        page.row("section.title", "Show title", RowKind.Toggle(sec.showTitle) { on -> save { it.setShowTitle(id, on) } },
            subtitle = "The name above the section", iconRes = R.drawable.ic_set_appearance)
        page.row("section.max", "Most items", RowKind.Stepper(sec.maxItems, HomeSection.MIN_MAX, HomeSection.MAX_MAX, { it.toString() }) { n ->
            save { it.setMaxItems(id, n) }
        }, iconRes = R.drawable.ic_set_tune)
        if (sec.type == SectionType.GROUP) {
            page.row("section.apps", "Apps in this group", RowKind.Action("${sec.apps.size}") {
                nav.push(sec.title) { c, n -> appPicker(c, n, id) }
            }, iconRes = R.drawable.ic_set_star)
            var armed = false
            lateinit var del: SettingsRow
            del = page.row("section.delete", "Delete group", RowKind.Action(chevron = false) {
                if (!armed) {
                    armed = true
                    del.setValue("OK again to delete")
                } else {
                    prefs.update { it.deleteGroup(id) }
                    nav.notify("${sec.title} deleted")
                    nav.pop()
                }
            }, iconRes = R.drawable.ic_set_reset)
            del.setOnFocusChangeListener { _, has -> if (!has && armed) { armed = false; del.setValue(null) } }
        }
        preview.show(prefs.load())
        return withPreview(context, page, preview)
    }

    /** Installed apps with OK toggles for group [id]. */
    private fun appPicker(context: Context, nav: SettingsNav, id: String): View {
        val page = SettingsPage(context, nav.style)
        val dots = HorizonLoader(context)
        page.column.addView(dots, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(24); marginStart = context.dp(8) })
        val prefs = HomeSectionsPrefs(context.applicationContext)
        nav.background({ AppRepository(context.applicationContext).load() }) { apps ->
            page.column.removeView(dots)
            val chosen = prefs.load().byId(id)?.apps.orEmpty()
            for (app in apps.sortedWith(compareBy({ it.packageName !in chosen }, { it.label.lowercase() }))) {
                page.row("pick.${app.packageName}", app.label, RowKind.Toggle(app.packageName in chosen) { _ ->
                    prefs.update { it.toggleGroupApp(id, app.packageName) }
                }, iconRes = R.drawable.ic_set_tiles)
            }
            nav.contentReady()
        }
        return page
    }

    private fun addGroup(context: Context, nav: SettingsNav) {
        val prefs = HomeSectionsPrefs(context.applicationContext)
        if (prefs.load().groups.size >= HomeSections.MAX_GROUPS) { nav.notify("You have 4 groups. Delete one to add another"); return }
        val host = contentRoot(context) ?: return
        val sheet = TextEntrySheet(context, nav.lite())
        sheet.show(host, TextEntrySheet.Request("New group", "A name for this row of apps", TextEntrySheet.Kind.TEXT, "Add",
            hint = "Name", icon = R.drawable.ic_set_star) { name ->
            val next = prefs.load().addGroup(name) ?: return@Request "Type a name"
            prefs.save(next)
            sheet.dismiss()
            val g = next.groups.last()
            nav.notify("Added ${g.name}. Pick its apps")
            nav.push(g.name) { c, n -> appPicker(c, n, g.id) }
            null
        })
    }
}

/** The stacked home drawn as blocks ([SectionPreview]); blocks are computed on change, drawing only scales them. */
@android.annotation.SuppressLint("ViewConstructor") // built in code only
internal class SectionsPreview(context: Context, private val accent: Int) : View(context) {
    private var blocks: List<SectionPreview.Block> = emptyList()
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

    fun show(s: HomeSections) {
        blocks = SectionPreview.blocks(s)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, frame)
        canvas.save()
        canvas.clipRect(0f, 0f, w, h)
        for (b in blocks) {
            fill.color = when (b.part) {
                SectionPreview.Part.TILE_FOCUSED -> accent
                SectionPreview.Part.TILE -> 0x55FFFFFF
                SectionPreview.Part.CARD -> 0x40FFFFFF
                SectionPreview.Part.HERO -> 0xCCFFFFFF.toInt()
                SectionPreview.Part.TITLE -> 0x66FFFFFF
                SectionPreview.Part.TOP_BAR, SectionPreview.Part.FOLD -> 0x40FFFFFF
            }
            if (b.faded) fill.alpha = fill.alpha / 3
            rect.set(b.left * w, b.top * h, b.right * w, b.bottom * h)
            canvas.drawRoundRect(rect, small, small, fill)
        }
        canvas.restore()
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, stroke)
    }
}
