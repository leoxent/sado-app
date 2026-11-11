package my.sado.app.data

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/** One weekly check: what the week looked like and what was changed because of it. */
@Serializable
data class CheckIn(
    val epochDay: Long,
    val avgKg: Float? = null, // this week's average weigh-in
    val prevAvgKg: Float? = null, // the week before's
    val lo: Float = 0f, // the weekly change the goal aims for, kg
    val hi: Float = 0f,
    val mode: String = "",
    val offTrack: Int = 0, // -1 below the range, 1 above, 0 inside or no data
    val weeksOff: Int = 0, // weeks in a row off the same way, reset after a change
    val kcalChange: Int = 0, // what this check changed the daily calories by
    val sessions: Int = 0,
    val planned: Int = 0,
    val proteinAvg: Int? = null, // average on days with food logged, when 3+ days were
    val proteinTarget: Int? = null,
    val seen: Boolean = false,
    val rest: Boolean = false, // rest mode touched this week, so nothing was judged
    val floored: Boolean = false, // calories would have gone down, but they are at the minimum
    val noCut: Boolean = false, // weight is above the range, but no deficit is allowed right now
)

/**
 * Every 7 days: the week's average weight against the one before, sessions done, protein eaten.
 * One week off target can be water or a salty dinner, so calories only move after two weeks off the same way,
 * by 150 kcal, and never more than 600 from the computed target in total, and never under the minimum in [Care].
 * A week touched by rest mode changes nothing: illness, exams, a new baby and fasting all move the scale on their own.
 */
object WeeklyCheck {
    const val STEP = 150
    const val CAP = 600

    private fun avg(s: AppState, from: Long, to: Long) =
        s.weights.filter { it.epochDay in from..to }.map { it.kg }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    fun due(s: AppState, today: Long): Boolean {
        if (s.profile == null) return false
        val last = s.checkins.lastOrNull()?.epochDay
        if (last != null) return today - last >= 7
        // the first one a week after the plan (or the first log) started
        val start = s.planStart ?: (s.logs.map { it.epochDay } + s.weights.map { it.epochDay }).minOrNull() ?: return false
        return today - start >= 7
    }

    /** The expected weekly change in kg for a mode, as a range. */
    fun range(mode: String, kg: Float): Pair<Float, Float> = when (mode) {
        "Cut" -> -0.01f * kg to -0.0025f * kg
        "Lean bulk" -> 0.1f to 0.5f
        else -> -0.3f to 0.3f
    }

    fun run(s: AppState, today: Long): AppState {
        val p = s.profile ?: return s
        val now = avg(s, today - 6, today)
        val before = avg(s, today - 13, today - 7)
        // targets from this week's weight, before any change made here
        val prof = if (now != null) p.copy(weightKg = (now * 10).roundToInt() / 10f) else p
        val t = Nutrition.targets(s, prof)
        val (lo, hi) = range(t.mode, prof.weightKg)
        val change = if (now != null && before != null) now - before else null
        val off = when { change == null -> 0; change < lo -> -1; change > hi -> 1; else -> 0 }
        val prev = s.checkins.lastOrNull()
        val rest = Care.restDaysIn(s, today - 6, today) > 0
        val noCut = Care.noCut(s) && off > 0
        val weeks = if (rest || noCut) 0 else if (off != 0 && prev?.offTrack == off) prev.weeksOff + 1 else if (off != 0) 1 else 0
        // below the range means eat more, above means eat less
        val step = if (weeks >= 2) (-off * STEP) else 0
        val wanted = (s.kcalAdjust + step).coerceIn(-CAP, CAP)
        // going down, count only what really came off: at the minimum the target stays where it is
        val applied = if (step < 0) Nutrition.targets(s.copy(kcalAdjust = wanted), prof).kcal - t.kcal else wanted - s.kcalAdjust
        val adjust = s.kcalAdjust + applied

        val week = s.logs.filter { it.epochDay in today - 6..today && !it.quit }
        val planned = s.plan?.week?.count { it != "Rest" } ?: 0
        val days = s.meals.filter { it.epochDay in today - 6..today }.groupBy { it.epochDay }
        val proteinAvg = if (days.size >= 3) days.values.map { d -> d.sumOf { m -> m.items.sumOf { it.protein.toDouble() } } }.average().roundToInt() else null

        val c = CheckIn(
            today, now, before, lo, hi, t.mode, off, if (applied != 0) 0 else weeks, applied,
            week.size, planned, proteinAvg, t.protein, rest = rest, floored = step < 0 && applied == 0, noCut = noCut,
        )
        return s.copy(profile = prof, kcalAdjust = adjust, checkins = s.checkins + c)
    }

    /** Takes back the calorie change a check made. */
    fun undo(s: AppState, c: CheckIn): AppState = s.copy(
        kcalAdjust = s.kcalAdjust - c.kcalChange,
        checkins = s.checkins.map { if (it == c) it.copy(kcalChange = 0, seen = true) else it },
    )

    enum class Tone { Good, Warn, Info }

    /** The check in plain words: one line each for weight, training and protein. */
    fun lines(c: CheckIn): List<Pair<Tone, String>> {
        val out = mutableListOf<Pair<Tone, String>>()
        val kg = { x: Float -> "%.1f".format(x) }
        val signed = { x: Float -> (if (x >= 0) "+" else "−") + kg(abs(x)) }
        if (c.rest) return listOf(Tone.Info to "Mod rehat minggu ni. Kalori tak diubah, dan berat naik turun masa ni perkara biasa.")
        val aim = "${signed(c.lo)} hingga ${signed(c.hi)} kg"
        when {
            c.avgKg == null || c.prevAvgKg == null -> out += Tone.Info to
                (if (c.avgKg != null) "Berat purata ${kg(c.avgKg)} kg. Minggu depan baru boleh banding." else "Takde timbang 2 minggu ni.") +
                " Timbang 2–3 kali seminggu, pagi lepas tandas, supaya kalori boleh disesuaikan."
            c.noCut -> out += Tone.Info to "Berat ${signed(c.avgKg - c.prevAvgKg)} kg seminggu. Defisit kalori dimatikan buat masa ni, jadi app tak turunkan kalori. Tanya doktor kalau risau."
            c.floored -> out += Tone.Warn to "Berat ${signed(c.avgKg - c.prevAvgKg)} kg seminggu, di luar sasaran ${c.mode} ($aim), tapi kalori kau dah di had minimum, jadi app tak turunkan lagi. Kalau berat tak turun berminggu-minggu, jumpa doktor atau dietitian."
            c.kcalChange != 0 -> out += Tone.Warn to "Berat ${signed(c.avgKg - c.prevAvgKg)} kg seminggu, 2 minggu berturut di luar sasaran ${c.mode} ($aim). " +
                "Kalori harian ${if (c.kcalChange > 0) "dinaikkan" else "diturunkan"} ${abs(c.kcalChange)} kcal."
            c.offTrack != 0 -> out += Tone.Info to "Berat ${signed(c.avgKg - c.prevAvgKg)} kg seminggu, sasaran ${c.mode} $aim. " +
                "Seminggu boleh jadi air je. Kalau minggu depan sama, kalori ${if (c.offTrack < 0) "dinaikkan" else "diturunkan"} $STEP kcal."
            else -> out += Tone.Good to "Berat ${signed(c.avgKg - c.prevAvgKg)} kg seminggu, dalam sasaran ${c.mode} ($aim). Teruskan."
        }
        if (c.planned > 0) out += when {
            c.sessions >= c.planned -> Tone.Good to "${c.sessions}/${c.planned} sesi siap. Mantap."
            c.sessions * 2 >= c.planned -> Tone.Info to "${c.sessions}/${c.planned} sesi. Cuba habiskan semua minggu ni."
            else -> Tone.Warn to "${c.sessions}/${c.planned} sesi. Kalau minggu memang padat, kurangkan hari dalam plan supaya senang konsisten."
        }
        if (c.proteinAvg != null && c.proteinTarget != null) out += if (c.proteinAvg >= c.proteinTarget * 0.9f)
            Tone.Good to "Protein purata ${c.proteinAvg}g sehari, target ${c.proteinTarget}g. Cukup."
        else Tone.Warn to "Protein purata ${c.proteinAvg}g sehari, target ${c.proteinTarget}g. Kurang ${c.proteinTarget - c.proteinAvg}g: tambah telur, ayam atau susu."
        else out += Tone.Info to "Log makan sekurang-kurangnya 3 hari seminggu supaya protein boleh disemak."
        return out
    }
}
