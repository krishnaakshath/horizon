package dev.tvlauncher.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private val sample = LauncherSettings(
        favorites = listOf("com.netflix.ninja"),
        order = listOf("com.netflix.ninja", "com.plexapp.android"),
        hidden = setOf("com.tcl.initsetup")
    )

    @Test
    fun export_thenImport_roundTrips() {
        val f = tmp.root.resolve(SettingsBackup.FILE_NAME)
        assertTrue(SettingsBackup.export(sample, f))
        assertEquals(sample, SettingsBackup.import(f, LauncherSettings()))
    }

    @Test
    fun import_corruptFile_leavesSettingsUntouched() {
        val f = tmp.newFile("corrupt.json").apply { writeText("{\"favorites\": [oops") }
        assertEquals(sample, SettingsBackup.import(f, sample))
        val notObject = tmp.newFile("array.json").apply { writeText("[1,2]") }
        assertEquals(sample, SettingsBackup.import(notObject, sample))
        val unrelated = tmp.newFile("other.json").apply { writeText("{\"hello\":\"world\"}") }
        assertEquals(sample, SettingsBackup.import(unrelated, sample))
    }

    @Test
    fun import_missingFile_leavesSettingsUntouched() {
        assertEquals(sample, SettingsBackup.import(tmp.root.resolve("nope.json"), sample))
        assertNull(SettingsBackup.parse(""))
    }
}
