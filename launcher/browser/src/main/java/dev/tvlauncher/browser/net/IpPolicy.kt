package dev.tvlauncher.browser.net

/**
 * Whether a resolved address is on the public internet. The icon/suggestion fetchers refuse
 * anything else (loopback, RFC 1918, CGNAT, link-local incl. 169.254.169.254, multicast,
 * reserved, IPv6 ULA / loopback / link-local / multicast, IPv4-mapped private).
 */
object IpPolicy {
    fun isPublic(addr: ByteArray): Boolean = when (addr.size) {
        4 -> v4Public(addr[0].u(), addr[1].u(), addr[2].u())
        16 -> v6Public(addr)
        else -> false
    }

    private fun Byte.u() = toInt() and 0xFF

    private fun v4Public(a: Int, b: Int, c: Int): Boolean = when {
        a == 0 || a == 10 || a == 127 -> false
        a == 100 && b in 64..127 -> false            // CGNAT 100.64/10
        a == 169 && b == 254 -> false                // link-local + metadata
        a == 172 && b in 16..31 -> false             // 172.16/12
        a == 192 && b == 0 && (c == 0 || c == 2) -> false // 192.0.0/24, TEST-NET-1
        a == 192 && b == 168 -> false                // 192.168/16
        a == 198 && (b == 18 || b == 19) -> false     // 198.18/15
        a == 198 && b == 51 && c == 100 -> false     // TEST-NET-2
        a == 203 && b == 0 && c == 113 -> false      // TEST-NET-3
        a >= 224 -> false                            // 224/4 multicast, 240/4 reserved, broadcast
        else -> true
    }

    private fun v6Public(x: ByteArray): Boolean {
        fun g(i: Int) = (x[i * 2].u() shl 8) or x[i * 2 + 1].u()
        if ((0 until 6).all { g(it) == 0 }) {
            if (g(6) == 0 && g(7) <= 1) return false   // :: and ::1
            return false                               // IPv4-compatible ::a.b.c.d (deprecated)
        }
        if ((0 until 5).all { g(it) == 0 } && g(5) == 0xFFFF) return v4Public(x[12].u(), x[13].u(), x[14].u()) // ::ffff:v4
        val g0 = g(0); val g1 = g(1)
        if (g0 == 0x0064 && g1 == 0xFF9B) return false                 // NAT64 64:ff9b::/96 and 64:ff9b:1::/48
        if (g0 == 0x2002) return false                                 // 6to4
        if (g0 == 0x2001 && g1 == 0x0000) return false                 // Teredo 2001::/32
        if (g0 == 0x2001 && g1 == 0x0DB8) return false                 // documentation
        if (g0 and 0xFE00 == 0xFC00) return false                      // fc00::/7 ULA
        if (g0 and 0xFFC0 == 0xFE80) return false                      // fe80::/10 link-local
        if (g0 and 0xFFC0 == 0xFEC0) return false                      // fec0::/10 site-local
        if (g0 and 0xFF00 == 0xFF00) return false                      // multicast
        return true
    }
}
