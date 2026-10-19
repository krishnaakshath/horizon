package dev.tvlauncher.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperCopyTest {
    private val jargon = Regex("""\badb\b|appops|accessibility service|crash|memory manager|\bkill|developer""", RegexOption.IGNORE_CASE)

    @Test fun linesAreCalmOneLiners() {
        val all = listOf(HelperCopy.LIMIT, HelperCopy.OPEN_NOTE) +
            listOf(true, false).flatMap { i -> listOf(true, false).map { HelperCopy.subtitle(i, it) } }
        for (s in all) {
            assertFalse(s, jargon.containsMatchIn(s))
            assertFalse(s, s.contains('\n'))
        }
    }

    @Test fun limitSaysTheLauncherKeepsWorking() {
        assertTrue(HelperCopy.LIMIT.contains("power-on"))
        assertTrue(HelperCopy.LIMIT.contains("keep working"))
        assertTrue(HelperCopy.subtitle(installed = true, on = true).contains("home still works"))
    }
}
