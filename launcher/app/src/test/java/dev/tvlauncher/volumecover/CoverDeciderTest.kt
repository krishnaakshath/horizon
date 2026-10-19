package dev.tvlauncher.volumecover

import dev.tvlauncher.brand.Brand
import dev.tvlauncher.volume.VolumeCopy
import dev.tvlauncher.volume.core.CoverDecider
import dev.tvlauncher.volume.core.CoverDecider.Companion.ANY_STREAM
import dev.tvlauncher.volume.core.CoverDecider.Companion.STREAM_MUSIC
import dev.tvlauncher.volume.core.CoverTiming
import dev.tvlauncher.volume.core.HideTimer
import dev.tvlauncher.volume.core.VolumeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverDeciderTest {
    private fun v(level: Int, muted: Boolean = false) = VolumeState(level, 100, muted)

    @Test fun a_change_shows_the_reread_state() {
        val d = CoverDecider()
        d.prime(v(20))
        assertEquals(v(21), d.decide(STREAM_MUSIC, v(21), 1_000))
    }

    @Test fun duplicate_broadcasts_for_one_press_show_once() {
        val d = CoverDecider()
        d.prime(v(20))
        assertEquals(v(21), d.decide(STREAM_MUSIC, v(21), 1_000))
        // The same press broadcast again (per device / alias): state unchanged, nothing new.
        assertNull(d.decide(STREAM_MUSIC, v(21), 1_003))
        assertNull(d.decide(STREAM_MUSIC, v(21), 1_010))
        // The next press moves it again.
        assertEquals(v(22), d.decide(STREAM_MUSIC, v(22), 1_200))
    }

    @Test fun other_streams_are_ignored_and_do_not_touch_the_last_state() {
        val d = CoverDecider()
        d.prime(v(20))
        assertNull(d.decide(1, v(24), 1_000)) // STREAM_SYSTEM
        assertNull(d.decide(4, v(24), 1_000)) // STREAM_ALARM
        assertEquals(v(20), d.lastSeen)
    }

    @Test fun mute_and_unmute_show_even_at_the_same_level() {
        val d = CoverDecider()
        d.prime(v(20))
        assertEquals(v(20, muted = true), d.decide(STREAM_MUSIC, v(20, muted = true), 1_000))
        assertNull(d.decide(STREAM_MUSIC, v(20, muted = true), 1_005))
        assertEquals(v(20), d.decide(STREAM_MUSIC, v(20), 2_000))
    }

    @Test fun master_mute_without_a_stream_counts() {
        val d = CoverDecider()
        d.prime(v(20))
        assertEquals(v(20, muted = true), d.decide(ANY_STREAM, v(20, muted = true), 1_000))
    }

    @Test fun first_broadcast_without_a_primed_state_shows() {
        val d = CoverDecider()
        assertEquals(v(5), d.decide(STREAM_MUSIC, v(5), 10))
    }

    @Test fun our_own_changes_are_not_covered_but_are_remembered() {
        val d = CoverDecider(ownWindowMs = 400)
        d.prime(v(20))
        d.noteOwnChange(1_000)
        assertNull(d.decide(STREAM_MUSIC, v(25), 1_050))
        assertNull(d.decide(STREAM_MUSIC, v(25), 1_399))
        assertEquals(v(25), d.lastSeen)
        // After the window a real (remote) change shows again; the same state does not.
        assertNull(d.decide(STREAM_MUSIC, v(25), 1_500))
        assertEquals(v(24), d.decide(STREAM_MUSIC, v(24), 1_600))
    }

    @Test fun unreadable_or_empty_state_shows_nothing() {
        val d = CoverDecider()
        d.prime(v(20))
        assertNull(d.decide(STREAM_MUSIC, null, 1_000))
        assertNull(d.decide(STREAM_MUSIC, VolumeState(0, 0, false), 1_000))
        assertEquals(v(20), d.lastSeen)
        d.prime(null)
        assertEquals(v(20), d.lastSeen)
    }

    @Test fun cover_outlasts_the_tv_popup() {
        assertTrue(CoverTiming.HOLD_MS > CoverTiming.TV_POPUP_MS)
        val t = HideTimer(CoverTiming.HOLD_MS)
        t.poke(0)
        assertFalse(t.due(CoverTiming.TV_POPUP_MS))
        // Every change restarts it, as the TV's own timer does.
        t.poke(2_000)
        assertFalse(t.due(2_000 + CoverTiming.TV_POPUP_MS))
        assertTrue(t.due(2_000 + CoverTiming.HOLD_MS))
    }

    @Test fun cover_row_copy() {
        val ok = VolumeCopy.HelperState(true, true, true, true)
        assertEquals("Off: the TV shows its own volume popup", CoverCopy.subtitle(false, ok))
        assertEquals("Our volume bar covers the TV's popup in every app", CoverCopy.subtitle(true, ok))
        assertTrue(CoverCopy.subtitle(true, VolumeCopy.HelperState.NOT_INSTALLED).startsWith("Needs ${Brand.HELPER}"))
        assertEquals("Our volume bar covers the TV's popup", CoverCopy.subtitle(true, VolumeCopy.HelperState.PENDING))
        assertTrue(CoverCopy.subtitle(true, VolumeCopy.HelperState(true, true, false, true)).startsWith("Paused"))
        assertTrue(CoverCopy.subtitle(true, VolumeCopy.HelperState.NOT_RUNNING).startsWith("Turn on"))
    }
}
