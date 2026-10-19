package dev.tvlauncher.spotlight

import android.view.KeyEvent
import dev.tvlauncher.spotlight.HelperStatus.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HelperLinkTest {
    private val BOOKMARK = KeyEvent.KEYCODE_BOOKMARK
    private val name: (Int) -> String = { TriggerKeys.name(it) }

    @Test fun payload_carriesOnlyTheExtraKey() {
        assertEquals(HelperLink.Payload(0, false), HelperLink.payload(SpotlightConfig.DEFAULT))
        val c = SpotlightConfig.DEFAULT.copy(extraKey = BOOKMARK)
        assertEquals(HelperLink.Payload(BOOKMARK, false), HelperLink.payload(c))
        assertEquals(HelperLink.Payload(BOOKMARK, true), HelperLink.payload(c.copy(doublePress = DoublePress.EXTRA)))
        // Double-press of Menu is a home-screen thing; the helper never hears about it.
        assertEquals(HelperLink.Payload(BOOKMARK, false), HelperLink.payload(c.copy(doublePress = DoublePress.MENU)))
        // A reserved key never leaves the launcher, even if one slipped into memory.
        assertEquals(0, HelperLink.payload(c.copy(extraKey = KeyEvent.KEYCODE_BACK)).key)
    }

    @Test fun report_isValidated() {
        assertNull(HelperLink.parseReport(0, HelperLink.REASON_HELLO, true, BOOKMARK, false, BOOKMARK))
        assertNull(HelperLink.parseReport(1, "spam", true, BOOKMARK, false, BOOKMARK))
        assertNull(HelperLink.parseReport(1, null, true, BOOKMARK, false, BOOKMARK))
        val r = HelperLink.parseReport(1, HelperLink.REASON_REPLY, true, KeyEvent.KEYCODE_HOME, true, -3)!!
        assertEquals(0, r.syncedKey)
        assertFalse(r.syncedDouble)
        assertEquals(0, r.effectiveKey)
        assertFalse(r.hello)
        assertNotNull(HelperLink.parseReport(2, HelperLink.REASON_HELLO, true, BOOKMARK, false, BOOKMARK)) // newer helper
    }

    @Test fun push_onlyAfterAStaleHello_soNoPingPong() {
        val p = HelperLink.Payload(BOOKMARK, false)
        val staleHello = HelperLink.parseReport(1, HelperLink.REASON_HELLO, true, 0, false, 0)!!
        val staleReply = HelperLink.parseReport(1, HelperLink.REASON_REPLY, true, 0, false, 0)!!
        val freshHello = HelperLink.parseReport(1, HelperLink.REASON_HELLO, true, BOOKMARK, false, BOOKMARK)!!
        assertTrue(HelperLink.needsPush(staleHello, p))
        assertFalse(HelperLink.needsPush(staleReply, p))
        assertFalse(HelperLink.needsPush(freshHello, p))
        assertTrue(HelperLink.needsPush(freshHello, p.copy(double = true)))
        assertTrue(HelperLink.inSync(freshHello, p))
        assertFalse(HelperLink.inSync(null, p))
    }

    @Test fun serviceEnabled_parsesTheSecureSetting() {
        assertFalse(HelperLink.serviceEnabled(null))
        assertFalse(HelperLink.serviceEnabled(""))
        assertTrue(HelperLink.serviceEnabled("dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService"))
        assertTrue(HelperLink.serviceEnabled("com.a/.X:dev.tvlauncher.helper/.HelperService"))
        assertFalse(HelperLink.serviceEnabled("dev.tvlauncher.helper.evil/dev.tvlauncher.helper.HelperService"))
        assertFalse(HelperLink.serviceEnabled("dev.tvlauncher.helper/dev.tvlauncher.helper.Other"))
    }

    // --- "Open from any app" row ---

    private fun status(installed: Boolean = true, on: Boolean = true, screen: Boolean = true, key: Int = BOOKMARK, report: HelperLink.Report? = null) =
        HelperStatus.resolve(installed, on, screen, HelperLink.Payload(key, false), report, name)

    @Test fun notInstalled() {
        val d = status(installed = false)
        assertEquals("Not installed", d.value)
        assertEquals(Action.NONE, d.action)
    }

    @Test fun serviceOff_isOneCalmLine_neverAnUnhandledLink() {
        val withScreen = status(on = false)
        assertEquals(HelperStatus.FINISH_SETUP, withScreen.value)
        assertNull(withScreen.subtitle)
        assertEquals(Action.OPEN_HELPER, withScreen.action)
        val noScreen = status(on = false, screen = false)
        assertEquals(HelperStatus.FINISH_SETUP, noScreen.value)
        assertEquals(Action.NONE, noScreen.action)
    }

    @Test fun noDeveloperText_inAnyState() {
        val reports = listOf(null, HelperLink.Report(true, BOOKMARK, false, BOOKMARK, false))
        for (i in listOf(true, false)) for (o in listOf(true, false)) for (s in listOf(true, false)) for (k in listOf(0, BOOKMARK)) for (r in reports) {
            val d = status(i, o, s, k, r)
            val text = (d.value + " " + d.subtitle.orEmpty()).lowercase()
            for (bad in listOf("adb", "shell", "settings put", "developer", "accessibility_")) assertFalse(text, text.contains(bad))
        }
    }

    @Test fun keyStates() {
        assertEquals(Action.OPEN_KEYS, status(key = 0).action)
        assertEquals("Choose a key", status(key = 0).value)
        val synced = status(report = HelperLink.Report(true, BOOKMARK, false, BOOKMARK, false))
        assertEquals("On", synced.value)
        assertEquals("Press Bookmark in any app", synced.subtitle)
        assertEquals("Waiting for the helper", status(report = null).value)
    }

    // --- Manifests: the guard is declared on both sides ---

    private fun read(vararg paths: String): String? = paths.map { File(it) }.firstOrNull { it.exists() }?.readText()

    @Test fun manifests_declareTheSignaturePermission_andGuardTheReceivers() {
        val app = read("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")!!
        assertTrue(app.contains("android:name=\"${HelperLink.PERMISSION}\"\n        android:protectionLevel=\"signature\""))
        assertTrue(app.contains("<uses-permission android:name=\"${HelperLink.PERMISSION}\""))
        assertTrue(Regex("HelperStateReceiver\"\\s+android:exported=\"true\"\\s+android:permission=\"${Regex.escape(HelperLink.PERMISSION)}\"").containsMatchIn(app))
        assertFalse("no INTERNET in the launcher", app.contains("android.permission.INTERNET"))
        val helper = read("../helper/src/main/AndroidManifest.xml", "helper/src/main/AndroidManifest.xml") ?: return
        assertTrue(helper.contains("android:name=\"${HelperLink.PERMISSION}\"\n        android:protectionLevel=\"signature\""))
        assertTrue(Regex("SpotlightSyncReceiver\"\\s+android:exported=\"true\"\\s+android:permission=\"${Regex.escape(HelperLink.PERMISSION)}\"").containsMatchIn(helper))
    }
}
