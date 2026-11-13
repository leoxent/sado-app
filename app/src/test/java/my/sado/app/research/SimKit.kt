package my.sado.app.research

import my.sado.app.data.PlannerAnswers
import java.util.Random
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Shared pieces for the research simulations: random draws, summaries and a small JSON writer. */
object SimKit {
    fun gauss(r: Random, mean: Double, sd: Double) = mean + sd * r.nextGaussian()
    fun logNormal(r: Random, median: Double, sigma: Double) = exp(ln(median) + sigma * r.nextGaussian())

    /** Beta(a, b) from two gamma draws (Marsaglia–Tsang). */
    fun beta(r: Random, a: Double, b: Double): Double { val x = gamma(r, a); val y = gamma(r, b); return x / (x + y) }
    private fun gamma(r: Random, k: Double): Double {
        if (k < 1) return gamma(r, k + 1) * Math.pow(r.nextDouble(), 1 / k)
        val d = k - 1.0 / 3; val c = 1 / sqrt(9 * d)
        while (true) {
            var x: Double; var v: Double
            do { x = r.nextGaussian(); v = 1 + c * x } while (v <= 0)
            v *= v * v
            val u = r.nextDouble()
            if (u < 1 - 0.0331 * x * x * x * x || ln(u) < 0.5 * x * x + d * (1 - v + ln(v))) return d * v
        }
    }

    fun <T> pick(r: Random, items: List<Pair<T, Double>>): T {
        var x = r.nextDouble() * items.sumOf { it.second }
        items.forEach { (v, w) -> x -= w; if (x <= 0) return v }
        return items.last().first
    }

    fun quantiles(xs: List<Double>, qs: List<Double> = listOf(0.05, 0.25, 0.5, 0.75, 0.95)): List<Double> {
        if (xs.isEmpty()) return qs.map { 0.0 }
        val s = xs.sorted()
        return qs.map { q -> val p = q * (s.size - 1); val lo = p.toInt(); val hi = minOf(lo + 1, s.lastIndex); s[lo] + (s[hi] - s[lo]) * (p - lo) }
    }

    fun mean(xs: Collection<Double>) = if (xs.isEmpty()) 0.0 else xs.sum() / xs.size
    fun sd(xs: Collection<Double>): Double { val m = mean(xs); return if (xs.size < 2) 0.0 else sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1)) }

    /** 95% margin of error of a proportion at sample size n (worst case p = 0.5 when p is null). */
    fun moe(n: Int, p: Double? = null) = 1.96 * sqrt((p ?: 0.5) * (1 - (p ?: 0.5)) / n)

    fun histogram(xs: List<Double>, lo: Double, hi: Double, bins: Int): Map<String, Any> {
        val w = (hi - lo) / bins
        val counts = IntArray(bins)
        xs.forEach { x -> counts[((x - lo) / w).toInt().coerceIn(0, bins - 1)]++ }
        return mapOf("lo" to lo, "hi" to hi, "bins" to bins, "counts" to counts.toList())
    }

    fun round(x: Double, d: Int = 3): Double { val f = Math.pow(10.0, d.toDouble()); return Math.round(x * f) / f }

    // ── quiz answers drawn like a real crowd might answer them ──────────────────────────────────
    val shapes = listOf("vshape", "aesthetic", "bulk", "athletic", "lean", "shredded")
    val groups = listOf("chest", "front_delts", "side_delts", "rear_delts", "lats", "traps", "lower_back", "arms", "abs", "legs", "glutes")

    fun answers(r: Random): PlannerAnswers {
        val shape = shapes.shuffled(r).take(if (r.nextDouble() < 0.3) 2 else 1)
        val pull = pick(r, listOf(-1 to 0.4, 0 to 0.25, 1 to 0.25, 2 to 0.1))
        val equip = listOf("chair" to 0.45, "towel" to 0.3, "backpack" to 0.35, "dumbbell" to 0.25, "band" to 0.15).filter { r.nextDouble() < it.second }.map { it.first }
        val injury = listOf("knee" to 0.1, "back" to 0.1, "shoulder" to 0.07, "impact" to 0.06).filter { r.nextDouble() < it.second }.map { it.first }
        val rated = groups.shuffled(r).take(pick(r, listOf(0 to 0.45, 1 to 0.25, 2 to 0.2, 3 to 0.1)))
        return PlannerAnswers(
            shape = shape, body = pick(r, listOf("average" to 0.6, "heavy" to 0.25, "skinny" to 0.15)),
            level = pick(r, listOf(0 to 0.5, 1 to 0.35, 2 to 0.15)), pull = pull,
            days = pick(r, listOf(2 to 0.15, 3 to 0.45, 4 to 0.25, 5 to 0.15)),
            time = pick(r, listOf(10 to 0.15, 20 to 0.4, 30 to 0.3, 45 to 0.15)),
            equip = equip, injury = injury, intensity = pick(r, listOf(0 to 0.25, 1 to 0.5, 2 to 0.25)),
            ratings = rated.associateWith { pick(r, listOf(1 to 0.15, 2 to 0.15, 4 to 0.3, 5 to 0.4)) },
        )
    }

    // ── JSON ───────────────────────────────────────────────────────────────────────────────────
    fun json(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        is Double -> if (v.isNaN() || v.isInfinite()) "null" else round(v, 4).toString()
        is Float -> json(v.toDouble())
        is Number, is Boolean -> v.toString()
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { json(it.key.toString()) + ":" + json(it.value) }
        is Iterable<*> -> v.joinToString(",", "[", "]") { json(it) }
        is IntArray -> json(v.toList())
        is DoubleArray -> json(v.toList())
        else -> json(v.toString())
    }
}
