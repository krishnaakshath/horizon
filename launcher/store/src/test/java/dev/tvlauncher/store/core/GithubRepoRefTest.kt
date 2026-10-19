package dev.tvlauncher.store.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubRepoRefTest {

    @Test fun parsesOwnerSlashRepo() {
        val r = GithubRepoRef.parse("jellyfin/jellyfin-androidtv")!!
        assertEquals("jellyfin", r.owner)
        assertEquals("jellyfin-androidtv", r.repo)
        assertEquals("jellyfin/jellyfin-androidtv", r.slug)
        assertEquals("https://api.github.com/repos/jellyfin/jellyfin-androidtv/releases/latest", r.latestReleaseApiUrl)
    }

    @Test fun acceptsPastedGithubUrlAndTrims() {
        assertEquals("o/r", GithubRepoRef.parse("  https://github.com/o/r  ")!!.slug)
        assertEquals("o/r", GithubRepoRef.parse("https://github.com/o/r.git")!!.slug)
        assertEquals("o/r", GithubRepoRef.parse("github.com/o/r/")!!.slug)
        assertEquals("o/r", GithubRepoRef.parse("https://github.com/o/r/releases")!!.slug)
    }

    @Test fun rejectsInvalidInput() {
        for (bad in listOf("", "owner", "/repo", "owner/", "a/b/c", "o w/r", "o/r?x", "../etc", "o/..", "./r",
            "a".repeat(40) + "/r", "o/" + "r".repeat(101), "o/r#x", "o/ŕ", "https://evil.com/o/r",
            "https://github.com.evil.com/o/r", "http://github.com/o/r")) {
            assertNull(bad, GithubRepoRef.parse(bad))
        }
    }

    @Test fun regexBoundaries() {
        assertEquals("a".repeat(39) + "/" + "r".repeat(100),
            GithubRepoRef.parse("a".repeat(39) + "/" + "r".repeat(100))!!.slug)
        assertEquals("A_b.c-d/R.e_p-o", GithubRepoRef.parse("A_b.c-d/R.e_p-o")!!.slug)
    }
}
