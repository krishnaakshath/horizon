package dev.tvlauncher.store.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ApkFetchTest {

    @get:Rule val tmp = TemporaryFolder()

    private val url = "https://a.example/app.apk"
    private val body = "abc".toByteArray()
    private val abcSha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    private val any = SafeFetcher.HopRule { _, _ -> true }

    private fun setup(): Pair<FakeConnector, File> {
        val c = FakeConnector()
        c.ok(url, body)
        return c to File(tmp.root, "x.apk")
    }

    @Test fun matchingPublishedHashIsVerified() {
        val (c, f) = setup()
        val r = ApkFetch(SafeFetcher(c)).fetchTo(f, url, any, HashExpectation.of("sha256:$abcSha"), -1)
        assertTrue(r.hashVerified)
        assertEquals(abcSha, r.sha256)
        assertTrue(f.exists())
    }

    @Test fun hashMismatchDeletesFile() {
        val (c, f) = setup()
        try {
            ApkFetch(SafeFetcher(c)).fetchTo(f, url, any, HashExpectation.of("00".repeat(32)), -1)
            fail()
        } catch (_: ApkFetch.HashMismatch) {
            assertFalse(f.exists())
        }
    }

    @Test fun malformedHash_refuses() {
        val (c, f) = setup()
        try {
            ApkFetch(SafeFetcher(c)).fetchTo(f, url, any, HashExpectation.of("sha256:nothex"), -1)
            fail()
        } catch (e: ApkFetch.MalformedHash) {
            assertEquals("The published checksum is malformed", e.message)
            assertTrue("nothing fetched", c.opened.isEmpty())
            assertFalse(f.exists())
        }
    }

    @Test fun blankHash_refuses_whenSourceProvidesHashField() {
        assertTrue(HashExpectation.of("") is HashExpectation.Malformed)
        assertTrue(HashExpectation.of("sha256:") is HashExpectation.Malformed)
        val (c, f) = setup()
        try {
            ApkFetch(SafeFetcher(c)).fetchTo(f, url, any, HashExpectation.of("  "), -1)
            fail()
        } catch (_: ApkFetch.MalformedHash) {
            assertTrue(c.opened.isEmpty())
        }
    }

    @Test fun nullHash_unverifiedBadgeShown_notVerified() {
        val (c, f) = setup()
        val r = ApkFetch(SafeFetcher(c)).fetchTo(f, url, any, HashExpectation.of(null), -1)
        assertFalse(r.hashVerified)
        assertEquals(abcSha, r.sha256)
    }

    @Test fun oversizedDeclaredLengthIsRefusedAndNothingKept() {
        val c = FakeConnector()
        c.routes[url] = FakeConnector.Spec(200, body = body, contentLength = DownloadLimiter.DEFAULT_CAP + 1)
        val f = File(tmp.root, "big.apk")
        try {
            ApkFetch(SafeFetcher(c)).fetchTo(f, url, any, HashExpectation.None, -1)
            fail()
        } catch (_: DownloadLimiter.TooLarge) {
            assertFalse(f.exists())
        }
    }

    @Test fun cancelDeletesPartialFile() {
        val (c, f) = setup()
        try {
            ApkFetch(SafeFetcher(c)).fetchTo(f, url, any, HashExpectation.None, -1, isCancelled = { true })
            fail()
        } catch (_: DownloadLimiter.Cancelled) {
            assertFalse(f.exists())
        }
    }
}
