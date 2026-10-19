package dev.tvlauncher.store.core

/** Facts about a downloaded APK and the device, gathered before the confirmation screen. */
data class ApkFacts(
    /** null when the file could not be parsed as an APK. */
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    /** SHA-256 fingerprints of the APK's signing certificate(s), including rotation history when available. */
    val signers: Set<String>,
    val permissions: List<String>,
    /** true = declares a leanback launcher activity / leanback feature; false = phone-style app; null = unknown. */
    val tvReady: Boolean?,
    /** Installed version, or null when the package is not installed. */
    val installedVersionCode: Long?,
    val installedSigners: Set<String>?,
    /** Package id the source promised (F-Droid entry); null for plain links / GitHub. */
    val expectedPackage: String?,
    /** Signer fingerprint the source published (F-Droid index); null when not published. */
    val expectedSigner: String?,
    /** Whether the source published a SHA-256 that the download was verified against. */
    val sourceHadHash: Boolean,
    val deviceSdk: Int,
)

/** Blockers stop the install (the Install button is disabled); warnings are shown prominently. */
data class Review(
    val blockers: List<String>,
    val warnings: List<String>,
    val isUpdate: Boolean,
    val signer: SignerCompare.Verdict,
) {
    val canInstall: Boolean get() = blockers.isEmpty()
}

object InstallReview {

    const val SIGNATURE_DIFFERS = "Signature differs: this will not install over the existing app"
    const val NOT_FOR_TV = "Not designed for TV; may need a mouse/touch"

    fun evaluate(f: ApkFacts): Review {
        val blockers = ArrayList<String>()
        val warnings = ArrayList<String>()
        val installed = f.installedVersionCode != null
        val verdict = SignerCompare.verdict(if (installed) f.installedSigners ?: emptySet() else null, f.signers)

        if (f.packageName == null) {
            blockers += "This file isn't a valid Android app."
            return Review(blockers, warnings, false, verdict)
        }
        when (verdict) {
            SignerCompare.Verdict.DIFFERENT -> blockers += SIGNATURE_DIFFERS
            SignerCompare.Verdict.UNSIGNED -> blockers += "This app has no valid signature, so Android can't install it."
            SignerCompare.Verdict.INSTALLED_UNKNOWN ->
                warnings += "Couldn't read the signature of the installed version; Android will check it during the install."
            else -> Unit
        }
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
        if (iv != null) {
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
        val sensitive = f.permissions.distinct().count { PermissionRisk.isSensitive(it) }
        if (sensitive > 0) {
            warnings += if (sensitive == 1) "Requests 1 sensitive permission (highlighted below)."
            else "Requests $sensitive sensitive permissions (highlighted below)."
        }
        return Review(blockers, warnings, installed, verdict)
    }
}
