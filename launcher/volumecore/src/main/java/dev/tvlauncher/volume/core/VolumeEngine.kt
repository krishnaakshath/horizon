package dev.tvlauncher.volume.core

import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent

/**
 * Our volume experience in one object: the key decision ([VolumeKeyRouter]), the pluggable
 * control with read-back ([VolumeController], [VolumeBackends]) and the bar ([VolumeOverlay]).
 * Used by the helper's accessibility service (every app) and by the launcher's own activities
 * (while the launcher is in front, without the helper). [onKey] returns true only when the key
 * was applied by us; false always means "let the system handle it".
 */
class VolumeEngine(
    context: Context,
    windowType: Int,
    face: Typeface?,
    enabled: () -> Boolean,
    motion: () -> BarMotion,
    onSeen: (VolumeKey, VolumeResult?) -> Unit = { _, _ -> },
) {
    private val overlay = VolumeOverlay(context, windowType, face, motion)
    val controller: VolumeController = VolumeBackends.default(context)
    private val router = VolumeKeyRouter(
        enabled = enabled,
        controller = controller,
        onShow = { overlay.show(it) },
        onRelease = { overlay.keepAlive() },
        onSeen = onSeen,
    )

    fun onKey(event: KeyEvent): Boolean {
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }
        return router.onKey(event.keyCode, down, event.repeatCount, (event.flags and KeyEvent.FLAG_CANCELED) != 0)
    }

    /** Shows the bar for a change made elsewhere in our UI (e.g. the Settings volume row). */
    fun showCurrent() {
        controller.current()?.let { overlay.show(it) }
    }

    fun release() {
        router.reset()
        overlay.hideNow()
    }

    companion object {
        fun isVolumeKey(keyCode: Int): Boolean = VolumeKey.of(keyCode) != null
    }
}
