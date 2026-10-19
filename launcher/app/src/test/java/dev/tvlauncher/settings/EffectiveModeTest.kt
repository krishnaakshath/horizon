package dev.tvlauncher.settings

import dev.tvlauncher.device.EffectsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffectiveModeTest {
    @Test
    fun effectiveMode_off_returnsNull() {
        assertNull(EffectiveMode.resolve(AnimationLevel.OFF, EffectsMode.FULL))
        assertNull(EffectiveMode.resolve(AnimationLevel.OFF, EffectsMode.LITE))
    }

    @Test
    fun effectiveMode_auto_followsDevice() {
        assertEquals(EffectsMode.FULL, EffectiveMode.resolve(AnimationLevel.AUTO, EffectsMode.FULL))
        assertEquals(EffectsMode.LITE, EffectiveMode.resolve(AnimationLevel.AUTO, EffectsMode.LITE))
    }

    @Test
    fun effectiveMode_lite_overridesFullDevice() {
        assertEquals(EffectsMode.LITE, EffectiveMode.resolve(AnimationLevel.LITE, EffectsMode.FULL))
    }

    @Test
    fun effectiveMode_full_overridesLiteDevice() {
        assertEquals(EffectsMode.FULL, EffectiveMode.resolve(AnimationLevel.FULL, EffectsMode.LITE))
    }

    @Test
    fun effectiveMode_systemAnimationsOff_returnsNull() {
        assertNull(EffectiveMode.resolve(AnimationLevel.FULL, EffectsMode.FULL, systemAnimatorScale = 0f))
        assertEquals(EffectsMode.FULL, EffectiveMode.resolve(AnimationLevel.AUTO, EffectsMode.FULL, 1f))
    }
}
