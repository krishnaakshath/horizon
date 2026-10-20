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

    @Test fun smartHomeWords_andActions() {
        assertEquals("Connected", SmartHomeText.state(LinkContract.HA_OK))
        assertEquals("Not connected", SmartHomeText.state(null))
        assertEquals("Can't reach Home Assistant right now", SmartHomeText.state(LinkContract.HA_UNREACHABLE))
        assertEquals(LinkContract.ACTION_ACTIVATE, SmartHomeText.action(LinkContract.CONTROL_SCENE))
        assertEquals(LinkContract.ACTION_TOGGLE, SmartHomeText.action(LinkContract.CONTROL_TOGGLE))
        assertNull(SmartHomeText.action(LinkContract.CONTROL_CLIMATE))
        assertEquals("Unavailable", SmartHomeText.entitySubtitle(SmartEntity("light.a", "A", "toggle", "On", true, false)))
    }

    @Test fun linkQuickActions_hiddenByDefault_andNeedLink() {
        val d = dev.tvlauncher.spotlight.SpotlightConfig.DEFAULT
        assertTrue(QuickActions.Item.PHONE_REMOTE in d.hidden)
        assertTrue(QuickActions.Item.SMART_HOME in d.hidden)
        assertTrue(QuickActions.unavailableReason(QuickActions.Item.PHONE_REMOTE, QuickActions.Availability(emptySet())) != null)
        assertNull(QuickActions.unavailableReason(QuickActions.Item.SMART_HOME, QuickActions.Availability(setOf(QuickActions.LINK_PACKAGE))))
    }
}

class UpdateRowTextTest {
    @Test fun offByDefault_andWithoutRepo() {
        assertEquals("Off", UpdateRowText.value(null))
        assertEquals("Off", UpdateRowText.value(UpdateState(true, "", "", false)))
        assertEquals("Off", UpdateRowText.value(UpdateState(false, "owner/repo", "", false)))
        assertEquals("Update checks are switched off", UpdateRowText.subtitle(null))
    }

    @Test fun activeStates() {
        assertEquals("Not checked yet", UpdateRowText.value(UpdateState(true, "o/r", "", false)))
        assertEquals("Up to date", UpdateRowText.value(UpdateState(true, "o/r", "v1", false)))
        assertEquals("Update available", UpdateRowText.value(UpdateState(true, "o/r", "v2", true)))
        assertEquals("Version v2 is ready in H02 Store", UpdateRowText.subtitle(UpdateState(true, "o/r", "v2", true)))
    }
}
