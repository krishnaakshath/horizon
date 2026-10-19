package dev.tvlauncher.room

import dev.tvlauncher.room.RoomMode.BRIGHT
import dev.tvlauncher.room.RoomMode.CINEMA
import dev.tvlauncher.room.RoomMode.EVENING
import dev.tvlauncher.room.RoomMode.STANDARD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The controller's pure core, driven with a fake clock, fake minute-of-day and fake sun source. */
class RoomEngineTest {
    private var now = 1_000_000L
    private var minute = 12 * 60
    private var sun: Pair<Int, Int>? = (6 * 60 + 24) to (17 * 60 + 47) // Delhi, 20 Oct
    private val emitted = mutableListOf<Triple<RoomMode, Palette, Long>>()

    private val engine = RoomEngine(
        clock = { now },
        minuteOfDay = { minute },
        daylight = { DaylightSource { sun } },
        transitionMs = { 1200L },
        emit = { m, p, ms -> emitted += Triple(m, p, ms) }
    )

    private fun advance(ms: Long) { now += ms }
    private fun lastMode() = emitted.last().first

    @Test fun first_emission_has_no_crossfade_later_ones_do() {
        engine.start(RoomSettings(), luxActive = false)
        assertEquals(1, emitted.size)
        assertEquals(STANDARD, lastMode())
        assertEquals(0L, emitted.last().third)
        minute = 17 * 60 + 17
        engine.tick()
        assertEquals(EVENING, lastMode())
        assertEquals(1200L, emitted.last().third)
    }

    @Test fun sun_known_then_lux_arrives_then_dwell_then_lux_lost() {
        engine.start(RoomSettings(), luxActive = true)
        assertEquals(STANDARD, lastMode()) // no lux yet: sun table at noon
        // First lux after start applies at once (no 45 s dwell), but still crossfades.
        advance(2_000); engine.onLux(2f)
        assertEquals(CINEMA, lastMode())
        assertEquals(1200L, emitted.last().third)
        // Later changes wait for the dwell even though the sun table says day.
        for (i in 1..22) { advance(2_000); engine.onLux(10f) }
        assertEquals(CINEMA, lastMode())
        assertEquals(EVENING, engine.state!!.pending)
        // The smoothed value crossed 6.25 lux at the 4th sample (+8 s); 45 s later it switches.
        for (i in 1..6) { advance(2_000); engine.onLux(10f) }
        assertEquals(EVENING, lastMode())
        // Sensor gone: the sun table takes over immediately.
        engine.update(RoomSettings(), luxActive = false)
        assertEquals(STANDARD, lastMode())
        // ... and follows the sun.
        minute = 20 * 60 + 30
        engine.tick()
        assertEquals(CINEMA, lastMode())
    }

    @Test fun sun_unknown_uses_the_no_sun_table() {
        sun = null
        minute = 7 * 60
        engine.start(RoomSettings(), luxActive = false)
        assertEquals(EVENING, lastMode())
        minute = 7 * 60 + 30
        engine.tick()
        assertEquals(STANDARD, lastMode())
    }

    @Test fun manual_override_clears_pending_and_auto_returns_immediately() {
        engine.start(RoomSettings(), luxActive = true)
        advance(2_000); engine.onLux(100f)
        assertEquals(STANDARD, lastMode())
        // A 10x drop is held once as a possible spike, then confirmed and smoothed below 30 lux.
        repeat(3) { advance(2_000); engine.onLux(10f) }
        assertEquals(STANDARD, lastMode())
        assertEquals(EVENING, engine.state!!.pending)
        engine.update(RoomSettings(choice = RoomChoice.BRIGHT), luxActive = false)
        assertEquals(BRIGHT, lastMode())
        assertNull(engine.state!!.pending)
        // Back to AUTO with the sensor: the next lux applies at once.
        engine.update(RoomSettings(), luxActive = true)
        advance(2_000); engine.onLux(10f)
        assertEquals(EVENING, lastMode())
    }

    @Test fun start_and_stop_are_idempotent() {
        engine.start(RoomSettings(), luxActive = false)
        engine.start(RoomSettings(), luxActive = false)
        assertEquals(1, emitted.size)
        engine.stop(); engine.stop()
        assertTrue(!engine.started)
        // Ticks and lux after stop do nothing.
        minute = 20 * 60 + 30
        engine.tick(); engine.onLux(5_000f)
        assertEquals(1, emitted.size)
        // Restart at the same moment of day: nothing changed, nothing emitted.
        minute = 12 * 60
        engine.start(RoomSettings(), luxActive = false)
        assertEquals(1, emitted.size)
        engine.stop()
        // Restart later: the change is emitted with a crossfade.
        minute = 21 * 60
        engine.start(RoomSettings(), luxActive = false)
        assertEquals(2, emitted.size)
        assertEquals(CINEMA, lastMode())
        assertEquals(1200L, emitted.last().third)
    }

    @Test fun palette_change_alone_is_emitted() {
        engine.start(RoomSettings(choice = RoomChoice.BRIGHT), luxActive = false)
        assertEquals(1f, emitted.last().second.textWeightBoost, 0f)
        engine.update(RoomSettings(choice = RoomChoice.BRIGHT, boostTextInBright = false), luxActive = false)
        assertEquals(2, emitted.size)
        assertEquals(0f, emitted.last().second.textWeightBoost, 0f)
    }

    @Test fun preview_mode_for_toast() {
        // Stopped: computes what AUTO would pick now without emitting.
        minute = 21 * 60
        assertEquals(CINEMA, engine.previewMode(RoomSettings()))
        assertEquals(BRIGHT, engine.previewMode(RoomSettings(choice = RoomChoice.BRIGHT)))
        assertTrue(emitted.isEmpty())
        // Started: the live decision.
        minute = 12 * 60
        engine.start(RoomSettings(), luxActive = false)
        assertEquals(STANDARD, engine.previewMode(RoomSettings()))
    }

    @Test fun choice_override_wins_over_saved_choice() {
        engine.choiceOverride = { RoomChoice.CINEMA }
        engine.start(RoomSettings(choice = RoomChoice.BRIGHT), luxActive = false)
        assertEquals(CINEMA, lastMode())
        assertEquals(RoomChoice.CINEMA, engine.choice())
    }

    @Test fun throwing_daylight_source_falls_back() {
        val e = RoomEngine(
            clock = { now }, minuteOfDay = { 7 * 60 },
            daylight = { DaylightSource { throw IllegalStateException("boom") } },
            transitionMs = { 0L }, emit = { m, p, ms -> emitted += Triple(m, p, ms) }
        )
        e.start(RoomSettings(), luxActive = false)
        assertEquals(EVENING, lastMode())
    }
}
