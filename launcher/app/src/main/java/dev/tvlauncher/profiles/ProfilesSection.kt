package dev.tvlauncher.profiles

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.system.InsistGate
import dev.tvlauncher.system.TextEntrySheet
import dev.tvlauncher.system.contentRoot
import dev.tvlauncher.system.lite

/** Row keys and wording of the Profiles pages (JVM-tested). */
object ProfilesPage {
    const val ASK = "profiles.ask"
    const val ADD = "profiles.add"
    /** One row per profile, in list order: profiles.slot1 .. profiles.slot5. */
    fun slot(i: Int) = "profiles.slot${i + 1}"

    val SPEC = PageSpec(ProfilesSection.ID, (0 until ProfileModel.MAX_PROFILES).map(::slot) + ADD + ASK)

    const val SWITCH = "pe.switch"
    const val NAME = "pe.name"
    const val COLOR = "pe.color"
    const val PIN = "pe.pin"
    const val PIN_REMOVE = "pe.pinRemove"
    const val DELETE = "pe.delete"
    val EDIT_SPEC = PageSpec("profiles.edit", listOf(SWITCH, NAME, COLOR, PIN, PIN_REMOVE, DELETE))

    /** (key, title, value) of each profile row. */
    fun rows(state: ProfileState): List<Triple<String, String, String>> = state.profiles.mapIndexed { i, p ->
        Triple(slot(i), p.name, listOfNotNull(if (p.id == state.activeId) "In use" else null, if (p.hasPin) "PIN" else null)
            .joinToString(" · "))
    }

    fun canAdd(state: ProfileState) = state.profiles.size < ProfileModel.MAX_PROFILES
}

/**
 * Settings > System > Profiles: everyone in the household gets their own favorites, order and
 * hidden apps, look, wallpaper, home layout, room lighting and Now Playing choices. Add, rename,
 * recolour, lock with a PIN, delete (never the last one), and "Ask on start".
 */
object ProfilesSection : SettingsSection, SpecifiedPage {
    const val ID = "profiles"
    override val id = ID
    override val title = "Profiles"
    override val iconRes = R.drawable.ic_profile
    override val group = SettingsGroup.SYSTEM
    override val description = "Each person gets their own apps, look and wallpaper."
    override val spec = ProfilesPage.SPEC

    fun register(registry: SettingsRegistry = SettingsRegistry.shared) = registry.register(this)

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val state = ProfileManager.load(context)
        val host = contentRoot(context)
        page.lead("Switch from the avatar in the top bar. Each profile keeps its own favorites, hidden apps, look, wallpaper and layout.")
        ProfilesPage.rows(state).forEachIndexed { i, (key, name, value) ->
            val p = state.profiles[i]
            page.row(key, name, RowKind.Action(value.ifEmpty { null }) {
                unlock(context, nav, host, p) { nav.push(p.name) { c, n -> editPage(c, n, p.id) } }
            }, iconRes = R.drawable.ic_profile)
        }
        if (ProfilesPage.canAdd(state)) {
            page.row(ProfilesPage.ADD, "Add a profile", RowKind.Action(chevron = false) { add(context, nav, host) },
                subtitle = "Starts as a copy of ${state.active.name}'s setup", iconRes = R.drawable.ic_disc_plus)
        }
        page.row(ProfilesPage.ASK, "Ask who's watching", RowKind.Toggle(state.askOnStart) { on ->
            ProfileManager.setAskOnStart(context, on)
        }, subtitle = if (state.profiles.size > 1) "When the TV starts" else "Once there are two or more profiles",
            iconRes = R.drawable.ic_set_home)
        return page
    }

    private fun add(context: Context, nav: SettingsNav, host: ViewGroup?) {
        val h = host ?: return
        val sheet = TextEntrySheet(context, nav.lite())
        sheet.show(h, TextEntrySheet.Request("New profile", "A name for this person", TextEntrySheet.Kind.TEXT, "Add",
            hint = "Name", icon = R.drawable.ic_profile) { name ->
            when (val r = ProfileManager.create(context, name)) {
                is ProfileModel.Result.Error -> r.message
                is ProfileModel.Result.Ok -> {
                    sheet.dismiss()
                    nav.notify("Added ${r.profile?.name}. Switch from the top bar")
                    nav.refresh()
                    null
                }
            }
        })
    }

    /** A profile with a PIN opens only after its PIN. */
    private fun unlock(context: Context, nav: SettingsNav, host: ViewGroup?, p: Profile, then: () -> Unit) {
        if (!p.hasPin) { then(); return }
        val h = host ?: return
        val sheet = TextEntrySheet(context, nav.lite())
        sheet.show(h, TextEntrySheet.Request("${p.name}'s PIN", "This profile is locked", TextEntrySheet.Kind.PIN, "Unlock", hint = "PIN") { pin ->
            ProfileManager.checkPin(p, pin) ?: run { sheet.dismiss(); then(); null }
        })
    }

    private val deleteGate = InsistGate()

    private fun editPage(context: Context, nav: SettingsNav, id: String): View {
        val page = SettingsPage(context, nav.style, ProfilesPage.EDIT_SPEC)
        val state = ProfileManager.load(context)
        val p = state.byId(id) ?: return page.also { page.note("This profile was deleted.") }
        val host = contentRoot(context)
        if (p.id != state.activeId) {
            page.row(ProfilesPage.SWITCH, "Switch to ${p.name}", RowKind.Action(chevron = false) {
                when (val r = ProfileManager.switchTo(context, p.id)) {
                    is ProfileModel.Result.Error -> nav.notify(r.message)
                    is ProfileModel.Result.Ok -> { nav.appearanceChanged(); nav.notify(ProfileSwitcherModel.greeting(p)) }
                }
            }, subtitle = "Home switches when you go back", iconRes = R.drawable.ic_set_check)
        }
        page.row(ProfilesPage.NAME, "Name", RowKind.Action(p.name) {
            val h = host ?: return@Action
            val sheet = TextEntrySheet(context, nav.lite())
            sheet.show(h, TextEntrySheet.Request("Rename", "A name for this person", TextEntrySheet.Kind.TEXT, "Save",
                initial = p.name, hint = "Name", icon = R.drawable.ic_profile) { name ->
                when (val r = ProfileManager.rename(context, p.id, name)) {
                    is ProfileModel.Result.Error -> r.message
                    is ProfileModel.Result.Ok -> { sheet.dismiss(); nav.refresh(); null }
                }
            })
        }, iconRes = R.drawable.ic_profile)
        page.row(ProfilesPage.COLOR, "Avatar colour", RowKind.Choice(ProfileModel.COLOR_NAMES, Math.floorMod(p.color, ProfileModel.COLORS.size)) { i ->
            ProfileManager.recolor(context, p.id, i)
        }, subtitle = "The disc behind ${p.initial} in the top bar", iconRes = R.drawable.ic_set_palette)
        page.row(ProfilesPage.PIN, if (p.hasPin) "Change PIN" else "Lock with a PIN", RowKind.Action {
            val h = host ?: return@Action
            val sheet = TextEntrySheet(context, nav.lite())
            sheet.show(h, TextEntrySheet.Request("New PIN", "Four digits", TextEntrySheet.Kind.PIN, "Next", hint = "PIN") { first ->
                if (!dev.tvlauncher.system.PinRules.valid(first)) return@Request "Use four digits."
                sheet.show(h, TextEntrySheet.Request("Confirm PIN", "Type it once more", TextEntrySheet.Kind.PIN, "Save", hint = "PIN") { second ->
                    if (second != first) return@Request "The PINs don't match."
                    when (val r = ProfileManager.setPin(context, p.id, first)) {
                        is ProfileModel.Result.Error -> r.message
                        is ProfileModel.Result.Ok -> { sheet.dismiss(); nav.notify("PIN saved"); nav.refresh(); null }
                    }
                })
                null
            })
        }, subtitle = "Asked before switching to ${p.name}", iconRes = R.drawable.ic_sys_lock)
        if (p.hasPin) {
            page.row(ProfilesPage.PIN_REMOVE, "Remove PIN", RowKind.Action(chevron = false) {
                ProfileManager.clearPin(context, p.id)
                nav.notify("PIN removed")
                nav.refresh()
            }, iconRes = R.drawable.ic_net_close)
        }
        if (state.profiles.size > 1) {
            page.row(ProfilesPage.DELETE, "Delete ${p.name}", RowKind.Action(chevron = false) {
                if (!deleteGate.press(p.id, SystemClock.elapsedRealtime())) {
                    nav.notify("Press OK again to delete ${p.name} and its settings")
                    return@Action
                }
                when (val r = ProfileManager.delete(context, p.id)) {
                    is ProfileModel.Result.Error -> nav.notify(r.message)
                    is ProfileModel.Result.Ok -> { nav.notify("${p.name} deleted"); nav.appearanceChanged(); nav.pop(); nav.refreshCurrent() }
                }
            }, subtitle = "Its favorites, look and wallpaper go too", iconRes = R.drawable.ic_set_reset)
        }
        return page
    }
}
