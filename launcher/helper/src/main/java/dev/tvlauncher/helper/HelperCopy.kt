package dev.tvlauncher.helper

/**
 * Plain words for the Helper's own screen. On TCL TVs the memory manager can stop the Helper;
 * then only its extras in other apps pause. The launcher, power-on and the home screen do not
 * depend on it.
 */
object HelperCopy {
    const val RUNNING = "On ✓"
    const val PAUSED = "Paused by the TV for now"

    const val MEMORY_NOTE = "This TV may pause the Helper to save memory. Double-Back, the volume bar and " +
        "the controller's Guide button then rest in other apps until it runs again. The launcher, " +
        "power-on and the home screen keep working."

    /** Value of the "Remote shortcuts" row while the service is switched on. */
    fun status(running: Boolean): String = if (running) RUNNING else PAUSED
}
