package dev.tvlauncher.power

/**
 * Pure order of the ways to bring home forward, each verified before the next is tried.
 * Measured on a TCL 32S5403AF (Android 11): a direct start is aborted unless the launcher holds
 * "Display over other apps"; the helper's HOME action needs its accessibility service bound.
 */
object PowerOnLadder {
    enum class Step { DIRECT, HELPER }

    const val VERIFY_MS = 1_500L

    /** Direct first when Android allows it, then the helper, then a last direct attempt. */
    fun steps(overlayAllowed: Boolean, helperReady: Boolean, sdk: Int): List<Step> {
        val directAllowed = overlayAllowed || sdk >= 31
        val out = ArrayList<Step>()
        if (directAllowed) out.add(Step.DIRECT)
        if (helperReady) out.add(Step.HELPER)
        if (!directAllowed) out.add(Step.DIRECT)
        return out
    }

    fun label(step: Step?): String = when (step) {
        Step.DIRECT -> "direct"
        Step.HELPER -> "helper"
        null -> "system"
    }
}
