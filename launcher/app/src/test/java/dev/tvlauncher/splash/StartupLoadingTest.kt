package dev.tvlauncher.splash

import dev.tvlauncher.appsettings.AppearanceSection
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowGuard
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.launch.DotsMath
import dev.tvlauncher.launch.LaunchGeometry
import dev.tvlauncher.launch.LaunchMotion
import dev.tvlauncher.launch.LaunchStyles
import dev.tvlauncher.launch.LaunchWatch
import dev.tvlauncher.launch.PulseRingMath
import dev.tvlauncher.settings.LauncherSettings
import dev.tvlauncher.settings.SettingsBackup
import dev.tvlauncher.setup.ConfigBackup
import dev.tvlauncher.ui.HorizonLoaderMath
import dev.tvlauncher.ui.SplashReveal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionStyleSettingsTest {
    @Test fun defaults() {
        val d = MotionStyleSettings()
        assertEquals(StartupStyle.SUNRISE, d.startup)
        assertEquals(StartupWhen.COLD_START, d.startupWhen)
        assertEquals(LoaderStyle.SUN_LINE, d.loader)
        assertEquals(LaunchStyle.BRAND_EXPAND, d.launch)
        assertEquals(MotionPace.NORMAL, d.pace)
    }

    @Test fun jsonRoundTripsEveryCombination() {
        for (st in StartupStyle.values()) for (l in LoaderStyle.values()) for (la in LaunchStyle.values()) for (p in MotionPace.values()) {
            val s = MotionStyleSettings(st, StartupWhen.ALSO_WAKE, l, la, p)
            assertEquals(s, MotionStyleSettings.fromJson(s.toJson()))
        }
    }

    @Test fun corruptOrPartialInputFallsBackPerField() {
        assertEquals(MotionStyleSettings.DEFAULT, MotionStyleSettings.fromJson(null))
        assertEquals(MotionStyleSettings.DEFAULT, MotionStyleSettings.fromJson("{not json"))
        val s = MotionStyleSettings.fromJson("""{"startup":"AURORA","loader":"NOPE","pace":7}""")
        assertEquals(StartupStyle.AURORA, s.startup)
        assertEquals(LoaderStyle.SUN_LINE, s.loader)
        assertEquals(MotionPace.NORMAL, s.pace)
    }
}

class StartupTimelinesTest {
    @Test fun everyStyleIsMonotonicAndWithinTheCaps() {
        for (full in listOf(true, false)) for (pace in MotionPace.values()) for (style in StartupStyle.values()) {
            val tl = StartupTimelines.of(style, full, pace) ?: continue
            assertTrue("$style $pace", tl.isMonotonic)
            assertTrue("$style full=$full $pace ${tl.totalMs}", tl.totalMs <= MotionCaps.startup(full))
            assertTrue(tl.introEndMs in 1 until tl.totalMs)
            assertNotNull(tl.first(StartupTimelines.Part.MARK))
            assertNotNull(tl.first(StartupTimelines.Part.CONTENT))
        }
    }

    @Test fun liteNeverPasses700() {
        for (pace in MotionPace.values()) {
            assertTrue(StartupTimelines.minimal(false, pace).totalMs <= 700)
            assertTrue(StartupTimelines.aurora(false, pace).totalMs <= 700)
        }
        assertTrue(SplashReveal.lite().totalMs <= MotionCaps.LITE_STARTUP_MS)
        assertTrue(SplashReveal.full(SplashReveal.MAX_STAGGERED_TILES).totalMs <= MotionCaps.FULL_STARTUP_MS)
    }

    @Test fun paceOrdersDurations() {
        for (full in listOf(true, false)) {
            val f = StartupTimelines.aurora(full, MotionPace.FAST).totalMs
            val n = StartupTimelines.aurora(full, MotionPace.NORMAL).totalMs
            val c = StartupTimelines.aurora(full, MotionPace.CALM).totalMs
            assertTrue("$f $n $c", f < n && n <= c)
        }
    }

    @Test fun sunriseAndOffHaveNoOwnTimeline() {
        assertNull(StartupTimelines.of(StartupStyle.SUNRISE, true, MotionPace.NORMAL))
        assertNull(StartupTimelines.of(StartupStyle.OFF, false, MotionPace.NORMAL))
    }

    @Test fun auroraColoursAreNeverOpaqueAndEdgesClear() {
        for (full in listOf(true, false)) {
            val c = AuroraLook.colors(0xFF4C8DFF.toInt(), full)
            assertEquals(4, c.size)
            assertEquals(0, c[0]); assertEquals(0, c[3])
            assertTrue(c.all { (it ushr 24) < 0xFF })
        }
    }
}

class LaunchStylesTest {
    private val pkgs = listOf("com.netflix.ninja", "org.example.app")

    @Test fun withinCapsForEveryPace() {
        for (mode in EffectsMode.values()) for (pace in MotionPace.values()) for (style in LaunchStyle.values()) for (p in pkgs) for (warm in listOf(true, false)) {
            val s = LaunchStyles.plan(style, mode, 1f, p, warm, pace)
            if (style == LaunchStyle.OFF) { assertFalse(s.animated); continue }
            assertTrue(s.animated)
            assertTrue("$mode $pace ${s.plan.expandMs}", s.plan.expandMs <= MotionCaps.launch(mode == EffectsMode.FULL))
            assertTrue(s.plan.logoDelayMs + s.plan.logoMs == s.plan.expandMs)
        }
        assertTrue(LaunchStyles.plan(LaunchStyle.ZOOM, EffectsMode.LITE, 1f, pkgs[0], false, MotionPace.CALM).plan.expandMs <= 350)
    }

    @Test fun animationsOffMeansNoMotion() {
        for (style in LaunchStyle.values()) {
            assertFalse(LaunchStyles.plan(style, null, 1f, "a.b", false, MotionPace.CALM).animated)
            assertFalse(LaunchStyles.plan(style, EffectsMode.FULL, 0f, "a.b", false, MotionPace.CALM).animated)
        }
        assertEquals(LaunchMotion.NONE, LaunchStyles.plan(LaunchStyle.FADE, EffectsMode.FULL, 0f, "a.b", false, MotionPace.FAST).plan)
    }

    @Test fun everyStyleEndsCoveringTheScreen() {
        val out = FloatArray(LaunchGeometry.OUT_SIZE)
        val tile = floatArrayOf(100f, 400f, 340f, 535f)
        for (style in listOf(LaunchStyle.BRAND_EXPAND, LaunchStyle.FADE, LaunchStyle.ZOOM)) for (from in listOf(tile, null)) {
            LaunchGeometry.frame(style, 1f, from, 1920f, 1080f, out)
            assertTrue("$style ${out.toList()}", out[0] <= 0.5f && out[1] <= 0.5f && out[2] >= 1919.5f && out[3] >= 1079.5f)
            assertEquals(1f, out[4], 1e-4f)
            assertEquals(0f, out[5], 1e-4f)
        }
        LaunchGeometry.frame(LaunchStyle.ZOOM, 0f, tile, 1920f, 1080f, out)
        assertEquals(100f, out[0], 0.01f); assertEquals(535f, out[3], 0.01f)
        LaunchGeometry.frame(LaunchStyle.FADE, 0f, tile, 1920f, 1080f, out)
        assertEquals(0f, out[4], 0f)
    }
}

class LaunchWatchTest {
    @Test fun fastAppNeverShowsTheLoader() {
        val w = LaunchWatch()
        w.begin(1000)
        assertEquals(LaunchWatch.Action.NONE, w.motionEnded(1350))
        assertEquals(LaunchWatch.State.WAITING, w.state)
        assertEquals(1600L, w.nextAt())
        assertEquals(LaunchWatch.Action.DISMISS, w.stop()) // home stopped at 1500: the app is up
        assertEquals(LaunchWatch.State.IDLE, w.state)
        assertEquals(LaunchWatch.Action.NONE, w.tick(1600))
        assertNull(w.nextAt())
    }

    @Test fun slowAppShowsTheLoaderAfter600AndIsDismissedAt4s() {
        val w = LaunchWatch()
        w.begin(0)
        assertEquals(LaunchWatch.Action.NONE, w.tick(300)) // still in motion
        assertEquals(LaunchWatch.Action.NONE, w.motionEnded(480))
        assertEquals(LaunchWatch.Action.NONE, w.tick(599))
        assertEquals(LaunchWatch.Action.SHOW_LOADER, w.tick(600))
        assertEquals(LaunchWatch.State.LOADER, w.state)
        assertEquals(4000L, w.nextAt())
        assertEquals(LaunchWatch.Action.NONE, w.tick(3999))
        assertEquals(LaunchWatch.Action.DISMISS, w.tick(4000))
        assertEquals(LaunchWatch.State.IDLE, w.state)
        assertEquals(LaunchWatch.Action.NONE, w.stop())
    }

    @Test fun lateMotionEndShowsTheLoaderAtOnce() {
        val w = LaunchWatch()
        w.begin(0)
        assertEquals(LaunchWatch.Action.SHOW_LOADER, w.motionEnded(900)) // a janky frame delayed the end
    }

    @Test fun stuckMotionIsStillDismissed() {
        val w = LaunchWatch()
        w.begin(0)
        assertEquals(4000L, w.nextAt())
        assertEquals(LaunchWatch.Action.DISMISS, w.tick(4200))
        assertEquals(LaunchWatch.Action.NONE, w.motionEnded(4300)) // a late end after dismissal does nothing
    }

    @Test fun newLaunchRestartsTheClock() {
        val w = LaunchWatch()
        w.begin(0); w.motionEnded(400); w.tick(700)
        w.stop(); w.begin(10_000)
        assertEquals(LaunchWatch.Action.NONE, w.motionEnded(10_400))
        assertEquals(10_600L, w.nextAt())
    }
}

class LoaderMathTest {
    @Test fun loadersAreContinuousAtTheWrap() {
        assertEquals(PulseRingMath.ringAlpha(1f), 0f, 1e-4f)
        assertEquals(PulseRingMath.ringRadius(0f), 0f, 1e-4f)
        for (i in 0 until DotsMath.COUNT) assertEquals(DotsMath.lift(0f, i), DotsMath.lift(1f, i), 1e-4f)
        assertEquals(HorizonLoaderMath.position(0f), HorizonLoaderMath.position(1f), 1e-4f)
    }

    @Test fun valuesStayInRange() {
        var p = 0f
        while (p <= 1f) {
            assertTrue(PulseRingMath.ringRadius(p) in 0f..1f)
            assertTrue(PulseRingMath.ringAlpha(p) in 0f..1f)
            for (i in 0 until DotsMath.COUNT) assertTrue(DotsMath.lift(p, i) in 0f..1f)
            p += 0.01f
        }
        assertTrue(DotsMath.lift(0.5f, 1) > 0.99f) // the middle dot leads mid-cycle
    }
}

class WakeSplashTest {
    private val wake = MotionStyleSettings(startupWhen = StartupWhen.ALSO_WAKE)

    @Test fun playsOnlyWhenChosenAndRecent() {
        assertTrue(WakeSplash.shouldPlay(wake, wokeAt = 1000, now = 3000))
        assertFalse(WakeSplash.shouldPlay(MotionStyleSettings(), 1000, 3000)) // cold start only
        assertFalse(WakeSplash.shouldPlay(wake.copy(startup = StartupStyle.OFF), 1000, 3000))
        assertFalse(WakeSplash.shouldPlay(wake, 0, 3000)) // no wake seen
        assertFalse(WakeSplash.shouldPlay(wake, 1000, 1000 + WakeSplash.WINDOW_MS + 1))
    }
}

class StartupLoadingPageTest {
    @Test fun rowsPassTheGuardAndBudget() {
        val g = RowGuard(StartupLoadingPage.SPEC)
        StartupLoadingPage.SPEC.primaryRows.forEach { g.claim(it, advanced = false) }
        assertTrue(StartupLoadingPage.SPEC.withinBudget)
        assertTrue(AppearanceSection.spec.primaryRows.size <= PageSpec.MAX_PRIMARY)
        assertTrue("appearance.startup" in AppearanceSection.spec.primaryRows)
    }

    @Test fun copyIsPlainAndHonest() {
        val banned = Regex("""\badb\b|developer|command line|\bshell\b|LITE|FULL|bootloader""", RegexOption.IGNORE_CASE)
        val all = listOf(StartupLoadingPage.TITLE, StartupLoadingPage.NOTE) +
            listOf(true, false).flatMap { f -> listOf(true, false).map { StartupLoadingPage.paceSubtitle(f, it) } } +
            StartupStyle.values().map { it.label } + LoaderStyle.values().map { it.label } +
            LaunchStyle.values().map { it.label } + MotionPace.values().map { it.label } + StartupWhen.values().map { it.label }
        for (s in all) assertFalse(s, banned.containsMatchIn(s))
        assertTrue(StartupLoadingPage.NOTE.contains("belongs to the TV"))
        assertTrue(StartupLoadingPage.paceSubtitle(false, true).contains("700 ms"))
    }
}

class MotionBackupTest {
    @Test fun settingsBackupCarriesTheMotionSection() {
        val m = MotionStyleSettings(StartupStyle.AURORA, StartupWhen.ALSO_WAKE, LoaderStyle.DOTS, LaunchStyle.ZOOM, MotionPace.CALM)
        val text = SettingsBackup.toText(LauncherSettings(), motion = m)
        assertEquals(m, SettingsBackup.parseRestored(text)!!.motion)
        assertNull(SettingsBackup.parseRestored(SettingsBackup.toText(LauncherSettings()))!!.motion) // older files: left alone
        val bad = org.json.JSONObject(text).put(SettingsBackup.MOTION_KEY, "x").toString()
        assertNull(SettingsBackup.parseRestored(bad))
    }

    @Test fun configBackupIncludesTheStore() {
        assertTrue(MotionStylePrefs.STORE in ConfigBackup.STORES)
        assertTrue(ConfigBackup.isAllowed(MotionStylePrefs.STORE))
    }
}
