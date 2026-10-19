package dev.tvlauncher.store.core

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sha256StreamTest {

    @Test fun knownVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256Stream.hex(ByteArrayInputStream(ByteArray(0))),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256Stream.hex(ByteArrayInputStream("abc".toByteArray())),
        )
    }

    @Test fun incrementalUpdatesMatchOneShot() {
        val acc = Sha256Stream()
        val data = "The quick brown fox jumps over the lazy dog".toByteArray()
        acc.update(data, 0, 10)
        acc.update(data, 10, data.size - 10)
        assertEquals("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592", acc.hexDigest())
    }

    @Test fun constantMemoryOverLargeStream() {
        // 64 MiB generated lazily: the hasher must not buffer the whole stream.
        val size = 64L * 1024 * 1024
        val stream = object : InputStream() {
            var left = size
            override fun read(): Int = if (left-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = minOf(len.toLong(), left).toInt()
                java.util.Arrays.fill(b, off, off + n, 0)
                left -= n
                return n
            }
        }
        val hex = Sha256Stream.hex(stream, bufferSize = 8192)
        assertEquals(64, hex.length)
    }

    @Test fun hashMatchingNormalisesSourceFormats() {
        val h = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertTrue(Sha256Stream.matches(h, h))
        assertTrue(Sha256Stream.matches("sha256:$h", h))
        assertTrue(Sha256Stream.matches(" SHA256:${h.uppercase()} ", h))
        assertFalse(Sha256Stream.matches(h.replace('a', 'b'), h))
        assertFalse(Sha256Stream.matches("sha1:$h", h))
        assertFalse(Sha256Stream.matches("", h))
        assertFalse(Sha256Stream.matches("abc", "abc"))
    }

    @Test fun normaliseRejectsMalformedHashes() {
        assertEquals(null, Sha256Stream.normalise("md5:abc"))
        assertEquals(null, Sha256Stream.normalise("zz".repeat(32)))
        assertEquals("ab".repeat(32), Sha256Stream.normalise("sha256:" + "AB".repeat(32)))
    }

    @Test fun shortForm() {
        val h = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals("ba7816bf…f20015ad", Sha256Stream.short(h))
        assertEquals("abc", Sha256Stream.short("abc"))
    }
}
