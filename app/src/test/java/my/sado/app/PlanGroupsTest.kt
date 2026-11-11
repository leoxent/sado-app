package my.sado.app

import my.sado.app.data.Catalog
import my.sado.app.data.PlanBuilder
import my.sado.app.data.PlannerAnswers
import my.sado.app.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Shoulders split into front, side and rear; lats count real pulls only.
class PlanGroupsTest {
    private val pulls = setOf("pullup", "chinup", "negative", "invrow", "dbrow", "dbrow2", "bprow", "bandpull", "towelrow", "doorrow", "archerpull", "wpullup")
    private fun vshape(equip: List<String>, pull: Int = -1) =
        PlannerAnswers(shape = listOf("vshape"), level = 1, pull = pull, days = 3, time = 20, equip = equip, intensity = 1)

    @Test fun noMoveIsStillPlainDelts() {
        assertTrue(Catalog.moves.none { it.group == "delts" })
        Catalog.moves.filter { it.group != "warmup" }.forEach { assertTrue(it.id, it.group in PlanBuilder.groupNames) }
    }

    @Test fun latsHoldOnlyPulls() {
        Catalog.moves.filter { it.group == "lats" }.forEach { assertTrue(it.id, it.id in pulls) }
        assertEquals("lower_back", Catalog.get("superman").group)
        assertEquals("traps", Catalog.get("shrug").group)
        assertEquals("rear_delts", Catalog.get("snow").group)
    }

    @Test fun planLatsEqualPullingSets() {
        listOf(emptyList(), listOf("dumbbell"), listOf("band", "chair")).forEach { equip ->
            val a = vshape(equip)
            val plan = PlanBuilder.build(a, PlanBuilder.startingLadders(a))
            val pullSets = plan.allSessions.sumOf { s -> s.exercises.filter { it.id in pulls && Catalog.get(it.id).group == "lats" }.sumOf { it.sets } * plan.daysOf(s).size }
            assertEquals(equip.toString(), pullSets, PlanBuilder.groupSets(plan)["lats"] ?: 0)
        }
    }

    @Test fun vshapeWithWeightsGetsSideDeltsOfTheirOwn() {
        listOf(listOf("dumbbell"), listOf("band")).forEach { equip ->
            val a = vshape(equip)
            val sets = PlanBuilder.groupSets(PlanBuilder.build(a, PlanBuilder.startingLadders(a)))
            assertTrue("$equip: $sets", (sets["side_delts"] ?: 0) >= 8)
            assertTrue("$equip: $sets", (sets["side_delts"] ?: 0) > (sets["front_delts"] ?: 0))
        }
    }

    @Test fun oldDeltsRatingCarriesOver() {
        val a = Store.splitDelts(PlannerAnswers(ratings = mapOf("delts" to 5, "chest" to 2), priority = listOf("delts")))
        assertEquals(mapOf("chest" to 2, "front_delts" to 5, "side_delts" to 5, "rear_delts" to 5), a.ratings)
        assertEquals(listOf("front_delts", "side_delts"), a.priority)
    }
}

class LeanDoseTest {
    @Test fun leanKeepsStrengthRepBased() {
        val a = PlannerAnswers(shape = listOf("lean"), level = 1, days = 3, time = 30, intensity = 1)
        assertEquals("12–15", PlanBuilder.dose(Catalog.get("squat"), a).second)
        assertEquals("40s work, 20s rest", PlanBuilder.dose(Catalog.get("climber"), a).second)
        assertEquals("30s", PlanBuilder.dose(Catalog.get("plank"), a).second)
    }
}
