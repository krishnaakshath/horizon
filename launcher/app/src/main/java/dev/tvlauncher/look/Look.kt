package dev.tvlauncher.look

import dev.tvlauncher.settings.GlassStyle
import org.json.JSONArray
import org.json.JSONObject

/** Corner roundness: a multiplier on every shared radius token (cards, rows, chips). */
enum class Corners(val label: String, val scale: Float) {
    SHARP("Sharp", 0.25f), SOFT("Soft", 0.6f), ROUND("Round", 1f), PILL("Pill", 1.6f);

    /** [baseDp] (a [dev.tvlauncher.ui.GlassTokens] radius) in this roundness; never negative. */
    fun radiusDp(baseDp: Int): Int = Math.round(baseDp * scale).coerceAtLeast(if (this == SHARP) 2 else 0)
}

/** Where the glass gets its colour. */
enum class LookTint(val label: String) { ACCENT("Accent"), NEUTRAL("Neutral"), BACKDROP("Backdrop colour") }

/** How app icons sit on home tiles. */
enum class IconStyle(val label: String) { CARD("Rounded card"), CIRCLE("Circle"), BANNER("Banner only") }

/**
 * Everything Settings > Appearance > Look controls, as one value. [glassStyle] null = the TV's
 * default style; [accent] null = keep the current accent; [textSize] null = keep the current text
 * size (it is a household setting). [amount] is the glass amount, 0..100 % ([GlassAmount]).
 */
data class Look(
    val glassStyle: GlassStyle? = null,
    val amount: Int = DEFAULT_AMOUNT,
    val corners: Corners = Corners.ROUND,
    val tint: LookTint = LookTint.BACKDROP,
    val accent: Int? = null,
    val textSize: String? = null,
    val iconStyle: IconStyle = IconStyle.CARD
) {
    companion object {
        const val DEFAULT_AMOUNT = 60
        const val STEP = 5
    }
}

/** A look with a name: a built-in preset or one of the user's saved looks. */
data class NamedLook(val name: String, val look: Look)

object LookPresets {
    val HORIZON = NamedLook("Horizon", Look())
    val CLEAR = NamedLook("Clear", Look(glassStyle = GlassStyle.CLEAR, amount = 85, corners = Corners.ROUND, tint = LookTint.NEUTRAL))
    val CINEMA = NamedLook("Cinema", Look(glassStyle = GlassStyle.FROSTED, amount = 20, corners = Corners.SOFT,
        tint = LookTint.NEUTRAL, accent = 0xFFC9D1DE.toInt()))
    val BRIGHT_ROOM = NamedLook("Bright room", Look(glassStyle = GlassStyle.FROSTED, amount = 35, corners = Corners.ROUND,
        tint = LookTint.ACCENT, accent = 0xFF4C8DFF.toInt()))
    val CLASSIC = NamedLook("Classic", Look(glassStyle = GlassStyle.SOLID, amount = 0, corners = Corners.SOFT,
        tint = LookTint.NEUTRAL, iconStyle = IconStyle.CARD))

    val ALL = listOf(HORIZON, CLEAR, CINEMA, BRIGHT_ROOM, CLASSIC)

    /** Twelve extra accent swatches (the 8 Appearance presets stay first). */
    val SWATCHES: List<Int> = listOf(
        0xFF3D6BFF, 0xFF6C8CFF, 0xFFB07CFF, 0xFFE05CFF, 0xFFFF4F6D, 0xFFFF8A5C,
        0xFFFFC94D, 0xFFB8E05C, 0xFF3DDC97, 0xFF2EC4B6, 0xFF4FB3FF, 0xFFE8ECF2
    ).map { it.toInt() }
}

/**
 * The look as small, strict JSON: versioned, every value clamped or defaulted on its own, unknown
 * keys ignored, documents over [MAX_BYTES] refused. Never throws.
 */
object LookCodec {
    const val VERSION = 1
    const val MAX_BYTES = 8 * 1024
    const val MAX_SAVED = 5
    const val MAX_NAME = 24
    const val KIND = "h02.look"

    fun toJson(look: Look, name: String? = null): JSONObject = JSONObject().apply {
        put("kind", KIND)
        put("version", VERSION)
        name?.let { put("name", cleanName(it)) }
        look.glassStyle?.let { put("glassStyle", it.name) }
        put("amount", clampAmount(look.amount))
        put("corners", look.corners.name)
        put("tint", look.tint.name)
        look.accent?.let { put("accent", it.toLong() and 0xFFFFFFFFL) }
        look.textSize?.let { put("textSize", it) }
        put("iconStyle", look.iconStyle.name)
    }

    fun encode(look: Look, name: String? = null): String = toJson(look, name).toString()

    /** A look from [text], or null when it is not a look document (wrong kind, newer version, too big, not JSON). */
    fun decode(text: String?): NamedLook? {
        if (text.isNullOrBlank() || text.toByteArray().size > MAX_BYTES) return null
        val o = try { JSONObject(text) } catch (e: Exception) { return null }
        return fromJson(o)
    }

    fun fromJson(o: JSONObject): NamedLook? = try {
        val kind = o.opt("kind")
        val version = (o.opt("version") as? Number)?.toInt()
        if (kind != KIND || version == null || version < 1 || version > VERSION) null
        else {
            val d = Look()
            val look = Look(
                glassStyle = enumOrNull<GlassStyle>(o, "glassStyle"),
                amount = (o.opt("amount") as? Number)?.let { clampAmount(it.toDouble().let { v -> if (v.isNaN()) d.amount.toDouble() else v }.toInt()) } ?: d.amount,
                corners = enumOrNull<Corners>(o, "corners") ?: d.corners,
                tint = enumOrNull<LookTint>(o, "tint") ?: d.tint,
                accent = (o.opt("accent") as? Number)?.toLong()?.takeIf { it in 0L..0xFFFFFFFFL }?.let { it.toInt() or 0xFF000000.toInt() },
                textSize = (o.opt("textSize") as? String)?.takeIf { it in TEXT_SIZES },
                iconStyle = enumOrNull<IconStyle>(o, "iconStyle") ?: d.iconStyle
            )
            NamedLook(cleanName(o.opt("name") as? String ?: ""), look)
        }
    } catch (e: Exception) {
        null
    }

    /** The current look (no name; kind/version still checked). */
    fun currentOf(text: String?): Look = decode(text)?.look ?: Look()

    fun encodeSaved(list: List<NamedLook>): String = JSONArray().apply {
        list.take(MAX_SAVED).forEach { put(toJson(it.look, it.name)) }
    }.toString()

    fun decodeSaved(text: String?): List<NamedLook> {
        if (text.isNullOrBlank()) return emptyList()
        val a = try { JSONArray(text) } catch (e: Exception) { return emptyList() }
        val out = ArrayList<NamedLook>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val n = fromJson(o) ?: continue
            if (n.name.isNotEmpty() && out.none { it.name.equals(n.name, true) }) out += n
            if (out.size == MAX_SAVED) break
        }
        return out
    }

    /** Saves [look] as [name]: replaces a look of the same name, else appends; null when full. */
    fun save(list: List<NamedLook>, name: String, look: Look): List<NamedLook>? {
        val n = cleanName(name)
        if (n.isEmpty()) return null
        val i = list.indexOfFirst { it.name.equals(n, true) }
        if (i >= 0) return list.toMutableList().also { it[i] = NamedLook(n, look) }
        if (list.size >= MAX_SAVED) return null
        return list + NamedLook(n, look)
    }

    fun delete(list: List<NamedLook>, name: String): List<NamedLook> = list.filterNot { it.name.equals(name, true) }

    /** Printable, trimmed, at most [MAX_NAME] characters. */
    fun cleanName(s: String): String =
        s.filter { !it.isISOControl() }.trim().take(MAX_NAME).trim()

    fun clampAmount(v: Int): Int = v.coerceIn(0, 100)

    /** [v] snapped to the slider's [Look.STEP] steps. */
    fun snap(v: Int): Int = (Math.round(clampAmount(v) / Look.STEP.toFloat()) * Look.STEP).coerceIn(0, 100)

    private val TEXT_SIZES = setOf("S", "M", "L", "XL")

    private inline fun <reified E : Enum<E>> enumOrNull(o: JSONObject, key: String): E? {
        val raw = o.opt(key) as? String ?: return null
        return enumValues<E>().firstOrNull { it.name == raw }
    }
}
