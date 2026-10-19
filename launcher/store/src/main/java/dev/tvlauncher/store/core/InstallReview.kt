package dev.tvlauncher.store.core

/** Whether the package is already on the device, as far as we can tell. */
enum class InstalledState {
    INSTALLED,
    NOT_INSTALLED,
    /** Lookup failed in a way that can't distinguish "absent" from "hidden by package visibility". */
    UNKNOWN,
}

/** Facts about a downloaded APK and the device, gathered before the confirmation screen. */
data class ApkFacts(
    /** null when the file could not be parsed as an APK. */
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    /** SHA-256 fingerprints of the APK's CURRENT signing certificate(s). */
    val signers: Set<String>,
    val permissions: List<String>,
    /** true = declares a leanback launcher activity / leanback feature; false = phone-style app; null = unknown. */
    val tvReady: Boolean?,
    /** Installed version, or null when the package is not installed. */
    val installedVersionCode: Long?,
    /** Current signer set of the installed package (empty = installed but unreadable). */
    val installedSigners: Set<String>?,
    /** Package id the source promised (F-Droid entry); null for plain links / GitHub. */
    val expectedPackage: String?,
    /** Signer fingerprint the source published (F-Droid index); null when not published. */
    val expectedSigner: String?,
    /** Whether the source published a SHA-256 that the download was verified against. */
    val sourceHadHash: Boolean,
    val deviceSdk: Int,
    /** Signing lineage of the APK (oldest → current); defaults to the current signers. */
    val signerHistory: Set<String> = signers,
    /** Capabilities from declared components (accessibility, device admin, VPN, keyboard, ...). */
    val capabilities: List<ComponentRisk.RiskEntry> = emptyList(),
    val installedState: InstalledState =
        if (installedVersionCode != null) InstalledState.INSTALLED else InstalledState.NOT_INSTALLED,
)

/** Blockers stop the install (the Install button is disabled); warnings are shown prominently. */
data class Review(
    val blockers: List<String>,
    val warnings: List<String>,
    val isUpdate: Boolean,
    val signer: SignerCompare.Verdict,
    val installedState: InstalledState = if (isUpdate) InstalledState.INSTALLED else InstalledState.NOT_INSTALLED,
    /** Sensitive capabilities + sensitive permissions. */
    val sensitiveCount: Int = 0,
) {
    val canInstall: Boolean get() = blockers.isEmpty()
}

object InstallReview {

    const val SIGNATURE_DIFFERS = "Signature differs: this will not install over the existing app"
    const val NOT_FOR_TV = "Not designed for TV; may need a mouse/touch"
    const val INSTALLED_SIGNER_UNREADABLE = "Can't verify the installed app's signature, so this update is blocked."
    const val INSTALL_STATE_UNKNOWN =
        "Couldn't check whether this app is already installed — Android will still block a different signer."

    fun evaluate(f: ApkFacts): Review {
        val blockers = ArrayList<String>()
        val warnings = ArrayList<String>()
        // A known installed version always means INSTALLED; otherwise trust the lookup's own verdict.
        val state = if (f.installedVersionCode != null) InstalledState.INSTALLED
        else if (f.installedState == InstalledState.INSTALLED) InstalledState.UNKNOWN else f.installedState
        val installed = state == InstalledState.INSTALLED
        val verdict = SignerCompare.verdict(
            if (installed) f.installedSigners ?: emptySet() else null,
            f.signers,
            f.signerHistory,
        )
        val sensitive = f.capabilities.size + f.permissions.distinct().count { PermissionRisk.isSensitive(it) }

        if (f.packageName == null) {
            blockers += "This file isn't a valid Android app."
            return Review(blockers, warnings, false, verdict, state, sensitive)
        }
        when (verdict) {
            SignerCompare.Verdict.DIFFERENT -> blockers += SIGNATURE_DIFFERS
            SignerCompare.Verdict.UNSIGNED -> blockers += "This app has no valid signature, so Android can't install it."
            SignerCompare.Verdict.INSTALLED_UNKNOWN -> blockers += INSTALLED_SIGNER_UNREADABLE
            else -> Unit
        }
        if (state == InstalledState.UNKNOWN) warnings += INSTALL_STATE_UNKNOWN
        if (f.expectedPackage != null && f.expectedPackage != f.packageName) {
            blockers += "The file is “${f.packageName}”, not “${f.expectedPackage}” as the source promised."
        }
        if (SignerCompare.matchesExpected(f.expectedSigner, f.signers) == false) {
            blockers += "The app's signature doesn't match the one the source published."
        }
        if (f.minSdk > f.deviceSdk) {
            blockers += "This app needs a newer Android version than this TV has (API ${f.minSdk} > ${f.deviceSdk})."
        }
        val iv = f.installedVersionCode
        if (installed && iv != null) {
            when {
                f.versionCode < iv -> blockers += "A newer version is already installed; Android can't downgrade it."
                f.versionCode == iv -> warnings += "This exact version is already installed (this will reinstall it)."
            }
        }
        when (f.tvReady) {
            false -> warnings += NOT_FOR_TV
            null -> warnings += "Couldn't tell whether this app is designed for TV."
            true -> Unit
        }
        if (!f.sourceHadHash) {
            warnings += "The source didn't publish a checksum, so the file couldn't be cross-checked. Install only if you trust the link."
        }
        if (sensitive > 0) warnings += summary(sensitive)
        return Review(blockers, warnings, installed, verdict, state, sensitive)
    }

    /** Never says "no sensitive permissions" when capabilities exist: everything sensitive is counted. */
    fun summary(sensitive: Int): String =
        if (sensitive == 1) "Contains 1 sensitive capability (highlighted)." else "Contains $sensitive sensitive capabilities (highlighted)."
}
