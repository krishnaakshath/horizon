package dev.tvlauncher.link.ha

import dev.tvlauncher.link.core.LinkContract
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** How a supported entity is controlled from the TV. */
enum class Control(val wire: String) {
    TOGGLE(LinkContract.CONTROL_TOGGLE),
    SCENE(LinkContract.CONTROL_SCENE),
    CLIMATE(LinkContract.CONTROL_CLIMATE),
    MEDIA(LinkContract.CONTROL_MEDIA);

    companion object {
        /** lights, switches, fans, input booleans; scenes and scripts; climate; media players. */
        fun forDomain(domain: String): Control? = when (domain) {
            "light", "switch", "fan", "input_boolean" -> TOGGLE
            "scene", "script" -> SCENE
            "climate" -> CLIMATE
            "media_player" -> MEDIA
            else -> null
        }
    }
}

/** One Home Assistant entity, reduced to what the TV shows. */
data class HaEntity(
    val entityId: String,
    val name: String,
    val state: String,
    val control: Control,
    /** climate: the target temperature; null otherwise or when unknown. */
    val target: Double? = null,
    val current: Double? = null,
    val minTemp: Double = 7.0,
    val maxTemp: Double = 35.0,
    val step: Double = 0.5,
    val unit: String = "°",
    val mediaTitle: String? = null,
) {
    val domain: String get() = entityId.substringBefore('.')
    val available: Boolean get() = state != "unavailable" && state != "unknown"
    val isOn: Boolean get() = StateText.isOn(this)
}

/** Readable state lines (short, calm, no jargon). Pure. */
object StateText {
    fun isOn(e: HaEntity): Boolean = when (e.control) {
        Control.TOGGLE -> e.state == "on"
        Control.MEDIA -> e.state == "playing"
        Control.CLIMATE -> e.state != "off" && e.available
        Control.SCENE -> false
    }

    fun of(e: HaEntity): String {
        if (e.state == "unavailable") return "Unavailable"
        if (e.state == "unknown" && e.control != Control.SCENE) return "Unknown"
        return when (e.control) {
            Control.TOGGLE -> if (e.state == "on") "On" else "Off"
            Control.SCENE -> "Activate"
            Control.CLIMATE -> {
                val mode = e.state.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }
                val t = e.target?.let { " · ${temp(it)}${e.unit}" } ?: ""
                if (e.state == "off") "Off" else "$mode$t"
            }
            Control.MEDIA -> when (e.state) {
                "playing" -> e.mediaTitle?.let { "Playing · $it" } ?: "Playing"
                "paused" -> "Paused"
                "idle", "standby" -> "Idle"
                "off" -> "Off"
                else -> e.state.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
        }
    }

    /** 21.0 -> "21", 21.5 -> "21.5". */
    fun temp(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.ROOT, "%.1f", v)
}

/** Parses GET /api/states. Bounded and tolerant: a bad entry is skipped, not fatal. */
object HaParser {
    const val MAX_ENTITIES = 5000
    private val ENTITY = Regex("^[a-z_]{1,64}\\.[a-z0-9_]{1,128}$")

    fun isEntityId(s: String?): Boolean = s != null && ENTITY.matches(s)

    fun parseStates(json: String): List<HaEntity> {
        val arr = JSONArray(json)
        val out = ArrayList<HaEntity>()
        for (i in 0 until minOf(arr.length(), MAX_ENTITIES)) {
            val o = arr.optJSONObject(i) ?: continue
            parseEntity(o)?.let { out += it }
        }
        return out
    }

    fun parseEntity(o: JSONObject): HaEntity? {
        val id = o.optString("entity_id", "")
        if (!isEntityId(id)) return null
        val control = Control.forDomain(id.substringBefore('.')) ?: return null
        val a = o.optJSONObject("attributes") ?: JSONObject()
        val name = clean(a.optString("friendly_name", "")).ifEmpty { id.substringAfter('.').replace('_', ' ') }
        val state = clean(o.optString("state", "unknown")).lowercase(Locale.ROOT).take(32)
        return HaEntity(
            entityId = id,
            name = name.take(48),
            state = state,
            control = control,
            target = num(a, "temperature"),
            current = num(a, "current_temperature"),
            minTemp = num(a, "min_temp") ?: 7.0,
            maxTemp = num(a, "max_temp") ?: 35.0,
            step = num(a, "target_temp_step")?.takeIf { it > 0 && it <= 5 } ?: 0.5,
            unit = clean(a.optString("temperature_unit", a.optString("unit_of_measurement", "°"))).take(4).ifEmpty { "°" },
            mediaTitle = clean(a.optString("media_title", "")).take(48).ifEmpty { null },
        )
    }

    private fun num(o: JSONObject, key: String): Double? {
        val v = o.opt(key)
        return (v as? Number)?.toDouble()?.takeIf { it.isFinite() }
    }

    private fun clean(s: String): String = s.filter { !it.isISOControl() }.trim()
}

/**
 * What a TV action becomes on the Home Assistant side: POST /api/services/<domain>/<service>
 * with [body]. Pure; [forAction] returns null when the action does not fit the entity.
 */
data class ServiceCall(val domain: String, val service: String, val body: JSONObject) {
    companion object {
        fun forAction(e: HaEntity, action: String): ServiceCall? {
            val base = JSONObject().put("entity_id", e.entityId)
            return when (action) {
                LinkContract.ACTION_TOGGLE -> when (e.control) {
                    Control.TOGGLE -> ServiceCall(e.domain, "toggle", base)
                    Control.MEDIA -> ServiceCall(e.domain, "media_play_pause", base)
                    Control.CLIMATE -> ServiceCall(e.domain, if (e.state == "off") "turn_on" else "turn_off", base)
                    Control.SCENE -> ServiceCall(e.domain, "turn_on", base)
                }
                LinkContract.ACTION_ACTIVATE -> if (e.control == Control.SCENE) ServiceCall(e.domain, "turn_on", base) else null
                LinkContract.ACTION_PLAY_PAUSE -> if (e.control == Control.MEDIA) ServiceCall(e.domain, "media_play_pause", base) else null
                LinkContract.ACTION_TEMP_UP, LinkContract.ACTION_TEMP_DOWN -> {
                    if (e.control != Control.CLIMATE) return null
                    val cur = e.target ?: e.current ?: return null
                    val dir = if (action == LinkContract.ACTION_TEMP_UP) 1 else -1
                    val next = (Math.round((cur + dir * e.step) / e.step) * e.step).coerceIn(e.minTemp, e.maxTemp)
                    ServiceCall(e.domain, "set_temperature", base.put("temperature", next))
                }
                else -> null
            }
        }
    }
}
