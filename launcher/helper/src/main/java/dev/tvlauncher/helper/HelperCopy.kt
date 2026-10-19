package dev.tvlauncher.helper

/**
 * Plain words for the Helper's own screen. On TCL TVs the memory manager can stop the Helper;
 * then only its extras in other apps pause. The launcher, power-on and the home screen do not
 * depend on it.
 */
object HelperCopy {
    const val RUNNING = "On ✓"
    const val PAUSED = "Paused by the TV for now"

    /** One calm line: on this TV the Helper can be paused; the launcher itself never depends on it. */
    const val MEMORY_NOTE = "May pause on this TV"

    /** Value of the "Remote shortcuts" row while the service is switched on. */
    fun status(running: Boolean): String = if (running) RUNNING else PAUSED
}
