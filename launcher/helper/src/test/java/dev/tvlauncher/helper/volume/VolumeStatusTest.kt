package dev.tvlauncher.helper.volume

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeStatusTest {
    @Test fun lines() {
        assertEquals("Off: the TV shows its own volume popup", VolumeStatus.line(false, 9, 9))
        assertEquals("On. Press a volume key on the remote to try it", VolumeStatus.line(true, 0, 0))
        assertEquals("On, but this TV keeps the volume keys for itself so far", VolumeStatus.line(true, 4, 0))
        assertEquals("On", VolumeStatus.line(true, 4, 2))
    }
}
