package dev.tvlauncher.homesections

import dev.tvlauncher.appsettings.HomeSectionsPage
import dev.tvlauncher.homelayout.ScreenDp
import dev.tvlauncher.settings.SettingsBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSectionsTest {
    private val c = HomeSections.CLASSIC

    @Test fun classic_isOnlyApps_andDefault() {
        assertTrue(c.isClassic)
        assertEquals(listOf(SectionType.APPS), c.enabled.map { it.type })
        assertEquals(SectionType.APPS, c.focusRow.type)
        assertEquals(c, HomeSections.fromJson(null))
        assertEquals(c, HomeSections.fromJson(""))
        assertEquals(SectionPreset.CLASSIC, c.matchingPreset())
    }

    @Test fun everyBuiltIn_presentOnce() {
        for (p in SectionPreset.values()) {
            val s = HomeSections.preset(p)
            val types = s.sections.map { it.type }
            for (t in SectionType.values()) if (t != SectionType.GROUP) assertEquals(1, types.count { it == t })
            if (p != SectionPreset.EVERYTHING) assertEquals(p, s.matchingPreset())
            assertEquals(HomeSections.presetTypes(p), s.enabled.map { it.type })
            assertTrue(s.enabled.all { it.type.drawn }) // presets use only sections Home draws
        }
        val g = c.addGroup("Kids")!!
        assertEquals(SectionPreset.EVERYTHING, HomeSections.preset(SectionPreset.EVERYTHING, g.groups).matchingPreset())
    }

    @Test fun favoritesRecent_preset_order() {
        val m = HomeSections.preset(SectionPreset.FAVORITES_RECENT)
        assertEquals(listOf(SectionType.FAVORITES, SectionType.RECENT, SectionType.APPS), m.enabled.map { it.type })
        assertEquals(SectionType.FAVORITES, m.focusRow.type)
        assertFalse(m.isClassic)
    }

    @Test fun undrawnTypes_loadTurnedOff_andCannotBeEnabled() {
        val json = """{"version":1,"preset":"MEDIA","sections":[
            {"id":"CONTINUE","type":"CONTINUE","enabled":true},{"id":"APPS","type":"APPS","enabled":true},
            {"id":"NOW_PLAYING","type":"NOW_PLAYING","enabled":true},{"id":"CLOCK","type":"CLOCK","enabled":true}]}"""
        val s = HomeSections.fromJson(json)
        assertTrue(s.isClassic)
        assertNull(s.preset)
        assertTrue(s.byId("CONTINUE") != null) // kept in the model for later
        for (t in SectionType.values()) if (!t.drawn) assertFalse(c.setEnabled(t.name, true).byId(t.name)!!.enabled)
        // Only Apps on, every undrawn type off: still Classic.
        assertTrue(c.setEnabled("WEATHER", true).isClassic)
    }

    @Test fun appsSection_ignoresStyleFields() {
        val s = c.setStyle("APPS", SectionStyle.GRID).setMaxItems("APPS", 3)
        assertEquals(SectionStyle.ROW, s.byId("APPS")!!.style)
        assertEquals(HomeSection.DEFAULT_MAX, s.byId("APPS")!!.maxItems)
    }

    @Test fun enableDisable_neverEmpty() {
        val off = c.setEnabled("APPS", false)
        assertTrue(off.isClassic) // nothing on falls back to Apps
        val fav = c.toggle("FAVORITES")
        assertEquals(listOf(SectionType.APPS, SectionType.FAVORITES), fav.enabled.map { it.type })
        assertNull(fav.matchingPreset())
    }

    @Test fun move_reorders_andClamps() {
        val s = c.setEnabled("FAVORITES", true).move("FAVORITES", -1)
        assertEquals(listOf(SectionType.FAVORITES, SectionType.APPS), s.enabled.map { it.type })
        assertEquals(SectionPreset.FAVORITES_APPS, s.matchingPreset())
        assertEquals(s.sections, s.move("FAVORITES", -5).sections)
        assertEquals(s, s.move("nope", 1))
    }

    @Test fun groups_addRenameToggleDelete_capAtFour() {
        var s = c
        for (i in 1..4) s = s.addGroup("Kids $i", listOf("com.a", "com.a", "bad pkg!", "com.b"))!!
        assertEquals(4, s.groups.size)
        assertNull(s.addGroup("Fifth"))
        assertNull(c.addGroup("   "))
        val g = s.groups.first()
        assertEquals(listOf("com.a", "com.b"), g.apps)
        assertTrue(g.enabled)
        s = s.toggleGroupApp(g.id, "com.a")
        assertEquals(listOf("com.b"), s.byId(g.id)!!.apps)
        s = s.toggleGroupApp(g.id, "com.c")
        assertEquals(listOf("com.b", "com.c"), s.byId(g.id)!!.apps)
        assertEquals("Films", s.renameGroup(g.id, "  Films ")!!.byId(g.id)!!.name)
        s = s.deleteGroup(g.id)
        assertEquals(3, s.groups.size)
        assertNotNull(s.addGroup("Again")) // the freed id is reused
        assertEquals(s, s.deleteGroup("APPS")) // built-ins cannot be deleted
    }

    @Test fun preset_keepsGroups_onOnlyForEverything() {
        val s = c.addGroup("Music")!!
        val p = HomeSections.preset(SectionPreset.EVERYTHING, s.groups)
        assertEquals(1, p.groups.size)
        assertTrue(p.groups[0].enabled)
        assertFalse(HomeSections.preset(SectionPreset.FAVORITES_RECENT, s.groups).groups[0].enabled)
    }

    @Test fun json_roundTrip() {
        val s = HomeSections.preset(SectionPreset.EVERYTHING).addGroup("Kids", listOf("com.x"))!!
            .setStyle("FAVORITES", SectionStyle.GRID).setSize("RECENT", SectionSize.L).setShowTitle("FAVORITES", false)
            .setMaxItems("RECENT", 7).move("group.1", -3)
        assertEquals(s, HomeSections.fromJson(s.toJson()))
    }

    @Test fun hostileInput_neverThrows_andClamps() {
        val bad = listOf(
            "{", "[]", "null", "{\"version\":99,\"sections\":[]}", "{\"version\":1}", "{\"version\":1,\"sections\":5}",
            "{\"version\":1,\"sections\":[{\"id\":\"APPS\",\"type\":\"NOPE\"}]}", "x".repeat(100_000)
        )
        for (b in bad) assertTrue(b.take(20), HomeSections.fromJson(b).isClassic)
        val weird = """{"version":1,"sections":[
            {"id":"APPS","type":"APPS","enabled":"yes","max":100000,"style":"HUGE","size":7},
            {"id":"RECENT","type":"RECENT","enabled":true,"max":100000,"style":"HUGE","size":7},
            {"id":"APPS","type":"APPS","enabled":true},
            {"id":"FAKE","type":"WEATHER","enabled":true},
            {"id":"group.1","type":"GROUP","enabled":true,"name":"","apps":["a"]},
            {"id":"group.2","type":"GROUP","enabled":true,"name":"Ok\u0000Name that is far far too long for a row","apps":["../x","com.ok",3]},
            {"id":"x","type":"GROUP","enabled":true,"name":"Bad id"}]}"""
        val s = HomeSections.fromJson(weird)
        val recent = s.byId("RECENT")!!
        assertEquals(HomeSection.MAX_MAX, recent.maxItems)
        assertEquals(SectionStyle.ROW, recent.style)
        assertFalse(s.byId("APPS")!!.enabled) // "yes" is not a boolean: off (Recent keeps the home non-empty)

        assertFalse(s.byId("WEATHER")!!.enabled) // a mismatched id is dropped, the built-in re-added off
        assertEquals(1, s.groups.size)
        assertEquals(HomeSections.MAX_NAME, s.groups[0].name.length)
        assertEquals(listOf("com.ok"), s.groups[0].apps)
        assertEquals(1, s.sections.count { it.id == "APPS" })
    }

    @Test fun settingsBackup_carriesSections() {
        val s = HomeSections.preset(SectionPreset.FAVORITES_RECENT)
        val text = SettingsBackup.toText(dev.tvlauncher.settings.LauncherSettings(), sections = s)
        assertEquals(s.enabled, SettingsBackup.parseRestored(text)!!.sections!!.enabled)
        assertNull(SettingsBackup.parseRestored(SettingsBackup.toText(dev.tvlauncher.settings.LauncherSettings()))!!.sections)
    }

    @Test fun configBackup_andProfiles_includeTheStore() {
        assertTrue(HomeSectionsPrefs.FILE in dev.tvlauncher.setup.ConfigBackup.STORES)
        assertTrue(HomeSectionsPrefs.FILE in dev.tvlauncher.profiles.ProfileStores.SCOPED)
    }

    @Test fun settingsPages_withinBudget() {
        assertTrue(HomeSectionsPage.spec.withinBudget)
        assertTrue(HomeSectionsPage.detailSpec.withinBudget)
        assertEquals("Classic", HomeSectionsPage.presetLabel(c))
        assertEquals("Custom", HomeSectionsPage.presetLabel(c.toggle("RECENT")))
    }

    // --- focus traversal ---------------------------------------------------------------------

    @Test fun nav_keepsColumn_clampsToNearest_andExits() {
        val counts = listOf(3, 10, 0, 5)
        assertEquals(SectionMove.Focus(1, 7), SectionNav.move(counts, 0, 7, SectionNav.Key.DOWN))
        assertEquals(SectionMove.Focus(0, 2), SectionNav.move(counts, 1, 7, SectionNav.Key.UP))
        assertEquals(SectionMove.Focus(3, 4), SectionNav.move(counts, 1, 7, SectionNav.Key.DOWN)) // empty row skipped
        assertEquals(SectionMove.TopBar, SectionNav.move(counts, 0, 0, SectionNav.Key.UP))
        assertEquals(SectionMove.Spotlight, SectionNav.move(counts, 3, 0, SectionNav.Key.DOWN))
        assertEquals(SectionMove.Pass, SectionNav.move(counts, 1, 1, SectionNav.Key.LEFT))
    }

    // --- sizing ------------------------------------------------------------------------------

    @Test fun tilesAcross_perResolution() {
        val row = HomeSection("APPS", SectionType.APPS, enabled = true)
        assertEquals(4, SectionLayout.tilesAcross(row, 1f, ScreenDp.TV))
        assertEquals(5, SectionLayout.tilesAcross(row, 1f, ScreenDp.MDPI_720))
        assertEquals(8, SectionLayout.tilesAcross(row, 1f, ScreenDp.XHDPI_4K))
        val compact = row.copy(style = SectionStyle.COMPACT)
        assertTrue(SectionLayout.tilesAcross(compact, 1f, ScreenDp.TV) > SectionLayout.tilesAcross(row, 1f, ScreenDp.TV))
    }

    @Test fun lite720_showsAtMostTwoSections_withinSafeArea() {
        val all = HomeSections.preset(SectionPreset.EVERYTHING, c.addGroup("A")!!.addGroup("B")!!.groups)
        for (screen in listOf(ScreenDp.TV, ScreenDp.MDPI_720, ScreenDp.XHDPI_4K)) {
            for (lite in listOf(true, false)) {
                for (scale in listOf(0.85f, 1f, 1.2f)) {
                    val shown = SectionRows.shown(all, scale, screen, lite)
                    assertTrue(shown.any { it.type == SectionType.APPS })
                    assertTrue(shown.size <= SectionLayout.maxVisible(lite, screen))
                    if (shown.size > 1) assertTrue(shown.sumOf { SectionLayout.rowHeightDp(it, scale) } <= SectionLayout.budgetDp(screen))
                }
            }
        }
        assertEquals(2, SectionLayout.maxVisible(true, ScreenDp.TV))
        assertEquals(listOf(SectionType.APPS), SectionRows.shown(c, 1f, ScreenDp.TV, true).map { it.type })
    }

    @Test fun paging_coversEveryRow() {
        val rows = HomeSections.preset(SectionPreset.EVERYTHING, c.addGroup("A")!!.addGroup("B")!!.groups).enabled
        var start = 0
        var pages = 0
        while (start < rows.size) {
            val n = SectionLayout.pageSize(rows, start, 1f, ScreenDp.TV, true)
            assertTrue(n in 1..2)
            for (i in start until start + n) assertEquals(start, SectionLayout.pageStart(rows, i, 1f, ScreenDp.TV, true))
            start += n; pages++
        }
        assertTrue(pages >= rows.size / 2)
    }

    @Test fun preview_blocksStayInside() {
        for (p in SectionPreset.values()) for (b in SectionPreview.blocks(HomeSections.preset(p))) {
            assertTrue(b.left >= 0f && b.right <= 1f && b.top >= 0f && b.bottom <= 1f && b.left < b.right)
        }
        assertEquals(1, SectionPreview.blocks(c).count { it.part == SectionPreview.Part.TILE_FOCUSED })
        assertEquals(0, SectionPreview.blocks(HomeSections.fromJson(
            """{"version":1,"sections":[{"id":"CLOCK","type":"CLOCK","enabled":true},{"id":"APPS","type":"APPS","enabled":true}]}"""
        )).count { it.part == SectionPreview.Part.CARD })
    }
}
