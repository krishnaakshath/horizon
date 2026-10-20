package dev.tvlauncher.link.sync

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase encryption of a backup: AES-256-GCM with a key from PBKDF2-HMAC-SHA256.
 *
 * File layout (all integers big-endian):
 *   "H02B" | version (1 byte, 1) | iterations (4 bytes) | salt (16) | nonce (12) | ciphertext+tag (16)
 *   (the "H02B" magic is the file-format id; the short-lived M02 builds wrote it too, so their backups restore)
 * The 37-byte header is the GCM associated data, so changing the iteration count or salt breaks
 * the tag. Salt and nonce are fresh random bytes per file. PBKDF2 is computed here on top of
 * HmacSHA256 (SecretKeyFactory's PBKDF2WithHmacSHA256 only exists from Android 8), and is checked
 * against the RFC 7914 test vectors.
 */
object BackupCrypto {
    val MAGIC = byteArrayOf('H'.code.toByte(), '0'.code.toByte(), '2'.code.toByte(), 'B'.code.toByte())
    const val VERSION: Byte = 1
    const val ITERATIONS = 210_000
    /** Files claiming more are refused (a hostile file could otherwise stall the TV). */
    const val MAX_ITERATIONS = 2_000_000
    const val MIN_ITERATIONS = 200_000
    const val SALT = 16
    const val NONCE = 12
    const val TAG_BITS = 128
    const val HEADER = 4 + 1 + 4 + SALT + NONCE
    const val MIN_PASSPHRASE = 8

    class BadPassphrase : Exception("wrong passphrase or damaged file")
    class BadFile(message: String) : Exception(message)

    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, keyLen: Int): ByteArray {
        require(iterations >= 1 && keyLen >= 1)
        val mac = Mac.getInstance("HmacSHA256")
        // An empty password is a legal PBKDF2 input but an illegal HMAC key spec: use a zero byte
        // key of the same effect (HMAC pads keys with zeros).
        mac.init(SecretKeySpec(if (password.isEmpty()) ByteArray(1) else password, "HmacSHA256"))
        val hLen = 32
        val blocks = (keyLen + hLen - 1) / hLen
        val out = ByteArray(keyLen)
        for (i in 1..blocks) {
            mac.update(salt)
            mac.update(ByteBuffer.allocate(4).putInt(i).array())
            var u = mac.doFinal()
            val t = u.copyOf()
            for (j in 2..iterations) {
                u = mac.doFinal(u)
                for (k in t.indices) t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
            }
            val off = (i - 1) * hLen
            System.arraycopy(t, 0, out, off, minOf(hLen, keyLen - off))
        }
        return out
    }

    fun encrypt(plain: ByteArray, passphrase: String, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE).also { random.nextBytes(it) }
        return encryptWith(plain, passphrase, iterations, salt, nonce)
    }

    /** Deterministic core of [encrypt] (fixed salt and nonce, for test vectors). */
    fun encryptWith(plain: ByteArray, passphrase: String, iterations: Int, salt: ByteArray, nonce: ByteArray): ByteArray {
        require(salt.size == SALT && nonce.size == NONCE)
        val header = ByteBuffer.allocate(HEADER).put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(nonce).array()
        val key = pbkdf2(passphrase.toByteArray(Charsets.UTF_8), salt, iterations, 32)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(header)
        return header + c.doFinal(plain)
    }

    fun decrypt(file: ByteArray, passphrase: String): ByteArray {
        if (file.size < HEADER + TAG_BITS / 8) throw BadFile("too short")
        if (!file.copyOfRange(0, 4).contentEquals(MAGIC)) throw BadFile("not a backup")
        if (file[4] != VERSION) throw BadFile("newer format")
        val bb = ByteBuffer.wrap(file, 5, 4)
        val iterations = bb.int
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw BadFile("iterations")
        val salt = file.copyOfRange(9, 9 + SALT)
        val nonce = file.copyOfRange(9 + SALT, HEADER)
        val key = pbkdf2(passphrase.toByteArray(Charsets.UTF_8), salt, iterations, 32)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(file, 0, HEADER)
        return try {
            c.doFinal(file, HEADER, file.size - HEADER)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw BadPassphrase()
        } catch (e: java.security.GeneralSecurityException) {
            throw BadPassphrase()
        }
    }

    fun passphraseOk(p: String?): Boolean = p != null && p.length >= MIN_PASSPHRASE && p.length <= 256
}
