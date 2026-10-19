package dev.tvlauncher.system

import dev.tvlauncher.network.RawScan
import dev.tvlauncher.network.WifiListBuilder
import dev.tvlauncher.network.WifiSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiCredentialsTest {
    private fun ok(r: CredentialResult) = (r as CredentialResult.Ok).credential
    private fun bad(r: CredentialResult) = (r as CredentialResult.Invalid).message

    @Test fun wpa2_quotesSsidAndPassword() {
        val c = ok(WifiCredentials.build("Home", WifiSecurity.WPA2, "hunter22", 30))
        assertEquals("\"Home\"", c.quotedSsid)
        assertEquals(KeyMgmt.WPA_PSK, c.keyMgmt)
        assertEquals("\"hunter22\"", c.psk)
        assertNull(c.wepKey)
    }

    @Test fun wpa_lengthRules_andHexKey() {
        assertEquals("The password needs 8 to 63 characters.", bad(WifiCredentials.build("H", WifiSecurity.WPA2, "short", 30)))
        assertTrue(WifiCredentials.build("H", WifiSecurity.WPA2, "x".repeat(64), 30) is CredentialResult.Invalid)
        val hex = "a1".repeat(32)
        assertEquals(hex, ok(WifiCredentials.build("H", WifiSecurity.WPA2, hex, 30)).psk)
        assertEquals("\"${"y".repeat(63)}\"", ok(WifiCredentials.build("H", WifiSecurity.WPA, "y".repeat(63), 30)).psk)
    }

    @Test fun transitionMode_usesWpaPsk() {
        assertEquals(KeyMgmt.WPA_PSK, ok(WifiCredentials.build("H", WifiSecurity.WPA2_WPA3, "password1", 30)).keyMgmt)
    }

    @Test fun wpa3_onlyFromAndroid10() {
        assertEquals(KeyMgmt.SAE, ok(WifiCredentials.build("H", WifiSecurity.WPA3, "password1", 30)).keyMgmt)
        assertEquals("This TV can't join this kind of network.", bad(WifiCredentials.build("H", WifiSecurity.WPA3, "password1", 28)))
    }

    @Test fun openAndOwe() {
        val o = ok(WifiCredentials.build("Cafe", WifiSecurity.OPEN, "", 30))
        assertEquals(KeyMgmt.NONE, o.keyMgmt); assertNull(o.psk)
        assertEquals(KeyMgmt.OWE, ok(WifiCredentials.build("Cafe", WifiSecurity.OWE, "", 30)).keyMgmt)
    }

    @Test fun wep_asciiQuoted_hexRaw_elseInvalid() {
        assertEquals("\"abcde\"", ok(WifiCredentials.build("W", WifiSecurity.WEP, "abcde", 30)).wepKey)
        assertEquals("0123456789", ok(WifiCredentials.build("W", WifiSecurity.WEP, "0123456789", 30)).wepKey)
        assertEquals(KeyMgmt.WEP, ok(WifiCredentials.build("W", WifiSecurity.WEP, "abcde", 30)).keyMgmt)
        assertTrue(WifiCredentials.build("W", WifiSecurity.WEP, "abc", 30) is CredentialResult.Invalid)
    }

    @Test fun enterprise_blankAndLongNames_refusedWithOneLine() {
        assertEquals("This network needs a sign-in the launcher can't do.", bad(WifiCredentials.build("Corp", WifiSecurity.ENTERPRISE, "x", 30)))
        assertEquals("Type the network name.", bad(WifiCredentials.build("  ", WifiSecurity.OPEN, "", 30)))
        assertEquals("That network name is too long.", bad(WifiCredentials.build("n".repeat(33), WifiSecurity.OPEN, "", 30)))
    }

    @Test fun hiddenFlag_andQuotesStripped() {
        val c = ok(WifiCredentials.build("\"Lab\"", WifiSecurity.OPEN, "", 30, hidden = true))
        assertEquals("Lab", c.ssid); assertEquals("\"Lab\"", c.quotedSsid); assertTrue(c.hidden)
    }

    @Test fun needsPassword() {
        assertFalse(WifiCredentials.needsPassword(WifiSecurity.OPEN))
        assertFalse(WifiCredentials.needsPassword(WifiSecurity.OWE))
        assertTrue(WifiCredentials.needsPassword(WifiSecurity.WPA2))
        assertFalse(WifiCredentials.needsPassword(WifiSecurity.ENTERPRISE))
    }
}

class JoinTrackerTest {
    @Test fun authError_isWrongPassword() {
        val t = JoinTracker()
        t.start("Home", 0)
        assertNull(t.onSupplicantError(0))
        assertEquals(JoinOutcome.WRONG_PASSWORD, t.onSupplicantError(JoinTracker.ERROR_AUTHENTICATING))
        assertEquals("Wrong password", t.outcome!!.message)
        assertFalse(t.active)
    }

    @Test fun twoFailedHandshakes_areWrongPassword() {
        val t = JoinTracker()
        t.start("Home", 0)
        assertNull(t.onSupplicantState("ASSOCIATED"))
        assertNull(t.onSupplicantState("FOUR_WAY_HANDSHAKE"))
        assertNull(t.onSupplicantState("DISCONNECTED"))
        assertNull(t.onSupplicantState("DISCONNECTED")) // no handshake in between: not counted
        assertNull(t.onSupplicantState("FOUR_WAY_HANDSHAKE"))
        assertEquals(JoinOutcome.WRONG_PASSWORD, t.onSupplicantState("DISCONNECTED"))
    }

    @Test fun connectedWhenTheSsidMatches() {
        val t = JoinTracker()
        t.start("\"Home\"", 0)
        assertNull(t.onConnection("Other"))
        assertNull(t.onConnection(null))
        assertEquals(JoinOutcome.CONNECTED, t.onConnection("Home"))
        assertNull(t.outcome!!.message)
    }

    @Test fun timeout_outOfRangeOrFailed() {
        val a = JoinTracker(1000)
        a.start("Far", 0)
        assertNull(a.onTick(999, inRange = false))
        assertEquals(JoinOutcome.OUT_OF_RANGE, a.onTick(1000, inRange = false))
        assertEquals("Out of range", a.outcome!!.message)
        val b = JoinTracker(1000)
        b.start("Near", 0)
        assertEquals(JoinOutcome.FAILED, b.onTick(5000, inRange = true))
    }

    @Test fun refused_andFinishedAttemptsIgnoreLateEvents() {
        val t = JoinTracker()
        t.start("Home", 0)
        assertEquals(JoinOutcome.NOT_ALLOWED, t.refused())
        assertNull(t.onConnection("Home"))
        assertNull(t.onSupplicantError(1))
        t.cancel()
        assertNull(t.target)
    }

    @Test fun everyFailureHasOneFriendlyLine() {
        for (o in JoinOutcome.values()) {
            if (o == JoinOutcome.CONNECTING || o == JoinOutcome.CONNECTED) continue
            val m = o.message!!
            assertFalse(m, m.contains("\n"))
            assertTrue(m, m.length < 60)
        }
    }
}

class WifiListOrderingTest {
    private fun scan(ssid: String?, bssid: String, level: Int) = RawScan(ssid, bssid, level, 2437, "[WPA2-PSK-CCMP]")

    @Test fun connected_thenSaved_thenSignal() {
        val list = WifiListBuilder.build(
            listOf(scan("Strong", "1", -40), scan("SavedWeak", "2", -85), scan("Mine", "3", -70), scan("Mid", "4", -60)),
            connectedBssid = "3", connectedSsid = "Mine", saved = setOf("SavedWeak", "Mine")
        )
        assertEquals(listOf("Mine", "SavedWeak", "Strong", "Mid"), list.map { it.ssid })
        assertTrue(list[0].connected && list[0].saved)
        assertTrue(list[1].saved && !list[1].connected)
        assertFalse(list[2].saved)
    }

    @Test fun hiddenNetworks_neverMarkedSaved() {
        val list = WifiListBuilder.build(listOf(scan("", "h", -40)), null, null, saved = setOf(""))
        assertFalse(list.single().saved)
    }
}
