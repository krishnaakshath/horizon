package dev.tvlauncher.browser.cursor

import org.json.JSONObject

enum class CursorStyle(val label: String) { ARROW("Arrow"), DOT("Dot"), RING("Ring"), HALO("Halo"), CLASSIC("Classic") }
enum class Magnet(val label: String) { OFF("Off"), LOW("Low"), MED("Medium"), HIGH("High") }
enum class CursorState { DEFAULT, LINK, TEXT, BUSY, SCROLL, DISABLED }

/** Cursor preferences, persisted as tolerant JSON (bad/unknown values fall back or clamp). */
data class CursorSettings(
    val style: CursorStyle = CursorStyle.ARROW,
    val sizeDp: Int = 28,
    val color: Int = -1,
    val opacity: Float = 1f,
    val speed: Float = 1f,
    val magnet: Magnet = Magnet.MED,
    val trail: Boolean = false,
    val autoHideMs: Long = 3000L
) {
    fun encode(): String = JSONObject()
        .put("style", style.name).put("size", sizeDp).put("color", color).put("opacity", opacity.toDouble())
        .put("speed", speed.toDouble()).put("magnet", magnet.name).put("trail", trail).put("autoHide", autoHideMs)
        .toString()

    companion object {
        const val MIN_OPACITY = 0.3f
        val SIZES = listOf(20, 28, 36, 48)
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val OPACITIES = listOf(0.4f, 0.6f, 0.8f, 1f)
        val AUTO_HIDE = listOf(0L, 3000L, 5000L, 10000L)
        /** White first, then 8 accents. */
        val COLORS = intArrayOf(
            -1, 0xFF5B8CFF.toInt(), 0xFF22D3EE.toInt(), 0xFF34D399.toInt(), 0xFFFACC15.toInt(),
            0xFFFB923C.toInt(), 0xFFF472B6.toInt(), 0xFFA78BFA.toInt(), 0xFFF87171.toInt()
        )

        fun snapSize(v: Int): Int = SIZES.minByOrNull { kotlin.math.abs(it - v) } ?: 28

        fun decode(json: String?): CursorSettings {
            val d = CursorSettings()
            val o = try { JSONObject(json ?: return d) } catch (e: Exception) { return d }
            fun f(key: String, def: Float): Float {
                val v = o.optDouble(key, def.toDouble())
                return if (v.isNaN() || v.isInfinite()) def else v.toFloat()
            }
            return CursorSettings(
                style = CursorStyle.values().firstOrNull { it.name == o.optString("style") } ?: CursorStyle.ARROW,
                sizeDp = snapSize(o.optInt("size", d.sizeDp)),
                color = o.optInt("color", d.color),
                opacity = f("opacity", d.opacity).coerceIn(MIN_OPACITY, 1f),
                speed = f("speed", d.speed).coerceIn(0.5f, 2f),
                magnet = Magnet.values().firstOrNull { it.name == o.optString("magnet") } ?: Magnet.MED,
                trail = o.optBoolean("trail", false),
                autoHideMs = o.optLong("autoHide", d.autoHideMs).coerceIn(0L, 60_000L)
            )
        }
    }
}
