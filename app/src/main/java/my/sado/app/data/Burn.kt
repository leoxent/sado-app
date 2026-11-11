package my.sado.app.data

/**
 * Calories a session burned, for display only: the weekly check-in already adjusts the food target from the weight
 * trend, so this is never added to it. A watch's heart rate beats the MET table when there is one.
 */
object Burn {
    // Compendium of Physical Activities: resistance training, multiple exercises 8–15 reps ≈ 3.5 MET;
    // vigorous calisthenics and intervals ≈ 8 MET. A session is weighted by its share of cardio sets.
    const val STRENGTH_MET = 3.5
    const val CARDIO_MET = 8.0

    fun met(logs: List<ExerciseLog>): Double {
        val sets = logs.filter { !it.skipped }.flatMap { e -> e.sets.map { e.id } }
        if (sets.isEmpty()) return STRENGTH_MET
        val cardio = sets.count { id -> Catalog.moves.firstOrNull { it.id == id }?.group == "cardio" }.toDouble() / sets.size
        return STRENGTH_MET + (CARDIO_MET - STRENGTH_MET) * cardio
    }

    fun metKcal(met: Double, kg: Float, seconds: Int) = (met * kg * seconds / 3600.0).toInt()

    /** Keytel et al. 2005: energy from average heart rate, weight, age and sex. */
    fun hrKcal(avgHr: Int, p: Profile, seconds: Int): Int {
        val perMin = if (p.sex == 1) (-20.4022 + 0.4472 * avgHr - 0.1263 * p.weightKg + 0.074 * p.age) / 4.184
        else (-55.0969 + 0.6309 * avgHr + 0.1988 * p.weightKg + 0.2017 * p.age) / 4.184
        return (perMin * seconds / 60.0).toInt().coerceAtLeast(0)
    }

    /** The best estimate on hand and where it came from; Health Connect can replace it later on the Done screen. */
    fun estimate(logs: List<ExerciseLog>, p: Profile?, seconds: Int, avgHr: Int?): Pair<Int, String> {
        val kg = p?.weightKg ?: 65f
        if (avgHr != null && p != null && avgHr > 60) hrKcal(avgHr, p, seconds).takeIf { it > 0 }?.let { return it to "HR jam" }
        return metKcal(met(logs), kg, seconds) to "MET"
    }
}
