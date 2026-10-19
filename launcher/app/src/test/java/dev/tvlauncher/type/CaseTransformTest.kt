package dev.tvlauncher.type

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaseTransformTest {
    @Test fun upperBasics() {
        assertEquals("HELLO", CaseTransform.upper("Hello", Locale.ENGLISH).toString())
        assertEquals("", CaseTransform.upper("", Locale.ENGLISH).toString())
        assertNull(CaseTransform.upper(null, Locale.ENGLISH))
    }

    @Test fun turkishDottedI() {
        assertEquals("İ", CaseTransform.upper("i", Locale("tr")).toString())
        assertEquals("I", CaseTransform.upper("i", Locale.ENGLISH).toString())
    }
}

/** Guard: the API 28 all-caps setter must never reappear (minSdk 24). */
class NoAllCapsApiGuardTest {
    private fun stripComments(src: String): String =
        src.replace(Regex("/\\*[\\s\\S]*?\\*/"), "").lines().joinToString("\n") { it.substringBefore("//") }

    @Test fun noApi28AllCapsCalls() {
        val offenders = File("src/main/java").walkTopDown().filter { it.extension == "kt" || it.extension == "java" }
            .filter { f -> stripComments(f.readText()).let { it.contains("isAllCaps") || it.contains("setAllCaps(") } }
            .map { it.path }.toList()
        assertTrue("API 28 all-caps used in $offenders", offenders.isEmpty())
    }
}
