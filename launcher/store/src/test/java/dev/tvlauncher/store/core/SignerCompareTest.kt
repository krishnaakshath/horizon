package dev.tvlauncher.store.core

import dev.tvlauncher.store.core.SignerCompare.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class SignerCompareTest {

    private val a = "aa".repeat(32)
    private val b = "bb".repeat(32)
    private val c = "cc".repeat(32)

    @Test fun notInstalledIsNewInstall() {
        assertEquals(Verdict.NEW_INSTALL, SignerCompare.verdict(null, setOf(a)))
    }

    @Test fun sameSignerMatches() {
        assertEquals(Verdict.SAME, SignerCompare.verdict(setOf(a), setOf(a)))
        assertEquals(Verdict.SAME, SignerCompare.verdict(setOf(a.uppercase()), setOf("AA:".repeat(31) + "AA")))
    }

    @Test fun rotatedKeyWithHistoryMatches() {
        // incoming carries signing history (old a -> new b)
        assertEquals(Verdict.SAME, SignerCompare.verdict(setOf(a), setOf(a, b)))
    }

    @Test fun differentSignerIsDifferent() {
        assertEquals(Verdict.DIFFERENT, SignerCompare.verdict(setOf(a), setOf(b)))
        assertEquals(Verdict.DIFFERENT, SignerCompare.verdict(setOf(a, c), setOf(b)))
    }

    @Test fun unsignedOrUnreadableIncomingIsUnknown() {
        assertEquals(Verdict.UNSIGNED, SignerCompare.verdict(null, emptySet()))
        assertEquals(Verdict.UNSIGNED, SignerCompare.verdict(setOf(a), setOf("not-a-hash")))
    }

    @Test fun installedButUnreadableSignerIsUnknown() {
        assertEquals(Verdict.INSTALLED_UNKNOWN, SignerCompare.verdict(emptySet(), setOf(a)))
    }

    @Test fun expectedSignerFromSource() {
        assertEquals(true, SignerCompare.matchesExpected(a, setOf(b, a)))
        assertEquals(false, SignerCompare.matchesExpected(a, setOf(b)))
        assertEquals(null, SignerCompare.matchesExpected(null, setOf(b)))
        assertEquals(null, SignerCompare.matchesExpected("junk", setOf(b)))
    }

    @Test fun fingerprintFormatting() {
        assertEquals("AA:AA:AA:AA…AA:AA:AA:AA", SignerCompare.shortFingerprint(a))
    }
}
