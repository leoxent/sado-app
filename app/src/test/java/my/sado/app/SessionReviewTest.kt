package my.sado.app

import my.sado.app.data.ExerciseLog
import my.sado.app.data.ExtraSession
import my.sado.app.data.HrAnalysis
import my.sado.app.data.Plan
import my.sado.app.data.PlanSession
import my.sado.app.data.PlannedExercise
import my.sado.app.data.SessionReview
import my.sado.app.data.SetLog
import my.sado.app.data.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SessionReviewTest {
    private fun sets(vararg r: Int) = r.map { SetLog(it) }
    private fun pe(id: String) = PlannedExercise(id, 3, "8-12", 12)
    // Monday 2026-11-09: Upper Mon, Lower Tue, Rest Wed, Upper Thu, Lower Fri, Rest Sat/Sun
    private val monday = LocalDate.of(2026, 11, 9)
    private val plan = Plan(
        "t", "w", emptyList(), listOf("Upper", "Lower", "Rest", "Upper", "Lower", "Rest", "Rest"), "Upper", emptyList(),
        listOf(PlanSession("Upper", "Upper body", listOf(pe("push"), pe("pullup"))), PlanSession("Lower", "Lower body", listOf(pe("squat"), pe("calf")))),
    )
    private fun log(day: LocalDate, vararg e: ExerciseLog, extra: Boolean = false, quit: Boolean = false) =
        WorkoutLog(day.toEpochDay(), "t", 20, e.toList(), 10, quit, seconds = 1500, extra = extra)

    @Test fun namesJoinInMalay() {
        assertEquals("a", SessionReview.names(listOf("a")))
        assertEquals("a dan b", SessionReview.names(listOf("a", "b")))
        assertEquals("a, b dan c", SessionReview.names(listOf("a", "b", "c")))
    }

    @Test fun recordUpperOnlyAndTomorrowLegs() {
        val earlier = listOf(log(monday.minusDays(7), ExerciseLog("push", sets(10, 10)), ExerciseLog("pullup", sets(6, 6))))
        val today = log(monday, ExerciseLog("push", sets(13, 11)), ExerciseLog("pullup", sets(6, 6)))
        val s = SessionReview.sentences(today, earlier, plan)
        assertEquals("Sesi 25 minit, 2 move, 4 set siap.", s[0])
        assertTrue(s.toString(), s.any { it.startsWith("Rekod baru untuk Push-up.") })
        assertTrue(s.any { it == "Kaki tak disentuh hari ni." })
        assertEquals("Esok Lower body, otot lain pula. Yang kerja hari ni dapat rehat.", s.last())
    }

    @Test fun dropSkipAndFlaggedAreSaid() {
        val earlier = listOf(log(monday.minusDays(3), ExerciseLog("push", sets(12, 12))))
        val today = log(monday, ExerciseLog("push", sets(10, 9)), ExerciseLog("pike", emptyList(), skipped = true), ExerciseLog("lateral", listOf(SetLog(10, flagged = true), SetLog(10))))
        val s = SessionReview.sentences(today, earlier, plan)
        assertTrue(s.toString(), s.any { it.startsWith("Push-up turun 5 rep.") })
        assertTrue(s.toString(), s.any { it.startsWith("Ko skip Pike push-up") })
        assertTrue(s.any { it.startsWith("1 set terlalu laju") })
    }

    @Test fun extraQuitAndRestTomorrow() {
        val tue = monday.plusDays(1) // Wed is rest
        val s = SessionReview.sentences(log(tue, ExerciseLog("squat", sets(15, 15)), extra = true), emptyList(), plan)
        assertTrue(s[0].startsWith("Sesi tambahan pada hari rest"))
        assertTrue(s.toString(), s.any { it.startsWith("Kali pertama buat Squat") })
        assertTrue(s.last().startsWith("Esok rest day."))
        val q = SessionReview.sentences(log(tue, ExerciseLog("squat", sets(15)), quit = true), emptyList(), null)
        assertTrue(q[0].startsWith("Stop awal"))
    }

    @Test fun sameMusclesTomorrowWarns() {
        val wed = monday.plusDays(2) // Thu is Upper
        val s = SessionReview.sentences(log(wed, ExerciseLog("push", sets(10, 10, 10))), emptyList(), plan)
        assertTrue(s.last(), s.last().startsWith("Esok Upper body, kena dada"))
    }

    @Test fun heartRateSentences() {
        val hr = HrAnalysis.Report(
            132, 168, 200, true, mapOf(HrAnalysis.State.Workout to 900, HrAnalysis.State.Active to 300),
            listOf(HrAnalysis.SetHr("push", 1, 160, 26), HrAnalysis.SetHr("push", 2, 165, 24)), true,
        )
        val s = SessionReview.sentences(log(monday, ExerciseLog("push", sets(10))), emptyList(), null, hr)
        assertTrue(s.toString(), s.contains("Purata HR 132, paling tinggi 168, kebanyakan masa dalam zon senaman."))
        assertTrue(s.contains("Seminit lepas set, HR turun purata 25. Badan pulih cepat."))
    }

    @Test fun nothingCountedGivesOneLine() {
        assertEquals(1, SessionReview.sentences(log(monday, ExerciseLog("push", emptyList(), skipped = true)), emptyList(), plan).size)
    }

    @Test fun extraPickerPrefersRestedMuscles() {
        val sat = monday.plusDays(5) // Sat and Sun rest; Friday was Lower
        val logs = listOf(log(sat.minusDays(1), ExerciseLog("squat", sets(15, 15))))
        val o = ExtraSession.options(plan, logs, sat)
        assertEquals(listOf("Upper", "Lower"), o.map { it.tag })
        assertTrue(o[0].best)
        assertFalse(o[1].best)
        assertTrue(o[1].notes.toString(), o[1].notes.single().startsWith("Semalam dah kerja: peha depan"))
        // Wednesday: Thursday is Upper, so Upper says it is due tomorrow
        val w = ExtraSession.options(plan, emptyList(), monday.plusDays(2))
        assertEquals(listOf("Esok sesi ni dalam plan"), w[0].notes)
        assertTrue(w[1].best)
    }
}
