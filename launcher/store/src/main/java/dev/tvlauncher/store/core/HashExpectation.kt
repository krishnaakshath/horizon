package dev.tvlauncher.store.core

/**
 * What a source told us about the file's SHA-256. A source that publishes a hash field that is blank or
 * malformed is NOT treated as "no hash": the download is refused (fail closed).
 */
sealed class HashExpectation {
    /** The source publishes no hash for this file (plain links, GitHub assets without "digest"). */
    object None : HashExpectation()
    data class Sha256(val hex: String) : HashExpectation()
    data class Malformed(val raw: String) : HashExpectation()

    companion object {
        fun of(published: String?): HashExpectation {
            if (published == null) return None
            val n = Sha256Stream.normalise(published) ?: return Malformed(published.take(80))
            return Sha256(n)
        }
    }
}
