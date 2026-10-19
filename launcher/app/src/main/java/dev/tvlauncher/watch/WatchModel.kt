package dev.tvlauncher.watch

import java.util.Locale

/** Where a program row came from in the system TV provider. */
enum class WatchSource { WATCH_NEXT, PREVIEW }

/**
 * One row of the system's Watch Next list (or an app's preview channel), already read out of the
 * provider. Plain values only, so the whole model is testable without Android.
 *
 * [lastEngagementMs] is the provider's last engagement time (UTC ms; 0 when unknown).
 * [positionMs] and [durationMs] are 0 when unknown. [posterUri] is what the app published as
 * poster art (or its thumbnail); the launcher only ever opens content:// and android.resource://
 * URIs (it has no network access), see [ThumbRules].
 */
data class WatchProgram(
    val id: Long,
    val packageName: String,
    val title: String,
    val episodeTitle: String? = null,
    val season: String? = null,
    val episode: String? = null,
    val posterUri: String? = null,
    val intentUri: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val lastEngagementMs: Long = 0,
    val source: WatchSource = WatchSource.WATCH_NEXT,
    /** The app's own id for the title (content id or internal provider id); null when absent. */
    val contentId: String? = null,
    val browsable: Boolean = true,
    /** Preview channels only: the app's weight (higher first). */
    val weight: Int = 0
) {
    /** Stable key for the "hidden entries" list and de-duplication: the same title stays hidden when the app re-publishes it. */
    val key: String get() = WatchModel.keyOf(this)
}

/**
 * Pure rules of the Continue watching row: filtering (hidden apps, hidden entries, rows without
 * a title), de-duplication, grouping by app, ordering by last engagement and the progress maths.
 */
object WatchModel {
    /** Cards per row (a TV with little memory shows [MAX_CARDS_LITE]). */
    const val MAX_CARDS = 8
    const val MAX_CARDS_LITE = 5
    /** Played this far, the title counts as finished: no progress bar, "Watched" label. */
    const val FINISHED = 0.97f

    fun keyOf(p: WatchProgram): String {
        val id = p.contentId?.trim()?.takeIf { it.isNotEmpty() }
        val what = id ?: listOf(p.title, p.season.orEmpty(), p.episode.orEmpty(), p.episodeTitle.orEmpty())
            .joinToString("/") { norm(it) }
        return p.packageName + "|" + what
    }

    private fun norm(s: String) = s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    /**
     * Played fraction in 0..1, or null when it cannot be shown (no duration, nothing played,
     * position past a sane bound). A position slightly past the end counts as 1.
     */
    fun progress(positionMs: Long, durationMs: Long): Float? {
        if (durationMs <= 0 || positionMs <= 0) return null
        if (positionMs > durationMs + durationMs / 10) return null // bogus data: hide the bar
        return (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
    }

    fun finished(p: WatchProgram): Boolean = (progress(p.positionMs, p.durationMs) ?: 0f) >= FINISHED

    /** "23 min left", "1 h 5 min left", "Watched", or null when unknown. */
    fun remainingLabel(p: WatchProgram): String? {
        val f = progress(p.positionMs, p.durationMs) ?: return null
        if (f >= FINISHED) return "Watched"
        val mins = ((p.durationMs - p.positionMs).coerceAtLeast(0) + 59_999) / 60_000
        return when {
            mins <= 0 -> null
            mins < 60 -> "$mins min left"
            mins % 60 == 0L -> "${mins / 60} h left"
            else -> "${mins / 60} h ${mins % 60} min left"
        }
    }

    /** "S2 · E5 · The pilot", "E5", "The pilot" or null. Season and episode numbers that are blank are skipped. */
    fun episodeLine(p: WatchProgram): String? {
        val parts = ArrayList<String>()
        p.season?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += "S$it" }
        p.episode?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += "E$it" }
        p.episodeTitle?.trim()?.takeIf { it.isNotEmpty() && !it.equals(p.title.trim(), ignoreCase = true) }?.let { parts += it }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /**
     * Everything the row may show, best first: browsable rows with a title from apps that are
     * not hidden, minus hidden entries, one card per [WatchProgram.key] (the most recent wins;
     * a Watch Next row beats a preview row of the same title). Watch Next rows come first,
     * ordered by last engagement (newest first); preview rows follow by weight. Ties keep the
     * provider order (stable sort).
     */
    fun clean(
        programs: List<WatchProgram>,
        hiddenPackages: Set<String>,
        blocked: Set<String>,
        includePreview: Boolean = true
    ): List<WatchProgram> {
        val usable = programs.filter {
            it.browsable && it.title.isNotBlank() && it.packageName.isNotBlank() &&
                it.packageName !in hiddenPackages && it.key !in blocked &&
                (includePreview || it.source == WatchSource.WATCH_NEXT)
        }
        val ordered = usable.sortedWith(ORDER)
        val seen = HashSet<String>()
        return ordered.filter { seen.add(it.key) }
    }

    private val ORDER = Comparator<WatchProgram> { a, b ->
        when {
            a.source != b.source -> if (a.source == WatchSource.WATCH_NEXT) -1 else 1
            a.source == WatchSource.WATCH_NEXT -> b.lastEngagementMs.compareTo(a.lastEngagementMs)
            else -> b.weight.compareTo(a.weight)
        }
    }

    /** [clean]ed programs grouped by app, each group in row order and capped at [max]; apps ordered by their newest card. */
    fun byApp(cleaned: List<WatchProgram>, max: Int): Map<String, List<WatchProgram>> {
        val out = LinkedHashMap<String, MutableList<WatchProgram>>()
        for (p in cleaned) {
            val l = out.getOrPut(p.packageName) { ArrayList() }
            if (l.size < max) l += p
        }
        return out
    }

    /** The cards for one focused app (per-app mode) or for every app in one row (all-apps mode). */
    fun rowFor(cleaned: List<WatchProgram>, focusedPackage: String?, allApps: Boolean, max: Int): List<WatchProgram> =
        if (allApps) cleaned.take(max)
        else if (focusedPackage == null) emptyList()
        else cleaned.asSequence().filter { it.packageName == focusedPackage }.take(max).toList()
}

/** Which poster URIs the launcher may open itself (it has no network permission). */
object ThumbRules {
    private val LOCAL = setOf("content", "android.resource")

    fun canOpen(uri: String?): Boolean {
        val u = uri?.trim() ?: return false
        val colon = u.indexOf(':')
        if (colon <= 0) return false
        return u.substring(0, colon).lowercase(Locale.ROOT) in LOCAL && u.length > colon + 3
    }

    /** Thumbnail cache budget in bytes: +3 MB on a full TV, 1 MB on a LITE one. */
    fun cacheBytes(lite: Boolean): Int = if (lite) 1 * 1024 * 1024 else 3 * 1024 * 1024

    /** Decoded thumbnail width in px for a card of [cardPx] (LITE decodes at half size). */
    fun decodeWidth(cardPx: Int, lite: Boolean): Int = if (lite) (cardPx / 2).coerceAtLeast(1) else cardPx

    /** The largest power-of-two sample size that keeps the decoded width >= [targetW]. */
    fun sampleSize(srcW: Int, srcH: Int, targetW: Int): Int {
        if (srcW <= 0 || srcH <= 0 || targetW <= 0) return 1
        var s = 1
        while (srcW / (s * 2) >= targetW) s *= 2
        return s
    }
}

/**
 * Whether a program's intent may be started: it must stay with the app that published the row
 * (its package, or a component of that package). An intent naming another package is refused,
 * so one app cannot use the row to open a different app.
 */
object WatchLaunchRule {
    fun allowed(owner: String, intentPackage: String?, componentPackage: String?): Boolean {
        if (owner.isBlank()) return false
        if (componentPackage != null && componentPackage != owner) return false
        if (intentPackage != null && intentPackage != owner) return false
        return true
    }

    /** Only intent: URIs and plain app links are parsed; anything else falls back to opening the app. */
    fun parseable(intentUri: String?): Boolean {
        val u = intentUri?.trim() ?: return false
        if (u.isEmpty() || u.length > 4096) return false
        val lower = u.lowercase(Locale.ROOT)
        return !(lower.startsWith("javascript:") || lower.startsWith("file:") || lower.startsWith("data:"))
    }
}

/**
 * Focus dwell: a tile must stay focused for [delayMs] before the row for its app appears.
 * [focus] restarts the wait for a new key (the same key keeps its start); [due] says whether
 * the wait is over at [nowMs]; [fire] consumes it so the row is shown once per focus.
 */
class DwellTimer(val delayMs: Long = DEFAULT_MS) {
    var key: String? = null
        private set
    private var since = 0L
    private var fired = false

    fun focus(newKey: String?, nowMs: Long) {
        if (newKey == key && newKey != null) return
        key = newKey
        since = nowMs
        fired = false
    }

    fun clear() {
        key = null
        fired = false
    }

    /** Milliseconds still to wait (0 when due), or -1 when nothing is pending. */
    fun remaining(nowMs: Long): Long {
        if (key == null || fired) return -1
        return (since + delayMs - nowMs).coerceAtLeast(0)
    }

    fun due(nowMs: Long): Boolean = remaining(nowMs) == 0L

    /** The key whose wait is over, once; null otherwise. */
    fun fire(nowMs: Long): String? {
        if (!due(nowMs)) return null
        fired = true
        return key
    }

    companion object {
        const val DEFAULT_MS = 400L
    }
}
