package my.sado.app

import my.sado.app.data.ExerciseLog
import my.sado.app.data.LadderProgress
import my.sado.app.data.SessionSummary
import my.sado.app.data.SetLog
import my.sado.app.data.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSummaryTest {
    private fun sets(vararg r: Int) = r.map { SetLog(it) }
    private fun log(vararg e: ExerciseLog) = WorkoutLog(1, "t", 20, e.toList(), 10, false)

    @Test fun setsTextSameAndMixed() {
        assertEquals("3 × 10", SessionSummary.setsText(ExerciseLog("push", sets(10, 10, 10)), false))
        assertEquals("12 · 10 · 8", SessionSummary.setsText(ExerciseLog("push", sets(12, 10, 8)), false))
        assertEquals("2 × 40s", SessionSummary.setsText(ExerciseLog("plank", sets(40, 40)), true))
        assertEquals("–", SessionSummary.setsText(ExerciseLog("push", emptyList(), skipped = true), false))
    }

    @Test fun setsTextShowsLoad() {
        val e = ExerciseLog("wpush", listOf(SetLog(8, kg = 5f), SetLog(7, kg = 5f)))
        assertEquals("8 · 7 @ 5 kg", SessionSummary.setsText(e, false))
        assertEquals("8 @ 7.5 kg", SessionSummary.setsText(ExerciseLog("wpush", listOf(SetLog(8, kg = 7.5f))), false))
    }

    @Test fun firstTimeIsNotARecord() {
        val r = SessionSummary.rows(log(ExerciseLog("push", sets(10, 9))), emptyList()).single()
        assertTrue(r.first)
        assertFalse(r.pr)
        assertNull(r.delta)
        assertEquals("Push-up", r.name)
    }

    @Test fun recordAndDeltaAgainstLastSession() {
        val earlier = listOf(log(ExerciseLog("push", sets(12, 8))), log(ExerciseLog("push", sets(10, 10, 9))))
        val r = SessionSummary.rows(log(ExerciseLog("push", sets(13, 10, 10))), earlier).single()
        assertTrue(r.pr) // 13 beats 12
        assertFalse(r.first)
        assertEquals(4, r.delta) // 33 against the last session's 29
    }

    @Test fun flaggedSetsDontMakeARecord() {
        val earlier = listOf(log(ExerciseLog("push", sets(12))))
        val now = ExerciseLog("push", listOf(SetLog(20, flagged = true), SetLog(10)))
        val r = SessionSummary.rows(log(now), earlier).single()
        assertFalse(r.pr)
        assertEquals(1, r.flagged)
        assertEquals(-2, r.delta)
    }

    @Test fun skippedMoveHasNoNumbers() {
        val earlier = listOf(log(ExerciseLog("push", sets(12))))
        val r = SessionSummary.rows(log(ExerciseLog("push", emptyList(), skipped = true)), earlier).single()
        assertTrue(r.skipped)
        assertFalse(r.pr)
        assertNull(r.delta)
    }

    @Test fun upsFindLadderClimbAndLoad() {
        val logs = listOf(ExerciseLog("push", sets(15, 15, 15)), ExerciseLog("wpush", listOf(SetLog(8, kg = 5f))))
        val u = SessionSummary.ups(
            beforeLadders = mapOf("push" to LadderProgress(1)), afterLadders = mapOf("push" to LadderProgress(2), "squat" to LadderProgress(0)),
            beforeLoads = mapOf("wpush" to 5f), afterLoads = mapOf("wpush" to 7.5f), logs = logs,
        )
        assertEquals(mapOf("push" to "decline"), u.climbed)
        assertEquals(mapOf("wpush" to 7.5f), u.kgNext)
    }

    @Test fun upsIgnoreUnchanged() {
        val logs = listOf(ExerciseLog("wpush", listOf(SetLog(6, kg = 5f))))
        val u = SessionSummary.ups(mapOf("push" to LadderProgress(2)), mapOf("push" to LadderProgress(2)), mapOf("wpush" to 5f), mapOf("wpush" to 5f), logs)
        assertTrue(u.climbed.isEmpty())
        assertTrue(u.kgNext.isEmpty())
    }
}
