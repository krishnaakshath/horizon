package dev.tvlauncher.setup

/**
 * What this TV can do and what it already has, probed at runtime ([SetupProber]); nothing is
 * assumed. Pure data so [SetupPlan] is testable on the JVM.
 */
data class SetupProbe(
    val isHome: Boolean = false,
    /** A system screen for choosing the home app resolves on this TV. */
    val homeChooser: Boolean = false,
    val listenerGranted: Boolean = false,
    /** The notification-access screen resolves on this TV. */
    val listenerScreen: Boolean = false,
    /** The launcher was granted WRITE_SECURE_SETTINGS (only possible with the one-time setup helper). */
    val canWriteSecure: Boolean = false,
    val helperInstalled: Boolean = false,
    val helperEnabled: Boolean = false,
    val accessibilityScreen: Boolean = false,
    val locationGranted: Boolean = false,
    /** Asked before and the TV no longer shows the permission dialog. */
    val locationBlocked: Boolean = false,
    val picturesGranted: Boolean = false,
    val picturesBlocked: Boolean = false,
    val weatherInstalled: Boolean = false,
    val weatherScreen: Boolean = false,
    val getAppsInstalled: Boolean = false,
    /** Titles of the companion apps that are not installed, in display order. */
    val missingCompanions: List<String> = emptyList(),
    val usageGranted: Boolean = false,
    val usageScreen: Boolean = false,
    val roomAutomatic: Boolean = true,
    val lite: Boolean = false
)

enum class StepId { HOME, NOW_PLAYING, SHORTCUTS, NEARBY, PHOTOS, WEATHER, APPS, INSIGHTS, TUNED }

/** DONE = ticked; ACTION = one press away; HELPER = needs the one-time setup helper; SKIPPED by the user. */
enum class StepState { DONE, ACTION, HELPER, SKIPPED }

enum class StepAction {
    NONE, CHOOSE_HOME, AUTO_ENABLE, OPEN_LISTENER_SCREEN, OPEN_ACCESSIBILITY, REQUEST_LOCATION,
    REQUEST_PICTURES, OPEN_WEATHER, OPEN_GET_APPS, OPEN_USAGE_SCREEN
}

data class SetupStep(
    val id: StepId,
    val title: String,
    val detail: String,
    val state: StepState,
    val action: StepAction = StepAction.NONE,
    val actionLabel: String = ""
) {
    val done: Boolean get() = state == StepState.DONE
}

/** Remembered progress that changes what a step offers (see [SetupStore]). */
data class SetupMemory(
    val skipped: Set<StepId> = emptySet(),
    /** Steps whose automatic fix was already attempted (a failed one falls back to a screen or the helper). */
    val autoTried: Set<StepId> = emptySet(),
    /** Steps whose companion screen was opened once (the app keeps its own state). */
    val visited: Set<StepId> = emptySet()
)

/** Decides the checklist for THIS TV. Pure: same probe and memory, same steps. */
object SetupPlan {
    fun steps(p: SetupProbe, m: SetupMemory = SetupMemory()): List<SetupStep> = listOfNotNull(
        home(p), nowPlaying(p, m), shortcuts(p, m), nearby(p), photos(p), weather(p, m), apps(p), insights(p), tuned(p)
    ).map { s -> if (s.state != StepState.DONE && s.id in m.skipped) s.copy(state = StepState.SKIPPED) else s }

    /** The single primary action: the first step one press away, else null ("Go to Home"). */
    fun next(steps: List<SetupStep>): SetupStep? = steps.firstOrNull { it.state == StepState.ACTION }

    fun allDone(steps: List<SetupStep>): Boolean = steps.none { it.state == StepState.ACTION }

    /** Short progress line, e.g. "5 of 8 ready". */
    fun progress(steps: List<SetupStep>): String = "${steps.count { it.done }} of ${steps.size} ready"

    private fun home(p: SetupProbe) = when {
        p.isHome -> SetupStep(StepId.HOME, SetupCopy.HOME_TITLE, SetupCopy.HOME_DONE, StepState.DONE)
        p.homeChooser -> SetupStep(StepId.HOME, SetupCopy.HOME_TITLE, SetupCopy.HOME_ACTION, StepState.ACTION,
            StepAction.CHOOSE_HOME, SetupCopy.HOME_LABEL)
        else -> SetupStep(StepId.HOME, SetupCopy.HOME_TITLE, SetupCopy.HOME_HELPER, StepState.HELPER)
    }

    private fun nowPlaying(p: SetupProbe, m: SetupMemory) = when {
        p.listenerGranted -> SetupStep(StepId.NOW_PLAYING, SetupCopy.NP_TITLE, SetupCopy.NP_DONE, StepState.DONE)
        p.canWriteSecure && StepId.NOW_PLAYING !in m.autoTried ->
            SetupStep(StepId.NOW_PLAYING, SetupCopy.NP_TITLE, SetupCopy.NP_AUTO, StepState.ACTION, StepAction.AUTO_ENABLE, SetupCopy.NP_LABEL)
        p.listenerScreen -> SetupStep(StepId.NOW_PLAYING, SetupCopy.NP_TITLE, SetupCopy.NP_ACTION, StepState.ACTION,
            StepAction.OPEN_LISTENER_SCREEN, SetupCopy.NP_LABEL)
        else -> SetupStep(StepId.NOW_PLAYING, SetupCopy.NP_TITLE, SetupCopy.NP_HELPER, StepState.HELPER)
    }

    /** Only while the Helper app is installed (otherwise the companion-apps step offers it). */
    private fun shortcuts(p: SetupProbe, m: SetupMemory): SetupStep? = when {
        !p.helperInstalled -> null
        p.helperEnabled -> SetupStep(StepId.SHORTCUTS, SetupCopy.SC_TITLE, SetupCopy.SC_DONE, StepState.DONE)
        p.canWriteSecure && StepId.SHORTCUTS !in m.autoTried ->
            SetupStep(StepId.SHORTCUTS, SetupCopy.SC_TITLE, SetupCopy.SC_AUTO, StepState.ACTION, StepAction.AUTO_ENABLE, SetupCopy.SC_LABEL)
        p.accessibilityScreen -> SetupStep(StepId.SHORTCUTS, SetupCopy.SC_TITLE, SetupCopy.SC_ACTION, StepState.ACTION,
            StepAction.OPEN_ACCESSIBILITY, SetupCopy.SC_LABEL)
        else -> SetupStep(StepId.SHORTCUTS, SetupCopy.SC_TITLE, SetupCopy.SC_HELPER, StepState.HELPER)
    }

    private fun nearby(p: SetupProbe) = when {
        p.locationGranted -> SetupStep(StepId.NEARBY, SetupCopy.NEAR_TITLE, SetupCopy.NEAR_DONE, StepState.DONE)
        p.locationBlocked -> SetupStep(StepId.NEARBY, SetupCopy.NEAR_TITLE, SetupCopy.NEAR_HELPER, StepState.HELPER)
        else -> SetupStep(StepId.NEARBY, SetupCopy.NEAR_TITLE, SetupCopy.NEAR_ACTION, StepState.ACTION,
            StepAction.REQUEST_LOCATION, SetupCopy.NEAR_LABEL)
    }

    private fun photos(p: SetupProbe) = when {
        p.picturesGranted -> SetupStep(StepId.PHOTOS, SetupCopy.PH_TITLE, SetupCopy.PH_DONE, StepState.DONE)
        p.picturesBlocked -> SetupStep(StepId.PHOTOS, SetupCopy.PH_TITLE, SetupCopy.PH_HELPER, StepState.HELPER)
        else -> SetupStep(StepId.PHOTOS, SetupCopy.PH_TITLE, SetupCopy.PH_ACTION, StepState.ACTION,
            StepAction.REQUEST_PICTURES, SetupCopy.PH_LABEL)
    }

    /** Only while the Weather app is installed; its city lives in that app, so one visit ticks it. */
    private fun weather(p: SetupProbe, m: SetupMemory): SetupStep? = when {
        !p.weatherInstalled -> null
        StepId.WEATHER in m.visited || !p.weatherScreen ->
            SetupStep(StepId.WEATHER, SetupCopy.WX_TITLE, SetupCopy.WX_DONE, StepState.DONE)
        else -> SetupStep(StepId.WEATHER, SetupCopy.WX_TITLE, SetupCopy.WX_ACTION, StepState.ACTION,
            StepAction.OPEN_WEATHER, SetupCopy.WX_LABEL)
    }

    private fun apps(p: SetupProbe) = when {
        p.missingCompanions.isEmpty() -> SetupStep(StepId.APPS, SetupCopy.APPS_TITLE, SetupCopy.APPS_DONE, StepState.DONE)
        p.getAppsInstalled -> SetupStep(StepId.APPS, SetupCopy.APPS_TITLE, SetupCopy.appsMissing(p.missingCompanions),
            StepState.ACTION, StepAction.OPEN_GET_APPS, SetupCopy.APPS_LABEL)
        else -> SetupStep(StepId.APPS, SetupCopy.APPS_TITLE, SetupCopy.appsHelper(p.missingCompanions), StepState.HELPER)
    }

    private fun insights(p: SetupProbe) = when {
        p.usageGranted -> SetupStep(StepId.INSIGHTS, SetupCopy.IN_TITLE, SetupCopy.IN_DONE, StepState.DONE)
        p.usageScreen -> SetupStep(StepId.INSIGHTS, SetupCopy.IN_TITLE, SetupCopy.IN_ACTION, StepState.ACTION,
            StepAction.OPEN_USAGE_SCREEN, SetupCopy.IN_LABEL)
        else -> SetupStep(StepId.INSIGHTS, SetupCopy.IN_TITLE, SetupCopy.IN_HELPER, StepState.HELPER)
    }

    /** Effects profile, room lighting and sounds: applied automatically, shown as one tick. */
    private fun tuned(p: SetupProbe) =
        SetupStep(StepId.TUNED, SetupCopy.TUNED_TITLE, SetupCopy.tuned(p.lite, p.roomAutomatic), StepState.DONE)
}
