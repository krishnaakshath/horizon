package dev.tvlauncher.routines

import dev.tvlauncher.routines.RoutinePlan.Facts
import dev.tvlauncher.routines.RoutinePlan.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineRunnerTest {
    private val on = Facts(lateMs = 0, screenOn = true, mayStartScreens = true)

    @Test fun ambientRunsOnTimeWithTheScreenOn() {
        assertEquals(Outcome.RAN, RoutinePlan.decide(RoutineAction.Ambient, on))
    }

    @Test fun onScreenActionsSkipInStandbyOrWhenLate() {
        assertEquals(Outcome.SKIP_STANDBY, RoutinePlan.decide(RoutineAction.Ambient, on.copy(screenOn = false)))
        assertEquals(Outcome.SKIP_STANDBY, RoutinePlan.decide(RoutineAction.Launch("a.b", "A"), on.copy(screenOn = false)))
        assertEquals(Outcome.SKIP_LATE, RoutinePlan.decide(RoutineAction.Ambient, on.copy(lateMs = 60 * 60_000L)))
        assertEquals("Skipped (TV was off)", Outcome.SKIP_STANDBY.label)
    }

    @Test fun settingsActionsRunEvenInStandbyAndLate() {
        val off = on.copy(screenOn = false, lateMs = 3 * 60 * 60_000L, mayStartScreens = false)
        for (a in listOf(RoutineAction.BedtimeOn, RoutineAction.BedtimeOff, RoutineAction.Room("CINEMA"),
            RoutineAction.Focus(true), RoutineAction.Focus(false), RoutineAction.Profile("p1"))) {
            assertEquals(a.kind.name, Outcome.RAN, RoutinePlan.decide(a, off))
        }
    }

    @Test fun onScreenActionsNeedTheRightToStartScreens() {
        val blocked = on.copy(mayStartScreens = false)
        assertEquals(Outcome.SKIP_BLOCKED, RoutinePlan.decide(RoutineAction.Ambient, blocked))
        assertEquals(Outcome.SKIP_BLOCKED, RoutinePlan.decide(RoutineAction.Launch("a.b", "A"), blocked))
        assertTrue(RoutinePlan.mayStart(28, overlayAllowed = false, inFront = false))
        assertFalse(RoutinePlan.mayStart(29, overlayAllowed = false, inFront = false))
        assertTrue(RoutinePlan.mayStart(30, overlayAllowed = true, inFront = false))
        assertTrue(RoutinePlan.mayStart(30, overlayAllowed = false, inFront = true))
    }

    @Test fun missingTargetsAndLockedProfilesSkip() {
        assertEquals(Outcome.SKIP_MISSING, RoutinePlan.decide(RoutineAction.Launch("gone.app", "Gone"), on.copy(targetExists = false)))
        assertEquals(Outcome.SKIP_MISSING, RoutinePlan.decide(RoutineAction.Profile("p9"), on.copy(targetExists = false)))
        assertEquals(Outcome.SKIP_LOCKED, RoutinePlan.decide(RoutineAction.Profile("p2"), on.copy(targetLocked = true)))
    }

    @Test fun lastRunLineAndItsStorageForm() {
        assertEquals("Not run yet", RoutinePlan.lastLine(null) { "x" })
        val l = RoutineStore.LastRun(1_700_000_000_000L, Outcome.SKIP_STANDBY)
        assertEquals("Last run Mon 07:00 · Skipped (TV was off)", RoutinePlan.lastLine(l) { "Mon 07:00" })
        assertEquals(l, RoutineStore.decodeLast(RoutineStore.encodeLast(l)))
        assertNull(RoutineStore.decodeLast("garbage"))
        assertNull(RoutineStore.decodeLast("12|NOPE"))
        assertNull(RoutineStore.decodeLast(null))
    }

    @Test fun exactAlarmsOnlyWhereAllowed() {
        assertTrue(RoutineScheduler.exactAllowed(30, canScheduleExact = false))
        assertFalse(RoutineScheduler.exactAllowed(31, canScheduleExact = false))
        assertTrue(RoutineScheduler.exactAllowed(33, canScheduleExact = true))
    }

    @Test fun pagesStayWithinTheRowBudget() {
        assertTrue(RoutinesPage.LIST_SPEC.withinBudget)
        assertTrue(RoutinesPage.EDIT_SPEC.withinBudget)
        assertTrue(RoutinesSection.spec.withinBudget)
    }

    @Test fun everyOfferedActionHasAFreshForm() {
        for (k in RoutinesPage.KINDS) {
            val a = RoutinesPage.actionFor(k, "p1", "a.b" to "App")
            assertEquals(k, a!!.kind)
        }
        assertNull(RoutinesPage.actionFor(RoutineAction.Kind.PROFILE, null, null))
        assertNull(RoutinesPage.actionFor(RoutineAction.Kind.LAUNCH, null, null))
        assertEquals(1, RoutinesPage.daysIndex(RoutineModel.WEEKDAYS))
        assertEquals(0, RoutinesPage.daysIndex(setOf(0, 2)))
    }

    @Test fun bedtimeOffSurvivesJson() {
        val r = Routine(1, true, 23 * 60, RoutineModel.EVERY_DAY, RoutineAction.BedtimeOff)
        assertEquals(listOf(r), RoutineModel.fromJson(RoutineModel.toJson(listOf(r))))
    }
}
