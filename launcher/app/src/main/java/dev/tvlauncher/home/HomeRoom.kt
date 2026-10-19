package dev.tvlauncher.home

import android.content.Context
import dev.tvlauncher.R
import dev.tvlauncher.room.PaletteFrame
import dev.tvlauncher.room.RoomChoice
import dev.tvlauncher.room.RoomLightController
import dev.tvlauncher.room.RoomPrefs
import dev.tvlauncher.room.RoomTheme
import dev.tvlauncher.ui.BackdropView

/**
 * Room lighting on the home screen: the [RoomLightController] (engine, minute tick and the light
 * sensor when the TV has one and the choice is Auto) feeds [RoomTheme], and the backdrop follows
 * every RoomTheme frame (crossfades included).
 *
 * Lifecycle: [start] in onStart (re-reads Settings > Room lighting), [stop] in onStop (sensor and
 * tick stopped, a running crossfade snapped to its end, listener removed).
 */
class HomeRoom(context: Context, lite: Boolean, private val backdrop: BackdropView) {
    private val prefs = RoomPrefs(context)
    private val controller = RoomLightController(context, liteProvider = { lite }) { mode, palette, ms ->
        RoomTheme.apply(mode, palette, ms)
    }
    // A field: RoomTheme holds listeners weakly.
    private val listener = RoomTheme.Listener { frame, _, _ -> paint(frame) }
    private var started = false

    fun start() {
        if (started) return
        started = true
        RoomTheme.addListener(listener)
        paint(RoomTheme.frame)
        controller.start()
    }

    fun stop() {
        if (!started) return
        started = false
        controller.stop()
        RoomTheme.finishTransition()
        RoomTheme.removeListener(listener)
    }

    /** Settings changed outside this screen (a routine, Bedtime mode): re-read and apply now. */
    fun refresh() { if (started) controller.refresh() }

    /** Spotlight quick action: next choice, applied now; returns the toast text. */
    fun cycle(): String = controller.cycleChoice()

    /** The saved choice (for the quick-action icon). */
    fun choice(): RoomChoice = prefs.load().choice

    private fun paint(f: PaletteFrame) =
        backdrop.setRoom(f.backdropTop, f.backdropBottom, f.backdropFloor, f.accentBlend, f.scrimStrength)

    companion object {
        /** Quick-action icon per choice (RoomQuickAction.iconKey names the same drawables). */
        fun iconRes(choice: RoomChoice): Int = when (choice) {
            RoomChoice.AUTO -> R.drawable.ic_room_auto
            RoomChoice.CINEMA -> R.drawable.ic_room_cinema
            RoomChoice.EVENING -> R.drawable.ic_room_evening
            RoomChoice.STANDARD -> R.drawable.ic_room_standard
            RoomChoice.BRIGHT -> R.drawable.ic_room_bright
        }
    }
}
