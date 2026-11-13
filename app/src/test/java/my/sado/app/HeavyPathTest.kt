package my.sado.app

import my.sado.app.data.AppState
import my.sado.app.data.PlannerAnswers
import my.sado.app.data.Profile
import my.sado.app.data.Progress
import my.sado.app.data.Settings
import my.sado.app.data.WeightLog
import my.sado.app.data.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

// Gempal → Bangkit: slower start, faster finish, both paths reach Fit I at week 10; 5% weight loss opens Bangkit I.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeavyPathTest {
    private val today = LocalDate.now().toEpochDay()

    private fun state(weeks: Int, xp: Int, path: String? = "gempal", weights: List<WeightLog> = emptyList()): AppState {
        val logs = (0..weeks * 3).map { i -> WorkoutLog(today - i * 7L / 3, "w", 30, emptyList(), 100, false) }.reversed()
        return AppState(xp = xp, logs = logs, levelFloor = 0, peakXp = xp, weights = weights, settings = Settings(bodyPath = path))
    }

    @Test fun autoPicksPath() {
        val big = AppState(profile = Profile(heightCm = 170f, weightKg = 90f))
        val slim = AppState(profile = Profile(heightCm = 170f, weightKg = 60f))
        assertTrue(Progress.heavyPath(big)) // BMI 31
        assertFalse(Progress.heavyPath(slim))
        assertTrue(Progress.heavyPath(slim.copy(answers = PlannerAnswers(body = "heavy"))))
        // waist beats BMI: a muscular 90 kg with a slim waist stays on Kurus → Berisi
        assertFalse(Progress.heavyPath(big.copy(profile = big.profile!!.copy(waistCm = 80f))))
        assertFalse(Progress.heavyPath(big.copy(settings = Settings(bodyPath = "kurus"))))
    }

    @Test fun slowerStartSameFit() {
        // week 4 with the XP for it: Berisi I on the slim path, still Gempal III on the big one
        assertEquals("Berisi I", Progress.stageFor(state(4, 1200, "kurus")).name)
        assertEquals("Gempal III", Progress.stageFor(state(4, 1200)).name)
        assertEquals("Bangkit I", Progress.stageFor(state(6, 1200)).name)
        assertEquals("Bangkit III", Progress.stageFor(state(8, 2400)).name)
        assertEquals("Fit I", Progress.stageFor(state(10, 2000)).name)
        assertEquals("Fit I", Progress.stageFor(state(10, 2000, "kurus")).name)
        assertEquals(my.sado.app.R.drawable.gempal3, Progress.stageFor(state(4, 1200)).image)
    }

    @Test fun fivePercentOpensBangkit() {
        val first = today - 30
        // 100 → 95 kg, the 95 logged 25 days after the first weigh-in
        val s = state(2, 600, weights = listOf(WeightLog(first, 100f), WeightLog(first + 25, 95f)))
        assertEquals("Bangkit I", Progress.stageFor(s).name)
        assertTrue(Progress.requirement(Progress.heavyStages[3], s).startsWith("Dah layak"))
        // same drop logged within three weeks doesn't count
        val quick = state(2, 600, weights = listOf(WeightLog(first, 100f), WeightLog(first + 5, 95f)))
        assertEquals("Gempal II", Progress.stageFor(quick).name)
        assertTrue(Progress.requirement(Progress.heavyStages[3], quick).endsWith("atau turun 5% berat"))
    }

    @Test fun provePlacement() {
        val man = Profile(age = 25, sex = 0, heightCm = 168f, weightKg = 55f)
        assertEquals(Progress.ProveBar(25, 75), Progress.proveBar(man))
        assertEquals(Progress.ProveBar(19, 56), Progress.proveBar(man.copy(age = 45)))
        assertEquals(Progress.ProveBar(13, 75), Progress.proveBar(man.copy(sex = 1)))
        assertFalse(Progress.provePassed(man, 24, 120))
        assertFalse(Progress.provePassed(man, 40, 74))
        assertTrue(Progress.provePassed(man, 25, 75))
        // a slim 168 cm / 55 kg (BMI 19.5) on the Kurus path is not offered the test: the body is not Berisi yet
        assertFalse(Progress.proveOffered(AppState(profile = man)))
        assertTrue(Progress.proveOffered(AppState(profile = man.copy(weightKg = 62f))))
        // a fresh user who passes starts at Berisi I and still needs real weeks for Berisi II
        val placed = Progress.place(AppState(profile = man))
        assertEquals("Berisi I", Progress.stageFor(placed).name)
        assertEquals(4, Progress.weeksActive(placed))
        assertEquals("Berisi I", Progress.stageFor(placed.copy(xp = 1200)).name)
        // heavy path lands on Bangkit I with its own XP and weeks
        val big = Progress.place(AppState(profile = Profile(heightCm = 170f, weightKg = 85f)))
        assertEquals("Bangkit I", Progress.stageFor(big).name)
        assertEquals(1200, big.xp)
        // switching path later never drops below level 4
        assertEquals("Bangkit I", Progress.stageFor(placed.copy(settings = Settings(bodyPath = "gempal"))).name)
        assertFalse(Progress.proveOffered(AppState(profile = Profile(heightCm = 165f, weightKg = 95f))))
    }
}
