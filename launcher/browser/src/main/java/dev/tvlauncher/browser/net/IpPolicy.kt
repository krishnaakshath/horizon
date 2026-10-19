package dev.tvlauncher.browser.net

/**
 * Whether a resolved address is on the public internet. The icon/suggestion fetchers refuse
 * anything else (loopback, RFC 1918, CGNAT, link-local incl. 169.254.169.254, multicast,
 * reserved, IPv6 ULA / loopback / link-local / multicast, IPv4-mapped private).
 */
object IpPolicy {
    fun isPublic(addr: ByteArray): Boolean = when (addr.size) {
        4 -> v4Public(addr[0].u(), addr[1].u())
        16 -> v6Public(addr)
        else -> false
    }

    private fun Byte.u() = toInt() and 0xFF

    private fun v4Public(a: Int, b: Int): Boolean = when {
        a == 0 || a == 10 || a == 127 -> false
        a == 100 && b in 64..127 -> false        // CGNAT 100.64/10
        a == 169 && b == 254 -> false            // link-local + metadata
        a == 172 && b in 16..31 -> false         // 172.16/12
        a == 192 && b == 168 -> false            // 192.168/16
        a == 198 && (b == 18 || b == 19) -> false // benchmarking 198.18/15
        a >= 224 -> false                        // multicast + reserved + broadcast
        else -> true
    }

    private fun v6Public(x: ByteArray): Boolean {
        val first = x[0].u()
        val second = x[1].u()
        if (x.all { it.toInt() == 0 }) return false                                   // ::
        if ((0 until 15).all { x[it].toInt() == 0 } && x[15].toInt() == 1) return false // ::1
        if ((0 until 10).all { x[it].toInt() == 0 } && x[10].u() == 0xFF && x[11].u() == 0xFF) {
            return v4Public(x[12].u(), x[13].u())                                       // ::ffff:a.b.c.d
        }
        if (first and 0xFE == 0xFC) return false                                        // fc00::/7 ULA
        if (first == 0xFE && second and 0xC0 == 0x80) return false                      // fe80::/10
        if (first == 0xFF) return false                                                 // multicast
        return true
    }
}
