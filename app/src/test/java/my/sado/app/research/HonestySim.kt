package my.sado.app.research

import my.sado.app.data.Burn
import my.sado.app.data.Catalog
import my.sado.app.data.ExerciseLog
import my.sado.app.data.HrAnalysis
import my.sado.app.data.HrPoint
import my.sado.app.data.Integrity
import my.sado.app.data.Plan
import my.sado.app.data.Profile
import my.sado.app.data.SetLog
import java.util.Random
import kotlin.math.roundToInt

/**
 * Kajian 4 and 5: sessions done for real, half faked and fully faked, with set timings and a watch's heart rate at
 * three sampling rates, through Integrity.flag, the verdict and the flat-heart-rate check. The genuine ones also give
 * the two calorie estimates (MET and heart rate) side by side.
 */
object HonestySim {
    const val SESSIONS = 50_000
    val kinds = listOf("Jujur", "Jujur laju", "Separuh tipu", "Tipu penuh", "Tipu perlahan")
    val sampling = listOf("Broadcast (5s)" to 5, "Strength mode (1–2s)" to 2, "All-day (60–120s)" to 90)

    private data class Gen(val logs: List<ExerciseLog>, val windows: List<Pair<Int, Int>>, val fakeMoves: Set<Int>, val seconds: Int)

    /** One session's sets: when each starts and ends, and the work windows the heart follows. */
    private fun generate(r: Random, plan: Plan, kind: String): Gen {
        val s = plan.allSessions[r.nextInt(plan.allSessions.size)]
        var t = 60 // warm-up
        val windows = mutableListOf<Pair<Int, Int>>()
        val fake = when (kind) {
            "Tipu penuh", "Tipu perlahan" -> s.exercises.indices.toSet()
            "Separuh tipu" -> s.exercises.indices.filter { r.nextBoolean() }.toSet().ifEmpty { setOf(0) }
            else -> emptySet()
        }
        val cadence = if (kind == "Jujur laju") SimKit.gauss(r, 1.3, 0.2) else SimKit.gauss(r, 2.6, 0.5)
        val logs = s.exercises.mapIndexed { i, e ->
            val m = Catalog.get(e.id)
            val sets = (0 until e.sets).map { j ->
                val lying = i in fake && kind != "Tipu perlahan" // a slow faker's timings look real
                val reps = e.target
                val secs = when {
                    lying -> (1 + r.nextInt(6))
                    m.timed -> (e.target + SimKit.gauss(r, 2.0, 2.0)).roundToInt().coerceAtLeast(1)
                    else -> (reps * cadence.coerceAtLeast(0.9) + SimKit.gauss(r, 2.0, 1.5)).roundToInt().coerceAtLeast(1)
                }
                val startT = t
                t += secs
                if (i !in fake) windows += startT to t
                val at = t
                val rest = if (lying) (if (r.nextDouble() < 0.85) r.nextInt(10) else 60 + r.nextInt(40)) else (if (r.nextDouble() < 0.06) 20 + r.nextInt(15) else 70 + r.nextInt(50))
                val skipped = Integrity.restSkipped(90, (90 - rest).coerceAtLeast(0))
                t += rest + (if (j == e.sets - 1) 20 else 0)
                SetLog(reps, secs = secs, at = at, restSkipped = skipped)
            }
            ExerciseLog(e.id, sets)
        }
        return Gen(logs, windows, fake, t)
    }

    /** Heart rate that climbs during work and falls back in rest, around a resting rate; a faker sits or walks about. */
    private fun heart(r: Random, g: Gen, resting: Int, fitness: Double, every: Int, idle: Double? = null): List<HrPoint> {
        val peak = resting + 55 + 25 * fitness
        var hr = resting + 8.0
        val out = mutableListOf<HrPoint>()
        var next = 0.0
        for (sec in 0 until g.seconds) {
            val working = g.windows.any { sec in it.first until it.second }
            // between sets the heart stays up; a faker's idle is sitting (a few bpm over resting) or moving about
            val target = if (working) peak else idle?.let { resting + it } ?: (resting + 22 + 10 * fitness)
            hr += (target - hr) * (if (working) 0.05 else 0.025)
            if (sec >= next) {
                out += HrPoint(sec * 1000L, (hr + r.nextGaussian() * 3).roundToInt())
                next += if (every >= 60) (60 + r.nextInt(61)).toDouble() else every * (0.7 + 0.6 * r.nextDouble())
            }
        }
        return out
    }

    fun run(seed: Long = 4, plans: List<Plan>): Map<String, Any> {
        val r = Random(seed)
        // confusion counts: kind to (doubtful by sets, by heart, by either), per sampling
        val counts = kinds.associateWith { mutableMapOf<String, Int>() }
        val nKind = kinds.associateWith { 0 }.toMutableMap()
        val c0 = mutableMapOf<String, Int>() // full fakers by what they did instead
        val setTp = IntArray(1); val setFp = IntArray(1); val setFn = IntArray(1); val setTn = IntArray(1)
        val roc = sampling.associate { it.first to Array(41) { IntArray(4) } } // threshold 0..40: tp fp fn tn (positive = fake session)
        val dense = sampling.associate { it.first to IntArray(2) }
        val zones = mutableListOf<Double>()
        val metK = mutableListOf<Double>(); val hrK = mutableListOf<Double>(); val diffs = mutableListOf<Double>(); val avgs = mutableListOf<Double>()
        val bland = mutableListOf<List<Double>>()
        repeat(SESSIONS) {
            val kind = SimKit.pick(r, listOf("Jujur" to 0.45, "Jujur laju" to 0.1, "Separuh tipu" to 0.15, "Tipu penuh" to 0.15, "Tipu perlahan" to 0.15))
            nKind[kind] = nKind.getValue(kind) + 1
            val fullFake = kind == "Tipu penuh" || kind == "Tipu perlahan"
            val plan = plans[r.nextInt(plans.size)]
            val g = generate(r, plan, kind)
            val timed = { id: String -> Catalog.get(id).takeIf { it.timed }?.let { m -> g.logs.first { it.id == id }.sets.firstOrNull()?.reps } }
            val flagged = Integrity.flag(g.logs, timed)
            // per set
            flagged.forEachIndexed { i, e -> e.sets.forEach { s ->
                val fake = i in g.fakeMoves
                when { fake && s.flagged -> setTp[0]++; !fake && s.flagged -> setFp[0]++; fake -> setFn[0]++; else -> setTn[0]++ }
            } }
            val bySets = Integrity.verdict(flagged, null, null).doubtful
            val resting = SimKit.gauss(r, 66.0, 8.0).roundToInt().coerceIn(45, 90)
            val restEst = resting + SimKit.gauss(r, 0.0, 4.0).roundToInt() // the app's estimate from last night or the week's lowest
            val fitness = r.nextDouble()
            // a full faker either sits (60%: 2–8 bpm over resting) or walks about doing other things (40%: 15–30)
            val sitting = r.nextDouble() < 0.6
            val fakeIdle = if (sitting) 2 + 6 * r.nextDouble() else 15 + 15 * r.nextDouble()
            if (fullFake) { val k = if (sitting) "duduk" else "bergerak"; c0[k] = (c0[k] ?: 0) + 1 }
            val c = counts.getValue(kind)
            c["sets"] = (c["sets"] ?: 0) + if (bySets) 1 else 0
            sampling.forEach { (name, every) ->
                val pts = if (fullFake) heart(r, g.copy(windows = emptyList()), resting, fitness, every, idle = fakeIdle) else heart(r, g, resting, fitness, every)
                val avg = pts.map { it.bpm }.average().roundToInt()
                // the app's rule: at least 10 readings and the average under resting + 15
                val flat = pts.size >= 10 && Integrity.hrFlat(avg, restEst)
                c["hr|$name"] = (c["hr|$name"] ?: 0) + if (flat) 1 else 0
                if (fullFake) { val k = (if (sitting) "duduk" else "bergerak") + "|$name"; c0[k] = (c0[k] ?: 0) + if (flat) 1 else 0 }
                c["any|$name"] = (c["any|$name"] ?: 0) + if (flat || bySets) 1 else 0
                c["n|$name"] = (c["n|$name"] ?: 0) + if (pts.size >= 10) 1 else 0
                // ROC: whole fakes against honest sessions (half fakes did real work, the heart can't tell)
                if (kind != "Separuh tipu") roc.getValue(name).forEachIndexed { th, cell ->
                    val f = pts.size >= 10 && avg < restEst + th
                    cell[when { fullFake && f -> 0; !fullFake && f -> 1; fullFake -> 2; else -> 3 }]++
                }
                if (kind == "Jujur") {
                    val rep = HrAnalysis.analyze(pts, g.logs, restEst, 30)
                    if (rep != null) dense.getValue(name).let { it[0]++; if (rep.dense) it[1]++ }
                    if (every == 5 && rep != null) zones += (rep.zones[HrAnalysis.State.Workout] ?: 0).toDouble() / rep.zones.values.sum().coerceAtLeast(1)
                    if (every == 5) {
                        val p = Profile(sex = if (r.nextDouble() < 0.7) 0 else 1, age = 18 + r.nextInt(30), weightKg = SimKit.gauss(r, 70.0, 12.0).toFloat().coerceIn(45f, 120f))
                        val met = Burn.metKcal(Burn.met(g.logs), p.weightKg, g.seconds).toDouble()
                        val hrk = Burn.hrKcal(avg, p, g.seconds).toDouble()
                        metK += met; hrK += hrk; diffs += hrk - met; avgs += (hrk + met) / 2
                        if (bland.size < 1500) bland += listOf((hrk + met) / 2, hrk - met, if (p.sex == 0) 0.0 else 1.0)
                    }
                }
            }
        }
        val dm = SimKit.mean(diffs); val ds = SimKit.sd(diffs)
        return mapOf(
            "sessions" to SESSIONS, "kinds" to kinds, "sampling" to sampling.map { it.first },
            "nKind" to nKind,
            "fakers" to c0,
            "detect" to counts.mapValues { (k, c) -> c.mapValues { it.value.toDouble() / nKind.getValue(k).coerceAtLeast(1) } },
            "perSet" to mapOf("tp" to setTp[0], "fp" to setFp[0], "fn" to setFn[0], "tn" to setTn[0]),
            "roc" to roc.mapValues { (_, cells) -> cells.mapIndexed { th, x -> mapOf("th" to th, "tpr" to x[0].toDouble() / (x[0] + x[2]).coerceAtLeast(1), "fpr" to x[1].toDouble() / (x[1] + x[3]).coerceAtLeast(1)) } },
            "appThreshold" to Integrity.HR_FLAT,
            "dense" to dense.mapValues { (_, d) -> d[1].toDouble() / d[0].coerceAtLeast(1) },
            "workoutZone" to SimKit.quantiles(zones),
            "kcal" to mapOf(
                "n" to diffs.size, "met" to SimKit.quantiles(metK), "hr" to SimKit.quantiles(hrK),
                "meanDiff" to dm, "loa" to listOf(dm - 1.96 * ds, dm + 1.96 * ds), "bland" to bland,
                "ratio" to SimKit.quantiles(hrK.zip(metK).filter { it.second > 0 }.map { it.first / it.second }),
            ),
            
        )
    }
}
