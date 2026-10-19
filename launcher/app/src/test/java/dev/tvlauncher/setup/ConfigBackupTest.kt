package dev.tvlauncher.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ConfigBackupTest {
    private val launcher = """{"version":1,"favorites":["com.netflix.ninja"],"order":[],"hidden":[],"theme":"dark"}"""

    private val stores: Map<String, Map<String, Any?>> = mapOf(
        "launcher_settings" to mapOf("settings_json" to launcher),
        "room_settings" to mapOf("json" to """{"choice":"AUTO"}"""),
        "sound_settings" to mapOf("json" to "{}", "flag" to true, "n" to 3, "big" to 5_000_000_000L, "f" to 0.5f,
            "set" to setOf("b", "a")),
        "crash_guard" to mapOf("times" to "1,2") // never exported
    )

    @Test fun roundTrip_keepsTypes_andDropsUnlistedStores() {
        val back = ConfigBackup.decode(ConfigBackup.encode(stores))!!
        assertEquals(setOf("launcher_settings", "room_settings", "sound_settings"), back.keys)
        val s = back["sound_settings"]!!
        assertEquals(true, s["flag"])
        assertEquals(3, s["n"])
        assertEquals(5_000_000_000L, s["big"])
        assertEquals(0.5f, s["f"])
        assertEquals(setOf("a", "b"), s["set"])
        assertEquals(launcher, back["launcher_settings"]!!["settings_json"])
    }

    @Test fun strict_rejectsForeignStoresWrongTypesAndBadAppLists() {
        assertNull(ConfigBackup.decode("not json"))
        assertNull(ConfigBackup.decode("""{"format":"other","version":1,"stores":{}}"""))
        assertNull(ConfigBackup.decode("""{"format":"tvlauncher-config","version":9,"stores":{}}"""))
        assertNull(ConfigBackup.decode("""{"format":"tvlauncher-config","version":1,"stores":{"crash_guard":{}}}"""))
        assertNull(ConfigBackup.decode("""{"format":"tvlauncher-config","version":1,"stores":{"sound_settings":{"x":{"t":"b","v":"yes"}}}}"""))
        assertNull(ConfigBackup.decode("""{"format":"tvlauncher-config","version":1,"stores":{"sound_settings":{"x":{"t":"zz","v":1}}}}"""))
        val badApps = ConfigBackup.encode(mapOf("launcher_settings" to mapOf("settings_json" to """{"favorites":["bad pkg!"],"order":[],"hidden":[]}""")))
        assertNull(ConfigBackup.decode(badApps))
        assertNotNull(ConfigBackup.decode("""{"format":"tvlauncher-config","version":1,"stores":{}}"""))
    }

    @Test fun candidates_usbFirst_thenThisTv() {
        val c = ConfigBackup.candidates(listOf(File("/storage/USB1")), listOf(File("/sdcard/Android/data/dev.tvlauncher/files")), File("/sdcard"))
        assertEquals(File("/storage/USB1/TVLauncher/tvlauncher-config.json"), c[0])
        assertEquals(File("/storage/USB1/tvlauncher-config.json"), c[1])
        assertEquals(File("/sdcard/Android/data/dev.tvlauncher/files/tvlauncher-config.json"), c[2])
        assertEquals(File("/sdcard/Pictures/TVLauncher/tvlauncher-config.json"), c[3])
    }
}
