package dev.tvlauncher.store.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpRangesExtraTest {

    private fun v6(vararg groups: Int): ByteArray {
        val out = ByteArray(16)
        groups.forEachIndexed { i, g -> out[i * 2] = (g shr 8).toByte(); out[i * 2 + 1] = g.toByte() }
        return out
    }

    @Test fun nat64EmbeddingPrivateIpv4IsBlocked() {
        assertTrue(IpRanges.isBlocked(v6(0x64, 0xff9b, 0, 0, 0, 0, 0xc0a8, 0x0101))) // 192.168.1.1
        assertTrue(IpRanges.isBlocked(v6(0x64, 0xff9b, 0, 0, 0, 0, 0x7f00, 0x0001))) // 127.0.0.1
        assertTrue(IpRanges.isBlocked(v6(0x64, 0xff9b, 1, 0, 0, 0, 0x0808, 0x0808))) // local-use 64:ff9b:1::/48
        assertFalse(IpRanges.isBlocked(v6(0x64, 0xff9b, 0, 0, 0, 0, 0x0808, 0x0808))) // 8.8.8.8 via NAT64
    }

    @Test fun sixToFourEmbeddingPrivateIpv4IsBlocked() {
        assertTrue(IpRanges.isBlocked(v6(0x2002, 0x0a00, 0x0001, 0, 0, 0, 0, 1)))   // 10.0.0.1
        assertTrue(IpRanges.isBlocked(v6(0x2002, 0xa9fe, 0xa9fe, 0, 0, 0, 0, 1)))   // 169.254.169.254
        assertFalse(IpRanges.isBlocked(v6(0x2002, 0x0808, 0x0808, 0, 0, 0, 0, 1)))  // 8.8.8.8
    }

    @Test fun teredoIsBlocked() {
        assertTrue(IpRanges.isBlocked(v6(0x2001, 0x0000, 0x4136, 0xe378, 0x8000, 0x63bf, 0x3fff, 0xfdd2)))
        assertFalse(IpRanges.isBlocked(v6(0x2001, 0x4860, 0, 0, 0, 0, 0, 0x8888))) // ordinary 2001:4860::
    }
}
