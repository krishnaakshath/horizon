package dev.tvlauncher.volume

import android.content.Context
import android.os.SystemClock
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.Chip
import dev.tvlauncher.appsettings.SectionIds
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.appsettings.SettingsRow
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.system.InsistGate
import dev.tvlauncher.system.TvScreens

/**
 * Settings > Sound > Volume & output: our own volume page (also what Spotlight's Sound action
 * opens). Volume and Mute change the media volume directly (never the TV's popup); Output says
 * where the sound plays.
 * What only the TV can do (switching outputs, sound modes, equaliser) is listed under Advanced.
 */
object VolumeSection : SettingsSection, SpecifiedPage {
    const val ID = "sound_output"
    override val id = ID
    override val title = "Volume & output"
    override val iconRes = R.drawable.ic_volume
    override val group = SettingsGroup.SOUND
    override val description = "Volume, mute and where the sound plays."
    override val spec = PageSpec(
        ID,
        listOf("sound_output.volume", "sound_output.mute", "sound_output.output"),
        listOf("sound_output.outputs", "sound_output.switch", "sound_output.modes")
    )

    /** Settings > Sound lists this page first, above UI sounds. */
    fun register(registry: SettingsRegistry = SettingsRegistry.shared) =
        registry.registerBefore(this, SectionIds.SOUNDS)

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val vol = SystemVolume(context)
        val state = vol.read()
        val controllable = state != null && !vol.fixed

        if (controllable && state != null) {
            var muted = state.muted
            page.row("sound_output.volume", "Volume", RowKind.Stepper(state.level, 0, state.max, { "$it" }, bar = true) { v ->
                if (!vol.setLevel(v)) nav.notify("The TV is managing the volume for this output")
                if (muted) { muted = false; nav.refresh() }
            }, iconRes = R.drawable.ic_volume)
            page.row("sound_output.mute", "Mute", RowKind.Toggle(state.muted) { on ->
                if (!vol.setMuted(on)) nav.notify("The TV is managing the volume for this output")
                muted = on
            }, iconRes = R.drawable.ic_volume_off)
        } else {
            page.row("sound_output.volume", "Volume", RowKind.Info("Managed by the TV"),
                subtitle = "Use the remote's volume keys", iconRes = R.drawable.ic_volume)
            page.row("sound_output.mute", "Mute", RowKind.Info("Managed by the TV"), iconRes = R.drawable.ic_volume_off)
        }

        val outputs = VolumeCopy.outputs(vol.outputTypes())
        page.row("sound_output.output", "Output", RowKind.Info(PlayingOn.label(vol.selectedRoute(), outputs)),
            subtitle = "Where the sound plays", iconRes = R.drawable.ic_dev_speaker)

        page.advanced(nav, "sound_output.advanced", "Connected outputs, sound modes") { adv ->
            adv.row("sound_output.outputs", "Connected outputs", RowKind.Action(if (outputs.isEmpty()) "None" else "${outputs.size}") {
                nav.push("Connected outputs") { c, n -> outputsPage(c, n, outputs) }
            }, subtitle = outputs.joinToString(", ").ifEmpty { null }, iconRes = R.drawable.ic_dev_soundbar)
            adv.row("sound_output.switch", "Choose the output", RowKind.Info("Managed by the TV"),
                subtitle = "Pick speakers or a sound system in the TV's own menu", iconRes = R.drawable.ic_dev_hdmi)
            // Like the other "Managed by the TV" rows: the first OK arms, a second OK opens the TV's page.
            val gate = InsistGate()
            lateinit var modes: SettingsRow
            modes = adv.row("sound_output.modes", "Sound modes and equaliser", RowKind.Action("On the TV", chip = Chip.NEUTRAL) {
                if (!gate.press("sound_output.modes", SystemClock.elapsedRealtime())) { modes.setValue("Press OK again to open"); return@Action }
                modes.setValue("On the TV")
                if (!TvScreens.open(context, TvScreens.Screen.PICTURE)) nav.notify("This TV has no sound settings page")
            }, subtitle = "Sound style, balance, Dolby and DTS stay in the TV's menu", iconRes = R.drawable.ic_set_tune)
        }
        return page
    }

    private fun outputsPage(context: Context, nav: SettingsNav, outputs: List<String>): View {
        val page = SettingsPage(context, nav.style)
        if (outputs.isEmpty()) page.row("sound_output.out.none", "No outputs reported", RowKind.Info())
        outputs.forEachIndexed { i, o ->
            page.row("sound_output.out.$i", o, RowKind.Info(), iconRes = R.drawable.ic_dev_speaker)
        }
        return page
    }
}
