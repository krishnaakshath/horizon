package dev.tvlauncher.room

import org.junit.Assert.*
import org.junit.Test

/** Fallback chain (Weather -> estimate -> fixed table) and the schedule derived from Weather's times. */
class SunChainTest {
    private val ok = WeatherSunStatus(WeatherSunState.OK, "Mumbai", SunTimes(391, 1085))
    private val none = WeatherSunStatus(WeatherSunState.NO_CITY, null, null)

    @Test fun weatherFirst() {
        val p = SunChain.resolve(ok, 400 to 1090)
        assertEquals(SunSource.WEATHER, p.source)
        assertEquals(SunTimes(391, 1085), p.times)
    }

    @Test fun estimateWhenWeatherHasNothing() {
        val p = SunChain.resolve(none, 400 to 1090)
        assertEquals(SunSource.ESTIMATE, p.source)
        assertEquals(SunTimes(400, 1090), p.times)
    }

    @Test fun fixedWhenNeitherIsUsable() {
        assertEquals(SunSource.FIXED, SunChain.resolve(none, null).source)
        // An implausible estimate (midnight sun) is skipped too.
        assertEquals(SunSource.FIXED, SunChain.resolve(none, 120 to 1400).source)
        // Implausible Weather times fall through to the estimate.
        val odd = WeatherSunStatus(WeatherSunState.OK, "Tromso", SunTimes(120, 1400))
        assertEquals(SunSource.ESTIMATE, SunChain.resolve(odd, 400 to 1090).source)
    }

    @Test fun daylightChainFallsThroughInsaneAnswers() {
        val weather = DaylightSource { 120 to 1400 }.saneOnly()
        val estimate = DaylightSource { 400 to 1090 }.saneOnly()
        assertEquals(400 to 1090, DaylightSources.firstOf(weather, estimate).sunriseSunsetMinutes())
        assertNull(DaylightSources.firstOf(weather, DaylightSource { null }).sunriseSunsetMinutes())
        assertEquals(391 to 1085, DaylightSources.firstOf(DaylightSource { 391 to 1085 }.saneOnly(), estimate).sunriseSunsetMinutes())
    }

    @Test fun scheduleFollowsWeatherTimes() {
        // Mumbai 2026-10-20: sunrise 06:31, sunset 18:05.
        fun mode(h: Int, m: Int) = RoomLightModel.scheduleMode(h * 60 + m, 391, 1085)
        assertEquals(RoomMode.CINEMA, mode(5, 59))   // cinema ends 30 min before sunrise
        assertEquals(RoomMode.EVENING, mode(6, 1))
        assertEquals(RoomMode.EVENING, mode(7, 30))
        assertEquals(RoomMode.STANDARD, mode(7, 31)) // sunrise + 60
        assertEquals(RoomMode.STANDARD, mode(17, 34))
        assertEquals(RoomMode.EVENING, mode(17, 35)) // sunset - 30
        assertEquals(RoomMode.EVENING, mode(20, 29))
        assertEquals(RoomMode.CINEMA, mode(20, 30))  // never before 20:30
        // The fixed table would still say STANDARD at 17:35 and 18:00.
        assertEquals(RoomMode.STANDARD, RoomLightModel.scheduleMode(17 * 60 + 35, null, null))
    }

    @Test fun engineUsesWeatherTimesThenFallsBack() {
        var weather: Pair<Int, Int>? = 391 to 1085
        var minute = 17 * 60 + 40
        val modes = ArrayList<RoomMode>()
        val engine = RoomEngine(
            clock = { 0L },
            minuteOfDay = { minute },
            daylight = { s ->
                val est = if (s.latitudeDeg != null) DaylightSource { 7 * 60 to 17 * 60 + 30 } else null
                val w = DaylightSource { weather }.saneOnly()
                if (est == null) w else DaylightSources.firstOf(w, est.saneOnly())
            },
            transitionMs = { 0L },
            emit = { m, _, _ -> modes += m }
        )
        engine.start(RoomSettings(), luxActive = false)
        assertEquals(RoomMode.EVENING, modes.last())          // from Weather: after 17:35
        weather = null
        engine.tick()
        assertEquals(RoomMode.STANDARD, modes.last())         // fixed table: evening from 18:30
        engine.update(RoomSettings(latitudeDeg = 28.6), luxActive = false)
        assertEquals(RoomMode.EVENING, modes.last())          // estimate: sunset 17:30
        weather = 391 to 1085
        minute = 17 * 60 + 20
        engine.tick()
        assertEquals(RoomMode.STANDARD, modes.last())         // Weather again
    }
}
