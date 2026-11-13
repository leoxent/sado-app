package my.sado.app.research

import my.sado.app.data.Catalog
import my.sado.app.data.Plan
import my.sado.app.data.PlanBuilder
import my.sado.app.data.PlannerAnswers
import my.sado.app.data.Progress
import my.sado.app.research.SimKit.quantiles
import java.util.Random

/**
 * Kajian 1: every plan a random crowd's quiz answers produce, checked for what the week delivers and for rules
 * that must never break (injuries, gear, a legs day without legs, the same move twice in a day).
 */
object PlanCensus {
    const val N = 100_000

    /** Minutes a session takes, by the app's own estimate (work, rest by move at the default 90 s, set-up, changes). */
    fun minutes(p: Plan, tag: String): Double = PlanBuilder.realSeconds(p.allSessions.first { it.tag == tag }.exercises) / 60.0

    fun run(seed: Long = 1): Map<String, Any> {
        val r = Random(seed)
        val perGroup = SimKit.groups.associateWith { mutableListOf<Double>() }
        val focusTotal = mutableMapOf<String, Int>()
        val focusMet = mutableMapOf<String, Int>()
        val focusNoMove = mutableMapOf<String, Int>() // focus groups no allowed move can train at all
        val cellSets = mutableMapOf<String, MutableList<Double>>()
        val cellFocus = mutableMapOf<String, IntArray>() // plans with a focus group, of which all met
        val timeRatio = mutableMapOf<Int, MutableList<Double>>()
        val movesPerSession = mutableListOf<Double>()
        val splits = mutableMapOf<String, Int>()
        val distinctMoves = mutableListOf<Double>()
        val groupDays = SimKit.groups.associateWith { mutableListOf<Double>() }
        var injuryBad = 0; var gearBad = 0; var legsBad = 0; var dupBad = 0; var emptyBad = 0; var plansWithFocus = 0
        val legsBadExamples = mutableListOf<String>()
        val moveUse = mutableMapOf<String, Int>()
        val buildMs = mutableListOf<Double>()
        repeat(N) {
            val a = SimKit.answers(r)
            val t0 = System.nanoTime()
            val plan = PlanBuilder.build(a, PlanBuilder.startingLadders(a))
            buildMs += (System.nanoTime() - t0) / 1e6
            val sets = PlanBuilder.groupSets(plan)
            SimKit.groups.forEach { g -> perGroup.getValue(g) += (sets[g] ?: 0).toDouble() }
            val days = PlanBuilder.groupDays(plan)
            SimKit.groups.forEach { g -> groupDays.getValue(g) += (days[g] ?: 0).toDouble() }
            val total = sets.values.sum().toDouble()
            val cell = "${a.days}|${a.time}"
            cellSets.getOrPut(cell) { mutableListOf() } += total
            val focus = SimKit.groups.filter { PlanBuilder.rating(a, it) == 5 }
            val short = PlanBuilder.short(a, plan)
            if (focus.isNotEmpty()) {
                plansWithFocus++
                cellFocus.getOrPut(cell) { IntArray(2) }.let { it[0]++; if (short.isEmpty()) it[1]++ }
            }
            focus.forEach { g ->
                focusTotal[g] = (focusTotal[g] ?: 0) + 1
                if (g !in short) focusMet[g] = (focusMet[g] ?: 0) + 1
                else if (Catalog.moves.none { it.group == g && PlanBuilder.allowed(it, a) }) focusNoMove[g] = (focusNoMove[g] ?: 0) + 1
            }
            splits[plan.week.filter { it != "Rest" }.distinct().joinToString(" / ")] = (splits[plan.week.filter { it != "Rest" }.distinct().joinToString(" / ")] ?: 0) + 1
            val gear = PlanBuilder.gearOf(a)
            plan.allSessions.forEach { s ->
                movesPerSession += s.exercises.size.toDouble()
                if (s.exercises.isEmpty()) emptyBad++
                timeRatio.getOrPut(a.time) { mutableListOf() } += minutes(plan, s.tag) / a.time
                if (s.exercises.map { it.id }.distinct().size < s.exercises.size) dupBad++
                val moves = s.exercises.map { Catalog.get(it.id) }
                moves.forEach { m -> moveUse[m.id] = (moveUse[m.id] ?: 0) + 1 }
                if (moves.any { m -> m.avoid.any { it in a.injury } }) injuryBad++
                if (moves.any { m -> m.need != null && m.need !in gear }) gearBad++
                if (s.tag in setOf("Legs+Push", "Legs+Core", "Lower") && PlanBuilder.rating(a, "legs") > 1 && moves.none { it.group == "legs" }) {
                    legsBad++
                    if (legsBadExamples.size < 5) legsBadExamples += "${s.label}: ${moves.joinToString { it.name }} (level ${a.level}, injury ${a.injury})"
                }
            }
            distinctMoves += plan.allSessions.flatMap { s -> s.exercises.map { it.id } }.distinct().size.toDouble()
        }
        val sessionsTotal = movesPerSession.size
        return mapOf(
            "n" to N, "sessions" to sessionsTotal, "plansWithFocus" to plansWithFocus,
            "groupSets" to SimKit.groups.associateWith { quantiles(perGroup.getValue(it)) },
            "groupDays" to SimKit.groups.associateWith { SimKit.mean(groupDays.getValue(it)) },
            "focus" to SimKit.groups.filter { (focusTotal[it] ?: 0) > 0 }.associateWith { mapOf("n" to focusTotal[it], "met" to (focusMet[it] ?: 0), "noMove" to (focusNoMove[it] ?: 0), "moves" to Catalog.moves.count { m -> m.group == it }) },
            "cells" to cellSets.mapValues { (k, v) -> mapOf("n" to v.size, "sets" to SimKit.mean(v), "focusN" to (cellFocus[k]?.get(0) ?: 0), "focusMet" to (cellFocus[k]?.get(1) ?: 0)) },
            "timeRatio" to timeRatio.mapKeys { it.key.toString() }.mapValues { (_, v) -> mapOf("q" to quantiles(v), "over" to v.count { it > 1.15 }.toDouble() / v.size, "hist" to SimKit.histogram(v, 0.0, 3.0, 30)) },
            "movesPerSession" to SimKit.histogram(movesPerSession, 0.5, 8.5, 8),
            "distinctMoves" to quantiles(distinctMoves),
            "splits" to splits.entries.sortedByDescending { it.value }.take(8).associate { it.key to it.value },
            "violations" to mapOf("injury" to injuryBad, "gear" to gearBad, "legs" to legsBad, "duplicate" to dupBad, "empty" to emptyBad),
            "legsExamples" to legsBadExamples,
            "moveUse" to moveUse.entries.sortedByDescending { it.value }.map { mapOf("id" to it.key, "name" to Catalog.get(it.key).name, "n" to it.value) },
            "unusedMoves" to Catalog.moves.filter { it.group != "warmup" && it.id !in moveUse }.map { it.name },
            "buildMs" to quantiles(buildMs),
        )
    }

    fun sample(n: Int, seed: Long): List<Pair<PlannerAnswers, Plan>> {
        val r = Random(seed)
        return List(n) { val a = SimKit.answers(r); a to PlanBuilder.build(a, PlanBuilder.startingLadders(a)) }
    }
}
