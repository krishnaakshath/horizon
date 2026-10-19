package dev.tvlauncher.store.core

/**
 * Compares signing-certificate SHA-256 fingerprints (hex, any case, colons allowed).
 * The system installer remains the final authority; this exists to warn early and to block installs that are
 * certain to fail or that do not match what the source published.
 */
object SignerCompare {

    enum class Verdict {
        /** Package is not installed yet. */
        NEW_INSTALL,
        /** Installed and incoming share a signer (incoming may also carry its rotation history). */
        SAME,
        /** No signer in common: the system will refuse to update over the installed app. */
        DIFFERENT,
        /** The incoming APK has no readable signature. */
        UNSIGNED,
        /** Installed, but its signer could not be read. */
        INSTALLED_UNKNOWN,
    }

    fun normalise(fp: String?): String? = Sha256Stream.normalise(fp?.replace(":", ""))

    /**
     * [installed] = current signer(s) of the installed package or null when not installed;
     * [incoming] = signer(s) of the downloaded APK including its signing history when available.
     */
    fun verdict(installed: Set<String>?, incoming: Set<String>): Verdict {
        val inc = incoming.mapNotNull { normalise(it) }.toSet()
        if (inc.isEmpty()) return Verdict.UNSIGNED
        if (installed == null) return Verdict.NEW_INSTALL
        val ins = installed.mapNotNull { normalise(it) }.toSet()
        if (ins.isEmpty()) return Verdict.INSTALLED_UNKNOWN
        return if (ins.any { it in inc }) Verdict.SAME else Verdict.DIFFERENT
    }

    /** null = source published no (valid) signer; true/false = the APK is / is not signed by it. */
    fun matchesExpected(expected: String?, incoming: Set<String>): Boolean? {
        val e = normalise(expected) ?: return null
        return incoming.mapNotNull { normalise(it) }.any { it == e }
    }

    /** "AA:BB:CC:DD…WW:XX:YY:ZZ" */
    fun shortFingerprint(hex: String): String {
        val h = normalise(hex)?.uppercase() ?: return hex
        val head = h.take(8).chunked(2).joinToString(":")
        val tail = h.takeLast(8).chunked(2).joinToString(":")
        return "$head…$tail"
    }
}
