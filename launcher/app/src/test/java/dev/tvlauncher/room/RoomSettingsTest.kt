package dev.tvlauncher.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSettingsTest {
    @Test fun defaults() {
        val d = RoomSettings()
        assertEquals(RoomChoice.AUTO, d.choice)
        assertNull(d.latitudeDeg)
        assertEquals(1.2f, d.transitionSeconds, 0f)
        assertEquals(18 * 60 + 30, d.eveningStart)
        assertEquals(23 * 60 + 30, d.nightStart)
        assertEquals(7 * 60, d.dayStart)
        assertTrue(d.boostTextInBright)
        assertEquals(Schedule.DEFAULT, d.schedule)
    }

    @Test fun round_trip() {
        val s = RoomSettings(RoomChoice.BRIGHT, 28.6, 0.6f, 1200, 60, 480, false)
        assertEquals(s, RoomSettings.fromJson(s.toJson()))
        val n = RoomSettings(latitudeDeg = null)
        assertEquals(n, RoomSettings.fromJson(n.toJson()))
    }

    @Test fun corrupt_blank_wrong_shape_give_defaults() {
        for (bad in listOf("", "   ", "{not json", "[1,2]", "null", "42")) {
            assertEquals(bad, RoomSettings(), RoomSettings.fromJson(bad))
        }
    }

    @Test fun unknown_and_wrong_types_tolerated() {
        val s = RoomSettings.fromJson("""{"choice":"SPACESHIP","latitudeDeg":"north","transitionSeconds":"x","future":1,"boostTextInBright":"maybe"}""")
        assertEquals(RoomSettings(), s)
        val p = RoomSettings.fromJson("""{"choice":"evening"}""")
        assertEquals(RoomChoice.EVENING, p.choice)
    }

    @Test fun values_clamped() {
        val s = RoomSettings.fromJson("""{"latitudeDeg":123.0,"transitionSeconds":99,"eveningStart":-5,"nightStart":5000,"dayStart":1440}""")
        assertNull(s.latitudeDeg)
        assertEquals(RoomSettings.MAX_TRANSITION_SECONDS, s.transitionSeconds, 0f)
        assertEquals(RoomSettings().eveningStart, s.eveningStart)
        assertEquals(RoomSettings().nightStart, s.nightStart)
        assertEquals(RoomSettings().dayStart, s.dayStart)
        assertEquals(0f, RoomSettings.fromJson("""{"transitionSeconds":-2}""").transitionSeconds, 0f)
        assertNull(RoomSettings.fromJson("""{"latitudeDeg":null}""").latitudeDeg)
        assertEquals(-33.9, RoomSettings.fromJson("""{"latitudeDeg":-33.9}""").latitudeDeg!!, 1e-9)
    }

    @Test fun transition_duration() {
        val s = RoomSettings()
        assertEquals(1200L, RoomTransitions.durationMs(s, lite = false, animatorScale = 1f))
        assertEquals(600L, RoomTransitions.durationMs(s, lite = true, animatorScale = 1f))
        assertEquals(0L, RoomTransitions.durationMs(s, lite = false, animatorScale = 0f))
        assertEquals(0L, RoomTransitions.durationMs(s.copy(transitionSeconds = 0f), lite = false, animatorScale = 1f))
        assertEquals(2000L, RoomTransitions.durationMs(s.copy(transitionSeconds = 2f), lite = false, animatorScale = 1f))
        assertEquals(600L, RoomTransitions.durationMs(s.copy(transitionSeconds = 2f), lite = true, animatorScale = 1f))
        assertEquals(1200L, RoomTransitions.durationMs(s, lite = false, animatorScale = Float.NaN))
    }

    // ---- Cycler and quick action ----

    @Test fun cycler_order_and_wrap() {
        val seen = mutableListOf(RoomChoice.AUTO)
        repeat(5) { seen += RoomChoiceCycler.next(seen.last()) }
        assertEquals(listOf(RoomChoice.AUTO, RoomChoice.CINEMA, RoomChoice.EVENING, RoomChoice.STANDARD, RoomChoice.BRIGHT, RoomChoice.AUTO), seen)
    }

    @Test fun toast_text() {
        assertEquals("Room lighting: Bright room (manual)", RoomChoiceCycler.toast(RoomChoice.BRIGHT, RoomMode.BRIGHT))
        assertEquals("Room lighting: Dark room (manual)", RoomChoiceCycler.toast(RoomChoice.CINEMA, RoomMode.CINEMA))
        assertEquals("Room lighting: Auto (now Evening)", RoomChoiceCycler.toast(RoomChoice.AUTO, RoomMode.EVENING))
    }

    @Test fun quick_action_model() {
        val q = RoomQuickAction.forChoice(RoomChoice.AUTO)
        assertEquals("Room lighting", q.label)
        assertEquals("Auto", q.value)
        assertEquals("ic_room_auto", q.iconKey)
        assertEquals("ic_room_cinema", RoomQuickAction.forChoice(RoomChoice.CINEMA).iconKey)
        assertEquals("ic_room_evening", RoomQuickAction.forChoice(RoomChoice.EVENING).iconKey)
        assertEquals("ic_room_standard", RoomQuickAction.forChoice(RoomChoice.STANDARD).iconKey)
        assertEquals("ic_room_bright", RoomQuickAction.forChoice(RoomChoice.BRIGHT).iconKey)
        assertEquals(RoomChoice.CINEMA, RoomQuickAction.forChoice(RoomChoice.AUTO).nextChoice)
    }

    // ---- Settings rows ----

    @Test fun rows_have_unique_keys_and_cover_settings() {
        val keys = RoomSettingsRows.rows.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.containsAll(listOf("choice", "latitudeDeg", "transitionSeconds", "eveningStart", "nightStart", "dayStart", "boostTextInBright")))
        assertEquals("Room lighting", RoomSettingsRows.row("choice")!!.label)
        assertEquals(listOf("Auto", "Dark room", "Evening", "Normal", "Bright room"), RoomSettingsRows.row("choice")!!.options.map { it.label })
        assertEquals("Location for sunrise and sunset", RoomSettingsRows.row("latitudeDeg")!!.label)
        assertEquals("Smooth transition", RoomSettingsRows.row("transitionSeconds")!!.label)
        assertNull(RoomSettingsRows.row("nope"))
    }

    @Test fun rows_defaults_match_settings_defaults() {
        val d = RoomSettings()
        for (r in RoomSettingsRows.rows) assertEquals(r.key, r.default, RoomSettingsRows.valueOf(d, r.key))
    }

    @Test fun rows_apply_and_read_back() {
        var s = RoomSettings()
        s = RoomSettingsRows.apply(s, "choice", "BRIGHT")
        assertEquals(RoomChoice.BRIGHT, s.choice)
        s = RoomSettingsRows.apply(s, "latitudeDeg", "51.5")
        assertEquals(51.5, s.latitudeDeg!!, 1e-9)
        s = RoomSettingsRows.apply(s, "latitudeDeg", "")
        assertNull(s.latitudeDeg)
        s = RoomSettingsRows.apply(s, "transitionSeconds", "0")
        assertEquals(0f, s.transitionSeconds, 0f)
        s = RoomSettingsRows.apply(s, "eveningStart", "1200")
        assertEquals(1200, s.eveningStart)
        s = RoomSettingsRows.apply(s, "boostTextInBright", "false")
        assertFalse(s.boostTextInBright)
        // Garbage leaves the setting unchanged.
        assertEquals(s, RoomSettingsRows.apply(s, "choice", "SPACESHIP"))
        assertEquals(s, RoomSettingsRows.apply(s, "eveningStart", "abc"))
        assertEquals(s, RoomSettingsRows.apply(s, "nope", "1"))
    }

    @Test fun rows_summaries_are_human() {
        val s = RoomSettings(latitudeDeg = 28.6)
        assertEquals("Auto", RoomSettingsRows.summary(s, "choice"))
        assertEquals("28.6° N", RoomSettingsRows.summary(s, "latitudeDeg"))
        assertEquals("33.9° S", RoomSettingsRows.summary(s.copy(latitudeDeg = -33.9), "latitudeDeg"))
        assertEquals("Not set (fixed schedule)", RoomSettingsRows.summary(RoomSettings(), "latitudeDeg"))
        assertEquals("18:30", RoomSettingsRows.summary(s, "eveningStart"))
        assertEquals("On", RoomSettingsRows.summary(s, "boostTextInBright"))
        assertEquals("Smooth (1.2 s)", RoomSettingsRows.summary(s, "transitionSeconds"))
    }

    // ---- Preview ----

    @Test fun preview_describes_every_mode() {
        for (m in RoomMode.values()) {
            val d = RoomPreview.describe(m)
            assertTrue(d.isNotBlank())
            assertTrue(d, d.contains(RoomPreview.title(m)))
            assertTrue(d, d.contains(":1"))
        }
        assertTrue(RoomPreview.describe(RoomMode.BRIGHT).contains("4 dp"))
        assertEquals("Dark room", RoomPreview.title(RoomMode.CINEMA))
    }
}
