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

    private fun file(name: String, text: String) = tmp.newFile(name).apply { writeText(text) }

    private val valid = """{"version":1,"favorites":["com.netflix.ninja"],"order":["com.netflix.ninja","com.plexapp.android"],"hidden":["com.tcl.initsetup"],"theme":"dark"}"""

    @Test
    fun import_wrongTypeFavorites_rejected_settingsUntouched() {
        val f = file("t.json", """{"favorites":"oops","order":[],"hidden":[]}""")
        assertEquals(sample, SettingsBackup.import(f, sample))
        assertNull(SettingsBackup.importOrNull(f))
        assertNull(SettingsBackup.parse("""{"favorites":[],"order":{},"hidden":[]}"""))
        assertNull(SettingsBackup.parse("""{"favorites":[],"order":[],"hidden":[],"version":"1"}"""))
        assertNull(SettingsBackup.parse("""{"favorites":[],"order":[],"hidden":[],"theme":5}"""))
    }

    @Test
    fun import_missingKeys_rejected() {
        assertNull(SettingsBackup.parse("""{"favorites":[],"order":[]}"""))
        assertNull(SettingsBackup.parse("""{"favorites":[],"hidden":[]}"""))
        assertNull(SettingsBackup.parse("""{"order":[],"hidden":[]}"""))
    }

    @Test
    fun import_nonStringElements_rejected() {
        assertNull(SettingsBackup.parse("""{"favorites":[1],"order":[],"hidden":[]}"""))
        assertNull(SettingsBackup.parse("""{"favorites":[],"order":[null],"hidden":[]}"""))
        assertNull(SettingsBackup.parse("""{"favorites":[],"order":[],"hidden":[["a"]]}"""))
        assertNull(SettingsBackup.parse("""{"favorites":[""],"order":[],"hidden":[]}"""))
    }

    @Test
    fun import_badPackageName_rejected() {
        assertNull(SettingsBackup.parse("""{"favorites":["com.evil/../x"],"order":[],"hidden":[]}"""))
        assertNull(SettingsBackup.parse("""{"favorites":[],"order":["has space"],"hidden":[]}"""))
        val long = "a".repeat(256)
        assertNull(SettingsBackup.parse("""{"favorites":[],"order":[],"hidden":["$long"]}"""))
        assertEquals(listOf("a".repeat(255)), SettingsBackup.parse("""{"favorites":["${"a".repeat(255)}"],"order":[],"hidden":[]}""")?.favorites)
    }

    @Test
    fun import_oversizeFile_rejected() {
        val pad = " ".repeat((SettingsBackup.MAX_BYTES + 1).toInt())
        val f = file("big.json", valid.dropLast(1) + "," + "\"pad\":\"x\"" + pad + "}")
        assertTrue(f.length() > SettingsBackup.MAX_BYTES)
        assertEquals(sample, SettingsBackup.import(f, sample))
        val ok = file("ok.json", valid)
        assertTrue(ok.length() <= SettingsBackup.MAX_BYTES)
        assertEquals(sample, SettingsBackup.import(ok, LauncherSettings()))
    }

    @Test
    fun import_hugeArray_rejected() {
        val many = (0..SettingsBackup.MAX_ENTRIES).joinToString(",") { "\"p$it\"" }
        assertNull(SettingsBackup.parse("""{"favorites":[],"order":[$many],"hidden":[]}"""))
        val max = (1..SettingsBackup.MAX_ENTRIES).joinToString(",") { "\"p$it\"" }
        assertEquals(SettingsBackup.MAX_ENTRIES, SettingsBackup.parse("""{"favorites":[],"order":[$max],"hidden":[]}""")?.order?.size)
    }

    @Test
    fun import_validFile_roundTrips() {
        assertEquals(sample, SettingsBackup.import(file("v.json", valid), LauncherSettings()))
        val f = tmp.root.resolve("exported.json")
        assertTrue(SettingsBackup.export(sample, f))
        assertEquals(sample, SettingsBackup.importOrNull(f))
    }

    @Test
    fun import_extraUnknownFields_ignored_butKnownValid_ok() {
        val s = SettingsBackup.parse("""{"favorites":["a.b"],"order":[],"hidden":[],"future":{"x":[1,2]},"rev":7}""")
        assertEquals(LauncherSettings(favorites = listOf("a.b")), s)
    }

    @Test
    fun import_nonObjectsAndThemeOnly_rejected() {
        assertNull(SettingsBackup.parse("""{"theme":"x"}"""))
        assertNull(SettingsBackup.parse("[]"))
        assertNull(SettingsBackup.parse("\"str\""))
        assertNull(SettingsBackup.parse("null"))
        assertEquals(sample, SettingsBackup.import(file("theme.json", """{"theme":"x"}"""), sample))
    }
}
