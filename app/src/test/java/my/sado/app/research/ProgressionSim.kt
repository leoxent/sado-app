package my.sado.app.research

import my.sado.app.data.AppState
import my.sado.app.data.Catalog
import my.sado.app.data.ExerciseLog
import my.sado.app.data.ExtraSession
import my.sado.app.data.GiveUp
import my.sado.app.data.Integrity
import my.sado.app.data.LadderProgress
import my.sado.app.data.Plan
import my.sado.app.data.PlanBuilder
import my.sado.app.data.PlannedExercise
import my.sado.app.data.PlannerAnswers
import my.sado.app.data.Progress
import my.sado.app.data.SetLog
import my.sado.app.data.WorkoutLog
import java.time.LocalDate
import java.util.Random
import kotlin.math.floor
import kotlin.math.pow

/**
 * Kajian 2: virtual users with a hidden capacity per move train for 24 weeks through the app's real progression
 * (refresh, boosted, climb, nextDeload, deload weeks). Same users, same luck, in five arms: how they answer the effort
 * question, and whether they add Sesi tambahan on rest days.
 */
object ProgressionSim {
    const val USERS = 4_000
    const val WEEKS = 24
    val arms = listOf("jujur", "optimis", "pesimis", "selalu Ngam", "tekan butang atas", "jujur + sesi tambahan")
    private const val RUNG_FACTOR = 0.6 // the next rung of a ladder is this much of the one below
    private const val SET_FATIGUE = 0.07 // each set after the first loses this share
    private const val KG_COST = 0.92 // capacity kept per 2.5 kg added

    private class User(
        val a: PlannerAnswers, val plan: Plan, val m: Double, val rate: Double, val attend: Double, val seed: Long,
    )

    private class Acc {
        val sets = IntArray(WEEKS); val failed = IntArray(WEEKS); val sessions = IntArray(WEEKS); val failSessions = IntArray(WEEKS)
        val ratio = DoubleArray(WEEKS); val ratioN = IntArray(WEEKS)
        val rir = Array(WEEKS) { IntArray(6) } // true reps in reserve on the last set: 0..4, 5+
        val climbs = IntArray(WEEKS); val deloads = IntArray(WEEKS); val extras = IntArray(WEEKS)
        val gain = Array(WEEKS) { mutableListOf<Double>() }
        val rungs = mutableListOf<Double>()
        val kgUps = IntArray(WEEKS)
        val tooEasy = IntArray(WEEKS) // first set target at or under 60% of capacity
        val tooHard = IntArray(WEEKS) // first set target above capacity
        val firstSets = IntArray(WEEKS)
    }

    private fun effort(rir: Int) = when { rir <= 0 -> 0; rir <= 2 -> 1; rir == 3 -> 3; else -> 4 }
    private val answers = listOf(0, 1, 3, 4)

    fun run(seed: Long = 2): Map<String, Any> {
        val r = Random(seed)
        val users = List(USERS) {
            var a: PlannerAnswers; var plan: Plan
            do { a = SimKit.answers(r); plan = PlanBuilder.build(a, PlanBuilder.startingLadders(a)) } while (plan.allSessions.none { s -> s.exercises.any { Catalog.ladderOf(it.id) == "push" } })
            User(a, plan, SimKit.logNormal(r, 1.35, 0.2), SimKit.logNormal(r, 0.03, 0.35), SimKit.beta(r, 7.0, 2.0), r.nextLong())
        }
        val accs = arms.associateWith { Acc() }
        arms.forEach { arm -> users.forEach { simulate(it, arm, accs.getValue(arm)) } }
        return mapOf(
            "users" to USERS, "weeks" to WEEKS, "arms" to arms,
            "byArm" to accs.mapValues { (_, a) ->
                mapOf(
                    "failRate" to (0 until WEEKS).map { w -> if (a.sets[w] == 0) 0.0 else a.failed[w].toDouble() / a.sets[w] },
                    "failSessionRate" to (0 until WEEKS).map { w -> if (a.sessions[w] == 0) 0.0 else a.failSessions[w].toDouble() / a.sessions[w] },
                    "ratio" to (0 until WEEKS).map { w -> if (a.ratioN[w] == 0) 0.0 else a.ratio[w] / a.ratioN[w] },
                    "tooEasy" to (0 until WEEKS).map { w -> if (a.firstSets[w] == 0) 0.0 else a.tooEasy[w].toDouble() / a.firstSets[w] },
                    "tooHard" to (0 until WEEKS).map { w -> if (a.firstSets[w] == 0) 0.0 else a.tooHard[w].toDouble() / a.firstSets[w] },
                    "rir" to (0 until WEEKS).map { w -> a.rir[w].toList() },
                    "climbs" to a.climbs.toList(), "kgUps" to a.kgUps.toList(), "deloads" to a.deloads.toList(), "extras" to a.extras.toList(),
                    "sessions" to a.sessions.toList(),
                    "gain" to (0 until WEEKS).map { w -> SimKit.quantiles(a.gain[w], listOf(0.1, 0.25, 0.5, 0.75, 0.9)) },
                    "rungs" to SimKit.histogram(a.rungs, -0.5, 5.5, 6),
                    "totals" to mapOf(
                        "sets" to a.sets.sum(), "failRate" to a.failed.sum().toDouble() / a.sets.sum().coerceAtLeast(1),
                        "tooHard" to a.tooHard.sum().toDouble() / a.firstSets.sum().coerceAtLeast(1),
                        "tooEasy" to a.tooEasy.sum().toDouble() / a.firstSets.sum().coerceAtLeast(1),
                        "gainMedian" to SimKit.quantiles(a.gain[WEEKS - 1])[2], "rungsMean" to SimKit.mean(a.rungs),
                        "deloadsPerUser" to a.deloads.sum().toDouble() / USERS, "climbsPerUser" to a.climbs.sum().toDouble() / USERS,
                        "extrasPerUser" to a.extras.sum().toDouble() / USERS,
                    ),
                )
            },
        )
    }

    private fun simulate(u: User, arm: String, acc: Acc) {
        val r = Random(u.seed) // the same luck in every arm
        val a = u.a; val plan = u.plan
        val start = LocalDate.of(2026, 1, 5) // a Monday
        var ladders: Map<String, LadderProgress> = PlanBuilder.startingLadders(a)
        var boosts = mapOf<String, Int>(); var loads = mapOf<String, Float>(); var deload = setOf<String>()
        val logs = mutableListOf<WorkoutLog>()
        val cap = mutableMapOf<String, Double>() // fresh capacity: reps (or seconds) at 0 in reserve on a first set
        val capMax = mutableMapOf<String, Double>()
        val lastTrained = mutableMapOf<String, Long>() // muscle to day
        val state0 = AppState(planStart = start.toEpochDay())
        val pushStart = mutableListOf<Double>()

        fun capOf(e: PlannedExercise): Double = cap.getOrPut(e.id) {
            val key = Catalog.ladderOf(e.id)
            val rungs = key?.let { Catalog.ladders.getValue(it) }
            val i = rungs?.indexOf(e.id) ?: -1
            val below = rungs?.let { (i - 1 downTo 0).firstOrNull { j -> cap.containsKey(it[j]) } }
            val c = if (below != null) cap.getValue(rungs[below]) * RUNG_FACTOR.pow(i - below)
            else (Progress.dose(e.base ?: e.reps)?.lo ?: e.target).toDouble() * u.m * SimKit.logNormal(r, 1.0, 0.1)
            capMax[e.id] = c * 2.5
            c
        }
        // push strength on one scale whatever the rung: capacity divided by how hard the rung is
        fun pushScore(): Double? {
            val rungs = Catalog.ladders.getValue("push")
            val best = rungs.indices.filter { cap.containsKey(rungs[it]) }.maxOrNull() ?: return null
            return cap.getValue(rungs[best]) / RUNG_FACTOR.pow(best)
        }

        for (day in 0 until WEEKS * 7) {
            val date = start.plusDays(day.toLong())
            val w = day / 7
            val epoch = date.toEpochDay()
            val tag = plan.week[date.dayOfWeek.value - 1]
            val extra = arm.endsWith("sesi tambahan") && tag == "Rest" && r.nextDouble() < 0.35
            val attend = r.nextDouble() < u.attend
            if (!(tag != "Rest" && attend) && !extra) {
                // days off past ten lose a little
                val idle = epoch - (logs.lastOrNull()?.epochDay ?: epoch)
                if (idle > 10) cap.keys.toList().forEach { cap[it] = cap.getValue(it) * 0.997 }
                if (day % 7 == 6) pushScore()?.let { s -> if (pushStart.isNotEmpty()) acc.gain[w] += s / pushStart[0] }
                continue
            }
            val session = if (extra) ExtraSession.options(plan, logs, date).first { it.best || plan.allSessions.size == 1 }.tag.let { t -> plan.allSessions.first { it.tag == t } }
            else plan.sessionOn(date)
            if (extra) acc.extras[w]++
            val cut = if (Progress.isDeloadWeek(state0, date)) 1 else 0
            val list = PlanBuilder.refresh(session.exercises, a, ladders).map { Progress.boosted(it, boosts) }
                .map { it.copy(sets = (it.sets - cut - (if (it.id in deload) 1 else 0)).coerceAtLeast(1)) }
            if (pushStart.isEmpty()) list.firstOrNull { Catalog.ladderOf(it.id) == "push" }?.let { capOf(it); pushScore()?.let { s -> pushStart += s } }
            val daily = SimKit.gauss(r, 1.0, 0.07)
            var anyFail = false
            val exLogs = list.map { e ->
                val move = Catalog.get(e.id)
                val tired = move.muscles.any { m -> (lastTrained[m] ?: -9) >= epoch - 1 }
                val kg = if (move.weighted) Progress.load(loads, e.id) else null
                val kgCost = kg?.let { KG_COST.pow(((it - Progress.START_KG) / Progress.KG_STEP).toDouble()) } ?: 1.0
                val fresh = capOf(e) * daily * kgCost * (if (tired) 0.9 else 1.0)
                val maxRep = Progress.dose(e.reps) == null // "maximum rep" or a negative: as many as they can
                val timed = move.timed
                val sets = (0 until e.sets).map { j ->
                    val eff = fresh * (1 - SET_FATIGUE * j)
                    if (j == 0) {
                        acc.firstSets[w]++
                        acc.ratio[w] += e.target / fresh; acc.ratioN[w]++
                        if (e.target > fresh) acc.tooHard[w]++
                        if (e.target <= 0.6 * fresh) acc.tooEasy[w]++
                    }
                    val can = floor(eff).toInt().coerceAtLeast(1)
                    val target = if (maxRep) can else e.target
                    val failed = !maxRep && can < target
                    val inReserve = if (timed) ((eff - target) / Progress.SECONDS_STEP).toInt() else can - target
                    acc.sets[w]++
                    if (failed) { acc.failed[w]++; anyFail = true }
                    Triple(SetLog(if (failed) can else target, kg = kg, failed = failed), inReserve, eff)
                }
                val trueRir = sets.last().second.coerceAtLeast(0)
                acc.rir[w][minOf(trueRir, 5)]++
                val honest = effort(trueRir)
                val said: Int = when (arm) {
                    "optimis" -> answers[minOf(answers.indexOf(honest) + 1, 3)]
                    "pesimis" -> answers[maxOf(answers.indexOf(honest) - 1, 0)]
                    "selalu Ngam" -> 1
                    "tekan butang atas" -> 4 // the first button on the effort screen is Sangat easy
                    else -> honest
                }
                val logged = sets.map { it.first.copy(rir = said) } // the app writes the answer on every set of the move
                // training effect: sets close to failure drive it, with less room to grow near the ceiling
                val hard = sets.count { it.second <= 3 }.toDouble() / e.sets
                val c = cap.getValue(e.id)
                cap[e.id] = c + u.rate * c * (1 - c / capMax.getValue(e.id)).coerceAtLeast(0.0) * hard
                move.muscles.forEach { lastTrained[it] = epoch }
                ExerciseLog(e.id, logged)
            }
            acc.sessions[w]++
            if (anyFail) acc.failSessions[w]++
            val before = ladders.values.sumOf { it.rung }
            val kgBefore = loads
            val climb = Progress.climb(ladders, boosts, list, exLogs, loads, usable = { PlanBuilder.usable(it, a) }, history = logs)
            acc.climbs[w] += climb.ladders.values.sumOf { it.rung } - before
            acc.kgUps[w] += climb.loads.count { (id, k) -> k > (kgBefore[id] ?: Progress.START_KG) && list.any { it.id == id } }
            val nextDeload = GiveUp.nextDeload(deload, exLogs, logs)
            acc.deloads[w] += (nextDeload - deload).size
            ladders = climb.ladders; boosts = climb.boosts; loads = climb.loads; deload = nextDeload
            logs += WorkoutLog(epoch, session.label, 20, exLogs, 100, false, extra = extra)
            if (day % 7 == 6) pushScore()?.let { s -> if (pushStart.isNotEmpty()) acc.gain[w] += s / pushStart[0] }
        }
        val pushRung = ladders["push"]?.rung ?: 0
        val startRung = PlanBuilder.startingLadders(a)["push"]?.rung ?: 0
        acc.rungs += (pushRung - startRung).toDouble()
        // sets flagged never happen here (all sets are real), so Integrity.real is the identity: checked once
        check(logs.all { l -> l.exercises.all { Integrity.real(it).size == it.sets.size } })
    }
}
