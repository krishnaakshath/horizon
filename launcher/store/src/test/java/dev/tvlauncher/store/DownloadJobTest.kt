package dev.tvlauncher.store

import dev.tvlauncher.store.core.FdroidApp
import dev.tvlauncher.store.core.GithubAsset
import dev.tvlauncher.store.core.GithubRelease
import dev.tvlauncher.store.core.GithubRepoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every redirect hop is checked against the rule of the source the download started from. */
class DownloadJobTest {

    private val sha = "ab".repeat(32)

    @Test fun fdroidDownloadsStayOnFdroid() {
        val app = FdroidApp("org.videolan.vlc", "VLC", "", "3.7.2", 1, "org.videolan.vlc_1.apk", sha, 10, null, 21,
            false, true, emptyList(), emptyList())
        val job = DownloadJob.fromFdroid(app)
        assertEquals("https://f-droid.org/repo/org.videolan.vlc_1.apk", job.url)
        assertEquals(sha, job.expectedSha256)
        assertEquals("org.videolan.vlc", job.expectedPackage)
        assertTrue(job.hopAllowed(job.url))
        assertFalse(job.hopAllowed("https://mirror.example.org/repo/org.videolan.vlc_1.apk"))
        assertFalse(job.hopAllowed("http://f-droid.org/repo/org.videolan.vlc_1.apk"))
    }

    @Test fun githubDownloadsStayOnGithubHosts() {
        val asset = GithubAsset("a.apk", 5, "https://github.com/o/r/releases/download/v1/a.apk", sha)
        val job = DownloadJob.fromGithub(GithubRepoRef("o", "r"), GithubRelease("v1", "v1", false, listOf(asset)), asset)
        assertEquals("v1", job.releaseTag)
        assertEquals(sha, job.expectedSha256)
        assertTrue(job.hopAllowed("https://release-assets.githubusercontent.com/x?sig=1"))
        assertFalse(job.hopAllowed("https://evil.example/x.apk"))
        assertFalse(job.hopAllowed("https://github.com.evil.com/x.apk"))
    }

    @Test fun linkDownloadsFollowUrlPolicyOnEveryHop() {
        val job = DownloadJob.fromLink("https://example.org/dl/app.apk?x=1")
        assertEquals("app.apk", job.title)
        assertTrue(job.hopAllowed("https://cdn.example.net/app.apk"))
        assertFalse(job.hopAllowed("http://cdn.example.net/app.apk"))
        assertFalse(job.hopAllowed("https://192.168.1.1/app.apk"))
        assertFalse(job.hopAllowed("https://localhost/app.apk"))
    }
}
