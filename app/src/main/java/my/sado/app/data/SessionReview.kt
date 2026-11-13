package my.sado.app.data

import java.time.LocalDate

/**
 * The Done screen's review in plain sentences, built from the session itself: how it went, what improved or slipped,
 * which muscles worked, the heart rate when there is one, and a word about tomorrow. No network, same input same words.
 */
object SessionReview {
    private val legs = setOf("quads", "glutes", "hamstrings", "calves")
    private val upper = setOf("chest", "triceps", "delts", "lats", "biceps", "traps")

    fun names(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " dan " + items.last()
    }

    private fun muscle(id: String) = Catalog.muscleNames[id]?.lowercase() ?: id
    private fun move(id: String) = Catalog.moves.firstOrNull { it.id == id }
    private fun moveName(id: String) = move(id)?.name ?: id

    /** Sets per muscle from the sets that counted: main muscles get every set, helpers half. */
    fun worked(log: WorkoutLog): Map<String, Float> {
        val out = mutableMapOf<String, Float>()
        log.exercises.filter { !it.skipped }.forEach { e ->
            val m = move(e.id) ?: return@forEach
            val n = Integrity.real(e).size.toFloat().takeIf { it > 0f } ?: return@forEach
            m.muscles.forEach { out[it] = (out[it] ?: 0f) + n }
            (m.secondary - m.muscles.toSet()).forEach { out[it] = (out[it] ?: 0f) + n / 2f }
        }
        return out
    }

    private fun sessionMuscles(s: PlanSession) = s.exercises.flatMap { move(it.id)?.muscles.orEmpty() }.toSet()

    /**
     * [earlier] are the sessions before [log], oldest first. [hr] is the heart-rate analysis when the watch sent
     * readings. [plan] gives tomorrow's session.
     */
    fun sentences(log: WorkoutLog, earlier: List<WorkoutLog>, plan: Plan?, hr: HrAnalysis.Report? = null): List<String> {
        val done = log.exercises.filter { !it.skipped && Integrity.real(it).isNotEmpty() }
        if (done.isEmpty()) return listOf("Hari ni takde set yang dikira. Esok cuba satu move pun jadi, yang penting muncul.")
        val out = mutableListOf<String>()
        val sets = done.sumOf { Integrity.real(it).size }
        val mins = log.seconds?.let { (it + 30) / 60 } ?: log.minutes

        // how it went
        out += when {
            log.extra -> "Sesi tambahan pada hari rest: $mins minit, ${done.size} move, $sets set. Bonus yang bagus, asalkan badan rasa segar."
            log.quit -> "Stop awal lepas $mins minit, tapi $sets set yang siap tetap dikira."
            else -> "Sesi $mins minit, ${done.size} move, $sets set siap."
        }

        // what improved, what slipped
        val rows = SessionSummary.rows(log, earlier).filter { !it.skipped }
        val prs = rows.filter { it.pr }.map { it.name }
        val ups = rows.filter { !it.pr && (it.delta ?: 0) > 0 }.sortedByDescending { it.delta }
        val down = rows.filter { (it.delta ?: 0) <= -3 }.minByOrNull { it.delta ?: 0 }
        val unit = { id: String -> if (move(id)?.timed == true) "saat" else "rep" }
        when {
            prs.isNotEmpty() -> out += "Rekod baru untuk ${names(prs.take(3))}." +
                (ups.firstOrNull()?.let { " ${it.name} pun naik ${it.delta} ${unit(it.id)}." } ?: "")
            ups.isNotEmpty() -> out += "${ups[0].name} naik ${ups[0].delta} ${unit(ups[0].id)} dari sesi lepas" +
                (ups.getOrNull(1)?.let { ", ${it.name} naik ${it.delta}." } ?: ".")
            rows.any { it.delta == 0 } -> out += "Rep sama macam sesi lepas. Sesi depan cuba tambah satu rep je pada set pertama."
        }
        down?.let { out += "${it.name} turun ${-(it.delta ?: 0)} ${unit(it.id)}. Sekali-sekala normal, mungkin penat atau kurang tidur." }
        val firsts = rows.filter { it.first }.map { it.name }
        if (firsts.isNotEmpty()) out += "Kali pertama buat ${names(firsts.take(2))}, sesi depan ada nombor untuk dilawan."
        if (log.climbed.isNotEmpty()) out += "Ko dah layak naik ke ${names(log.climbed.values.map { moveName(it) })}."
        log.kgNext.entries.firstOrNull()?.let { (id, kg) -> out += "Sesi depan beban ${moveName(id)} naik ke ${if (kg % 1f == 0f) kg.toInt() else kg} kg." }

        // muscles
        val w = worked(log)
        val top = w.entries.filter { it.key in log.exercises.flatMap { e -> move(e.id)?.muscles.orEmpty() } }
            .sortedByDescending { it.value }.take(2)
        if (top.isNotEmpty()) out += names(top.map { muscle(it.key) }).replaceFirstChar { it.uppercase() } +
            " paling banyak kerja (${top.first().value.let { if (it % 1f == 0f) it.toInt() else it }} set)."
        val legsHit = w.keys.any { it in legs }
        val upperHit = w.keys.any { it in upper }
        if (upperHit && !legsHit) out += "Kaki tak disentuh hari ni."
        else if (legsHit && !upperHit) out += "Badan atas tak disentuh hari ni."

        // skipped and sets that didn't count
        val skipped = log.exercises.filter { it.skipped }.mapNotNull { move(it.id) }
        if (skipped.isNotEmpty()) out += "Ko skip ${names(skipped.take(2).map { it.name })}, jadi " +
            names(skipped.flatMap { it.muscles }.distinct().take(2).map { muscle(it) }) + " kurang kerja."
        val flagged = log.exercises.sumOf { e -> e.sets.count { it.flagged } }
        if (flagged > 0) out += "$flagged set terlalu laju dan tak dikira. Buat perlahan, setiap rep penuh."

        // heart rate
        if (hr != null) {
            val total = hr.zones.values.sum().coerceAtLeast(1)
            val work = (hr.zones[HrAnalysis.State.Workout] ?: 0) * 100 / total
            out += "Purata HR ${hr.avg}, paling tinggi ${hr.max}" +
                (if (work >= 50) ", kebanyakan masa dalam zon senaman." else if (hr.rose == false) ", hampir tak naik dari rehat." else ".")
            val drops = hr.sets.mapNotNull { it.drop }
            if (drops.size >= 2) {
                val avg = drops.average().toInt()
                out += when {
                    avg >= 20 -> "Seminit lepas set, HR turun purata $avg. Badan pulih cepat."
                    avg < 12 -> "HR lambat turun lepas set (purata $avg seminit). Rehat lama sikit antara set."
                    else -> "Seminit lepas set, HR turun purata $avg. Pemulihan sederhana."
                }
            }
        }

        // tomorrow
        if (plan != null && plan.week.size == 7) {
            val day = LocalDate.ofEpochDay(log.epochDay + 1)
            val tag = plan.week[day.dayOfWeek.value - 1]
            val worked = top.map { it.key }.toSet()
            out += if (tag == "Rest") "Esok rest day. Tidur cukup dan makan protein, otot membesar masa rehat."
            else {
                val next = plan.allSessions.firstOrNull { it.tag == tag }
                val same = next?.let { sessionMuscles(it) intersect worked }.orEmpty()
                when {
                    next == null -> "Esok ada sesi lagi, rehat cukup malam ni."
                    same.isNotEmpty() -> "Esok ${next.label}, kena ${names(same.map { muscle(it) })} lagi. Kalau masih sakit, buat ringan sikit."
                    else -> "Esok ${next.label}, otot lain pula. Yang kerja hari ni dapat rehat."
                }
            }
        }
        return out
    }
}

/** Picking a session from the plan on a rest day: each one says if its muscles worked yesterday or it is due tomorrow. */
object ExtraSession {
    data class Option(val tag: String, val label: String, val moves: Int, val notes: List<String>, val best: Boolean)

    fun options(plan: Plan, logs: List<WorkoutLog>, today: LocalDate): List<Option> {
        val yesterday = logs.filter { it.epochDay == today.toEpochDay() - 1 }
            .flatMap { l -> l.exercises.filter { !it.skipped && Integrity.real(it).isNotEmpty() } }
            .flatMap { e -> Catalog.moves.firstOrNull { it.id == e.id }?.muscles.orEmpty() }.toSet()
        val tomorrow = plan.week.getOrNull(today.plusDays(1).dayOfWeek.value - 1)
        val raw = plan.allSessions.map { s ->
            val muscles = s.exercises.flatMap { e -> Catalog.moves.firstOrNull { it.id == e.id }?.muscles.orEmpty() }.toSet()
            val tired = (muscles intersect yesterday).map { Catalog.muscleNames[it]?.lowercase() ?: it }
            val notes = listOfNotNull(
                tired.takeIf { it.isNotEmpty() }?.let { "Semalam dah kerja: ${SessionReview.names(it.take(3))}" },
                "Esok sesi ni dalam plan".takeIf { s.tag == tomorrow },
            )
            Triple(s, notes, tired.size * 2 + (if (s.tag == tomorrow) 3 else 0))
        }
        val bestScore = raw.minOfOrNull { it.third }
        val bestTag = raw.firstOrNull { it.third == bestScore }?.first?.tag.takeIf { raw.size > 1 }
        return raw.map { (s, notes, _) -> Option(s.tag, s.label, s.exercises.size, notes, s.tag == bestTag) }
    }
}
