package my.sado.app

import my.sado.app.data.Achievements
import my.sado.app.data.AppState
import my.sado.app.data.ExerciseLog
import my.sado.app.data.PlannerAnswers
import my.sado.app.data.Profile
import my.sado.app.data.SetLog
import my.sado.app.data.WeightLog
import my.sado.app.data.WorkoutLog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementGoalTest {
    private val bulking = AppState(
        profile = Profile(age = 25, heightCm = 169f, weightKg = 55f),
        answers = PlannerAnswers(shape = listOf("vshape"), body = "skinny"),
        weights = listOf(WeightLog(1, 55f), WeightLog(30, 57.1f)),
    )

    private fun ids(s: AppState) = Achievements.all(s).associateBy { it.id }

    @Test fun aBulkCountsKilosGained() {
        val a = ids(bulking)
        assertTrue(a.getValue("gain2").unlocked)
        assertFalse(a.getValue("gain5p").unlocked) // 3.8% so far
        assertFalse("lost2" in a)
    }

    @Test fun strengthFirstsComeFromRealSets() {
        fun log(id: String, reps: Int, flagged: Boolean = false) = WorkoutLog(5, "A", 20, listOf(ExerciseLog(id, listOf(SetLog(reps, flagged = flagged)))), 30, false)
        val s = bulking.copy(logs = listOf(log("push", 6), log("pullup", 10), log("archer", 4, flagged = true)))
        val a = ids(s)
        assertTrue(a.getValue("push1").unlocked)
        assertTrue(a.getValue("pullup").unlocked)
        assertTrue(a.getValue("pull10").unlocked)
        assertFalse(a.getValue("archer1").unlocked) // only a flagged set
        assertFalse(a.getValue("pistol").unlocked)
    }
}
