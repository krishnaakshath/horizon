package dev.tvlauncher.volume.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeMathTest {
    @Test fun step_size_follows_the_range() {
        assertEquals(0, VolumeMath.baseStep(0))
        assertEquals(1, VolumeMath.baseStep(15))
        assertEquals(1, VolumeMath.baseStep(100))
        assertEquals(2, VolumeMath.baseStep(150))
        assertEquals(3, VolumeMath.baseStep(255))
    }

    @Test fun first_press_is_one_step() {
        assertEquals(1, VolumeMath.delta(15, 0))
        assertEquals(1, VolumeMath.delta(100, 0))
        assertEquals(0, VolumeMath.delta(0, 0))
    }

    @Test fun short_ranges_move_on_every_third_repeat_without_acceleration() {
        val moves = (1..30).map { VolumeMath.delta(15, it) }
        assertEquals(10, moves.count { it > 0 })
        assertTrue(moves.all { it == 0 || it == 1 })
        assertEquals(0, VolumeMath.delta(15, 1))
        assertEquals(1, VolumeMath.delta(15, 3))
    }

    @Test fun mid_ranges_move_on_every_second_repeat() {
        assertEquals(0, VolumeMath.delta(40, 1))
        assertEquals(1, VolumeMath.delta(40, 2))
        assertEquals(1, VolumeMath.delta(40, 40))
    }

    @Test fun long_ranges_accelerate_while_held() {
        assertEquals(1, VolumeMath.delta(100, 1))
        assertEquals(1, VolumeMath.delta(100, VolumeMath.ACCEL_2_AFTER - 1))
        assertEquals(2, VolumeMath.delta(100, VolumeMath.ACCEL_2_AFTER))
        assertEquals(2, VolumeMath.delta(100, VolumeMath.ACCEL_3_AFTER - 1))
        assertEquals(3, VolumeMath.delta(100, VolumeMath.ACCEL_3_AFTER))
        assertEquals(6, VolumeMath.delta(200, 50))
    }

    @Test fun holding_from_zero_reaches_max_in_a_few_seconds() {
        // Remote repeat is about 20 per second: 0..100 should take roughly 2-4 s.
        var level = 0
        var repeat = 0
        while (level < 100 && repeat < 1000) {
            level = VolumeMath.clamp(level + VolumeMath.delta(100, repeat), 100)
            repeat++
        }
        assertTrue("took $repeat events", repeat in 30..90)
    }

    @Test fun clamping() {
        assertEquals(0, VolumeMath.clamp(-3, 15))
        assertEquals(15, VolumeMath.clamp(99, 15))
        assertEquals(7, VolumeMath.clamp(7, 15))
        assertEquals(0, VolumeMath.clamp(5, -1))
    }

    @Test fun up_and_down_move_and_clamp() {
        val s = VolumeState(14, 15, false)
        assertEquals(VolumeState(15, 15, false), VolumeMath.plan(s, VolumeKey.UP, 0))
        assertEquals(VolumeState(15, 15, false), VolumeMath.plan(VolumeState(15, 15, false), VolumeKey.UP, 0))
        assertEquals(VolumeState(0, 15, false), VolumeMath.plan(VolumeState(0, 15, false), VolumeKey.DOWN, 0))
        assertEquals(VolumeState(13, 15, false), VolumeMath.plan(s, VolumeKey.DOWN, 0))
    }

    @Test fun mute_toggles_once_and_ignores_repeats() {
        val on = VolumeState(30, 100, false)
        val muted = VolumeMath.plan(on, VolumeKey.MUTE, 0)
        assertEquals(VolumeState(30, 100, true), muted)
        assertEquals(on, VolumeMath.plan(muted, VolumeKey.MUTE, 0))
        assertEquals(muted, VolumeMath.plan(muted, VolumeKey.MUTE, 5))
    }

    @Test fun volume_keys_unmute() {
        val muted = VolumeState(30, 100, true)
        assertEquals(VolumeState(31, 100, false), VolumeMath.plan(muted, VolumeKey.UP, 0))
        assertEquals(VolumeState(29, 100, false), VolumeMath.plan(muted, VolumeKey.DOWN, 0))
        assertEquals(VolumeState(100, 100, false), VolumeMath.plan(VolumeState(100, 100, true), VolumeKey.UP, 0))
    }

    @Test fun limits() {
        assertTrue(VolumeMath.atLimit(VolumeState(15, 15, false), VolumeKey.UP))
        assertTrue(VolumeMath.atLimit(VolumeState(0, 15, false), VolumeKey.DOWN))
        assertFalse(VolumeMath.atLimit(VolumeState(15, 15, true), VolumeKey.UP))
        assertFalse(VolumeMath.atLimit(VolumeState(0, 15, false), VolumeKey.MUTE))
    }

    @Test fun key_mapping() {
        assertEquals(VolumeKey.UP, VolumeKey.of(24))
        assertEquals(VolumeKey.DOWN, VolumeKey.of(25))
        assertEquals(VolumeKey.MUTE, VolumeKey.of(164))
        assertNull(VolumeKey.of(91)) // KEYCODE_MUTE is the microphone
        assertNull(VolumeKey.of(4))
    }

    @Test fun fraction() {
        assertEquals(0.5f, VolumeState(50, 100, false).fraction, 0.0001f)
        assertEquals(0f, VolumeState(5, 0, false).fraction, 0.0001f)
        assertEquals(1f, VolumeState(120, 100, false).fraction, 0.0001f)
    }
}
