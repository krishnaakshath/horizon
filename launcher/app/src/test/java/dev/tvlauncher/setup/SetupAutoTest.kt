package dev.tvlauncher.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupAutoTest {
    private val base = AutoInputs(canWriteSecure = false, listenerGranted = false, listenerFlat = null, helperInstalled = true,
        a11yFlat = null, a11yEnabled = null, tvUserSetupComplete = "0", nowPlayingAutoDone = false, roomConfigured = true,
        zoneId = "Asia/Kolkata")

    @Test fun withoutWriteSecure_nothingSecureIsWritten() {
        assertTrue(SetupAuto.plan(base).none { it is AutoOp.PutSecure })
    }

    @Test fun withWriteSecure_enablesListenerHelperAndTvSetup_keepingExistingEntries() {
        val ops = SetupAuto.plan(base.copy(canWriteSecure = true, listenerFlat = "com.other/.L", a11yFlat = "a.b/a.b.S"))
        assertTrue(ops.contains(AutoOp.PutSecure(SetupAuto.LISTENERS, "com.other/.L:" + SetupAuto.LISTENER_COMPONENT)))
        assertTrue(ops.contains(AutoOp.PutSecure(SetupAuto.A11Y_SERVICES, "a.b/a.b.S:" + SetupAuto.HELPER_COMPONENT)))
        assertTrue(ops.contains(AutoOp.PutSecure(SetupAuto.A11Y_ENABLED, "1")))
        assertTrue(ops.contains(AutoOp.PutSecure(SetupAuto.TV_SETUP_COMPLETE, "1")))
        assertTrue(ops.contains(AutoOp.EnableNowPlaying))
    }

    @Test fun tvSetupComplete_onlyWhenPresentAndZero() {
        val w = base.copy(canWriteSecure = true)
        for (v in listOf(null, "1", "")) {
            assertFalse(SetupAuto.plan(w.copy(tvUserSetupComplete = v)).any { it is AutoOp.PutSecure && it.key == SetupAuto.TV_SETUP_COMPLETE })
        }
    }

    @Test fun idempotent_onceApplied_planIsEmpty() {
        val done = base.copy(canWriteSecure = true, listenerGranted = true, listenerFlat = SetupAuto.LISTENER_COMPONENT,
            a11yFlat = "dev.tvlauncher.helper/.HelperService", a11yEnabled = "1", tvUserSetupComplete = "1", nowPlayingAutoDone = true)
        assertEquals(emptyList<AutoOp>(), SetupAuto.plan(done))
    }

    @Test fun helperNotInstalled_accessibilityUntouched() {
        val ops = SetupAuto.plan(base.copy(canWriteSecure = true, helperInstalled = false))
        assertFalse(ops.any { it is AutoOp.PutSecure && it.key.startsWith("accessibility") || it is AutoOp.PutSecure && it.key == SetupAuto.A11Y_SERVICES })
    }

    @Test fun nowPlaying_enabledOnceWhenAccessExists() {
        assertTrue(SetupAuto.plan(base.copy(listenerGranted = true)).contains(AutoOp.EnableNowPlaying))
        assertFalse(SetupAuto.plan(base).contains(AutoOp.EnableNowPlaying)) // no access and no way to get it
        assertFalse(SetupAuto.plan(base.copy(listenerGranted = true, nowPlayingAutoDone = true)).contains(AutoOp.EnableNowPlaying))
    }

    @Test fun room_onlyWhenNeverConfigured_withTimeZonePlace() {
        assertFalse(SetupAuto.plan(base).any { it is AutoOp.ConfigureRoom })
        val op = SetupAuto.plan(base.copy(roomConfigured = false)).filterIsInstance<AutoOp.ConfigureRoom>().single()
        assertEquals(21.1, op.latitude!!, 0.01)
        assertEquals(79.0, op.longitude!!, 0.01)
        val unknown = SetupAuto.plan(base.copy(roomConfigured = false, zoneId = "Etc/Unknown")).filterIsInstance<AutoOp.ConfigureRoom>().single()
        assertNull(unknown.latitude)
    }

    @Test fun secureList_normalizesShortForms_andNeverDuplicates() {
        assertEquals("a.b/a.b.C", SecureList.normalize("a.b/.C"))
        assertTrue(SecureList.contains("x/y:dev.tvlauncher/.ambient.AmbientNotificationService", SetupAuto.LISTENER_COMPONENT))
        assertEquals("x/y", SecureList.add("x/y", "x/y"))
        assertEquals(SetupAuto.LISTENER_COMPONENT, SecureList.add(null, SetupAuto.LISTENER_COMPONENT))
        assertEquals(SetupAuto.LISTENER_COMPONENT, SecureList.add("null", SetupAuto.LISTENER_COMPONENT))
        assertEquals(emptyList<String>(), SecureList.entries(" : "))
    }

    @Test fun gate_runsOnceAndNeverLoops() {
        assertTrue(SetupGate.shouldAutoStart(completed = false, autoStarts = 0, spotlightRequested = false))
        assertFalse(SetupGate.shouldAutoStart(completed = true, autoStarts = 0, spotlightRequested = false))
        assertFalse(SetupGate.shouldAutoStart(completed = false, autoStarts = SetupGate.MAX_AUTO_STARTS, spotlightRequested = false))
        assertFalse(SetupGate.shouldAutoStart(completed = false, autoStarts = 0, spotlightRequested = true))
    }

    @Test fun timeZoneGuess_validRanges() {
        assertNull(TimeZoneGuess.place(null))
        val p = TimeZoneGuess.place("Europe/London")!!
        assertTrue(p.first in -90.0..90.0 && p.second in -180.0..180.0)
    }
}
