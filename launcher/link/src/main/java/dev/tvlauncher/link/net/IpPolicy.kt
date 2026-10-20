package dev.tvlauncher.link.net

/**
 * Which addresses count as "this home's network". Pure (raw 4 or 16 byte addresses), so the rule
 * is JVM-testable and the same for the phone server (who may connect) and the outgoing clients
 * (which servers may be reached over plain http).
 *
 * Local: IPv4 10/8, 172.16/12, 192.168/16, 169.254/16 (link-local), 127/8 (this TV);
 * IPv6 ::1, fe80::/10, fc00::/7 and IPv4-mapped forms of the above. Carrier-grade NAT
 * (100.64/10) is NOT local: other customers of the same provider share it.
 */
object IpPolicy {
    private fun u(b: Byte) = b.toInt() and 0xff

    fun isLocal(addr: ByteArray?): Boolean = when (addr?.size) {
        4 -> isLocalV4(addr)
        16 -> isLocalV6(addr)
        else -> false
    }

    private fun isLocalV4(a: ByteArray): Boolean {
        val b0 = u(a[0]); val b1 = u(a[1])
        return b0 == 10 ||
            (b0 == 172 && b1 in 16..31) ||
            (b0 == 192 && b1 == 168) ||
            (b0 == 169 && b1 == 254) ||
            b0 == 127
    }

    private fun isLocalV6(a: ByteArray): Boolean {
        val first10Zero = (0 until 10).all { a[it].toInt() == 0 }
        if (first10Zero && u(a[10]) == 0xff && u(a[11]) == 0xff) return isLocalV4(a.copyOfRange(12, 16))
        if ((0 until 15).all { a[it].toInt() == 0 } && u(a[15]) == 1) return true // ::1
        val b0 = u(a[0]); val b1 = u(a[1])
        if (b0 == 0xfe && (b1 and 0xc0) == 0x80) return true // fe80::/10
        return (b0 and 0xfe) == 0xfc // fc00::/7
    }

    /** Parses a dotted IPv4 literal ("192.168.1.38"); null for anything else. */
    fun parseV4(s: String): ByteArray? {
        val parts = s.split('.')
        if (parts.size != 4) return null
        val out = ByteArray(4)
        for ((i, p) in parts.withIndex()) {
            if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
            if (p.length > 1 && p[0] == '0') return null // no octal-looking forms
            val v = p.toInt()
            if (v > 255) return null
            out[i] = v.toByte()
        }
        return out
    }

    /** Dotted form of a 4-byte address. */
    fun formatV4(a: ByteArray): String = a.joinToString(".") { u(it).toString() }

    /**
     * Picks the address the phone should type: a private IPv4 address, preferring 192.168/16,
     * then 10/8, then 172.16/12 (Wi-Fi and Ethernet), never loopback or link-local. Null if none.
     */
    fun pickLanAddress(candidates: List<ByteArray>): ByteArray? {
        val v4 = candidates.filter { it.size == 4 }
        fun rank(a: ByteArray): Int {
            val b0 = u(a[0]); val b1 = u(a[1])
            return when {
                b0 == 192 && b1 == 168 -> 0
                b0 == 10 -> 1
                b0 == 172 && b1 in 16..31 -> 2
                else -> -1
            }
        }
        return v4.filter { rank(it) >= 0 }.minByOrNull { rank(it) }
    }
}
