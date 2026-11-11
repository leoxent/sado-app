package my.sado.app

import my.sado.app.data.Catalog
import my.sado.app.data.ExerciseLog
import my.sado.app.data.LadderProgress
import my.sado.app.data.PlanBuilder
import my.sado.app.data.PlannedExercise
import my.sado.app.data.PlannerAnswers
import my.sado.app.data.Progress
import my.sado.app.data.SetLog
import org.junit.Assert.assertEquals
import org.junit.Test

// Longer ladders: archer and weighted pull-ups, weighted push-ups and pistols that progress by kg.
class LadderTest {
    private val bar = PlannerAnswers(level = 2, pull = 2)
    private val barBag = bar.copy(equip = listOf("backpack"))

    @Test fun laddersReachTheWeightedRungs() {
        assertEquals(listOf("invrow", "negative", "pullup", "archerpull", "wpullup"), Catalog.ladders["pull"])
        assertEquals("wpush", Catalog.ladders.getValue("push").last())
        assertEquals("wpistol", Catalog.ladders.getValue("squat").last())
    }

    @Test fun anUnusableRungFallsBackToTheHighestUsable() {
        val top = mapOf("pull" to LadderProgress(4))
        assertEquals("wpullup", PlanBuilder.progressed("pullup", barBag, top))
        assertEquals("archerpull", PlanBuilder.progressed("pullup", bar, top)) // no backpack: not back to pull-up
    }

    @Test fun noClimbOntoARungTheGearRulesOut() {
        val e = PlannedExercise("archerpull", 3, "6", 6, base = "3–6")
        val log = ExerciseLog("archerpull", List(3) { SetLog(6, 3) })
        val boosts = mapOf("archerpull" to 3)
        val ladders = mapOf("pull" to LadderProgress(3))
        assertEquals(3, Progress.climb(ladders, boosts, listOf(e), listOf(log), usable = { PlanBuilder.usable(it, bar) }).ladders.getValue("pull").rung)
        assertEquals(4, Progress.climb(ladders, boosts, listOf(e), listOf(log), usable = { PlanBuilder.usable(it, barBag) }).ladders.getValue("pull").rung)
    }

    @Test fun weightedMovesAddKgAtTheTopOfTheRange() {
        val e = PlannedExercise("wpullup", 3, "8", 8, base = "5–8")
        val top = Progress.climb(emptyMap(), mapOf("wpullup" to 3), listOf(e), listOf(ExerciseLog("wpullup", List(3) { SetLog(8, 3, kg = 5f) })), mapOf("wpullup" to 5f))
        assertEquals(7.5f, top.loads.getValue("wpullup"))
        assertEquals(null, top.boosts["wpullup"]) // starts again at the bottom of the range
        // not at the top yet: reps climb, the load stays what was carried
        val mid = Progress.climb(emptyMap(), mapOf("wpullup" to 1), listOf(e), listOf(ExerciseLog("wpullup", List(3) { SetLog(6, 3, kg = 5f) })), mapOf("wpullup" to 2.5f))
        assertEquals(5f, mid.loads.getValue("wpullup"))
        assertEquals(2, mid.boosts.getValue("wpullup"))
    }
}
