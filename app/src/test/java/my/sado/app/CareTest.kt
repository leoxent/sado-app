package my.sado.app

import my.sado.app.data.AppState
import my.sado.app.data.Care
import my.sado.app.data.CheckIn
import my.sado.app.data.HealthCheck
import my.sado.app.data.Nutrition
import my.sado.app.data.Pets
import my.sado.app.data.PlannerAnswers
import my.sado.app.data.Profile
import my.sado.app.data.Progress
import my.sado.app.data.RamadanDates
import my.sado.app.data.RestSpell
import my.sado.app.data.WeeklyCheck
import my.sado.app.data.WeightLog
import my.sado.app.data.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

// Age limit, calorie minimum, health answers and rest mode: the rules behind design/kajian-keselamatan.md.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CareTest {
    private val heavy = PlannerAnswers(body = "heavy")
    private val today = LocalDate.now()
    private val day = today.toEpochDay()

    // ── calories ──
    @Test fun aWomansCutStopsAt1200() {
        val small = Profile(sex = 1, age = 59, heightCm = 152f, weightKg = 60f, activity = 0) // upkeep about 1,430
        val t = Nutrition.targets(small, heavy, adjust = -450)
        assertEquals("Cut", t.mode)
        assertEquals(1200, t.kcal)
        assertTrue(t.atFloor)
    }

    @Test fun aMansCutStopsAt1500() {
        val t = Nutrition.targets(Profile(sex = 0, age = 40, heightCm = 160f, weightKg = 62f, activity = 0), heavy, adjust = -600)
        assertEquals(1500, t.kcal)
    }

    @Test fun aDeficitNeverPasses750() {
        val big = Profile(sex = 0, age = 30, heightCm = 185f, weightKg = 120f, activity = 2)
        val keep = Nutrition.maintenance(big)
        assertEquals(keep - 750, Nutrition.targets(big, heavy, adjust = -600).kcal)
    }

    @Test fun aBodyWithUpkeepUnderTheMinimumEatsItsUpkeep() {
        val tiny = Profile(sex = 0, age = 70, heightCm = 150f, weightKg = 45f, activity = 0)
        val keep = Nutrition.maintenance(tiny)
        assertTrue(keep < Care.FLOOR_MEN)
        assertEquals(keep, Nutrition.targets(tiny, heavy).kcal)
    }

    @Test fun anOrdinaryCutIsUntouched() {
        val p = Profile(sex = 0, age = 28, heightCm = 168f, weightKg = 78f, activity = 1)
        val t = Nutrition.targets(p, heavy)
        assertEquals(((Nutrition.maintenance(p) * 0.8f) / 10).toInt() * 10 / 10, t.kcal / 10)
        assertFalse(t.atFloor)
    }

    @Test fun nursingPregnantOrEatingHistoryMeansNoCut() {
        val p = Profile(sex = 1, age = 30, heightCm = 160f, weightKg = 75f, activity = 1)
        val base = AppState(profile = p, answers = heavy, kcalAdjust = -300)
        assertEquals("Cut", Nutrition.targets(base, p).mode)
        val keep = Nutrition.maintenance(p)
        assertEquals(keep + Care.NURSING_EXTRA, Nutrition.targets(base.copy(nursing = true), p).kcal)
        for (h in listOf(HealthCheck(day, pregnant = true), HealthCheck(day, eating = true))) {
            val t = Nutrition.targets(base.copy(health = h), p)
            assertEquals("Kekal", t.mode)
            assertEquals(keep, t.kcal)
        }
    }

    @Test fun sicknessAndBirthPauseTheCutButExamsDoNot() {
        val p = Profile(sex = 1, age = 30, heightCm = 160f, weightKg = 75f, activity = 1)
        val base = AppState(profile = p, answers = heavy)
        assertEquals("Kekal", Nutrition.targets(Care.start(base, Care.SICK, day), p).mode)
        assertEquals("Kekal", Nutrition.targets(Care.start(base, Care.BIRTH, day), p).mode)
        assertEquals("Cut", Nutrition.targets(Care.start(base, Care.EXAM, day, 7), p).mode)
    }

    // ── weekly check ──
    private fun gaining(p: Profile, adjust: Int, weeksOff: Int) = AppState(
        profile = p, answers = heavy, kcalAdjust = adjust,
        weights = (0..13).map { WeightLog(day - it, if (it <= 6) p.weightKg + 1f else p.weightKg) },
        checkins = listOf(CheckIn(day - 7, offTrack = 1, weeksOff = weeksOff)),
    )

    @Test fun theWeeklyCheckCannotCutBelowTheMinimum() {
        val p = Profile(sex = 1, age = 59, heightCm = 152f, weightKg = 60f, activity = 0)
        val out = WeeklyCheck.run(gaining(p, -450, 1), day)
        val c = out.checkins.last()
        assertEquals(0, c.kcalChange)
        assertTrue(c.floored)
        assertEquals(-450, out.kcalAdjust)
        assertTrue(WeeklyCheck.lines(c).first().second.contains("had minimum"))
    }

    @Test fun theWeeklyCheckStillCutsWhenThereIsRoom() {
        val p = Profile(sex = 0, age = 28, heightCm = 175f, weightKg = 90f, activity = 1)
        val out = WeeklyCheck.run(gaining(p, 0, 1), day)
        assertEquals(-150, out.checkins.last().kcalChange)
        assertEquals(-150, out.kcalAdjust)
    }

    @Test fun theWeeklyCheckPromisesNoCutWhenDeficitIsOff() {
        val p = Profile(sex = 1, age = 30, heightCm = 160f, weightKg = 75f, activity = 1)
        val out = WeeklyCheck.run(gaining(p, 0, 1).copy(nursing = true), day)
        val c = out.checkins.last()
        assertEquals(0, c.kcalChange)
        assertTrue(c.noCut)
        assertEquals(0, c.weeksOff)
        assertFalse(WeeklyCheck.lines(c).first().second.contains("diturunkan"))
    }

    @Test fun restModeFreezesTheWeeklyCheck() {
        val p = Profile(sex = 0, age = 28, heightCm = 175f, weightKg = 90f, activity = 1)
        val out = WeeklyCheck.run(Care.start(gaining(p, 0, 1), Care.EXAM, day - 1, 7), day)
        val c = out.checkins.last()
        assertEquals(0, c.kcalChange)
        assertTrue(c.rest)
        assertEquals(0, c.weeksOff)
        assertEquals(1, WeeklyCheck.lines(c).size)
    }

    // ── rest mode ──
    private fun trained(idle: Int, xp: Int = 2400): AppState {
        val last = day - idle
        return AppState(xp = xp, levelFloor = 0, peakXp = xp, logs = (0 until 27).map { i -> WorkoutLog(last - i * 7L / 3, "w", 30, emptyList(), 100, false) }.reversed())
    }

    @Test fun restDaysAreNotIdleDays() {
        val s = trained(30).copy(rest = listOf(RestSpell(Care.EXAM, day - 28, day)))
        assertEquals(1, Progress.idleDays(s, today))
        assertFalse(Progress.isTired(s, today))
    }

    @Test fun nothingDropsDuringRest() {
        val off = trained(40)
        assertTrue(Progress.applyDecay(off, today).xp < off.xp)
        val s = off.copy(rest = listOf(RestSpell(Care.SICK, day - 38)))
        assertEquals(s, Progress.applyDecay(s, today))
        assertNull(Progress.daysToDrop(s, today))
    }

    @Test fun theClockRunsAgainAfterRest() {
        val s = trained(40).copy(rest = listOf(RestSpell(Care.EXAM, day - 38, day - 30)))
        assertEquals(31, Progress.idleDays(s, today))
        assertTrue(Progress.isTired(s, today))
        assertTrue(Progress.applyDecay(s, today).xp < s.xp)
    }

    @Test fun restKeepsTheStreak() {
        val monday = today.minusDays(today.dayOfWeek.value - 1L)
        // trained until five weeks ago, nothing since, then one session this week
        val train = (5..12).flatMap { w -> listOf(0L, 2L).map { monday.minusWeeks(w.toLong()).plusDays(it).toEpochDay() } }
        val logs = (train + day).sorted().map { WorkoutLog(it, "w", 30, emptyList(), 100, false) }
        val broken = AppState(logs = logs)
        val rested = broken.copy(rest = listOf(RestSpell(Care.EXAM, monday.minusWeeks(5).toEpochDay(), monday.toEpochDay() - 1)))
        assertEquals(1, Progress.streak(broken, today).days)
        assertTrue(Progress.streak(rested, today).days > 30)
    }

    @Test fun startingAndEndingASpell() {
        val on = Care.start(AppState(), Care.SICK, day - 5)
        assertNotNull(Care.active(on, day))
        val off = Care.end(on, day)
        assertNull(Care.active(off, day))
        assertEquals(day - 1, off.rest.single().until)
        // begun and ended the same day leaves nothing
        assertTrue(Care.end(Care.start(AppState(), Care.EXAM, day, 7), day).rest.isEmpty())
        assertEquals(day + 83, Care.start(AppState(), Care.BIRTH, day).rest.single().until)
    }

    @Test fun aBirthEndsThePregnancyAnswer() {
        val s = Care.start(AppState(health = HealthCheck(day, pregnant = true)), Care.BIRTH, day)
        assertFalse(s.health!!.pregnant)
    }

    @Test fun birthGuidanceFollowsTheWeek() {
        val spell = RestSpell(Care.BIRTH, day - 35)
        assertTrue(Care.tips(spell, day).first().startsWith("Minggu 6"))
        assertTrue(Care.tips(spell, day).any { it.contains("minggu 12") })
    }

    @Test fun aLongIllnessPointsToADoctor() {
        assertFalse(Care.tips(RestSpell(Care.SICK, day - 3), day).any { it.contains("2 minggu") })
        assertTrue(Care.tips(RestSpell(Care.SICK, day - 14), day).any { it.contains("2 minggu") })
    }

    // ── Ramadan ──
    private val reply = """{"prayerTime":[
        {"hijri":"1447-08-29","date":"17-Feb-2026"},
        {"hijri":"1447-09-01","date":"19-Feb-2026"},
        {"hijri":"1447-09-30","date":"20-Mac-2026"},
        {"hijri":"1447-10-01","date":"21-Mac-2026"}],"status":"OK!"}"""

    @Test fun readsJakimsCalendar() {
        val r = Care.parseRamadan(reply, 2026)!!
        assertEquals(LocalDate.of(2026, 2, 19).toEpochDay(), r.start)
        assertEquals(LocalDate.of(2026, 3, 20).toEpochDay(), r.end)
    }

    @Test fun aBrokenReplyIsJustNoDates() {
        assertNull(Care.parseRamadan("<html>down</html>", 2026))
        assertNull(Care.parseRamadan("""{"prayerTime":[{"hijri":"1447-08-29","date":"17-Feb-2026"}]}""", 2026))
        assertNull(Care.parseDay("31-Xyz-2026"))
        assertEquals(LocalDate.of(2026, 8, 3).toEpochDay(), Care.parseDay("03-Ogos-2026"))
    }

    @Test fun ramadanIsOfferedOnceAndEndsWithTheCalendar() {
        val dates = RamadanDates(today.year, day - 2, day + 27)
        val s = AppState(ramadan = dates)
        assertTrue(Care.ramadanOffer(s, day))
        assertFalse(Care.ramadanOffer(s, day + 28))
        assertFalse(Care.ramadanOffer(s.copy(ramadanAsked = today.year), day))
        val on = Care.start(s, Care.RAMADAN, day)
        assertEquals(day + 27, on.rest.single().until)
        assertFalse(Care.ramadanOffer(on, day))
    }

    @Test fun thePetDoesNotCallAFastingOwnerHungry() {
        val noon = LocalDateTime.now().withHour(15)
        val meal = my.sado.app.data.Meal(day - 1, 1200, emptyList())
        val s = AppState(meals = listOf(meal))
        assertEquals(Pets.Mood.Lapar, Pets.mood(s, noon))
        assertEquals(Pets.Mood.Biasa, Pets.mood(Care.start(s, Care.RAMADAN, day), noon))
    }

    // ── age and health answers ──
    @Test fun under18IsUnderAge() {
        assertTrue(Care.underAge(Profile(age = 17)))
        assertFalse(Care.underAge(Profile(age = 18)))
        assertFalse(Care.underAge(null))
    }

    @Test fun healthQuestionsComeDueOnceAYear() {
        val p = Profile()
        assertFalse(Care.healthDue(AppState(), day))
        assertTrue(Care.healthDue(AppState(profile = p), day))
        assertFalse(Care.healthDue(AppState(profile = p, health = HealthCheck(day - 364)), day))
        assertTrue(Care.healthDue(AppState(profile = p, health = HealthCheck(day - 365)), day))
    }

    @Test fun answersBecomeAdvice() {
        assertEquals(Care.Level.Clear, Care.advice(HealthCheck(day), 30, false).first().first)
        assertEquals(Care.Level.Stop, Care.advice(HealthCheck(day, chest = true), 30, false).first().first)
        assertEquals(Care.Level.Ask, Care.advice(HealthCheck(day, heart = true), 30, false).first().first)
        assertTrue(Care.advice(HealthCheck(day), 50, false).any { it.second.contains("45") })
        assertTrue(Care.flagged(AppState(health = HealthCheck(day, dizzy = true))))
        assertFalse(Care.flagged(AppState(health = HealthCheck(day, joint = true))))
    }
}
