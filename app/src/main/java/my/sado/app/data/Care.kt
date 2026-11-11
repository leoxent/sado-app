package my.sado.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.Month

/** Answers to the health questions, in our own words and guided by PAR-Q+. Stays on the phone. */
@Serializable
data class HealthCheck(
    val epochDay: Long,
    val heart: Boolean = false,
    val chest: Boolean = false,
    val dizzy: Boolean = false,
    val chronic: Boolean = false,
    val joint: Boolean = false,
    val doctorOnly: Boolean = false,
    val pregnant: Boolean = false,
    val eating: Boolean = false,
)

/** A stretch of rest mode. [until] is the last day it covers, inclusive; null runs until the user ends it. */
@Serializable
data class RestSpell(val reason: String, val from: Long, val until: Long? = null)

/** Ramadan's first and last day from JAKIM's calendar for one Gregorian year. */
@Serializable
data class RamadanDates(val year: Int, val start: Long, val end: Long)

/**
 * The safety rules. Sources are listed in design/kajian-keselamatan.md.
 * Age: AAP 2016 calls dieting unsuitable for growing bodies, and every target here uses an adult formula.
 * Calories: Malaysia's obesity CPG 2023 (MOH with the Malaysian Dietitians' Association) gives 1,200 kcal for women,
 * 1,500 for men, or 500 to 750 below usual intake, and keeps under 800 kcal for medical teams.
 */
object Care {
    const val MIN_AGE = 18
    const val FLOOR_WOMEN = 1200
    const val FLOOR_MEN = 1500
    const val MAX_DEFICIT = 750
    const val NURSING_EXTRA = 350 // CDC: 330 to 400 kcal a day on top of the pre-pregnancy intake
    const val HEALTH_VALID_DAYS = 365 // PAR-Q+ clearance lasts 12 months
    const val SICK_DOCTOR_DAYS = 14
    const val BIRTH_WEEKS = 12 // no running or high-intensity work before week 12 (Goom, Donnelly & Brockwell 2019)
    const val RAMADAN_DAYS = 30

    const val SICK = "sakit"
    const val EXAM = "exam"
    const val BIRTH = "bersalin"
    const val RAMADAN = "ramadan"

    fun underAge(p: Profile?) = p != null && p.age < MIN_AGE

    fun kcalFloor(sex: Int) = if (sex == 1) FLOOR_WOMEN else FLOOR_MEN

    // ── rest mode ─────────────────────────────────────────────────────────────────────────────
    private fun RestSpell.covers(day: Long) = day >= from && (until == null || day <= until)

    fun active(s: AppState, day: Long = LocalDate.now().toEpochDay()): RestSpell? = s.rest.lastOrNull { it.covers(day) }

    fun restDaysIn(s: AppState, from: Long, to: Long): Int =
        if (s.rest.isEmpty()) 0 else (from..to).count { d -> s.rest.any { it.covers(d) } }

    fun start(s: AppState, reason: String, today: Long, days: Int? = null): AppState {
        val ended = end(s, today)
        val until = when {
            reason == RAMADAN -> s.ramadan?.takeIf { today in it.start..it.end }?.end ?: (today + RAMADAN_DAYS - 1)
            reason == BIRTH -> today + BIRTH_WEEKS * 7 - 1
            days != null -> today + days - 1
            else -> null
        }
        // a birth ends the pregnancy answer, so calories go by the nursing choice from here
        val health = if (reason == BIRTH) ended.health?.copy(pregnant = false) else ended.health
        return ended.copy(rest = ended.rest + RestSpell(reason, today, until), health = health)
    }

    /** Ends today's spell. Today itself counts as back; a spell begun today leaves nothing behind. */
    fun end(s: AppState, today: Long): AppState {
        val cur = active(s, today) ?: return s
        val rest = s.rest - cur
        return s.copy(rest = if (cur.from >= today) rest else rest + cur.copy(until = today - 1))
    }

    fun label(reason: String) = when (reason) {
        SICK -> "Sakit"; EXAM -> "Exam"; BIRTH -> "Lepas bersalin"; RAMADAN -> "Ramadan"; else -> reason
    }

    /** What to do during this spell, from the sources in the study note. */
    fun tips(spell: RestSpell, today: Long): List<String> {
        val day = (today - spell.from).toInt()
        return when (spell.reason) {
            SICK -> listOfNotNull(
                "Tunggu sampai rasa sihat sebelum bersenam.",
                "Sakit dada, sesak nafas atau demam tak surut: jumpa doktor.",
                "Bila dah sihat, mula dengan satu atau dua sesi ringan dulu.",
                "Dah lebih 2 minggu. Elok jumpa doktor kalau belum.".takeIf { day >= SICK_DOCTOR_DAYS },
            )
            EXAM -> listOf(
                "Fokus exam dulu. Kekuatan yang hilang masa rehat cepat balik bila sambung semula.",
                "Kalau ada masa, sesi pendek pun bagus untuk rehatkan kepala.",
                "Tidur dan makan cukup. Kalori tak diubah masa mod ni.",
            )
            BIRTH -> birthTips(day / 7 + 1)
            RAMADAN -> listOf(
                "Sesi pendek dan sederhana, sejam dua sebelum berbuka.",
                "Elak sesi berat masa puasa. Kalau nak berat, buat lepas berbuka.",
                "Cukupkan air dan tidur antara berbuka dan sahur.",
                "Ada kencing manis atau darah tinggi? Ikut nasihat doktor.",
            )
            else -> emptyList()
        }
    }

    private fun birthTips(week: Int): List<String> {
        val now = when {
            week <= 2 -> "Minggu $week: jalan kaki, latihan pelvic floor dan core asas."
            week <= 4 -> "Minggu $week: tambah jalan kaki. Squat dan bridging kalau selesa."
            week <= 6 -> "Minggu $week: mula senaman low impact, contohnya basikal statik."
            week <= 8 -> "Minggu $week: jalan laju, dan berat ringan paling banyak setara berat baby."
            else -> "Minggu $week: tambah perlahan-lahan. Berenang kalau luka dah elok."
        }
        return listOf(
            now,
            "Jangan lari, lompat atau HIIT sebelum minggu 12, dan sebelum ujian pelvic floor selesa.",
            "Berat rasa ke bawah, kebocoran atau sakit: berhenti dan tanya doktor atau fisio.",
            "Bersalin secara caesarean atau ada komplikasi: ikut masa yang doktor bagi.",
        )
    }

    // ── calories ──────────────────────────────────────────────────────────────────────────────
    /** No deficit for a body that is healing, growing a baby, nursing, or has a history with food. */
    fun noCut(s: AppState, day: Long = LocalDate.now().toEpochDay()): Boolean {
        val reason = active(s, day)?.reason
        return s.nursing || s.health?.pregnant == true || s.health?.eating == true || reason == SICK || reason == BIRTH
    }

    fun extraKcal(s: AppState) = if (s.nursing) NURSING_EXTRA else 0

    // ── health questions ──────────────────────────────────────────────────────────────────────
    fun healthDue(s: AppState, today: Long = LocalDate.now().toEpochDay()): Boolean =
        s.profile != null && (s.health == null || today - s.health.epochDay >= HEALTH_VALID_DAYS)

    enum class Level { Stop, Ask, Info, Clear }

    /** The answers in plain words, most serious first. */
    fun advice(h: HealthCheck, age: Int, nursing: Boolean): List<Pair<Level, String>> {
        val out = mutableListOf<Pair<Level, String>>()
        if (h.chest || h.dizzy) out += Level.Stop to "Jumpa doktor sebelum mula. Sakit dada, pening sampai hilang imbangan atau pengsan perlu diperiksa dulu."
        if (h.heart || h.chronic || h.doctorOnly) out += Level.Ask to "Tanya doktor dulu, dan tunjuk plan kau. Mungkin ada move atau intensiti yang perlu diubah."
        if (h.pregnant) out += Level.Ask to "Tengah mengandung: app takkan bagi defisit kalori. Tanya doktor sebelum mula atau ubah senaman."
        if (nursing) out += Level.Info to "Menyusu: app takkan bagi defisit kalori, dan tambah $NURSING_EXTRA kcal sehari."
        if (h.eating) out += Level.Ask to "App takkan bagi defisit kalori. Elok berbincang dengan doktor atau pakar sebelum kira kalori."
        if (h.joint) out += Level.Info to "Tanda bahagian yang sakit dalam soalan plan, supaya move yang berisiko dibuang."
        if (age >= 45) out += Level.Info to "Umur 45 ke atas dan belum biasa senaman berat: tanya pakar senaman sebelum pilih intensiti paling tinggi."
        if (out.none { it.first == Level.Stop || it.first == Level.Ask }) out.add(0, Level.Clear to "Takde tanda yang perlu diperiksa dulu. Mula perlahan, dan jawab semula kalau kesihatan berubah.")
        return out
    }

    fun flagged(s: AppState): Boolean {
        val h = s.health ?: return false
        return h.chest || h.dizzy || h.heart || h.chronic || h.doctorOnly || h.pregnant || h.eating
    }

    // ── Ramadan dates ─────────────────────────────────────────────────────────────────────────
    // JAKIM's e-Solat feed: free and keyless, but it publishes no terms or uptime promise, so the app caches the
    // answer and manual rest mode keeps working without it. Only a zone code goes out. Ramadan runs on the same dates
    // across the Malaysian zones in this calendar, and the real start still follows the moon sighting.
    private const val ESOLAT = "https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=year&zone=SGR01"

    private val months = mapOf(
        "jan" to Month.JANUARY, "feb" to Month.FEBRUARY, "mac" to Month.MARCH, "mar" to Month.MARCH, "apr" to Month.APRIL,
        "mei" to Month.MAY, "may" to Month.MAY, "jun" to Month.JUNE, "jul" to Month.JULY, "ogos" to Month.AUGUST, "ogo" to Month.AUGUST,
        "aug" to Month.AUGUST, "sep" to Month.SEPTEMBER, "okt" to Month.OCTOBER, "oct" to Month.OCTOBER, "nov" to Month.NOVEMBER,
        "dis" to Month.DECEMBER, "dec" to Month.DECEMBER,
    )

    /** "19-Feb-2026" or "20-Mac-2026" to an epoch day; null when it can't be read. */
    fun parseDay(text: String): Long? {
        val p = text.split("-")
        if (p.size != 3) return null
        val day = p[0].toIntOrNull() ?: return null
        val year = p[2].toIntOrNull() ?: return null
        val month = months[p[1].lowercase()] ?: return null
        return runCatching { LocalDate.of(year, month, day).toEpochDay() }.getOrNull()
    }

    /** Picks Ramadan (Hijri month 09) out of a year's reply; null when the reply doesn't hold it. */
    fun parseRamadan(json: String, year: Int): RamadanDates? = runCatching {
        val days = Json.parseToJsonElement(json).jsonObject.getValue("prayerTime").jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            val month = o.getValue("hijri").jsonPrimitive.content.split("-").getOrNull(1)
            if (month == "09") parseDay(o.getValue("date").jsonPrimitive.content) else null
        }
        if (days.isEmpty()) null else RamadanDates(year, days.min(), days.max())
    }.getOrNull()

    /** Network call: run off the main thread. Null on any failure, and the app simply tries again another day. */
    fun fetchRamadan(year: Int): RamadanDates? = runCatching {
        val c = URL(ESOLAT).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        try {
            if (c.responseCode != 200) null else parseRamadan(c.inputStream.bufferedReader().readText(), year)
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    /** Offer rest mode once when Ramadan begins. */
    fun ramadanOffer(s: AppState, today: Long = LocalDate.now().toEpochDay()): Boolean {
        val r = s.ramadan ?: return false
        return today in r.start..r.end && s.ramadanAsked != r.year && active(s, today) == null
    }
}
