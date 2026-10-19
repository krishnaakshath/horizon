package dev.tvlauncher.room

import java.util.Locale

/**
 * Declarative rows for the Settings page's "Room lighting" section. The Settings UI renders each
 * [Row] with its existing row widgets (CHOICE = picker, TOGGLE = switch, LATITUDE = picker of
 * [Row.options] plus "Not set", TIME = time picker in 15-min steps) and writes back through [apply].
 * Values travel as strings so the UI needs no knowledge of [RoomSettings].
 */
object RoomSettingsRows {
    enum class Type { CHOICE, TOGGLE, LATITUDE, TIME }

    data class Option(val value: String, val label: String)

    data class Row(
        val key: String,
        val type: Type,
        val label: String,
        val subtitle: String,
        /** Serialized default; null = "not set". */
        val default: String?,
        val options: List<Option> = emptyList(),
        /** Icon drawable name, or null. */
        val iconKey: String? = null
    )

    private val choiceOptions = RoomChoice.values().map { Option(it.name, RoomChoiceCycler.label(it)) }

    private val transitionOptions = listOf(
        Option("0", "Off"), Option("0.6", "Quick"), Option("1.2", "Smooth"), Option("2", "Slow")
    )

    /** A short ladder of latitudes the remote can step through (the UI also accepts free entry). */
    private val latitudeOptions = (-60..70 step 5).map { Option(it.toString(), latText(it.toDouble())) }

    val rows: List<Row> = listOf(
        Row("choice", Type.CHOICE, "Room lighting",
            "Adapts brightness, contrast and glass to your room. Auto uses a light sensor if the TV has one, else the time of day.",
            RoomChoice.AUTO.name, choiceOptions, "ic_room_auto"),
        Row("latitudeDeg", Type.LATITUDE, "Location for sunrise and sunset",
            "Optional. Your latitude lets Auto follow the real sunset; nothing leaves the TV.",
            null, latitudeOptions),
        Row("transitionSeconds", Type.CHOICE, "Smooth transition",
            "How gently the screen changes between room modes.", "1.2", transitionOptions),
        Row("dayStart", Type.TIME, "Day starts", "Used by Auto when sunrise is unknown.", Schedule.DEFAULT.dayStart.toString()),
        Row("eveningStart", Type.TIME, "Evening starts", "Used by Auto when sunset is unknown.", Schedule.DEFAULT.eveningStart.toString()),
        Row("nightStart", Type.TIME, "Dark room from", "Used by Auto at night.", Schedule.DEFAULT.nightStart.toString()),
        Row("boostTextInBright", Type.TOGGLE, "Bolder text in bright rooms",
            "Heavier, slightly larger text when the room is bright.", "true")
    )

    fun row(key: String): Row? = rows.firstOrNull { it.key == key }

    /** The serialized current value of [key], comparable with [Row.default]. */
    fun valueOf(s: RoomSettings, key: String): String? = when (key) {
        "choice" -> s.choice.name
        "latitudeDeg" -> s.latitudeDeg?.let { num(it) }
        "transitionSeconds" -> num(s.transitionSeconds.toDouble())
        "dayStart" -> s.dayStart.toString()
        "eveningStart" -> s.eveningStart.toString()
        "nightStart" -> s.nightStart.toString()
        "boostTextInBright" -> s.boostTextInBright.toString()
        else -> null
    }

    /** Returns [s] with [key] set from [value]; invalid input or unknown keys leave [s] unchanged. */
    fun apply(s: RoomSettings, key: String, value: String?): RoomSettings {
        val v = value?.trim()
        return when (key) {
            "choice" -> RoomChoice.parse(v)?.let { s.copy(choice = it) } ?: s
            "latitudeDeg" ->
                if (v.isNullOrEmpty()) s.copy(latitudeDeg = null)
                else RoomSettings.latitude(v.toDoubleOrNull())?.let { s.copy(latitudeDeg = it) } ?: s
            "transitionSeconds" -> v?.toFloatOrNull()?.takeIf { it.isFinite() }
                ?.let { s.copy(transitionSeconds = it.coerceIn(0f, RoomSettings.MAX_TRANSITION_SECONDS)) } ?: s
            "dayStart" -> RoomSettings.minute(v?.toIntOrNull())?.let { s.copy(dayStart = it) } ?: s
            "eveningStart" -> RoomSettings.minute(v?.toIntOrNull())?.let { s.copy(eveningStart = it) } ?: s
            "nightStart" -> RoomSettings.minute(v?.toIntOrNull())?.let { s.copy(nightStart = it) } ?: s
            "boostTextInBright" -> when (v?.lowercase(Locale.ROOT)) {
                "true" -> s.copy(boostTextInBright = true)
                "false" -> s.copy(boostTextInBright = false)
                else -> s
            }
            else -> s
        }
    }

    /** Human summary for the row's value line. */
    fun summary(s: RoomSettings, key: String): String = when (key) {
        "choice" -> RoomChoiceCycler.label(s.choice)
        "latitudeDeg" -> s.latitudeDeg?.let { latText(it) } ?: "Not set (fixed schedule)"
        "transitionSeconds" -> {
            val t = s.transitionSeconds
            val name = when {
                t <= 0f -> "Off"
                t < 0.9f -> "Quick"
                t < 1.6f -> "Smooth"
                else -> "Slow"
            }
            if (t <= 0f) name else "$name (${num(t.toDouble())} s)"
        }
        "dayStart" -> hhmm(s.dayStart)
        "eveningStart" -> hhmm(s.eveningStart)
        "nightStart" -> hhmm(s.nightStart)
        "boostTextInBright" -> if (s.boostTextInBright) "On" else "Off"
        else -> ""
    }

    fun hhmm(m: Int): String = String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)

    private fun latText(lat: Double): String {
        val hemi = if (lat < 0) "S" else "N"
        return "${num(kotlin.math.abs(lat))}° $hemi"
    }

    /** "1.2", "2", "28.6": shortest plain form, one decimal at most. */
    private fun num(d: Double): String {
        val r = Math.round(d * 10.0) / 10.0
        return if (r == Math.floor(r)) r.toLong().toString() else r.toString()
    }
}

/** Spotlight quick action: cycles Auto -> Dark -> Evening -> Normal -> Bright -> Auto. Pure. */
object RoomChoiceCycler {
    private val ORDER = listOf(RoomChoice.AUTO, RoomChoice.CINEMA, RoomChoice.EVENING, RoomChoice.STANDARD, RoomChoice.BRIGHT)

    fun next(choice: RoomChoice): RoomChoice = ORDER[(ORDER.indexOf(choice) + 1) % ORDER.size]

    fun label(choice: RoomChoice): String = when (choice) {
        RoomChoice.AUTO -> "Auto"
        RoomChoice.CINEMA -> "Dark room"
        RoomChoice.EVENING -> "Evening"
        RoomChoice.STANDARD -> "Normal"
        RoomChoice.BRIGHT -> "Bright room"
    }

    /** Toast after a press, e.g. "Room lighting: Bright room (manual)" / "Room lighting: Auto (now Evening)". */
    fun toast(choice: RoomChoice, effective: RoomMode): String =
        if (choice == RoomChoice.AUTO) "Room lighting: Auto (now ${RoomPreview.title(effective)})"
        else "Room lighting: ${label(choice)} (manual)"
}

/** Model for the Spotlight quick-action chip. [iconKey] names a drawable `R.drawable.<iconKey>`. */
data class RoomQuickAction(
    val id: String,
    val label: String,
    val value: String,
    val iconKey: String,
    val nextChoice: RoomChoice
) {
    companion object {
        const val ID = "room_lighting"

        fun iconKey(choice: RoomChoice): String = "ic_room_" + choice.name.lowercase(Locale.ROOT)

        fun forChoice(choice: RoomChoice) = RoomQuickAction(
            id = ID,
            label = "Room lighting",
            value = RoomChoiceCycler.label(choice),
            iconKey = iconKey(choice),
            nextChoice = RoomChoiceCycler.next(choice)
        )
    }
}
