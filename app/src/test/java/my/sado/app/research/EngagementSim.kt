package my.sado.app.research

import my.sado.app.data.AppState
import my.sado.app.data.ExerciseLog
import my.sado.app.data.HonestRest
import my.sado.app.data.PlanBuilder
import my.sado.app.data.Progress
import my.sado.app.data.SetLog
import my.sado.app.data.WorkoutLog
import java.time.LocalDate
import java.util.Random

/**
 * Kajian 3: a year of real-looking attendance for many users, through the app's streak, XP and level rules.
 * Paired arms: the same days missed, with and without "Rehat jujur" on some of the missed days.
 */
object EngagementSim {
    const val USERS = 10_000
    const val WEEKS = 52
    val personas = listOf("Konsisten", "Sederhana", "On-off")

    private data class Persona(val name: String, val a: Double, val b: Double, val dropout: Double, val share: Double)
    private val mix = listOf(Persona("Konsisten", 16.0, 2.0, 0.008, 0.25), Persona("Sederhana", 6.0, 3.0, 0.025, 0.45), Persona("On-off", 3.0, 4.0, 0.05, 0.30))

    fun run(seed: Long = 3): Map<String, Any> {
        val r = Random(seed)
        val start = LocalDate.of(2026, 1, 5)
        val arms = listOf("Tanpa rehat jujur", "Dengan rehat jujur")
        // per arm: streak (days) at the end of each week for every user, and breaks counted
        val streakAt = arms.associateWith { Array(WEEKS) { mutableListOf<Double>() } }
        val breaks = arms.associateWith { mutableListOf<Double>() }
        val active = IntArray(WEEKS) // users still training that week
        val xpWeek = mutableMapOf<Int, MutableList<Double>>() // days/week to XP per week while active
        val levelAt = personas.associateWith { Array(WEEKS) { mutableListOf<Double>() } }
        val levelUpWeeks = Array(Progress.stages.size) { mutableListOf<Double>() }
        val gateXp = IntArray(Progress.stages.size); val gateWeeks = IntArray(Progress.stages.size)
        val sessionsPerWeek = personas.associateWith { mutableListOf<Double>() }
        val honestUsed = mutableListOf<Double>()
        repeat(USERS) {
            val p = SimKit.pick(r, mix.map { it to it.share })
            val ans = SimKit.answers(r)
            val plan = PlanBuilder.build(ans, PlanBuilder.startingLadders(ans))
            val attend = SimKit.beta(r, p.a, p.b)
            val finish = SimKit.beta(r, 8.0, 2.0) // chance a move is done in full
            var dropped = false
            val logs = mutableListOf<WorkoutLog>()
            val rests = mutableListOf<Long>()
            var xp = 0
            var level = 0
            var weeksHere = 0; var sessions = 0
            val prevStreak = arms.associateWith { 0 }.toMutableMap()
            val brk = arms.associateWith { 0 }.toMutableMap()
            for (w in 0 until WEEKS) {
                if (!dropped && w > 1 && r.nextDouble() < p.dropout) dropped = true
                val holiday = r.nextDouble() < 0.03
                var weekXp = 0
                for (d in 0 until 7) {
                    val date = start.plusDays(w * 7L + d)
                    val epoch = date.toEpochDay()
                    if (plan.week[d] == "Rest") continue
                    val goes = !dropped && !holiday && r.nextDouble() < attend
                    if (goes) {
                        val s = plan.sessionOn(date)
                        val ex = s.exercises.map { e -> if (r.nextDouble() < finish) ExerciseLog(e.id, List(e.sets) { SetLog(10) }) else ExerciseLog(e.id, List(r.nextInt(e.sets)) { SetLog(8) }) }
                        val gained = Progress.sessionXp(s.exercises, ex)
                        xp += gained; weekXp += gained; sessions++
                        logs += WorkoutLog(epoch, s.label, 20, ex, gained, false)
                    } else if (!dropped && r.nextDouble() < 0.6) {
                        // a missed day turned into an honest rest, where the weekly allowance still has some
                        val st = AppState(logs = logs, honestRests = rests)
                        if (HonestRest.canTake(st, epoch)) rests += epoch
                    }
                }
                if (!dropped) { active[w]++; weeksHere++; xpWeek.getOrPut(ans.days) { mutableListOf() } += weekXp.toDouble() }
                val sunday = start.plusDays(w * 7L + 6)
                arms.forEach { arm ->
                    val st = AppState(logs = logs, honestRests = if (arm == arms[1]) rests else emptyList())
                    val days = Progress.streak(st, sunday).days
                    streakAt.getValue(arm)[w] += days.toDouble()
                    if (days < prevStreak.getValue(arm)) brk[arm] = brk.getValue(arm) + 1
                    prevStreak[arm] = days
                }
                // level: XP and weeks since the first workout both gate it (levels 12+ also need a test, not simulated)
                val st = AppState(logs = logs)
                val weeks = Progress.weeksActive(st, sunday)
                val now = Progress.stages.last { it.index < 12 && xp >= it.at && weeks >= it.minWeeks }.index
                if (now > level) {
                    for (i in level + 1..now) {
                        levelUpWeeks[i] += (w + 1).toDouble()
                        // which gate held it back: XP arrived after the weeks, or the weeks after the XP
                        if (Progress.stages[i].minWeeks >= weeks) gateWeeks[i]++ else gateXp[i]++
                    }
                    level = now
                }
                levelAt.getValue(p.name)[w] += level.toDouble()
            }
            arms.forEach { breaks.getValue(it) += brk.getValue(it).toDouble() }
            sessionsPerWeek.getValue(p.name) += if (weeksHere == 0) 0.0 else sessions.toDouble() / weeksHere
            honestUsed += rests.size.toDouble()
        }
        return mapOf(
            "users" to USERS, "weeks" to WEEKS, "arms" to arms, "personas" to mix.map { mapOf("name" to it.name, "share" to it.share, "dropout" to it.dropout, "attendMean" to it.a / (it.a + it.b)) },
            "active" to active.toList(),
            "streak" to arms.associateWith { a -> streakAt.getValue(a).map { SimKit.quantiles(it, listOf(0.25, 0.5, 0.75)) } },
            "streakFinal" to arms.associateWith { a -> SimKit.histogram(streakAt.getValue(a)[WEEKS - 1].map { it / 7 }, 0.0, 52.0, 26) },
            "streakAlive" to arms.associateWith { a -> streakAt.getValue(a).map { ws -> ws.count { it >= 28 }.toDouble() / USERS } },
            "breaks" to arms.associateWith { a -> SimKit.mean(breaks.getValue(a)) },
            "xpWeek" to xpWeek.mapKeys { it.key.toString() }.mapValues { SimKit.quantiles(it.value) },
            "levelAt" to levelAt.mapValues { (_, ws) -> ws.map { SimKit.mean(it) } },
            "levelDesign" to Progress.stages.filter { it.index < 12 }.map { mapOf("index" to it.index, "xp" to it.at, "weeks" to it.minWeeks) },
            "levelUp" to (1 until 12).map { i -> mapOf("index" to i, "n" to levelUpWeeks[i].size, "median" to SimKit.quantiles(levelUpWeeks[i])[2], "design" to Progress.stages[i].minWeeks, "xpGate" to gateXp[i], "weekGate" to gateWeeks[i]) },
            "sessionsPerWeek" to sessionsPerWeek.mapValues { SimKit.quantiles(it.value) },
            "honestPerUser" to SimKit.mean(honestUsed),
        )
    }
}
