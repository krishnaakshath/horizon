package dev.tvlauncher.launch

import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.device.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLauncherTest {
    private val app = AppEntry("p.a", "A", "p.a.Main")
    private val lite = DeviceProfile(1_000_000, true, 1920, 1080, "armeabi-v7a")
    private val full = DeviceProfile(4_000_000, false, 1920, 1080, "arm64-v8a")

    private class Fake(
        val clip: () -> Boolean = { true },
        val scale: () -> Boolean = { true },
        val fade: () -> Boolean = { true }
    ) : LaunchBackend {
        val calls = mutableListOf<String>()
        var duration = -1L
        override fun startWithClipReveal(app: AppEntry, source: Any, durationMs: Long): Boolean {
            calls += "clip"; duration = durationMs; return clip()
        }
        override fun startWithScaleUp(app: AppEntry, source: Any, durationMs: Long): Boolean {
            calls += "scale"; duration = durationMs; return scale()
        }
        override fun startWithFade(app: AppEntry, durationMs: Long): Boolean {
            calls += "fade"; duration = durationMs; return fade()
        }
    }

    private fun gone(): Boolean = throw RuntimeException("activity gone")

    @Test
    fun launch_missingActivity_returnsFalse_noThrow() {
        val b = Fake({ gone() }, { throw SecurityException() }, { throw IllegalStateException() })
        assertFalse(AppLauncher(full, b).launch(app, Any()))
        assertEquals(listOf("clip", "scale", "fade"), b.calls)
    }

    @Test
    fun sourceViewNull_usesFadeFallback() {
        val b = Fake()
        assertTrue(AppLauncher(full, b).launch(app, null))
        assertEquals(listOf("fade"), b.calls)
    }

    @Test
    fun liteMode_usesAppOpenMs250() {
        val b = Fake()
        AppLauncher(lite, b).launch(app, Any())
        assertEquals(250L, b.duration)
        val f = Fake()
        AppLauncher(full, f).launch(app, Any())
        assertEquals(350L, f.duration)
    }

    @Test
    fun fallsBackToScaleUp_whenClipRevealThrows() {
        val b = Fake(clip = { gone() })
        assertTrue(AppLauncher(full, b).launch(app, Any()))
        assertEquals(listOf("clip", "scale"), b.calls)
    }
}
