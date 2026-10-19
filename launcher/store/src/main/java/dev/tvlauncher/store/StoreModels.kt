package dev.tvlauncher.store

import dev.tvlauncher.store.core.FdroidApp
import dev.tvlauncher.store.core.GithubRelease
import dev.tvlauncher.store.core.GithubRepoRef
import dev.tvlauncher.store.core.HostAllowlist
import dev.tvlauncher.store.core.InstalledRecord
import dev.tvlauncher.store.core.UrlPolicy

/** Everything needed to download, check and record one APK, whatever the source. */
data class DownloadJob(
    val title: String,
    val url: String,
    val source: InstalledRecord.Source,
    /** F-Droid package id, GitHub slug or the link itself (stored for "Check update"). */
    val ref: String,
    /** Release tag (GitHub) recorded on success; null = use the APK's versionCode. */
    val releaseTag: String?,
    val expectedSha256: String?,
    val declaredSize: Long,
    val expectedPackage: String?,
    val expectedSigner: String?,
) {
    /** Per-source rule applied to the first URL and every redirect hop. */
    fun hopAllowed(u: String): Boolean = when (source) {
        InstalledRecord.Source.LINK -> UrlPolicy.check(u, maxLength = 8192) is UrlPolicy.Verdict.Allowed
        InstalledRecord.Source.FDROID -> HostAllowlist.isFdroidUrl(u)
        InstalledRecord.Source.GITHUB -> HostAllowlist.isGithubAssetUrl(u)
    }

    companion object {
        fun fromLink(url: String) = DownloadJob(
            title = url.substringAfterLast('/').substringBefore('?').ifEmpty { "Download" }.take(60),
            url = url, source = InstalledRecord.Source.LINK, ref = url, releaseTag = null,
            expectedSha256 = null, declaredSize = -1, expectedPackage = null, expectedSigner = null,
        )

        fun fromFdroid(app: FdroidApp) = DownloadJob(
            title = app.name, url = app.apkUrl, source = InstalledRecord.Source.FDROID, ref = app.packageName,
            releaseTag = null, expectedSha256 = app.sha256, declaredSize = app.size,
            expectedPackage = app.packageName, expectedSigner = app.signerSha256,
        )

        fun fromGithub(ref: GithubRepoRef, release: GithubRelease, asset: dev.tvlauncher.store.core.GithubAsset) = DownloadJob(
            title = asset.name, url = asset.url, source = InstalledRecord.Source.GITHUB, ref = ref.slug,
            releaseTag = release.tag, expectedSha256 = asset.sha256, declaredSize = asset.size,
            expectedPackage = null, expectedSigner = null,
        )
    }
}
