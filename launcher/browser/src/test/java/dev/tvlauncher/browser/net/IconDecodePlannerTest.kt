package dev.tvlauncher.browser.net

import dev.tvlauncher.browser.net.IconDecodePlanner.Plan
import org.junit.Assert.assertEquals
import org.junit.Test

class IconDecodePlannerTest {
    @Test fun tall100x60000_rejected() = assertEquals(Plan.Reject, IconDecodePlanner.plan(100, 60000))
    @Test fun wide60000x100_rejected() = assertEquals(Plan.Reject, IconDecodePlanner.plan(60000, 100))
    @Test fun aspect4to1_allowed_5to1_rejected() {
        assertEquals(Plan.Sample(1), IconDecodePlanner.plan(200, 50))
        assertEquals(Plan.Reject, IconDecodePlanner.plan(250, 50))
    }
    @Test fun sample4096x4096_sampled() = assertEquals(Plan.Sample(16), IconDecodePlanner.plan(4096, 4096))
    @Test fun sample64x64_sample1() = assertEquals(Plan.Sample(1), IconDecodePlanner.plan(64, 64))
    @Test fun sample_usesLongestEdge() = assertEquals(Plan.Sample(4), IconDecodePlanner.plan(300, 1024))
    @Test fun zeroDims_rejected() {
        assertEquals(Plan.Reject, IconDecodePlanner.plan(0, 0))
        assertEquals(Plan.Reject, IconDecodePlanner.plan(-1, 64))
        assertEquals(Plan.Reject, IconDecodePlanner.plan(64, 0))
    }
    @Test fun overflow65535x65535_rejectedWithoutOverflow() = assertEquals(Plan.Reject, IconDecodePlanner.plan(65535, 65535))
    @Test fun tooSmall_rejected() = assertEquals(Plan.Reject, IconDecodePlanner.plan(8, 8))

    @Test fun fit_capsAt96_keepingAspect() {
        assertEquals(96 to 48, IconDecodePlanner.fit(256, 128, 96))
        assertEquals(24 to 96, IconDecodePlanner.fit(64, 256, 96))
        assertEquals(64 to 64, IconDecodePlanner.fit(64, 64, 96))
    }
}
