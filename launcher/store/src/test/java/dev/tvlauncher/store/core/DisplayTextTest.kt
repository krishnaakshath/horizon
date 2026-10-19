package dev.tvlauncher.store.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayTextTest {

    @Test fun rtlOverrideTrickIsNeutralised() {
        // "Invoice<RLO>gpj.exe" renders as "Invoiceexe.jpg" without sanitising
        assertEquals("Invoicegpj.exe", DisplayText.sanitize("Invoice‮gpj.exe", 80))
        assertEquals("ab", DisplayText.sanitize("a⁦⁧⁨⁩‪‫‬‭b", 80))
    }

    @Test fun zeroWidthAndControlCharactersAreRemoved() {
        assertEquals("VLC", DisplayText.sanitize("V​L‌‍C﻿­", 80))
        assertEquals("ab", DisplayText.sanitize("a\u0000\u0007\u001Bb", 80))
    }

    @Test fun whitespaceCollapsesToSingleSpaces() {
        assertEquals("a b c", DisplayText.sanitize("  a\n\n\tb   \r\nc  ", 80))
    }

    @Test fun capsLengthWithEllipsis() {
        assertEquals("abcd…", DisplayText.sanitize("abcdefghij", 5))
        assertEquals("abcde", DisplayText.sanitize("abcde", 5))
        val huge = "x".repeat(5_000_000)
        val t0 = System.nanoTime()
        val s = DisplayText.sanitize(huge, 80)
        assertEquals(80, s.codePointCount(0, s.length))
        assertTrue(s.endsWith("…"))
        assertTrue("bounded work", System.nanoTime() - t0 < 1_000_000_000L)
    }

    @Test fun nullAndEmpty() {
        assertEquals("", DisplayText.sanitize(null, 80))
        assertEquals("", DisplayText.sanitize("", 80))
        assertEquals("", DisplayText.sanitize("‮​ \n", 80))
    }

    @Test fun keepsOrdinaryUnicode() {
        assertEquals("Télé 電視 😀", DisplayText.sanitize("Télé 電視 😀", 80))
    }
}
