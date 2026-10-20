package dev.tvlauncher.routines

import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

/** What a time routine does when it fires. */
sealed class RoutineAction {
    abstract val kind: Kind

    enum class Kind(val label: String, val needsScreen: Boolean) {
        AMBIENT("Open ambient (clock and weather)", true),
        BEDTIME("Bedtime mode on", false),
        BEDTIME_OFF("Bedtime mode off", false),
        ROOM("Room lighting", false),
        PROFILE("Switch profile", false),
        LAUNCH("Open an app", true),
        FOCUS_START("Start focus mode", false),
        FOCUS_STOP("Stop focus mode", false)
    }

    object Ambient : RoutineAction() { override val kind = Kind.AMBIENT }
    object BedtimeOn : RoutineAction() { override val kind = Kind.BEDTIME }
    object BedtimeOff : RoutineAction() { override val kind = Kind.BEDTIME_OFF }
    /** [choice]: a room lighting choice name (AUTO, CINEMA, EVENING, STANDARD, BRIGHT). */
    data class Room(val choice: String) : RoutineAction() { override val kind = Kind.ROOM }
    data class Profile(val id: String) : RoutineAction() { override val kind = Kind.PROFILE }
    data class Launch(val pkg: String, val label: String) : RoutineAction() { override val kind = Kind.LAUNCH }
    data class Focus(val start: Boolean) : RoutineAction() { override val kind = if (start) Kind.FOCUS_START else Kind.FOCUS_STOP }
}

/**
 * One time routine: at [minuteOfDay] on the [days] of the week (0 = Monday .. 6 = Sunday), run
 * [action]. At most [RoutineModel.MAX] exist.
 */
data class Routine(
    val id: Int,
    val enabled: Boolean,
    val minuteOfDay: Int,
    val days: Set<Int>,
    val action: RoutineAction
)

/**
 * Pure routine rules: day presets, the next fire time across days, time zones and daylight-saving
 * changes ([nextFire]), the soonest alarm for a list ([soonest]), what to do with a late alarm
 * ([decide]) and a tolerant JSON form. Uses only java.util (minSdk 24 has no java.time).
 */
object RoutineModel {
    const val MAX = 5
    const val MINUTE_MS = 60_000L
    const val DAY_MS = 86_400_000L
    /** A routine that fires later than this (the TV was in standby) skips its on-screen action. */
    const val LATE_GRACE_MS = 15 * MINUTE_MS

    val EVERY_DAY = (0..6).toSet()
    val WEEKDAYS = (0..4).toSet()
    val WEEKENDS = setOf(5, 6)
    private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** The day choices offered on the routine page, in order: presets, then single days. */
    val DAY_PRESETS: List<Set<Int>> = listOf(EVERY_DAY, WEEKDAYS, WEEKENDS) + (0..6).map { setOf(it) }

    fun daysLabel(days: Set<Int>): String = when (days) {
        EVERY_DAY -> "Every day"
        WEEKDAYS -> "Weekdays"
        WEEKENDS -> "Weekends"
        emptySet<Int>() -> "Never"
        else -> days.sorted().joinToString(", ") { DAY_NAMES[it] }
    }

    fun timeLabel(minuteOfDay: Int, h24: Boolean = true): String {
        val m = Math.floorMod(minuteOfDay, 24 * 60)
        val h = m / 60
        val mm = m % 60
        return if (h24) "%02d:%02d".format(h, mm)
        else "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, mm, if (h < 12) "AM" else "PM")
    }

    /** 15-minute steps for the time row. */
    const val STEP_MIN = 15
    val SLOTS = 24 * 60 / STEP_MIN

    /** A new routine: weekdays at 07:00, open ambient (clock and weather). */
    fun defaultRoutine(id: Int) = Routine(id, true, 7 * 60, WEEKDAYS, RoutineAction.Ambient)

    /** The next free id, or null when [MAX] routines exist. */
    fun nextId(list: List<Routine>): Int? =
        if (list.size >= MAX) null else (1..MAX + list.size).first { id -> list.none { it.id == id } }

    // ---- schedule -------------------------------------------------------------------------------

    /** Local calendar day of [instant] in [tz], as days since 1970-01-01. */
    fun localDay(instant: Long, tz: TimeZone): Long = Math.floorDiv(instant + tz.getOffset(instant), DAY_MS)

    /** 0 = Monday .. 6 = Sunday (1970-01-01 was a Thursday). */
    fun dayOfWeek(localDay: Long): Int = Math.floorMod(localDay + 3, 7L).toInt()

    /**
     * The instant of [minuteOfDay] local time on [localDay]. A time that does not exist (the
     * spring-forward gap) moves forward by the gap, like a wall clock (02:30 -> 03:30); a time that
     * happens twice (fall back) fires at its first occurrence.
     */
    fun instantOf(localDay: Long, minuteOfDay: Int, tz: TimeZone): Long {
        val local = localDay * DAY_MS + minuteOfDay * MINUTE_MS
        val before = tz.getOffset(local - DAY_MS)
        val after = tz.getOffset(local + DAY_MS)
        val valid = listOf(before, after).distinct().map { local - it }.filter { tz.getOffset(it) + it == local }
        if (valid.isNotEmpty()) return valid.minOrNull()!!
        return local - before
    }

    /** The first time after [now] that [r] fires, or null (off, or no days). */
    fun nextFire(r: Routine, now: Long, tz: TimeZone): Long? {
        if (!r.enabled || r.days.isEmpty()) return null
        val today = localDay(now, tz)
        for (d in -1L..8L) {
            val day = today + d
            if (dayOfWeek(day) !in r.days) continue
            val t = instantOf(day, r.minuteOfDay, tz)
            if (t > now) return t
        }
        return null
    }

    /** The soonest fire time of [list] after [now] and the ids due then; null when none is on. */
    fun soonest(list: List<Routine>, now: Long, tz: TimeZone): Pair<Long, List<Int>>? {
        val times = list.mapNotNull { r -> nextFire(r, now, tz)?.let { r.id to it } }
        val t = times.minOfOrNull { it.second } ?: return null
        return t to times.filter { it.second == t }.map { it.first }
    }

    enum class Decision { RUN, SKIP_LATE, SKIP_STANDBY }

    /**
     * What a fired routine does. Settings-only actions (room lighting, Bedtime mode, profile,
     * focus) always apply. On-screen actions (ambient, an app) skip when the screen is off (the TV
     * is in standby) or when the alarm arrived more than [LATE_GRACE_MS] late: an ambient screen
     * at noon for a 07:00 routine would only be confusing.
     */
    fun decide(kind: RoutineAction.Kind, lateMs: Long, screenOn: Boolean): Decision = when {
        !kind.needsScreen -> Decision.RUN
        !screenOn -> Decision.SKIP_STANDBY
        lateMs > LATE_GRACE_MS -> Decision.SKIP_LATE
        else -> Decision.RUN
    }

    fun decisionLabel(d: Decision): String = when (d) {
        Decision.RUN -> "Ran on time"
        Decision.SKIP_LATE -> "Skipped: the TV woke up too late"
        Decision.SKIP_STANDBY -> "Skipped: the TV was in standby"
    }

    // ---- JSON -----------------------------------------------------------------------------------

    fun toJson(list: List<Routine>): String {
        val arr = JSONArray()
        for (r in list) {
            val a = JSONObject().put("kind", r.action.kind.name)
            when (val x = r.action) {
                is RoutineAction.Room -> a.put("choice", x.choice)
                is RoutineAction.Profile -> a.put("id", x.id)
                is RoutineAction.Launch -> a.put("pkg", x.pkg).put("label", x.label)
                else -> Unit
            }
            arr.put(JSONObject().put("id", r.id).put("on", r.enabled).put("min", r.minuteOfDay)
                .put("days", JSONArray(r.days.sorted())).put("action", a))
        }
        return JSONObject().put("v", 1).put("routines", arr).toString()
    }

    private val PKG = Regex("^[A-Za-z0-9_.]{1,255}$")

    /** Tolerant: broken entries are dropped, duplicate ids keep the first, at most [MAX] are kept. */
    fun fromJson(json: String?): List<Routine> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = try { JSONObject(json).optJSONArray("routines") } catch (e: Exception) { null } ?: return emptyList()
        val out = ArrayList<Routine>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optInt("id", -1)
            if (id <= 0 || out.any { it.id == id }) continue
            val min = o.optInt("min", -1)
            if (min !in 0 until 24 * 60) continue
            val d = o.optJSONArray("days") ?: continue
            val days = (0 until d.length()).mapNotNull { (d.opt(it) as? Int)?.takeIf { v -> v in 0..6 } }.toSet()
            val action = parseAction(o.optJSONObject("action")) ?: continue
            out += Routine(id, o.optBoolean("on", true), min, days, action)
            if (out.size >= MAX) break
        }
        return out
    }

    private fun parseAction(a: JSONObject?): RoutineAction? {
        if (a == null) return null
        return when (a.optString("kind")) {
            "AMBIENT" -> RoutineAction.Ambient
            "BEDTIME" -> RoutineAction.BedtimeOn
            "BEDTIME_OFF" -> RoutineAction.BedtimeOff
            "ROOM" -> a.optString("choice").takeIf { it in ROOM_CHOICES }?.let { RoutineAction.Room(it) }
            "PROFILE" -> a.optString("id").takeIf { dev.tvlauncher.profiles.ProfileModel.isValidId(it) }?.let { RoutineAction.Profile(it) }
            "LAUNCH" -> a.optString("pkg").takeIf { PKG.matches(it) }?.let { RoutineAction.Launch(it, a.optString("label").take(60)) }
            "FOCUS_START" -> RoutineAction.Focus(true)
            "FOCUS_STOP" -> RoutineAction.Focus(false)
            else -> null
        }
    }

    /** Room lighting choices a routine may apply, with their Settings names. */
    val ROOM_CHOICES = listOf("AUTO", "CINEMA", "EVENING", "STANDARD", "BRIGHT")
    val ROOM_LABELS = listOf("Auto", "Dark room", "Evening", "Normal", "Bright room")

    /** One line for a routine row: "Weekdays 07:00 · Open ambient (clock and weather)". */
    fun summary(r: Routine, h24: Boolean = true, profileName: (String) -> String? = { null }): String {
        val what = when (val a = r.action) {
            is RoutineAction.Room -> "Room lighting: ${ROOM_LABELS.getOrElse(ROOM_CHOICES.indexOf(a.choice)) { a.choice }}"
            is RoutineAction.Profile -> "Switch to ${profileName(a.id) ?: "a deleted profile"}"
            is RoutineAction.Launch -> "Open ${a.label.ifBlank { a.pkg }}"
            else -> a.kind.label
        }
        return "${daysLabel(r.days)} ${timeLabel(r.minuteOfDay, h24)} · $what"
    }
}
