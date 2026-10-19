package dev.tvlauncher.browser.cursor

import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

/**
 * What the page reports under the cursor (from evaluateJavascript). Display hints only: never
 * used for navigation. [snapX]/[snapY] are the nearest clickable's center in CSS px, if any.
 */
data class HitInfo(
    val tag: String = "", val href: String? = null, val role: String? = null, val editable: Boolean = false,
    val clickable: Boolean = false, val cursor: String = "", val snapX: Float? = null, val snapY: Float? = null
) {
    companion object {
        val EMPTY = HitInfo()

        /** Accepts the raw evaluateJavascript result (a JSON string literal) or a plain JSON object. Never throws. */
        fun parse(raw: String?): HitInfo = try {
            if (raw == null || raw.length > 32 * 1024) EMPTY else parseUnsafe(raw)
        } catch (e: Exception) {
            EMPTY
        } catch (e: StackOverflowError) {
            EMPTY
        }

        private fun parseUnsafe(raw: String): HitInfo {
            var v: Any? = JSONTokener(raw).nextValue()
            if (v is String) v = JSONTokener(v).nextValue()
            val o = v as? JSONObject ?: return EMPTY
            fun str(k: String, max: Int): String? = (o.opt(k) as? String)?.take(max)
            fun num(x: Any?): Float? {
                val d = (x as? Number)?.toDouble() ?: return null
                return if (d.isNaN() || d.isInfinite() || kotlin.math.abs(d) > 1e7) null else d.toFloat()
            }
            val snap = o.opt("snap") as? JSONObject
            val sx = num(snap?.opt("x")); val sy = num(snap?.opt("y"))
            return HitInfo(
                tag = (str("tag", 32) ?: "").lowercase(Locale.ROOT),
                href = str("href", 2048)?.takeIf { it.isNotEmpty() },
                role = str("role", 32)?.lowercase(Locale.ROOT),
                editable = o.opt("editable") == true,
                clickable = o.opt("clickable") == true,
                cursor = (str("cursor", 32) ?: "").lowercase(Locale.ROOT),
                snapX = if (sx != null && sy != null) sx else null,
                snapY = if (sx != null && sy != null) sy else null
            )
        }
    }
}

/** Maps page hit info + browser state to a cursor look. Edge scroll beats loading beats element. */
object CursorStateMapper {
    private val TEXT_TAGS = setOf("input", "textarea", "select")
    private val LINK_TAGS = setOf("a", "button", "summary", "label")

    fun map(h: HitInfo, loading: Boolean, atEdge: Boolean, disabled: Boolean): CursorState {
        if (disabled) return CursorState.DISABLED
        if (atEdge) return CursorState.SCROLL
        val element = when {
            h.cursor == "not-allowed" -> CursorState.DISABLED
            h.editable || h.tag in TEXT_TAGS -> CursorState.TEXT
            h.cursor == "text" -> CursorState.TEXT
            h.tag in LINK_TAGS || h.href != null || h.role == "button" || h.role == "link" || h.clickable -> CursorState.LINK
            h.cursor == "pointer" -> CursorState.LINK
            else -> CursorState.DEFAULT
        }
        return if (element == CursorState.DEFAULT && loading) CursorState.BUSY else element
    }
}
