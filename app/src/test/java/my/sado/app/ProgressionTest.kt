package my.sado.app

import my.sado.app.data.ExerciseLog
import my.sado.app.data.LadderProgress
import my.sado.app.data.PlanBuilder
import my.sado.app.data.PlannedExercise
import my.sado.app.data.PlannerAnswers
import my.sado.app.data.Progress
import my.sado.app.data.SetLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Double progression: the target climbs one rep per Ngam (two per Easy) to the top of the range, then the ladder moves up.
class ProgressionTest {
    private fun log(id: String, rir: Int, sets: Int = 3) = ExerciseLog(id, List(sets) { SetLog(10, rir) })
    private fun session(id: String, reps: String, boosts: Map<String, Int>) =
        Progress.boosted(PlannedExercise(id, 3, reps, 12), boosts)

    @Test fun startsAtTheBottomOfTheRange() {
        val e = session("push", "8–12", emptyMap())
        assertEquals("8", e.reps)
        assertEquals(8, e.target)
        assertEquals("8–12", e.base)
    }

    @Test fun effortMovesTheTarget() {
        val ladders = mapOf("push" to LadderProgress(1))
        val e = session("push", "8–12", mapOf("push" to 2))
        fun after(rir: Int, sets: Int = 3) = Progress.climb(ladders, mapOf("push" to 2), listOf(e), listOf(log("push", rir, sets))).boosts["push"]
        assertEquals(4, after(4)) // sangat easy +2
        assertEquals(3, after(3)) // easy +1
        assertEquals(2, after(1)) // ngam keeps
        assertEquals(1, after(0)) // susah -1
        assertEquals(2, after(3, sets = 2)) // a set short: no change
        // susah at the bottom goes below the range, down to half of it
        assertEquals("4", session("push", "8–12", mapOf("push" to -99)).reps)
    }

    @Test fun extraSetsApplyAndKeepTheBase() {
        val e = Progress.boosted(PlannedExercise("push", 3, "8–12", 12), emptyMap(), mapOf("push" to 2))
        assertEquals(5, e.sets)
        assertEquals(3, e.baseSets)
    }

    @Test fun topOfRangeClimbsTheLadderAndResets() {
        val ladders = mapOf("push" to LadderProgress(1))
        val atTop = mapOf("push" to 4)
        val e = session("push", "8–12", atTop)
        assertEquals("12", e.reps)
        assertEquals(1, Progress.climb(ladders, atTop, listOf(e), listOf(log("push", 1))).ladders.getValue("push").rung) // ngam at the top stays
        val c = Progress.climb(ladders, atTop, listOf(e), listOf(log("push", 3)))
        assertEquals(2, c.ladders.getValue("push").rung)
        assertEquals("8", session("decline", "8–12", c.boosts).reps)
    }

    @Test fun lastRungAndTimedKeepGoingToACap() {
        // one-arm push-up has nothing above it: 8–12 runs on to 18
        assertEquals("18", session("onearm", "8–12", mapOf("onearm" to 99)).reps)
        // plank 30s climbs 5s a step, at most double
        assertEquals("35s", session("plank", "30s", mapOf("plank" to 1)).reps)
        assertEquals("60s", session("plank", "30s", mapOf("plank" to 99)).reps)
    }

    @Test fun maxRepMovesKeepTheOldLadderRule() {
        val e = Progress.boosted(PlannedExercise("negative", 3, "5 rep turun slow", 5), emptyMap())
        assertNull(e.base)
        val c = Progress.climb(mapOf("pull" to LadderProgress(1)), emptyMap(), listOf(e), listOf(log("negative", 3)))
        assertEquals(2, c.ladders.getValue("pull").rung)
    }

    // the bug: the saved plan kept the old move after a level-up, so the ladder never moved again
    @Test fun savedPlanFollowsTheLadder() {
        val a = PlannerAnswers(shape = listOf("bulk"), level = 1, intensity = 1, equip = listOf("chair"))
        val plan = listOf(PlannedExercise("push", 3, "6–10", 10))
        val out = PlanBuilder.refresh(plan, a, mapOf("push" to LadderProgress(2)))
        assertEquals("decline", out.single().id)
    }
}
