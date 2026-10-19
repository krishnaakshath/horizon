package dev.tvlauncher.audit

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** See-through panels, opacity-based occlusion, scaling, scrollers, windows, stats and the pair cap. */
class AuditRound1Test {
    private fun run(t: Tree, config: AuditConfig = AuditConfig()) = AuditEngine.run(t.nodes, 1280, 720, 1f, config)
    private fun audit(t: Tree, config: AuditConfig = AuditConfig()) = run(t, config).findings
    private fun labels(t: Tree) = t.nodes.filter { it.text?.startsWith("App ") == true }.map { it.id }.toSet()

    // ---- SEE_THROUGH ----

    @Test
    fun spotlightPanel_translucent_overShelfTiles_reportsSeeThrough() {
        val t = HomeFixture.build(panel = HomeFixture.Panel(bg = 0.72f))
        val panel = t.idOf("panel")
        val f = audit(t)
        val st = f.filter { it.kind == Kind.SEE_THROUGH }
        assertTrue(f.toString(), st.isNotEmpty())
        assertTrue(st.all { it.a == panel })
        val underLabels = st.filter { it.b in labels(t) }
        assertTrue(st.toString(), underLabels.size >= 3) // tile labels App 1..3 sit under the panel
        assertTrue(underLabels.all { it.severity == Severity.HIGH })
        assertTrue(underLabels.first().message, underLabels.first().message.contains("alpha=0.72"))
        assertTrue(underLabels.first().message.startsWith("content beneath a translucent container"))
        // The most specific content is named: labels, not the tiles that contain them.
        val tiles = t.nodes.filter { it.cls.endsWith("TileView") }.map { it.id }.toSet()
        assertTrue(st.none { it.b in tiles })
        // Same when the translucency comes from the view alpha instead of the fill.
        val viaAlpha = HomeFixture.build(panel = HomeFixture.Panel(bg = 1f, alpha = 0.72f))
        assertTrue(audit(viaAlpha).any { it.kind == Kind.SEE_THROUGH && it.severity == Severity.HIGH })
    }

    @Test
    fun spotlightPanel_opaque_noFinding() {
        val t = HomeFixture.build(panel = HomeFixture.Panel(bg = 1f))
        assertEquals(emptyList<Finding>(), audit(t))
    }

    @Test
    fun scrim_tagged_notReported() {
        val scrim = HomeFixture.Panel(bg = 0.45f, l = 0, t = 0, r = 1280, b = 720, text = false)
        assertEquals(emptyList<Finding>(), audit(HomeFixture.build(panel = scrim.copy(tag = AuditTokens.SCRIM))))
        // The same scrim untagged is reported (it really does dim everything beneath it).
        assertTrue(audit(HomeFixture.build(panel = scrim)).any { it.kind == Kind.SEE_THROUGH })
    }

    @Test
    fun fullScreenRootContainer_holdingSmallPanel_doesNotSuppress() {
        // A match_parent holder (no fill) holds a small, fill-less panel whose text lands on tile labels.
        val t = HomeFixture.build(panel = HomeFixture.Panel(bg = 0f, textRect = listOf(300, 600, 600, 640)))
        val text = t.idOf("panelText")
        val f = audit(t)
        val o = f.filter { it.kind == Kind.OVERLAP && (it.a == text || it.b == text) }
        assertTrue(f.toString(), o.isNotEmpty())
        assertTrue(o.all { it.severity == Severity.HIGH })
        assertTrue(o.all { setOf(it.a, it.b).any { id -> id in labels(t) } })
    }

    @Test
    fun hiddenOrZeroAlphaPanel_noFinding() {
        assertEquals(emptyList<Finding>(), audit(HomeFixture.build(panel = HomeFixture.Panel(bg = 0.72f, visible = false))))
        assertEquals(emptyList<Finding>(), audit(HomeFixture.build(panel = HomeFixture.Panel(bg = 0.72f, alpha = 0f))))
    }

    @Test
    fun seeThrough_unknownBackground_isNeitherOpaqueNorTranslucent() {
        val t = Tree.screen()
        val label = t.text(0, 100, 100, 300, 140)
        val panel = t.frame(0, 50, 50, 600, 300, bg = 1f).let { id ->
            t.nodes[id] = t.nodes[id].copy(bgKnown = false); id
        }
        t.text(panel, 120, 110, 320, 150)
        val f = audit(t)
        assertTrue(f.none { it.kind == Kind.SEE_THROUGH })
        assertEquals(listOf(Kind.OVERLAP), f.map { it.kind }) // not occluded: opacity unknown
        assertTrue(label >= 0)
    }

    // ---- windows ----

    @Test
    fun popupWindow_translucent_overActivity_reports() {
        fun tree(bg: Float) = Tree.screen().also { t ->
            t.nodes[0] = t.nodes[0].copy(bgAlpha = 1f)
            t.text(0, 100, 100, 400, 140, "Home label")
            val popup = t.add(-1, "android.widget.PopupWindow\$PopupDecorView", 50, 50, 600, 300, bg = bg) // window 2
            t.text(popup, 120, 110, 420, 150, "Popup text")
        }
        val f = audit(tree(0.6f))
        assertTrue(f.toString(), f.any { it.kind == Kind.SEE_THROUGH && it.a == 2 && it.b == 1 && it.severity == Severity.HIGH })
        assertTrue(f.any { it.kind == Kind.OVERLAP && setOf(it.a, it.b) == setOf(1, 3) })
        // An opaque popup window hides what is beneath it.
        assertEquals(emptyList<Finding>(), audit(tree(1f)))
    }

    // ---- scaling ----

    @Test
    fun unscaled_unchanged() {
        assertArrayEquals(intArrayOf(10, 20, 40, 60), TransformMath.rect(10, 20, 30, 40, 1f, 1f, 15f, 20f))
        assertArrayEquals(intArrayOf(10, 20, 40, 60), TransformMath.fromOrigin(10, 20, 30, 40, 1f, 1f))
    }

    @Test
    fun scaledTile_1_1x_rectEnlargedAroundPivot() {
        assertArrayEquals(intArrayOf(90, 95, 310, 205), TransformMath.rect(100, 100, 200, 100, 1.1f, 1.1f, 100f, 50f))
        // Pivot at the top-left corner grows right/down only.
        assertArrayEquals(intArrayOf(100, 100, 320, 210), TransformMath.rect(100, 100, 200, 100, 1.1f, 1.1f, 0f, 0f))
    }

    @Test
    fun nestedScale_composes() {
        val parent = TransformMath.Affine.view(0f, 0f, 2f, 2f, 200f, 200f)
        val child = TransformMath.Affine.view(100f, 100f, 1.1f, 1.1f, 50f, 50f)
        val composed = parent.after(child).mapRect(0f, 0f, 100f, 100f)
        assertArrayEquals(intArrayOf(-10, -10, 210, 210), composed)
        // The snapshotter's shortcut (mapped origin + size × cumulative scale) agrees.
        assertArrayEquals(composed, TransformMath.fromOrigin(-10, -10, 100, 100, 2f * 1.1f, 2f * 1.1f))
    }

    @Test
    fun scaledNeighbors_overlapOnlyWhenScaledRectsIntersect() {
        fun tree(scale: Float) = Tree.screen().also { t ->
            for (x in listOf(100, 316)) {
                val r = TransformMath.rect(x, 300, 200, 140, scale, scale, 100f, 70f)
                t.add(0, "dev.tvlauncher.ui.TileView", r[0], r[1], r[2], r[3], focusable = true)
            }
        }
        assertEquals(emptyList<Finding>(), audit(tree(1f)))
        assertEquals(emptyList<Finding>(), audit(tree(1.05f))) // grows 5 px per side: 6 px gap left
        val f = audit(tree(1.1f)) // grows 10 px per side: 4 px overlap
        assertEquals(f.toString(), listOf(Kind.OVERLAP), f.map { it.kind })
        assertEquals(Severity.HIGH, f.single().severity)
    }

    // ---- scrollers ----

    @Test
    fun scroller_clipChildrenFalse_doesNotClip() {
        val t = Tree.screen()
        val rv = t.add(0, "androidx.recyclerview.widget.RecyclerView", 100, 100, 600, 300, clip = false)
        t.frame(rv, 500, 120, 700, 280, focusable = true)
        assertEquals(emptyList<Finding>(), audit(t))
    }

    @Test
    fun scroller_atScreenSize_rowsBelowFold_notOffscreen() {
        val t = Tree.screen()
        val sv = t.add(0, "android.widget.ScrollView", 0, 0, 1280, 720, clip = true)
        val col = t.add(sv, "android.widget.LinearLayout", 0, 0, 1280, 2000, clip = false)
        t.text(col, 100, 300, 400, 340, "Visible row")
        val below = t.text(col, 100, 900, 400, 940, "Below the fold")
        val partial = t.text(col, 100, 700, 400, 760, "Half row")
        val f = audit(t)
        assertTrue(f.toString(), f.none { it.kind == Kind.OFFSCREEN })
        assertTrue(f.none { it.a == below })
        assertEquals(Severity.LOW, f.single { it.kind == Kind.CLIPPED }.severity)
        assertEquals(partial, f.single { it.kind == Kind.CLIPPED }.a)
    }

    // ---- TEXT_OVERFLOW_VALUE ----

    @Test
    fun textOverflowValue_rowSiblings() {
        val t = Tree.screen()
        val row = t.add(0, "android.widget.LinearLayout", 100, 100, 800, 140)
        val title = t.text(row, 100, 100, 403, 140, "Bluetooth headphones")
        val value = t.text(row, 400, 100, 600, 140, "Connected")
        val f = audit(t)
        assertEquals(f.toString(), 1, f.size)
        assertEquals(Kind.TEXT_OVERFLOW_VALUE, f.single().kind)
        assertEquals(Severity.HIGH, f.single().severity)
        assertEquals(title, f.single().a)
        assertEquals(value, f.single().b)

        val touch = Tree.screen() // 1 px is below the 2 dp threshold
        val r2 = touch.add(0, "android.widget.LinearLayout", 100, 100, 800, 140)
        touch.text(r2, 100, 100, 401, 140); touch.text(r2, 400, 100, 600, 140)
        assertEquals(emptyList<Finding>(), audit(touch))

        val cousins = Tree.screen() // different parents: plain OVERLAP rules (needs 4 dp)
        val p1 = cousins.add(0, "android.widget.LinearLayout", 100, 100, 403, 140, clip = false)
        val p2 = cousins.add(0, "android.widget.LinearLayout", 400, 100, 600, 140, clip = false)
        cousins.text(p1, 100, 100, 403, 140); cousins.text(p2, 400, 100, 600, 140)
        assertEquals(emptyList<Finding>(), audit(cousins))
    }

    // ---- stats / notes ----

    @Test
    fun ignoreInheritance_metaCounts_andWarning() {
        val t = Tree.screen()
        val deco = t.frame(0, 0, 0, 1280, 720, tag = AuditTokens.IGNORE)
        repeat(3) { t.text(deco, 100, 100 + it * 50, 400, 140 + it * 50) }
        val stack = t.frame(0, 100, 400, 600, 600, tag = AuditTokens.LAYER)
        t.text(stack, 110, 410, 300, 450)
        val r = run(t)
        assertEquals(emptyList<Finding>(), r.findings)
        assertEquals(4, r.stats["ignoredNodes"])
        assertEquals(2, r.stats["layerNodes"])
        assertEquals(7, r.stats["visibleNodes"])
        assertTrue(r.notes.toString(), r.notes.any { it.startsWith("WARN:") && it.contains("85%") })
        val json = JSONObject(AuditReport.toJson(r, mapOf("label" to "x")))
        assertEquals(4, json.getJSONObject("summary").getJSONObject("stats").getInt("ignoredNodes"))
        assertTrue(json.getJSONObject("summary").getJSONArray("notes").getString(0).startsWith("WARN:"))

        val calm = HomeFixture.build()
        assertTrue(run(calm).notes.none { it.startsWith("WARN:") })
    }

    @Test
    fun perfGuard_2000StackedNodes_isCappedAndFast() {
        val t = Tree.screen()
        repeat(2000) { t.text(0, 100, 100, 400, 140, "Item $it") }
        val start = System.nanoTime()
        val r = run(t)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 2000)
        assertEquals(200, r.findings.size)
        assertTrue(r.notes.toString(), r.notes.any { it.startsWith("TRUNCATED: overlap pair enumeration") })
        assertTrue(AuditReport.summary(r).contains("showing first 200"))
    }
}
