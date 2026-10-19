package dev.tvlauncher.system

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.CaptioningManager
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.room.RoomChoice
import dev.tvlauncher.room.RoomPrefs
import dev.tvlauncher.settings.AnimationLevel
import dev.tvlauncher.settings.AppearancePrefs
import dev.tvlauncher.sound.SoundPrefs

/** Which accessibility rows show, and how. Pure. */
object A11yRows {
    /** A row that can switch (true) or only show state (false). */
    data class Plan(val keys: List<String>, val switchable: Set<String>, val note: Boolean)

    fun plan(readerInstalled: Boolean, helperInstalled: Boolean, canWriteSecure: Boolean): Plan {
        val keys = ArrayList(listOf("a11y.text", "a11y.contrast", "a11y.motion", "a11y.captions"))
        if (readerInstalled) keys += "a11y.reader"
        keys += "a11y.helper"
        val sw = HashSet(listOf("a11y.text", "a11y.contrast", "a11y.motion"))
        if (canWriteSecure) {
            sw += "a11y.captions"
            if (readerInstalled) sw += "a11y.reader"
            if (helperInstalled) sw += "a11y.helper"
        }
        return Plan(keys, sw, note = !canWriteSecure)
    }
}

/** Launcher text size: wraps an activity's base context with a scaled fontScale. */
object A11yScale {
    fun wrap(base: Context): Context = safe(base) {
        val size = A11yModel.textSizeOf(base.getSharedPreferences(SystemPrefs.FILE, Context.MODE_PRIVATE).getString("textSize", null))
        if (size == A11yModel.TextSize.M) return@safe base
        val c = Configuration(base.resources.configuration)
        c.fontScale = c.fontScale * size.scale
        base.createConfigurationContext(c)
    }
}

/** Secure-settings switches, possible only after the one-time WRITE_SECURE_SETTINGS grant. */
object SecureSwitches {
    const val CAPTIONS = "accessibility_captioning_enabled"
    const val DALTONIZER = "accessibility_display_daltonizer_enabled"
    const val INVERSION = "accessibility_display_inversion_enabled"

    fun get(context: Context, key: String) = safe(false) { Settings.Secure.getInt(context.contentResolver, key, 0) == 1 }

    fun put(context: Context, key: String, on: Boolean): Boolean =
        SystemPermissions.canWriteSecure(context) && safe(false) { Settings.Secure.putInt(context.contentResolver, key, if (on) 1 else 0) }

    fun services(context: Context): String? = safe(null) {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    }

    /** Adds or removes [component] in ENABLED_ACCESSIBILITY_SERVICES, keeping every other service. */
    fun setService(context: Context, component: String, on: Boolean): Boolean {
        if (!SystemPermissions.canWriteSecure(context)) return false
        val cur = services(context)
        val next = if (on) A11yModel.enable(cur, component) else A11yModel.disable(cur, component)
        return safe(false) {
            Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            if (on || next.isNotEmpty()) Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            else Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
            true
        }
    }
}

/**
 * Settings > Accessibility, the launcher's own menu for TVs without one: text size, high contrast
 * (the Bright room palette: solid panels, white bold text, a thick focus ring), reduce motion,
 * captions, the screen reader when installed and the Helper add-on. Switches that live in the
 * TV's secure settings work once the provisioning grant exists; otherwise they show state only.
 */
object AccessibilitySection : SettingsSection, SpecifiedPage {
    override val id = SystemSpecs.ACCESSIBILITY
    override val title = "Accessibility"
    override val iconRes = R.drawable.ic_sys_access
    override val group = SettingsGroup.SYSTEM
    override val description = "Text size, contrast, motion, captions and helpers."
    override val spec = SystemSpecs.ACCESS

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val canWrite = SystemPermissions.canWriteSecure(context)
        val reader = readerComponent(context)
        val helperInstalled = PowerActions.helperInstalled(context)
        val plan = A11yRows.plan(reader != null, helperInstalled, canWrite)
        val prefs = SystemPrefs(context)
        val sizes = A11yModel.TextSize.values()
        val cur = A11yModel.textSizeOf(prefs.textSize)
        page.row("a11y.text", "Text size", RowKind.Choice(sizes.map { it.label }, sizes.indexOf(cur)) { i ->
            prefs.textSize = sizes[i].name
            (context as? Activity)?.let { a -> a.window.decorView.postDelayed({ a.recreate() }, 250) }
        }, subtitle = "Everything in the launcher", iconRes = R.drawable.ic_sys_access)

        val room = RoomPrefs(context)
        page.row("a11y.contrast", "High contrast", RowKind.Toggle(room.load().choice == RoomChoice.BRIGHT) { on ->
            val r = room.load()
            if (on) {
                if (r.choice != RoomChoice.BRIGHT) prefs.roomBeforeContrast = r.choice.name
                room.save(r.copy(choice = RoomChoice.BRIGHT, boostTextInBright = true))
            } else {
                val back = RoomChoice.values().firstOrNull { it.name == prefs.roomBeforeContrast } ?: RoomChoice.AUTO
                room.save(r.copy(choice = if (back == RoomChoice.BRIGHT) RoomChoice.AUTO else back))
            }
            nav.appearanceChanged()
        }, subtitle = "Solid panels, bold white text, thick focus ring", iconRes = R.drawable.ic_set_palette)

        val appearance = AppearancePrefs(context)
        page.row("a11y.motion", "Reduce motion", RowKind.Toggle(appearance.load().animationLevel == AnimationLevel.OFF) { on ->
            appearance.update { a ->
                if (on) {
                    if (a.animationLevel != AnimationLevel.OFF) prefs.motionBefore = a.animationLevel.name
                    a.copy(animationLevel = AnimationLevel.OFF)
                } else {
                    val back = AnimationLevel.values().firstOrNull { it.name == prefs.motionBefore } ?: AnimationLevel.AUTO
                    a.copy(animationLevel = if (back == AnimationLevel.OFF) AnimationLevel.AUTO else back)
                }
            }
            nav.appearanceChanged()
        }, iconRes = R.drawable.ic_set_motion)

        val captionsOn = safe(false) { (context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager)?.isEnabled == true }
        if ("a11y.captions" in plan.switchable) {
            page.row("a11y.captions", "Captions", RowKind.Toggle(captionsOn) { on ->
                if (!SecureSwitches.put(context, SecureSwitches.CAPTIONS, on)) nav.notify("Captions could not be switched")
            }, subtitle = "Apps with subtitles follow this", iconRes = R.drawable.ic_set_tiles)
        } else {
            page.row("a11y.captions", "Captions", RowKind.Info(if (captionsOn) "On" else "Off"),
                subtitle = "Apps with subtitles follow this", iconRes = R.drawable.ic_set_tiles)
        }

        if (reader != null) {
            val on = A11yModel.isEnabled(SecureSwitches.services(context), reader)
            if ("a11y.reader" in plan.switchable) page.row("a11y.reader", "Screen reader", RowKind.Toggle(on) { want ->
                if (!SecureSwitches.setService(context, reader, want)) nav.notify("The screen reader could not be switched")
            }, subtitle = "Reads the screen aloud", iconRes = R.drawable.ic_sys_volume)
            else page.row("a11y.reader", "Screen reader", RowKind.Info(if (on) "On" else "Off"),
                subtitle = "Reads the screen aloud", iconRes = R.drawable.ic_sys_volume)
        }

        val helperOn = PowerActions.helperEnabled(context)
        when {
            !helperInstalled -> page.row("a11y.helper", "Helper", RowKind.Info("Not installed"),
                subtitle = HelperCopy.subtitle(installed = false, on = false), iconRes = R.drawable.ic_set_helper)
            "a11y.helper" in plan.switchable -> page.row("a11y.helper", "Helper", RowKind.Toggle(helperOn) { want ->
                if (!SecureSwitches.setService(context, A11yModel.HELPER_COMPONENT, want)) nav.notify("The Helper could not be switched")
            }, subtitle = HelperCopy.subtitle(installed = true, on = helperOn), iconRes = R.drawable.ic_set_helper)
            else -> page.row("a11y.helper", "Helper", RowKind.Info(if (helperOn) "On" else "Off"),
                subtitle = HelperCopy.subtitle(installed = true, on = helperOn), iconRes = R.drawable.ic_set_helper)
        }
        if (plan.note) page.note("Captions, colour and helper switches appear here once this TV lets the launcher change them.")

        page.advanced(nav, "a11y.advanced", "Bold text, sounds, colour") { adv ->
            adv.row("a11y.bold", "Bold text in bright rooms", RowKind.Toggle(room.load().boostTextInBright) { on ->
                room.save(room.load().copy(boostTextInBright = on)); nav.appearanceChanged()
            }, iconRes = R.drawable.ic_set_palette)
            val sounds = SoundPrefs(context)
            adv.row("a11y.sounds", "Sound feedback", RowKind.Toggle(sounds.load().enabled) { on ->
                sounds.save(sounds.load().copy(enabled = on))
            }, subtitle = "Clicks and chimes as you move", iconRes = R.drawable.ic_set_sounds)
            for ((key, label, setting) in listOf(
                Triple("a11y.daltonizer", "Colour correction", SecureSwitches.DALTONIZER),
                Triple("a11y.inversion", "Colour inversion", SecureSwitches.INVERSION)
            )) {
                val on = SecureSwitches.get(context, setting)
                if (canWrite) adv.row(key, label, RowKind.Toggle(on) { want ->
                    if (!SecureSwitches.put(context, setting, want)) nav.notify("$label could not be switched")
                }, iconRes = R.drawable.ic_set_palette)
                else adv.row(key, label, RowKind.Info(if (on) "On" else "Off"), iconRes = R.drawable.ic_set_palette)
            }
        }
        return page
    }

    /** The installed screen reader's service component, or null. */
    private fun readerComponent(context: Context): String? = safe(null) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return@safe null
        am.installedAccessibilityServiceList.firstOrNull { it.resolveInfo?.serviceInfo?.packageName in A11yModel.TALKBACK_PACKAGES }
            ?.let { "${it.resolveInfo.serviceInfo.packageName}/${it.resolveInfo.serviceInfo.name}" }
    }
}
