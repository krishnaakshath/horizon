package dev.tvlauncher.store.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpRangesTest {

    private fun v4(a: Int, b: Int, c: Int, d: Int) = byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())

    private fun v6(vararg groups: Int): ByteArray {
        val out = ByteArray(16)
        groups.forEachIndexed { i, g -> out[i * 2] = (g shr 8).toByte(); out[i * 2 + 1] = g.toByte() }
        return out
    }

    @Test fun blocksPrivateIpv4() {
        assertTrue(IpRanges.isBlocked(v4(127, 0, 0, 1)))
        assertTrue(IpRanges.isBlocked(v4(10, 1, 2, 3)))
        assertTrue(IpRanges.isBlocked(v4(172, 20, 0, 1)))
        assertTrue(IpRanges.isBlocked(v4(192, 168, 0, 1)))
        assertTrue(IpRanges.isBlocked(v4(169, 254, 1, 1)))
        assertTrue(IpRanges.isBlocked(v4(100, 100, 0, 1)))
        assertTrue(IpRanges.isBlocked(v4(0, 1, 2, 3)))
        assertTrue(IpRanges.isBlocked(v4(239, 1, 1, 1)))
    }

    @Test fun allowsPublicIpv4() {
        assertFalse(IpRanges.isBlocked(v4(8, 8, 8, 8)))
        assertFalse(IpRanges.isBlocked(v4(140, 82, 112, 3)))
        assertFalse(IpRanges.isBlocked(v4(172, 15, 0, 1)))
        assertFalse(IpRanges.isBlocked(v4(100, 63, 0, 1)))
    }

    @Test fun blocksSpecialIpv6() {
        assertTrue(IpRanges.isBlocked(v6(0, 0, 0, 0, 0, 0, 0, 1)))
        assertTrue(IpRanges.isBlocked(ByteArray(16)))
        assertTrue(IpRanges.isBlocked(v6(0xfe80, 0, 0, 0, 0, 0, 0, 1)))
        assertTrue(IpRanges.isBlocked(v6(0xfd00, 0, 0, 0, 0, 0, 0, 1)))
        assertTrue(IpRanges.isBlocked(v6(0xff02, 0, 0, 0, 0, 0, 0, 1)))
        // IPv4-mapped private address
        assertTrue(IpRanges.isBlocked(v6(0, 0, 0, 0, 0, 0xffff, 0xc0a8, 0x0001)))
    }

    @Test fun allowsPublicIpv6() {
        assertFalse(IpRanges.isBlocked(v6(0x2606, 0x4700, 0, 0, 0, 0, 0, 0x1111)))
        assertFalse(IpRanges.isBlocked(v6(0, 0, 0, 0, 0, 0xffff, 0x0808, 0x0808)))
    }

    @Test fun malformedAddressIsBlocked() {
        assertTrue(IpRanges.isBlocked(ByteArray(3)))
    }
}
