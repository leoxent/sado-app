package my.sado.app

import my.sado.app.data.Burn
import my.sado.app.data.Catalog
import my.sado.app.data.ExerciseLog
import my.sado.app.data.Integrity
import my.sado.app.data.PlanBuilder
import my.sado.app.data.PlannedExercise
import my.sado.app.data.PlannerAnswers
import my.sado.app.data.Progress
import my.sado.app.data.SetLog
import my.sado.app.data.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The changes the research run asked for: Ngam runs, session time, shape muscles, small muscles, HR and calories. */
class ResearchFixesTest {
    private val e = PlannedExercise("push", 3, "10", 10, base = "8–12")
    private fun ngam(reps: Int = 10, failed: Boolean = false) = List(3) { SetLog(reps, rir = 1, failed = failed && it == 2) }
    private fun past(n: Int) = (1..n).map { WorkoutLog(it.toLong(), "w", 20, listOf(ExerciseLog("push", ngam())), 100, false) }

    @Test fun thirdNgamInARowCountsAsEasy() {
        val boost = Progress.climb(emptyMap(), mapOf("push" to 2), listOf(e), listOf(ExerciseLog("push", ngam())), history = past(2)).boosts["push"]
        assertEquals(3, boost)
    }

    @Test fun ngamStaysWithoutTheRun() {
        // only one Ngam session before: no change
        assertEquals(2, Progress.climb(emptyMap(), mapOf("push" to 2), listOf(e), listOf(ExerciseLog("push", ngam())), history = past(1)).boosts["push"])
        // reps short of today's target: no change
        assertFalse(Progress.ngamRun("push", e, ngam(reps = 8), past(2)))
    }

    @Test fun aFailedSetLocksTheNgamRun() {
        val failedBefore = listOf(WorkoutLog(0, "w", 20, listOf(ExerciseLog("push", ngam(failed = true))), 100, false)) +
            (1..2).map { WorkoutLog(10L + it, "w", 20, listOf(ExerciseLog("push", ngam())), 100, false) }
        assertFalse(Progress.ngamRun("push", e, ngam(), failedBefore))
        // the same failure 4+ weeks back no longer locks it
        val longAgo = failedBefore.mapIndexed { i, l -> if (i == 0) l else l.copy(epochDay = 40L + i) }
        assertTrue(Progress.ngamRun("push", e, ngam(), longAgo))
    }

    @Test fun smallMovesRestLess() {
        assertEquals(60, PlanBuilder.restBetween(Catalog.get("calf"), 90))
        assertEquals(90, PlanBuilder.restBetween(Catalog.get("squat"), 90))
        assertEquals(90, PlanBuilder.restBetween(Catalog.get("nordic"), 90))
        assertEquals(45, PlanBuilder.restBetween(Catalog.get("lateral"), 60))
    }

    @Test fun builtSessionsFitTheirMinutes() {
        for (time in listOf(10, 20, 30, 45)) for (shape in listOf("bulk", "vshape", "lean")) {
            val a = PlannerAnswers(shape = listOf(shape), time = time, days = 3, equip = listOf("dumbbell"))
            val plan = PlanBuilder.build(a, PlanBuilder.startingLadders(a))
            plan.allSessions.forEach { s ->
                val secs = PlanBuilder.realSeconds(s.exercises)
                assertTrue("$shape $time min: ${secs / 60.0}", s.exercises.size <= PlanBuilder.movesPer(a) || secs <= time * 60 * 1.15)
            }
        }
    }

    @Test fun sessionFitsItsMinutes() {
        for (time in listOf(10, 20, 30, 45)) for (intensity in 0..2) {
            val a = PlannerAnswers(shape = listOf("aesthetic"), time = time, intensity = intensity)
            val n = PlanBuilder.movesPer(a)
            val minutes = PlanBuilder.sessionSeconds(a, n) / 60.0
            assertTrue("$time min, intensity $intensity: $minutes", n == 2 || minutes <= time * 1.15)
        }
        assertEquals(2, PlanBuilder.setsPer(PlannerAnswers(time = 10, intensity = 2)))
    }

    @Test fun shapeMusclesComeFirst() {
        val a = PlannerAnswers(shape = listOf("vshape"), days = 3, time = 30, pull = 1, equip = listOf("dumbbell"))
        val got = PlanBuilder.groupSets(PlanBuilder.build(a, PlanBuilder.startingLadders(a)))
        assertTrue("lats ${got["lats"]}", (got["lats"] ?: 0) >= PlanBuilder.FOCUS_SETS)
        assertTrue("side delts ${got["side_delts"]}", (got["side_delts"] ?: 0) >= PlanBuilder.FOCUS_SETS)
    }

    @Test fun legDaysTrainCalvesOrHamstrings() {
        val a = PlannerAnswers(shape = listOf("bulk"), days = 4, time = 30, level = 1)
        val plan = PlanBuilder.build(a, PlanBuilder.startingLadders(a))
        val muscles = plan.allSessions.flatMap { s -> s.exercises.flatMap { Catalog.get(it.id).muscles } }.toSet()
        assertTrue(muscles.toString(), "calves" in muscles || "hamstrings" in muscles)
    }

    @Test fun newMovesNeedNoMoreThanTheyShould() {
        assertEquals(null, Catalog.get("bottlelat").need)
        assertEquals("backpack", Catalog.get("bpshrug").need)
        assertTrue("knee" in Catalog.get("nordic").avoid)
    }

    @Test fun hrFlatUsesTheWiderGap() {
        assertTrue(Integrity.hrFlat(79, 60))
        assertFalse(Integrity.hrFlat(80, 60))
    }

    @Test fun heartRateCaloriesAreBlended() {
        assertEquals(150, Burn.blend(200, 100))
        assertEquals(200, Burn.blend(200, 0))
    }
}
