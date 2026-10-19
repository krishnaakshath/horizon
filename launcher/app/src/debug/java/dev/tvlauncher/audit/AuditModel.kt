package dev.tvlauncher.audit

/*
 * Pure, dependency-free model for the debug-only layout auditor. Lives in src/debug so a release
 * build contains none of it; it is unit-tested from src/testDebug (run by :app:testDebugUnitTest).
 */

/**
 * One view in a snapshot. Bounds are absolute screen pixels. [visible] and [alpha] are EFFECTIVE
 * values (the view and all its ancestors are VISIBLE / the product of all alphas). [parent] is -1 for
 * a window root. [z] is the drawing order among siblings (for roots: the window order).
 * [tag] holds whitespace/comma separated `audit:*` tokens (see AuditTags), or null.
 * [bgAlpha] is the EFFECTIVE alpha of the view's own background fill (drawable alpha × effective view
 * alpha; 0 = no background). [bgKnown] is false when the drawable's opacity could not be read; such a
 * node is never treated as opaque nor as translucent.
 */
data class Node(
    val id: Int,
    val parent: Int,
    val cls: String,
    val name: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val visible: Boolean,
    val alpha: Float,
    val clipChildren: Boolean,
    val text: String?,
    val textSizePx: Float,
    val maxLines: Int,
    val lineCount: Int,
    val ellipsized: Boolean,
    val focusable: Boolean,
    val tag: String?,
    val z: Int,
    val bgAlpha: Float = 0f,
    val bgKnown: Boolean = true,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Declared in priority order (findings of equal severity sort by this order). */
enum class Kind { OVERLAP, TEXT_OVERFLOW_VALUE, SEE_THROUGH, CLIPPED, OFFSCREEN, SAFE_AREA, TEXT_TRUNCATED, TOO_SMALL_TARGET, TOO_SMALL_TEXT, EMPTY_VISIBLE }

/** Declared most severe first; findings are sorted by this order. */
enum class Severity { HIGH, MEDIUM, LOW }

/** [a]/[b] are node ids; [rect] is `[left, top, right, bottom]` of the problem area in screen px. */
data class Finding(val kind: Kind, val severity: Severity, val a: Int, val b: Int?, val message: String, val rect: List<Int>)

/** The capped finding list plus counts over ALL findings (before the cap). */
data class AuditResult(
    val findings: List<Finding>,
    val total: Int,
    val bySeverity: Map<Severity, Int>,
    val byKind: Map<Kind, Int>,
    /** Warnings about the audit itself (e.g. "TRUNCATED: ...", too many exempt nodes). */
    val notes: List<String> = emptyList(),
    /** Node counts: nodes, visibleNodes, ignoredNodes, layerNodes, scrimNodes. */
    val stats: Map<String, Int> = emptyMap(),
) {
    val truncated: Boolean get() = total > findings.size
}

/** All thresholds in dp/sp, scaled by the snapshot's density. */
data class AuditConfig(
    /** OVERLAP: intersection must be at least this deep in BOTH axes. */
    val overlapMinDp: Float = 4f,
    /** OVERLAP: nodes whose effective alpha is below this are ignored. */
    val minAlpha: Float = 0.2f,
    /** CLIPPED: a child must stick out of its clipping ancestor by at least this much. */
    val clipMinDp: Float = 2f,
    /** OFFSCREEN (partial): a content node must stick out of the screen by at least this much. */
    val offscreenMinDp: Float = 2f,
    /** SAFE_AREA: overscan-safe insets. */
    val safeHorizontalDp: Float = 48f,
    val safeVerticalDp: Float = 27f,
    /** TOO_SMALL_TARGET: focusable width or height below this. */
    val minTargetDp: Float = 40f,
    /** TOO_SMALL_TEXT: below this is MEDIUM (the hard floor, also for `audit:dense` labels). */
    val minTextSp: Float = 14f,
    /** TOO_SMALL_TEXT: TV body/caption recommendation; below it is LOW when [reportBelowBodyText]. */
    val bodyTextSp: Float = 18f,
    val reportBelowBodyText: Boolean = false,
    /**
     * A non-content container whose effective background alpha is at least this is OPAQUE: overlaps it
     * fully covers (drawn above) are hidden and not reported. Never decided by bounds alone.
     */
    val opaqueAlpha: Float = 0.95f,
    /** SEE_THROUGH: a container fill at least this visible (and below [opaqueAlpha]) is "translucent". */
    val seeThroughMinAlpha: Float = 0.05f,
    /** TEXT_OVERFLOW_VALUE: same-row text siblings crossing by at least this much. */
    val textOverflowMinDp: Float = 2f,
    /** TEXT_OVERFLOW_VALUE: "same row" = vertical intersection ≥ this fraction of the smaller height. */
    val rowOverlapRatio: Float = 0.5f,
    /** Overlap/see-through pair enumeration stops after this many raw pairs (a "TRUNCATED" note is added). */
    val maxPairs: Int = 2000,
    /** Warn when more than this fraction of visible nodes is exempt via audit:ignore / audit:layer. */
    val exemptWarnRatio: Float = 0.3f,
    /** Report children cut by SCROLLING containers (peeking shelf tiles); always LOW. */
    val reportScrollClipping: Boolean = true,
    /** Class-name fragments that mark a scrolling container. */
    val scrollClassHints: List<String> = listOf("ScrollView", "RecyclerView", "ListView", "GridView", "ViewPager"),
    /** Class-name fragments that mark an image/button as "content" even without text or focus. */
    val contentClassHints: List<String> = listOf("ImageView", "ImageButton", "Button"),
    /** GLASS cards: what still shows through (1 - coverage) x the scrims between must stay below this. */
    val glassLeakMax: Float = 0.15f,
    /** Maximum findings returned (the rest are only counted). */
    val maxFindings: Int = 200,
)

/** Tag tokens understood by the engine (the constants live in src/main: [AuditTags]). */
object AuditTokens {
    const val LAYER = AuditTags.LAYER
    const val IGNORE = AuditTags.IGNORE
    const val FULLBLEED = AuditTags.FULLBLEED
    const val DENSE = AuditTags.DENSE
    const val CONTENT = AuditTags.CONTENT
    const val SCRIM = AuditTags.SCRIM
    const val OPAQUE = AuditTags.OPAQUE
    const val PEEK = AuditTags.PEEK
    const val GLASS = AuditTags.GLASS

    fun parse(tag: String?): Set<String> =
        if (tag.isNullOrBlank()) emptySet()
        else tag.split(' ', ',', '\t', '\n').filter { it.startsWith("audit:") }.toSet()
}
