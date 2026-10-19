package dev.tvlauncher.appsettings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RowGuardTest {
    private val spec = PageSpec("p", listOf("p.a", "p.b"), listOf("p.x"))

    @Test
    fun declaredRows_pass_andAreCounted() {
        val g = RowGuard(spec)
        g.claim("p.a", advanced = false)
        g.claim("p.b", advanced = false)
        g.claim("p.x", advanced = true)
        assertEquals(2, g.primaryCount)
    }

    @Test
    fun undeclaredRow_throws() {
        assertThrows(IllegalArgumentException::class.java) { RowGuard(spec).claim("p.c", advanced = false) }
    }

    @Test
    fun advancedRowOutsideAdvanced_throws_andViceVersa() {
        assertThrows(IllegalArgumentException::class.java) { RowGuard(spec).claim("p.x", advanced = false) }
        assertThrows(IllegalArgumentException::class.java) { RowGuard(spec).claim("p.a", advanced = true) }
    }

    @Test
    fun duplicateRow_throws() {
        val g = RowGuard(spec)
        g.claim("p.a", advanced = false)
        assertThrows(IllegalArgumentException::class.java) { g.claim("p.a", advanced = false) }
    }

    @Test
    fun anOverfullSpec_isStoppedAtTheEighthRow() {
        val fat = PageSpec("f", (1..8).map { "f.$it" })
        val g = RowGuard(fat)
        (1..7).forEach { g.claim("f.$it", advanced = false) }
        assertThrows(IllegalStateException::class.java) { g.claim("f.8", advanced = false) }
    }

    @Test
    fun everyBuiltInSpec_canBeBuiltCompletelyThroughTheGuard() {
        val r = SettingsRegistry.newDefault()
        ModuleSections.registerAll(r)
        for (s in r.sections().filterIsInstance<SpecifiedPage>()) {
            val g = RowGuard(s.spec)
            s.spec.primaryRows.forEach { g.claim(it, advanced = false) }
            s.spec.advancedRows.forEach { g.claim(it, advanced = true) }
        }
    }
}
