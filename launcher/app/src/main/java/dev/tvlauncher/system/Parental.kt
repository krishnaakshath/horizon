package dev.tvlauncher.system

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage

/** The parental PIN that guards hidden apps (stored only as a salted hash in [SystemPrefs]). */
object PinLock {
    private var misses = 0
    private var lockedUntil = 0L

    fun isSet(context: Context) = SystemPrefs(context).pinHash != null

    fun set(context: Context, pin: String): Boolean {
        if (!PinRules.valid(pin)) return false
        val p = SystemPrefs(context)
        p.pinHash = PinRules.hash(pin, p.pinSalt)
        return true
    }

    fun clear(context: Context) { SystemPrefs(context).pinHash = null }

    /** Null when correct; otherwise one line for the PIN sheet. */
    fun check(context: Context, pin: String): String? {
        val now = SystemClock.elapsedRealtime()
        if (now < lockedUntil) return "Too many tries. Wait a moment."
        val p = SystemPrefs(context)
        if (PinRules.matches(pin, p.pinSalt, p.pinHash)) { misses = 0; return null }
        misses++
        val wait = PinRules.lockoutSeconds(misses)
        if (wait > 0) { lockedUntil = now + wait * 1000L; misses = 0 }
        return "Wrong PIN"
    }
}

/**
 * Runs an action that reveals hidden apps: at once without a PIN, otherwise after the PIN is
 * typed on the TV keyboard. [host] is the activity's content view.
 */
object PinGate {
    fun run(context: Context, host: ViewGroup?, lite: Boolean, action: () -> Unit) {
        if (!PinLock.isSet(context)) { action(); return }
        val h = host ?: return
        val sheet = TextEntrySheet(context, lite)
        sheet.show(h, TextEntrySheet.Request("Parental PIN", "Hidden apps are locked", TextEntrySheet.Kind.PIN, "Unlock", hint = "PIN") { pin ->
            PinLock.check(context, pin) ?: run { sheet.dismiss(); action(); null }
        })
    }
}

/** Settings > Parental PIN: set, change or remove the PIN that keeps hidden apps hidden. */
object ParentalSection : SettingsSection, SpecifiedPage {
    override val id = SystemSpecs.PARENTAL
    override val title = "Parental PIN"
    override val iconRes = R.drawable.ic_sys_lock
    override val group = SettingsGroup.SYSTEM
    override val description = "A PIN keeps hidden apps hidden."
    override val spec = SystemSpecs.PIN

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val on = PinLock.isSet(context)
        val host = contentRoot(context)
        page.row("pin.state", "PIN", RowKind.Info(if (on) "On" else "Off"),
            subtitle = "Asked before hidden apps are shown again", iconRes = R.drawable.ic_sys_lock)
        page.row("pin.set", if (on) "Change PIN" else "Set a PIN", RowKind.Action {
            val ask = { askNew(context, nav, host) }
            if (on) PinGate.run(context, host, nav.lite(), ask) else ask()
        }, iconRes = R.drawable.ic_set_check)
        if (on) page.row("pin.remove", "Remove PIN", RowKind.Action(chevron = false) {
            PinGate.run(context, host, nav.lite()) { PinLock.clear(context); nav.notify("PIN removed"); nav.refresh() }
        }, iconRes = R.drawable.ic_net_close)
        return page
    }

    private fun askNew(context: Context, nav: SettingsNav, host: ViewGroup?) {
        val h = host ?: return
        val sheet = TextEntrySheet(context, nav.lite())
        sheet.show(h, TextEntrySheet.Request("New PIN", "Four digits", TextEntrySheet.Kind.PIN, "Next", hint = "PIN") { first ->
            if (!PinRules.valid(first)) return@Request "Use four digits."
            sheet.show(h, TextEntrySheet.Request("Confirm PIN", "Type it once more", TextEntrySheet.Kind.PIN, "Save", hint = "PIN") { second ->
                if (second != first) return@Request "The PINs don't match."
                PinLock.set(context, first)
                sheet.dismiss()
                nav.notify("PIN saved")
                nav.refresh()
                null
            })
            null
        })
    }
}
