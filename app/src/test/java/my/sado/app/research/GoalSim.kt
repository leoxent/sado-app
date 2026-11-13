package my.sado.app.research

import my.sado.app.data.AppState
import my.sado.app.data.Catalog
import my.sado.app.data.CheckIn
import my.sado.app.data.Nutrition
import my.sado.app.data.Plan
import my.sado.app.data.PlanBuilder
import my.sado.app.data.PlannerAnswers
import my.sado.app.data.Profile
import my.sado.app.data.WeeklyCheck
import my.sado.app.data.WeightLog
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Kajian 6: does the plan and the food target move each user towards the body they asked for?
 *
 * Part A, volume: weekly sets per muscle in 100k real plans, counted the way hypertrophy research counts them
 * (a main muscle 1 set, a helper muscle half a set; Pelland et al. 2024), against the 10–20 set range.
 *
 * Part B, body: virtual users with a real profile train their plan for 24 weeks and eat to the app's own target
 * (Nutrition.targets, re-set by the real WeeklyCheck from noisy weigh-ins). A small body model turns that into
 * muscle, fat and weight. Same users, same luck, in four arms: how closely they follow the food target, and
 * whether their sets are really hard (Kajian 2 found "selalu Ngam" leaves 53% of first sets far too easy).
 */
object GoalSim {
    const val PLANS = 100_000
    const val USERS = 10_000
    const val WEEKS = 24

    val muscles = listOf("chest", "delts", "triceps", "lats", "biceps", "forearms", "traps", "abs", "obliques", "lowerback", "quads", "hamstrings", "glutes", "calves")
    // share of skeletal muscle mass in each one; the rest (about 10%) is muscle no move here trains
    private val massShare = mapOf(
        "chest" to .07, "delts" to .07, "triceps" to .05, "lats" to .10, "biceps" to .03, "forearms" to .04, "traps" to .05,
        "abs" to .05, "obliques" to .03, "lowerback" to .04, "quads" to .14, "hamstrings" to .08, "glutes" to .10, "calves" to .05,
    )
    // the muscles each body shape is really about
    val shapeMuscles = mapOf(
        "vshape" to listOf("lats", "delts"), "aesthetic" to listOf("chest", "delts", "abs"), "bulk" to listOf("chest", "lats", "quads"),
        "athletic" to listOf("quads", "glutes"), "lean" to listOf("quads", "glutes", "chest"), "shredded" to listOf("chest", "delts", "abs"),
    )
    val arms = listOf("Ikut penuh", "Ikut separuh", "Latihan je", "Ikut penuh, selalu Ngam")
    val modes = listOf("Cut", "Recomp", "Kekal", "Lean bulk")

    // ── body model constants ─────────────────────────────────────────────────────────────────────
    private val G0 = listOf(0.010, 0.006, 0.0035) // weekly relative growth at full dose, by quiz level (training age)
    private const val DOSE_K = 5.0 // growth = 1 − e^(−sets/5): about half at 3.5 sets, 95% at 15 (Schoenfeld 2017 bands)
    private const val CEILING = 0.40 // a muscle slows as it nears +40% of its start
    private const val KCAL_FAT = 9440.0 // Hall 2008
    private const val KCAL_LEAN = 1816.0
    private const val FORBES_C = 10.4

    /** Weekly sets per muscle for one session: main muscles count 1, helpers 0.5. Cardio moves are left out. */
    fun sessionSets(ex: List<my.sado.app.data.PlannedExercise>): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        ex.forEach { e ->
            val m = Catalog.get(e.id)
            if (m.group == "cardio") return@forEach
            m.muscles.forEach { out[it] = (out[it] ?: 0.0) + e.sets }
            m.secondary.forEach { out[it] = (out[it] ?: 0.0) + e.sets * 0.5 }
        }
        return out
    }

    fun weekSets(plan: Plan): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        plan.allSessions.forEach { s -> val d = plan.daysOf(s).size; sessionSets(s.exercises).forEach { (k, v) -> out[k] = (out[k] ?: 0.0) + v * d } }
        return out
    }

    fun run(seed: Long = 6): Map<String, Any> = mapOf("volume" to volume(seed), "body" to body(seed + 1))

    // ── A · volume ───────────────────────────────────────────────────────────────────────────────
    private fun volume(seed: Long): Map<String, Any> {
        val sample = PlanCensus.sample(PLANS, seed)
        val per = muscles.associateWith { mutableListOf<Double>() }
        val bands = muscles.associateWith { IntArray(4) } // <4, 4–10, 10–20, 20+
        val byDays = mutableMapOf<String, MutableMap<String, MutableList<Double>>>()
        val shapeOk = mutableMapOf<String, IntArray>() // [plans, plans where every key muscle has 10+]
        val shapeKey = mutableMapOf<String, MutableList<Double>>()
        val focusOk = mutableMapOf<String, IntArray>()
        sample.forEach { (a, plan) ->
            val w = weekSets(plan)
            muscles.forEach { m ->
                val v = w[m] ?: 0.0
                per.getValue(m) += v
                bands.getValue(m)[when { v < 4 -> 0; v < 10 -> 1; v <= 20 -> 2; else -> 3 }]++
                byDays.getOrPut(a.days.toString()) { mutableMapOf() }.getOrPut(m) { mutableListOf() } += v
            }
            a.shape.forEach { s ->
                val key = shapeMuscles.getValue(s)
                val c = shapeOk.getOrPut(s) { IntArray(2) }
                c[0]++
                if (key.all { (w[it] ?: 0.0) >= 10 }) c[1]++
                shapeKey.getOrPut(s) { mutableListOf() } += key.minOf { w[it] ?: 0.0 }
            }
            // a group rated Fokus: does it reach 10 counted sets once helpers count too?
            a.ratings.filter { it.value == 5 }.keys.forEach { g ->
                val c = focusOk.getOrPut(g) { IntArray(2) }
                c[0]++
                if ((PlanBuilder.groupSets(plan)[g] ?: 0) >= 10) c[1]++
            }
        }
        return mapOf(
            "n" to PLANS,
            "sets" to per.mapValues { SimKit.quantiles(it.value) },
            "bands" to bands.mapValues { it.value.map { c -> c.toDouble() / PLANS } },
            "byDays" to byDays.mapValues { (_, m) -> m.mapValues { SimKit.quantiles(it.value, listOf(0.5))[0] } },
            "shape" to shapeOk.mapValues { (s, c) -> mapOf("n" to c[0], "ok" to c[1].toDouble() / c[0], "muscles" to shapeMuscles.getValue(s), "minKey" to SimKit.quantiles(shapeKey.getValue(s))) },
        )
    }

    // ── B · body ─────────────────────────────────────────────────────────────────────────────────
    private class User(
        val a: PlannerAnswers, val plan: Plan, val p: Profile, val bf: Double, val maint: Double, val habit: Double,
        val resp: Double, val attend: Double, val seed: Long,
    )

    private fun mifflin(kg: Double, p: Profile) = 10 * kg + 6.25 * p.heightCm - 5 * p.age + if (p.sex == 0) 5 else -161
    private val actF = listOf(1.2, 1.375, 1.55)

    private fun profile(r: Random, a: PlannerAnswers): Pair<Profile, Double> {
        val sex = if (r.nextDouble() < 0.5) 0 else 1
        val age = 18 + (r.nextDouble() * r.nextDouble() * 30).toInt()
        val h = if (sex == 0) SimKit.gauss(r, 169.0, 6.5) else SimKit.gauss(r, 157.0, 6.0)
        val bmi = when (a.body) { "skinny" -> SimKit.gauss(r, 18.8, 1.2); "heavy" -> SimKit.gauss(r, 29.5, 3.5); else -> SimKit.gauss(r, 23.0, 2.0) }.coerceIn(16.0, 42.0)
        val kg = bmi * (h / 100) * (h / 100)
        val waist = if (r.nextDouble() < 0.4) (h * (0.30 + 0.0085 * bmi + SimKit.gauss(r, 0.0, 0.02))).toFloat() else null
        // Deurenberg 1991
        val bf = ((1.2 * bmi + 0.23 * age - 10.8 * (1 - sex) - 5.4) / 100).coerceIn(0.06, 0.55)
        return Profile("", sex, age, h.toFloat(), kg.toFloat(), SimKit.pick(r, listOf(0 to 0.4, 1 to 0.45, 2 to 0.15)), waist) to bf
    }

    private class Track {
        val lean = Array(WEEKS) { mutableListOf<Double>() }
        val fat = Array(WEEKS) { mutableListOf<Double>() }
    }

    private fun body(seed: Long): Map<String, Any> {
        val r = Random(seed)
        val users = List(USERS) {
            val a = SimKit.answers(r)
            val plan = PlanBuilder.build(a, PlanBuilder.startingLadders(a))
            val (p, bf) = profile(r, a)
            User(a, plan, p, bf, SimKit.logNormal(r, 1.0, 0.08), SimKit.gauss(r, 0.0, 0.05), SimKit.logNormal(r, 1.0, 0.4), SimKit.beta(r, 7.0, 2.0), r.nextLong())
        }
        val modeOf = users.map { Nutrition.targets(it.p, it.a).mode }
        // per arm, per mode
        val dLean = arms.associateWith { modes.associateWith { mutableListOf<Double>() } }
        val dFat = arms.associateWith { modes.associateWith { mutableListOf<Double>() } }
        val dW = arms.associateWith { modes.associateWith { mutableListOf<Double>() } }
        val ok = arms.associateWith { modes.associateWith { IntArray(2) } }
        val track = arms.associateWith { modes.associateWith { Track() } }
        val growth = arms.associateWith { muscles.associateWith { mutableListOf<Double>() } }
        val shapeOk = arms.associateWith { mutableMapOf<String, IntArray>() }
        val shapeGrow = arms.associateWith { mutableMapOf<String, MutableList<Double>>() }
        val inRange = arms.associateWith { IntArray(2) }
        val checks = arms.associateWith { IntArray(1) }
        val kcalMoves = arms.associateWith { mutableListOf<Double>() }
        val floored = arms.associateWith { IntArray(1) }
        val protein = arms.associateWith { mutableListOf<Double>() }
        val balance = arms.associateWith { modes.associateWith { mutableListOf<Double>() } }
        val modeN = modes.associateWith { m -> modeOf.count { it == m } }

        users.forEachIndexed { ui, u ->
            val mode = modeOf[ui]
            val days = (0..6).map { d -> u.plan.week[d].takeIf { it != "Rest" }?.let { t -> u.plan.allSessions.firstOrNull { it.tag == t } } }
            val daySets = days.map { s -> s?.let { sessionSets(it.exercises) } }
            val planned = days.count { it != null }
            arms.forEachIndexed { ai, arm ->
                val r2 = Random(u.seed)
                val follow = when (ai) { 0, 3 -> SimKit.beta(r2, 9.0, 1.5); 1 -> SimKit.beta(r2, 4.0, 4.0); else -> 0.0.also { SimKit.beta(r2, 9.0, 1.5) } }
                val hard = if (ai == 3) SimKit.beta(r2, 4.0, 5.0) else SimKit.beta(r2, 8.0, 2.0)
                val weighP = when (ai) { 0, 3 -> 0.75; 1 -> 0.4; else -> 0.0 }
                val habitProtein = SimKit.logNormal(r2, 1.0, 0.25) // g/kg eaten without trying
                var kg = u.p.weightKg.toDouble()
                var fat = kg * u.bf
                val lean0 = kg - fat; val fat0 = fat; val kg0 = kg
                val smm0 = lean0 * if (u.p.sex == 0) 0.50 else 0.45
                val mass = muscles.associateWith { smm0 * massShare.getValue(it) }.toMutableMap()
                val mass0 = mass.toMap()
                var lean = lean0
                var st = AppState(profile = u.p, answers = u.a)
                val weights = ArrayDeque<WeightLog>()
                var day = 0L
                val g0 = G0[u.a.level.coerceIn(0, 2)] * u.resp
                val tk = track.getValue(arm).getValue(mode)
                var proteinSum = 0.0
                var balSum = 0.0
                for (w in 0 until WEEKS) {
                    val t = Nutrition.targets(st, st.profile!!)
                    // this week's training: planned days attended, sets that were really hard
                    val sets = mutableMapOf<String, Double>()
                    var sessions = 0
                    daySets.forEach { ds -> if (ds != null && r2.nextDouble() < u.attend) { sessions++; ds.forEach { (k, v) -> sets[k] = (sets[k] ?: 0.0) + v * hard } } }
                    // energy: true upkeep from the current weight, plus what the sessions burned
                    val upkeep = mifflin(kg, u.p) * actF[u.p.activity] * u.maint
                    val tdee = upkeep + sessions * 4.5 * kg / 70 * 25 / 7
                    val habit = upkeep * (1 + u.habit)
                    val intake = follow * t.kcal + (1 - follow) * habit + SimKit.gauss(r2, 0.0, 60.0)
                    val proteinGkg = follow * (t.protein / kg) + (1 - follow) * habitProtein
                    proteinSum += proteinGkg
                    val b = (intake - tdee) / tdee
                    balSum += b
                    // muscle: dose × room left × energy × protein
                    val fatHigh = fat / kg > if (u.p.sex == 0) 0.25 else 0.33
                    val eF = (1 + 2 * b).coerceIn(if (fatHigh) 0.7 else 0.3, 1.2)
                    val pF = (0.75 + 0.25 * proteinGkg / 1.6).coerceAtMost(1.0)
                    var gained = 0.0
                    muscles.forEach { m ->
                        val e = sets[m] ?: 0.0
                        val room = (1 - (mass.getValue(m) / mass0.getValue(m) - 1) / CEILING).coerceIn(0.0, 1.0)
                        val dm = mass0.getValue(m) * g0 * (1 - exp(-e / DOSE_K)) * room * eF * pF
                        mass[m] = mass.getValue(m) + dm; gained += dm
                    }
                    // energy left after building muscle goes to (or comes from) fat and, in a deficit, some lean
                    val bal = 7 * (intake - tdee) - gained * KCAL_LEAN
                    var leanLoss = 0.0
                    if (bal < 0) {
                        val forbes = FORBES_C / (FORBES_C + fat)
                        val guard = 1 - 0.6 * min(1.0, sessions.toDouble() / max(1, planned)) * hard
                        val p = forbes * guard * (if (proteinGkg >= 1.6) 0.7 else 1.0)
                        // p is the share of the kilos lost that is lean (Forbes), so split the kilos, not the energy
                        val lost = bal / (p * KCAL_LEAN + (1 - p) * KCAL_FAT)
                        leanLoss = lost * p
                        fat += lost * (1 - p)
                    } else fat += bal / KCAL_FAT
                    fat = max(fat, kg * 0.03)
                    lean += gained + leanLoss
                    val kgPrev = kg
                    kg = lean + fat
                    tk.lean[w] += lean - lean0; tk.fat[w] += fat - fat0
                    // weigh-ins through the week, with water noise, then the app's own weekly check
                    for (d in 0 until 7) {
                        day++
                        if (r2.nextDouble() < weighP) {
                            weights.addLast(WeightLog(day, (kgPrev + (kg - kgPrev) * (d + 1) / 7 + SimKit.gauss(r2, 0.0, 0.6)).toFloat()))
                            while (weights.first().epochDay < day - 13) weights.removeFirst()
                        }
                    }
                    if (weighP > 0) {
                        st = WeeklyCheck.run(st.copy(weights = weights.toList(), checkins = st.checkins.takeLast(1)), day)
                        val c: CheckIn = st.checkins.last()
                        checks.getValue(arm)[0]++
                        if (c.avgKg != null && c.prevAvgKg != null) { inRange.getValue(arm)[0]++; if (c.offTrack == 0) inRange.getValue(arm)[1]++ }
                        if (c.floored) floored.getValue(arm)[0]++
                    }
                }
                val dl = lean - lean0; val df = fat - fat0; val dw = kg - kg0
                dLean.getValue(arm).getValue(mode) += dl; dFat.getValue(arm).getValue(mode) += df; dW.getValue(arm).getValue(mode) += dw
                kcalMoves.getValue(arm) += abs(st.kcalAdjust).toDouble()
                protein.getValue(arm) += proteinSum / WEEKS
                balance.getValue(arm).getValue(mode) += balSum / WEEKS
                muscles.forEach { m -> growth.getValue(arm).getValue(m) += mass.getValue(m) / mass0.getValue(m) - 1 }
                // did the body move the way the mode means? Lean gains for women are counted at 60% of men's.
                val s = if (u.p.sex == 0) 1.0 else 0.6
                val success = when (mode) {
                    "Cut" -> df <= -0.04 * kg0 && dl >= 0.2 * dw // most of what came off is fat
                    "Lean bulk" -> dl >= 1.0 * s && df <= 1.5 * dl
                    "Recomp" -> df <= -1.0 && dl >= 0.5 * s
                    else -> abs(dw) <= 2.0 && dl >= 0
                }
                ok.getValue(arm).getValue(mode).let { it[0]++; if (success) it[1]++ }
                u.a.shape.forEach { sh ->
                    shapeOk.getValue(arm).getOrPut(sh) { IntArray(2) }.let { it[0]++; if (success) it[1]++ }
                    shapeGrow.getValue(arm).getOrPut(sh) { mutableListOf() } += shapeMuscles.getValue(sh).map { mass.getValue(it) / mass0.getValue(it) - 1 }.average()
                }
            }
        }
        return mapOf(
            "users" to USERS, "weeks" to WEEKS, "arms" to arms, "modes" to modes, "modeN" to modeN,
            "lean" to dLean.mapValues { (_, m) -> m.mapValues { SimKit.quantiles(it.value) } },
            "fat" to dFat.mapValues { (_, m) -> m.mapValues { SimKit.quantiles(it.value) } },
            "weight" to dW.mapValues { (_, m) -> m.mapValues { SimKit.quantiles(it.value) } },
            "success" to ok.mapValues { (_, m) -> m.mapValues { if (it.value[0] == 0) 0.0 else it.value[1].toDouble() / it.value[0] } },
            "track" to track.mapValues { (_, m) -> m.mapValues { (_, t) -> mapOf("lean" to t.lean.map { SimKit.quantiles(it, listOf(0.25, 0.5, 0.75)) }, "fat" to t.fat.map { SimKit.quantiles(it, listOf(0.25, 0.5, 0.75)) }) } },
            "growth" to growth.mapValues { (_, m) -> m.mapValues { SimKit.quantiles(it.value) } },
            "shape" to shapeOk.mapValues { (a, m) -> m.mapValues { (sh, c) -> mapOf("n" to c[0], "ok" to c[1].toDouble() / c[0], "grow" to SimKit.quantiles(shapeGrow.getValue(a).getValue(sh))) } },
            "checkInRange" to inRange.mapValues { if (it.value[0] == 0) null else it.value[1].toDouble() / it.value[0] },
            "kcalAdjust" to kcalMoves.mapValues { SimKit.quantiles(it.value) },
            "floored" to floored.mapValues { (a, f) -> f[0].toDouble() / max(1, checks.getValue(a)[0]) },
            "protein" to protein.mapValues { SimKit.quantiles(it.value) },
            "balance" to balance.mapValues { (_, m) -> m.mapValues { SimKit.quantiles(it.value, listOf(0.5))[0] } },
        )
    }
}
