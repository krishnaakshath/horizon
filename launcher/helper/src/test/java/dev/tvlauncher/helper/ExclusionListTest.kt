package dev.tvlauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExclusionListTest {
    @Test fun parse_normalizes_trims_lowercases_dedupes() {
        val s = ExclusionList.parse(" Com.Foo.Bar ,com.foo.bar\n\ncom.baz ;  ")
        assertEquals(setOf("com.foo.bar", "com.baz"), s)
    }

    @Test fun parse_drops_invalid_names() {
        assertEquals(setOf("a.b"), ExclusionList.parse("a.b, nodot, bad name.x, .x., "))
    }

    @Test fun format_is_sorted_and_round_trips() {
        val s = setOf("z.b", "a.b")
        assertEquals("a.b\nz.b", ExclusionList.format(s))
        assertEquals(s, ExclusionList.parse(ExclusionList.format(s)))
    }

    @Test fun defaults_contain_launcher_and_self() {
        val d = ExclusionList.DEFAULTS
        assertTrue("dev.tvlauncher" in d)
        assertTrue("dev.tvlauncher.helper" in d)
        assertTrue("com.android.systemui" in d)
        assertTrue("com.android.settings" in d)
    }

    @Test fun isExcluded_handles_null_and_blank() {
        assertTrue(ExclusionList.isExcluded(null, setOf("a.b")))
        assertTrue(ExclusionList.isExcluded("", setOf("a.b")))
        assertTrue(ExclusionList.isExcluded("A.B", setOf("a.b")))
        assertFalse(ExclusionList.isExcluded("c.d", setOf("a.b")))
    }
}
