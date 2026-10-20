package dev.tvlauncher.ambient.widgets

import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** The owner's ambient widget choices (per profile: the "ambient_widgets" store). */
data class AmbientWidgetSettings(
    val calendar: Boolean = false,
    val photos: Boolean = false,
    val albumArt: Boolean = false,
    val photoSeconds: Int = 60
)

/** One upcoming calendar event. */
data class CalendarEvent(val title: String, val begin: Long, val allDay: Boolean)

/** Pure ambient-widget rules (JVM-tested): which rows show, calendar lines, photo captions and sources. */
object AmbientWidgetsModel {
    const val MAX_EVENTS = 3
    const val LOOKAHEAD_MS = 7 * 86_400_000L
    const val MAX_PHOTOS = 400
    val PHOTO_SECONDS = listOf(30, 60, 120, 300)
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")

    enum class CalendarRow { ALLOW, TOGGLE }

    /** Without READ_CALENDAR the page offers a friendly "Allow" row and the widget stays hidden. */
    fun calendarRow(granted: Boolean): CalendarRow = if (granted) CalendarRow.TOGGLE else CalendarRow.ALLOW

    fun showCalendar(s: AmbientWidgetSettings, granted: Boolean) = s.calendar && granted

    /** Next [MAX_EVENTS] events that have not ended: soonest first, blank titles named. */
    fun upcoming(events: List<CalendarEvent>, now: Long): List<CalendarEvent> =
        events.filter { it.begin + (if (it.allDay) 86_400_000L else 0L) > now }
            .sortedBy { it.begin }
            .map { if (it.title.isBlank()) it.copy(title = "Busy") else it.copy(title = it.title.trim().take(60)) }
            .take(MAX_EVENTS)

    /** "Today 18:30", "Tomorrow", "Fri 09:00" relative to [now] in [tz]. */
    fun whenLabel(e: CalendarEvent, now: Long, tz: TimeZone, h24: Boolean, locale: Locale = Locale.getDefault()): String {
        // All-day events are stored at UTC midnight.
        val cal = Calendar.getInstance(if (e.allDay) TimeZone.getTimeZone("UTC") else tz, locale).apply { timeInMillis = e.begin }
        val today = Calendar.getInstance(tz, locale).apply { timeInMillis = now }
        val dayDiff = dayNumber(cal) - dayNumber(today)
        val day = when (dayDiff) {
            0 -> "Today"
            1 -> "Tomorrow"
            else -> cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale) ?: ""
        }
        if (e.allDay) return day
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val time = if (h24) "%02d:%02d".format(h, m)
            else "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
        return "$day $time"
    }

    private fun dayNumber(c: Calendar) = c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR)

    /** EXIF "2023:07:14 18:22:01" -> "14 Jul 2023"; null when missing or malformed. */
    fun exifDate(raw: String?): String? {
        val m = Regex("""^(\d{4}):(\d{2}):(\d{2})""").find(raw?.trim() ?: return null) ?: return null
        val (y, mo, d) = m.destructured
        val month = mo.toInt()
        if (month !in 1..12 || d.toInt() !in 1..31 || y.toInt() < 1900) return null
        val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return "${d.toInt()} ${names[month - 1]} $y"
    }

    /** Caption under a photo: "14 Jul 2023 · Holiday", the folder alone, or nothing. */
    fun caption(exifDate: String?, folder: String?): String? {
        val f = folder?.takeIf { it.isNotBlank() && it !in GENERIC_FOLDERS }
        return listOfNotNull(exifDate, f).joinToString(" · ").ifEmpty { null }
    }

    private val GENERIC_FOLDERS = setOf("DCIM", "Pictures", "Camera", "100ANDRO", "100MEDIA")

    fun isImage(f: File) = f.isFile && f.extension.lowercase(Locale.ROOT) in IMAGE_EXT && !f.name.startsWith(".")

    /**
     * The photo-frame folders: Pictures on the TV and DCIM / Pictures on each USB stick under
     * [storageRoot] (/storage), skipping the emulated internal volume and "self".
     */
    fun photoRoots(storageRoot: File?, pictures: File?): List<File> {
        val out = ArrayList<File>()
        pictures?.let { out += it }
        storageRoot?.listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }?.sortedBy { it.name }?.forEach { v ->
            out += File(v, "DCIM"); out += File(v, "Pictures")
        }
        return out
    }

    /** Up to [MAX_PHOTOS] images below [roots] (two folder levels deep), in a stable order. */
    fun scan(roots: List<File>, max: Int = MAX_PHOTOS): List<File> {
        val out = ArrayList<File>()
        fun walk(dir: File, depth: Int) {
            if (out.size >= max || depth > 2) return
            val kids = try { dir.listFiles() } catch (e: SecurityException) { null } ?: return
            for (k in kids.sortedBy { it.name }) {
                if (out.size >= max) return
                if (k.isDirectory) { if (!k.name.startsWith(".")) walk(k, depth + 1) } else if (isImage(k)) out += k
            }
        }
        roots.forEach { if (it.isDirectory) walk(it, 0) }
        return out
    }

    /** The next photo index after [i] (wraps). */
    fun next(i: Int, size: Int): Int = if (size <= 0) -1 else Math.floorMod(i + 1, size)

    /** Album art wins over the photo frame while something plays; the photo frame otherwise. */
    enum class Backdrop { WALLPAPER, PHOTO, ALBUM_ART }

    fun backdrop(s: AmbientWidgetSettings, hasArt: Boolean, hasPhoto: Boolean): Backdrop = when {
        s.albumArt && hasArt -> Backdrop.ALBUM_ART
        s.photos && hasPhoto -> Backdrop.PHOTO
        else -> Backdrop.WALLPAPER
    }
}
