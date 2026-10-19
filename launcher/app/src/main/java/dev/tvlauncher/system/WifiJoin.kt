package dev.tvlauncher.system

import dev.tvlauncher.network.WifiSecurity

/** WifiConfiguration.KeyMgmt values the launcher uses (plain names so this stays JVM-testable). */
enum class KeyMgmt(val bit: Int) { NONE(0), WPA_PSK(1), WEP(0), SAE(8), OWE(9) }

/**
 * Everything needed to fill a legacy WifiConfiguration: [quotedSsid] for WifiConfiguration.SSID,
 * [psk] for preSharedKey (already quoted, or 64 raw hex digits), [wepKey] for wepKeys[0].
 */
data class WifiCredential(
    val ssid: String,
    val quotedSsid: String,
    val keyMgmt: KeyMgmt,
    val psk: String? = null,
    val wepKey: String? = null,
    val hidden: Boolean = false
)

sealed class CredentialResult {
    data class Ok(val credential: WifiCredential) : CredentialResult()
    /** [message] is one friendly line for the password dialog. */
    data class Invalid(val message: String) : CredentialResult()
}

/** Builds and checks Wi-Fi credentials before anything is handed to the system. Pure. */
object WifiCredentials {
    const val MIN_WPA = 8
    const val MAX_WPA = 63
    private const val HEX_PSK = 64
    /** WPA3 (SAE) and Enhanced Open (OWE) exist as KeyMgmt values from Android 10 (API 29). */
    const val SAE_MIN_SDK = 29

    fun needsPassword(security: WifiSecurity): Boolean = !security.isOpen && security != WifiSecurity.ENTERPRISE

    fun supported(security: WifiSecurity, sdk: Int): Boolean = when (security) {
        WifiSecurity.ENTERPRISE -> false
        WifiSecurity.WPA3, WifiSecurity.OWE -> sdk >= SAE_MIN_SDK
        else -> true
    }

    fun quote(s: String): String = "\"" + s + "\""

    fun isHex(s: String): Boolean = s.isNotEmpty() && s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    fun build(ssid: String, security: WifiSecurity, password: String, sdk: Int, hidden: Boolean = false): CredentialResult {
        val name = ssid.trim('"')
        if (name.isBlank()) return CredentialResult.Invalid("Type the network name.")
        if (name.toByteArray(Charsets.UTF_8).size > 32) return CredentialResult.Invalid("That network name is too long.")
        if (!supported(security, sdk)) return CredentialResult.Invalid(
            if (security == WifiSecurity.ENTERPRISE) "This network needs a sign-in the launcher can't do."
            else "This TV can't join this kind of network."
        )
        val q = quote(name)
        fun ok(k: KeyMgmt, psk: String? = null, wep: String? = null) =
            CredentialResult.Ok(WifiCredential(name, q, k, psk, wep, hidden))
        return when (security) {
            WifiSecurity.OPEN -> ok(KeyMgmt.NONE)
            WifiSecurity.OWE -> ok(KeyMgmt.OWE)
            WifiSecurity.WEP -> wep(password)?.let { ok(KeyMgmt.WEP, wep = it) }
                ?: CredentialResult.Invalid("That password doesn't fit this network.")
            WifiSecurity.WPA3 -> psk(password)?.let { ok(KeyMgmt.SAE, psk = it) } ?: shortPassword()
            WifiSecurity.WPA, WifiSecurity.WPA2, WifiSecurity.WPA2_WPA3 ->
                psk(password)?.let { ok(KeyMgmt.WPA_PSK, psk = it) } ?: shortPassword()
            WifiSecurity.ENTERPRISE -> CredentialResult.Invalid("This network needs a sign-in the launcher can't do.")
        }
    }

    private fun shortPassword() = CredentialResult.Invalid("The password needs $MIN_WPA to $MAX_WPA characters.")

    /** 8..63 printable characters (quoted) or exactly 64 hex digits (raw). */
    fun psk(password: String): String? = when {
        password.length == HEX_PSK && isHex(password) -> password
        password.length in MIN_WPA..MAX_WPA && password.all { it.code in 0x20..0x7e } -> quote(password)
        else -> null
    }

    /** 5/13/16 characters (quoted) or 10/26/32 hex digits (raw). */
    fun wep(password: String): String? = when {
        password.length in setOf(10, 26, 32) && isHex(password) -> password
        password.length in setOf(5, 13, 16) -> quote(password)
        else -> null
    }
}

/** How a join attempt ended, with the single friendly line the panel shows. */
enum class JoinOutcome(val message: String?) {
    CONNECTING(null),
    CONNECTED(null),
    WRONG_PASSWORD("Wrong password"),
    OUT_OF_RANGE("Out of range"),
    NOT_ALLOWED("This TV didn't let the launcher join this network."),
    FAILED("Couldn't connect. Try again.");

    val finished: Boolean get() = this != CONNECTING
}

/**
 * Follows one join attempt from the legacy Wi-Fi broadcasts and decides its [JoinOutcome].
 * Wrong passwords show up either as SupplicantState error 1 (ERROR_AUTHENTICATING) or as a
 * handshake that drops back to DISCONNECTED twice; a network that never answers within
 * [timeoutMs] is "Out of range" when it is missing from the latest scan. Pure: times are passed in.
 */
class JoinTracker(private val timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
    var target: String? = null
        private set
    var outcome: JoinOutcome? = null
        private set
    private var startedAt = 0L
    private var handshakes = 0
    private var inHandshake = false

    val active: Boolean get() = target != null && outcome == JoinOutcome.CONNECTING

    fun start(ssid: String, now: Long) {
        target = ssid.trim('"'); startedAt = now; handshakes = 0; inHandshake = false
        outcome = JoinOutcome.CONNECTING
    }

    /** The system refused the request (addNetwork -1, enableNetwork false). */
    fun refused(): JoinOutcome = finish(JoinOutcome.NOT_ALLOWED)

    fun cancel() { target = null; outcome = null }

    /** WifiManager.EXTRA_SUPPLICANT_ERROR; 1 is ERROR_AUTHENTICATING. */
    fun onSupplicantError(code: Int): JoinOutcome? =
        if (active && code == ERROR_AUTHENTICATING) finish(JoinOutcome.WRONG_PASSWORD) else null

    /** SupplicantState name (FOUR_WAY_HANDSHAKE, GROUP_HANDSHAKE, DISCONNECTED, COMPLETED, ...). */
    fun onSupplicantState(state: String): JoinOutcome? {
        if (!active) return null
        when (state) {
            "FOUR_WAY_HANDSHAKE", "GROUP_HANDSHAKE" -> inHandshake = true
            "DISCONNECTED" -> if (inHandshake) {
                inHandshake = false
                if (++handshakes >= HANDSHAKE_FAILS) return finish(JoinOutcome.WRONG_PASSWORD)
            }
            "COMPLETED" -> inHandshake = false
        }
        return null
    }

    /** The current connection's SSID (null when not connected). */
    fun onConnection(ssid: String?): JoinOutcome? {
        val t = target ?: return null
        if (!active || ssid == null) return null
        return if (ssid.trim('"') == t) finish(JoinOutcome.CONNECTED) else null
    }

    /** Call periodically; [inRange] is whether the latest scan still lists the network. */
    fun onTick(now: Long, inRange: Boolean): JoinOutcome? {
        if (!active || now - startedAt < timeoutMs) return null
        return finish(if (inRange) JoinOutcome.FAILED else JoinOutcome.OUT_OF_RANGE)
    }

    private fun finish(o: JoinOutcome): JoinOutcome { outcome = o; return o }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 25_000L
        const val ERROR_AUTHENTICATING = 1
        const val HANDSHAKE_FAILS = 2
    }
}
