package dev.tvlauncher.store.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreRecordsTest {

    @Test fun installedRegistryRoundTripAndUpsert() {
        val r1 = InstalledRecord("org.videolan.vlc", "VLC", InstalledRecord.Source.FDROID, "org.videolan.vlc", "106", 1L)
        val r2 = InstalledRecord("org.x", "X", InstalledRecord.Source.GITHUB, "o/r", "v1", 2L)
        var list = InstalledRegistry.upsert(emptyList(), r1)
        list = InstalledRegistry.upsert(list, r2)
        list = InstalledRegistry.upsert(list, r1.copy(version = "107", installedAt = 3L))
        assertEquals(listOf("org.videolan.vlc", "org.x"), list.map { it.packageName }.sorted())
        assertEquals("107", list.first { it.packageName == "org.videolan.vlc" }.version)
        assertEquals(list, InstalledRegistry.decode(InstalledRegistry.encode(list)))
        assertEquals(listOf(r2), InstalledRegistry.remove(list, "org.videolan.vlc"))
    }

    @Test fun registryDecodeIsTolerant() {
        assertTrue(InstalledRegistry.decode("junk").isEmpty())
        assertTrue(InstalledRegistry.decode("").isEmpty())
        val one = InstalledRegistry.decode("""[{"pkg":"a.b"},{"nope":1},5,{"pkg":"bad pkg!"}]""")
        assertEquals(listOf("a.b"), one.map { it.packageName })
        assertEquals(InstalledRecord.Source.LINK, one[0].source)
    }

    @Test fun packageNameValidation() {
        assertTrue(InstalledRegistry.isValidPackageName("org.videolan.vlc"))
        assertTrue(InstalledRegistry.isValidPackageName("a.b_c1"))
        assertTrue(!InstalledRegistry.isValidPackageName("vlc"))
        assertTrue(!InstalledRegistry.isValidPackageName("1a.b"))
        assertTrue(!InstalledRegistry.isValidPackageName("a..b"))
        assertTrue(!InstalledRegistry.isValidPackageName("a.b;rm"))
    }

    @Test fun linkHistoryDedupesCapsAndKeepsOnlyAllowedUrls() {
        var h = emptyList<String>()
        for (i in 1..15) h = LinkHistory.add(h, "https://example.org/$i.apk")
        h = LinkHistory.add(h, "https://example.org/3.apk")
        h = LinkHistory.add(h, "http://example.org/insecure.apk")
        assertEquals(LinkHistory.MAX, h.size)
        assertEquals("https://example.org/3.apk", h.first())
        assertEquals(1, h.count { it.endsWith("/3.apk") })
        assertEquals(h, LinkHistory.decode(LinkHistory.encode(h)))
        assertTrue(LinkHistory.decode("""["http://x.org/a.apk", 3, "https://ok.org/a.apk"]""") == listOf("https://ok.org/a.apk"))
    }

    @Test fun linkHistoryStoresHostAndPathOnly() {
        val h = LinkHistory.add(emptyList(), "https://cdn.example.org/dl/app.apk?token=SECRET&x=1#frag")
        assertEquals(listOf("https://cdn.example.org/dl/app.apk"), h)
        assertEquals(listOf("https://ok.org/a.apk"), LinkHistory.decode("""["https://ok.org/a.apk?sig=abc"]"""))
    }

    @Test fun stripRemovesUserInfoQueryAndFragment() {
        assertEquals("https://host.example/p/a.apk", LinkHistory.strip("https://user:pw@host.example/p/a.apk?t=1#x"))
        assertEquals("https://host.example", LinkHistory.strip("https://u@host.example"))
        assertEquals("https://host.example/a@b.apk", LinkHistory.strip("https://host.example/a@b.apk"))
    }

    @Test fun registryLabelsAreSanitised() {
        val one = InstalledRegistry.decode("""[{"pkg":"a.b","label":"Evil\u202egpj.exe"}]""")
        assertEquals("Evilgpj.exe", one[0].label)
    }

    @Test fun githubRepoListRoundTrip() {
        val list = listOf("o/r", "jellyfin/jellyfin-androidtv")
        assertEquals(list, RepoList.decode(RepoList.encode(list)))
        assertEquals(listOf("o/r"), RepoList.decode("""["o/r", "bad/../x", 7]"""))
        assertEquals(listOf("a/b", "o/r"), RepoList.add(listOf("o/r"), "a/b"))
        assertEquals(listOf("o/r"), RepoList.add(listOf("o/r"), "o/r"))
    }

    @Test fun formatting() {
        assertEquals("0 B", Format.size(0))
        assertEquals("999 B", Format.size(999))
        assertEquals("1.5 KB", Format.size(1536))
        assertEquals("20.9 MB", Format.size(21950619))
        assertEquals("1.20 GB", Format.size(1288490189))
        assertEquals("—", Format.size(-1))
        assertEquals(37, Format.percent(37, 100))
        assertEquals(-1, Format.percent(5, -1))
        assertEquals(100, Format.percent(200, 100))
    }

    @Test fun releaseTagComparisonIsLoose() {
        assertTrue(Format.sameRelease("v1.2.3", "1.2.3"))
        assertTrue(Format.sameRelease("V1.2.3 ", "v1.2.3"))
        assertTrue(!Format.sameRelease("v1.2.4", "1.2.3"))
        assertTrue(!Format.sameRelease(null, "1.2.3"))
    }
}
