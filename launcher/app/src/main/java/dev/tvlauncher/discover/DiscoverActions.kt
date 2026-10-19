package dev.tvlauncher.discover

/** What selecting a catalog card does. Pure. */
object DiscoverActions {
    enum class Action { OPEN, OPEN_PLAY_STORE, OPEN_GET_APPS, NONE }

    /**
     * Installed → OPEN. Play apps → the Play Store. F-Droid apps → Get Apps when it is installed,
     * else the Play Store if the app is also listed there. GitHub apps → Get Apps only.
     */
    fun decide(app: PopularApp, installedPackages: Set<String>, getAppsInstalled: Boolean): Action {
        if (!PlayStoreLinks.isValidPackage(app.packageName)) return Action.NONE
        if (app.packageName in installedPackages) return Action.OPEN
        return when (app.source) {
            is Source.Play -> Action.OPEN_PLAY_STORE
            is Source.Fdroid -> when {
                getAppsInstalled && GetAppsLink.forSource(app.source) != null -> Action.OPEN_GET_APPS
                app.onPlay -> Action.OPEN_PLAY_STORE
                else -> Action.NONE
            }
            is Source.Github ->
                if (getAppsInstalled && GetAppsLink.forSource(app.source) != null) Action.OPEN_GET_APPS else Action.NONE
        }
    }

    fun label(action: Action): String = when (action) {
        Action.OPEN -> "Open"
        Action.OPEN_PLAY_STORE -> "Install"
        Action.OPEN_GET_APPS -> "Get via Get Apps"
        Action.NONE -> "Not available"
    }
}

/**
 * Hand-off to the Get Apps companion (`dev.tvlauncher.store`). Today it exposes only its
 * MAIN/LEANBACK_LAUNCHER entry, so the launcher starts that and adds these extras as a hint;
 * Get Apps ignores them until it implements the contract below.
 *
 * Extras: [EXTRA_SOURCE] = "fdroid" | "github", [EXTRA_VALUE] = package id or owner/repo.
 */
data class GetAppsLink(val source: String, val value: String) {
    companion object {
        const val PACKAGE = "dev.tvlauncher.store"
        const val EXTRA_SOURCE = "dev.tvlauncher.store.extra.SOURCE"
        const val EXTRA_VALUE = "dev.tvlauncher.store.extra.VALUE"
        private val REPO = Regex("^[A-Za-z0-9-]{1,39}/[A-Za-z0-9_.-]{1,100}$")

        fun forSource(source: Source): GetAppsLink? = when (source) {
            is Source.Fdroid -> source.packageName.takeIf { PlayStoreLinks.isValidPackage(it) }?.let { GetAppsLink("fdroid", it) }
            is Source.Github -> source.repo.takeIf { REPO.matches(it) && it.split('/').none { p -> p == "." || p == ".." } }
                ?.let { GetAppsLink("github", it) }
            is Source.Play -> null
        }
    }
}
