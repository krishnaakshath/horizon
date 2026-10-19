package dev.tvlauncher.store

import dev.tvlauncher.store.core.FdroidApp
import dev.tvlauncher.store.core.GithubAsset
import dev.tvlauncher.store.core.GithubRelease
import dev.tvlauncher.store.core.GithubRepoRef
import dev.tvlauncher.store.core.HashExpectation
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
        assertTrue(job.hopAllowed(job.url, 0))
        assertFalse(job.hopAllowed("https://mirror.example.org/repo/org.videolan.vlc_1.apk", 1))
        assertFalse(job.hopAllowed("http://f-droid.org/repo/org.videolan.vlc_1.apk", 1))
    }

    @Test fun githubDownloadsStayOnGithubHosts() {
        val asset = GithubAsset("a.apk", 5, "https://github.com/o/r/releases/download/v1/a.apk", sha)
        val job = DownloadJob.fromGithub(GithubRepoRef("o", "r"), GithubRelease("v1", "v1", false, listOf(asset)), asset)
        assertEquals("v1", job.releaseTag)
        assertEquals(sha, job.expectedSha256)
        assertTrue(job.hopAllowed(job.url, 0))
        assertFalse("hop 0 must be exactly the validated URL", job.hopAllowed("https://github.com/evil/r/releases/download/v1/a.apk", 0))
        assertTrue(job.hopAllowed("https://release-assets.githubusercontent.com/x?sig=1", 1))
        assertTrue(job.hopAllowed("https://objects.githubusercontent.com/x?sig=1", 2))
        assertFalse(job.hopAllowed("https://raw.githubusercontent.com/o/r/main/a.apk", 1))
        assertFalse(job.hopAllowed("https://evil.example/x.apk", 1))
        assertFalse(job.hopAllowed("https://github.com.evil.com/x.apk", 1))
        assertEquals(HashExpectation.Sha256(sha), job.published)
    }

    @Test fun linkDownloadsFollowUrlPolicyOnEveryHop() {
        val job = DownloadJob.fromLink("https://example.org/dl/app.apk?x=1")
        assertEquals("app.apk", job.title)
        assertTrue(job.hopAllowed("https://cdn.example.net/app.apk", 1))
        assertFalse(job.hopAllowed("http://cdn.example.net/app.apk", 1))
        assertFalse(job.hopAllowed("https://192.168.1.1/app.apk", 1))
        assertFalse(job.hopAllowed("https://localhost/app.apk", 1))
        assertEquals(HashExpectation.None, job.published)
        // installed.json keeps host + path only; the download itself still uses the full URL
        assertEquals("https://example.org/dl/app.apk", job.ref)
        assertEquals("https://example.org/dl/app.apk?x=1", job.url)
    }
}
