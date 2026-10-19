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
        assertTrue(IpPolicy.isPublic(v6(0xfec0, 0, 0, 0, 0, 0, 0, 1)))    // outside fe80::/10
    }

    @Test fun badLengths_notPublic() {
        assertFalse(IpPolicy.isPublic(ByteArray(0)))
        assertFalse(IpPolicy.isPublic(ByteArray(5)))
    }
}
