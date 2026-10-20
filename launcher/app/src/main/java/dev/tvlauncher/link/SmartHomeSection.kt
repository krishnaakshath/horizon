package dev.tvlauncher.link

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.system.TextEntrySheet
import dev.tvlauncher.system.contentRoot
import dev.tvlauncher.system.lite

/** One favourite Home Assistant entity, as M02 Link's provider lists it (read-only). */
data class SmartEntity(
    val id: String,
    val name: String,
    val control: String,
    val state: String,
    val on: Boolean,
    val available: Boolean,
)

/** Words and decisions of the Smart home page (pure, tested). */
object SmartHomeText {
    fun state(s: String?): String = when (s) {
        LinkContract.HA_OK -> "Connected"
        LinkContract.HA_UNREACHABLE -> "Can't reach Home Assistant right now"
        LinkContract.HA_AUTH -> "Home Assistant didn't accept the token"
        LinkContract.HA_LOADING -> "Checking…"
        else -> "Not connected"
    }

    /** Scenes activate; climate and media have their own controls on the phone; the rest toggle. */
    fun action(control: String): String? = when (control) {
        LinkContract.CONTROL_SCENE -> LinkContract.ACTION_ACTIVATE
        LinkContract.CONTROL_TOGGLE -> LinkContract.ACTION_TOGGLE
        LinkContract.CONTROL_MEDIA -> LinkContract.ACTION_PLAY_PAUSE
        else -> null
    }

    fun entitySubtitle(e: SmartEntity): String = if (!e.available) "Unavailable" else e.state
}

/** M02 Link's Home Assistant snapshot through its signature-protected provider. Never throws. */
object SmartHomeData {
    fun favourites(context: Context): Pair<String, List<SmartEntity>> = try {
        context.contentResolver.query(Uri.parse(LinkContract.URI_HOME), null, null, null, null)?.use { c ->
            fun s(col: String) = c.getColumnIndex(col).let { if (it < 0) "" else c.getString(it) ?: "" }
            fun l(col: String) = c.getColumnIndex(col).let { if (it < 0) 0L else c.getLong(it) }
            val out = ArrayList<SmartEntity>()
            var state = c.extras?.getString(LinkContract.COL_HA_STATE) ?: LinkContract.HA_NOT_CONFIGURED
            while (c.moveToNext() && out.size < LinkContract.MAX_HOME_ROWS) {
                state = s(LinkContract.COL_HA_STATE).ifEmpty { state }
                out += SmartEntity(s(LinkContract.COL_ENTITY_ID), s(LinkContract.COL_NAME), s(LinkContract.COL_CONTROL),
                    s(LinkContract.COL_STATE), l(LinkContract.COL_ON) != 0L, l(LinkContract.COL_AVAILABLE) != 0L)
            }
            state to out
        } ?: (LinkContract.HA_NOT_CONFIGURED to emptyList())
    } catch (e: Exception) {
        LinkContract.HA_NOT_CONFIGURED to emptyList()
    }

    fun call(context: Context, method: String, arg: String?, extras: Bundle? = null): Bundle? = try {
        context.contentResolver.call(Uri.parse(LinkContract.URI_BASE), method, arg, extras)
    } catch (e: Exception) {
        null
    }

    fun act(context: Context, e: SmartEntity): Boolean {
        val a = SmartHomeText.action(e.control) ?: return false
        return call(context, LinkContract.METHOD_HA_ACTION, e.id, Bundle().apply { putString(LinkContract.EXTRA_ACTION, a) })
            ?.getBoolean(LinkContract.EXTRA_OK) == true
    }
}

/**
 * Settings > System > Smart home: Home Assistant through M02 Link (the launcher itself has no
 * network access). Connect with the TV keyboard or from the phone page, test the connection, and
 * control the favourites chosen on the phone.
 */
object SmartHomeSection : SettingsSection, SpecifiedPage {
    const val ID = "smart_home"
    override val id = ID
    override val title = "Smart home"
    override val iconRes = R.drawable.ic_set_home
    override val group = SettingsGroup.SYSTEM
    override val description = "Lights and scenes from Home Assistant."

    const val STATUS = "ha.status"
    const val CONNECT = "ha.connect"
    const val TEST = "ha.test"
    const val DEVICES = "ha.devices"
    const val FORGET = "ha.forget"

    override val spec = PageSpec(ID, listOf(STATUS, CONNECT, TEST, DEVICES, FORGET))

    fun register(registry: SettingsRegistry = SettingsRegistry.shared) = registry.register(this)

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val link = LinkStatus.read(context)
        if (!link.installed) {
            page.row(STATUS, "Smart home", RowKind.Info("Not installed"),
                subtitle = "Install M02 Link to control Home Assistant from the TV", iconRes = R.drawable.ic_set_about)
            return page
        }
        val (state, favs) = SmartHomeData.favourites(context)
        val host = contentRoot(context)
        page.row(STATUS, "Home Assistant", RowKind.Info(SmartHomeText.state(state)), iconRes = R.drawable.ic_set_check)
        page.row(CONNECT, "Connect", RowKind.Action { connect(context, nav, host) },
            subtitle = "Type the address and token here, or use the phone remote's More page", iconRes = R.drawable.ic_set_tune)
        page.row(TEST, "Test connection", RowKind.Action(chevron = false) {
            val s = SmartHomeData.call(context, LinkContract.METHOD_HA_REFRESH, null)?.getString(LinkContract.EXTRA_STATE)
            nav.notify(SmartHomeText.state(s))
            nav.refresh()
        }, iconRes = R.drawable.ic_set_about)
        page.row(DEVICES, "Favourites", RowKind.Action {
            nav.push("Favourites") { c, n -> devicesPage(c, n) }
        }, subtitle = if (favs.isEmpty()) "Choose up to 8 on the phone remote" else "${favs.size} on this TV", iconRes = R.drawable.ic_set_home)
        page.row(FORGET, "Disconnect", RowKind.Action(chevron = false) {
            SmartHomeData.call(context, LinkContract.METHOD_HA_FORGET, null)
            nav.refresh()
        }, subtitle = "Removes the saved address and token", iconRes = R.drawable.ic_set_reset)
        return page
    }

    private fun connect(context: Context, nav: SettingsNav, host: android.view.ViewGroup?) {
        val h = host ?: return
        val sheet = TextEntrySheet(context, nav.lite())
        sheet.show(h, TextEntrySheet.Request("Home Assistant", "Its address on your network", TextEntrySheet.Kind.TEXT, "Next",
            hint = "homeassistant.local:8123", icon = R.drawable.ic_set_home) { url ->
            val u = url.trim()
            if (u.isEmpty()) return@Request "Type the address."
            sheet.dismiss()
            val tokenSheet = TextEntrySheet(context, nav.lite())
            tokenSheet.show(h, TextEntrySheet.Request("Access token", "A long-lived token from your Home Assistant profile",
                TextEntrySheet.Kind.PASSWORD, "Connect", hint = "Token", icon = R.drawable.ic_sys_lock) { token ->
                val s = SmartHomeData.call(context, LinkContract.METHOD_HA_CONFIGURE, u,
                    Bundle().apply { putString(LinkContract.EXTRA_VALUE, token.trim()) })?.getString(LinkContract.EXTRA_STATE)
                if (s == LinkContract.HA_OK) { tokenSheet.dismiss(); nav.notify("Connected to Home Assistant"); nav.refresh(); null }
                else SmartHomeText.state(s)
            })
            null
        })
    }

    private fun devicesPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        val (state, favs) = SmartHomeData.favourites(context)
        if (state != LinkContract.HA_OK && favs.isEmpty()) { page.note(SmartHomeText.state(state)); return page }
        if (favs.isEmpty()) { page.note("Choose favourites on the phone remote's More page."); return page }
        favs.forEachIndexed { i, e ->
            val kind = when (e.control) {
                LinkContract.CONTROL_TOGGLE -> RowKind.Toggle(e.on) { if (!SmartHomeData.act(context, e)) nav.notify("Home Assistant didn't accept that") }
                else -> RowKind.Action(chevron = false) {
                    nav.notify(if (SmartHomeData.act(context, e)) "Done" else "Home Assistant didn't accept that")
                }
            }
            page.row("ha.e$i", e.name.ifEmpty { e.id }, kind, subtitle = SmartHomeText.entitySubtitle(e), iconRes = R.drawable.ic_set_home)
        }
        return page
    }
}
