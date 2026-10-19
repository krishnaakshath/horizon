package dev.tvlauncher.store.core

/**
 * Makes untrusted strings (APK labels, F-Droid names/summaries, GitHub tags/release names, URLs) safe to show:
 * bidi overrides/isolates (U+202A–202E, U+2066–2069) and every other control (Cc) or format (Cf) character —
 * zero-width spaces/joiners, BOM, soft hyphen, ... — are removed, all whitespace (newlines, tabs, NBSP, ...)
 * becomes a single space, the result is trimmed and capped at [max] code points (with "…").
 * This stops "Invoice‮gpj.exe" style spoofing and layout-breaking input.
 */
object DisplayText {

    const val LABEL = 80
    const val NAME = 80
    const val SUMMARY = 160
    const val RELEASE_NAME = 100
    const val TAG = 60
    const val URL = 300

    fun sanitize(s: String?, max: Int): String {
        if (s.isNullOrEmpty() || max <= 0) return ""
        val out = StringBuilder()
        var count = 0
        var pendingSpace = false
        var more = false
        // Bound the work on huge input: never scan further than needed.
        val scanLimit = minOf(s.length.toLong(), max.toLong() * 16 + 1024).toInt()
        var i = 0
        while (i < s.length) {
            if (i >= scanLimit) { more = true; break }
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) { pendingSpace = count > 0; continue }
            val type = Character.getType(cp)
            if (type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt() ||
                type == Character.UNASSIGNED.toInt() || type == Character.PRIVATE_USE.toInt() ||
                type == Character.SURROGATE.toInt()
            ) continue
            if (pendingSpace) {
                if (count >= max) { more = true; break }
                out.append(' '); count++; pendingSpace = false
            }
            if (count >= max) { more = true; break }
            out.appendCodePoint(cp); count++
        }
        if (!more) return out.toString()
        // Keep max-1 code points and add an ellipsis.
        var keep = max - 1
        var end = 0
        while (keep > 0 && end < out.length) { end += Character.charCount(out.codePointAt(end)); keep-- }
        return out.substring(0, end).trimEnd() + "…"
    }
}
