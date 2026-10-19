package dev.tvlauncher.store.core

/** Accepts installer status broadcasts only for the install we started, and only for a bounded time. */
object ReceiverTokenPolicy {
    /** The UI gives up (and abandons the session) after this long… */
    const val SESSION_TIMEOUT_MILLIS = 12L * 60 * 1000
    /** …but a late status is still accepted until this, so a confirm at the last second isn't lost. */
    const val MAX_PENDING_MILLIS = 14L * 60 * 1000

    fun accepts(received: String?, current: String?, issuedAtMillis: Long, nowMillis: Long): Boolean {
        if (received.isNullOrEmpty() || current.isNullOrEmpty()) return false
        if (received != current) return false
        return !isExpired(issuedAtMillis, nowMillis)
    }

    fun isExpired(issuedAtMillis: Long, nowMillis: Long): Boolean =
        nowMillis < issuedAtMillis || nowMillis - issuedAtMillis > MAX_PENDING_MILLIS
}

/**
 * When the legacy ACTION_INSTALL_PACKAGE path may be used: only if creating a PackageInstaller session itself
 * is unsupported or forbidden. Failures while writing or committing a session are real errors, not a reason
 * to try another route.
 */
object InstallFallbackPolicy {
    enum class Stage { CREATE_SESSION, WRITE, COMMIT }

    fun allowed(stage: Stage, error: Throwable): Boolean =
        stage == Stage.CREATE_SESSION && (error is SecurityException || error is UnsupportedOperationException)

    data class Candidate(val packageName: String, val isSystemApp: Boolean)

    /** The handler to target explicitly: the first SYSTEM app; null when there is none (then don't install). */
    fun pickSystemInstaller(candidates: List<Candidate>): String? =
        candidates.firstOrNull { it.isSystemApp && it.packageName.isNotBlank() }?.packageName
}
