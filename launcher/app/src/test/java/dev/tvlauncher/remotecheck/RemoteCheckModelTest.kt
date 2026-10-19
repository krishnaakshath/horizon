package dev.tvlauncher.remotecheck

import dev.tvlauncher.launch.LaunchMotion
import dev.tvlauncher.device.EffectsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCheckModelTest {
    @Test fun recordsSeenKeys_andOthers() {
        val m = RemoteCheckModel()
        m.onKeyDown(RemoteKeys.DPAD_CENTER, 352, "Remote", 0, 0)
        m.onKeyDown(RemoteKeys.ENTER, 28, "Keyboard", 0, 10) // same "OK" key
        m.onKeyDown(RemoteKeys.PROG_RED, 0, "Remote", 0, 20)
        m.onKeyDown(999, 0, "Remote", 0, 30, "KEYCODE_999")
        assertEquals(2, m.seenCount())
        assertEquals(mapOf(999 to "Key 999"), m.otherKeys)
        assertEquals("Key 999", m.last?.name)
        m.reset()
        assertEquals(0, m.seenCount())
    }

    @Test fun backTwiceLeaves_volumePasses() {
        val m = RemoteCheckModel()
        assertFalse(m.onKeyDown(RemoteKeys.BACK, 0, "", 0, 0))
        assertTrue(m.onKeyDown(RemoteKeys.BACK, 0, "", 0, 800))
        assertFalse(m.onKeyDown(RemoteKeys.BACK, 0, "", 0, 900))
        assertFalse(m.onKeyDown(RemoteKeys.BACK, 0, "", 0, 5000))
        assertTrue(m.passThrough(RemoteKeys.VOLUME_UP))
        assertFalse(m.passThrough(RemoteKeys.DPAD_UP))
    }

    @Test fun names_andSources() {
        assertEquals("OK", RemoteKeys.name(RemoteKeys.NUMPAD_ENTER))
        assertEquals("Tv zoom mode", RemoteKeys.name(255, "KEYCODE_TV_ZOOM_MODE"))
        assertEquals("Controller · Pad", KeySource.label(KeySource.SOURCE_GAMEPAD, "Pad", false))
        assertEquals("Remote · TCL RC", KeySource.label(KeySource.SOURCE_KEYBOARD, "TCL RC", false))
        assertEquals("The TV", KeySource.label(0, null, true))
        val codes = RemoteKeys.ALL.flatMap { it.codes }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test fun launchMotion_limits() {
        for (pkg in listOf("com.netflix.ninja", "x.y")) for (warm in listOf(true, false)) {
            assertTrue(LaunchMotion.plan(EffectsMode.LITE, 1f, pkg, warm).expandMs <= LaunchMotion.LITE_MAX_MS)
            assertTrue(LaunchMotion.plan(EffectsMode.FULL, 1f, pkg, warm).expandMs <= LaunchMotion.FULL_MAX_MS)
        }
        assertFalse(LaunchMotion.plan(null, 1f, "x.y", false).animated)
        assertFalse(LaunchMotion.plan(EffectsMode.FULL, 0f, "x.y", false).animated)
        assertTrue(LaunchMotion.plan(EffectsMode.LITE, 1f, "com.netflix.ninja", false).expandMs >
            LaunchMotion.plan(EffectsMode.LITE, 1f, "com.netflix.ninja", true).expandMs)
    }
}
