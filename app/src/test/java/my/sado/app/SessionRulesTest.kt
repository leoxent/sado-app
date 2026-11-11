package my.sado.app

import my.sado.app.data.AppState
import my.sado.app.data.Burn
import my.sado.app.data.ExerciseLog
import my.sado.app.data.GiveUp
import my.sado.app.data.HonestRest
import my.sado.app.data.Integrity
import my.sado.app.data.LadderProgress
import my.sado.app.data.PlannedExercise
import my.sado.app.data.PlannerAnswers
import my.sado.app.data.Profile
import my.sado.app.data.Progress
import my.sado.app.data.SetLog
import my.sado.app.data.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GiveUpTest {
    private val push = PlannedExercise("push", 3, "10", 10, base = "8–12")

    @Test fun shortOfTheRangeLowersTheNextSetAndRestsLonger() {
        assertEquals(GiveUp.Next(6, 30), GiveUp.after(push, 10, 6)) // drops by the shortfall
        assertEquals(GiveUp.Next(6, 30), GiveUp.after(push, 10, 3)) // never below the bottom minus 2
        assertNull(GiveUp.after(push, 10, 9)) // inside the range: target stays
    }

    @Test fun twoFailedSetsInARowOfferTheRungBelow() {
        val f = SetLog(5, failed = true)
        assertTrue(GiveUp.offerDrop(listOf(f, f), 3))
        assertFalse(GiveUp.offerDrop(listOf(f, f), 2)) // no sets left
        assertFalse(GiveUp.offerDrop(listOf(f, SetLog(8)), 3))
        assertEquals("kneepush", GiveUp.rungBelow("push", PlannerAnswers()))
        assertNull(GiveUp.rungBelow("kneepush", PlannerAnswers()))
    }

    @Test fun aFailedSetCountsAsNoRepsInReserve() {
        val log = ExerciseLog("push", listOf(SetLog(10, 4), SetLog(10, 4), SetLog(6, 4, failed = true)))
        val c = Progress.climb(mapOf("push" to LadderProgress(1)), mapOf("push" to 2), listOf(push), listOf(log))
        assertEquals(1, c.boosts["push"]) // Susah: −1, never the +2 the answer asked for
    }

    @Test fun failingTwoSessionsRunningDeloadsTheMove() {
        val failed = ExerciseLog("push", listOf(SetLog(10), SetLog(5, failed = true)))
        val ok = ExerciseLog("push", listOf(SetLog(10), SetLog(10)))
        val before = listOf(WorkoutLog(1, "A", 20, listOf(failed), 60, false))
        assertEquals(setOf("push"), GiveUp.nextDeload(emptySet(), listOf(failed), before))
        assertEquals(emptySet<String>(), GiveUp.nextDeload(emptySet(), listOf(failed), emptyList()))
        assertEquals(emptySet<String>(), GiveUp.nextDeload(setOf("push"), listOf(ok), before)) // done fine: off the list
    }
}

class IntegrityTest {
    private fun flag(vararg sets: SetLog) = Integrity.flag(listOf(ExerciseLog("push", sets.toList()))) { null }.first().sets.map { it.flagged }

    @Test fun aSetFasterThanFortyPercentOfTwoSecondsARepIsFlagged() {
        // 10 reps: plausible 20 s, flagged under 8 s
        assertEquals(listOf(true, false), flag(SetLog(10, secs = 5, at = 100), SetLog(10, secs = 12, at = 300)))
    }

    @Test fun timedSetsUseTheirDuration() {
        val out = Integrity.flag(listOf(ExerciseLog("plank", listOf(SetLog(30, secs = 5), SetLog(30, secs = 25))))) { 30 }
        assertEquals(listOf(true, false), out.first().sets.map { it.flagged })
    }

    @Test fun threeSkippedRestsInARowAreFlagged() {
        val s = SetLog(10, secs = 30, restSkipped = true)
        assertEquals(listOf(true, true, true, false), flag(s.copy(at = 0), s.copy(at = 200), s.copy(at = 400), SetLog(10, secs = 30, at = 600)))
        assertEquals(listOf(false, false, false), flag(s.copy(at = 0), s.copy(at = 200), SetLog(10, secs = 30, at = 400)))
    }

    @Test fun fiveSetsInsideAMinuteAreABurst() {
        val burst = (0 until 5).map { SetLog(4, secs = 10, at = it * 10) }.toTypedArray()
        assertTrue(flag(*burst).all { it })
        val spread = (0 until 5).map { SetLog(4, secs = 10, at = it * 30) }.toTypedArray()
        assertTrue(flag(*spread).none { it })
    }

    @Test fun verdictIsDoubtfulOverHalfOrWithFlatHeartRate() {
        val logs = listOf(ExerciseLog("push", listOf(SetLog(10, flagged = true), SetLog(10, flagged = true), SetLog(10))))
        assertTrue(Integrity.verdict(logs, null, null).doubtful)
        val fine = listOf(ExerciseLog("push", listOf(SetLog(10, flagged = true), SetLog(10), SetLog(10))))
        assertFalse(Integrity.verdict(fine, 130, 60).doubtful)
        assertTrue(Integrity.verdict(fine, 70, 60).doubtful) // HR barely above resting
    }

    @Test fun flaggedSetsNeverMoveTheLadderOrVolume() {
        val push = PlannedExercise("push", 3, "10", 10, base = "8–12")
        val log = ExerciseLog("push", listOf(SetLog(10, 4), SetLog(10, 4, flagged = true), SetLog(10, 4)))
        val c = Progress.climb(mapOf("push" to LadderProgress(1)), mapOf("push" to 2), listOf(push), listOf(log))
        assertEquals(2, c.boosts["push"])
        val s = AppState(logs = listOf(WorkoutLog(LocalDate.now().toEpochDay(), "A", 20, listOf(log), 30, false)))
        assertEquals(2, Progress.weeklySets(s).getValue("Dada"))
    }
}

class HonestRestTest {
    private val monday = LocalDate.of(2026, 11, 9).toEpochDay()

    @Test fun twoAWeekAndNotOnATrainedDay() {
        var s = AppState()
        s = HonestRest.take(s, monday)
        s = HonestRest.take(s, monday + 1)
        assertEquals(0, HonestRest.left(s, monday + 2))
        assertFalse(HonestRest.canTake(s, monday + 2))
        assertTrue(HonestRest.canTake(s, monday + 7)) // a new week
        val trained = AppState(logs = listOf(WorkoutLog(monday, "A", 20, emptyList(), 30, false)))
        assertFalse(HonestRest.canTake(trained, monday))
    }

    @Test fun restDaysKeepTheStreak() {
        // week 1: two workouts; week 2: one workout and one honest rest; today in week 3
        val logs = listOf(monday, monday + 2, monday + 7).map { WorkoutLog(it, "A", 20, emptyList(), 30, false) }
        val today = LocalDate.ofEpochDay(monday + 15)
        val broken = Progress.streak(AppState(logs = logs), today).days
        val kept = Progress.streak(AppState(logs = logs, honestRests = listOf(monday + 9)), today).days
        assertEquals(0, broken) // week 2 had one day: the run broke and nothing came after
        assertEquals(16, kept)
    }
}

class FallbackProgressTest {
    private val push = PlannedExercise("push", 2, "12", 12, base = "8–12")
    private fun top(rir: Int? = null) = ExerciseLog("push", listOf(SetLog(12, rir), SetLog(12, rir)))

    @Test fun twoSessionsAtTheTopWithoutRirStillProgress() {
        val before = listOf(WorkoutLog(1, "A", 20, listOf(top()), 60, false))
        val c = Progress.climb(mapOf("push" to LadderProgress(1)), mapOf("push" to 4), listOf(push), listOf(top()), history = before)
        assertEquals(2, c.ladders.getValue("push").rung) // at the top of 8–12: climbs to decline
        val once = Progress.climb(mapOf("push" to LadderProgress(1)), mapOf("push" to 4), listOf(push), listOf(top()))
        assertEquals(1, once.ladders.getValue("push").rung) // one session is not enough
    }
}

class BurnTest {
    @Test fun metFromTheShareOfCardio() {
        assertEquals(3.5, Burn.met(listOf(ExerciseLog("push", listOf(SetLog(10))))), 0.001)
        assertEquals(5.75, Burn.met(listOf(ExerciseLog("push", listOf(SetLog(10))), ExerciseLog("climber", listOf(SetLog(40))))), 0.001)
        assertEquals(96, Burn.metKcal(3.5, 55f, 30 * 60)) // 3.5 × 55 kg × 0.5 h
    }

    @Test fun watchHeartRateWins() {
        val p = Profile(sex = 0, age = 25, weightKg = 55f)
        assertEquals("HR jam", Burn.estimate(emptyList(), p, 1800, 140).second)
        assertEquals("MET", Burn.estimate(emptyList(), p, 1800, null).second)
    }
}
