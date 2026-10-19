package dev.tvlauncher.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopularAppsTest {
    private val pkgRegex = Regex("^[A-Za-z0-9_.]+$")

    @Test fun catalog_hasAboutFortyApps() {
        assertTrue("size ${PopularApps.all.size}", PopularApps.all.size in 35..60)
    }

    @Test fun ids_unique() {
        val ids = PopularApps.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun packages_unique_andValid() {
        val pkgs = PopularApps.all.map { it.packageName }
        assertEquals(pkgs.size, pkgs.toSet().size)
        pkgs.forEach {
            assertTrue(it, pkgRegex.matches(it))
            assertTrue(it, PlayStoreLinks.isValidPackage(it))
        }
    }

    @Test fun every_entry_hasColorLabelAndCategory() {
        PopularApps.all.forEach {
            assertNotEquals(it.id, 0, it.brandColor)
            assertEquals("opaque ${it.id}", 0xFF, it.brandColor ushr 24)
            assertTrue(it.id, it.label.isNotBlank())
            assertTrue(it.id, it.id.isNotBlank())
        }
    }

    @Test fun regions_areIsoAlpha2Upper() {
        PopularApps.all.flatMap { it.regions }.forEach { assertTrue(it, Regex("^[A-Z]{2}$").matches(it)) }
    }

    @Test fun indianRegionalApps_areTaggedIN() {
        listOf("in.startv.hotstar", "com.graymatrix.did", "com.sonyliv", "com.mxtech.videoplayer.ad",
            "com.gaana", "com.jio.media.jiobeats", "tv.accedo.airtel.wynk").forEach { pkg ->
            val app = PopularApps.byPackage(pkg)!!
            assertEquals(pkg, setOf("IN"), app.regions)
        }
    }

    @Test fun globalApps_haveEmptyRegions() {
        listOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.spotify.tv.android").forEach {
            assertTrue(it, PopularApps.byPackage(it)!!.regions.isEmpty())
        }
    }

    @Test fun forRegion_null_returnsOnlyGlobal() {
        val list = PopularApps.forRegion(null)
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { it.regions.isEmpty() })
    }

    @Test fun forRegion_IN_includesIndianAndGlobal_notUsOnly() {
        val list = PopularApps.forRegion("in")
        assertTrue(list.any { it.packageName == "com.graymatrix.did" })
        assertTrue(list.any { it.packageName == "com.netflix.ninja" })
        assertFalse(list.any { it.packageName == "com.hulu.livingroomplus" })
    }

    @Test fun forRegion_garbage_isTreatedAsUnknown() {
        assertEquals(PopularApps.forRegion(null), PopularApps.forRegion("not a country"))
        assertEquals(PopularApps.forRegion(null), PopularApps.forRegion(""))
    }

    @Test fun phoneOnlyOrUnverifiedTv_isNotClaimedTvReady() {
        // Play listing showed no TV form factor when verified (2026-10-20).
        listOf("in.startv.hotstar", "com.gaana", "tv.accedo.airtel.wynk", "com.google.android.apps.youtube.kids")
            .forEach { assertFalse(it, PopularApps.byPackage(it)!!.tvReady) }
        assertTrue(PopularApps.byPackage("com.netflix.ninja")!!.tvReady)
    }

    @Test fun droppedIds_areAbsent() {
        // 404 on Play at verification time, or not available for Android TV.
        listOf("com.jio.media.ondemand", "com.hbo.hbonow", "com.apple.android.music", "deezer.android.tv",
            "in.startv.hotstartvonly", "com.google.android.youtube.tvkids")
            .forEach { assertEquals(it, null, PopularApps.byPackage(it)) }
    }

    @Test fun kodi_isFdroidSource() {
        val kodi = PopularApps.byPackage("org.xbmc.kodi")!!
        assertEquals(Source.Fdroid("org.xbmc.kodi"), kodi.source)
    }

    @Test fun brandColorFor_reusesBrandColors_thenCatalog_thenNull() {
        assertEquals(0xFFE50914.toInt(), PopularApps.brandColorFor("com.netflix.ninja"))
        assertEquals(PopularApps.byPackage("com.crunchyroll.crunchyroid")!!.brandColor,
            PopularApps.brandColorFor("com.crunchyroll.crunchyroid"))
        assertEquals(null, PopularApps.brandColorFor("com.example.unknown"))
    }

    @Test fun everyCategory_isPresent() {
        val cats = PopularApps.all.map { it.category }.toSet()
        AppCategory.values().forEach { assertTrue(it.name, it in cats) }
    }
}
