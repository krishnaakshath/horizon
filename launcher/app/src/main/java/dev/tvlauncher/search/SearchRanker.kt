package dev.tvlauncher.search

import java.text.Normalizer
import java.util.Locale

/** Result groups of the Everything search, in their tie-break order. */
enum class SearchSection(val title: String) {
    ANSWER("Answer"),
    RECENT("Recent"),
    APPS("Apps"),
    CONTINUE("Continue watching"),
    SETTINGS("Settings"),
    ACTIONS("Quick actions"),
    DISCOVER("Popular apps"),
    WEB("Search")
}

/**
 * Something the Everything search can find. [recency] is 0 for the most recently used item,
 * 1 for the next and so on (-1 = not recent). [keywords] are matched like the subtitle, one
 * tier below the title. [ref] is the caller's own handle (an app, a page id...).
 */
data class SearchItem(
    val section: SearchSection,
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val keywords: List<String> = emptyList(),
    val recency: Int = -1,
    val ref: Any? = null
)

/** How well an item matched: lower [tier] is better. */
data class SearchHit(val item: SearchItem, val tier: Int)

/** Matching rules: case- and diacritics-insensitive, word-aware, with one typo allowed in longer words. */
object SearchMatch {
    const val EXACT = 0
    const val PREFIX = 1
    const val WORD = 2
    const val DETAIL = 3
    const val FUZZY = 4
    /** Words this long (or longer) may have one typo (a wrong, missing, extra or swapped letter). */
    const val TYPO_MIN_LEN = 4

    private val MARKS = Regex("\\p{Mn}+")
    private val SPLIT = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(s: String): String =
        MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

    fun words(normalized: String): List<String> = normalized.split(SPLIT).filter { it.isNotEmpty() }

    /** The best tier for [item] against the normalized query [q] (with its [qWords]), or null for no match. */
    fun tier(q: String, qWords: List<String>, item: SearchItem): Int? {
        if (q.isEmpty() || qWords.isEmpty()) return null
        val title = normalize(item.title)
        if (title == q) return EXACT
        if (title.startsWith(q)) return PREFIX
        val tWords = words(title)
        if (qWords.all { qw -> tWords.any { it.startsWith(qw) } }) return WORD
        val detail = (listOfNotNull(item.subtitle) + item.keywords).flatMap { words(normalize(it)) }
        if (detail.isNotEmpty() && qWords.all { qw -> (tWords + detail).any { it.startsWith(qw) } }) return DETAIL
        if (title.replace(" ", "").contains(q.replace(" ", "")) && q.length >= 3) return FUZZY
        if (qWords.all { qw -> (tWords + detail).any { w -> w.startsWith(qw) || typo(qw, w) } }) return FUZZY
        return null
    }

    /** One edit between [q] and [w] (or [w]'s start), for query words of [TYPO_MIN_LEN]+ letters. */
    fun typo(q: String, w: String): Boolean {
        if (q.length < TYPO_MIN_LEN) return false
        if (osa(q, w) <= 1) return true
        // Still typing: compare with the start of the word, one letter either way.
        for (len in (q.length - 1)..(q.length + 1)) {
            if (len in TYPO_MIN_LEN until w.length && osa(q, w.substring(0, len)) <= 1) return true
        }
        return false
    }

    /** Optimal string alignment distance (insert, delete, substitute, swap neighbours), capped at 2. */
    fun osa(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 1) return 2
        if (a == b) return 0
        val n = a.length
        val m = b.length
        var prev2 = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > 1) return 2
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return minOf(prev[m], 2)
    }
}

/** One group of rows under a heading. */
data class SearchGroup(val section: SearchSection, val hits: List<SearchHit>)

/** What the Everything search shows: groups in display order, never more than [SearchRanker.MAX_ROWS] rows. */
data class SearchResults(val groups: List<SearchGroup>) {
    val rows: Int get() = groups.sumOf { it.hits.size }
    val isEmpty: Boolean get() = groups.isEmpty()
    fun flat(): List<SearchHit> = groups.flatMap { it.hits }
}

/**
 * Ranks and groups Everything search results. Order of hits: tier ([SearchMatch]), then recency
 * (recently used first), then section order, then input order. Groups are shown in the order of
 * their best hit. Every group with a hit gets a row before any group gets a second, within
 * [MAX_ROWS] rows in all (the answer and web rows count) and at most [MAX_GROUPS] headings.
 */
object SearchRanker {
    const val MAX_ROWS = 8
    /** With an empty query only recent things show, in fewer rows (the quick actions are on screen too). */
    const val MAX_ROWS_IDLE = 4
    const val MAX_GROUPS = 4
    private val CAPS = mapOf(SearchSection.APPS to 5, SearchSection.RECENT to 4, SearchSection.WEB to 2, SearchSection.ANSWER to 1)
    private const val DEFAULT_CAP = 3

    fun cap(s: SearchSection) = CAPS[s] ?: DEFAULT_CAP

    /**
     * [items] are everything searchable; [answer] the calculator row (or null); [web] the web
     * rows (shown last, only for a non-empty query). An empty query shows recent items only.
     */
    fun search(query: String, items: List<SearchItem>, answer: SearchItem? = null, web: List<SearchItem> = emptyList()): SearchResults {
        val q = SearchMatch.normalize(query)
        if (q.isEmpty()) return idle(items)
        val qWords = SearchMatch.words(q)
        val hits = items.withIndex().mapNotNull { (i, it) -> SearchMatch.tier(q, qWords, it)?.let { t -> Triple(SearchHit(it, t), i, it) } }
            .sortedWith(compareBy<Triple<SearchHit, Int, SearchItem>>(
                { it.first.tier },
                { if (it.third.recency >= 0) it.third.recency else Int.MAX_VALUE },
                { it.third.section.ordinal },
                { it.second }
            )).map { it.first }
        val fixed = ArrayList<SearchGroup>()
        answer?.let { fixed += SearchGroup(SearchSection.ANSWER, listOf(SearchHit(it, SearchMatch.EXACT))) }
        val webHits = web.take(cap(SearchSection.WEB)).map { SearchHit(it, SearchMatch.FUZZY + 1) }
        val budget = MAX_ROWS - fixed.sumOf { it.hits.size } - webHits.size
        val groupBudget = MAX_GROUPS - fixed.size - (if (webHits.isNotEmpty()) 1 else 0)
        val middle = allocate(hits, budget.coerceAtLeast(0), groupBudget.coerceAtLeast(0))
        val groups = fixed + middle + (if (webHits.isNotEmpty()) listOf(SearchGroup(SearchSection.WEB, webHits)) else emptyList())
        return SearchResults(groups)
    }

    /** Recent apps and continue-watching titles for an empty query, most recent first. */
    private fun idle(items: List<SearchItem>): SearchResults {
        val recent = items.filter { it.recency >= 0 && (it.section == SearchSection.APPS || it.section == SearchSection.RECENT) }
            .sortedBy { it.recency }.map { it.copy(section = SearchSection.RECENT) }
        val cont = items.filter { it.section == SearchSection.CONTINUE && it.recency >= 0 }.sortedBy { it.recency }
        val r = recent.take(if (cont.isEmpty()) MAX_ROWS_IDLE else MAX_ROWS_IDLE / 2).map { SearchHit(it, SearchMatch.EXACT) }
        val c = cont.take(MAX_ROWS_IDLE - r.size).map { SearchHit(it, SearchMatch.EXACT) }
        return SearchResults(listOfNotNull(
            SearchGroup(SearchSection.RECENT, r).takeIf { r.isNotEmpty() },
            SearchGroup(SearchSection.CONTINUE, c).takeIf { c.isNotEmpty() }
        ))
    }

    /** [sorted] hits into at most [groups] groups and [rows] rows: one row per group first, then the best of the rest. */
    fun allocate(sorted: List<SearchHit>, rows: Int, groups: Int): List<SearchGroup> {
        if (rows <= 0 || groups <= 0 || sorted.isEmpty()) return emptyList()
        val order = ArrayList<SearchSection>()
        for (h in sorted) if (h.item.section !in order) order += h.item.section
        val chosen = order.take(minOf(groups, rows)).toSet()
        val taken = LinkedHashMap<SearchSection, MutableList<SearchHit>>()
        chosen.forEach { taken[it] = ArrayList() }
        val used = HashSet<SearchHit>()
        var left = rows
        // Pass 1: each chosen group's best hit.
        for (s in order) {
            if (s !in chosen || left == 0) continue
            val h = sorted.first { it.item.section == s }
            taken.getValue(s) += h; used += h; left--
        }
        // Pass 2: the best remaining hits, within each group's cap.
        for (h in sorted) {
            if (left == 0) break
            val s = h.item.section
            if (s !in chosen || h in used) continue
            val l = taken.getValue(s)
            if (l.size >= cap(s)) continue
            l += h; used += h; left--
        }
        return order.filter { it in chosen }.map { s -> SearchGroup(s, taken.getValue(s).sortedBy { sorted.indexOf(it) }) }
    }
}

/** Heights of the Everything results inside the Spotlight panel (dp), so the panel always fits a 540 dp tall screen. */
object SearchLayout {
    const val ROW_DP = 38
    const val HEADER_DP = 20
    const val SCREEN_DP = 540

    /** Search field, card padding and the gap above the results. */
    const val CHROME_DP = 48 + 2 * 24 + 12
    /** Quick-action buttons, their label and gaps (shown with an empty query only). */
    const val ACTIONS_DP = 52 + 24 + 28
    const val HINT_DP = 32

    fun resultsDp(r: SearchResults): Int = r.rows * ROW_DP + r.groups.size * HEADER_DP

    fun panelDp(r: SearchResults, actions: Boolean, hint: Boolean): Int =
        CHROME_DP + resultsDp(r) + (if (actions) ACTIONS_DP else 0) + (if (hint) HINT_DP else 0)

    /** Whether the panel (bottom margin included) fits the screen. */
    fun fits(r: SearchResults, actions: Boolean, hint: Boolean, bottomMarginDp: Int): Boolean =
        panelDp(r, actions, hint) + bottomMarginDp <= SCREEN_DP
}
