package dev.tvlauncher.browser.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionGateTest {
    @Test fun words_areQueried() {
        assertTrue(SuggestionGate.shouldQuery("weather"))
        assertTrue(SuggestionGate.shouldQuery("tv shows tonight"))
        assertTrue(SuggestionGate.shouldQuery("node.js tutorial"))
    }

    @Test fun urlsHostsAndIps_neverLeaveTheDevice() {
        listOf(
            "https://router.local/admin", "http://x", "192.168.1.1", "10.0.0.5:8080", "192.168.1.38:5555",
            "router.lan", "nas.home/share", "example.com", "localhost", "localhost:3000", "[::1]", "fe80::1",
            "2001:db8::1", "intranet.corp", "  my-nas.local  "
        ).forEach { assertFalse(it, SuggestionGate.shouldQuery(it)) }
    }

    @Test fun blank_notQueried() {
        assertFalse(SuggestionGate.shouldQuery(""))
        assertFalse(SuggestionGate.shouldQuery("   "))
    }
}
