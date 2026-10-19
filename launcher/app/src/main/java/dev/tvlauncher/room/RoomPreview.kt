package dev.tvlauncher.room

import java.util.Locale

/** Plain-text description of a mode for the Settings page (and debugging). Pure. */
object RoomPreview {
    fun title(mode: RoomMode): String = when (mode) {
        RoomMode.CINEMA -> "Dark room"
        RoomMode.EVENING -> "Evening"
        RoomMode.STANDARD -> "Normal"
        RoomMode.BRIGHT -> "Bright room"
    }

    fun blurb(mode: RoomMode): String = when (mode) {
        RoomMode.CINEMA -> "Deep blacks, dimmed whites and soft glows so nothing glares in the dark."
        RoomMode.EVENING -> "Warmer and a touch lifted for a lamp-lit room."
        RoomMode.STANDARD -> "Balanced contrast; blacks are a dark gray so the screen isn't a mirror."
        RoomMode.BRIGHT -> "Lifted blacks, solid panels, white bold text and a thick focus ring against glare and reflections."
    }

    /** Title, blurb and the key tokens with measured contrast ratios. */
    fun describe(mode: RoomMode): String {
        val p = Palettes.of(mode)
        val text = Contrast.ratio(p.textPrimary, p.backdropFloor)
        val sec = Contrast.ratio(p.textSecondary, p.surface)
        val glass = Contrast.ratio(p.textPrimary, Contrast.over(p.glassFill, p.backdropTop))
        val ring = Contrast.ratio(p.focusRingColor, p.surface)
        val glassPct = ((p.glassFill ushr 24) and 255) * 100 / 255
        return buildString {
            append(title(mode)).append('\n')
            append(blurb(mode)).append('\n')
            append("Black floor ").append(hex(p.backdropFloor))
            append(" · text ").append(hex(p.textPrimary)).append(" (").append(r(text)).append(")\n")
            append("Secondary text ").append(r(sec)).append(" · glass ").append(glassPct).append("% opaque (")
                .append(r(glass)).append(")\n")
            append("Focus ring ").append(fmt(p.focusRingWidthDp)).append(" dp (").append(r(ring)).append(")")
            append(" · glow ").append((p.glowStrength * 100).toInt()).append('%')
            if (p.textWeightBoost > 0.5f) append(" · bold text")
        }
    }

    private fun r(x: Double) = String.format(Locale.ROOT, "%.1f:1", x)
    private fun hex(c: Int) = String.format(Locale.ROOT, "#%06X", c and 0xFFFFFF)
    private fun fmt(f: Float) = if (f == Math.floor(f.toDouble()).toFloat()) f.toInt().toString() else f.toString()
}
