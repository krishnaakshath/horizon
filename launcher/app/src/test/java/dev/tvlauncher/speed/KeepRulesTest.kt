package dev.tvlauncher.speed

import dev.tvlauncher.speed.KeepRules.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepRulesTest {
    private val apps = setOf(
        "com.netflix.ninja", "com.google.android.youtube.tv", "com.spotify.tv.android", "org.videolan.vlc",
        "com.tcl.tv", "com.tcl.esticker", "dev.tvlauncher", "dev.tvlauncher.helper", "dev.tvlauncher.weather",
        "com.android.tv.settings", "com.google.android.katniss", "com.example.keyboard", "com.example.reader",
        "com.google.android.apps.mediashell", "com.mediatek.wwtv.tvcenter", "com.android.vending"
    )
    private val base = KeepRules.Context(ownPackage = "dev.tvlauncher", launchable = apps)

    private fun r(pkg: String, c: KeepRules.Context = base) = KeepRules.reason(pkg, c)

    @Test fun ourOwnPackages_areAlwaysKept() {
        assertEquals(Reason.OURS, r("dev.tvlauncher"))
        assertEquals(Reason.OURS, r("dev.tvlauncher.helper"))
        assertEquals(Reason.OURS, r("dev.tvlauncher.weather"))
        assertEquals(Reason.OURS, r("dev.tvlauncher.store"))
    }

    @Test fun liveTvAndTclApps_areKept() {
        assertEquals(Reason.LIVE_TV, r("com.tcl.tv"))
        assertEquals(Reason.LIVE_TV, r("com.tcl.esticker"))
        assertEquals(Reason.LIVE_TV, r("com.android.tv"))
        // "com.tclx" is not TCL's namespace.
        assertNull(r("com.tclx.player", base.copy(launchable = setOf("com.tclx.player"))))
    }

    @Test fun systemCriticalPackages_areKept() {
        listOf("android", "com.android.systemui", "com.google.android.gms", "com.android.vending",
            "com.google.android.katniss", "com.google.android.apps.mediashell", "com.android.tv.settings",
            "com.mediatek.wwtv.tvcenter", "com.android.providers.tv", "com.google.android.inputmethod.latin")
            .forEach { pkg -> assertTrue(pkg, r(pkg) == Reason.SYSTEM || r(pkg) == Reason.LIVE_TV) }
    }

    @Test fun inputAccessibilityHomeAndScreensaver_areKept() {
        val c = base.copy(
            inputMethods = setOf("com.example.keyboard"),
            accessibility = setOf("com.example.reader"),
            homeApps = setOf("com.other.home"),
            screensaver = setOf("com.example.dream")
        )
        assertEquals(Reason.INPUT, r("com.example.keyboard", c))
        assertEquals(Reason.ACCESSIBILITY, r("com.example.reader", c))
        assertEquals(Reason.HOME_APP, r("com.other.home", c))
        assertEquals(Reason.SCREENSAVER, r("com.example.dream", c))
    }

    @Test fun playingForegroundOwnerAndRecent_areKept() {
        val c = base.copy(
            playing = setOf("com.spotify.tv.android"),
            foreground = setOf("com.netflix.ninja"),
            owner = setOf("org.videolan.vlc"),
            recent = setOf("com.google.android.youtube.tv")
        )
        assertEquals(Reason.PLAYING, r("com.spotify.tv.android", c))
        assertEquals(Reason.FOREGROUND, r("com.netflix.ninja", c))
        assertEquals(Reason.OWNER, r("org.videolan.vlc", c))
        assertEquals(Reason.RECENT, r("com.google.android.youtube.tv", c))
    }

    @Test fun ordinaryBackgroundApps_areClosable() {
        assertNull(r("com.netflix.ninja"))
        assertNull(r("com.google.android.youtube.tv"))
        assertTrue(KeepRules.closable("org.videolan.vlc", base))
    }

    @Test fun packagesWithoutALauncherEntry_areNeverClosed() {
        assertEquals(Reason.NOT_AN_APP, r("com.example.backgroundservice"))
        assertEquals(Reason.NOT_AN_APP, r(""))
    }

    @Test fun fixedReason_ignoresMomentaryState() {
        val c = base.copy(playing = setOf("com.netflix.ninja"))
        assertNull(KeepRules.fixedReason("com.netflix.ninja", c))
        assertEquals(Reason.OURS, KeepRules.fixedReason("dev.tvlauncher.browser", c))
    }

    // Close plan

    @Test fun plan_withUsage_onlyRecentlyUsedClosable_newestFirst() {
        val now = 10 * RateLimit.DAY_MS
        val used = mapOf(
            "com.netflix.ninja" to now - 60_000L,
            "com.google.android.youtube.tv" to now - 3_600_000L,
            "org.videolan.vlc" to now - 5 * RateLimit.DAY_MS, // too old: not in memory
            "dev.tvlauncher" to now - 1_000L,
            "com.tcl.tv" to now - 2_000L,
            "com.example.service" to now - 5_000L // no launcher entry
        )
        assertEquals(listOf("com.netflix.ninja", "com.google.android.youtube.tv"), ClosePlan.candidates(base, used, now))
    }

    @Test fun plan_withoutUsage_allLaunchableClosable_sorted() {
        val plan = ClosePlan.candidates(base, null, 0L)
        assertEquals(listOf("com.example.keyboard", "com.example.reader", "com.google.android.youtube.tv",
            "com.netflix.ninja", "com.spotify.tv.android", "org.videolan.vlc"), plan)
    }

    @Test fun plan_isBounded() {
        val many = (1..100).map { "com.example.app$it" }.toSet()
        assertEquals(ClosePlan.MAX_PER_RUN, ClosePlan.candidates(base.copy(launchable = many), null, 0L).size)
    }

    @Test fun backgroundCount_usesTodayOnly_andNeedsUsage() {
        val now = 10 * RateLimit.DAY_MS
        val used = mapOf("com.netflix.ninja" to now - 60_000L, "org.videolan.vlc" to now - 2 * RateLimit.DAY_MS)
        assertEquals(1, ClosePlan.backgroundCount(base, used, now))
        assertNull(ClosePlan.backgroundCount(base, null, now))
    }

    // Foreground guess and settings parsing

    @Test fun foreground_isLastAppThatCameForwardAndStayed() {
        val e = listOf(
            ForegroundGuess.Event("a.b", ForegroundGuess.Kind.FOREGROUND, 1),
            ForegroundGuess.Event("a.b", ForegroundGuess.Kind.BACKGROUND, 2),
            ForegroundGuess.Event("c.d", ForegroundGuess.Kind.FOREGROUND, 3),
            ForegroundGuess.Event("e.f", ForegroundGuess.Kind.FOREGROUND, 4),
            ForegroundGuess.Event("e.f", ForegroundGuess.Kind.BACKGROUND, 5)
        )
        assertEquals("c.d", ForegroundGuess.current(e))
        assertNull(ForegroundGuess.current(emptyList()))
    }

    @Test fun componentLists_parseSecureSettings() {
        assertEquals(setOf("dev.tvlauncher.helper", "com.google.android.marvin.talkback"),
            ComponentLists.packages("dev.tvlauncher.helper/.HelperService:com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"))
        assertEquals(setOf("com.google.android.inputmethod.latin"),
            ComponentLists.packages("com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"))
        assertEquals(emptySet<String>(), ComponentLists.packages(null))
        assertEquals(emptySet<String>(), ComponentLists.packages(" : /x :nodot/.S"))
    }

    @Test fun ownerList_roundTrips() {
        val s = ComponentLists.parseOwner(" b.b , a.a,, b.b ")
        assertEquals(setOf("a.a", "b.b"), s)
        assertEquals("a.a,b.b", ComponentLists.formatOwner(s))
        assertEquals(emptySet<String>(), ComponentLists.parseOwner(null))
    }
}
