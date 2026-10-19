package dev.tvlauncher.browser.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpPolicyTest {
    private fun v4(a: Int, b: Int, c: Int, d: Int) = byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())
    private fun v6(vararg groups: Int): ByteArray {
        val out = ByteArray(16)
        groups.forEachIndexed { i, g -> out[i * 2] = (g shr 8).toByte(); out[i * 2 + 1] = g.toByte() }
        return out
    }

    @Test fun publicV4() {
        assertTrue(IpPolicy.isPublic(v4(8, 8, 8, 8)))
        assertTrue(IpPolicy.isPublic(v4(142, 250, 1, 1)))
    }

    @Test fun loopbackAndUnspecified() {
        assertFalse(IpPolicy.isPublic(v4(127, 0, 0, 1)))
        assertFalse(IpPolicy.isPublic(v4(127, 255, 255, 255)))
        assertFalse(IpPolicy.isPublic(v4(0, 0, 0, 0)))
    }

    @Test fun rfc1918_withBoundaries() {
        assertFalse(IpPolicy.isPublic(v4(10, 0, 0, 1)))
        assertFalse(IpPolicy.isPublic(v4(10, 255, 255, 255)))
        assertTrue(IpPolicy.isPublic(v4(11, 0, 0, 0)))
        assertTrue(IpPolicy.isPublic(v4(172, 15, 255, 255)))
        assertFalse(IpPolicy.isPublic(v4(172, 16, 0, 0)))
        assertFalse(IpPolicy.isPublic(v4(172, 31, 255, 255)))
        assertTrue(IpPolicy.isPublic(v4(172, 32, 0, 0)))
        assertFalse(IpPolicy.isPublic(v4(192, 168, 1, 38)))
        assertTrue(IpPolicy.isPublic(v4(192, 169, 0, 0)))
    }

    @Test fun linkLocal_metadata_cgnat() {
        assertFalse(IpPolicy.isPublic(v4(169, 254, 169, 254)))
        assertFalse(IpPolicy.isPublic(v4(169, 254, 0, 1)))
        assertFalse(IpPolicy.isPublic(v4(100, 64, 0, 0)))
        assertFalse(IpPolicy.isPublic(v4(100, 127, 255, 255)))
        assertTrue(IpPolicy.isPublic(v4(100, 63, 255, 255)))
        assertTrue(IpPolicy.isPublic(v4(100, 128, 0, 0)))
    }

    @Test fun multicastAndReserved() {
        assertFalse(IpPolicy.isPublic(v4(224, 0, 0, 1)))
        assertFalse(IpPolicy.isPublic(v4(255, 255, 255, 255)))
    }

    @Test fun v6Ranges() {
        assertFalse(IpPolicy.isPublic(v6(0, 0, 0, 0, 0, 0, 0, 1)))       // ::1
        assertFalse(IpPolicy.isPublic(ByteArray(16)))                      // ::
        assertFalse(IpPolicy.isPublic(v6(0xfc00, 0, 0, 0, 0, 0, 0, 1)))   // ULA
        assertFalse(IpPolicy.isPublic(v6(0xfdff, 1, 0, 0, 0, 0, 0, 1)))
        assertFalse(IpPolicy.isPublic(v6(0xfe80, 0, 0, 0, 0, 0, 0, 1)))   // link-local
        assertFalse(IpPolicy.isPublic(v6(0xfebf, 0, 0, 0, 0, 0, 0, 1)))
        assertFalse(IpPolicy.isPublic(v6(0xff02, 0, 0, 0, 0, 0, 0, 1)))   // multicast
        assertFalse(IpPolicy.isPublic(v6(0, 0, 0, 0, 0, 0xffff, 0xc0a8, 0x0101))) // ::ffff:192.168.1.1
        assertTrue(IpPolicy.isPublic(v6(0, 0, 0, 0, 0, 0xffff, 0x0808, 0x0808)))  // ::ffff:8.8.8.8
        assertTrue(IpPolicy.isPublic(v6(0x2001, 0x4860, 0x4860, 0, 0, 0, 0, 0x8888)))
        assertFalse(IpPolicy.isPublic(v6(0xfec0, 0, 0, 0, 0, 0, 0, 1)))   // site-local fec0::/10 (deprecated, local)
        assertFalse(IpPolicy.isPublic(v6(0xfeff, 0, 0, 0, 0, 0, 0, 1)))
        assertTrue(IpPolicy.isPublic(v6(0xfe7f, 0, 0, 0, 0, 0, 0, 1)))    // just below fe80::/10
    }

    @Test fun badLengths_notPublic() {
        assertFalse(IpPolicy.isPublic(ByteArray(0)))
        assertFalse(IpPolicy.isPublic(ByteArray(5)))
    }

    @Test fun v4_specialRanges_andEdges() {
        // 0.0.0.0/8
        assertFalse(IpPolicy.isPublic(v4(0, 255, 255, 255)))
        assertTrue(IpPolicy.isPublic(v4(1, 0, 0, 0)))
        // 192.0.0.0/24 and 192.0.2.0/24 (TEST-NET-1)
        assertFalse(IpPolicy.isPublic(v4(192, 0, 0, 0)))
        assertFalse(IpPolicy.isPublic(v4(192, 0, 0, 255)))
        assertTrue(IpPolicy.isPublic(v4(192, 0, 1, 0)))
        assertFalse(IpPolicy.isPublic(v4(192, 0, 2, 0)))
        assertFalse(IpPolicy.isPublic(v4(192, 0, 2, 255)))
        assertTrue(IpPolicy.isPublic(v4(192, 0, 3, 0)))
        // 198.18.0.0/15
        assertTrue(IpPolicy.isPublic(v4(198, 17, 255, 255)))
        assertFalse(IpPolicy.isPublic(v4(198, 18, 0, 0)))
        assertFalse(IpPolicy.isPublic(v4(198, 19, 255, 255)))
        assertTrue(IpPolicy.isPublic(v4(198, 20, 0, 0)))
        // 198.51.100.0/24 (TEST-NET-2), 203.0.113.0/24 (TEST-NET-3)
        assertTrue(IpPolicy.isPublic(v4(198, 51, 99, 255)))
        assertFalse(IpPolicy.isPublic(v4(198, 51, 100, 0)))
        assertFalse(IpPolicy.isPublic(v4(198, 51, 100, 255)))
        assertTrue(IpPolicy.isPublic(v4(198, 51, 101, 0)))
        assertTrue(IpPolicy.isPublic(v4(203, 0, 112, 255)))
        assertFalse(IpPolicy.isPublic(v4(203, 0, 113, 0)))
        assertFalse(IpPolicy.isPublic(v4(203, 0, 113, 255)))
        assertTrue(IpPolicy.isPublic(v4(203, 0, 114, 0)))
        // 224/4 multicast, 240/4 reserved incl. broadcast
        assertTrue(IpPolicy.isPublic(v4(223, 255, 255, 255)))
        assertFalse(IpPolicy.isPublic(v4(224, 0, 0, 0)))
        assertFalse(IpPolicy.isPublic(v4(239, 255, 255, 255)))
        assertFalse(IpPolicy.isPublic(v4(240, 0, 0, 0)))
        assertFalse(IpPolicy.isPublic(v4(255, 255, 255, 255)))
    }

    @Test fun v6_translationTunnelAndDocRanges() {
        assertFalse(IpPolicy.isPublic(v6(0x64, 0xff9b, 0, 0, 0, 0, 0x0808, 0x0808)))  // NAT64 64:ff9b::/96
        assertFalse(IpPolicy.isPublic(v6(0x64, 0xff9b, 1, 0, 0, 0, 0, 1)))            // 64:ff9b:1::/48
        assertTrue(IpPolicy.isPublic(v6(0x64, 0xff9a, 0, 0, 0, 0, 0, 1)))
        assertFalse(IpPolicy.isPublic(v6(0x2002, 0x0808, 0x0808, 0, 0, 0, 0, 1)))    // 6to4 2002::/16
        assertTrue(IpPolicy.isPublic(v6(0x2003, 0, 0, 0, 0, 0, 0, 1)))
        assertFalse(IpPolicy.isPublic(v6(0x2001, 0x0000, 0x4136, 0, 0, 0, 0, 1)))    // Teredo 2001::/32
        assertTrue(IpPolicy.isPublic(v6(0x2001, 0x0001, 0, 0, 0, 0, 0, 1)))
        assertFalse(IpPolicy.isPublic(v6(0x2001, 0x0db8, 0, 0, 0, 0, 0, 1)))         // documentation
        assertTrue(IpPolicy.isPublic(v6(0x2001, 0x0db9, 0, 0, 0, 0, 0, 1)))
        assertFalse(IpPolicy.isPublic(v6(0, 0, 0, 0, 0, 0, 0x0808, 0x0808)))         // IPv4-compatible ::8.8.8.8
        assertFalse(IpPolicy.isPublic(v6(0xff00, 0, 0, 0, 0, 0, 0, 0)))              // multicast
        assertTrue(IpPolicy.isPublic(v6(0xfbff, 0, 0, 0, 0, 0, 0, 1)))               // just below fc00::/7
    }
}
