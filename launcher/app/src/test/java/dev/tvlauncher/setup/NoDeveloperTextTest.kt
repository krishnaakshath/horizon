package dev.tvlauncher.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No screen of any module may show a command line or developer instructions: every string
 * literal in Kotlin sources and every resource XML under <module>/src/main is scanned (comments
 * are skipped; recipes belong in docs/PROVISIONING.md).
 */
class NoDeveloperTextTest {
    private val banned = Regex(
        """\badb\b|pm grant|from a computer|settings put|appops|allow_listener|\brun:|developer|command line|\bshell\b""",
        RegexOption.IGNORE_CASE
    )
    private val literal = Regex(""""(?:[^"\\]|\\.)*"""")

    private fun hits(root: File): List<String> {
        val out = ArrayList<String>()
        root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                val t = line.trim()
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || t.startsWith("<!--")) return@forEachIndexed
                val parts = if (f.extension == "kt") literal.findAll(line).map { it.value }.toList() else listOf(line)
                parts.filter { banned.containsMatchIn(it) }.forEach { out += "${f.path}:${i + 1}: $it" }
            }
        }
        return out
    }

    @Test fun noModuleShowsDeveloperText() {
        val modules = listOf("src/main", "../helper/src/main", "../store/src/main", "../browser/src/main", "../weather/src/main")
            .map(::File).filter { it.isDirectory }
        assertTrue("module sources not found from ${File(".").absolutePath}", modules.isNotEmpty() && modules[0].isDirectory)
        assertEquals(emptyList<String>(), modules.flatMap(::hits))
    }

    @Test fun setupCopy_isPlainWords() {
        val all = listOf(SetupCopy.HELPER_LINE, SetupCopy.HOME_HELPER, SetupCopy.NP_ACTION, SetupCopy.LOCATION_NEEDED,
            SetupCopy.SCREENSAVER_NOTE, SetupCopy.PHOTOS_NOTE, SetupCopy.INSTALL_NOTE, SetupCopy.appsHelper(listOf("Browser")),
            SetupCopy.tuned(lite = true, roomAutomatic = true), SetupCopy.notificationAccessHint(false))
        for (s in all) {
            assertFalse(s, banned.containsMatchIn(s))
            assertFalse(s, s.contains("dev.tvlauncher"))
        }
    }
}
