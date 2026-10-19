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
        /** Same current signer set, or a single-signer key rotation whose lineage includes the installed signer. */
        SAME,
        /** Not the same signer (including partial multi-signer overlap): the system would refuse the update. */
        DIFFERENT,
        /** The incoming APK has no readable signature. */
        UNSIGNED,
        /** Installed, but its signer could not be read. */
        INSTALLED_UNKNOWN,
    }

    fun normalise(fp: String?): String? = Sha256Stream.normalise(fp?.replace(":", ""))

    /**
     * [installed] = the installed package's CURRENT signer set (null when not installed);
     * [incoming] = the APK's CURRENT signer set (apkContentsSigners);
     * [incomingHistory] = the APK's proven signing lineage (signingCertificateHistory, includes current).
     *
     * Conservative rule: SAME only when the current signer sets are equal, or — key rotation — when both sides
     * have exactly one current signer and the incoming lineage contains the installed one. A partial overlap of
     * multi-signer sets is DIFFERENT.
     */
    fun verdict(installed: Set<String>?, incoming: Set<String>, incomingHistory: Set<String> = incoming): Verdict {
        val inc = incoming.mapNotNull { normalise(it) }.toSet()
        if (inc.isEmpty()) return Verdict.UNSIGNED
        if (installed == null) return Verdict.NEW_INSTALL
        val ins = installed.mapNotNull { normalise(it) }.toSet()
        if (ins.isEmpty()) return Verdict.INSTALLED_UNKNOWN
        if (ins == inc) return Verdict.SAME
        val lineage = incomingHistory.mapNotNull { normalise(it) }.toSet() + inc
        if (ins.size == 1 && inc.size == 1 && ins.first() in lineage) return Verdict.SAME
        return Verdict.DIFFERENT
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
