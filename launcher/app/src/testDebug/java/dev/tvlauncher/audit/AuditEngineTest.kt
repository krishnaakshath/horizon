package dev.tvlauncher.audit

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hand-built trees: ids are assigned in insertion order, z = sibling index. */
class Tree {
    val nodes = ArrayList<Node>()
    private val childCount = HashMap<Int, Int>()

    fun add(
        parent: Int, cls: String, l: Int, t: Int, r: Int, b: Int,
        text: String? = null, focusable: Boolean = false, tag: String? = null,
        clip: Boolean = true, alpha: Float = 1f, visible: Boolean = true, textPx: Float = 40f,
        maxLines: Int = Int.MAX_VALUE, lineCount: Int = if (text != null) 1 else 0, ellipsized: Boolean = false,
        name: String? = null, bg: Float = 0f, bgKnown: Boolean = true,
    ): Int {
        val id = nodes.size
        val z = childCount.getOrDefault(parent, 0)
        childCount[parent] = z + 1
        nodes += Node(id, parent, cls, name, l, t, r, b, visible, alpha, clip, text, if (text != null) textPx else 0f,
            maxLines, lineCount, ellipsized, focusable, tag, z, bg, bgKnown)
        return id
    }

    fun idOf(name: String) = nodes.single { it.name == name }.id

    fun text(parent: Int, l: Int, t: Int, r: Int, b: Int, s: String = "Label", textPx: Float = 40f, tag: String? = null, alpha: Float = 1f, visible: Boolean = true) =
        add(parent, "android.widget.TextView", l, t, r, b, text = s, textPx = textPx, tag = tag, alpha = alpha, visible = visible)

    fun frame(parent: Int, l: Int, t: Int, r: Int, b: Int, tag: String? = null, clip: Boolean = true, focusable: Boolean = false,
              bg: Float = 0f, alpha: Float = 1f, visible: Boolean = true, name: String? = null) =
        add(parent, "android.widget.FrameLayout", l, t, r, b, tag = tag, clip = clip, focusable = focusable, bg = bg,
            alpha = alpha, visible = visible, name = name)

    companion object {
        /** A tree with a screen-sized window root (id 0). */
        fun screen(w: Int = 1280, h: Int = 720) = Tree().also { it.add(-1, "com.android.internal.policy.DecorView", 0, 0, w, h) }
    }
}

class AuditEngineTest {
    private fun audit(t: Tree, density: Float = 1f, config: AuditConfig = AuditConfig(), w: Int = 1280, h: Int = 720) =
        AuditEngine.audit(t.nodes, w, h, density, config)

    // ---- OVERLAP ----

    @Test
    fun overlap_siblingText_detected() {
        val t = Tree.screen()
        val a = t.text(0, 100, 100, 300, 140, "Wi-Fi")
        val b = t.text(0, 280, 130, 480, 170, "Connected") // offset rows: not the same text row
        val f = audit(t)
        assertEquals(f.toString(), 1, f.size)
        val o = f.single()
        assertEquals(Kind.OVERLAP, o.kind)
        assertEquals(Severity.HIGH, o.severity)
        assertEquals(a, o.a)
        assertEquals(b, o.b)
        assertEquals(listOf(280, 130, 300, 140), o.rect)
        assertTrue(o.message, o.message.contains("3%")) // 20x10 of 200x40
    }

    @Test
    fun overlap_imageAndText_isMedium() {
        val t = Tree.screen()
        t.add(0, "android.widget.ImageView", 100, 100, 200, 200)
        t.text(0, 150, 150, 400, 190)
        val f = audit(t)
        assertEquals(f.toString(), 1, f.size)
        assertEquals(Severity.MEDIUM, f.single().severity)
    }

    @Test
    fun overlap_ignoredWhenAncestor() {
        val t = Tree.screen()
        val tile = t.frame(0, 100, 100, 400, 200, focusable = true)
        t.text(tile, 110, 110, 300, 150)
        t.add(tile, "android.widget.ImageView", 100, 100, 400, 200)
        // The tile's text and image overlap each other: siblings, so that one IS found...
        assertEquals(1, audit(t).size)
        // ...but nothing is reported between the tile and its own children.
        assertTrue(audit(t).none { it.a == tile || it.b == tile })
    }

    @Test
    fun overlap_ignoredWhenLayerTag() {
        val t = Tree.screen()
        val stack = t.frame(0, 100, 100, 600, 300, tag = AuditTokens.LAYER)
        val inner = t.frame(stack, 100, 100, 600, 300)
        t.text(inner, 110, 110, 300, 150)
        t.text(inner, 120, 120, 320, 160)
        assertEquals(emptyList<Finding>(), audit(t))

        val u = Tree.screen()
        u.text(0, 110, 110, 300, 150, tag = AuditTokens.IGNORE)
        u.text(0, 120, 120, 320, 160)
        assertEquals(emptyList<Finding>(), audit(u))
    }

    @Test
    fun overlap_ignoredWhenAlphaLow() {
        val t = Tree.screen()
        t.text(0, 110, 110, 300, 150, alpha = 0.1f)
        t.text(0, 120, 120, 320, 160)
        assertEquals(emptyList<Finding>(), audit(t))

        val u = Tree.screen()
        u.text(0, 110, 110, 300, 150, visible = false)
        u.text(0, 120, 120, 320, 160)
        assertEquals(emptyList<Finding>(), audit(u))
    }

    @Test
    fun overlap_smallTouch_ignoredBelowThreshold() {
        val t = Tree.screen()
        t.text(0, 100, 100, 300, 140)
        t.text(0, 100, 137, 300, 177) // stacked rows touching by 3 px = 3 dp: below 4 dp
        assertEquals(emptyList<Finding>(), audit(t))

        val d2 = Tree.screen(1920, 1080)
        d2.text(0, 200, 200, 600, 280)
        d2.text(0, 200, 274, 600, 354) // 6 px at density 2 = 3 dp
        assertEquals(emptyList<Finding>(), audit(d2, density = 2f, w = 1920, h = 1080))

        val d3 = Tree.screen(1920, 1080)
        d3.text(0, 200, 200, 600, 280)
        d3.text(0, 200, 272, 600, 352) // 8 px at density 2 = 4 dp
        assertEquals(listOf(Kind.OVERLAP), audit(d3, density = 2f, w = 1920, h = 1080).map { it.kind })
    }

    @Test
    fun overlap_underOpaquePanel_suppressed_butNotByBoundsAlone() {
        fun tree(bg: Float) = Tree.screen().also { t ->
            val home = t.frame(0, 0, 0, 1280, 720)
            t.text(home, 100, 100, 300, 140)
            val panel = t.frame(0, 0, 0, 1280, 720, bg = bg) // drawn later, covers the screen
            t.text(panel, 120, 110, 320, 150)
        }
        assertEquals(emptyList<Finding>(), audit(tree(bg = 1f)))
        // A transparent full-screen container hides nothing.
        assertEquals(listOf(Kind.OVERLAP), audit(tree(bg = 0f)).map { it.kind })
    }

    // ---- CLIPPED ----

    @Test
    fun clipped_childBeyondClippingParent() {
        val t = Tree.screen()
        val row = t.add(0, "android.widget.LinearLayout", 100, 100, 400, 200, clip = true)
        val label = t.text(row, 300, 110, 450, 150, "Bluetooth headphones")
        val f = audit(t)
        assertEquals(f.toString(), 1, f.size)
        val c = f.single()
        assertEquals(Kind.CLIPPED, c.kind)
        assertEquals(Severity.MEDIUM, c.severity)
        assertEquals(label, c.a)
        assertEquals(row, c.b)
        assertTrue(c.message, c.message.contains("50"))
    }

    @Test
    fun clipped_notWhenParentDoesntClip() {
        val t = Tree.screen()
        val row = t.add(0, "android.widget.LinearLayout", 100, 100, 400, 200, clip = false)
        t.text(row, 300, 110, 450, 150)
        assertEquals(emptyList<Finding>(), audit(t))
    }

    @Test
    fun clipped_scrollContainer_isLow_andScrolledOutIsSilent() {
        val t = Tree.screen()
        val rv = t.add(0, "androidx.recyclerview.widget.RecyclerView", 100, 100, 600, 300, clip = true)
        t.frame(rv, 500, 120, 700, 280, focusable = true) // peeking
        t.frame(rv, 900, 120, 1100, 280, focusable = true) // fully scrolled out
        val f = audit(t)
        assertEquals(f.toString(), 1, f.size)
        assertEquals(Kind.CLIPPED, f.single().kind)
        assertEquals(Severity.LOW, f.single().severity)
        assertEquals(emptyList<Finding>(), audit(t, config = AuditConfig(reportScrollClipping = false)))
    }

    // ---- OFFSCREEN ----

    @Test
    fun offscreen_partial_and_full() {
        val t = Tree.screen()
        val full = t.text(0, 1300, 100, 1400, 140)
        val part = t.text(0, 1150, 100, 1350, 140)
        val off = audit(t).filter { it.kind == Kind.OFFSCREEN }
        assertEquals(off.toString(), 2, off.size)
        assertEquals(Severity.MEDIUM, off.single { it.a == full }.severity)
        assertEquals(Severity.LOW, off.single { it.a == part }.severity)
        // Fully offscreen text is not also reported for the safe area.
        assertTrue(audit(t).none { it.kind == Kind.SAFE_AREA && it.a == full })
    }

    // ---- SAFE_AREA ----

    @Test
    fun safeArea_violation_and_fullbleedExempt() {
        val t = Tree.screen()
        val bad = t.text(0, 10, 100, 200, 140)
        val f = audit(t)
        assertEquals(f.toString(), listOf(Kind.SAFE_AREA), f.map { it.kind })
        assertEquals(bad, f.single().a)
        assertEquals(Severity.MEDIUM, f.single().severity)

        val u = Tree.screen()
        u.text(0, 10, 100, 200, 140, tag = AuditTokens.FULLBLEED)
        val bleed = u.frame(0, 0, 0, 1280, 100, tag = AuditTokens.FULLBLEED)
        u.text(bleed, 10, 5, 400, 40)
        assertEquals(emptyList<Finding>(), audit(u))

        // Density-scaled: 60 px is inside 48 dp at density 1 but not at density 2 (96 px).
        val d = Tree.screen(1920, 1080)
        d.text(0, 60, 300, 400, 380)
        assertEquals(listOf(Kind.SAFE_AREA), audit(d, density = 2f, w = 1920, h = 1080).map { it.kind })
    }

    // ---- TEXT ----

    @Test
    fun truncated_ellipsized() {
        val t = Tree.screen()
        val e = t.add(0, "android.widget.TextView", 100, 100, 300, 140, text = "A very long app name", ellipsized = true, maxLines = 1)
        val m = t.add(0, "android.widget.TextView", 100, 200, 300, 240, text = "Two lines max", maxLines = 2, lineCount = 3)
        t.add(0, "android.widget.TextView", 100, 300, 300, 340, text = "Fine", maxLines = 1, lineCount = 1)
        val f = audit(t)
        assertEquals(f.toString(), listOf(e, m), f.filter { it.kind == Kind.TEXT_TRUNCATED }.map { it.a })
        assertEquals(2, f.size)
    }

    @Test
    fun tooSmallTarget() {
        val t = Tree.screen()
        val small = t.frame(0, 100, 100, 130, 200, focusable = true)
        t.frame(0, 300, 100, 340, 140, focusable = true) // exactly 40 dp: fine
        val empty = t.frame(0, 500, 100, 500, 100, focusable = true)
        val f = audit(t)
        assertEquals(f.toString(), 2, f.size)
        assertEquals(small, f.single { it.kind == Kind.TOO_SMALL_TARGET }.a)
        assertEquals(empty, f.single { it.kind == Kind.EMPTY_VISIBLE }.a)
    }

    @Test
    fun tooSmallText_densityScaled() {
        val t = Tree.screen(1920, 1080)
        val tiny = t.text(0, 200, 200, 600, 240, textPx = 24f) // 12 sp at density 2
        t.text(0, 200, 300, 600, 360, textPx = 36f) // 18 sp
        val f = audit(t, density = 2f, w = 1920, h = 1080)
        assertEquals(f.toString(), 1, f.size)
        assertEquals(Kind.TOO_SMALL_TEXT, f.single().kind)
        assertEquals(tiny, f.single().a)
        assertEquals(Severity.MEDIUM, f.single().severity)

        val u = Tree.screen()
        u.text(0, 200, 200, 600, 240, textPx = 24f) // 24 sp at density 1
        assertEquals(emptyList<Finding>(), audit(u))

        // Opt-in body-size check: 16 sp is LOW unless tagged dense.
        val v = Tree.screen()
        v.text(0, 200, 200, 600, 240, textPx = 16f)
        v.text(0, 200, 300, 600, 340, textPx = 16f, tag = AuditTokens.DENSE)
        val body = audit(v, config = AuditConfig(reportBelowBodyText = true))
        assertEquals(body.toString(), 1, body.size)
        assertEquals(Severity.LOW, body.single().severity)
    }

    // ---- ordering / cap / report ----

    @Test
    fun stableOrdering() {
        val t = Tree.screen()
        t.text(0, 10, 100, 200, 140) // safe area
        t.text(0, 300, 300, 500, 340); t.text(0, 310, 310, 510, 350) // overlap
        t.frame(0, 700, 300, 720, 320, focusable = true) // small target
        t.add(0, "android.widget.TextView", 700, 500, 900, 540, text = "x", ellipsized = true)
        val first = audit(t)
        val shuffled = t.nodes.shuffled(java.util.Random(42))
        val second = AuditEngine.audit(shuffled, 1280, 720, 1f)
        assertEquals(first, second)
        assertEquals(first, audit(t))
        val sev = first.map { it.severity.ordinal }
        assertEquals(sev.sorted(), sev)
        assertEquals(Severity.HIGH, first.first().severity)
    }

    @Test
    fun capAt200WithSummary() {
        val t = Tree.screen()
        repeat(30) { t.text(0, 100 + it, 100, 400 + it, 140) } // 435 overlapping pairs
        val r = AuditEngine.run(t.nodes, 1280, 720, 1f)
        assertEquals(200, r.findings.size)
        assertEquals(435, r.total)
        assertTrue(r.truncated)
        assertEquals(435, r.bySeverity[Severity.HIGH])
        val s = AuditReport.summary(r)
        assertTrue(s, s.contains("435") && s.contains("200"))
        assertEquals(200, AuditEngine.audit(t.nodes, 1280, 720, 1f).size)
        assertEquals(10, AuditEngine.run(t.nodes, 1280, 720, 1f, AuditConfig(maxFindings = 10)).findings.size)
    }

    @Test
    fun json_roundtrip_shape() {
        val t = Tree.screen()
        t.text(0, 100, 100, 300, 140, "Say \"hi\"\nnow\\")
        t.text(0, 280, 120, 480, 160, "Ünïcødé ✓")
        val f = audit(t)
        val json = AuditReport.toJson(f, linkedMapOf("label" to "home-1", "screenW" to 1280, "density" to 1.5f, "note" to null))
        val o = JSONObject(json)
        assertEquals("home-1", o.getJSONObject("meta").getString("label"))
        assertEquals(1280, o.getJSONObject("meta").getInt("screenW"))
        assertTrue(o.getJSONObject("meta").isNull("note"))
        assertEquals(1, o.getJSONObject("summary").getInt("total"))
        assertEquals(1, o.getJSONObject("summary").getInt("high"))
        val arr = o.getJSONArray("findings")
        assertEquals(1, arr.length())
        val x = arr.getJSONObject(0)
        assertEquals("TEXT_OVERFLOW_VALUE", x.getString("kind")) // same-row text siblings
        assertTrue(o.getJSONObject("summary").has("stats"))
        assertEquals(0, o.getJSONObject("summary").getJSONArray("notes").length())
        assertEquals("HIGH", x.getString("severity"))
        assertEquals(1, x.getInt("a"))
        assertEquals(2, x.getInt("b"))
        assertEquals(4, x.getJSONArray("rect").length())
        assertEquals(f.single().message, x.getString("message"))

        val tree = JSONObject(AuditReport.nodesToJson(t.nodes, mapOf("label" to "t")))
        val nodes = tree.getJSONArray("nodes")
        assertEquals(3, nodes.length())
        assertEquals("Say \"hi\"\nnow\\", nodes.getJSONObject(1).getString("text"))
        assertEquals(-1, nodes.getJSONObject(0).getInt("parent"))
        assertTrue(nodes.getJSONObject(0).isNull("text"))
    }

    // ---- realistic home screen ----

    @Test
    fun homeFixture_legitTree_hasNoFindings() {
        val r = AuditEngine.run(HomeFixture.build(planted = false).nodes, 1280, 720, 1f)
        assertEquals(emptyList<Finding>(), r.findings)
        assertEquals(emptyList<String>(), r.notes)
    }

    @Test
    fun homeFixture_plantedTextCollision_isTheOnlyFinding() {
        val fx = HomeFixture.build(planted = true)
        val f = AuditEngine.audit(fx.nodes, 1280, 720, 1f)
        assertEquals(f.toString(), 1, f.size)
        val o = f.single()
        assertEquals(Kind.TEXT_OVERFLOW_VALUE, o.kind) // date runs into the weather value on the bar row
        assertEquals(Severity.HIGH, o.severity)
        assertEquals(HomeFixture.DATE, o.a)
        assertEquals(fx.nodes.size - 1, o.b)
        assertFalse(AuditReport.summary(f).isBlank())
    }
}

/** 1280x720 @1x: top bar (clock, date, status icons, 2 pills), hero, shelf of 5 tiles, dock. */
object HomeFixture {
    const val DATE = 6

    /**
     * An overlay added over the home column, like Spotlight: a full-screen transparent holder
     * ("holder") with the panel ("panel", optional text "panelText") inside.
     */
    data class Panel(
        val bg: Float, val l: Int = 240, val t: Int = 300, val r: Int = 1040, val b: Int = 690,
        val tag: String? = null, val visible: Boolean = true, val alpha: Float = 1f, val text: Boolean = true,
        val textRect: List<Int> = listOf(280, 320, 600, 360),
    )

    fun build(planted: Boolean = false, panel: Panel? = null): Tree {
        val t = Tree.screen()
        val content = t.frame(0, 0, 0, 1280, 720)
        t.add(content, "dev.tvlauncher.ui.BackdropView", 0, 0, 1280, 720, tag = AuditTokens.FULLBLEED)
        val column = t.add(content, "android.widget.LinearLayout", 0, 0, 1280, 720)
        val bar = t.add(column, "dev.tvlauncher.ui.TopBar", 48, 27, 1232, 91)
        t.text(bar, 48, 35, 168, 83, "21:04", textPx = 32f)
        val date = t.text(bar, 180, 47, 360, 71, "Mon 20 Oct", textPx = 18f)
        check(date == DATE)
        t.add(bar, "android.widget.ImageView", 900, 43, 932, 75, name = "bluetooth")
        t.add(bar, "android.widget.ImageView", 944, 43, 976, 75, name = "wifi")
        for ((x, label) in listOf(996 to "Inputs", 1120 to "Settings")) {
            val pill = t.add(bar, "dev.tvlauncher.ui.TopBar\$PillButton", x, 35, x + 112, 83, focusable = true)
            t.add(pill, "android.widget.ImageView", x + 16, 47, x + 40, 71)
            t.text(pill, x + 48, 47, x + 104, 71, label, textPx = 18f)
        }
        val hero = t.add(column, "dev.tvlauncher.ui.HeroView", 0, 91, 1280, 431, tag = AuditTokens.LAYER)
        t.add(hero, "android.widget.ImageView", 0, 91, 1280, 431)
        t.add(hero, "android.view.View", 0, 91, 1280, 431, name = "scrim", bg = 0.5f) // translucent, but inside the layer
        t.text(hero, 64, 300, 700, 356, "Big Buck Bunny", textPx = 48f)
        t.text(hero, 64, 364, 700, 392, "Movies · 2008", textPx = 20f)
        t.text(column, 64, 447, 400, 475, "Apps", textPx = 20f)
        val rv = t.add(column, "androidx.recyclerview.widget.RecyclerView", 48, 483, 1232, 643, clip = true)
        for (i in 0 until 5) {
            val x = 56 + i * 236
            val tile = t.add(rv, "dev.tvlauncher.ui.TileView", x, 493, x + 220, 633, focusable = true)
            t.add(tile, "android.view.View", x, 493, x + 220, 633, name = "ring")
            t.add(tile, "android.widget.ImageView", x, 493, x + 220, 603)
            t.text(tile, x + 8, 607, x + 212, 631, "App $i", textPx = 18f)
        }
        val dock = t.add(column, "android.widget.LinearLayout", 440, 651, 840, 693)
        for (i in 0 until 3) t.add(dock, "dev.tvlauncher.ui.RoundButton", 440 + i * 56, 652, 480 + i * 56, 692, focusable = true)
        if (planted) t.text(bar, 330, 47, 520, 71, "18° Cloudy", textPx = 18f)
        if (panel != null) {
            val holder = t.frame(content, 0, 0, 1280, 720, name = "holder")
            val p = t.frame(holder, panel.l, panel.t, panel.r, panel.b, tag = panel.tag, bg = panel.bg * panel.alpha,
                alpha = panel.alpha, visible = panel.visible, name = "panel")
            if (panel.text) {
                val (l, tp, r, b) = panel.textRect
                t.add(p, "android.widget.TextView", l, tp, r, b, text = "Search apps", textPx = 24f, alpha = panel.alpha,
                    visible = panel.visible, name = "panelText")
            }
        }
        return t
    }
}

/** `audit:opaque` cards (frosted glass, panel cards) hide what is beneath them even when clickable. */
class AuditOpaqueCardTest {
    private fun audit(t: Tree) = AuditEngine.audit(t.nodes, 1280, 720, 1f)

    private fun panelOverHero(tag: String?, bgKnown: Boolean): List<Finding> {
        val t = Tree.screen()
        t.text(0, 100, 200, 600, 260, "Netflix") // hero text, drawn first
        val scrim = t.frame(0, 0, 0, 1280, 720, tag = AuditTags.SCRIM, bg = 0.55f)
        // A clickable card (focusable on API 26+) with a custom drawable whose opacity is unknown.
        val card = t.add(scrim, "android.widget.LinearLayout", 80, 150, 700, 600, focusable = true, tag = tag, bg = 1f, bgKnown = bgKnown)
        t.text(card, 100, 210, 680, 250, "Connected · Excellent signal")
        return audit(t)
    }

    @Test
    fun untaggedClickableCard_reportsHighOverlaps() {
        assertTrue(panelOverHero(null, bgKnown = false).any { it.severity == Severity.HIGH })
    }

    @Test
    fun taggedCard_yieldsNoHighOverlaps() {
        val f = panelOverHero(AuditTags.OPAQUE, bgKnown = false)
        assertTrue(f.toString(), f.none { it.severity == Severity.HIGH })
        assertTrue(f.none { it.kind == Kind.OVERLAP })
    }

    @Test
    fun taggedCard_fadedOut_doesNotHide() {
        val t = Tree.screen()
        t.text(0, 100, 200, 600, 260, "Netflix")
        val card = t.add(0, "android.widget.LinearLayout", 80, 150, 700, 600, tag = AuditTags.OPAQUE, bg = 1f, alpha = 0.5f)
        t.text(card, 100, 210, 680, 250, "Row")
        assertTrue(audit(t).any { it.kind == Kind.OVERLAP })
    }
}
