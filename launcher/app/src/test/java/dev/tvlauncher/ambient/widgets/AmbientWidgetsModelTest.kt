package dev.tvlauncher.ambient.widgets

import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientWidgetsModelTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L
    // 2024-01-01 (Monday) 12:00 UTC
    private val now = 1_704_110_400_000L

    @Test fun calendarIsAnAllowRowUntilGrantedAndHiddenWithoutIt() {
        assertEquals(AmbientWidgetsModel.CalendarRow.ALLOW, AmbientWidgetsModel.calendarRow(false))
        assertEquals(AmbientWidgetsModel.CalendarRow.TOGGLE, AmbientWidgetsModel.calendarRow(true))
        assertFalse(AmbientWidgetsModel.showCalendar(AmbientWidgetSettings(calendar = true), granted = false))
        assertTrue(AmbientWidgetsModel.showCalendar(AmbientWidgetSettings(calendar = true), granted = true))
    }

    @Test fun nextThreeEventsSoonestFirst() {
        val e = listOf(
            CalendarEvent("Late", now + 3 * day, false),
            CalendarEvent("  ", now + 3_600_000L, false),
            CalendarEvent("Past", now - 3_600_000L, false),
            CalendarEvent("Today all day", now - 12 * 3_600_000L, true),
            CalendarEvent("Later", now + 5 * day, false)
        )
        val up = AmbientWidgetsModel.upcoming(e, now)
        assertEquals(listOf("Today all day", "Busy", "Late"), up.map { it.title })
    }

    @Test fun whenLabels() {
        assertEquals("Today 13:00", AmbientWidgetsModel.whenLabel(CalendarEvent("a", now + 3_600_000L, false), now, utc, true, Locale.US))
        assertEquals("Tomorrow 1:00 PM", AmbientWidgetsModel.whenLabel(CalendarEvent("a", now + day + 3_600_000L, false), now, utc, false, Locale.US))
        assertEquals("Wed", AmbientWidgetsModel.whenLabel(CalendarEvent("a", now - 12 * 3_600_000L + 2 * day, true), now, utc, true, Locale.US))
    }

    @Test fun exifDateCaption() {
        assertEquals("14 Jul 2023", AmbientWidgetsModel.exifDate("2023:07:14 18:22:01"))
        assertNull(AmbientWidgetsModel.exifDate("0000:00:00 00:00:00"))
        assertNull(AmbientWidgetsModel.exifDate(null))
        assertEquals("14 Jul 2023 · Holiday", AmbientWidgetsModel.caption("14 Jul 2023", "Holiday"))
        assertEquals("14 Jul 2023", AmbientWidgetsModel.caption("14 Jul 2023", "DCIM"))
        assertNull(AmbientWidgetsModel.caption(null, "Camera"))
    }

    @Test fun photoRootsAndScan() {
        val root = Files.createTempDirectory("storage").toFile()
        File(root, "emulated/0").mkdirs()
        File(root, "ABCD-1234/DCIM/Trip").mkdirs()
        File(root, "ABCD-1234/DCIM/Trip/a.JPG").writeText("x")
        File(root, "ABCD-1234/DCIM/.thumbs").mkdirs()
        File(root, "ABCD-1234/DCIM/.thumbs/t.jpg").writeText("x")
        File(root, "ABCD-1234/DCIM/notes.txt").writeText("x")
        val roots = AmbientWidgetsModel.photoRoots(root, null)
        assertEquals(listOf("DCIM", "Pictures"), roots.map { it.name })
        assertEquals(listOf("a.JPG"), AmbientWidgetsModel.scan(roots).map { it.name })
        root.deleteRecursively()
    }

    @Test fun albumArtWinsWhileMusicPlays() {
        val all = AmbientWidgetSettings(photos = true, albumArt = true)
        assertEquals(AmbientWidgetsModel.Backdrop.ALBUM_ART, AmbientWidgetsModel.backdrop(all, hasArt = true, hasPhoto = true))
        assertEquals(AmbientWidgetsModel.Backdrop.PHOTO, AmbientWidgetsModel.backdrop(all, hasArt = false, hasPhoto = true))
        assertEquals(AmbientWidgetsModel.Backdrop.WALLPAPER, AmbientWidgetsModel.backdrop(AmbientWidgetSettings(), true, true))
        assertEquals(0, AmbientWidgetsModel.next(2, 3))
        assertEquals(-1, AmbientWidgetsModel.next(0, 0))
    }

    @Test fun pageWithinBudget() {
        assertTrue(AmbientWidgetsSection.spec.withinBudget)
    }
}
