package dev.tvlauncher.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupPlanTest {
    private fun step(steps: List<SetupStep>, id: StepId) = steps.first { it.id == id }

    /** The owner's TCL: no listener screen, no accessibility screen, stock home, nothing granted. */
    private val tcl = SetupProbe(homeChooser = false, listenerScreen = false, accessibilityScreen = false,
        helperInstalled = true, weatherInstalled = true, weatherScreen = true, getAppsInstalled = true,
        missingCompanions = listOf("Browser"), lite = true)

    @Test fun tclWithoutTheHelper_offersOnlyWhatTheTvCanDo() {
        val s = SetupPlan.steps(tcl)
        assertEquals(StepState.HELPER, step(s, StepId.HOME).state)
        assertEquals(StepState.HELPER, step(s, StepId.NOW_PLAYING).state)
        assertEquals(StepState.HELPER, step(s, StepId.SHORTCUTS).state)
        assertEquals(StepAction.REQUEST_LOCATION, step(s, StepId.NEARBY).action)
        assertEquals(StepAction.REQUEST_PICTURES, step(s, StepId.PHOTOS).action)
        assertEquals(StepAction.OPEN_WEATHER, step(s, StepId.WEATHER).action)
        assertEquals(StepAction.OPEN_GET_APPS, step(s, StepId.APPS).action)
        assertTrue(step(s, StepId.TUNED).done)
        assertTrue(step(s, StepId.TUNED).detail.startsWith("Lite"))
        // The single primary action is the first step one press away.
        assertEquals(StepId.NEARBY, SetupPlan.next(s)!!.id)
    }

    @Test fun afterProvisioning_secureWritesAreOfferedAutomatically_thenFallBack() {
        val p = tcl.copy(canWriteSecure = true)
        val s = SetupPlan.steps(p)
        assertEquals(StepAction.AUTO_ENABLE, step(s, StepId.NOW_PLAYING).action)
        assertEquals(StepAction.AUTO_ENABLE, step(s, StepId.SHORTCUTS).action)
        // Tried and still off (the TV ignored the write): no loop, the helper line instead.
        val tried = SetupPlan.steps(p, SetupMemory(autoTried = setOf(StepId.NOW_PLAYING, StepId.SHORTCUTS)))
        assertEquals(StepState.HELPER, step(tried, StepId.NOW_PLAYING).state)
        assertEquals(StepState.HELPER, step(tried, StepId.SHORTCUTS).state)
    }

    @Test fun screensAreUsedOnlyWhenTheyResolve() {
        val p = SetupProbe(homeChooser = true, listenerScreen = true, helperInstalled = true, accessibilityScreen = true,
            usageScreen = true)
        val s = SetupPlan.steps(p)
        assertEquals(StepAction.CHOOSE_HOME, step(s, StepId.HOME).action)
        assertEquals(StepAction.OPEN_LISTENER_SCREEN, step(s, StepId.NOW_PLAYING).action)
        assertEquals("Allow Now Playing", step(s, StepId.NOW_PLAYING).actionLabel)
        assertEquals(StepAction.OPEN_ACCESSIBILITY, step(s, StepId.SHORTCUTS).action)
        assertEquals(StepAction.OPEN_USAGE_SCREEN, step(s, StepId.INSIGHTS).action)
    }

    @Test fun optionalCompanionSteps_hiddenWhenTheAppIsMissing() {
        val s = SetupPlan.steps(SetupProbe(missingCompanions = listOf("Weather", "Helper")))
        assertFalse(s.any { it.id == StepId.SHORTCUTS || it.id == StepId.WEATHER })
        val apps = step(s, StepId.APPS)
        assertEquals(StepState.HELPER, apps.state)
        assertTrue(apps.detail.contains("Weather, Helper"))
    }

    @Test fun everythingDone_noPrimaryAction() {
        val p = SetupProbe(isHome = true, listenerGranted = true, helperInstalled = true, helperEnabled = true,
            locationGranted = true, picturesGranted = true, usageGranted = true)
        val s = SetupPlan.steps(p)
        assertTrue(s.all { it.done })
        assertNull(SetupPlan.next(s))
        assertTrue(SetupPlan.allDone(s))
        assertEquals("${s.size} of ${s.size} ready", SetupPlan.progress(s))
    }

    @Test fun skipped_isNotThePrimaryAction_andDoneStepsCannotBeSkipped() {
        val m = SetupMemory(skipped = setOf(StepId.NEARBY, StepId.TUNED))
        val s = SetupPlan.steps(tcl, m)
        assertEquals(StepState.SKIPPED, step(s, StepId.NEARBY).state)
        assertTrue(step(s, StepId.TUNED).done)
        assertEquals(StepId.PHOTOS, SetupPlan.next(s)!!.id)
    }

    @Test fun deniedPermissions_pointToTheTvSettings() {
        val s = SetupPlan.steps(SetupProbe(locationBlocked = true, picturesBlocked = true))
        assertEquals(StepState.HELPER, step(s, StepId.NEARBY).state)
        assertEquals(StepState.HELPER, step(s, StepId.PHOTOS).state)
    }

    @Test fun weather_onceVisitedIsTicked() {
        val p = SetupProbe(weatherInstalled = true, weatherScreen = true)
        assertTrue(step(SetupPlan.steps(p, SetupMemory(visited = setOf(StepId.WEATHER))), StepId.WEATHER).done)
    }
}
