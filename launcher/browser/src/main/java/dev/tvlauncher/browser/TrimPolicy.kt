package dev.tvlauncher.browser

/**
 * onTrimMemory levels (ComponentCallbacks2 values) -> what to drop. UI_HIDDEN (20) is only a
 * "went to background" notice and drops nothing. Thumbnails/icons are caches (reloadable);
 * inactive tabs' back/forward states are dropped only at RUNNING_CRITICAL or COMPLETE.
 */
object TrimPolicy {
    enum class Action { NONE, DROP_CACHES, DROP_ALL }

    const val RUNNING_MODERATE = 5
    const val RUNNING_LOW = 10
    const val RUNNING_CRITICAL = 15
    const val UI_HIDDEN = 20
    const val BACKGROUND = 40
    const val MODERATE = 60
    const val COMPLETE = 80

    fun actionFor(level: Int): Action = when (level) {
        RUNNING_MODERATE -> Action.NONE
        RUNNING_LOW -> Action.DROP_CACHES
        RUNNING_CRITICAL -> Action.DROP_ALL
        UI_HIDDEN -> Action.NONE
        BACKGROUND, MODERATE -> Action.DROP_CACHES
        COMPLETE -> Action.DROP_ALL
        else -> when {
            level >= COMPLETE -> Action.DROP_ALL
            level >= BACKGROUND -> Action.DROP_CACHES
            else -> Action.NONE
        }
    }
}
