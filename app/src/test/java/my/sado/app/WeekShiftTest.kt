package my.sado.app

import my.sado.app.data.AppState
import my.sado.app.data.Plan
import my.sado.app.data.PlanSession
import my.sado.app.data.PlannedExercise
import my.sado.app.data.RestSpell
import my.sado.app.data.WeekShift
import my.sado.app.data.WeekShift.Status
import my.sado.app.data.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** A missed workout slides to the next day until a rest day takes it, twice a week at most. */
class WeekShiftTest {
    // Monday 2026-11-16
    private val mon = LocalDate.of(2026, 11, 16)
    private val m = mon.toEpochDay()
    private val week = listOf("Push", "Legs", "Rest", "Pull", "Full body", "Rest", "Rest")
    private fun pe(id: String) = PlannedExercise(id, 3, "8-12", 12)
    private val plan = Plan("t", "w", emptyList(), week, "Push day", emptyList(), week.distinct().filter { it != "Rest" }.map { PlanSession(it, it, listOf(pe("push"))) })
    private fun log(day: Long) = WorkoutLog(day, "t", 20, emptyList(), 10, false)
    private fun tags(days: List<WeekShift.Day>) = days.map { it.tag }
    private fun state(vararg done: Long) = AppState(plan = plan, logs = done.map { log(it) })

    @Test fun nothingMissedIsThePlan() {
        val w = WeekShift.weekOf(week, m, m + 2, setOf(m, m + 1))
        assertEquals(week, tags(w))
        assertEquals(listOf(Status.DONE, Status.DONE, Status.REST, Status.PLANNED), w.take(4).map { it.status })
    }

    @Test fun missedLegsTakesTheRestDay() {
        // Tuesday's Legs not done; it's Wednesday now
        val w = WeekShift.weekOf(week, m, m + 2, setOf(m))
        assertEquals(listOf("Push", "Legs", "Legs", "Pull", "Full body", "Rest", "Rest"), tags(w))
        assertEquals(Status.MOVED, w[1].status)
        assertEquals(true, w[2].shiftedIn)
        assertEquals(false, w[3].shiftedIn)
    }

    @Test fun missedMondayPushesUntilTheRestDay() {
        val w = WeekShift.weekOf(week, m, m + 1, emptySet())
        assertEquals(listOf("Push", "Push", "Legs", "Pull", "Full body", "Rest", "Rest"), tags(w))
    }

    @Test fun todayNotDoneYetDoesNotSlide() {
        val w = WeekShift.weekOf(week, m, m + 1, setOf(m))
        assertEquals(Status.PLANNED, w[1].status)
        assertEquals(week, tags(w))
    }

    @Test fun twoMissesInARowKeepTheOrder() {
        val w = WeekShift.weekOf(week, m, m + 2, emptySet())
        assertEquals(listOf("Push", "Push", "Push", "Legs", "Pull", "Full body", "Rest"), tags(w))
        assertEquals(listOf(Status.MOVED, Status.MOVED), w.take(2).map { it.status })
    }

    @Test fun onlyTwoSlidesAWeek() {
        // nothing done Mon, Tue, Wed: the third miss is just missed
        val w = WeekShift.weekOf(week, m, m + 3, emptySet())
        assertEquals(listOf(Status.MOVED, Status.MOVED, Status.MISSED), w.take(3).map { it.status })
        assertEquals("Legs", w[3].tag)
    }

    @Test fun pendingOnSundayIsDroppedAndNextWeekIsThePlan() {
        val s = state(m, m + 1, m + 3)
        // Friday missed: Full body slides to Saturday
        assertEquals("Full body", WeekShift.tagOn(s, mon.plusDays(5), mon.plusDays(5)))
        assertEquals("Push", WeekShift.tagOn(s, mon.plusDays(7), mon.plusDays(6)))
    }

    @Test fun restModeDaysDoNotSlide() {
        val s = state().copy(rest = listOf(RestSpell("sakit", m, m)))
        val w = WeekShift.week(s, m, m + 1)
        assertEquals(Status.MISSED, w[0].status)
        assertEquals("Legs", w[1].tag)
    }

    @Test fun restDaySessionLooksAheadPastSlides() {
        // Monday done, Tuesday missed: Wednesday is Legs now, so the session for Wednesday is Legs
        val s = state(m)
        assertEquals("Legs", WeekShift.sessionOn(s, mon.plusDays(2), mon.plusDays(2))?.tag)
        assertEquals(1, WeekShift.movedThisWeek(s, mon.plusDays(2)))
    }
}
