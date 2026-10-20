package dev.tvlauncher.look

import android.app.Activity
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.settings.AccentMode
import dev.tvlauncher.settings.AppearancePrefs
import dev.tvlauncher.settings.AppearanceSettings
import dev.tvlauncher.settings.GlassStyle
import dev.tvlauncher.system.A11yModel
import dev.tvlauncher.system.TextEntrySheet
import dev.tvlauncher.ui.BrandColors
import dev.tvlauncher.ui.Glass
import dev.tvlauncher.ui.GlassBackdrop
import dev.tvlauncher.ui.GlassTokens
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp

/**
 * Settings > Appearance > Look: glass style and amount, corners, tint, accent, text size and tile
 * icons, with a live preview card; plus ready-made looks, the user's own looks and look files.
 * Glass changes repaint every glass surface at once ([GlassBackdrop.setLook]).
 */
object LookPage {
    const val TITLE = "Look"
    val SPEC = PageSpec(
        "look",
        listOf("look.presets", "look.glass", "look.amount", "look.corners", "look.tint", "look.accent", "look.text"),
        listOf("look.icons", "look.mine", "look.export", "look.import")
    )

    fun build(context: Context, nav: SettingsNav): View {
        val store = LookStore(context)
        val look = store.load()
        val full = nav.profile.effectsMode == EffectsMode.FULL
        val page = SettingsPage(context, nav.style, SPEC)
        page.add(preview(context, full))

        page.row("look.presets", "Looks", RowKind.Action(LookPresets.ALL.firstOrNull { matches(it.look, look) }?.name) {
            nav.push("Looks") { c, n -> presets(c, n) }
        }, subtitle = "Horizon, Clear, Cinema, Bright room and Classic", iconRes = R.drawable.ic_set_palette)

        val styles = GlassStyle.values().toList()
        val glass = GlassStyle.resolve(look.glassStyle, full)
        page.row("look.glass", "Glass style", RowKind.Choice(styles.map { it.label }, styles.indexOf(glass)) { i ->
            store.update { it.copy(glassStyle = styles[i]) }
            GlassBackdrop.setStyle(styles[i])
        }, subtitle = "Solid is easiest to read", iconRes = R.drawable.ic_set_appearance)

        page.row("look.amount", "Glass amount", RowKind.Stepper(LookCodec.snap(look.amount) / Look.STEP, 0, 100 / Look.STEP,
            { "${it * Look.STEP}%" }, bar = true) { v ->
            store.update { it.copy(amount = v * Look.STEP) }
            store.applyGlass()
        }, subtitle = if (full) "How much the backdrop shows through" else "How much the backdrop shows through. This TV uses light glass without blur.",
            iconRes = R.drawable.ic_set_tune)

        val corners = Corners.values().toList()
        page.row("look.corners", "Corners", RowKind.Choice(corners.map { it.label }, corners.indexOf(look.corners)) { i ->
            store.update { it.copy(corners = corners[i]) }
            store.applyGlass()
            nav.appearanceChanged()
        }, iconRes = R.drawable.ic_set_tiles)

        val tints = LookTint.values().toList()
        page.row("look.tint", "Tint", RowKind.Choice(tints.map { it.label }, tints.indexOf(look.tint)) { i ->
            store.update { it.copy(tint = tints[i]) }
            store.applyGlass()
        }, subtitle = "The colour of the glass", iconRes = R.drawable.ic_set_palette)

        page.row("look.accent", "Accent colour", RowKind.Action(if (look.accent == null) "From the app" else null) {
            nav.push("Accent colour") { c, n -> accent(c, n) }
        }, iconRes = R.drawable.ic_set_palette)

        val sizes = A11yModel.TextSize.values()
        val cur = A11yModel.textSizeOf(look.textSize)
        page.row("look.text", "Text size", RowKind.Choice(sizes.map { it.label }, sizes.indexOf(cur)) { i ->
            store.update { it.copy(textSize = sizes[i].name) }
            (context as? Activity)?.let { a -> a.window.decorView.postDelayed({ a.recreate() }, 250) }
        }, iconRes = R.drawable.ic_sys_access)

        page.advanced(nav, "look.advanced", "Tile icons, your looks and look files") { adv ->
            val icons = IconStyle.values().toList()
            adv.row("look.icons", "Icons on tiles", RowKind.Choice(icons.map { it.label }, icons.indexOf(look.iconStyle)) { i ->
                store.update { it.copy(iconStyle = icons[i]) }
            }, subtitle = "Apps without a banner", iconRes = R.drawable.ic_set_tiles)
            adv.row("look.mine", "Your looks", RowKind.Action("${store.saved().size} of ${LookCodec.MAX_SAVED}") {
                nav.push("Your looks") { c, n -> mine(c, n) }
            }, subtitle = "Save this look, use or delete a saved one", iconRes = R.drawable.ic_set_appearance)
            adv.row("look.export", "Copy this look to a file", RowKind.Action(chevron = false) {
                val l = store.load()
                nav.task({ LookFiles.export(context, "My look", l) }) { f ->
                    nav.notify(if (f != null) "Saved to ${f.parentFile?.name ?: LookStore.FOLDER}" else "No place to save the look")
                }
            }, subtitle = "To a USB stick or Pictures/${LookStore.FOLDER}", iconRes = R.drawable.ic_set_tune)
            adv.row("look.import", "Use a look file", RowKind.Action {
                nav.push("Look files") { c, n -> files(c, n) }
            }, subtitle = "From a USB stick or Pictures/${LookStore.FOLDER}", iconRes = R.drawable.ic_set_tune)
        }
        return page
    }

    /** True when [current] already shows preset [p] (glass and shape). */
    fun matches(p: Look, current: Look): Boolean =
        p.amount == current.amount && p.corners == current.corners && p.tint == current.tint &&
            (p.glassStyle == null || p.glassStyle == current.glassStyle) && p.iconStyle == current.iconStyle

    /** Applies [look] in one step: saved, glass repainted, the screen re-tinted. */
    fun use(context: Context, nav: SettingsNav, look: Look) {
        val store = LookStore(context)
        val target = if (look.glassStyle == null) look.copy(glassStyle = GlassStyle.default(nav.profile.effectsMode == EffectsMode.FULL)) else look
        store.save(target)
        target.glassStyle?.let { GlassBackdrop.setStyle(it) }
        store.applyGlass()
        nav.appearanceChanged()
    }

    private fun presets(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        page.add(preview(context, nav.profile.effectsMode == EffectsMode.FULL))
        val current = LookStore(context).load()
        for (p in LookPresets.ALL) {
            page.row("looks.${p.name}", p.name, RowKind.Action(if (matches(p.look, current)) "In use" else null, chevron = false) {
                use(context, nav, p.look)
                nav.notify("${p.name} look on")
            }, subtitle = describe(p.look), iconRes = R.drawable.ic_set_palette)
        }
        return page
    }

    fun describe(l: Look): String =
        listOfNotNull(l.glassStyle?.label, "${l.amount}% glass", "${l.corners.label} corners").joinToString(" · ")

    private fun accent(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        val prefs = AppearancePrefs(context)
        val a = prefs.load()
        val selected = if (a.accentMode == AccentMode.FIXED) a.fixedAccent else 0
        fun pick(c: Int) {
            prefs.update { it.copy(accentMode = AccentMode.FIXED, fixedAccent = c or 0xFF000000.toInt()) }
            LookStore(context).applyGlass()
            nav.appearanceChanged()
        }
        page.row("accent.auto", "Follow the focused app", RowKind.Action(if (a.accentMode == AccentMode.AUTO_BRAND) "In use" else null, chevron = false) {
            prefs.update { it.copy(accentMode = AccentMode.AUTO_BRAND) }
            LookStore(context).applyGlass()
            nav.appearanceChanged()
        }, subtitle = "Each app's brand colour", iconRes = R.drawable.ic_set_palette)
        page.row("accent.wallpaper", "From wallpaper", RowKind.Action(chevron = false) {
            pick(BrandColors.dominantColor(GlassBackdrop.stops.copyOf()))
        }, subtitle = "The main colour behind the glass", iconRes = R.drawable.ic_set_palette)
        page.group("Colours")
        val all = AppearanceSettings.PRESETS + LookPresets.SWATCHES
        for ((i, chunk) in all.chunked(7).withIndex()) {
            page.add(dev.tvlauncher.appsettings.SwatchStrip(context, nav.style, "accent.$i", chunk, selected) { pick(it) })
        }
        return page
    }

    private fun mine(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        val store = LookStore(context)
        val saved = store.saved()
        page.row("mine.save", "Save this look as…", RowKind.Action(chevron = false) {
            val host = (context as? Activity)?.findViewById<ViewGroup>(android.R.id.content) ?: return@Action
            val sheet = TextEntrySheet(context, !(nav.profile.effectsMode == EffectsMode.FULL))
            sheet.show(host, TextEntrySheet.Request("Save look", "Up to ${LookCodec.MAX_SAVED} looks", TextEntrySheet.Kind.TEXT, "Save",
                hint = "Name", icon = R.drawable.ic_set_appearance) { name ->
                val next = LookCodec.save(store.saved(), name, store.load())
                    ?: return@Request if (LookCodec.cleanName(name).isEmpty()) "Type a name." else "Delete a look first."
                store.setSaved(next)
                sheet.dismiss()
                nav.notify("Look saved")
                nav.refresh()
                null
            })
        }, subtitle = "${saved.size} of ${LookCodec.MAX_SAVED} saved", iconRes = R.drawable.ic_set_appearance)
        for ((i, n) in saved.withIndex()) {
            page.row("mine.use.$i", n.name, RowKind.Action("Use", chevron = false) {
                use(context, nav, n.look)
                nav.notify("${n.name} look on")
            }, subtitle = describe(n.look), iconRes = R.drawable.ic_set_palette)
            page.row("mine.del.$i", "Delete ${n.name}", RowKind.Action(chevron = false) {
                store.setSaved(LookCodec.delete(store.saved(), n.name))
                nav.notify("Deleted")
                nav.refresh()
            }, iconRes = R.drawable.ic_set_tune)
        }
        return page
    }

    private fun files(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        val info = page.note("Looking for look files…")
        nav.background({ LookFiles.list(context) }) { list ->
            info.text = if (list.isEmpty()) "Put a look file in ${LookStore.FOLDER} on a USB stick or in Pictures." else "Choose a look to use it."
            for ((i, n) in list.withIndex()) {
                page.row("files.$i", n.name.ifEmpty { "Look" }, RowKind.Action("Use", chevron = false) {
                    use(context, nav, n.look)
                    nav.notify("Look on")
                }, subtitle = describe(n.look), iconRes = R.drawable.ic_set_palette)
            }
        }
        return page
    }

    /** A small glass card over the current backdrop colours: repaints live with every glass change. */
    private fun preview(context: Context, full: Boolean): View {
        val frame = FrameLayout(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, GlassBackdrop.stops.copyOf()).apply {
                cornerRadius = context.dp(GlassBackdrop.corners.radiusDp(GlassTokens.RADIUS_ITEM)).toFloat()
            }
            setPadding(context.dp(16), context.dp(14), context.dp(16), context.dp(14))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(20), context.dp(12), context.dp(20), context.dp(12))
        }
        Glass.frost(card, full, GlassTokens.RADIUS_PANEL)
        card.addView(TextView(context).apply { text = "Preview"; style(18f, Theme.medium, Theme.TEXT) })
        card.addView(TextView(context).apply { text = "Panels, Spotlight and Settings look like this"; style(13f, Theme.regular, Theme.TEXT_2) })
        frame.addView(card, FrameLayout.LayoutParams(-1, context.dp(84)))
        return frame
    }
}
