package dev.tvlauncher.store.core

/**
 * Classifies raw IP addresses (4 or 16 bytes). Used for literal hosts in URLs and for every address a host
 * name resolves to before connecting, so a public-looking name cannot point the download at the local network.
 */
object IpRanges {

    fun isBlocked(addr: ByteArray): Boolean = when (addr.size) {
        4 -> isBlockedV4(addr)
        16 -> isBlockedV6(addr)
        else -> true
    }

    private fun u(b: Byte) = b.toInt() and 0xff

    private fun isBlockedV4(a: ByteArray): Boolean {
        val b0 = u(a[0]); val b1 = u(a[1])
        return b0 == 0 ||                          // "this" network
            b0 == 10 ||                            // private
            b0 == 127 ||                           // loopback
            (b0 == 100 && b1 in 64..127) ||        // carrier-grade NAT
            (b0 == 169 && b1 == 254) ||            // link-local / cloud metadata
            (b0 == 172 && b1 in 16..31) ||         // private
            (b0 == 192 && b1 == 168) ||            // private
            (b0 == 192 && b1 == 0 && u(a[2]) == 0) || // IETF protocol assignments
            (b0 == 198 && (b1 == 18 || b1 == 19)) || // benchmarking
            b0 >= 224                              // multicast, reserved, broadcast
    }

    private fun isBlockedV6(a: ByteArray): Boolean {
        val first10Zero = (0 until 10).all { a[it].toInt() == 0 }
        if (first10Zero && u(a[10]) == 0xff && u(a[11]) == 0xff) {
            return isBlockedV4(a.copyOfRange(12, 16)) // IPv4-mapped
        }
        if ((0 until 12).all { a[it].toInt() == 0 }) return true // ::, ::1 and deprecated IPv4-compatible
        val b0 = u(a[0]); val b1 = u(a[1])
        if (b0 == 0xff) return true                       // multicast
        if (b0 == 0xfe && (b1 and 0xc0) == 0x80) return true // link-local fe80::/10
        if (b0 == 0xfe && (b1 and 0xc0) == 0xc0) return true // site-local (deprecated)
        if ((b0 and 0xfe) == 0xfc) return true            // unique local fc00::/7
        val g1 = (b0 shl 8) or b1
        val g2 = (u(a[2]) shl 8) or u(a[3])
        // NAT64 well-known prefix 64:ff9b::/96 embeds an IPv4 address in the last 4 bytes; anything else under
        // 64:ff9b (e.g. the local-use 64:ff9b:1::/48) is refused outright.
        if (g1 == 0x0064 && g2 == 0xff9b) {
            val zeros = (4 until 12).all { a[it].toInt() == 0 }
            return !zeros || isBlockedV4(a.copyOfRange(12, 16))
        }
        // 6to4 2002::/16 embeds an IPv4 address in bytes 2..5.
        if (g1 == 0x2002) return isBlockedV4(a.copyOfRange(2, 6))
        // Teredo 2001:0::/32 carries an obfuscated client address: refuse outright.
        if (g1 == 0x2001 && g2 == 0x0000) return true
        return false
    }
}
