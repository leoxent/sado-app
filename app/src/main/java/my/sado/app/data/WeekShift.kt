package my.sado.app.data

import java.time.LocalDate

/**
 * A missed workout isn't lost: when a planned day ends with no workout logged, its session slides to
 * the next day, and the rest of the week slides with it until a rest day takes up the slack. At most
 * [MAX_PER_WEEK] slides a week; a session still pending on Sunday night is dropped and next week starts
 * from the plan again. Days in rest mode (sick, Ramadan) or before the plan began never slide.
 */
object WeekShift {
    const val MAX_PER_WEEK = 2
    const val REST = "Rest"

    enum class Status { DONE, PLANNED, MOVED, MISSED, REST }

    /** One day of the week as it plays out: [tag] is what's on (after slides), [shiftedIn] when that differs from the plan. */
    data class Day(val epochDay: Long, val tag: String, val status: Status, val shiftedIn: Boolean)

    private fun mondayOf(day: Long) = LocalDate.ofEpochDay(day).let { it.minusDays(it.dayOfWeek.value - 1L).toEpochDay() }

    /** The Mon–Sun week holding [day], with every slide up to [today] applied. */
    fun week(s: AppState, day: Long = LocalDate.now().toEpochDay(), today: Long = LocalDate.now().toEpochDay()): List<Day> {
        val plan = s.plan ?: return emptyList()
        val done = s.logs.map { it.epochDay }.toSet()
        return weekOf(plan.week, mondayOf(day), today, done) { d -> Care.active(s, d) != null || (s.planStart != null && d < s.planStart) }
    }

    fun weekOf(planWeek: List<String>, monday: Long, today: Long, done: Set<Long>, noSlide: (Long) -> Boolean = { false }): List<Day> {
        val queue = ArrayDeque<String>()
        var moves = 0
        return (0..6).map { i ->
            val day = monday + i
            val planned = planWeek.getOrElse(i) { REST }
            var tag = planned
            if (queue.isNotEmpty()) {
                tag = queue.removeFirst()
                if (planned != REST) queue.addLast(planned)
            }
            val status = when {
                day in done -> Status.DONE
                tag == REST -> Status.REST
                day >= today -> Status.PLANNED
                // an ended day with no workout: the session goes to the front of the queue
                !noSlide(day) && moves < MAX_PER_WEEK -> { queue.addFirst(tag); moves++; Status.MOVED }
                else -> Status.MISSED
            }
            Day(day, tag, status, tag != planned)
        }
    }

    /** What's on [date]: the slid tag in this week or earlier ones, the plan's own for weeks still ahead. */
    fun tagOn(s: AppState, date: LocalDate = LocalDate.now(), today: LocalDate = LocalDate.now()): String? {
        val plan = s.plan ?: return null
        val d = date.toEpochDay()
        if (mondayOf(d) > mondayOf(today.toEpochDay())) return plan.week.getOrNull(date.dayOfWeek.value - 1)
        return week(s, d, today.toEpochDay()).firstOrNull { it.epochDay == d }?.tag
    }

    /** The session for [date]; a rest day looks ahead to the next workout, slides included. */
    fun sessionOn(s: AppState, date: LocalDate = LocalDate.now(), today: LocalDate = LocalDate.now()): PlanSession? {
        val plan = s.plan ?: return null
        val tag = (0L..6L).map { tagOn(s, date.plusDays(it), today) }.firstOrNull { it != null && it != REST }
        return plan.allSessions.firstOrNull { it.tag == tag } ?: plan.sessionOn(date)
    }

    /** Slides used this week. */
    fun movedThisWeek(s: AppState, today: LocalDate = LocalDate.now()) = week(s, today.toEpochDay(), today.toEpochDay()).count { it.status == Status.MOVED }
}
