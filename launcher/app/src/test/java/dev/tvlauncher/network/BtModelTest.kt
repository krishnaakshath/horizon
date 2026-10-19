package dev.tvlauncher.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BtModelTest {
    private fun raw(addr: String, name: String? = null, major: Int = -1, cls: Int = -1) =
        BtRaw(addr, name, major, cls)

    @Test fun kind_fromClass() {
        fun k(n: String?, cls: Int) = BtKind.classify(n, cls and 0x1F00, cls)
        assertEquals(BtKind.HEADPHONES, k("X", 0x0418))
        assertEquals(BtKind.HEADPHONES, k("X", 0x0404))
        assertEquals(BtKind.SPEAKER, k("X", 0x0414))
        assertEquals(BtKind.GAMEPAD, k("X", 0x0508))
        assertEquals(BtKind.KEYBOARD, k("X", 0x0540))
        assertEquals(BtKind.MOUSE, k("X", 0x0580))
        assertEquals(BtKind.KEYBOARD, k("X", 0x05C0))
        assertEquals(BtKind.PHONE, k("X", 0x0204))
        assertEquals(BtKind.COMPUTER, k("X", 0x010C))
        assertEquals(BtKind.WATCH, k("X", 0x0704))
        assertEquals(BtKind.OTHER, k("X", 0x0900))
        assertEquals(BtKind.OTHER, k("X", -1))
    }

    @Test fun kind_nameHints() {
        assertEquals(BtKind.GAMEPAD, BtKind.classify("DualSense Wireless Controller", -1, -1))
        assertEquals(BtKind.GAMEPAD, BtKind.classify("DUALSHOCK 4", 0x0500, 0x0540))
        assertEquals(BtKind.GAMEPAD, BtKind.classify("Xbox Wireless Controller", -1, -1))
        assertEquals(BtKind.HEADPHONES, BtKind.classify("AirPods Pro", -1, -1))
        assertEquals(BtKind.OTHER, BtKind.classify(null, -1, -1))
    }

    @Test fun connected_listedFirst() {
        val s = BtListBuilder.build(
            bonded = listOf(raw("A1", "Zed"), raw("B2", "Alpha"), raw("C3", "Mid")),
            found = emptyList(),
            connectedAddresses = setOf("C3")
        )
        assertEquals(listOf("Mid"), s.connected.map { it.name })
        assertEquals(listOf("Alpha", "Zed"), s.paired.map { it.name })
        assertTrue(s.connected[0].connected && s.connected[0].bonded)
        assertEquals(listOf("Mid", "Alpha", "Zed"), s.all.map { it.name })
    }

    @Test fun found_excludesBonded() {
        val s = BtListBuilder.build(
            bonded = listOf(raw("A1", "Pad")),
            found = listOf(raw("a1", "Pad"), raw("D4", "New")),
            connectedAddresses = emptySet()
        )
        assertEquals(listOf("New"), s.nearby.map { it.name })
        assertEquals(1, s.paired.size)
    }

    @Test fun unnamedDevice_usesAddress() {
        val s = BtListBuilder.build(emptyList(), listOf(raw("AA:BB", null), raw("CC:DD", "  ")), emptySet())
        assertEquals(listOf("AA:BB", "CC:DD"), s.nearby.map { it.name })
    }

    @Test fun duplicateFoundEvents_deduped() {
        val s = BtListBuilder.build(
            emptyList(),
            listOf(raw("E5", null), raw("E5", "Speaker"), raw("E5", null)),
            emptySet()
        )
        assertEquals(1, s.nearby.size)
        assertEquals("Speaker", s.nearby[0].name)
    }

    @Test fun connectedAddressMatchIsCaseInsensitive() {
        val s = BtListBuilder.build(listOf(raw("aa:bb", "X")), emptyList(), setOf("AA:BB"))
        assertEquals(1, s.connected.size)
    }
}
