package dev.tvlauncher.store.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubReleaseParserTest {

    private val h1 = "8510a517c99927076082917f41bf306dd7a2546c59d492dd11bde18d6e2e4628"

    private val latest = """
        {
          "tag_name": "v0.19.10", "name": "Jellyfin 0.19.10", "draft": false, "prerelease": false,
          "html_url": "https://github.com/jellyfin/jellyfin-androidtv/releases/tag/v0.19.10",
          "assets": [
            {"name": "jellyfin-androidtv-v0.19.10-release.aab", "size": 20231843, "state": "uploaded",
             "browser_download_url": "https://github.com/jellyfin/jellyfin-androidtv/releases/download/v0.19.10/x.aab"},
            {"name": "jellyfin-androidtv-v0.19.10-release.apk", "size": 21950619, "state": "uploaded",
             "digest": "sha256:$h1",
             "browser_download_url": "https://github.com/jellyfin/jellyfin-androidtv/releases/download/v0.19.10/jellyfin-androidtv-v0.19.10-release.apk"},
            {"name": "jellyfin-androidtv-v0.19.10-debug.apk", "size": 28386222, "state": "uploaded",
             "browser_download_url": "https://github.com/jellyfin/jellyfin-androidtv/releases/download/v0.19.10/jellyfin-androidtv-v0.19.10-debug.apk"},
            {"name": "version.txt", "size": 16,
             "browser_download_url": "https://github.com/jellyfin/jellyfin-androidtv/releases/download/v0.19.10/version.txt"}
          ]
        }
    """.trimIndent()

    @Test fun parsesLatestReleaseAndKeepsOnlyApkAssets() {
        val r = GithubReleaseParser.parse(latest)!!
        assertEquals("v0.19.10", r.tag)
        assertEquals("Jellyfin 0.19.10", r.name)
        assertEquals(2, r.apkAssets.size)
        val rel = r.apkAssets.first { it.name.endsWith("release.apk") }
        assertEquals(21950619L, rel.size)
        assertEquals(h1, rel.sha256)
        assertNull(r.apkAssets.first { it.name.endsWith("debug.apk") }.sha256)
    }

    @Test fun ignoresDraftsAndPrereleasesUnlessAsked() {
        val pre = """{"tag_name":"v2-beta","prerelease":true,"assets":[
            {"name":"a.apk","size":1,"browser_download_url":"https://github.com/o/r/releases/download/v2/a.apk"}]}"""
        assertNull(GithubReleaseParser.parse(pre))
        assertEquals("v2-beta", GithubReleaseParser.parse(pre, includePrerelease = true)!!.tag)
        val draft = """{"tag_name":"v3","draft":true,"assets":[]}"""
        assertNull(GithubReleaseParser.parse(draft, includePrerelease = true))
    }

    @Test fun arrayInputPicksFirstEligibleRelease() {
        val arr = """[
            {"tag_name":"v3","draft":true,"assets":[]},
            {"tag_name":"v2","prerelease":true,"assets":[]},
            {"tag_name":"v1","assets":[{"name":"a.apk","size":5,"browser_download_url":"https://github.com/o/r/releases/download/v1/a.apk"}]}
        ]"""
        assertEquals("v1", GithubReleaseParser.parse(arr)!!.tag)
        assertEquals("v2", GithubReleaseParser.parse(arr, includePrerelease = true)!!.tag)
    }

    @Test fun dropsAssetsWithForeignOrInsecureUrls() {
        val json = """{"tag_name":"v1","assets":[
            {"name":"a.apk","size":1,"browser_download_url":"http://github.com/o/r/releases/download/v1/a.apk"},
            {"name":"b.apk","size":1,"browser_download_url":"https://github.com.evil.com/b.apk"},
            {"name":"c.apk","size":1,"browser_download_url":"https://github.com@evil.com/c.apk"},
            {"name":"d.apk","size":1,"browser_download_url":"https://github.com/o/r/releases/download/v1/d.apk"}
        ]}"""
        assertEquals(listOf("d.apk"), GithubReleaseParser.parse(json)!!.apkAssets.map { it.name })
    }

    @Test fun ignoresAssetsNotFullyUploadedAndMalformedDigests() {
        val json = """{"tag_name":"v1","assets":[
            {"name":"a.apk","size":1,"state":"starter","browser_download_url":"https://github.com/o/r/releases/download/v1/a.apk"},
            {"name":"b.apk","size":1,"digest":"md5:abcd","browser_download_url":"https://github.com/o/r/releases/download/v1/b.apk"},
            {"name":"c.apk","size":-4,"digest":"sha256:nothex","browser_download_url":"https://github.com/o/r/releases/download/v1/c.apk"}
        ]}"""
        val assets = GithubReleaseParser.parse(json)!!.apkAssets
        assertEquals(listOf("b.apk", "c.apk"), assets.map { it.name })
        assertNull(assets[0].sha256)
        assertNull(assets[1].sha256)
        assertEquals(-1L, assets[1].size)
    }

    @Test fun neverThrowsOnJunk() {
        for (junk in listOf(null, "", "null", "{", "[]", "42", "\"x\"", "{\"assets\": 7}", "{\"tag_name\": {}}",
            "{\"assets\":[1,null,\"x\",{\"name\":5}]}", "<html>rate limited</html>", "[".repeat(5000))) {
            val r = GithubReleaseParser.parse(junk)
            if (r != null) assertTrue(r.apkAssets.isEmpty())
        }
    }

    @Test fun missingTagFallsBackToNameOrPlaceholder() {
        val r = GithubReleaseParser.parse("""{"name":"Spring build","assets":[]}""")!!
        assertEquals("Spring build", r.tag)
        assertFalse(r.prerelease)
    }

    @Test fun parsesApiErrorMessage() {
        assertEquals("API rate limit exceeded for 1.2.3.4.",
            GithubReleaseParser.errorMessage("""{"message":"API rate limit exceeded for 1.2.3.4.","documentation_url":"x"}"""))
        assertNull(GithubReleaseParser.errorMessage("nope"))
    }
}
