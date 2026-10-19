package dev.tvlauncher.profiles

import dev.tvlauncher.setup.ConfigBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileModelTest {
    private fun ok(r: ProfileModel.Result) = (r as ProfileModel.Result.Ok)

    @Test fun existingInstall_becomesHome_withTheOriginalFiles() {
        val s = ProfileModel.fromJson(null)
        assertEquals(listOf(ProfileModel.HOME_ID), s.profiles.map { it.id })
        assertEquals("Home", s.active.name)
        assertFalse(s.showChip)
        for (store in ProfileStores.SCOPED) assertEquals(store, ProfileStores.route(store, ProfileModel.HOME_ID))
        assertEquals(ProfileModel.initial(), ProfileModel.fromJson("garbage"))
    }

    @Test fun createRenameSwitchDelete() {
        var s = ProfileModel.initial()
        s = ok(ProfileModel.create(s, "  Sam  ", "pabc")).state
        assertEquals("Sam", s.byId("pabc")!!.name)
        assertTrue(s.showChip)
        assertTrue(ProfileModel.create(s, "sam", "pxyz") is ProfileModel.Result.Error)
        assertTrue(ProfileModel.create(s, "  ", "pxyz") is ProfileModel.Result.Error)
        s = ok(ProfileModel.rename(s, "pabc", "Alex")).state
        s = ok(ProfileModel.switchTo(s, "pabc")).state
        assertEquals("pabc", s.activeId)
        s = ok(ProfileModel.delete(s, "pabc")).state
        assertEquals(ProfileModel.HOME_ID, s.activeId)
        assertTrue(ProfileModel.delete(s, ProfileModel.HOME_ID) is ProfileModel.Result.Error) // never the last
    }

    @Test fun maxProfiles_andUniqueIds() {
        var s = ProfileModel.initial()
        for (i in 1 until ProfileModel.MAX_PROFILES) s = ok(ProfileModel.create(s, "P$i", ProfileModel.newId(s) { i * 7 })).state
        assertEquals(ProfileModel.MAX_PROFILES, s.profiles.size)
        assertEquals(s.profiles.size, s.profiles.map { it.id }.toSet().size)
        assertTrue(ProfileModel.create(s, "More", "pzzz") is ProfileModel.Result.Error)
    }

    @Test fun pin_setCheckClear_andJsonRoundTrip() {
        var s = ok(ProfileModel.create(ProfileModel.initial(), "Kid", "pkid")).state
        s = ok(ProfileModel.setPin(s, "pkid", "1234", "salt")).state
        val kid = s.byId("pkid")!!
        assertTrue(ProfileModel.pinOk(kid, "1234"))
        assertFalse(ProfileModel.pinOk(kid, "1111"))
        assertTrue(ProfileModel.setPin(s, "pkid", "12a4", "salt") is ProfileModel.Result.Error)
        s = s.copy(askOnStart = true)
        val back = ProfileModel.fromJson(ProfileModel.toJson(s))
        assertEquals(s, back)
        assertTrue(ProfileModel.shouldAsk(back))
        s = ok(ProfileModel.clearPin(s, "pkid")).state
        assertTrue(ProfileModel.pinOk(s.byId("pkid")!!, null))
    }

    @Test fun fromJson_isTolerant() {
        val s = ProfileModel.fromJson("""{"profiles":[{"id":"BAD ID"},{"id":"pa","name":""},{"id":"pa","name":"Dup"}],"active":"nope"}""")
        assertEquals(listOf("pa"), s.profiles.map { it.id })
        assertEquals("pa", s.activeId)
    }

    @Test fun stores_routeCopies_andBackupAllowsThem() {
        assertEquals("room_settings__pabc", ProfileStores.route("room_settings", "pabc"))
        assertEquals("sound_settings", ProfileStores.route("sound_settings", "pabc")) // shared
        assertEquals("profiles", ProfileStores.route("profiles", "pabc"))
        assertEquals("room_settings" to "pabc", ProfileStores.parseCopy("room_settings__pabc"))
        assertNull(ProfileStores.parseCopy("crash_guard__pabc"))
        assertTrue(ConfigBackup.isAllowed("launcher_settings__pabc"))
        assertTrue(ConfigBackup.isAllowed("profiles"))
        assertFalse(ConfigBackup.isAllowed("crash_guard"))
        val names = ConfigBackup.storeNames(listOf("home", "pabc"))
        assertTrue("wallpaper_settings__pabc" in names && "wallpaper_settings" in names)
        val launcher = """{"favorites":["a.b"],"order":[],"hidden":[]}"""
        val back = ConfigBackup.decode(ConfigBackup.encode(mapOf(
            "profiles" to mapOf("json" to "{}"), "launcher_settings__pabc" to mapOf("settings_json" to launcher))))!!
        assertEquals(launcher, back["launcher_settings__pabc"]!!["settings_json"])
        val bad = ConfigBackup.encode(mapOf("launcher_settings__pabc" to mapOf("settings_json" to """{"favorites":["bad pkg!"],"order":[],"hidden":[]}""")))
        assertNull(ConfigBackup.decode(bad))
    }

    @Test fun switcherRows_andSettingsPage_withinBudget() {
        var s = ProfileModel.initial()
        s = ok(ProfileModel.create(s, "Sam", "psam")).state
        val c = ProfileSwitcherModel.build(s)
        assertEquals(listOf("profile:home", "profile:psam", ProfileSwitcherModel.MANAGE), c.rows.map { it.key })
        assertEquals("psam", ProfileSwitcherModel.idOf("profile:psam"))
        assertTrue(ProfilesPage.SPEC.withinBudget)
        assertTrue(ProfilesPage.EDIT_SPEC.withinBudget)
        assertEquals("Hi, Sam", ProfileSwitcherModel.greeting(s.byId("psam")!!))
        assertEquals("S", s.byId("psam")!!.initial)
    }
}
