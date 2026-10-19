package dev.tvlauncher.switcher

import dev.tvlauncher.speed.KeepRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsModelTest {
    private val ctx = KeepRules.Context(ownPackage = "dev.tvlauncher", homeApps = setOf("com.other.home"), inputMethods = setOf("com.kb"))
    private val launchable = setOf("a.netflix", "b.youtube", "c.prime", "dev.tvlauncher", "dev.tvlauncher.browser",
        "com.other.home", "com.android.tv.settings", "com.tcl.tv", "com.kb", "d.kodi")
    private fun ex(p: String) = RecentsFilter.excluded(p, ctx)

    @Test fun ordersByLastUse_newestFirst_andCaps() {
        val usage = mapOf("a.netflix" to 100L, "b.youtube" to 300L, "c.prime" to 200L, "d.kodi" to 50L)
        val r = RecentsOrder.build(usage, emptyList(), launchable, ::ex, max = 3)
        assertEquals(listOf("b.youtube", "c.prime", "a.netflix"), r.map { it.pkg })
    }

    @Test fun filtersLauncherHomeSystemKeyboardsAndNonLaunchable() {
        val usage = mapOf("dev.tvlauncher" to 900L, "dev.tvlauncher.browser" to 800L, "com.other.home" to 700L,
            "com.android.tv.settings" to 600L, "com.kb" to 500L, "x.service" to 400L, "a.netflix" to 10L, "com.tcl.tv" to 20L)
        val r = RecentsOrder.build(usage, emptyList(), launchable, ::ex, max = 8)
        assertEquals(listOf("com.tcl.tv", "a.netflix"), r.map { it.pkg }) // Live TV stays listed
    }

    @Test fun mergesLogWithUsage_newerWins() {
        val usage = mapOf("a.netflix" to 100L)
        val log = listOf(RecentUse("b.youtube", 150L), RecentUse("a.netflix", 50L))
        assertEquals(listOf("b.youtube", "a.netflix"), RecentsOrder.build(usage, log, launchable, ::ex).map { it.pkg })
    }

    @Test fun withoutUsageAccess_usesLog() {
        val log = listOf(RecentUse("c.prime", 5L), RecentUse("a.netflix", 9L))
        assertEquals(listOf("a.netflix", "c.prime"), RecentsOrder.build(null, log, launchable, ::ex).map { it.pkg })
    }

    @Test fun closedAppsHideUntilUsedAgain_andClearedCutoff() {
        val usage = mapOf("a.netflix" to 100L, "b.youtube" to 300L)
        assertEquals(listOf("b.youtube"), RecentsOrder.build(usage, emptyList(), launchable, ::ex, closed = mapOf("a.netflix" to 150L)).map { it.pkg })
        assertEquals(2, RecentsOrder.build(usage, emptyList(), launchable, ::ex, closed = mapOf("a.netflix" to 90L)).size)
        assertEquals(listOf("b.youtube"), RecentsOrder.build(usage, emptyList(), launchable, ::ex, since = 100L).map { it.pkg })
    }

    @Test fun log_recordEncodeDecodeRoundTrip() {
        var log = emptyList<RecentUse>()
        for (i in 1..15) log = RecentsLog.record(log, "p$i", i.toLong())
        log = RecentsLog.record(log, "p3", 99L)
        assertEquals(RecentsLog.MAX, log.size)
        assertEquals("p3", log.first().pkg)
        assertEquals(log, RecentsLog.decode(RecentsLog.encode(log)))
        assertEquals(listOf(RecentUse("a", 5L)), RecentsLog.decode("a=3,,broken,b=x,a=5,=7"))
        assertTrue(RecentsLog.decode(null).isEmpty())
        assertEquals(log, RecentsLog.record(log, "bad,pkg", 1L))
    }

    @Test fun closeGuard_neverOursSystemLiveTvOrPlaying() {
        val ok = CloseGuard.Verdict.Allowed
        assertEquals(ok, CloseGuard.check("a.netflix", ctx, emptySet(), false))
        assertFalse(CloseGuard.check("dev.tvlauncher", ctx, emptySet(), false) == ok)
        assertFalse(CloseGuard.check("com.android.tv.settings", ctx, emptySet(), false) == ok)
        assertFalse(CloseGuard.check("com.tcl.tv", ctx, emptySet(), false) == ok)
        assertFalse(CloseGuard.check("com.other.home", ctx, emptySet(), false) == ok)
        assertFalse(CloseGuard.check("a.netflix", ctx, setOf("a.netflix"), false) == ok)
        assertFalse(CloseGuard.check("a.netflix", ctx, emptySet(), true) == ok)
        assertEquals("Netflix is playing, so it stays open", CloseGuard.message("Netflix", CloseGuard.check("a.netflix", ctx, setOf("a.netflix"), false)))
    }

    @Test fun doubleHome_within600ms() {
        val g = HomePressGesture()
        assertFalse(g.onHome(1000, true))
        assertTrue(g.onHome(1500, true))
        assertFalse(g.onHome(1600, true)) // pair used up
        assertFalse(g.onHome(2300, true)) // too slow
        assertFalse(g.onHome(2400, false)) // switched off
        assertFalse(g.onHome(2500, true))
    }

    @Test fun cardKeys() {
        assertEquals(CardKeys.Action.LAUNCH, CardKeys.onOk(longPress = false, confirming = false))
        assertEquals(CardKeys.Action.ASK_CLOSE, CardKeys.onOk(longPress = true, confirming = false))
        assertEquals(CardKeys.Action.CONFIRM_CLOSE, CardKeys.onOk(longPress = false, confirming = true))
        assertEquals(CardKeys.Action.ASK_CLOSE, CardKeys.onMenu(false))
        assertEquals(CardKeys.Action.CANCEL_CLOSE, CardKeys.onBack(true))
        assertEquals(CardKeys.Action.LEAVE, CardKeys.onBack(false))
    }
}
