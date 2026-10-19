package dev.tvlauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperCopyTest {
    private val banned = Regex("""\badb\b|appops|settings put|developer|accessibility|crash|memory manager|\bkill""", RegexOption.IGNORE_CASE)

    @Test fun statusIsCalm() {
        assertEquals(HelperCopy.RUNNING, HelperCopy.status(true))
        assertEquals(HelperCopy.PAUSED, HelperCopy.status(false))
    }

    @Test fun memoryNote_namesWhatPausesAndWhatKeepsWorking() {
        val n = HelperCopy.MEMORY_NOTE
        for (w in listOf("Double-Back", "volume bar", "Guide", "launcher", "power-on", "keep working")) assertTrue(w, n.contains(w))
        for (s in listOf(n, HelperCopy.RUNNING, HelperCopy.PAUSED)) assertFalse(s, banned.containsMatchIn(s))
    }
}
