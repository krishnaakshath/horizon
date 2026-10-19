package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves the stored "Show notification text" toggle reaches the text AmbientView renders. */
class AmbientViewModelTest {
    private val now = 1_800_000_000_000L
    private fun feed() = NotificationFeed().apply {
        post(NotifInfo("k1", "com.chat", "Chat", "Ann", "See you at 6", now - 3 * 60_000, false, false, false, NotifInfo.VISIBILITY_PUBLIC))
        post(NotifInfo("k2", "com.bank", "Bank", "OTP", "1234", now - 60_000, false, false, false, NotifInfo.VISIBILITY_PRIVATE))
    }

    @Test fun toggleOff_renderedChipsShowAppAndTimeOnly() {
        val s = WallpaperStoreJson.roundTrip(WallpaperSettings(notificationsEnabled = true))
        val texts = AmbientViewModel.chipsFor(s, feed(), accessGranted = true, nowMs = now).map { AmbientViewModel.chipText(it) }
        assertEquals(listOf("Bank  1m", "Chat  3m"), texts)
    }

    @Test fun toggleOn_publicChipRendersMessage_privateDoesNot() {
        val s = WallpaperStoreJson.roundTrip(WallpaperSettings(notificationsEnabled = true, showNotificationText = true))
        val texts = AmbientViewModel.chipsFor(s, feed(), accessGranted = true, nowMs = now).map { AmbientViewModel.chipText(it) }
        assertEquals(listOf("Bank  1m", "Chat  Ann: See you at 6  3m"), texts)
        assertFalse(texts.any { it.contains("1234") })
    }

    @Test fun chipsHidden_whenOptedOut_orAccessMissing() {
        val on = WallpaperSettings(notificationsEnabled = true, showNotificationText = true)
        assertTrue(AmbientViewModel.chipsFor(on.copy(notificationsEnabled = false), feed(), true, now).isEmpty())
        assertTrue(AmbientViewModel.chipsFor(on, feed(), accessGranted = false, nowMs = now).isEmpty())
    }

    @Test fun titleSpan_coversOnlyTheMessagePart() {
        val chip = NotificationChip("Chat", "Ann", "3m")
        val r = AmbientViewModel.chipText(chip)
        assertEquals("Chat  Ann  3m", r)
        assertEquals(4..9, AmbientViewModel.titleRange(chip))
    }
}

/** Persists through the same JSON the store uses. */
private object WallpaperStoreJson {
    fun roundTrip(s: WallpaperSettings) = WallpaperSettings.fromJson(s.toJson())
}
