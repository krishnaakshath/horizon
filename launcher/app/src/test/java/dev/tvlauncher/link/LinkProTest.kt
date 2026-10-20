package dev.tvlauncher.link

import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.link.core.LinkContract.Outcome
import dev.tvlauncher.ui.QuickActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkProTest {
    @Test fun backInAnotherApp_usesTheHelperOnlyWhenItIsOn() {
        assertEquals(Outcome.HELPER, LinkCommands.outsideOutcome(LinkContract.Key.BACK, helperOn = true))
        assertEquals(Outcome.NEEDS_HELPER, LinkCommands.outsideOutcome(LinkContract.Key.BACK, helperOn = false))
        assertEquals(Outcome.LAUNCHER, LinkCommands.outsideOutcome(LinkContract.Key.HOME, helperOn = false))
        assertEquals(Outcome.NEEDS_HELPER, LinkCommands.outsideOutcome(LinkContract.Key.UP, helperOn = true))
        assertEquals(Outcome.NEEDS_HELPER, LinkCommands.outsideOutcome(LinkContract.Key.DEL, helperOn = true))
    }

    @Test fun quickActions_mapToSpotlightItems() {
        assertNull(LinkCommands.quickItem(LinkContract.QUICK_SPOTLIGHT))
        assertEquals(QuickActions.Item.INPUTS, LinkCommands.quickItem("inputs"))
        assertEquals(QuickActions.Item.SETTINGS, LinkCommands.quickItem("settings"))
        assertEquals(QuickActions.Item.AMBIENT, LinkCommands.quickItem("ambient"))
        assertEquals(QuickActions.Item.FOCUS, LinkCommands.quickItem("focus"))
        assertEquals(QuickActions.Item.BEDTIME, LinkCommands.quickItem("bedtime"))
        LinkContract.QUICK_ITEMS.forEach { assertTrue(LinkCommands.quickAllowed(it)) }
        assertFalse(LinkCommands.quickAllowed("restart"))
        assertFalse(LinkCommands.quickAllowed(null))
    }

    @Test fun pickSession_prefersPlaying() {
        assertEquals(1, LinkCommands.pickSession(listOf(2, 3, 3)))
        assertEquals(0, LinkCommands.pickSession(listOf(2, 1)))
        assertEquals(1, LinkCommands.pickSession(listOf(null, 6)))
        assertEquals(-1, LinkCommands.pickSession(listOf(null, 0)))
        assertEquals(-1, LinkCommands.pickSession(emptyList()))
    }

    @Test fun seekTarget_clampsToTheTrack() {
        assertEquals(15_000L, LinkContract.seekTarget(5_000, 60_000, true))
        assertEquals(0L, LinkContract.seekTarget(5_000, 60_000, false))
        assertEquals(60_000L, LinkContract.seekTarget(55_000, 60_000, true))
        assertEquals(1_010_000L, LinkContract.seekTarget(1_000_000, 0, true))
    }

    @Test fun newKeys_haveWireNames() {
        assertEquals(LinkContract.Key.DEL, LinkContract.Key.fromWire("del"))
        assertEquals(67, LinkContract.Key.DEL.keyCode)
        assertEquals(LinkContract.Key.OK_LONG, LinkContract.Key.fromWire("ok_long"))
        assertEquals(23, LinkContract.Key.OK_LONG.keyCode)
        assertTrue(LinkContract.MEDIA_ACTIONS.containsAll(listOf("play_pause", "next", "prev", "forward", "rewind")))
    }
}
