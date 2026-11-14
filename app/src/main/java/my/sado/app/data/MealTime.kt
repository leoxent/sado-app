package my.sado.app.data

import java.time.LocalDate
import java.time.LocalTime

/**
 * When a meal was eaten. [daysAgo] 0 = today; [minute] null = right now (resolved when the meal is saved).
 * A meal can go back [MAX_DAYS_BACK] days at most, and never into the future.
 */
data class MealWhen(val daysAgo: Int = 0, val minute: Int? = null) {
    val isNow get() = daysAgo == 0 && minute == null
}

object MealTime {
    const val MAX_DAYS_BACK = 2

    /** The usual meal times, named the way people say them. */
    val SLOTS = listOf("Pagi" to 8 * 60, "Tengah hari" to 13 * 60, "Petang" to 17 * 60, "Malam" to 20 * 60)

    val DAYS = listOf("Hari ni", "Semalam", "Kelmarin")

    /** The epoch day and minute to save, clamped: no further back than allowed, nothing after [nowMinute] today. */
    fun resolve(w: MealWhen, today: Long = LocalDate.now().toEpochDay(), nowMinute: Int = nowMinute()): Pair<Long, Int> {
        val back = w.daysAgo.coerceIn(0, MAX_DAYS_BACK)
        val m = (w.minute ?: nowMinute).coerceIn(0, 1439)
        return (today - back) to if (back == 0) minOf(m, nowMinute) else m
    }

    /** A slot can be picked for that day only once its time has come. */
    fun allowed(daysAgo: Int, minute: Int, nowMinute: Int = nowMinute()) = daysAgo in 1..MAX_DAYS_BACK || (daysAgo == 0 && minute <= nowMinute)

    /** The same moment as a [MealWhen], for editing a meal already in the log; null when it's older than allowed. */
    fun of(epochDay: Long, minute: Int, today: Long = LocalDate.now().toEpochDay()): MealWhen? =
        (today - epochDay).toInt().takeIf { it in 0..MAX_DAYS_BACK }?.let { MealWhen(it, minute) }

    fun clock(minute: Int) = "%d:%02d".format(minute / 60, minute % 60)

    /** "Sekarang", "Hari ni 13:00", "Semalam 20:00", "Kelmarin 8:00". */
    fun label(w: MealWhen, nowMinute: Int = nowMinute()) =
        if (w.isNow) "Sekarang · ${clock(nowMinute)}" else "${DAYS[w.daysAgo.coerceIn(0, MAX_DAYS_BACK)]} ${clock(w.minute ?: nowMinute)}"

    /** The words after "masuk log" in the toast: nothing for today, the day and time otherwise. */
    fun toastSuffix(epochDay: Long, minute: Int, today: Long = LocalDate.now().toEpochDay()) = when (today - epochDay) {
        0L -> ""
        1L -> " semalam ${clock(minute)}"
        else -> " kelmarin ${clock(minute)}"
    }

    fun nowMinute() = LocalTime.now().let { it.hour * 60 + it.minute }
}
