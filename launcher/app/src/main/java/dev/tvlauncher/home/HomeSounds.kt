package dev.tvlauncher.home

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import dev.tvlauncher.sound.SoundEvent
import dev.tvlauncher.sound.SoundEventMapper
import dev.tvlauncher.sound.SoundPlayer
import dev.tvlauncher.sound.SoundPrefs
import dev.tvlauncher.sound.UiSoundsFacade
import dev.tvlauncher.ui.TileMenu
import java.util.concurrent.atomic.AtomicReference

/**
 * The home screen's one [SoundPlayer] / [UiSoundsFacade], created once by HomeActivity.
 * The settings supplier reads an [AtomicReference] because the player calls it from its builder
 * thread. Decisions come from [SoundEventMapper]; audibility only from the sound settings.
 * Edits made in Settings (which has its own preview player) are picked up by [reload] in onResume.
 */
class HomeSounds(context: Context) {
    private val prefs = SoundPrefs(context)
    private val current = AtomicReference(prefs.load())
    val player = SoundPlayer(context.applicationContext, settings = { current.get() })
    private val facade = UiSoundsFacade(player)
    private var readyAtMs = SoundEventMapper.NOT_READY
    private var lastDpadMs = SoundEventMapper.NEVER

    init {
        facade.prewarm()
    }

    /** onResume: pick up edits made elsewhere and make sure the sounds are built. */
    fun reload() {
        current.set(prefs.load())
        facade.prewarm()
    }

    /** MOVE stays silent until [delayMs] from now (first load done, splash reveal over). */
    fun readyIn(delayMs: Long) {
        readyAtMs = SystemClock.uptimeMillis() + delayMs
    }

    /** Every key the home window dispatches; remembers D-pad presses so focus moves can be told apart. */
    fun onKey(e: KeyEvent) {
        if (e.action == KeyEvent.ACTION_DOWN && SoundEventMapper.isDpadMove(e.keyCode)) {
            lastDpadMs = SystemClock.uptimeMillis()
        }
    }

    fun onFocusChanged() = play(SoundEventMapper.focusMoved(SystemClock.uptimeMillis(), readyAtMs, lastDpadMs))
    fun launch(started: Boolean) = play(SoundEventMapper.launch(started))
    fun action(ok: Boolean) = play(SoundEventMapper.action(ok))
    fun panel(opened: Boolean) = play(SoundEventMapper.panel(opened))
    fun back(closedSomething: Boolean) = play(SoundEventMapper.back(closedSomething))
    fun stripAction(opensMenu: Boolean) = play(SoundEventMapper.stripAction(opensMenu))
    fun menuOpen() = play(SoundEvent.MENU_OPEN)

    /** TileMenu dialogs: SELECT for a picked entry, BACK when dismissed with Back. */
    fun bind(menu: TileMenu) {
        menu.onPicked = { play(SoundEvent.SELECT) }
        menu.onCancelled = { play(SoundEvent.BACK) }
    }

    private fun play(e: SoundEvent?) {
        when (e) {
            SoundEvent.MOVE -> facade.onFocusMoved()
            SoundEvent.SELECT -> facade.onSelect()
            SoundEvent.BACK -> facade.onBack()
            SoundEvent.LAUNCH -> facade.onLaunch()
            SoundEvent.MENU_OPEN -> facade.onMenuOpen()
            SoundEvent.ERROR -> facade.onError()
            null -> Unit
        }
    }

    fun release() = facade.release()
}
