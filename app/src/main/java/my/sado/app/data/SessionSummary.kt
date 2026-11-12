package my.sado.app.data

/** The Done screen's per-move recap: what was done, records broken, change since last time, and what goes up next. */
object SessionSummary {
    data class Row(
        val id: String,
        val name: String,
        val sets: String, // "12 · 10 · 8", "3 × 10", "40s · 40s", "8 · 8 @ 5 kg"
        val pr: Boolean, // best set beats every earlier session of this move
        val first: Boolean, // never done before
        val delta: Int?, // total reps (or seconds) against the last session with this move; null when there is none
        val flagged: Int, // sets too quick to count
        val skipped: Boolean,
    )

    private fun kg(k: Float) = if (k % 1f == 0f) k.toInt().toString() else "%.1f".format(k)

    fun setsText(e: ExerciseLog, timed: Boolean): String {
        if (e.sets.isEmpty()) return "–"
        val unit = if (timed) "s" else ""
        val reps = e.sets.map { it.reps }
        val body = if (reps.size > 1 && reps.distinct().size == 1) "${reps.size} × ${reps.first()}$unit" else reps.joinToString(" · ") { "$it$unit" }
        val load = e.sets.mapNotNull { it.kg }.maxOrNull()
        return if (load != null && load > 0f) "$body @ ${kg(load)} kg" else body
    }

    private fun total(e: ExerciseLog) = Integrity.real(e).sumOf { it.reps }
    private fun best(e: ExerciseLog) = Integrity.real(e).maxOfOrNull { it.reps }

    /** [earlier] are the sessions before [log], oldest first. */
    fun rows(log: WorkoutLog, earlier: List<WorkoutLog>): List<Row> = log.exercises.map { e ->
        val move = Catalog.moves.firstOrNull { it.id == e.id }
        val past = earlier.flatMap { it.exercises }.filter { it.id == e.id && !it.skipped && Integrity.real(it).isNotEmpty() }
        val prevBest = past.mapNotNull { best(it) }.maxOrNull()
        val mine = best(e)
        Row(
            id = e.id,
            name = move?.name ?: e.id,
            sets = setsText(e, move?.timed == true),
            pr = !e.skipped && mine != null && prevBest != null && mine > prevBest,
            first = !e.skipped && mine != null && past.isEmpty(),
            delta = if (e.skipped || past.isEmpty() || mine == null) null else total(e) - total(past.last()),
            flagged = e.sets.count { it.flagged },
            skipped = e.skipped,
        )
    }

    /** What this session moved up: ladder rungs (from move to next move) and loads (move to its next kg). */
    data class Ups(val climbed: Map<String, String>, val kgNext: Map<String, Float>)

    fun ups(
        beforeLadders: Map<String, LadderProgress>, afterLadders: Map<String, LadderProgress>,
        beforeLoads: Map<String, Float>, afterLoads: Map<String, Float>, logs: List<ExerciseLog>,
    ): Ups {
        val climbed = afterLadders.mapNotNull { (key, after) ->
            val from = beforeLadders[key]?.rung ?: 0
            val rungs = Catalog.ladders[key] ?: return@mapNotNull null
            if (after.rung <= from) null else rungs.getOrNull(from)?.let { a -> rungs.getOrNull(after.rung)?.let { b -> a to b } }
        }.toMap()
        // a weighted move whose next load is above anything carried today went up a step
        val kgNext = afterLoads.filter { (id, next) ->
            val carried = logs.filter { it.id == id }.flatMap { it.sets }.mapNotNull { it.kg }.maxOrNull() ?: beforeLoads[id] ?: return@filter false
            next > carried
        }
        return Ups(climbed, kgNext)
    }
}
