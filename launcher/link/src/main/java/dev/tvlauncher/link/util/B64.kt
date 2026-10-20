package dev.tvlauncher.link.util

/** Standard Base64 (RFC 4648, with padding). java.util.Base64 needs Android 8; this runs everywhere. */
object B64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val INDEX = IntArray(128) { -1 }.also { t -> ALPHABET.forEachIndexed { i, c -> t[c.code] = i } }

    fun encode(data: ByteArray): String {
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < data.size) {
            val n = (data[i].toInt() and 0xff shl 16) or (data[i + 1].toInt() and 0xff shl 8) or (data[i + 2].toInt() and 0xff)
            sb.append(ALPHABET[n shr 18 and 63]).append(ALPHABET[n shr 12 and 63]).append(ALPHABET[n shr 6 and 63]).append(ALPHABET[n and 63])
            i += 3
        }
        val rest = data.size - i
        if (rest == 1) {
            val n = data[i].toInt() and 0xff shl 16
            sb.append(ALPHABET[n shr 18 and 63]).append(ALPHABET[n shr 12 and 63]).append("==")
        } else if (rest == 2) {
            val n = (data[i].toInt() and 0xff shl 16) or (data[i + 1].toInt() and 0xff shl 8)
            sb.append(ALPHABET[n shr 18 and 63]).append(ALPHABET[n shr 12 and 63]).append(ALPHABET[n shr 6 and 63]).append('=')
        }
        return sb.toString()
    }

    /** Null on any character outside the alphabet (whitespace is skipped) or a bad length. */
    fun decode(text: String): ByteArray? {
        val s = text.filter { !it.isWhitespace() }
        if (s.length % 4 != 0) return null
        val pad = s.takeLast(2).count { it == '=' }
        if (s.dropLast(pad).contains('=')) return null
        val out = ByteArray(s.length / 4 * 3 - pad)
        var o = 0
        var i = 0
        while (i < s.length) {
            var n = 0
            for (k in 0 until 4) {
                val c = s[i + k]
                val v = if (c == '=') 0 else if (c.code < 128) INDEX[c.code] else -1
                if (v < 0) return null
                n = n shl 6 or v
            }
            for (k in 0 until 3) if (o < out.size) out[o++] = (n shr (16 - 8 * k) and 0xff).toByte()
            i += 4
        }
        return out
    }
}
