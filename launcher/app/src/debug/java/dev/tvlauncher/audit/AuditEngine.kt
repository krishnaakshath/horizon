package dev.tvlauncher.audit

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure layout rules over a [Node] snapshot. No Android dependencies. Deterministic: the result
 * depends only on the node set (not its order) and the parameters.
 *
 * Geometry notes:
 * - An ancestor with clipChildren=true clips its subtree (also for scrollers: clipChildren=false is
 *   honoured). A NON-scrolling ancestor that covers the whole screen (the window root) is treated as
 *   "the screen": what it cuts is OFFSCREEN, not CLIPPED. A full-screen scroller still clips (rows
 *   scrolled below the fold are silent, not OFFSCREEN).
 * - Overlap, see-through, safe-area, target and text rules use the EFFECTIVE rect (bounds ∩ clipping
 *   ancestors ∩ screen) and skip nodes that are not drawn at all.
 * - Occlusion is decided by OPACITY, never by bounds alone: an overlap is hidden only when a
 *   non-content container in the upper node's chain (below the common ancestor) is opaque
 *   (bgAlpha ≥ opaqueAlpha and effective alpha ≥ opaqueAlpha) and covers the intersection. The same
 *   applies across windows (no common ancestor: the later window root is "upper").
 * - Tags are inherited: `audit:ignore` / `audit:fullbleed` / `audit:dense` / `audit:scrim` on a
 *   container apply to its subtree; `audit:layer` exempts overlaps between any two of its descendants.
 */
object AuditEngine {
    fun audit(nodes: List<Node>, screenW: Int, screenH: Int, density: Float, config: AuditConfig = AuditConfig()): List<Finding> =
        run(nodes, screenW, screenH, density, config).findings

    fun run(nodes: List<Node>, screenW: Int, screenH: Int, density: Float, config: AuditConfig = AuditConfig()): AuditResult {
        val ctx = Ctx(nodes, screenW, screenH, if (density > 0f) density else 1f, config)
        val sorted = ctx.findings().sortedWith(ORDER)
        val stats = ctx.stats()
        val notes = ArrayList(ctx.notes)
        if (sorted.size > config.maxFindings) notes += "TRUNCATED: showing first ${max(0, config.maxFindings)} of ${sorted.size} findings"
        return AuditResult(
            findings = sorted.take(max(0, config.maxFindings)),
            total = sorted.size,
            bySeverity = Severity.values().associateWith { s -> sorted.count { it.severity == s } },
            byKind = Kind.values().associateWith { k -> sorted.count { it.kind == k } },
            notes = notes,
            stats = stats,
        )
    }

    private val ORDER = compareBy<Finding>({ it.severity.ordinal }, { it.kind.ordinal }, { it.a }, { it.b ?: -1 }, { it.message })

    private class R(val l: Int, val t: Int, val r: Int, val b: Int) {
        val empty get() = r <= l || b <= t
        val w get() = r - l
        val h get() = b - t
        val area get() = if (empty) 0L else w.toLong() * h
        fun and(o: R) = R(max(l, o.l), max(t, o.t), min(r, o.r), min(b, o.b))
        fun contains(o: R) = l <= o.l && t <= o.t && r >= o.r && b >= o.b
        fun list() = listOf(l, t, r, b)
        override fun toString() = "[$l,$t,$r,$b]"
    }

    /** Unordered pair key. */
    private fun key(a: Int, b: Int): Long = (min(a, b).toLong() shl 32) or (max(a, b).toLong() and 0xffffffffL)
    /** Ordered pair key. */
    private fun okey(a: Int, b: Int): Long = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)

    private class Ctx(input: List<Node>, w: Int, h: Int, val d: Float, val c: AuditConfig) {
        val nodes = input.sortedBy { it.id }
        val byId = nodes.associateBy { it.id }
        val screen = R(0, 0, w, h)
        val own = nodes.associate { it.id to AuditTokens.parse(it.tag) }
        val notes = ArrayList<String>()

        fun px(dp: Float) = dp * d
        fun rect(n: Node) = R(n.left, n.top, n.right, n.bottom)

        /** Ancestors, nearest first (cycle-safe). */
        val anc: Map<Int, List<Node>> = nodes.associate { n ->
            val out = ArrayList<Node>()
            val seen = HashSet<Int>().apply { add(n.id) }
            var p = byId[n.parent]
            while (p != null && seen.add(p.id)) { out += p; p = byId[p.parent] }
            n.id to out
        }
        val ancIds = anc.mapValues { (_, v) -> v.mapTo(HashSet()) { it.id } }
        /** Own + inherited tokens. */
        val inh: Map<Int, Set<String>> = nodes.associate { n ->
            val s = HashSet(own.getValue(n.id))
            for (a in anc.getValue(n.id)) s += own.getValue(a.id)
            n.id to s
        }

        fun has(n: Node, token: String) = token in inh.getValue(n.id)
        fun isScroll(n: Node) = c.scrollClassHints.any { n.cls.contains(it) }
        fun clips(n: Node) = n.clipChildren
        fun hasText(n: Node) = !n.text.isNullOrBlank()
        /** Text or focusable: the "strong" content whose collisions are HIGH. */
        fun strong(n: Node) = hasText(n) || n.focusable
        fun content(n: Node) = strong(n) || AuditTokens.CONTENT in own.getValue(n.id) ||
            c.contentClassHints.any { n.cls.contains(it) }
        fun shown(n: Node) = n.visible && n.width > 0 && n.height > 0
        fun opaque(n: Node) = n.visible && n.bgKnown && n.bgAlpha >= c.opaqueAlpha && n.alpha >= c.opaqueAlpha
        fun translucent(n: Node) = n.bgKnown && n.bgAlpha >= c.seeThroughMinAlpha && n.bgAlpha < c.opaqueAlpha

        /** A non-scrolling ancestor at least screen-sized acts as the screen itself. */
        fun screenLevel(a: Node) = rect(a).contains(screen) && !isScroll(a)
        /** Clipping ancestors (nearest first), screen-level ones excluded. */
        val clipAnc = nodes.associate { n -> n.id to anc.getValue(n.id).filter { clips(it) && !screenLevel(it) } }
        val eff = nodes.associate { n ->
            var r = rect(n).and(screen)
            for (a in clipAnc.getValue(n.id)) r = r.and(rect(a))
            n.id to r
        }
        fun drawn(n: Node) = shown(n) && !eff.getValue(n.id).empty

        fun findings(): List<Finding> {
            val out = ArrayList<Finding>()
            for (n in nodes) {
                if (!n.visible) continue
                if (has(n, AuditTokens.IGNORE)) continue
                emptyVisible(n, out)
                if (!shown(n)) continue
                clipped(n, out)
                offscreen(n, out)
                if (!drawn(n)) continue
                safeArea(n, out)
                truncated(n, out)
                smallTarget(n, out)
                smallText(n, out)
            }
            overlaps(out)
            seeThrough(out)
            return out
        }

        fun stats(): Map<String, Int> {
            val vis = nodes.filter { it.visible }
            val ignored = vis.count { has(it, AuditTokens.IGNORE) }
            val layer = vis.count { has(it, AuditTokens.LAYER) }
            val exempt = vis.count { has(it, AuditTokens.IGNORE) || has(it, AuditTokens.LAYER) }
            if (vis.isNotEmpty() && exempt > vis.size * c.exemptWarnRatio)
                notes += "WARN: ${exempt * 100 / vis.size}% of visible nodes are exempt via audit:ignore/audit:layer; real overlaps may be hidden"
            return linkedMapOf("nodes" to nodes.size, "visibleNodes" to vis.size, "ignoredNodes" to ignored,
                "layerNodes" to layer, "scrimNodes" to vis.count { has(it, AuditTokens.SCRIM) })
        }

        fun emptyVisible(n: Node, out: MutableList<Finding>) {
            if (n.focusable && (n.width <= 0 || n.height <= 0))
                out += Finding(Kind.EMPTY_VISIBLE, Severity.LOW, n.id, null, "${label(n)} is visible and focusable but has zero size (${n.width}x${n.height})", rect(n).list())
        }

        fun clipped(n: Node, out: MutableList<Finding>) {
            if (!content(n)) return
            val r = rect(n)
            val min = px(c.clipMinDp)
            for (a in clipAnc.getValue(n.id)) {
                val ar = rect(a)
                val stick = maxOf(ar.l - r.l, ar.t - r.t, r.r - ar.r, r.b - ar.b)
                if (stick < min) continue
                val partly = !r.and(ar).empty
                if (isScroll(a)) {
                    if (c.reportScrollClipping && partly)
                        out += Finding(Kind.CLIPPED, Severity.LOW, n.id, a.id,
                            "${label(n)} is cut by scrolling ${label(a)} by ${dp(stick)}dp (peeking item)", r.list())
                } else {
                    val sev = if (strong(n)) Severity.MEDIUM else Severity.LOW
                    val how = if (partly) "sticks out of" else "is completely hidden by"
                    out += Finding(Kind.CLIPPED, sev, n.id, a.id, "${label(n)} $how clipping ${label(a)} by ${dp(stick)}dp", r.list())
                }
                return // report the nearest cutting ancestor only
            }
        }

        fun offscreen(n: Node, out: MutableList<Finding>) {
            if (!content(n) || has(n, AuditTokens.FULLBLEED)) return
            val r = rect(n)
            // Hidden by a clipping ancestor (scrolled out / reported as CLIPPED), or cut by a scroller.
            if (clipAnc.getValue(n.id).any { r.and(rect(it)).empty || (isScroll(it) && !rect(it).contains(r)) }) return
            if (r.and(screen).empty) {
                out += Finding(Kind.OFFSCREEN, Severity.MEDIUM, n.id, null, "${label(n)} is entirely off screen at $r", r.list())
                return
            }
            val stick = maxOf(screen.l - r.l, screen.t - r.t, r.r - screen.r, r.b - screen.b)
            if (stick >= px(c.offscreenMinDp))
                out += Finding(Kind.OFFSCREEN, Severity.LOW, n.id, null, "${label(n)} runs ${dp(stick)}dp off screen", r.list())
        }

        fun safeArea(n: Node, out: MutableList<Finding>) {
            if (!strong(n) || has(n, AuditTokens.FULLBLEED)) return
            val hx = px(c.safeHorizontalDp).roundToInt()
            val vy = px(c.safeVerticalDp).roundToInt()
            val safe = R(hx, vy, screen.r - hx, screen.b - vy)
            val e = eff.getValue(n.id)
            if (safe.contains(e)) return
            val by = maxOf(safe.l - e.l, safe.t - e.t, e.r - safe.r, e.b - safe.b)
            out += Finding(Kind.SAFE_AREA, Severity.MEDIUM, n.id, null,
                "${label(n)} is ${dp(by)}dp outside the overscan-safe area (${c.safeHorizontalDp.fmt()}x${c.safeVerticalDp.fmt()}dp insets)", e.list())
        }

        fun truncated(n: Node, out: MutableList<Finding>) {
            if (!hasText(n)) return
            val over = n.maxLines in 1 until n.lineCount
            if (!n.ellipsized && !over) return
            val why = if (n.ellipsized) "is ellipsized" else "needs ${n.lineCount} lines but maxLines=${n.maxLines}"
            out += Finding(Kind.TEXT_TRUNCATED, Severity.MEDIUM, n.id, null, "${label(n)} $why", eff.getValue(n.id).list())
        }

        fun smallTarget(n: Node, out: MutableList<Finding>) {
            if (!n.focusable) return
            val min = px(c.minTargetDp)
            if (n.width >= min && n.height >= min) return
            out += Finding(Kind.TOO_SMALL_TARGET, Severity.MEDIUM, n.id, null,
                "${label(n)} is ${dp(n.width)}x${dp(n.height)}dp; focus targets need ${c.minTargetDp.fmt()}dp", rect(n).list())
        }

        fun smallText(n: Node, out: MutableList<Finding>) {
            if (!hasText(n) || n.textSizePx <= 0f) return
            val sp = n.textSizePx / d
            val e = eff.getValue(n.id).list()
            if (sp < c.minTextSp - 0.01f) {
                out += Finding(Kind.TOO_SMALL_TEXT, Severity.MEDIUM, n.id, null, "${label(n)} text is ${sp.fmt()}sp (< ${c.minTextSp.fmt()}sp floor)", e)
            } else if (c.reportBelowBodyText && sp < c.bodyTextSp - 0.01f && !has(n, AuditTokens.DENSE)) {
                out += Finding(Kind.TOO_SMALL_TEXT, Severity.LOW, n.id, null, "${label(n)} text is ${sp.fmt()}sp (< ${c.bodyTextSp.fmt()}sp TV body size; tag audit:dense if intended)", e)
            }
        }

        // ---- drawing order / occlusion ----

        fun lca(a: Node, b: Node): Node? = anc.getValue(a.id).firstOrNull { it.id in ancIds.getValue(b.id) }
        fun root(n: Node): Node = anc.getValue(n.id).lastOrNull() ?: n
        /** The child of [lca] on the path to [n] (or the window root when there is no common ancestor). */
        fun branch(n: Node, lca: Node?): Node =
            if (lca == null) root(n) else if (n.parent == lca.id) n else anc.getValue(n.id).first { it.parent == lca.id }

        /** True when [u] is drawn after (above) [l]. */
        fun drawnAfter(u: Node, l: Node): Boolean {
            val x = lca(u, l)
            val bu = branch(u, x); val bl = branch(l, x)
            return bu.z > bl.z || (bu.z == bl.z && bu.id > bl.id)
        }

        fun layered(a: Node, b: Node): Boolean {
            val aa = anc.getValue(a.id)
            val i = aa.indexOfFirst { it.id in ancIds.getValue(b.id) }
            return i >= 0 && (i until aa.size).any { AuditTokens.LAYER in own.getValue(aa[it].id) }
        }

        /** An opaque non-content container in [upper]'s chain below the common ancestor covers [area]. */
        fun occluded(upper: Node, lower: Node, area: R): Boolean {
            val x = lca(upper, lower)
            for (a in anc.getValue(upper.id)) {
                if (a.id == x?.id) break
                if (!content(a) && opaque(a) && rect(a).contains(area)) return true
            }
            return false
        }

        fun candidate(n: Node) = drawn(n) && content(n) && n.alpha >= c.minAlpha && !has(n, AuditTokens.IGNORE)

        fun overlaps(out: MutableList<Finding>) {
            val cands = nodes.filter { candidate(it) }
            val candIds = cands.mapTo(HashSet()) { it.id }
            val min = px(c.overlapMinDp)
            val tov = px(c.textOverflowMinDp)
            class P(val a: Node, val b: Node, val kind: Kind, val x: R)
            val pairs = ArrayList<P>()
            outer@ for (i in cands.indices) for (j in i + 1 until cands.size) {
                val a = cands[i]; val b = cands[j]
                val ea = eff.getValue(a.id); val eb = eff.getValue(b.id)
                val x = ea.and(eb)
                if (x.empty) continue
                if (a.id in ancIds.getValue(b.id) || b.id in ancIds.getValue(a.id)) continue
                val row = hasText(a) && hasText(b) && a.parent == b.parent &&
                    x.h >= c.rowOverlapRatio * min(ea.h, eb.h) && x.w >= tov
                val kind = when {
                    row -> Kind.TEXT_OVERFLOW_VALUE
                    x.w >= min && x.h >= min -> Kind.OVERLAP
                    else -> continue
                }
                if (layered(a, b)) continue
                val upper = if (drawnAfter(a, b)) a else b
                if (occluded(upper, if (upper === a) b else a, x)) continue
                if (pairs.size >= c.maxPairs) {
                    notes += "TRUNCATED: overlap pair enumeration stopped at ${c.maxPairs} pairs"
                    break@outer
                }
                pairs += P(a, b, kind, x)
            }
            // Keep the most specific pairs: drop (A,B) when a pair of their descendants-or-self is also reported.
            // Each pair marks its candidate ancestor pairs as dominated: O(pairs × depth²), no pair-vs-pair scan.
            val dominated = HashSet<Long>()
            for (p in pairs) {
                val xs = listOf(p.a) + anc.getValue(p.a.id).filter { it.id in candIds }
                val ys = listOf(p.b) + anc.getValue(p.b.id).filter { it.id in candIds }
                for (x in xs) for (y in ys) if (x !== p.a || y !== p.b) dominated += key(x.id, y.id)
            }
            for (p in pairs) {
                if (key(p.a.id, p.b.id) in dominated) continue
                val a = p.a; val b = p.b; val x = p.x
                if (p.kind == Kind.TEXT_OVERFLOW_VALUE) {
                    val (l, r) = if (a.left <= b.left) a to b else b to a
                    out += Finding(Kind.TEXT_OVERFLOW_VALUE, Severity.HIGH, l.id, r.id,
                        "${label(l)} runs ${dp(x.w)}dp into ${label(r)} on the same row (value column)", x.list())
                } else {
                    val smaller = min(eff.getValue(a.id).area, eff.getValue(b.id).area).coerceAtLeast(1)
                    val pct = (x.area * 100.0 / smaller).roundToInt()
                    val sev = if (strong(a) && strong(b)) Severity.HIGH else Severity.MEDIUM
                    out += Finding(Kind.OVERLAP, sev, a.id, b.id,
                        "${label(a)} overlaps ${label(b)} by ${dp(x.w)}x${dp(x.h)}dp ($pct% of the smaller)", x.list())
                }
            }
        }

        /** Content drawn UNDER a translucent non-content container (a glass panel) shows through it. */
        fun seeThrough(out: MutableList<Finding>) {
            val uppers = nodes.filter { u ->
                drawn(u) && !content(u) && translucent(u) && u.alpha >= c.minAlpha &&
                    !has(u, AuditTokens.IGNORE) && !has(u, AuditTokens.SCRIM)
            }
            val lowers = nodes.filter { candidate(it) }
            val upperIds = uppers.mapTo(HashSet()) { it.id }
            val lowerIds = lowers.mapTo(HashSet()) { it.id }
            val min = px(c.overlapMinDp)
            val pairs = ArrayList<Pair<Node, Node>>()
            outer@ for (u in uppers) for (l in lowers) {
                val x = eff.getValue(u.id).and(eff.getValue(l.id))
                if (x.w < min || x.h < min) continue
                if (l.id in ancIds.getValue(u.id) || u.id in ancIds.getValue(l.id)) continue
                if (!drawnAfter(u, l) || layered(u, l) || occluded(u, l, x)) continue
                if (pairs.size >= c.maxPairs) {
                    notes += "TRUNCATED: see-through pair enumeration stopped at ${c.maxPairs} pairs"
                    break@outer
                }
                pairs += u to l
            }
            val have = pairs.mapTo(HashSet()) { (u, l) -> okey(u.id, l.id) }
            // (u, l) is dominated when a descendant of l is also under u (report the most specific content).
            val lowerDominated = HashSet<Long>()
            for ((u, l) in pairs) for (a in anc.getValue(l.id)) if (a.id in lowerIds) lowerDominated += okey(u.id, a.id)
            for ((u, l) in pairs) {
                // Report the outermost translucent container only.
                if (anc.getValue(u.id).any { it.id in upperIds && okey(it.id, l.id) in have }) continue
                if (okey(u.id, l.id) in lowerDominated) continue
                val x = eff.getValue(u.id).and(eff.getValue(l.id))
                val sev = if (strong(l)) Severity.HIGH else Severity.MEDIUM
                out += Finding(Kind.SEE_THROUGH, sev, u.id, l.id,
                    "content beneath a translucent container: ${label(u)} alpha=${u.bgAlpha.fmt2()} over ${label(l)}", x.list())
            }
        }

        fun dp(px: Int): String = (px / d).fmt()
        fun label(n: Node): String {
            val short = n.cls.substringAfterLast('.').substringBefore('|')
            val what = when {
                !n.name.isNullOrBlank() -> n.name
                hasText(n) -> "\"" + n.text!!.trim().replace('\n', ' ').take(24) + "\""
                else -> null
            }
            return "#${n.id} $short" + (what?.let { " $it" } ?: "")
        }
    }

    internal fun Float.fmt(): String = if (this == Math.rint(this.toDouble()).toFloat()) toInt().toString() else ((this * 10).roundToInt() / 10.0).toString()
    internal fun Float.fmt2(): String = ((this * 100).roundToInt() / 100.0).toString()
}
