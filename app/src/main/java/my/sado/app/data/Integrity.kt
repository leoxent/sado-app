package my.sado.app.data

import java.time.LocalDate

/**
 * "Tak larat" mid-set: what the next set asks for, and when to step down a rung.
 * The bottom of a move's range is its plan dose ("8–12" → 8); today's target can sit above it.
 */
object GiveUp {
    const val EXTRA_REST = 30
    const val BELOW_RANGE = 2 // the next set may ask for this much under the bottom of the range, no lower

    data class Next(val target: Int, val extraRest: Int)

    fun bottom(e: PlannedExercise): Int? = Progress.dose(e.base ?: e.reps)?.lo

    /** Short of the bottom of the range: the next set drops by the shortfall (so to what was done) and rests 30 s more. */
    fun after(e: PlannedExercise, target: Int, done: Int): Next? {
        val lo = bottom(e) ?: return null
        if (done >= lo) return null
        val shortfall = target - done
        return Next(maxOf(target - shortfall, lo - BELOW_RANGE, 1), EXTRA_REST)
    }

    /** Two failed sets in a row on the same move, with sets still to go: time to offer the rung below. */
    fun offerDrop(sets: List<SetLog>, planned: Int) = sets.size >= 2 && sets.size < planned && sets.takeLast(2).all { it.failed }

    /** The rung below on the move's ladder that the gear allows, or null. */
    fun rungBelow(id: String, a: PlannerAnswers?): String? {
        val key = Catalog.ladderOf(id) ?: return null
        val rungs = Catalog.ladders.getValue(key)
        return (rungs.indexOf(id) - 1 downTo 0).map { rungs[it] }.firstOrNull { PlanBuilder.usable(it, a) }
    }

    /**
     * Moves to deload next session: failed this session and in the last session that had them. A move done again
     * without failing comes off the list; one not done keeps waiting.
     */
    fun nextDeload(prev: Set<String>, done: List<ExerciseLog>, history: List<WorkoutLog>): Set<String> {
        val out = prev.toMutableSet()
        done.filter { !it.skipped && it.sets.isNotEmpty() }.forEach { e ->
            out.remove(e.id)
            val before = history.lastOrNull { l -> l.exercises.any { it.id == e.id && it.sets.isNotEmpty() } }?.exercises?.first { it.id == e.id && it.sets.isNotEmpty() }
            if (e.sets.any { it.failed } && before?.sets?.any { it.failed } == true) out += e.id
        }
        return out
    }
}

/**
 * Pressing Next without doing the work. Every set gets a minimum plausible time, and runs of skipped rests or bursts of
 * sets are flagged. Flagged sets stay in the log but never count for the ladder, reps, kg or weekly volume, so fake data
 * can't make the plan harder than the body can do.
 */
object Integrity {
    const val SEC_PER_REP = 2
    const val TOO_FAST = 0.4 // under this share of the plausible time is not a real set
    const val REST_SKIP_RUN = 3
    const val BURST_SETS = 5
    const val BURST_SECS = 60
    const val HR_OVER_REST = 15

    /** A rest counts as skipped when it ends with more than a third and more than 15 s still on the clock. */
    fun restSkipped(planned: Int, left: Int) = left > 15 && left * 3 > planned

    fun minSecs(s: SetLog, timed: Boolean, target: Int) = if (timed) target else s.reps * SEC_PER_REP

    /** The same logs with too-quick sets flagged; [timedTarget] gives a timed move's planned seconds. */
    fun flag(logs: List<ExerciseLog>, timedTarget: (String) -> Int?): List<ExerciseLog> {
        data class Ref(val e: Int, val s: Int)
        val order = logs.flatMapIndexed { ei, e -> e.sets.indices.map { Ref(ei, it) } }
        val bad = mutableSetOf<Ref>()
        order.forEach { r ->
            val e = logs[r.e]
            val set = e.sets[r.s]
            val t = timedTarget(e.id)
            val secs = set.secs ?: return@forEach
            if (secs < TOO_FAST * minSecs(set, t != null, t ?: 0)) bad += r
        }
        // rest cut short on 3+ sets in a row
        var run = mutableListOf<Ref>()
        (order + listOf(null)).forEach { r ->
            if (r != null && logs[r.e].sets[r.s].restSkipped) run += r
            else { if (run.size >= REST_SKIP_RUN) bad += run; run = mutableListOf() }
        }
        // 5+ sets done inside a minute
        val timed = order.filter { logs[it.e].sets[it.s].at != null }
        for (i in 0..timed.size - BURST_SETS) {
            val w = timed.subList(i, i + BURST_SETS)
            if (logs[w.last().e].sets[w.last().s].at!! - logs[w.first().e].sets[w.first().s].at!! < BURST_SECS) bad += w
        }
        return logs.mapIndexed { ei, e -> e.copy(sets = e.sets.mapIndexed { si, s -> if (Ref(ei, si) in bad) s.copy(flagged = true) else s }) }
    }

    data class Verdict(val flagged: Int, val total: Int, val hrFlat: Boolean) {
        val doubtful get() = (total > 0 && flagged * 2 > total) || hrFlat
    }

    /** A watch that barely moved above resting is the strongest sign; null when either number is missing. */
    fun hrFlat(avgHr: Int?, restingHr: Int?) = avgHr != null && restingHr != null && avgHr < restingHr + HR_OVER_REST

    fun verdict(flagged: List<ExerciseLog>, avgHr: Int?, restingHr: Int?): Verdict {
        val all = flagged.flatMap { it.sets }
        return Verdict(all.count { it.flagged }, all.size, hrFlat(avgHr, restingHr))
    }

    /** The sets that count for progression and volume. */
    fun real(e: ExerciseLog) = e.sets.filter { !it.flagged }
}

/** "Rehat jujur": an honest rest day keeps the streak, a few a week, instead of tapping through a fake session. */
object HonestRest {
    private fun monday(day: Long) = LocalDate.ofEpochDay(day).let { it.minusDays(it.dayOfWeek.value - 1L).toEpochDay() }

    fun usedThisWeek(s: AppState, day: Long = LocalDate.now().toEpochDay()) = monday(day).let { m -> s.honestRests.count { it in m..m + 6 } }

    fun left(s: AppState, day: Long = LocalDate.now().toEpochDay()) = (s.settings.honestRestPerWeek - usedThisWeek(s, day)).coerceAtLeast(0)

    /** Allowed when the week still has some, today isn't one already and nothing was trained today. */
    fun canTake(s: AppState, day: Long = LocalDate.now().toEpochDay()) =
        left(s, day) > 0 && day !in s.honestRests && s.logs.none { it.epochDay == day && it.xp > 0 } && s.sports.none { it.epochDay == day }

    fun take(s: AppState, day: Long = LocalDate.now().toEpochDay()) = if (canTake(s, day)) s.copy(honestRests = s.honestRests + day) else s

    /** The rest days that count, at most the weekly allowance per Mon–Sun week. */
    fun counted(s: AppState): Set<Long> =
        s.honestRests.distinct().sorted().groupBy(::monday).values.flatMap { it.take(s.settings.honestRestPerWeek) }.toSet()
}
