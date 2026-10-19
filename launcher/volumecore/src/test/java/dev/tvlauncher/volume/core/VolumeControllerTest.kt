package dev.tvlauncher.volume.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A volume source whose behaviour the test picks. */
class FakeBackend(
    override val id: String,
    var state: VolumeState? = VolumeState(10, 100, false),
    var fixed: Boolean = false,
    /** What a write really does to the state: default applies it exactly. */
    var effect: (VolumeState, VolumeState) -> VolumeState = { _, to -> to },
    var throwOnRead: Boolean = false,
) : VolumeBackend {
    var writes = 0
    override fun read(): VolumeState? {
        if (throwOnRead) throw IllegalStateException("boom")
        return state
    }
    override fun canControl() = !fixed
    override fun write(from: VolumeState, to: VolumeState) {
        writes++
        state = effect(from, to)
    }
}

class VolumeControllerTest {
    private var route = "speaker"

    private fun controller(vararg b: VolumeBackend) = VolumeController(b.toList()) { route }

    @Test fun applies_and_verifies() {
        val a = FakeBackend("a")
        val c = controller(a)
        val r = c.apply(VolumeKey.UP, 0)
        assertEquals(VolumeResult.Handled(VolumeState(11, 100, false), "a"), r)
        assertTrue(c.isProven("speaker", "a"))
    }

    @Test fun fixed_volume_passes_to_the_system() {
        val a = FakeBackend("a", fixed = true)
        val r = controller(a).apply(VolumeKey.UP, 0)
        assertTrue(r is VolumeResult.Pass)
        assertEquals(0, a.writes)
    }

    @Test fun no_reading_passes() {
        assertTrue(controller(FakeBackend("a", state = null)).apply(VolumeKey.UP, 0) is VolumeResult.Pass)
        assertTrue(controller(FakeBackend("a", state = VolumeState(0, 0, false))).apply(VolumeKey.UP, 0) is VolumeResult.Pass)
        assertTrue(controller(FakeBackend("a", throwOnRead = true)).apply(VolumeKey.UP, 0) is VolumeResult.Pass)
    }

    @Test fun invisible_change_falls_back_to_the_next_backend_then_to_the_system() {
        val ignored = FakeBackend("set", effect = { from, _ -> from })
        val adjust = FakeBackend("adjust", effect = { from, _ -> from })
        val c = controller(ignored, adjust)
        assertTrue(c.apply(VolumeKey.UP, 0) is VolumeResult.Pass)
        assertTrue(c.isFailed("speaker", "set"))
        assertTrue(c.isFailed("speaker", "adjust"))
        // Next press: nothing is written any more, the key goes straight to the system.
        assertTrue(c.apply(VolumeKey.UP, 0) is VolumeResult.Pass)
        assertEquals(1, ignored.writes)
        assertEquals(1, adjust.writes)
    }

    @Test fun second_backend_takes_over() {
        val ignored = FakeBackend("set", effect = { from, _ -> from })
        val works = FakeBackend("adjust")
        val r = controller(ignored, works).apply(VolumeKey.DOWN, 0)
        assertEquals(VolumeResult.Handled(VolumeState(9, 100, false), "adjust"), r)
    }

    @Test fun partial_change_is_consumed_once_then_passes() {
        // A full-volume output snaps to max: something changed, so passing would apply the key twice.
        val snaps = FakeBackend("a", effect = { from, _ -> from.copy(level = from.max) })
        val c = controller(snaps)
        val r = c.apply(VolumeKey.UP, 0)
        assertEquals(VolumeResult.Handled(VolumeState(100, 100, false), "a"), r)
        assertTrue(c.isFailed("speaker", "a"))
        assertTrue(c.apply(VolumeKey.DOWN, 0) is VolumeResult.Pass)
    }

    @Test fun at_limit_passes_until_the_route_is_proven() {
        val a = FakeBackend("a", state = VolumeState(100, 100, false))
        val c = controller(a)
        assertTrue(c.apply(VolumeKey.UP, 0) is VolumeResult.Pass)
        assertEquals(0, a.writes)
        // Prove the route with a real change, then the end of the range is ours.
        assertTrue(c.apply(VolumeKey.DOWN, 0) is VolumeResult.Handled)
        assertTrue(c.apply(VolumeKey.UP, 0) is VolumeResult.Handled)
        assertEquals(VolumeResult.Handled(VolumeState(100, 100, false), "a"), c.apply(VolumeKey.UP, 0))
    }

    @Test fun skipped_repeat_keeps_the_bar_without_writing() {
        val a = FakeBackend("a", state = VolumeState(5, 15, false))
        val r = controller(a).apply(VolumeKey.UP, 1)
        assertEquals(VolumeResult.Handled(VolumeState(5, 15, false), "a"), r)
        assertEquals(0, a.writes)
    }

    @Test fun failure_is_per_route() {
        val a = FakeBackend("a", effect = { from, _ -> from })
        val c = controller(a)
        assertTrue(c.apply(VolumeKey.UP, 0) is VolumeResult.Pass)
        route = "headphones"
        a.effect = { _, to -> to }
        assertTrue(c.apply(VolumeKey.UP, 0) is VolumeResult.Handled)
        route = "speaker"
        assertTrue(c.apply(VolumeKey.UP, 0) is VolumeResult.Pass)
    }

    @Test fun route_errors_do_not_break_control() {
        val a = FakeBackend("a")
        val c = VolumeController(listOf(a)) { throw RuntimeException("no route") }
        assertTrue(c.apply(VolumeKey.UP, 0) is VolumeResult.Handled)
    }

    @Test fun mute_is_verified_too() {
        val a = FakeBackend("a")
        val c = controller(a)
        assertEquals(VolumeResult.Handled(VolumeState(10, 100, true), "a"), c.apply(VolumeKey.MUTE, 0))
        val deaf = FakeBackend("b", effect = { from, _ -> from })
        assertTrue(controller(deaf).apply(VolumeKey.MUTE, 0) is VolumeResult.Pass)
    }

    @Test fun current_skips_fixed_and_failed() {
        val fixed = FakeBackend("a", fixed = true)
        val b = FakeBackend("b", state = VolumeState(3, 15, false))
        assertEquals(VolumeState(3, 15, false), controller(fixed, b).current())
        assertFalse(controller(fixed).current() != null)
    }
}
