package dev.tvlauncher.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverModelTest {
    private fun ids(s: DiscoverState) = s.sections.flatMap { sec -> sec.items.map { it.app.packageName } }

    @Test fun sections_nonEmpty_andRecommendedFirst() {
        val s = DiscoverModel.build(emptySet(), null, emptySet())
        assertTrue(s.sections.isNotEmpty())
        assertEquals(DiscoverModel.RECOMMENDED_KEY, s.sections.first().key)
        assertEquals("Recommended for you", s.sections.first().title)
        s.sections.forEach { assertTrue(it.key, it.items.isNotEmpty()) }
        assertFalse(s.isEmpty)
    }

    @Test fun installed_areExcluded_andListedSeparately() {
        val s = DiscoverModel.build(setOf("com.netflix.ninja", "com.spotify.tv.android"), null, emptySet())
        assertFalse("com.netflix.ninja" in ids(s))
        assertFalse("com.spotify.tv.android" in ids(s))
        assertEquals(setOf("com.netflix.ninja", "com.spotify.tv.android"), s.installed.map { it.packageName }.toSet())
    }

    @Test fun hidden_areExcluded() {
        val netflixId = PopularApps.byPackage("com.netflix.ninja")!!.id
        val s = DiscoverModel.build(emptySet(), null, setOf(netflixId))
        assertFalse("com.netflix.ninja" in ids(s))
    }

    @Test fun noDuplicates_acrossSections() {
        val all = ids(DiscoverModel.build(emptySet(), "IN", emptySet()))
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun regionIN_recommended_startsWithIndianPicks() {
        val rec = DiscoverModel.build(emptySet(), "IN", emptySet()).sections.first().items.map { it.app.packageName }
        assertEquals(listOf("in.startv.hotstar", "com.graymatrix.did", "com.sonyliv"), rec.take(3))
    }

    @Test fun regionIN_recommended_skipsInstalledPicks() {
        val rec = DiscoverModel.build(setOf("in.startv.hotstar"), "IN", emptySet()).sections.first().items
        assertEquals("com.graymatrix.did", rec.first().app.packageName)
    }

    @Test fun regionNull_noRegionalApps() {
        val s = DiscoverModel.build(emptySet(), null, emptySet())
        assertTrue(s.sections.all { sec -> sec.items.all { it.app.regions.isEmpty() } })
    }

    @Test fun regionUS_includesHulu() {
        assertTrue("com.hulu.livingroomplus" in ids(DiscoverModel.build(emptySet(), "US", emptySet())))
        assertFalse("com.hulu.livingroomplus" in ids(DiscoverModel.build(emptySet(), "IN", emptySet())))
    }

    @Test fun categorySections_orderedByWeightThenLabel() {
        val s = DiscoverModel.build(emptySet(), "US", emptySet())
        s.sections.drop(1).forEach { sec ->
            val apps = sec.items.map { it.app }
            val sorted = apps.sortedWith(DiscoverModel.ORDER)
            assertEquals(sec.key, sorted, apps)
            assertTrue(apps.all { it.category.name == sec.key })
        }
    }

    @Test fun ordering_isStable_acrossCalls() {
        val a = DiscoverModel.build(setOf("com.netflix.ninja"), "IN", emptySet())
        val b = DiscoverModel.build(setOf("com.netflix.ninja"), "IN", emptySet())
        assertEquals(a, b)
    }

    @Test fun ordering_comparator_weightDesc_thenLabel() {
        val base = PopularApps.all.first()
        val x = base.copy(id = "x", label = "Beta", popularity = 50)
        val y = base.copy(id = "y", label = "alpha", popularity = 50)
        val z = base.copy(id = "z", label = "Zed", popularity = 90)
        assertEquals(listOf(z, y, x), listOf(x, y, z).sortedWith(DiscoverModel.ORDER))
    }

    @Test fun everythingInstalled_isEmptyState() {
        val s = DiscoverModel.build(PopularApps.all.map { it.packageName }.toSet(), "IN", emptySet())
        assertTrue(s.isEmpty)
        assertTrue(s.sections.isEmpty())
    }

    @Test fun recommended_isCapped() {
        val rec = DiscoverModel.build(emptySet(), "US", emptySet()).sections.first()
        assertTrue(rec.items.size in 1..DiscoverModel.RECOMMENDED_MAX)
    }

    @Test fun items_carryAction() {
        val s = DiscoverModel.build(emptySet(), null, emptySet(), getAppsInstalled = false)
        val kodi = s.sections.flatMap { it.items }.first { it.app.packageName == "org.xbmc.kodi" }
        assertEquals(DiscoverActions.Action.OPEN_PLAY_STORE, kodi.action)
        val s2 = DiscoverModel.build(emptySet(), null, emptySet(), getAppsInstalled = true)
        val kodi2 = s2.sections.flatMap { it.items }.first { it.app.packageName == "org.xbmc.kodi" }
        assertEquals(DiscoverActions.Action.OPEN_PLAY_STORE, kodi2.action)
    }

    @Test fun categoryLine_mentionsUnverifiedTv() {
        val hotstar = PopularApps.byPackage("in.startv.hotstar")!!
        assertTrue(DiscoverModel.subtitle(hotstar).contains("TV version unconfirmed"))
        assertEquals("Video", DiscoverModel.subtitle(PopularApps.byPackage("com.netflix.ninja")!!))
    }
}
