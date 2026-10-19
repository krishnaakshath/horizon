package dev.tvlauncher.store.core

/** Maps PackageInstaller status codes (+ the system's status message) to plain-language outcomes. */
object InstallStatusMapper {

    // Values of android.content.pm.PackageInstaller.STATUS_* (public, stable API constants).
    const val STATUS_PENDING_USER_ACTION = -1
    const val STATUS_SUCCESS = 0
    const val STATUS_FAILURE = 1
    const val STATUS_FAILURE_BLOCKED = 2
    const val STATUS_FAILURE_ABORTED = 3
    const val STATUS_FAILURE_INVALID = 4
    const val STATUS_FAILURE_CONFLICT = 5
    const val STATUS_FAILURE_STORAGE = 6
    const val STATUS_FAILURE_INCOMPATIBLE = 7
    const val STATUS_FAILURE_TIMEOUT = 8

    enum class Kind { NEEDS_USER, SUCCESS, CANCELLED, FAILURE }

    data class Outcome(val kind: Kind, val title: String, val message: String, val detail: String?)

    fun map(status: Int, systemMessage: String?): Outcome {
        val m = systemMessage.orEmpty()
        val detail = systemMessage?.takeIf { it.isNotBlank() }
        fun fail(msg: String) = Outcome(Kind.FAILURE, "Not installed", msg, detail)
        return when (status) {
            STATUS_PENDING_USER_ACTION -> Outcome(Kind.NEEDS_USER, "Confirm on screen", "Android is asking you to confirm the install.", detail)
            STATUS_SUCCESS -> Outcome(Kind.SUCCESS, "Installed", "The app is installed and ready to open.", detail)
            STATUS_FAILURE_ABORTED -> Outcome(Kind.CANCELLED, "Install cancelled", "The install was cancelled. Nothing was changed.", detail)
            STATUS_FAILURE_BLOCKED -> fail("Android blocked this install. A device policy or security setting may not allow it.")
            STATUS_FAILURE_INVALID -> when {
                m.contains("DOWNGRADE", ignoreCase = true) ->
                    fail("A newer version of this app is already installed. Android can't replace it with an older one.")
                else -> fail("The app file looks damaged or is not a valid Android app.")
            }
            STATUS_FAILURE_CONFLICT -> fail(
                "This app is signed differently from the version already installed, or conflicts with another app. " +
                    "To switch versions you would have to uninstall the existing app first (its data would be lost).",
            )
            STATUS_FAILURE_STORAGE -> fail("There isn't enough free space on the TV. Remove some apps or files and try again.")
            STATUS_FAILURE_INCOMPATIBLE -> when {
                m.contains("ABI", ignoreCase = true) ->
                    fail("This app isn't compatible with this TV's processor. Try a different build (arm64 / armeabi-v7a).")
                m.contains("SDK", ignoreCase = true) ->
                    fail("This app isn't compatible with this TV's Android version.")
                else -> fail("This app isn't compatible with this TV.")
            }
            STATUS_FAILURE_TIMEOUT -> fail("The install took too long and was stopped. Please try again.")
            STATUS_FAILURE -> fail("Android couldn't install the app.")
            else -> fail("Android couldn't install the app (status $status).")
        }
    }
}
