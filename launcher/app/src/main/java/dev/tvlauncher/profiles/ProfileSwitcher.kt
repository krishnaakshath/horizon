package dev.tvlauncher.profiles

import android.app.Activity
import android.content.Intent
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import dev.tvlauncher.R
import dev.tvlauncher.home.HomeLook
import dev.tvlauncher.panel.PanelContent
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.PanelHeader
import dev.tvlauncher.panel.PanelShell
import dev.tvlauncher.panel.Trailing
import dev.tvlauncher.system.TextEntrySheet
import dev.tvlauncher.ui.Motion
import dev.tvlauncher.ui.SystemActions

/** The switcher panel's rows, from plain values (JVM-tested). */
object ProfileSwitcherModel {
    const val MANAGE = "profiles.manage"
    fun rowKey(id: String) = "profile:$id"
    fun idOf(key: String): String? = key.removePrefix("profile:").takeIf { key.startsWith("profile:") }

    fun build(state: ProfileState, ask: Boolean = false): PanelContent {
        val header = PanelHeader(R.drawable.ic_profile, if (ask) "Who's watching?" else "Profiles",
            if (ask) "Pick your profile" else "Your own apps, look and wallpaper")
        val rows = state.profiles.map { p ->
            PanelEntry.Row(
                rowKey(p.id), R.drawable.ic_profile, p.name,
                value = when {
                    p.id == state.activeId -> "In use"
                    p.hasPin -> "PIN"
                    else -> null
                },
                trailing = when {
                    p.id == state.activeId -> Trailing.Check
                    p.hasPin -> Trailing.Lock
                    else -> Trailing.None
                }
            )
        }
        return PanelContent(header, rows + PanelEntry.Row(MANAGE, R.drawable.ic_settings, "Manage profiles", trailing = Trailing.Chevron))
    }

    /** "Hi, Sam": the short greeting after a switch. */
    fun greeting(p: Profile) = "Hi, ${p.name}"
}

/**
 * The home screen's profile glue: the top-bar avatar chip, the glass switcher panel (the shared
 * [PanelShell] rows), the PIN sheet, "Ask on start", and the live switch. A switch fades the home
 * screen out, points every per-person store at the new profile ([ProfileManager.switchTo]) and
 * rebuilds the home screen ([HomeLook.rebuild]: look, layout, shelf, room lighting and backdrop all
 * re-read); the new screen fades in and says "Hi, <name>".
 */
class ProfileHome(
    private val activity: Activity,
    private val root: ViewGroup,
    private val lite: Boolean,
    private val chip: ProfileChip,
    private val onOpened: (Boolean) -> Unit = {}
) {
    private val shell = PanelShell(activity, lite)
    private val pinSheet = TextEntrySheet(activity, lite)
    private val builtAt = ProfileManager.generation
    private var started = false
    private var switching = false
    private var ask = false
    private val listener = ProfileManager.Listener { if (started && !switching) rebuild(withFade = true) }

    val isShowing: Boolean get() = shell.isVisible || pinSheet.isShowing

    init {
        shell.onRowClick = ::onRow
        shell.onClosed = { onOpened(false); if (!switching) chip.takeIf { it.isShown }?.requestFocus() }
        ProfileManager.addListener(listener)
    }

    /** onCreate: a fade-in and the greeting when this screen was rebuilt for a switch. */
    fun onCreated() {
        val intent = activity.intent ?: return
        val name = intent.getStringExtra(EXTRA_SWITCHED) ?: return
        activity.intent = Intent(intent).apply { removeExtra(EXTRA_SWITCHED) }
        val ms = Motion.ms(FADE_IN_MS)
        if (ms > 0 && !lite) {
            root.alpha = 0f
            root.animate().alpha(1f).setDuration(ms).setInterpolator(DecelerateInterpolator()).start()
        }
        toast("Hi, $name")
    }

    /**
     * onStart: the chip follows the profile list; a switch made elsewhere (Settings, a routine)
     * rebuilds this screen. True when a rebuild was started (the caller stops its onStart there).
     */
    fun start(): Boolean {
        started = true
        if (ProfileManager.generation != builtAt) { rebuild(withFade = false); return true }
        chip.bind(ProfileManager.load(activity))
        return false
    }

    fun stop() {
        started = false
        shell.dismiss()
        pinSheet.dismiss()
    }

    fun release() {
        ProfileManager.removeListener(listener)
        shell.release()
        pinSheet.dismiss()
    }

    /** After the first app load of a cold start: "Who's watching?" when Settings asks for it. */
    fun maybeAskOnStart() {
        if (ProfileManager.consumeAskOnStart(activity)) open(asking = true)
    }

    fun onBack(): Boolean {
        if (shell.isOpen) { shell.dismiss(); return true }
        return shell.isVisible
    }

    fun closeAll() {
        shell.release()
        pinSheet.dismiss()
        onOpened(false)
    }

    /** The avatar chip (or "Ask on start"). */
    fun open(asking: Boolean = false): Boolean {
        if (shell.isOpen || switching) return false
        ask = asking
        shell.show(root)
        val state = ProfileManager.load(activity)
        shell.render(ProfileSwitcherModel.build(state, asking), preferKey = ProfileSwitcherModel.rowKey(state.activeId))
        onOpened(true)
        return true
    }

    private fun onRow(row: PanelEntry.Row) {
        if (row.key == ProfileSwitcherModel.MANAGE) {
            shell.dismiss()
            SystemActions.launcherSettings(activity, ProfilesSection.ID)
            return
        }
        val id = ProfileSwitcherModel.idOf(row.key) ?: return
        val state = ProfileManager.load(activity)
        val p = state.byId(id) ?: return
        if (p.id == state.activeId) { shell.dismiss(); return }
        if (!p.hasPin) { switchTo(p); return }
        pinSheet.show(root, TextEntrySheet.Request("${p.name}'s PIN", "This profile is locked", TextEntrySheet.Kind.PIN, "Switch",
            hint = "PIN", icon = R.drawable.ic_sys_lock) { pin ->
            ProfileManager.checkPin(p, pin) ?: run { pinSheet.dismiss(); switchTo(p); null }
        })
    }

    private fun switchTo(p: Profile) {
        val r = ProfileManager.switchTo(activity, p.id)
        if (r is ProfileModel.Result.Error) { toast(r.message); return }
        // ProfileManager told the listener; with this screen in front that already rebuilt it.
        if (!switching) rebuild(withFade = true)
    }

    private fun rebuild(withFade: Boolean) {
        if (switching) return
        switching = true
        val name = ProfileManager.active(activity).name
        shell.release()
        pinSheet.dismiss()
        val go = Runnable {
            activity.intent = Intent(activity.intent).putExtra(EXTRA_SWITCHED, name)
            HomeLook.rebuild(activity)
        }
        val ms = if (withFade && !lite) Motion.ms(FADE_OUT_MS) else 0L
        if (ms > 0) root.animate().alpha(0f).setDuration(ms).withEndAction(go).start() else go.run()
    }

    private fun toast(text: String) {
        try { Toast.makeText(activity.applicationContext, text, Toast.LENGTH_SHORT).show() } catch (e: RuntimeException) { }
    }

    companion object {
        private const val EXTRA_SWITCHED = "dev.tvlauncher.extra.PROFILE_SWITCHED"
        const val FADE_OUT_MS = 180L
        const val FADE_IN_MS = 320L
    }
}
