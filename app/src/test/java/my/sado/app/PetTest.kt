package my.sado.app

import my.sado.app.data.AppState
import my.sado.app.data.FitnessTest
import my.sado.app.data.FoodItem
import my.sado.app.data.Meal
import my.sado.app.data.Pet
import my.sado.app.data.Pets
import my.sado.app.data.Profile
import my.sado.app.data.Progress
import my.sado.app.data.PetDay
import my.sado.app.data.SleepLog
import my.sado.app.data.SportLog
import my.sado.app.data.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

// Spirit animal growth from food logging, the 4-week test reward, and low-impact mode.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PetTest {
    private val today = LocalDate.of(2026, 11, 5)
    private val t = today.toEpochDay()
    private val profile = Profile(age = 28, heightCm = 168f, weightKg = 60f)
    private fun food(protein: Float) = FoodItem("Nasi ayam", "1 pinggan", 500, 600, protein, source = "Anggaran")
    private fun meals(day: Long, n: Int, protein: Float = 10f) = (0 until n).map { Meal(day, 480 + it * 240, listOf(food(protein))) }

    @Test fun unlocksOnTheSecondWorkoutDay() {
        fun w(day: Long, mini: Boolean = false) = WorkoutLog(day, "Full body", 30, emptyList(), 100, false, mini = mini)
        val one = AppState(planStart = t - 30, logs = listOf(w(t - 1)))
        assertEquals(1, Pets.workoutsToUnlock(one))
        assertFalse(Pets.unlocked(one, today))
        // a second session the same day or a Sesi mini doesn't count
        assertFalse(Pets.unlocked(one.copy(logs = one.logs + w(t - 1) + w(t, mini = true)), today))
        assertTrue(Pets.unlocked(one.copy(logs = one.logs + w(t)), today))
        // a logged sport counts as a workout day
        assertTrue(Pets.unlocked(one.copy(sports = listOf(SportLog(t, "Futsal", 60, 1, 400, 50))), today))
    }

    private fun adopted(kind: String = "harimau") = Pets.choose(AppState(profile = profile, planStart = t - 10), kind, today)
    private fun workout(day: Long, quit: Boolean = false) = WorkoutLog(day, "Push day", 20, emptyList(), 100, quit)

    @Test fun foodPaysWhenFedNotWhenLogged() {
        var s = adopted().copy(meals = meals(t, 1))
        assertEquals(0, s.pets.first().growth)
        assertEquals(1, Pets.foodQueue(s, today).size)
        s = Pets.feed(s, today)
        assertEquals(Pets.FOOD_EXP, s.pets.first().growth)
        assertTrue(Pets.foodQueue(s, today).isEmpty())
    }

    @Test fun foodIsCappedAtThreeADayAndComesOneByOne() {
        val five = (0 until 5).map { Meal(t, 480 + it * 60, listOf(FoodItem(listOf("Nasi lemak", "Roti canai", "Laksa", "Satay", "Rojak")[it], "", 0, 0, 0f, source = "Anggaran"))) }
        var s = adopted().copy(meals = five)
        assertEquals(listOf("nasi_lemak", "roti_canai", "laksa"), Pets.foodQueue(s, today).map { it.icon }) // in the order logged
        repeat(5) { s = Pets.feed(s, today) } // extra taps pay nothing
        assertEquals(Pets.FOOD_MAX * Pets.FOOD_EXP, s.pets.first().growth)
        assertEquals(3, Pets.todayExp(s, today).fed)
    }

    private fun growth(s: AppState) = s.pets.first().growth

    @Test fun deletingAFedMealTakesItsExpBackAndLoggingAgainNetsNothing() {
        var s = Pets.feed(adopted().copy(meals = meals(t, 1)), today)
        assertEquals(1, growth(s))
        s = Pets.settle(s.copy(meals = emptyList()))
        assertEquals(0, growth(s))
        assertEquals(0, Pets.todayExp(s, today).fed)
        // the loop: log, feed, delete, log, feed still ends on one meal's EXP
        s = Pets.feed(Pets.settle(s.copy(meals = meals(t, 1))), today)
        assertEquals(1, growth(s))
        assertEquals(s, Pets.settle(s))
    }

    @Test fun deletingOneOfFourMealsWhenThreeWereFedTakesNothing() {
        var s = adopted().copy(meals = meals(t, 4))
        repeat(3) { s = Pets.feed(s, today) }
        s = Pets.settle(s.copy(meals = s.meals.drop(1)))
        assertEquals(3, growth(s))
        assertEquals(3, Pets.todayExp(s, today).fed)
    }

    @Test fun deletingAMealNotYetFedTakesNothing() {
        var s = Pets.feed(adopted().copy(meals = meals(t, 2)), today)
        s = Pets.settle(s.copy(meals = s.meals.take(1)))
        assertEquals(1, growth(s))
        assertTrue(Pets.foodQueue(s, today).isEmpty())
    }

    @Test fun editingAMealTakesNothing() {
        var s = Pets.feed(adopted().copy(meals = meals(t, 1)), today)
        val m = s.meals.first()
        s = Pets.settle(s.copy(meals = listOf(m.copy(items = listOf(food(99f))))))
        assertEquals(1, growth(s))
    }

    @Test fun deletingAPaidWorkoutTakesItsExpBack() {
        var s = Pets.feed(adopted().copy(logs = listOf(workout(t), workout(t))), today)
        assertEquals(Pets.WORKOUT_EXP, growth(s))
        // one of two still counts for the day
        s = Pets.settle(s.copy(logs = s.logs.drop(1)))
        assertEquals(Pets.WORKOUT_EXP, growth(s))
        s = Pets.settle(s.copy(logs = emptyList()))
        assertEquals(0, growth(s))
    }

    @Test fun aTaskCountsAsDoneOnceLoggedBeforeItIsGiven() {
        val s = adopted().copy(meals = meals(t, 5), sleep = listOf(SleepLog(t, 7f, "Jawapan")), logs = listOf(workout(t)))
        assertEquals(PetDay(3, true, true), Pets.todayDone(s, today))
        assertEquals(PetDay(), Pets.todayExp(s, today))
        assertEquals(PetDay(), Pets.todayDone(adopted(), today))
        // yesterday's logs tick nothing today
        assertEquals(PetDay(), Pets.todayDone(adopted().copy(sleep = listOf(SleepLog(t - 1, 7f, "Jawapan"))), today))
    }

    @Test fun takingExpBackStopsAtZeroAndNeverLowersTheStage() {
        var s = Pets.feed(adopted().copy(meals = meals(t, 1)), today)
        s = s.copy(pets = s.pets.map { it.copy(growth = 30) })
        s = Pets.evolve(s)
        s = Pets.settle(s.copy(meals = emptyList()))
        val p = Pets.active(s)!!
        assertEquals(29, p.growth)
        assertEquals(1, Pets.stage(p)) // under Remaja's EXP but still Remaja
        assertEquals(0f, Pets.progress(p))
        assertFalse(Pets.canEvolve(p))
        // and a pet with no EXP left doesn't go below 0
        val fresh = Pets.feed(adopted().copy(meals = meals(t, 1)), today)
        val zero = Pets.settle(fresh.copy(pets = fresh.pets.map { it.copy(growth = 0) }, meals = emptyList()))
        assertEquals(0, growth(zero))
    }

    @Test fun lastNightsMealCanStillBeFedTodayButCountsForItsOwnDay() {
        var s = adopted().copy(meals = meals(t - 1, 1) + meals(t, 3))
        assertEquals(listOf(t - 1, t, t, t), Pets.foodQueue(s, today).map { it.day })
        repeat(4) { s = Pets.feed(s, today) }
        assertEquals(4, s.pets.first().growth)
        assertEquals(3, Pets.todayExp(s, today).fed)
        // two days back has gone
        assertTrue(Pets.foodQueue(adopted().copy(meals = meals(t - 2, 2)), today).isEmpty())
    }

    @Test fun sleepAndWorkoutWaitAsEnergyDrinksAndPayOnceADay() {
        var s = adopted().copy(sleep = listOf(SleepLog(t, 7f, "Jawapan")))
        assertEquals(0, s.pets.first().growth) // nothing until it is given
        assertEquals(listOf(Pets.ENERGY_ICON to Pets.SLEEP_EXP), Pets.foodQueue(s, today).map { it.icon to it.exp })
        s = Pets.feed(s, today)
        assertEquals(Pets.SLEEP_EXP, s.pets.first().growth)
        // a changed answer replaces the log: nothing more
        s = s.copy(sleep = listOf(SleepLog(t, 8f, "Jawapan")))
        assertTrue(Pets.foodQueue(s, today).isEmpty())
        s = s.copy(logs = listOf(workout(t), workout(t), workout(t)))
        assertEquals(listOf(Pets.ENERGY_ICON to Pets.WORKOUT_EXP), Pets.foodQueue(s, today).map { it.icon to it.exp })
        s = Pets.feed(s, today)
        assertEquals(Pets.SLEEP_EXP + Pets.WORKOUT_EXP, s.pets.first().growth)
        assertEquals(s, Pets.feed(s, today))
    }

    @Test fun aQuitWorkoutPaysNothingButSportDoes() {
        assertTrue(Pets.foodQueue(adopted().copy(logs = listOf(workout(t, quit = true))), today).isEmpty())
        val sport = Pets.feed(adopted().copy(sports = listOf(SportLog(t, "Futsal", 60, 1, 480, 40))), today)
        assertEquals(Pets.WORKOUT_EXP, sport.pets.first().growth)
    }

    @Test fun aFullDayIsTheMaxWithMealsFirst() {
        var s = adopted().copy(meals = meals(t, 6), sleep = listOf(SleepLog(t, 7f, "Jawapan")), logs = listOf(workout(t)))
        assertEquals(listOf(Pets.FOOD_MAX + 2, 2), Pets.foodQueue(s, today).let { listOf(it.size, it.count { f -> f.icon == Pets.ENERGY_ICON }) })
        assertEquals(Pets.ENERGY_ICON, Pets.foodQueue(s, today).last().icon)
        repeat(8) { s = Pets.feed(s, today) }
        assertEquals(7, s.pets.first().growth) // 3 + 2 + 2
    }

    @Test fun simulatedFirstWeekShowsTheGift() {
        val s = Pets.simulateFirstWeek(Pets.feed(adopted().copy(planStart = t - 2, meals = meals(t, 1)), today), today)
        assertNull(Pets.active(s))
        assertTrue(s.pets.isEmpty())
        assertEquals(0, Pets.workoutsToUnlock(s))
        assertTrue(Pets.unlocked(s, today))
        // real workouts stay, only what's missing is added
        assertEquals(2, Pets.workoutDays(Pets.simulateFirstWeek(AppState(planStart = t - 30), today)).size)
    }

    @Test fun onlyActivePetGrowsAndNoDayPaysTwice() {
        var s = Pets.feed(adopted().copy(meals = meals(t, 2)), today)
        assertEquals(1, s.pets.first().growth)
        // switching keeps the tiger's EXP, and what is already paid is not paid again to the eagle
        s = Pets.choose(s, "helang", today)
        assertEquals(1, Pets.foodQueue(s, today).size)
        s = Pets.feed(s, today)
        assertEquals(1, s.pets.first { it.kind == "harimau" }.growth)
        assertEquals(1, s.pets.first { it.kind == "helang" }.growth)
        assertTrue(Pets.foodQueue(s, today).isEmpty())
    }

    @Test fun firstChoiceDoesNotBackPay() {
        // what was logged before the pet was picked stays unpaid
        val s = Pets.choose(AppState(profile = profile, meals = meals(t, 2), sleep = listOf(SleepLog(t, 7f, "Jawapan"))), "buaya", today)
        assertEquals(0, s.pets.first().growth)
        assertTrue(Pets.foodQueue(s, today).isEmpty())
    }

    @Test fun reachingTheExpDoesNotEvolveUntilAsked() {
        var s = adopted()
        assertEquals(0, s.pets.first().stage)
        s = s.copy(pets = s.pets.map { it.copy(growth = 68) }, sleep = listOf(SleepLog(t, 7f, "Jawapan")))
        s = Pets.feed(s, today)
        var p = Pets.active(s)!!
        assertEquals(0, Pets.stage(p)) // it stays as it is
        assertTrue(Pets.canEvolve(p))
        assertEquals(1f, Pets.progress(p))
        assertEquals(0, Pets.toNext(p)!!.first)
        s = Pets.evolve(s)
        p = Pets.active(s)!!
        assertEquals(1, Pets.stage(p))
        assertFalse(Pets.canEvolve(p))
        assertEquals(s, Pets.evolve(s)) // not again until the next stage's EXP
    }

    @Test fun evolvesOneStageAtATime() {
        var s = adopted()
        s = s.copy(pets = s.pets.map { it.copy(growth = 600) })
        for (stage in 1..3) {
            s = Pets.evolve(s)
            assertEquals(stage, Pets.stage(Pets.active(s)!!))
        }
        assertFalse(Pets.canEvolve(Pets.active(s)!!))
        assertEquals(s, Pets.evolve(s))
    }

    @Test fun stagesFollowExp() {
        assertEquals(0, Pets.reached(29))
        assertEquals(1, Pets.reached(30))
        assertEquals(2, Pets.reached(240))
        assertEquals(3, Pets.reached(540))
        assertNull(Pets.toNext(Pet("beruang", growth = 600)))
    }

    @Test fun oldSavesKeepTheirStage() {
        val old = AppState(pets = listOf(Pet("harimau", growth = 7), Pet("helang", growth = 14), Pet("beruang", growth = 49), Pet("seladang", growth = 150), Pet("buaya", growth = 160)))
        val s = Pets.migrate(old)
        assertEquals(listOf(0, 1, 1, 3, 3), s.pets.map { it.stage })
        assertEquals(listOf(15, 30, 234, 540, 560), s.pets.map { it.growth })
        // none is left owing an evolution, and running it again changes nothing
        assertTrue(s.pets.none { Pets.canEvolve(it) })
        assertEquals(s, Pets.migrate(s))
    }

    @Test fun mood() {
        val noon = LocalDateTime.of(2026, 11, 5, 12, 0)
        val evening = LocalDateTime.of(2026, 11, 5, 18, 0)
        assertEquals(Pets.Mood.Tidur, Pets.mood(AppState(), noon))
        assertEquals(Pets.Mood.Tidur, Pets.mood(AppState(meals = meals(t - 2, 1)), noon))
        assertEquals(Pets.Mood.Kenyang, Pets.mood(AppState(meals = listOf(Meal(t, 11 * 60, listOf(food(10f))))), noon))
        assertEquals(Pets.Mood.Lapar, Pets.mood(AppState(meals = meals(t - 1, 1)), evening))
        assertEquals(Pets.Mood.Biasa, Pets.mood(AppState(meals = meals(t - 1, 1)), noon))
    }

    @Test fun testReward() {
        val first = FitnessTest(t - 28, 18, 60, null)
        assertEquals(50, Progress.testReward(null, first).total)
        val r = Progress.testReward(first, FitnessTest(t, 24, 75, null))
        assertEquals(50 + 6 * 5 + 3, r.total)
        assertEquals(listOf("push", "plank"), r.records)
        // a worse day still pays for showing up, and the bonus is capped
        assertEquals(50, Progress.testReward(first, FitnessTest(t, 10, 30, null)).total)
        assertEquals(150, Progress.testReward(first, FitnessTest(t, 60, 300, null)).total)
    }

    @Test fun lowImpact() {
        assertFalse(Progress.lowImpact(profile))
        assertTrue(Progress.lowImpact(Profile(heightCm = 170f, weightKg = 90f))) // BMI 31
        assertTrue(Progress.lowImpact(Profile(heightCm = 170f, weightKg = 80f, waistCm = 104f))) // WHtR 0.61
        val a = my.sado.app.data.PlannerAnswers(shape = listOf("lean"), level = 1, days = 3, time = 20, injury = listOf("impact"))
        val moves = my.sado.app.data.PlanBuilder.build(a, emptyMap()).allSessions.flatMap { it.exercises }.map { it.id }
        assertTrue(moves.none { it in setOf("jack", "highknee", "burpee") })
        assertTrue(my.sado.app.data.Templates.session(my.sado.app.data.Templates.all.first { it.id == "stamina" }, a, emptyMap()).none { it.id == "jack" })
    }
}
