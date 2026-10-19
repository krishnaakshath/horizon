package dev.tvlauncher.ui

import dev.tvlauncher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsEditsTest {
    private val cur = listOf("a", "b", "c")

    @Test
    fun toggleFavorite_addsThenRemoves() {
        val s1 = SettingsEdits.toggleFavorite(LauncherSettings(), "b", cur)
        assertEquals(listOf("b"), s1.favorites)
        val s2 = SettingsEdits.toggleFavorite(s1, "b", cur)
        assertEquals(emptyList<String>(), s2.favorites)
    }

    @Test
    fun moveRight_swapsWithNeighbor() {
        val s = SettingsEdits.moveRight(LauncherSettings(), "a", cur)
        assertEquals(listOf("b", "a", "c"), s.order)
    }

    @Test
    fun moveLeft_atStart_isNoop() {
        val base = LauncherSettings()
        assertEquals(base, SettingsEdits.moveLeft(base, "a", cur))
        assertEquals(base, SettingsEdits.moveRight(base, "c", cur))
    }

    @Test
    fun hide_addsToHidden_andRemovesFromFavorites() {
        val s = SettingsEdits.hide(LauncherSettings(favorites = listOf("a", "b")), "a", cur)
        assertEquals(setOf("a"), s.hidden)
        assertEquals(listOf("b"), s.favorites)
        assertFalse("a" in s.order)
    }
}
