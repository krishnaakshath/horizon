package dev.tvlauncher.weather

import org.junit.Assert.*
import org.junit.Test

class SunParserTest {
    private val oct20Utc = 1792454400000L // 2026-10-20T00:00Z
    private val mumbaiRise = oct20Utc + 3_660_000L // 06:31 +05:30 == 01:01Z
    private val kolkata = SunParser.zoneOf("Asia/Kolkata")

    @Test fun localStringWithIanaZone() {
        assertEquals(mumbaiRise, SunParser.epochMs("2026-10-20T06:31", kolkata, 0))
        assertEquals(mumbaiRise + 30_000L, SunParser.epochMs("2026-10-20T06:31:30", kolkata, 0))
    }

    @Test fun localStringWithOffsetOnly() {
        assertEquals(mumbaiRise, SunParser.epochMs("2026-10-20T06:31", null, 19_800))
        // Negative offsets (west of Greenwich): 06:31 at -04:00 == 10:31Z
        assertEquals(oct20Utc + (10 * 60 + 31) * 60_000L, SunParser.epochMs("2026-10-20T06:31", null, -14_400))
    }

    @Test fun explicitOffsetInStringWins() {
        assertEquals(mumbaiRise, SunParser.epochMs("2026-10-20T06:31+05:30", null, -36_000))
        assertEquals(mumbaiRise, SunParser.epochMs("2026-10-20T06:31+0530", kolkata, 0))
        assertEquals(mumbaiRise, SunParser.epochMs("2026-10-20T01:01Z", kolkata, 3600))
        assertEquals(oct20Utc + (10 * 60 + 31) * 60_000L, SunParser.epochMs("2026-10-20T06:31-04:00", null, 0))
    }

    @Test fun unixSeconds() {
        assertEquals(mumbaiRise, SunParser.epochMs(mumbaiRise / 1000L, null, 0))
        assertNull(SunParser.epochMs(-5, null, 0))
        assertNull(SunParser.epochMs(Double.NaN, null, 0))
    }

    @Test fun ianaZoneIsExactAcrossDst() {
        // Berlin leaves summer time on 2026-10-25; a response fetched in summer says +7200.
        val berlin = SunParser.zoneOf("Europe/Berlin")
        val oct26Utc = oct20Utc + 6 * 86_400_000L
        // 06:58 CET (+01:00) == 05:58Z; the offset-only approximation would say 04:58Z.
        assertEquals(oct26Utc + (5 * 60 + 58) * 60_000L, SunParser.epochMs("2026-10-26T06:58", berlin, 7200))
        assertEquals(oct26Utc + (4 * 60 + 58) * 60_000L, SunParser.epochMs("2026-10-26T06:58", null, 7200))
    }

    @Test fun unknownZoneIdFallsBackToOffset() {
        assertNull(SunParser.zoneOf("Mars/Olympus_Mons"))
        assertNull(SunParser.zoneOf(""))
        assertNull(SunParser.zoneOf(null))
        assertNotNull(SunParser.zoneOf("GMT"))
    }

    @Test fun malformedStrings() {
        for (s in listOf("", "06:31", "2026-10-20", "2026-13-01T06:31", "2026-02-30T06:31", "2026-10-20T24:00",
            "2026-10-20T06:61", "2026-10-20 06:31", "2026-10-20T06:31+25:00", "x2026-10-20T06:31")) {
            assertNull(s, SunParser.epochMs(s, null, 0))
        }
        assertNull(SunParser.epochMs(null, null, 0))
        assertNull(SunParser.epochMs(true, null, 0))
    }

    private val mumbaiDay = oct20Utc - 19_800_000L // local midnight

    @Test fun pairForANormalDay() {
        val p = SunParser.pair(mumbaiDay, "2026-10-20T06:31", "2026-10-20T18:05", kolkata, 19_800)!!
        assertEquals(mumbaiRise, p.first)
        assertEquals(mumbaiRise + (11 * 60 + 34) * 60_000L, p.second)
    }

    @Test fun polarNightAndDayGiveNull() {
        // Missing values (polar night or day as some responses send it).
        assertNull(SunParser.pair(mumbaiDay, null, null, kolkata, 0))
        assertNull(SunParser.pair(mumbaiDay, "2026-10-20T06:31", null, kolkata, 0))
        // Sunrise == sunset (zero-length day).
        assertNull(SunParser.pair(mumbaiDay, "2026-10-20T00:00", "2026-10-20T00:00", kolkata, 0))
        // Midnight to midnight: a 24 h "day".
        assertNull(SunParser.pair(mumbaiDay, "2026-10-20T00:00", "2026-10-21T00:00", kolkata, 0))
        // Sunset before sunrise.
        assertNull(SunParser.pair(mumbaiDay, "2026-10-20T18:05", "2026-10-20T06:31", kolkata, 0))
    }

    @Test fun sunriseMustBeOnTheEntrysDate() {
        assertNull(SunParser.pair(mumbaiDay, "2026-10-22T06:31", "2026-10-22T18:05", kolkata, 0))
        assertNull(SunParser.pair(mumbaiDay, "2026-10-18T06:31", "2026-10-18T18:05", kolkata, 0))
    }

    @Test fun validStored() {
        assertTrue(SunParser.validStored(1000L, 2000L))
        assertFalse(SunParser.validStored(null, 2000L))
        assertFalse(SunParser.validStored(1000L, null))
        assertFalse(SunParser.validStored(2000L, 1000L))
        assertFalse(SunParser.validStored(1000L, 1000L + 86_400_000L))
        assertFalse(SunParser.validStored(0L, 1000L))
    }
}
