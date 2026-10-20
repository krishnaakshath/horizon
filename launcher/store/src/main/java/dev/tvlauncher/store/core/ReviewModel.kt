package dev.tvlauncher.store.core

/**
 * Pure layout model of the review screen: decides what goes where and makes every APK-supplied string safe.
 *
 * APK-controlled text (versionName, permission names) is sanitised, and permission names are rendered only
 * when they look like real permission identifiers (`^[A-Za-z0-9_.]+$`). Anything else (e.g. a fake
 * "✓ checked: no sensitive capabilities" entry) is counted as "unrecognized" and never shown raw.
 * APK-supplied rows are drawn with a fixed neutral bullet in a dimmed style by the UI, so they can't pass for
 * M02 Store's own status lines.
 */
object ReviewModel {

    const val MAX_PERMISSIONS = 40
    const val MAX_LEFT_CAPABILITIES = 5
    const val VERSION_MAX = 40
    const val PERMISSION_MAX = 100

    private val PERMISSION_NAME = Regex("^[A-Za-z0-9_.]+$")

    data class PermissionRow(val label: String, val name: String, val level: PermissionRisk.Level)

    data class Model(
        /** Sensitive capabilities (HIGH, MEDIUM), most severe first. */
        val sensitiveCapabilities: List<ComponentRisk.RiskEntry>,
        /** INFO capabilities, for a muted "Also declares" line; not counted as sensitive. */
        val infoCapabilities: List<ComponentRisk.RiskEntry>,
        /** Compact list next to the Install button (up to [MAX_LEFT_CAPABILITIES]). */
        val leftCapabilities: List<ComponentRisk.RiskEntry>,
        val leftMore: Int,
        val permissions: List<PermissionRow>,
        val permissionsMore: Int,
        val unrecognizedPermissions: Int,
        val totalPermissions: Int,
        val versionText: String,
    )

    fun isRecognizedPermissionName(name: String?): Boolean =
        name != null && name.length <= 255 && PERMISSION_NAME.matches(name)

    fun versionText(versionName: String?, versionCode: Long): String =
        "${DisplayText.sanitize(versionName, VERSION_MAX).ifEmpty { "?" }} ($versionCode)"

    fun build(f: ApkFacts): Model {
        val sensitiveCaps = f.capabilities.filter { it.severity != ComponentRisk.Severity.INFO }
        val infoCaps = f.capabilities.filter { it.severity == ComponentRisk.Severity.INFO }
        val all = f.permissions.distinct()
        val recognized = all.filter { isRecognizedPermissionName(it) }
        val sorted = PermissionRisk.sort(recognized)
        val rows = sorted.take(MAX_PERMISSIONS).map {
            PermissionRow(
                label = DisplayText.sanitize(PermissionRisk.label(it), PERMISSION_MAX),
                name = DisplayText.sanitize(it, PERMISSION_MAX),
                level = PermissionRisk.classify(it),
            )
        }
        return Model(
            sensitiveCapabilities = sensitiveCaps,
            infoCapabilities = infoCaps,
            leftCapabilities = sensitiveCaps.take(MAX_LEFT_CAPABILITIES),
            leftMore = (sensitiveCaps.size - MAX_LEFT_CAPABILITIES).coerceAtLeast(0),
            permissions = rows,
            permissionsMore = (sorted.size - rows.size).coerceAtLeast(0),
            unrecognizedPermissions = all.size - recognized.size,
            totalPermissions = all.size,
            versionText = versionText(f.versionName, f.versionCode),
        )
    }
}

/** Outcome of looking up an installed package, without shared mutable state. */
sealed class InstalledLookupResult<out T> {
    data class Found<T>(val value: T) : InstalledLookupResult<T>()
    object NotFound : InstalledLookupResult<Nothing>()
    data class Failed(val reason: String) : InstalledLookupResult<Nothing>()

    companion object {
        /**
         * Below API 30 there is no visibility filtering, so not-found is trusted. On API 30+ our `<queries>`
         * cover every LAUNCHER / LEANBACK_LAUNCHER app, so not-found is trusted when the incoming APK declares
         * such an activity; otherwise the package might simply be hidden from us → UNKNOWN.
         */
        fun state(result: InstalledLookupResult<*>, sdkInt: Int, apkDeclaresLauncher: Boolean): InstalledState = when (result) {
            is Found<*> -> InstalledState.INSTALLED
            NotFound -> if (sdkInt < 30 || apkDeclaresLauncher) InstalledState.NOT_INSTALLED else InstalledState.UNKNOWN
            is Failed -> InstalledState.UNKNOWN
        }
    }
}

/**
 * Before telling the user an install was cancelled or timed out, check the package itself: if it now has the
 * APK's versionCode and was actually (re)written after we committed, the install succeeded.
 */
object InstallOutcomeResolver {
    enum class Reported { CANCELLED, TIMED_OUT, FAILED, SUCCESS }

    fun resolve(
        reported: Reported,
        expectedVersionCode: Long,
        previousVersionCode: Long?,
        nowVersionCode: Long?,
        lastUpdateTimeMillis: Long?,
        committedAtMillis: Long,
    ): Reported {
        if (reported == Reported.SUCCESS || reported == Reported.FAILED) return reported
        if (nowVersionCode == null || nowVersionCode != expectedVersionCode) return reported
        val changedVersion = previousVersionCode != expectedVersionCode
        val rewritten = lastUpdateTimeMillis != null && lastUpdateTimeMillis >= committedAtMillis
        return if (changedVersion || rewritten) Reported.SUCCESS else reported
    }
}

/** Which of our own PackageInstaller sessions to abandon (left over after process death). */
object SessionJanitor {
    data class SessionView(val id: Int, val installerPackage: String?)

    fun toAbandon(sessions: List<SessionView>, ourPackage: String, activeSessionId: Int?): List<Int> =
        sessions.filter { it.installerPackage == ourPackage && it.id != activeSessionId }.map { it.id }
}
