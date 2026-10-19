package dev.tvlauncher.sound

class ParsedWav(val sampleRate: Int, val samples: ShortArray)

/** Minimal RIFF/WAVE reader: PCM 16-bit, mono or stereo (downmixed), 8-48 kHz, <= 500 KB. */
object WavParser {
    const val MAX_BYTES = 500 * 1024

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, o: Int): Long =
        (u16(b, o).toLong()) or (u16(b, o + 2).toLong() shl 16)
    private fun tag(b: ByteArray, o: Int, s: String) =
        s.indices.all { b[o + it] == s[it].code.toByte() }

    fun parse(bytes: ByteArray): ParsedWav? = try {
        parseInternal(bytes)
    } catch (e: Exception) {
        null
    }

    private fun parseInternal(b: ByteArray): ParsedWav? {
        if (b.size < 44 || b.size > MAX_BYTES) return null
        if (!tag(b, 0, "RIFF") || !tag(b, 8, "WAVE")) return null
        var pos = 12
        var channels = 0
        var rate = 0
        var fmtOk = false
        while (pos + 8 <= b.size) {
            val size = u32(b, pos + 4)
            val body = pos + 8
            if (tag(b, pos, "fmt ")) {
                if (size < 16 || body + 16 > b.size) return null
                if (u16(b, body) != 1) return null
                channels = u16(b, body + 2)
                rate = u32(b, body + 4).toInt()
                if (u16(b, body + 14) != 16) return null
                if (channels !in 1..2 || rate !in 8000..48000) return null
                fmtOk = true
            } else if (tag(b, pos, "data")) {
                if (!fmtOk) return null
                // Declared data size larger than the file means truncated: reject.
                if (size > (b.size - body).toLong()) return null
                val len = size.toInt()
                val frameBytes = 2 * channels
                val frames = len / frameBytes
                if (frames == 0) return null
                val out = ShortArray(frames)
                for (i in 0 until frames) {
                    val o = body + i * frameBytes
                    val l = u16(b, o).toShort().toInt()
                    out[i] = if (channels == 1) l.toShort()
                    else ((l + u16(b, o + 2).toShort().toInt()) / 2).toShort()
                }
                return ParsedWav(rate, out)
            }
            val next = body.toLong() + size + (size and 1L)
            if (next > b.size) return null
            pos = next.toInt()
        }
        return null
    }
}
