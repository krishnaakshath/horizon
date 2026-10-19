package dev.tvlauncher.store.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.StringReader

class JsonLimitsTest {

    private val cfg = FdroidIndexParser.Config(abis = listOf("arm64-v8a"), sdkInt = 28)

    @Test fun hugeStringAbortsTheParseWithoutThrowing() {
        val json = """{"packages": {"a.b": {"metadata": {"name": "${"x".repeat(70_000)}"}}}}"""
        val r = FdroidIndexParser(cfg).parse(StringJsonTokenSource(json))
        assertNotNull(r.error)
        assertTrue(r.error!!.contains("too long"))
        assertTrue(r.apps.isEmpty())
    }

    @Test fun deepNestingAbortsTheParse() {
        val json = """{"packages": {"a.b": {"metadata": {"x": ${"[".repeat(200)}${"]".repeat(200)}}}}}"""
        val r = FdroidIndexParser(cfg).parse(StringJsonTokenSource(json))
        assertTrue(r.error!!.contains("deeply"))
    }

    @Test fun limitsAreConfigurable() {
        val json = """{"packages": {"a.b": {"metadata": {"name": "${"x".repeat(100)}"}}}}"""
        val r = FdroidIndexParser(cfg.copy(maxString = 50)).parse(StringJsonTokenSource(json))
        assertTrue(r.error!!.contains("too long"))
    }

    @Test fun boundedSourcePassesNormalInput() {
        val s = BoundedTokenSource(StringJsonTokenSource("""{"a": [1, "two", {"b": null}]}"""))
        s.skipValue()
        assertEquals(JsonTokenSource.Token.END_DOCUMENT, s.peek())
    }

    @Test fun guardReaderStopsLongStringsBeforeTheyAreBuffered() {
        val r = JsonGuardReader(StringReader("""{"k": "${"y".repeat(100)}"}"""), maxString = 64)
        try {
            r.readText()
            fail()
        } catch (e: JsonLimitExceeded) {
            assertTrue(e.message!!.contains("too long"))
        }
    }

    @Test fun guardReaderStopsDeepNestingAndIgnoresBracketsInStrings() {
        assertEquals(""""[[[[" """.trim(), JsonGuardReader(StringReader(""""[[[[" """.trim()), maxDepth = 2).readText())
        try {
            JsonGuardReader(StringReader("[[[[1]]]]"), maxDepth = 3).readText()
            fail()
        } catch (e: JsonLimitExceeded) {
            assertTrue(e.message!!.contains("deeply"))
        }
        // escaped quotes don't end the string
        assertEquals(""""a\"b"""", JsonGuardReader(StringReader(""""a\"b""""), maxString = 10).readText())
    }

    @Test fun jsonCapIs100MB() {
        assertEquals(100L * 1024 * 1024, dev.tvlauncher.store.data.FdroidRepository.MAX_JSON_BYTES)
    }
}
