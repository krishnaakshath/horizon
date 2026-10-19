package dev.tvlauncher.audit

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure layout rules over a [Node] snapshot. No Android dependencies. Deterministic: the result
 * depends only on the node set (not its order) and the parameters.
 *
 * Geometry notes:
 * - Every ancestor with clipChildren (Android's default) or a scrolling class clips its subtree. An
 *   ancestor that covers the whole screen (the window root) is treated as "the screen" instead: what
 *   it cuts is OFFSCREEN, not CLIPPED.
 * - Overlap, safe-area, target and text rules use the EFFECTIVE rect (bounds ∩ clipping ancestors ∩
 *   screen) and skip nodes that are not drawn at all.
 * - Tags are inherited: `audit:ignore` / `audit:fullbleed` / `audit:dense` on a container apply to
 *   its whole subtree; `audit:layer` on a container exempts overlaps between any two of its descendants.
 */
object AuditEngine {
    fun audit(nodes: List<Node>, screenW: Int, screenH: Int, density: Float, config: AuditConfig = AuditConfig()): List<Finding> =
        run(nodes, screenW, screenH, density, config).findings

    fun run(nodes: List<Node>, screenW: Int, screenH: Int, density: Float, config: AuditConfig = AuditConfig()): AuditResult {
        val all = Ctx(nodes, screenW, screenH, if (density > 0f) density else 1f, config).findings()
        val sorted = all.sortedWith(ORDER)
        return AuditResult(
            findings = sorted.take(max(0, config.maxFindings)),
            total = sorted.size,
            bySeverity = Severity.values().associateWith { s -> sorted.count { it.severity == s } },
            byKind = Kind.values().associateWith { k -> sorted.count { it.kind == k } },
        )
    }

    private val ORDER = compareBy<Finding>({ it.severity.ordinal }, { it.kind.ordinal }, { it.a }, { it.b ?: -1 }, { it.message })

    private class R(val l: Int, val t: Int, val r: Int, val b: Int) {
        val empty get() = r <= l || b <= t
        val area get() = if (empty) 0L else (r - l).toLong() * (b - t)
        fun and(o: R) = R(max(l, o.l), max(t, o.t), min(r, o.r), min(b, o.b))
        fun contains(o: R) = l <= o.l && t <= o.t && r >= o.r && b >= o.b
        fun list() = listOf(l, t, r, b)
        override fun toString() = "[$l,$t,$r,$b]"
    }

    private class Ctx(input: List<Node>, w: Int, h: Int, val d: Float, val c: AuditConfig) {
        val nodes = input.sortedBy { it.id }
        val byId = nodes.associateBy { it.id }
        val screen = R(0, 0, w, h)
        val tokens = nodes.associate { it.id to AuditTokens.parse(it.tag) }

        fun px(dp: Float) = dp * d
        fun rect(n: Node) = R(n.left, n.top, n.right, n.bottom)

        /** Ancestors, nearest first (cycle-safe). */
        fun ancestors(n: Node): List<Node> {
            val out = ArrayList<Node>()
            val seen = HashSet<Int>().apply { add(n.id) }
            var p = byId[n.parent]
            while (p != null && seen.add(p.id)) { out += p; p = byId[p.parent] }
            return out
        }
        val anc = nodes.associate { it.id to ancestors(it) }
        val ancIds = anc.mapValues { (_, v) -> v.map { it.id }.toHashSet() }

        fun has(n: Node, token: String, inherited: Boolean = true) =
            token in tokens[n.id].orEmpty() || (inherited && anc[n.id].orEmpty().any { token in tokens[it.id].orEmpty() })

        fun isScroll(n: Node) = c.scrollClassHints.any { n.cls.contains(it) }
        fun clips(n: Node) = n.clipChildren || isScroll(n)
        fun hasText(n: Node) = !n.text.isNullOrBlank()
        /** Text or focusable: the "strong" content whose collisions are HIGH. */
        fun strong(n: Node) = hasText(n) || n.focusable
        fun content(n: Node) = strong(n) || AuditTokens.CONTENT in tokens[n.id].orEmpty() ||
            c.contentClassHints.any { n.cls.contains(it) }
        fun shown(n: Node) = n.visible && n.width > 0 && n.height > 0

        /** Clipping ancestors that are not screen-sized, nearest first. */
        val clipAnc = nodes.associate { n -> n.id to anc[n.id].orEmpty().filter { clips(it) && !rect(it).contains(screen) } }
        val eff = nodes.associate { n ->
            var r = rect(n).and(screen)
            for (a in clipAnc[n.id].orEmpty()) r = r.and(rect(a))
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
            return out
        }

        fun emptyVisible(n: Node, out: MutableList<Finding>) {
            if (n.focusable && (n.width <= 0 || n.height <= 0))
                out += Finding(Kind.EMPTY_VISIBLE, Severity.LOW, n.id, null, "${label(n)} is visible and focusable but has zero size (${n.width}x${n.height})", rect(n).list())
        }

        fun clipped(n: Node, out: MutableList<Finding>) {
            if (!content(n)) return
            val r = rect(n)
            val min = px(c.clipMinDp)
            for (a in clipAnc[n.id].orEmpty()) {
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
            // Hidden by a (non-screen) clipping ancestor: scrolled out or reported as CLIPPED.
            val r = rect(n)
            if (clipAnc[n.id].orEmpty().any { r.and(rect(it)).empty }) return
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

        fun overlaps(out: MutableList<Finding>) {
            val cands = nodes.filter { n ->
                drawn(n) && content(n) && n.alpha >= c.minAlpha && !has(n, AuditTokens.IGNORE)
            }
            val min = px(c.overlapMinDp)
            val pairs = ArrayList<Pair<Node, Node>>()
            for (i in cands.indices) for (j in i + 1 until cands.size) {
                val a = cands[i]; val b = cands[j]
                if (a.id in ancIds.getValue(b.id) || b.id in ancIds.getValue(a.id)) continue
                val x = eff.getValue(a.id).and(eff.getValue(b.id))
                if (x.r - x.l < min || x.b - x.t < min) continue
                if (exempt(a, b)) continue
                pairs += a to b
            }
            // Keep the most specific pairs: drop (A,B) when a pair of their descendants-or-self is also reported.
            val keep = pairs.filter { (a, b) ->
                pairs.none { (a2, b2) ->
                    (a2.id != a.id || b2.id != b.id) && (
                        (inSub(a2, a) && inSub(b2, b)) || (inSub(a2, b) && inSub(b2, a)))
                }
            }
            for ((a, b) in keep) {
                val x = eff.getValue(a.id).and(eff.getValue(b.id))
                val smaller = min(eff.getValue(a.id).area, eff.getValue(b.id).area).coerceAtLeast(1)
                val pct = (x.area * 100.0 / smaller).roundToInt()
                val sev = if (strong(a) && strong(b)) Severity.HIGH else Severity.MEDIUM
                out += Finding(Kind.OVERLAP, sev, a.id, b.id,
                    "${label(a)} overlaps ${label(b)} by ${dp(x.r - x.l)}x${dp(x.b - x.t)}dp ($pct% of the smaller)", x.list())
            }
        }

        fun inSub(n: Node, root: Node) = n.id == root.id || root.id in ancIds.getValue(n.id)

        fun exempt(a: Node, b: Node): Boolean {
            val aa = anc.getValue(a.id); val bb = ancIds.getValue(b.id)
            val lcaIdx = aa.indexOfFirst { it.id in bb }
            if (lcaIdx < 0) return true // different windows: the upper window simply covers the lower
            val lca = aa[lcaIdx]
            // Same layered stack.
            if (aa.drop(lcaIdx).any { AuditTokens.LAYER in tokens[it.id].orEmpty() }) return true
            if (c.suppressBehindFullScreenOverlays) {
                val ca = branch(a, lca); val cb = branch(b, lca)
                if (ca != null && cb != null) {
                    val upper = if (cb.z > ca.z || (cb.z == ca.z && cb.id > ca.id)) cb else ca
                    val cover = rect(upper).and(screen).area.toDouble() / screen.area.coerceAtLeast(1)
                    if (cover >= c.overlayCoverage) return true
                }
            }
            return false
        }

        /** The child of [lca] on the path to [n] (or [n] itself). */
        fun branch(n: Node, lca: Node): Node? {
            if (n.parent == lca.id) return n
            return anc.getValue(n.id).firstOrNull { it.parent == lca.id }
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

    internal fun Float.fmt(): String = if (this == rint(this)) toInt().toString() else ((this * 10).roundToInt() / 10.0).toString()
    private fun rint(f: Float) = Math.rint(f.toDouble()).toFloat()
}
