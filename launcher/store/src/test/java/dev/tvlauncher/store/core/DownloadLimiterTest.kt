package dev.tvlauncher.store.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DownloadLimiterTest {

    @Test fun defaultCapIs300MiB() {
        assertEquals(300L * 1024 * 1024, DownloadLimiter.DEFAULT_CAP)
    }

    @Test fun declaredLengthCheck() {
        val l = DownloadLimiter(1000)
        assertTrue(l.acceptsDeclared(-1))
        assertTrue(l.acceptsDeclared(0))
        assertTrue(l.acceptsDeclared(1000))
        assertFalse(l.acceptsDeclared(1001))
        assertFalse(l.acceptsDeclared(Long.MAX_VALUE))
    }

    @Test fun countsChunksAndAbortsWhenCapExceeded() {
        val l = DownloadLimiter(10)
        l.onChunk(4)
        l.onChunk(6)
        assertEquals(10, l.total)
        try {
            l.onChunk(1)
            fail("expected TooLarge")
        } catch (e: DownloadLimiter.TooLarge) {
            assertEquals(10, e.cap)
        }
    }

    @Test fun copyStreamsAndHashes() {
        val data = "abc".toByteArray()
        val out = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()
        val r = DownloadLimiter(100).copy(ByteArrayInputStream(data), out, bufferSize = 1, onProgress = { progress += it })
        assertEquals(3, r.bytes)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", r.sha256)
        assertEquals("abc", out.toString())
        assertEquals(listOf(1L, 2L, 3L), progress)
    }

    @Test fun copyAbortsStreamThatLiesAboutItsLength() {
        val endless = object : InputStream() {
            var reads = 0
            override fun read(): Int = 1
            override fun read(b: ByteArray, off: Int, len: Int): Int { reads++; return len }
        }
        val out = ByteArrayOutputStream()
        try {
            DownloadLimiter(64 * 1024).copy(endless, out, bufferSize = 8192)
            fail("expected TooLarge")
        } catch (e: DownloadLimiter.TooLarge) {
            assertTrue(out.size() <= 64 * 1024)
            assertTrue(endless.reads <= 10)
        }
    }

    @Test fun copyStopsWhenCancelled() {
        var calls = 0
        val src = ByteArrayInputStream(ByteArray(10_000))
        try {
            DownloadLimiter(1_000_000).copy(src, ByteArrayOutputStream(), bufferSize = 100, isCancelled = { ++calls > 3 })
            fail("expected Cancelled")
        } catch (_: DownloadLimiter.Cancelled) {
            assertEquals(4, calls)
        }
    }

    @Test fun exactlyAtCapIsAccepted() {
        val r = DownloadLimiter(5).copy(ByteArrayInputStream(ByteArray(5)), ByteArrayOutputStream(), bufferSize = 2)
        assertEquals(5, r.bytes)
    }
}
