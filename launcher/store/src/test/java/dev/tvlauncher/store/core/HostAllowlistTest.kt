package dev.tvlauncher.store.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostAllowlistTest {

    @Test fun githubDownloadHostsAccepted() {
        assertTrue(HostAllowlist.isGithubAssetUrl("https://github.com/o/r/releases/download/v1/a.apk"))
        assertTrue(HostAllowlist.isGithubAssetUrl("https://objects.githubusercontent.com/github-production-release-asset-2e65be/1/2?x=y"))
        assertTrue(HostAllowlist.isGithubAssetUrl("https://release-assets.githubusercontent.com/github-production-release-asset/1/2"))
        assertTrue(HostAllowlist.isGithubAssetUrl("https://GitHub.com/o/r/releases/download/v1/a.apk"))
    }

    @Test fun lookalikeHostsRejected() {
        assertFalse(HostAllowlist.isGithubAssetUrl("https://github.com.evil.com/a.apk"))
        assertFalse(HostAllowlist.isGithubAssetUrl("https://evilgithub.com/a.apk"))
        assertFalse(HostAllowlist.isGithubAssetUrl("https://raw.githubusercontent.com.attacker.net/a.apk"))
        assertFalse(HostAllowlist.isGithubAssetUrl("https://githubusercontent.com.attacker.net/a.apk"))
        assertFalse(HostAllowlist.isGithubAssetUrl("https://evilgithubusercontent.com/a.apk"))
        assertFalse(HostAllowlist.isGithubAssetUrl("https://www.github.com.attacker.net/a.apk"))
        assertFalse(HostAllowlist.isGithubAssetUrl("https://github.co/a.apk"))
    }

    @Test fun userInfoTricksRejected() {
        assertFalse(HostAllowlist.isGithubAssetUrl("https://github.com@evil.com/x.apk"))
        assertFalse(HostAllowlist.isGithubAssetUrl("https://evil.com#@github.com/x.apk"))
        assertFalse(HostAllowlist.isGithubAssetUrl("https://evil.com\\@github.com/x.apk"))
        assertFalse(HostAllowlist.isGithubAssetUrl("https://evil.com?@github.com/x.apk"))
    }

    @Test fun nonAsciiAndPunycodeRejected() {
        assertFalse(HostAllowlist.isGithubAssetUrl("https://gіthub.com/x.apk")) // Cyrillic i
        assertFalse(HostAllowlist.isGithubAssetUrl("https://xn--gthub-n4a.com/x.apk"))
        assertFalse(HostAllowlist.isGithubAssetHost("xn--objects-.githubusercontent.com"))
    }

    @Test fun httpAndPortsRejected() {
        assertFalse(HostAllowlist.isGithubAssetUrl("http://github.com/o/r/releases/download/v1/a.apk"))
        assertFalse(HostAllowlist.isGithubAssetUrl("https://github.com:8443/o/r/a.apk"))
        assertFalse(HostAllowlist.isGithubAssetUrl("https://github.com./o/r/a.apk"))
    }

    @Test fun githubApiHostIsExact() {
        assertTrue(HostAllowlist.isGithubApiUrl("https://api.github.com/repos/o/r/releases/latest"))
        assertFalse(HostAllowlist.isGithubApiUrl("https://api.github.com.evil.com/repos/o/r"))
        assertFalse(HostAllowlist.isGithubApiUrl("https://github.com/repos/o/r"))
    }

    @Test fun fdroidHostIsExact() {
        assertTrue(HostAllowlist.isFdroidUrl("https://f-droid.org/repo/index-v2.json"))
        assertTrue(HostAllowlist.isFdroidUrl("https://f-droid.org/repo/org.videolan.vlc_1.apk"))
        assertFalse(HostAllowlist.isFdroidUrl("https://f-droid.org.evil.com/repo/x.apk"))
        assertFalse(HostAllowlist.isFdroidUrl("https://evilf-droid.org/repo/x.apk"))
        assertFalse(HostAllowlist.isFdroidUrl("http://f-droid.org/repo/x.apk"))
    }
}
