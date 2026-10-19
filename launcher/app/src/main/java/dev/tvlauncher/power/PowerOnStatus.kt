package dev.tvlauncher.power

/** Pure wording for the Settings page: which way forward the TV has, in plain words. */
object PowerOnStatus {
    enum class Readiness { HELPER, OVERLAY, ANDROID, NEEDS_PERMISSION }

    fun readiness(helperReady: Boolean, overlayAllowed: Boolean, sdk: Int): Readiness = when {
        helperReady -> Readiness.HELPER
        overlayAllowed -> Readiness.OVERLAY
        sdk >= 31 -> Readiness.ANDROID // Android 12+ let the default home app start itself
        else -> Readiness.NEEDS_PERMISSION
    }

    fun value(r: Readiness): String = when (r) {
        Readiness.HELPER -> "Remote helper"
        Readiness.OVERLAY, Readiness.ANDROID -> "Allowed"
        Readiness.NEEDS_PERMISSION -> "Needed"
    }

    fun subtitle(r: Readiness): String = when (r) {
        Readiness.HELPER -> "The Remote helper brings home forward"
        Readiness.OVERLAY -> "Display over other apps is on for TV Launcher"
        Readiness.ANDROID -> "This Android version allows it"
        Readiness.NEEDS_PERMISSION -> "Turn on Display over other apps for TV Launcher"
    }

    fun ready(r: Readiness): Boolean = r != Readiness.NEEDS_PERMISSION
}
