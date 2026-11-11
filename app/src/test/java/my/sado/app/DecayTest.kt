package my.sado.app

import my.sado.app.data.AppState
import my.sado.app.data.Progress
import my.sado.app.data.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

// Time off: tired after 8 days, one tier lost at day 21 and every 14 days after, never below tier I.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DecayTest {
    private val today = LocalDate.now() // weeksActive counts to now

    // trained 3x a week for [weeks] weeks, the last session [idle] days ago
    private fun state(weeks: Int, idle: Int, xp: Int): AppState {
        val last = today.toEpochDay() - idle
        val logs = (0 until weeks * 3).map { i -> WorkoutLog(last - i * 7L / 3, "w", 30, emptyList(), 100, false) }.reversed()
        return AppState(xp = xp, logs = logs, levelFloor = 0, peakXp = xp)
    }

    @Test fun weekOffIsRest() {
        val s = state(weeks = 9, idle = 7, xp = 2400) // Berisi III
        assertFalse(Progress.isTired(s, today))
        assertEquals(s, Progress.applyDecay(s, today))
    }

    @Test fun tiredButNoDropBeforeDay21() {
        val s = state(weeks = 9, idle = 20, xp = 2400)
        assertTrue(Progress.isTired(s, today))
        assertEquals(1, Progress.daysToDrop(s, today))
        assertEquals("Berisi III", Progress.stageFor(Progress.applyDecay(s, today)).name)
    }

    @Test fun dropsOneTierAtDay21ThenEvery14() {
        val s = state(weeks = 9, idle = 21, xp = 2400)
        val d = Progress.applyDecay(s, today)
        assertEquals("Berisi II", Progress.stageFor(d).name)
        assertEquals(2400, d.peakXp)
        assertTrue(Progress.dropped(d))
        // same day again: nothing more
        assertEquals(d, Progress.applyDecay(d, today))
        // 14 days later: one more
        assertEquals("Berisi I", Progress.stageFor(Progress.applyDecay(d, today.plusDays(14))).name)
    }

    @Test fun neverBelowTierOneOfTheBody() {
        val s = state(weeks = 9, idle = 200, xp = 2400)
        val d = Progress.applyDecay(s, today)
        assertEquals("Berisi I", Progress.stageFor(d).name)
        assertEquals(null, Progress.daysToDrop(d, today))
    }

    // an old-ladder floor of Berisi I must not lift a fresh start at 300 XP past the week gates
    @Test fun oldFloorNeedsTheWeeks() {
        val s = state(weeks = 2, idle = 0, xp = 300).copy(levelFloor = 3)
        assertEquals("Kurus II", Progress.stageFor(s).name)
        // with the weeks behind it, the floor still holds
        assertEquals("Berisi I", Progress.stageFor(state(weeks = 5, idle = 0, xp = 300).copy(levelFloor = 3)).name)
    }

    @Test fun comebackDoublesUpToTheBest() {
        val d = Progress.applyDecay(state(weeks = 9, idle = 21, xp = 2400), today) // xp now 1800
        assertEquals(150, Progress.comebackBonus(d, 150))
        assertEquals(100, Progress.comebackBonus(d.copy(xp = 2300), 150))
        assertEquals(0, Progress.comebackBonus(d.copy(xp = 2400), 150))
    }
}
