package dev.tvlauncher.store.core

import dev.tvlauncher.store.core.LeanbackDetector.Event
import org.junit.Assert.assertEquals
import org.junit.Test

class LeanbackDetectorTest {

    private fun start(name: String, vararg attrs: Pair<String, String>) = Event.Start(name, attrs.toMap())
    private fun end(name: String) = Event.End(name)
    private val pkg = start("manifest", "package" to "org.x")

    @Test fun activityWithLeanbackLauncherCategory() {
        val ev = listOf(pkg, start("application"),
            start("activity", "name" to ".Main"),
            start("intent-filter"),
            start("action", "name" to "android.intent.action.MAIN"), end("action"),
            start("category", "name" to "android.intent.category.LEANBACK_LAUNCHER"), end("category"),
            end("intent-filter"), end("activity"), end("application"), end("manifest"))
        assertEquals(LeanbackDetector.Result(packageName = "org.x", leanbackLauncher = true, leanbackFeature = false, banner = false),
            LeanbackDetector.scan(ev.asSequence()))
    }

    @Test fun aliasCountsAndPhoneLauncherDoesNot() {
        val alias = listOf(pkg, start("activity-alias"), start("intent-filter"),
            start("category", "name" to "android.intent.category.LEANBACK_LAUNCHER"))
        assertEquals(true, LeanbackDetector.scan(alias.asSequence()).leanbackLauncher)
        val phone = listOf(pkg, start("activity"), start("intent-filter"),
            start("category", "name" to "android.intent.category.LAUNCHER"))
        assertEquals(false, LeanbackDetector.scan(phone.asSequence()).leanbackLauncher)
    }

    @Test fun categoryOutsideActivityIgnored() {
        val ev = listOf(pkg, start("queries"), start("intent"),
            start("category", "name" to "android.intent.category.LEANBACK_LAUNCHER"), end("intent"), end("queries"))
        assertEquals(false, LeanbackDetector.scan(ev.asSequence()).leanbackLauncher)
    }

    @Test fun featureAndBannerRecorded() {
        val ev = listOf(pkg, start("uses-feature", "name" to "android.software.leanback", "required" to "false"),
            start("application", "banner" to "@0x7f080001"))
        val r = LeanbackDetector.scan(ev.asSequence())
        assertEquals(true, r.leanbackFeature)
        assertEquals(true, r.banner)
        assertEquals(true, r.tvReady)
    }

    @Test fun tvReadyRequiresLauncherOrFeature() {
        assertEquals(false, LeanbackDetector.Result("p", false, false, true).tvReady)
        assertEquals(true, LeanbackDetector.Result("p", true, false, false).tvReady)
    }
}
