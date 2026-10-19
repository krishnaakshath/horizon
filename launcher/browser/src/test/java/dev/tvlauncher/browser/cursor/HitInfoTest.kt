package dev.tvlauncher.browser.cursor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HitInfoTest {
    @Test fun parse_evaluateJavascriptStringResult() {
        // evaluateJavascript hands back a JSON-encoded string containing our JSON.
        val raw = "\"{\\\"tag\\\":\\\"A\\\",\\\"href\\\":\\\"https://x.com/\\\",\\\"editable\\\":false,\\\"clickable\\\":true,\\\"cursor\\\":\\\"pointer\\\",\\\"snap\\\":{\\\"x\\\":10.5,\\\"y\\\":20,\\\"d\\\":3}}\""
        val h = HitInfo.parse(raw)
        assertEquals("a", h.tag)
        assertEquals("https://x.com/", h.href)
        assertTrue(h.clickable)
        assertFalse(h.editable)
        assertEquals("pointer", h.cursor)
        assertEquals(10.5f, h.snapX!!, 0.001f)
        assertEquals(20f, h.snapY!!, 0.001f)
    }

    @Test fun parse_plainObject() {
        val h = HitInfo.parse("""{"tag":"input","editable":true}""")
        assertEquals("input", h.tag)
        assertTrue(h.editable)
        assertNull(h.snapX)
    }

    @Test fun parse_hostile_neverThrows_givesEmpty() {
        listOf(
            null, "", "null", "\"\"", "\"{\"", "[1,2]", "{\"tag\":5,\"editable\":\"yes\",\"snap\":{\"x\":\"NaN\"}}",
            "{".repeat(5000), "\u0000\uFFFF", "\"\\u0000\"", "{\"snap\":{\"x\":1e400,\"y\":-1e400}}"
        ).forEach { HitInfo.parse(it) }
        assertEquals(HitInfo.EMPTY, HitInfo.parse("garbage"))
        val bad = HitInfo.parse("{\"snap\":{\"x\":1e400,\"y\":2}}")
        assertNull(bad.snapX)
    }

    @Test fun parse_capsLongStrings() {
        val h = HitInfo.parse("{\"tag\":\"" + "a".repeat(5000) + "\",\"href\":\"" + "h".repeat(5000) + "\"}")
        assertTrue(h.tag.length <= 32)
        assertTrue((h.href ?: "").length <= 2048)
    }
}
