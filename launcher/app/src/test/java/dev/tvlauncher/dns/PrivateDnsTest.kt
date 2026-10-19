package dev.tvlauncher.dns

import dev.tvlauncher.discover.AppCategory
import dev.tvlauncher.discover.DiscoverActions
import dev.tvlauncher.discover.PlayStoreLinks
import dev.tvlauncher.discover.PopularApps
import dev.tvlauncher.dns.DnsCheck.Outcome
import dev.tvlauncher.dns.PrivateDns.Choice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateDnsTest {
    // Mapping

    @Test fun choices_mapToAndroidModesAndHosts() {
        assertEquals("opportunistic", Choice.AUTOMATIC.mode)
        assertEquals("off", Choice.OFF.mode)
        assertEquals("one.one.one.one", Choice.CLOUDFLARE.host)
        assertEquals("security.cloudflare-dns.com", Choice.CLOUDFLARE_SECURITY.host)
        assertEquals("family.cloudflare-dns.com", Choice.CLOUDFLARE_FAMILY.host)
        assertEquals("dns.adguard-dns.com", Choice.ADGUARD.host)
        assertEquals("dns.google", Choice.GOOGLE.host)
        Choice.values().filter { it.host != null }.forEach {
            assertEquals(it.name, "hostname", it.mode)
            assertTrue(it.name, PrivateDns.isValidHost(it.host))
        }
        assertNull(Choice.AUTOMATIC.host)
        assertNull(Choice.OFF.host)
    }

    @Test fun writes_specifierBeforeMode() {
        assertEquals(listOf("private_dns_specifier" to "one.one.one.one", "private_dns_mode" to "hostname"),
            PrivateDns.writes(Choice.CLOUDFLARE))
        assertEquals(listOf("private_dns_mode" to "opportunistic"), PrivateDns.writes(Choice.AUTOMATIC))
        assertEquals(listOf("private_dns_mode" to "off"), PrivateDns.writes(Choice.OFF))
    }

    @Test fun restore_leavesStrictModeFirst() {
        assertEquals(listOf("private_dns_mode" to "opportunistic", "private_dns_specifier" to null),
            PrivateDns.restore("opportunistic", null))
        assertEquals(listOf("private_dns_mode" to null, "private_dns_specifier" to "x.example"),
            PrivateDns.restore(null, "x.example"))
        assertEquals(listOf("private_dns_specifier" to "dns.google", "private_dns_mode" to "hostname"),
            PrivateDns.restore("hostname", "dns.google"))
    }

    // State parsing

    @Test fun parse_unsetIsAutomatic() {
        assertEquals(Choice.AUTOMATIC, PrivateDns.parse(null, null).choice)
        assertEquals(Choice.AUTOMATIC, PrivateDns.parse("", "one.one.one.one").choice)
        assertEquals(Choice.AUTOMATIC, PrivateDns.parse("weird", null).choice)
    }

    @Test fun parse_usesTheTvsDefaultModeWhenUnset() {
        assertEquals(Choice.OFF, PrivateDns.parse(null, null, defaultMode = "off").choice)
        assertEquals(Choice.AUTOMATIC, PrivateDns.parse("opportunistic", null, defaultMode = "off").choice)
    }

    @Test fun parse_knownHosts() {
        assertEquals(Choice.CLOUDFLARE, PrivateDns.parse("hostname", "one.one.one.one").choice)
        assertEquals(Choice.CLOUDFLARE, PrivateDns.parse("HOSTNAME", "1dot1dot1dot1.cloudflare-dns.com").choice)
        assertEquals(Choice.GOOGLE, PrivateDns.parse("hostname", " DNS.Google. ").choice)
        assertEquals(Choice.CLOUDFLARE_FAMILY, PrivateDns.parse("hostname", "family.cloudflare-dns.com").choice)
        assertEquals(Choice.ADGUARD, PrivateDns.parse("hostname", "dns.adguard.com").choice)
        assertEquals(Choice.OFF, PrivateDns.parse("off", "dns.google").choice)
    }

    @Test fun parse_customAndBrokenHosts() {
        val c = PrivateDns.parse("hostname", "dns.quad9.net")
        assertNull(c.choice)
        assertEquals("Custom (dns.quad9.net)", c.label)
        assertEquals(Choice.AUTOMATIC, PrivateDns.parse("hostname", null).choice)
        assertEquals(Choice.AUTOMATIC, PrivateDns.parse("hostname", "not a host").choice)
    }

    @Test fun host_validation() {
        assertTrue(PrivateDns.isValidHost("dns.google"))
        assertFalse(PrivateDns.isValidHost("dns"))
        assertFalse(PrivateDns.isValidHost("-bad.example"))
        assertFalse(PrivateDns.isValidHost("a b.example"))
        assertFalse(PrivateDns.isValidHost(null))
    }

    @Test fun marker_showsTheCurrentChoice() {
        val cur = PrivateDns.parse("hostname", "dns.google")
        assertEquals("On", PrivateDns.marker(Choice.GOOGLE, cur))
        assertEquals("", PrivateDns.marker(Choice.CLOUDFLARE, cur))
    }

    // Grant missing

    @Test fun grantMissing_showsTheCalmSetupLine_andNoDeveloperText() {
        assertEquals("Finish setup to enable", PrivateDns.chooseSubtitle(canWrite = false))
        assertEquals("Works across all apps", PrivateDns.chooseSubtitle(canWrite = true))
        val texts = listOf(PrivateDns.NEEDS_SETUP, PrivateDns.EXPLAIN) + Choice.values().map { it.note } +
            Outcome.values().map { DnsCheck.message(Choice.CLOUDFLARE, it) } +
            listOf(Warp.subtitle(true, false), Warp.subtitle(false, false), Warp.subtitle(false, true))
        for (t in texts) for (w in listOf("adb", "shell", "pm grant", "write_secure", "permission", "settings put")) {
            assertFalse("$t contains $w", t.lowercase().contains(w))
        }
    }

    // Post-change check

    private fun decide(choice: Choice = Choice.CLOUDFLARE, net: Boolean = true, before: Boolean = true,
                       server: String? = "one.one.one.one", validated: Boolean = true, elapsed: Long = 3_000L) =
        DnsCheck.decide(choice, net, before, server, validated, elapsed)

    @Test fun check_okAsSoonAsValidated() {
        assertEquals(Outcome.OK, decide())
        assertEquals(Outcome.OK, decide(server = "ONE.ONE.ONE.ONE."))
    }

    @Test fun check_waitsThenReverts_onlyIfInternetWorkedBefore() {
        assertEquals(Outcome.WAIT, decide(validated = false))
        assertEquals(Outcome.REVERT, decide(validated = false, elapsed = DnsCheck.WAIT_MS))
        assertEquals(Outcome.UNCHECKED, decide(validated = false, elapsed = DnsCheck.WAIT_MS, before = false))
        assertEquals(Outcome.REVERT, decide(server = null, elapsed = DnsCheck.WAIT_MS))
    }

    @Test fun check_nonStrictChoicesAndNoNetwork() {
        assertEquals(Outcome.UNCHECKED, decide(choice = Choice.AUTOMATIC, validated = false, elapsed = 99_000L))
        assertEquals(Outcome.UNCHECKED, decide(choice = Choice.OFF))
        assertEquals(Outcome.NO_NETWORK, decide(net = false, elapsed = 99_000L))
    }

    @Test fun status_line() {
        val cf = PrivateDns.parse("hostname", "one.one.one.one")
        val auto = PrivateDns.parse(null, null)
        assertEquals("Encrypted via one.one.one.one", DnsCheck.status(cf, true, true, "one.one.one.one", true))
        assertEquals("Can't reach one.one.one.one", DnsCheck.status(cf, true, true, "one.one.one.one", false))
        assertEquals("Encrypted (network's resolver)", DnsCheck.status(auto, true, true, null, true))
        assertEquals("Online, not encrypted", DnsCheck.status(auto, true, false, null, true))
        assertEquals("Not connected", DnsCheck.status(auto, false, false, null, false))
        assertEquals("Online, Private DNS off", DnsCheck.status(PrivateDns.parse("off", null), true, false, null, true))
    }

    // WARP

    @Test fun warp_isInTheVerifiedCatalog_asAPhoneListing() {
        val e = PopularApps.byPackage(Warp.PACKAGE)
        assertNotNull(e)
        assertEquals(Warp.CATALOG_ID, e!!.id)
        assertEquals(AppCategory.TOOLS, e.category)
        assertFalse("Play listing showed Phone/Tablet only", e.tvReady)
        assertTrue(PlayStoreLinks.isValidPackage(Warp.PACKAGE))
        assertEquals(DiscoverActions.Action.OPEN_PLAY_STORE, DiscoverActions.decide(e, emptySet(), getAppsInstalled = true))
        assertEquals(DiscoverActions.Action.OPEN, DiscoverActions.decide(e, setOf(Warp.PACKAGE), getAppsInstalled = false))
    }

    @Test fun warp_rowText() {
        assertEquals("Open", Warp.value(true))
        assertEquals("Get", Warp.value(false))
        assertTrue(Warp.subtitle(false, true).contains("VPN is on"))
    }
}
