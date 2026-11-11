package my.sado.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import my.sado.app.R
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// 15 levels: 5 bodies × 3 tiers. Every level needs XP *and* weeks of training, so the character
// can't outrun the real body (visible change takes ~8–12 weeks, "sado" months, beast mode a year+).
// The top body also needs a strength test that gets harder per tier.
data class Stage(
    val index: Int, val body: Int, val tier: Int, val at: Int, val minWeeks: Int,
    val testPush: Int? = null, val testPull: Int? = null,
    val heavy: Boolean = false, // the Gempal → Bangkit path for users who start big
) {
    val bodyName get() = if (heavy && body < 2) Progress.heavyNames[body] else Progress.bodyNames[body]
    val name get() = bodyName + " " + listOf("I", "II", "III")[tier]
    /** for the voice: roman numerals read badly, "tier 2" becomes "tier dua" */
    val spoken get() = "$bodyName tier ${tier + 1}"
    val image get() = if (heavy && index < 6) Progress.heavyImages[index] else Progress.levelImages[index]
    val needTest get() = testPush != null
}

data class StreakInfo(val days: Int, val thisWeek: Int)

object Progress {
    val bodyNames = listOf("Kurus", "Berisi", "Fit", "Sado", "Beast mode")
    val heavyNames = listOf("Gempal", "Bangkit")
    val heavyImages = listOf(R.drawable.gempal1, R.drawable.gempal2, R.drawable.gempal3, R.drawable.bangkit1, R.drawable.bangkit2, R.drawable.bangkit3)
    // one picture per level: each tier adds a piece of kit and keeps the ones before it
    val levelImages = listOf(
        R.drawable.tier1, R.drawable.tier2, R.drawable.tier3, R.drawable.tier4, R.drawable.tier5,
        R.drawable.tier6, R.drawable.tier7, R.drawable.tier8, R.drawable.tier9, R.drawable.tier10,
        R.drawable.tier11, R.drawable.tier12, R.drawable.tier13, R.drawable.tier14, R.drawable.tier15,
    )

    // weeks assume ~3 sessions a week; XP is ~300 per such week, so XP and time line up
    private val weeks = listOf(0, 1, 2, 4, 6, 8, 10, 13, 16, 20, 26, 32, 40, 52, 72)
    private val beastTests = listOf(30 to 10, 40 to 12, 50 to 15)
    val stages = ladder(weeks, heavy = false)

    // Starting big, the first visible change (a smaller belly) needs ~5% of body weight gone, which at the
    // guideline 0.5–1% a week is 5–10 weeks; so Gempal → Bangkit is slower than Kurus → Berisi. Once the fat
    // comes off, the muscle that carried the extra weight shows quickly, so Bangkit → Fit is faster. Both reach
    // Fit I at week 10, so neither path is longer overall.
    private val heavyWeeks = listOf(0, 2, 4, 6, 7, 8) + weeks.drop(6)
    val heavyStages = ladder(heavyWeeks, heavy = true)

    private fun ladder(ws: List<Int>, heavy: Boolean) = ws.mapIndexed { i, w ->
        val test = if (i >= 12) beastTests[i - 12] else null
        Stage(i, body = i / 3, tier = i % 3, at = w * 300, minWeeks = w, testPush = test?.first, testPull = test?.second, heavy = heavy)
    }

    /** Big start: chosen in Settings, else the planner's "Berisi" answer, waist:height ≥ 0.53 or BMI ≥ 27. */
    fun heavyPath(s: AppState): Boolean = when (s.settings.bodyPath) {
        "gempal" -> true
        "kurus" -> false
        else -> {
            val p = s.profile
            val whtr = p?.waistCm?.takeIf { it > 0f }?.let { it / p.heightCm }
            val bmi = p?.let { it.weightKg / ((it.heightCm / 100f) * (it.heightCm / 100f)) }
            s.answers?.body == "heavy" || (whtr != null && whtr >= 0.53f) || (whtr == null && bmi != null && bmi >= 27f)
        }
    }

    fun stagesFor(s: AppState) = if (heavyPath(s)) heavyStages else stages

    // Low-impact: at BMI 30+ or waist-to-height 0.6+ every landing loads the knees and ankles with several times the
    // body weight, so jumping moves (jumping jack, high knees, burpee) leave the plan and the warm-up marches instead.
    fun lowImpact(p: Profile?): Boolean {
        p ?: return false
        val whtr = p.waistCm?.takeIf { it > 0f }?.let { it / p.heightCm }
        return bmi(p) >= 30f || (whtr != null && whtr >= 0.6f)
    }

    fun lowImpact(s: AppState) = lowImpact(s.profile) || "impact" in s.answers?.injury.orEmpty()

    const val SHORTCUT_LOSS = 0.05f // 5% of starting weight
    const val SHORTCUT_DAYS = 21 // measured at least three weeks after the first weigh-in

    /** Fraction of starting weight lost, counting only weigh-ins at least three weeks after the first. */
    fun weightLost(s: AppState): Float {
        val first = s.weights.minByOrNull { it.epochDay } ?: return 0f
        val later = s.weights.filter { it.epochDay - first.epochDay >= SHORTCUT_DAYS }.maxByOrNull { it.epochDay } ?: return 0f
        return ((first.kg - later.kg) / first.kg).coerceAtLeast(0f)
    }

    /** On the heavy path, losing 5% of starting weight opens Bangkit I early: real change beats waiting. */
    fun shortcut(st: Stage, s: AppState) = st.heavy && st.index == 3 && weightLost(s) >= SHORTCUT_LOSS
    const val MIN_PER_WEEK = 2
    const val BEAST_PUSHUPS = 30
    const val BEAST_PULLUPS = 10

    // the old 5-level ladder, kept only to work out where existing users stood before the change
    fun oldLevel(s: AppState): Int {
        val w = weeksActive(s)
        val test = s.tests.any { it.pushups >= 30 || (it.pullups ?: 0) >= 10 }
        return when {
            s.xp >= 2000 && w >= 24 && test -> 4
            s.xp >= 1000 && w >= 8 -> 3
            s.xp >= 600 -> 2
            s.xp >= 300 -> 1
            else -> 0
        }
    }

    fun stage(xp: Int): Stage = stages.last { xp >= it.at }

    fun firstDay(s: AppState): Long? = (s.logs.map { it.epochDay } + s.sports.map { it.epochDay }).minOrNull()

    fun weeksActive(s: AppState, today: LocalDate = LocalDate.now()): Int =
        s.creditWeeks + (firstDay(s)?.let { (ChronoUnit.DAYS.between(LocalDate.ofEpochDay(it), today) / 7).toInt() } ?: 0)

    // ── placement ("Buktikan!") ─────────────────────────────────────────────────────────────────
    // Offered once at setup. One unbroken set of push-ups, rest, then one plank. Passing puts the user at
    // level 4 with that level's XP and weeks, so someone who already trains doesn't wait a month as Kurus.
    // Push-ups: 25 unbroken sits in the "average" band of ACSM-style norms for men 20–39 (17–29), well past someone
    // who never trains; women about half, 40+ gets 25% off. Plank 75 s, between the coach "beginner" 20–45 s and the
    // 95–110 s median of trained students (56 s at 40+). Berisi is a fuller body than Kurus, so on that path the BMI
    // has to fit too (20+): a strong but slim user still starts as Kurus, which is the honest picture.
    data class ProveBar(val pushups: Int, val plankSec: Int)

    const val PROVE_MIN_BMI = 20f

    fun bmi(p: Profile) = p.weightKg / ((p.heightCm / 100f) * (p.heightCm / 100f))

    fun proveBar(p: Profile?): ProveBar {
        val older = (p?.age ?: 25) >= 40
        val push = if (p?.sex == 1) 13 else 25
        return ProveBar(if (older) Math.round(push * 0.75f) else push, if (older) 56 else 75)
    }

    fun provePassed(p: Profile?, pushups: Int, plankSec: Int) = proveBar(p).let { pushups >= it.pushups && plankSec >= it.plankSec }

    /** Not offered at BMI 30+ (pushing to failure is not wise), nor on the Kurus path below BMI 20 (the body is not Berisi yet). */
    fun proveOffered(s: AppState): Boolean {
        val b = s.profile?.let { bmi(it) } ?: return true
        return b < 30f && (heavyPath(s) || b >= PROVE_MIN_BMI)
    }

    /** Applies a pass: level 4 of the current path, with its XP and weeks. */
    fun place(s: AppState): AppState {
        val st = stagesFor(s)[3]
        return s.copy(placed = true, xp = maxOf(s.xp, st.at), peakXp = maxOf(s.peakXp, st.at), creditWeeks = st.minWeeks)
    }

    fun testPassed(s: AppState, st: Stage) =
        !st.needTest || s.tests.any { it.pushups >= st.testPush!! || (it.pullups ?: 0) >= st.testPull!! }

    fun unlocked(st: Stage, s: AppState, xp: Int = s.xp): Boolean =
        xp >= st.at && weeksActive(s) >= st.minWeeks && testPassed(s, st) || shortcut(st, s)

    // users who were already high on the old ladder don't drop, but only while their XP still
    // earns that old level and their weeks of training still back it: the old ladder gave Berisi
    // at 300 XP, so without the weeks check a fresh start would jump straight to Berisi I
    fun stageFor(s: AppState, xp: Int = s.xp): Stage {
        val weeks = weeksActive(s)
        val list = stagesFor(s)
        val floor = minOf(s.levelFloor, oldLevel(s.copy(xp = xp)) * 3, list.last { weeks >= it.minWeeks }.index)
        return list[maxOf(list.last { unlocked(it, s, xp) }.index, floor, if (s.placed) 3 else 0)]
    }

    // ── time off ──────────────────────────────────────────────────────────────────────────────
    // Detraining research: strength and muscle hold for about 3 weeks off (McMaster 2013 review,
    // Ogasawara 2013), endurance starts slipping after about 2 weeks (Coyle 1984), and lost muscle
    // comes back faster than it was first built (Seaborne 2018). So a week off is just rest, after
    // that the character looks tired, and from day 21 he drops a tier every 14 days, never below
    // tier I of his body. One session resets the clock; XP counts double until he is back at his best.
    const val TIRED_DAYS = 8
    const val DECAY_DAYS = 21
    const val DECAY_EVERY = 14

    fun tiredImage(st: Stage): Int = if (st.heavy && st.index < 6) heavyTiredImages[st.index] else tiredImages[st.index]

    val heavyTiredImages = listOf(R.drawable.tired_g1, R.drawable.tired_g2, R.drawable.tired_g3, R.drawable.tired_b1, R.drawable.tired_b2, R.drawable.tired_b3)

    val tiredImages = listOf(
        R.drawable.tired1, R.drawable.tired2, R.drawable.tired3, R.drawable.tired4, R.drawable.tired5,
        R.drawable.tired6, R.drawable.tired7, R.drawable.tired8, R.drawable.tired9, R.drawable.tired10,
        R.drawable.tired11, R.drawable.tired12, R.drawable.tired13, R.drawable.tired14, R.drawable.tired15,
    )

    fun idleDays(s: AppState, today: LocalDate = LocalDate.now()): Int? =
        activeDays(s).maxOrNull()?.let { (today.toEpochDay() - it).toInt() - Care.restDaysIn(s, it + 1, today.toEpochDay()) }

    // days in rest mode never count as time off, so nothing drops while it is on
    fun isTired(s: AppState, today: LocalDate = LocalDate.now()) = Care.active(s, today.toEpochDay()) == null && (idleDays(s, today) ?: 0) >= TIRED_DAYS

    private fun stepsDue(idle: Int) = if (idle < DECAY_DAYS) 0 else 1 + (idle - DECAY_DAYS) / DECAY_EVERY

    /** days until the next tier drop, or null when there is nothing left to lose in this body */
    fun daysToDrop(s: AppState, today: LocalDate = LocalDate.now()): Int? {
        val idle = idleDays(s, today) ?: return null
        if (stageFor(s).tier == 0 || Care.active(s, today.toEpochDay()) != null) return null
        return if (idle < DECAY_DAYS) DECAY_DAYS - idle else DECAY_EVERY - (idle - DECAY_DAYS) % DECAY_EVERY
    }

    /** Applies any tier drops that are due. Each drop sets XP to the start of the tier below. */
    fun applyDecay(s: AppState, today: LocalDate = LocalDate.now()): AppState {
        val last = activeDays(s).maxOrNull() ?: return s
        val due = stepsDue(idleDays(s, today) ?: return s)
        val done = if (s.decayFrom == last) s.decaySteps else 0
        if (due <= done) return s
        var out = s.copy(peakXp = maxOf(s.peakXp, s.xp))
        repeat(due - done) {
            val cur = stageFor(out)
            if (cur.tier > 0) {
                val to = stagesFor(out)[cur.index - 1]
                out = out.copy(xp = minOf(out.xp, to.at), levelFloor = minOf(out.levelFloor, to.index))
            }
        }
        return out.copy(decayFrom = last, decaySteps = due)
    }

    /** dropped by time off and not yet back */
    fun dropped(s: AppState) = s.decaySteps > 0 && s.xp < s.peakXp

    /** extra XP a session earns while below the best XP reached: double, up to that best */
    fun comebackBonus(s: AppState, xp: Int) = minOf(xp, maxOf(s.peakXp, s.xp) - s.xp).coerceAtLeast(0)

    fun next(s: AppState): Stage? = stagesFor(s).firstOrNull { it.index > stageFor(s).index }

    fun requirement(st: Stage, s: AppState): String {
        val parts = mutableListOf<String>()
        if (s.xp < st.at) parts += "lagi ${st.at - s.xp} XP"
        if (weeksActive(s) < st.minWeeks) parts += "${st.minWeeks - weeksActive(s)} minggu lagi konsisten"
        if (!testPassed(s, st)) parts += "ujian: ${st.testPush} push-up atau ${st.testPull} pull-up"
        if (shortcut(st, s)) return "Dah layak: berat turun ${(weightLost(s) * 100).toInt()}%"
        val text = parts.joinToString(" · ").ifBlank { "Dah layak" }
        return if (st.heavy && st.index == 3 && parts.isNotEmpty()) "$text, atau turun 5% berat" else text
    }

    const val XP_PER_EXERCISE = 30
    const val XP_PER_SET = 10

    // full exercises earn 30; an unfinished one earns 10 per finished set; skipped ones earn nothing
    fun sessionXp(plan: List<PlannedExercise>, logs: List<ExerciseLog>): Int =
        logs.sumOf { log ->
            val p = plan.firstOrNull { it.id == log.id } ?: return@sumOf 0
            when {
                log.skipped -> 0
                log.sets.size >= p.sets -> XP_PER_EXERCISE
                else -> XP_PER_SET * log.sets.size
            }
        }

    fun activeDays(s: AppState): Set<Long> = (s.logs.map { it.epochDay } + s.sports.map { it.epochDay }).toSet()

    // Streak counts calendar days since the run began. A run survives as long as every finished
    // Mon–Sun week has at least MIN_PER_WEEK active days; the week in progress can never break it,
    // and neither can the very first (partial) week.
    fun streak(s: AppState, today: LocalDate = LocalDate.now()): StreakInfo {
        val active = activeDays(s)
        val monday = today.minusDays(today.dayOfWeek.value - 1L)
        val thisWeek = active.count { it in monday.toEpochDay()..today.toEpochDay() }
        val first = active.minOrNull() ?: return StreakInfo(0, thisWeek)
        val firstMonday = LocalDate.ofEpochDay(first).let { it.minusDays(it.dayOfWeek.value - 1L) }
        var start = first
        var w = firstMonday.plusWeeks(1)
        while (w < monday) {
            val days = active.count { it in w.toEpochDay()..w.plusDays(6).toEpochDay() }
            // a failed week resets the run; it restarts on the next active day after that week
            if (days < MIN_PER_WEEK && Care.restDaysIn(s, w.toEpochDay(), w.plusDays(6).toEpochDay()) == 0) start = active.filter { it > w.plusDays(6).toEpochDay() }.minOrNull() ?: Long.MAX_VALUE
            w = w.plusWeeks(1)
        }
        val n = if (start == Long.MAX_VALUE || start > today.toEpochDay()) 0 else (today.toEpochDay() - start + 1).toInt()
        return StreakInfo(n, thisWeek)
    }

    fun todayTag(state: AppState, today: LocalDate = LocalDate.now()): String? =
        state.plan?.week?.get(today.dayOfWeek.value - 1)

    // every 5th week of a plan is a lighter week: one set fewer per move
    fun isDeloadWeek(s: AppState, today: LocalDate = LocalDate.now()): Boolean {
        val start = s.planStart ?: return false
        val week = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(start), today) / 7
        return week > 0 && (week + 1) % 5 == 0L
    }

    fun testDue(s: AppState, today: LocalDate = LocalDate.now()): Boolean {
        val last = s.tests.maxOfOrNull { it.epochDay }
        return if (last == null) s.logs.size >= 2 else today.toEpochDay() - last >= 28
    }

    // ── 4-week test reward ───────────────────────────────────────────────────────────────────
    // Showing up is worth 50 XP. Beating the last test adds 5 XP per extra push-up or pull-up and
    // 1 XP per extra 5 s of plank, capped at +100, so the honest number always pays more than skipping.
    const val TEST_XP = 50
    const val TEST_BONUS_CAP = 100

    data class TestReward(val base: Int, val bonus: Int, val records: List<String>) { val total get() = base + bonus }

    fun testReward(prev: FitnessTest?, now: FitnessTest): TestReward {
        if (prev == null) return TestReward(TEST_XP, 0, emptyList())
        val push = (now.pushups - prev.pushups).coerceAtLeast(0)
        val plank = (now.plankSec - prev.plankSec).coerceAtLeast(0)
        val pull = if (now.pullups != null && prev.pullups != null) (now.pullups - prev.pullups).coerceAtLeast(0) else 0
        val records = listOfNotNull("push".takeIf { push > 0 }, "plank".takeIf { plank > 0 }, "pull".takeIf { pull > 0 })
        return TestReward(TEST_XP, minOf(TEST_BONUS_CAP, push * 5 + plank / 5 + pull * 5), records)
    }

    val muscleGroups = linkedMapOf(
        "Belakang" to setOf("lats"), "Bahu" to setOf("delts", "traps"), "Dada" to setOf("chest"),
        "Lengan" to setOf("biceps", "triceps", "forearms"), "Perut" to setOf("abs", "obliques", "lowerback"),
        "Kaki" to setOf("quads", "hamstrings", "calves", "glutes"),
    )

    // hard sets per muscle group this week (Mon-Sun); ~10+ sits in the productive range of the volume data
    fun weeklySets(s: AppState, today: LocalDate = LocalDate.now()): Map<String, Int> {
        val monday = today.minusDays(today.dayOfWeek.value - 1L).toEpochDay()
        val out = muscleGroups.keys.associateWith { 0 }.toMutableMap()
        s.logs.filter { it.epochDay >= monday }.forEach { log ->
            log.exercises.filter { !it.skipped }.forEach { e ->
                val m = Catalog.moves.firstOrNull { it.id == e.id } ?: return@forEach
                muscleGroups.forEach { (g, ids) -> if (m.muscles.any { it in ids }) out[g] = out.getValue(g) + e.sets.size }
            }
        }
        return out
    }

    fun weeklyActiveMinutes(s: AppState, today: LocalDate = LocalDate.now()): Int {
        val monday = today.minusDays(today.dayOfWeek.value - 1L).toEpochDay()
        return s.logs.filter { it.epochDay >= monday }.sumOf { it.minutes } + s.sports.filter { it.epochDay >= monday }.sumOf { it.minutes }
    }

    // best single set per exercise across all sessions
    fun records(s: AppState): List<Pair<Move, Int>> =
        s.logs.flatMap { it.exercises }.filter { it.sets.isNotEmpty() }
            .groupBy { it.id }
            .mapNotNull { (id, logs) -> Catalog.moves.firstOrNull { it.id == id }?.let { m -> m to logs.maxOf { l -> l.sets.maxOf { it.reps } } } }
            .sortedByDescending { it.second }

    // Double progression. A dose label is a rep range "8–12", a fixed count "15" or seconds "40s";
    // max-rep sets, negatives and intervals have no number to climb and give null.
    data class Dose(val lo: Int, val hi: Int, val timed: Boolean)

    fun dose(reps: String): Dose? {
        Regex("(\\d+)s").matchEntire(reps)?.let { val n = it.groupValues[1].toInt(); return Dose(n, n, true) }
        Regex("(\\d+)(?:–(\\d+))?").matchEntire(reps)?.let { m ->
            val lo = m.groupValues[1].toInt()
            return Dose(lo, m.groupValues[2].toIntOrNull() ?: lo, false)
        }
        return null
    }

    const val SECONDS_STEP = 5

    fun climbs(id: String) = Catalog.ladderOf(id)?.let { Catalog.ladders.getValue(it).last() != id } == true

    // the highest target a move reaches: a ladder move that can still climb hands over to the next rung at the
    // top of its range; anything else keeps going to half again (reps) or double (seconds, at most 2 minutes)
    fun top(id: String, d: Dose): Int = when {
        d.timed -> maxOf(d.lo, minOf(d.lo * 2, 120))
        Catalog.moves.firstOrNull { it.id == id }?.weighted == true -> d.hi // past the top of its range a weighted move adds kg, not reps

        climbs(id) && d.hi > d.lo -> d.hi
        else -> d.hi + d.hi / 2
    }

    private fun unit(d: Dose) = if (d.timed) SECONDS_STEP else 1

    /** The steps a move's target can sit at: down to half the bottom of its range, up to its top. */
    fun stepRange(id: String, d: Dose): IntRange = -(d.lo / 2) / unit(d)..(top(id, d) - d.lo) / unit(d)

    const val MAX_SETS = 8

    /** Today's dose: the bottom of the range plus what the move has earned, and any sets the user added or took off. */
    fun boosted(e: PlannedExercise, boosts: Map<String, Int>, extraSets: Map<String, Int> = emptyMap()): PlannedExercise {
        val sets = (e.sets + (extraSets[e.id] ?: 0)).coerceIn(1, MAX_SETS)
        val d = dose(e.reps) ?: return e.copy(sets = sets, baseSets = e.sets)
        val t = d.lo + (boosts[e.id] ?: 0).coerceIn(stepRange(e.id, d)) * unit(d)
        return e.copy(sets = sets, reps = if (d.timed) "${t}s" else "$t", target = t, base = e.reps, baseSets = e.sets)
    }

    // the effort answer after a move's last set: Sangat easy = 4, Easy = 3, Ngam = 1, Susah = 0 reps in reserve
    fun stepFor(effort: Int) = when { effort >= 4 -> 2; effort >= 3 -> 1; effort >= 1 -> 0; else -> -1 }

    data class Climb(val ladders: Map<String, LadderProgress>, val boosts: Map<String, Int>, val loads: Map<String, Float> = emptyMap())

    const val START_KG = 2.5f // a weighted move starts with about two full water bottles in the bag
    const val KG_STEP = 2.5f

    fun load(loads: Map<String, Float>, id: String) = loads[id] ?: START_KG

    // After a session, for each move done in full: Sangat easy adds 2 reps (or 5s steps), Easy 1, Ngam keeps it, Susah takes 1 off.
    // A ladder move at the top of its range answered Easy or better goes up a rung, and the new move starts at the bottom;
    // a rung the gear rules out ([usable]) is not climbed onto. A weighted move at the top of its range adds KG_STEP and
    // starts again at the bottom. Moves with no number to climb (max reps, negatives, the user's own workouts) climb the ladder on Easy or better.
    fun climb(
        ladders: Map<String, LadderProgress>, boosts: Map<String, Int>, plan: List<PlannedExercise>, logs: List<ExerciseLog>,
        loads: Map<String, Float> = emptyMap(), usable: (String) -> Boolean = { true },
    ): Climb {
        val outL = ladders.toMutableMap()
        val outB = boosts.toMutableMap()
        val outK = loads.toMutableMap()
        for (log in logs) {
            val p = plan.firstOrNull { it.id == log.id } ?: continue
            val effort = log.sets.lastOrNull()?.rir ?: continue
            if (log.skipped || log.sets.size < p.sets) continue
            val key = Catalog.ladderOf(log.id)
            val prog = key?.let { outL[it] } ?: LadderProgress()
            val rungs = key?.let { Catalog.ladders.getValue(it) }
            val onRung = rungs != null && rungs.getOrNull(prog.rung) == log.id
            val canClimb = onRung && prog.rung < rungs!!.lastIndex && usable(rungs[prog.rung + 1])
            val weighted = Catalog.moves.firstOrNull { it.id == log.id }?.weighted == true
            val d = p.base?.let(::dose)
            val now = outB[log.id] ?: 0
            // what was carried today is the load from now on, whatever the user changed it to in the session
            if (weighted) log.sets.mapNotNull { it.kg }.maxOrNull()?.let { outK[log.id] = it }
            when {
                d == null -> if (effort >= 3 && canClimb) outL[key!!] = LadderProgress(prog.rung + 1)
                now >= stepRange(log.id, d).last && effort >= 3 && canClimb -> {
                    outL[key!!] = LadderProgress(prog.rung + 1)
                    outB.remove(rungs!![prog.rung + 1])
                }
                weighted && now >= stepRange(log.id, d).last && effort >= 3 -> {
                    outK[log.id] = load(outK, log.id) + KG_STEP
                    outB.remove(log.id)
                }
                else -> (now + stepFor(effort)).coerceIn(stepRange(log.id, d)).let { if (it == 0) outB.remove(log.id) else outB[log.id] = it }
            }
        }

        return Climb(outL, outB, outK)
    }


    // MET values approximate the Compendium of Physical Activities; legs flags sports that load the legs hard
    data class Sport(val name: String, val met: Double, val legs: Boolean)
    val sports = listOf(
        Sport("Futsal", 7.0, true), Sport("Bola sepak", 7.0, true), Sport("Badminton", 5.5, false), Sport("Larian", 8.0, true),
        Sport("Basikal", 6.8, true), Sport("Renang", 6.0, false), Sport("Hiking", 6.0, true), Sport("Jalan kaki", 3.5, false),
        Sport("Bola keranjang", 6.5, true), Sport("Gym", 5.0, false), Sport("Lain-lain", 5.0, false),
    )

    fun sportKcal(sport: Sport, minutes: Int, level: Int, kg: Float): Int =
        (sport.met * listOf(0.8, 1.0, 1.25)[level] * kg * minutes / 60.0 / 10).toInt() * 10

    fun sportXp(minutes: Int) = minOf(60, minutes / 15 * 10)
}

@Serializable
data class WgerItem(
    val id: Int,
    val name: String,
    val category: String = "",
    val equipment: List<String> = emptyList(),
    val muscles: List<String> = emptyList(),
    val secondary: List<String> = emptyList(),
    val description: String = "",
    val license: String = "",
    val author: String = "",
    val image: String? = null,
    val video: String? = null,
)

@Serializable
private data class WgerFile(val source: String, val fetched: String, val items: List<WgerItem>)

object Wger {
    private var cache: List<WgerItem>? = null
    var fetched: String = ""
        private set

    fun items(context: Context): List<WgerItem> = cache ?: run {
        val text = context.assets.open("wger_exercises.json").bufferedReader().use { it.readText() }
        val file = Json { ignoreUnknownKeys = true }.decodeFromString(WgerFile.serializer(), text)
        fetched = file.fetched
        file.items.also { cache = it }
    }

    fun find(context: Context, name: String?): WgerItem? =
        name?.let { n -> items(context).firstOrNull { it.name.equals(n, ignoreCase = true) } }
}
