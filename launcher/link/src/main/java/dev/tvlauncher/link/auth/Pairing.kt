package dev.tvlauncher.link.auth

import java.security.MessageDigest

/**
 * Phone pairing, pure (time and randomness are passed in).
 *
 * - A pairing window is opened on the TV (Settings > System > Phone remote shows the PIN on a
 *   glass card). Outside a window no PIN exists, so pairing needs someone in front of the TV.
 * - The PIN is 6 digits, valid for [WINDOW_MS]. [MAX_FAILURES] wrong tries close the window
 *   (a new PIN must be shown again), so guessing gets at most 5 tries per million codes.
 * - A correct PIN yields a 256-bit random token (the cookie). Only its SHA-256 is kept, at most
 *   [MAX_TOKENS] phones, oldest dropped first. Comparison is constant time.
 */
class Pairing(private val random: (Int) -> ByteArray) {
    data class Token(val hash: String, val createdMs: Long)

    enum class Result { OK, WRONG, NO_WINDOW, LOCKED }

    var pin: String? = null
        private set
    var expiresMs: Long = 0L
        private set
    private var failures = 0
    private val tokens = ArrayList<Token>()

    @Synchronized
    fun openWindow(nowMs: Long): String {
        val b = random(4)
        val n = ((b[0].toLong() and 0xff) shl 24 or ((b[1].toLong() and 0xff) shl 16) or
            ((b[2].toLong() and 0xff) shl 8) or (b[3].toLong() and 0xff)) % 1_000_000
        val p = n.toString().padStart(6, '0')
        pin = p
        expiresMs = nowMs + WINDOW_MS
        failures = 0
        return p
    }

    @Synchronized
    fun closeWindow() { pin = null; expiresMs = 0L; failures = 0 }

    @Synchronized
    fun windowOpen(nowMs: Long): Boolean = pin != null && nowMs < expiresMs

    /** Checks [attempt]; on success returns the new raw token through [onToken]. */
    @Synchronized
    fun pair(attempt: String?, nowMs: Long, onToken: (String) -> Unit): Result {
        val p = pin
        if (p == null || nowMs >= expiresMs) { closeWindow(); return Result.NO_WINDOW }
        val ok = attempt != null && attempt.length == 6 && constantTimeEquals(attempt, p)
        if (!ok) {
            failures++
            if (failures >= MAX_FAILURES) { closeWindow(); return Result.LOCKED }
            return Result.WRONG
        }
        closeWindow()
        val raw = hex(random(32))
        tokens += Token(sha256(raw), nowMs)
        while (tokens.size > MAX_TOKENS) tokens.removeAt(0)
        onToken(raw)
        return Result.OK
    }

    @Synchronized
    fun isPaired(rawToken: String?): Boolean {
        if (rawToken == null || rawToken.length != 64) return false
        val h = sha256(rawToken)
        var found = false
        for (t in tokens) if (constantTimeEquals(t.hash, h)) found = true
        return found
    }

    @Synchronized
    fun forget(rawToken: String?) {
        if (rawToken == null) return
        val h = sha256(rawToken)
        tokens.removeAll { constantTimeEquals(it.hash, h) }
    }

    @Synchronized
    fun forgetAll() = tokens.clear()

    @Synchronized
    fun pairedCount(): Int = tokens.size

    @Synchronized
    fun snapshot(): List<Token> = tokens.toList()

    @Synchronized
    fun restore(saved: List<Token>) {
        tokens.clear()
        tokens += saved.filter { it.hash.length == 64 }.takeLast(MAX_TOKENS)
    }

    companion object {
        const val WINDOW_MS = 10 * 60 * 1000L
        const val MAX_FAILURES = 5
        const val MAX_TOKENS = 8
        const val COOKIE = "h02link"

        fun sha256(s: String): String = hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)))

        fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it.toInt() and 0xff) }

        fun constantTimeEquals(a: String, b: String): Boolean =
            MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

        /** Encodes tokens for private storage: "hash:created" joined by commas. */
        fun encode(tokens: List<Token>): String = tokens.joinToString(",") { "${it.hash}:${it.createdMs}" }

        fun decode(s: String?): List<Token> = s.orEmpty().split(',').mapNotNull { e ->
            val h = e.substringBefore(':')
            val t = e.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
            if (h.length == 64 && h.all { it in '0'..'9' || it in 'a'..'f' }) Token(h, t) else null
        }
    }
}
