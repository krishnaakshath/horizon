package dev.tvlauncher.store.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubReleaseParserTest {

    private val ref = GithubRepoRef("o", "r")
    private val jf = GithubRepoRef("jellyfin", "jellyfin-androidtv")

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
        val r = GithubReleaseParser.parse(latest, jf)!!
        assertEquals("v0.19.10", r.tag)
        assertEquals("Jellyfin 0.19.10", r.name)
        assertEquals(2, r.apkAssets.size)
        val rel = r.apkAssets.first { it.name.endsWith("release.apk") }
        assertEquals(21950619L, rel.size)
        assertEquals("sha256:$h1", rel.sha256)
        assertEquals(HashExpectation.Sha256(h1), HashExpectation.of(rel.sha256))
        assertNull(r.apkAssets.first { it.name.endsWith("debug.apk") }.sha256)
    }

    @Test fun ignoresDraftsAndPrereleasesUnlessAsked() {
        val pre = """{"tag_name":"v2-beta","prerelease":true,"assets":[
            {"name":"a.apk","size":1,"browser_download_url":"https://github.com/o/r/releases/download/v2/a.apk"}]}"""
        assertNull(GithubReleaseParser.parse(pre, ref))
        assertEquals("v2-beta", GithubReleaseParser.parse(pre, ref, includePrerelease = true)!!.tag)
        val draft = """{"tag_name":"v3","draft":true,"assets":[]}"""
        assertNull(GithubReleaseParser.parse(draft, ref, includePrerelease = true))
    }

    @Test fun arrayInputPicksFirstEligibleRelease() {
        val arr = """[
            {"tag_name":"v3","draft":true,"assets":[]},
            {"tag_name":"v2","prerelease":true,"assets":[]},
            {"tag_name":"v1","assets":[{"name":"a.apk","size":5,"browser_download_url":"https://github.com/o/r/releases/download/v1/a.apk"}]}
        ]"""
        assertEquals("v1", GithubReleaseParser.parse(arr, ref)!!.tag)
        assertEquals("v2", GithubReleaseParser.parse(arr, ref, includePrerelease = true)!!.tag)
    }

    @Test fun dropsAssetsWithForeignOrInsecureUrls() {
        val json = """{"tag_name":"v1","assets":[
            {"name":"a.apk","size":1,"browser_download_url":"http://github.com/o/r/releases/download/v1/a.apk"},
            {"name":"b.apk","size":1,"browser_download_url":"https://github.com.evil.com/b.apk"},
            {"name":"c.apk","size":1,"browser_download_url":"https://github.com@evil.com/c.apk"},
            {"name":"d.apk","size":1,"browser_download_url":"https://github.com/o/r/releases/download/v1/d.apk"}
        ]}"""
        assertEquals(listOf("d.apk"), GithubReleaseParser.parse(json, ref)!!.apkAssets.map { it.name })
    }

    @Test fun ignoresAssetsNotFullyUploadedAndMalformedDigests() {
        val json = """{"tag_name":"v1","assets":[
            {"name":"a.apk","size":1,"state":"starter","browser_download_url":"https://github.com/o/r/releases/download/v1/a.apk"},
            {"name":"b.apk","size":1,"digest":"md5:abcd","browser_download_url":"https://github.com/o/r/releases/download/v1/b.apk"},
            {"name":"c.apk","size":-4,"digest":"sha256:nothex","browser_download_url":"https://github.com/o/r/releases/download/v1/c.apk"}
        ]}"""
        val assets = GithubReleaseParser.parse(json, ref)!!.apkAssets
        assertEquals(listOf("b.apk", "c.apk"), assets.map { it.name })
        assertNull(assets[0].sha256) // md5 is not a sha256 digest: nothing to verify against
        assertEquals("sha256:nothex", assets[1].sha256) // kept verbatim so the download fails closed
        assertTrue(HashExpectation.of(assets[1].sha256) is HashExpectation.Malformed)
        assertEquals(-1L, assets[1].size)
    }

    @Test fun neverThrowsOnJunk() {
        for (junk in listOf(null, "", "null", "{", "[]", "42", "\"x\"", "{\"assets\": 7}", "{\"tag_name\": {}}",
            "{\"assets\":[1,null,\"x\",{\"name\":5}]}", "<html>rate limited</html>", "[".repeat(5000))) {
            val r = GithubReleaseParser.parse(junk, ref)
            if (r != null) assertTrue(r.apkAssets.isEmpty())
        }
    }

    @Test fun missingTagFallsBackToNameOrPlaceholder() {
        val r = GithubReleaseParser.parse("""{"name":"Spring build","assets":[]}""", ref)!!
        assertEquals("Spring build", r.tag)
        assertFalse(r.prerelease)
    }

    @Test fun parsesApiErrorMessage() {
        assertEquals("API rate limit exceeded for 1.2.3.4.",
            GithubReleaseParser.errorMessage("""{"message":"API rate limit exceeded for 1.2.3.4.","documentation_url":"x"}"""))
        assertNull(GithubReleaseParser.errorMessage("nope"))
    }

    private fun one(url: String, tag: String = "v1", name: String = "a.apk", r: GithubRepoRef = ref) =
        GithubReleaseParser.parse("""{"tag_name":"$tag","assets":[{"name":"$name","size":1,"browser_download_url":"$url"}]}""", r)!!.apkAssets

    @Test fun assetMustBelongToTheRequestedRepository() {
        assertEquals(1, one("https://github.com/o/r/releases/download/v1/a.apk").size)
        assertEquals(1, one("https://github.com/O/R/releases/download/v1/a.apk").size) // case-insensitive owner/repo
        assertTrue(one("https://github.com/evil/r/releases/download/v1/a.apk").isEmpty())
        assertTrue(one("https://github.com/o/other/releases/download/v1/a.apk").isEmpty())
        assertTrue(one("https://github.com/o/r/releases/download/v2/a.apk").isEmpty()) // other tag
        assertTrue(one("https://github.com/o/r/releases/download/v1/b.apk").isEmpty()) // other file
        assertTrue(one("https://github.com/o/r/raw/main/a.apk").isEmpty())
        assertTrue(one("https://github.com/o/r/releases/download/v1/a.apk?x=1").isEmpty())
    }

    @Test fun hostileHostsAreRejected() {
        assertTrue(one("https://raw.githubusercontent.com/o/r/main/a.apk").isEmpty())
        assertTrue(one("https://objects.githubusercontent.com/o/r/releases/download/v1/a.apk").isEmpty())
        assertTrue(one("https://github.com@evil.com/o/r/releases/download/v1/a.apk").isEmpty())
        assertTrue(one("https://user@github.com/o/r/releases/download/v1/a.apk").isEmpty())
        assertTrue(one("https://github.com.evil.com/o/r/releases/download/v1/a.apk").isEmpty())
        assertTrue(one("https://github.com/o/r/releases/download/v1/../../evil/r/a.apk").isEmpty())
    }

    @Test fun tagWithSlashAndEncodedNameMatch() {
        assertEquals(1, one("https://github.com/o/r/releases/download/release/1.0/a.apk", tag = "release/1.0").size)
        assertEquals(1, one("https://github.com/o/r/releases/download/v1/my%20app.apk", name = "my app.apk").size)
    }

    @Test fun releaseNameIsSanitisedForDisplay() {
        val r = GithubReleaseParser.parse("""{"tag_name":"v1\u202e","name":"Evil\u202egpj.exe\nline","assets":[]}""", ref)!!
        assertEquals("Evilgpj.exe line", r.name)
        assertEquals("v1", r.displayTag)
    }
}
