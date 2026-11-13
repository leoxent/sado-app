package my.sado.app.data

import android.content.Context
import kotlin.math.abs

/**
 * What the heart said during a session, from the live broadcast or from Health Connect after the watch synced. How
 * much it can say depends on how dense the readings are: Strength mode on the watch gives a reading every few seconds,
 * all-day tracking about one a minute or two, which is enough for the session but not for single sets.
 */
object HrAnalysis {
    const val DENSE_GAP_S = 15 // median gap between readings at or under this: per-set numbers are trustworthy
    const val MIN_POINTS = 5 // fewer readings than this say nothing about a session
    const val STALE_MS = 2 * 3_600_000L // the Home bar calls a reading older than this old

    enum class State(val label: String) { Rest("Rehat"), Active("Aktif"), Workout("Senaman") }

    fun maxHr(age: Int) = 220 - age

    fun state(bpm: Int, resting: Int?, age: Int): State {
        val r = resting ?: 65
        return when {
            bpm >= maxOf(r + 40, (0.6 * maxHr(age)).toInt()) -> State.Workout
            bpm >= r + Integrity.HR_OVER_REST -> State.Active
            else -> State.Rest
        }
    }

    /** Lowest average over any [windowMs] stretch with at least 3 readings in it. */
    fun lowestWindow(points: List<HrPoint>, windowMs: Long = 10 * 60_000L): Int? {
        var best: Double? = null
        var j = 0
        var sum = 0
        for (i in points.indices) {
            sum += points[i].bpm
            while (points[i].t - points[j].t > windowMs) { sum -= points[j].bpm; j++ }
            val n = i - j + 1
            if (n >= 3 && points[i].t - points[j].t >= windowMs / 2) {
                val avg = sum.toDouble() / n
                if (best == null || avg < best) best = avg
            }
        }
        return best?.toInt()
    }

    fun medianGapS(points: List<HrPoint>): Int? {
        if (points.size < 2) return null
        val gaps = points.zipWithNext { a, b -> b.t - a.t }.sorted()
        return (gaps[gaps.size / 2] / 1000).toInt()
    }

    fun dense(points: List<HrPoint>) = points.size >= 10 && (medianGapS(points) ?: Int.MAX_VALUE) <= DENSE_GAP_S

    data class SetHr(val move: String, val set: Int, val peak: Int, val drop: Int?)

    data class Report(
        val avg: Int,
        val max: Int,
        val points: Int,
        val dense: Boolean,
        val zones: Map<State, Int>, // seconds in each state
        val sets: List<SetHr>, // only when dense
        val rose: Boolean?, // did the heart rate climb above resting at all; null without a resting rate
    )

    fun analyze(points: List<HrPoint>, logs: List<ExerciseLog>, resting: Int?, age: Int): Report? {
        if (points.size < MIN_POINTS) return null
        val zones = mutableMapOf<State, Int>()
        points.forEachIndexed { i, p ->
            // each reading stands for the time until the next, capped so a gap in the data isn't counted as anything
            val secs = if (i < points.lastIndex) ((points[i + 1].t - p.t) / 1000).toInt().coerceIn(0, 120) else 0
            val st = state(p.bpm, resting, age)
            zones[st] = (zones[st] ?: 0) + secs
        }
        val dense = dense(points)
        val sets = if (!dense) emptyList() else logs.flatMap { e ->
            e.sets.mapIndexedNotNull { i, s ->
                val end = s.endMs ?: return@mapIndexedNotNull null
                val start = end - (s.secs ?: 30) * 1000L
                val peak = points.filter { it.t in start - 5_000..end + 15_000 }.maxOfOrNull { it.bpm } ?: return@mapIndexedNotNull null
                // a minute into the rest: how far it came down tells how well the body recovers
                val after = points.filter { abs(it.t - (end + 60_000)) <= 15_000 }.minByOrNull { abs(it.t - (end + 60_000)) }
                SetHr(e.id, i + 1, peak, after?.let { peak - it.bpm })
            }
        }
        val avg = points.map { it.bpm }.average().toInt()
        return Report(avg, points.maxOf { it.bpm }, points.size, dense, zones, sets, resting?.let { avg >= it + Integrity.HR_OVER_REST })
    }

    /**
     * Fills in heart rate for recent sessions that have none yet, once Health Connect has the watch's readings. A
     * session whose heart rate never left resting is marked doubtful and its sets stop counting for volume and records.
     */
    suspend fun backfill(context: Context) {
        val s = Store.state.value
        if (!s.settings.healthConnect || !Health.available(context)) return
        val now = System.currentTimeMillis()
        val todo = s.logs.filter { !HrStore.hasHr(it) && it.startMs != null && it.endMs != null && now - it.endMs < 3 * 86_400_000L }
        if (todo.isEmpty()) return
        val resting = Health.restingEstimate(context)
        val p = s.profile
        todo.forEach { log ->
            val pts = Health.heartRate(context, log.startMs!!, log.endMs!!)
            if (pts.size < MIN_POINTS) {
                // no readings yet: the active calories the watch logged are still better than the MET table
                if (log.kcalSource == "MET") Health.activeKcal(context, log.startMs, log.endMs)?.let { kcal ->
                    Store.update { st -> st.copy(logs = st.logs.map { if (it.startMs == log.startMs) it.copy(kcal = kcal, kcalSource = "Health Connect") else it }) }
                }
                return@forEach
            }
            Store.update { st -> st.copy(logs = st.logs.map { if (it.startMs == log.startMs) withHr(it, pts, "Health Connect", resting, p) else it }) }
        }
    }

    /** The log with its readings, average and peak, heart-rate calories, and the post-session honesty check. */
    fun withHr(log: WorkoutLog, pts: List<HrPoint>, source: String, resting: Int?, p: Profile?): WorkoutLog {
        val avg = pts.map { it.bpm }.average().toInt()
        val seconds = log.seconds ?: (log.minutes * 60)
        val kcal = if (p != null) Burn.hrKcal(avg, p, seconds).takeIf { it > 0 } else null
        // enough readings and the heart stayed at rest the whole time: the reps can't have been real
        val flat = pts.size >= 10 && resting != null && Integrity.hrFlat(avg, resting) && !log.doubtful
        return HrStore.put(log, pts).copy(
            hrSource = source, restingHr = resting, avgHr = avg, maxHr = pts.maxOf { it.bpm },
            kcal = kcal ?: log.kcal, kcalSource = if (kcal != null) "HR jam" else log.kcalSource,
            doubtful = log.doubtful || flat,
            exercises = if (flat) log.exercises.map { e -> e.copy(sets = e.sets.map { it.copy(flagged = true) }) } else log.exercises,
        )
    }
}
