package dev.tvlauncher.store.core

import java.io.InputStream
import java.security.MessageDigest

/** Incremental SHA-256 with constant memory, plus helpers to compare against hashes published by a source. */
class Sha256Stream {

    private val md: MessageDigest = MessageDigest.getInstance("SHA-256")

    fun update(buf: ByteArray, off: Int, len: Int) {
        if (len > 0) md.update(buf, off, len)
    }

    /** Finishes the digest; the instance must not be reused afterwards. */
    fun hexDigest(): String = toHex(md.digest())

    companion object {
        private val HEX = "0123456789abcdef".toCharArray()

        fun toHex(bytes: ByteArray): String {
            val out = CharArray(bytes.size * 2)
            for ((i, b) in bytes.withIndex()) {
                val v = b.toInt() and 0xff
                out[i * 2] = HEX[v ushr 4]
                out[i * 2 + 1] = HEX[v and 0x0f]
            }
            return String(out)
        }

        fun hex(input: InputStream, bufferSize: Int = 64 * 1024): String {
            val h = Sha256Stream()
            val buf = ByteArray(bufferSize)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                h.update(buf, 0, n)
            }
            return h.hexDigest()
        }

        fun of(bytes: ByteArray): String = toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

        /** Lower-case 64-char hex from "abc…", "sha256:abc…", tolerant of case/whitespace; null when malformed. */
        fun normalise(hash: String?): String? {
            if (hash == null) return null
            var h = hash.trim().lowercase()
            if (h.contains(':')) {
                if (!h.startsWith("sha256:")) return null
                h = h.removePrefix("sha256:").trim()
            }
            if (h.length != 64 || h.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
            return h
        }

        fun matches(expected: String?, actualHex: String?): Boolean {
            val e = normalise(expected) ?: return false
            val a = normalise(actualHex) ?: return false
            return MessageDigest.isEqual(e.toByteArray(), a.toByteArray())
        }

        /** "ba7816bf…f20015ad" */
        fun short(hex: String): String = if (hex.length <= 16) hex else hex.take(8) + "…" + hex.takeLast(8)
    }
}
