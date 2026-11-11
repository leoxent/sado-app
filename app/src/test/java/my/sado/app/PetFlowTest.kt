package my.sado.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import my.sado.app.data.AppState
import my.sado.app.data.FoodItem
import my.sado.app.data.Meal
import my.sado.app.data.Pets
import my.sado.app.data.Profile
import my.sado.app.data.Store
import my.sado.app.ui.EvolveScene
import my.sado.app.ui.HomeScreen
import my.sado.app.ui.LocalReduceMotion
import my.sado.app.ui.PetEvolveScreen
import my.sado.app.ui.SadoTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

// Plays the feeding and the evolution for real on a test clock, with the animations on.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1400dp-xxhdpi")
class PetFlowTest {
    @get:Rule val rule = createComposeRule()
    private val day = LocalDate.now().toEpochDay()
    private fun meal(name: String, minute: Int) = Meal(day, minute, listOf(FoodItem(name, "", 0, 0, 0f, source = "Anggaran")))

    @Before fun seed() {
        Store.init(ApplicationProvider.getApplicationContext())
        Store.update { Pets.choose(AppState(profile = Profile("Faiq", 0, 28, 172f, 70f, 1), planStart = day - 10), "harimau") }
        rule.mainClock.autoAdvance = false
    }

    private fun exp() = Store.state.value.pets.first().growth

    @Test fun feedingOneAtATimeThenTheNext() {
        Store.update { it.copy(meals = listOf(meal("Nasi lemak", 480), meal("Roti canai", 720))) }
        rule.setContent { SadoTheme { CompositionLocalProvider(LocalReduceMotion provides false) { HomeScreen({}, {}, {}, {}, {}, {}, {}) } } }
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithContentDescription("Suap Nasi lemak kat Si Belang, 2 menunggu").assertExists()
        rule.onAllNodesWithContentDescription("Suap Roti canai kat Si Belang").assertCountEquals(0)

        rule.onNodeWithContentDescription("Suap Nasi lemak kat Si Belang, 2 menunggu").performClick()
        rule.mainClock.advanceTimeBy(200)
        assertEquals(1, exp())
        // while it eats, no food waits: the next one must not jump the queue
        rule.onAllNodesWithContentDescription("Suap Roti canai kat Si Belang").assertCountEquals(0)

        rule.mainClock.advanceTimeBy(2300) // flight and four bites
        rule.onNodeWithText("+1 EXP").assertExists()
        rule.mainClock.advanceTimeBy(1500)
        rule.onAllNodesWithText("+1 EXP").assertCountEquals(0)
        rule.onNodeWithContentDescription("Suap Roti canai kat Si Belang").assertExists()

        rule.onNodeWithContentDescription("Suap Roti canai kat Si Belang").performClick()
        rule.mainClock.advanceTimeBy(5000)
        assertEquals(2, exp())
        rule.onAllNodesWithContentDescription("Suap Roti canai kat Si Belang").assertCountEquals(0)
    }

    @Test fun aWorkoutWaitsAsAnEnergyDrinkAndPaysTwo() {
        Store.update { it.copy(logs = listOf(my.sado.app.data.WorkoutLog(day, "Push day", 20, emptyList(), 100, false))) }
        assertEquals(0, exp())
        rule.setContent { SadoTheme { CompositionLocalProvider(LocalReduceMotion provides false) { HomeScreen({}, {}, {}, {}, {}, {}, {}) } } }
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithContentDescription("Bagi Minuman tenaga kat Si Belang, +2 EXP").performClick()
        rule.mainClock.advanceTimeBy(2500)
        rule.onNodeWithText("+2 EXP").assertExists()
        assertEquals(2, exp())
    }

    @Test @org.robolectric.annotation.Config(qualifiers = "w411dp-h2600dp-xxhdpi") fun aLoggedTaskTicksAtOnceAndTheGuideLinksHome() {
        var home = 0
        rule.setContent { SadoTheme { CompositionLocalProvider(LocalReduceMotion provides false) { PetEvolveScreen(onHome = { home++ }) {} } } }
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodesWithContentDescription("Dah cukup").assertCountEquals(0)
        rule.onAllNodesWithText("Bagi kat Si Belang sekarang").assertCountEquals(0)
        rule.onNodeWithText("7+ jam").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodesWithContentDescription("Dah cukup").assertCountEquals(1)
        assertEquals(0, exp()) // ticked, but the EXP lands only once it is given
        rule.onNodeWithText("Bagi kat Si Belang sekarang").performClick()
        assertEquals(1, home)
    }

    @Test fun sleepLoggedOnTheGuideWaitsAsAnEnergyDrink() {
        rule.setContent { SadoTheme { CompositionLocalProvider(LocalReduceMotion provides false) { PetEvolveScreen {} } } }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(0, Pets.foodQueue(Store.state.value).size)
        rule.onNodeWithText("7+ jam").performClick()
        rule.mainClock.advanceTimeBy(500)
        assertEquals(listOf(my.sado.app.data.PetFood.Source.Sleep), Pets.foodQueue(Store.state.value).map { it.source })
        rule.onNodeWithText("+2 EXP, sekali sehari. Minuman tenaga tengah tunggu kat Home.").assertExists()
        // changing the answer adds nothing
        rule.onNodeWithText("6–7 jam").performClick()
        rule.mainClock.advanceTimeBy(500)
        assertEquals(1, Pets.foodQueue(Store.state.value).size)
    }

    @Config(sdk = [34], qualifiers = "w411dp-h2600dp-xxhdpi") // the question sits low on the tab
    @Test fun sleepLoggedOnTheBodyTabWaitsAsAnEnergyDrink() {
        rule.setContent { SadoTheme { CompositionLocalProvider(LocalReduceMotion provides false) { my.sado.app.ui.BodyScreen({}, {}, {}) } } }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("7+ jam").performClick()
        rule.mainClock.advanceTimeBy(500)
        assertEquals(listOf(my.sado.app.data.PetFood.Source.Sleep), Pets.foodQueue(Store.state.value).map { it.source })
    }

    private fun owning(n: Int) = Store.update { s -> s.copy(pets = Pets.kinds.take(n).map { my.sado.app.data.Pet(it.id, stage = 0) }, activePet = Pets.kinds.first().id) }

    @Test fun theSecondAnimalIsFreeRightAfterTheFirst() {
        Store.update { it.copy(planStart = day) }
        owning(1)
        assertEquals(null, Pets.adoptLock(Store.state.value))
    }

    @Test fun theThirdAnimalOpensAfterTwoWeeks() {
        owning(2)
        Store.update { it.copy(planStart = day - 13) }
        assertEquals(Pets.Lock("Terbuka lepas 2 minggu aktif", "Lagi 1 hari."), Pets.adoptLock(Store.state.value))
        Store.update { it.copy(planStart = day - 14) }
        assertEquals(null, Pets.adoptLock(Store.state.value))
        // a passed placement test counts its weeks
        Store.update { it.copy(planStart = day, creditWeeks = 4) }
        assertEquals(null, Pets.adoptLock(Store.state.value))
    }

    private fun trainedFor(weeks: Int, xp: Int, peak: Int = xp, heavy: Boolean = false) = Store.update { s ->
        s.copy(
            xp = xp, peakXp = peak, levelFloor = 0, settings = s.settings.copy(bodyPath = if (heavy) "gempal" else "kurus"),
            logs = listOf(my.sado.app.data.WorkoutLog(day - weeks * 7L, "Push day", 20, emptyList(), 0, false)),
        )
    }

    @Test fun laterAnimalsGoByLevelOnEitherPath() {
        owning(3)
        trainedFor(4, 1200)
        assertEquals("Capai Berisi II", Pets.adoptLock(Store.state.value)?.goal)
        trainedFor(4, 1200, heavy = true)
        assertEquals("Capai Bangkit II", Pets.adoptLock(Store.state.value)?.goal)
        trainedFor(7, 2100, heavy = true)
        assertEquals(null, Pets.adoptLock(Store.state.value))
        owning(4)
        assertEquals("Capai Fit I", Pets.adoptLock(Store.state.value)?.goal)
        trainedFor(10, 3000)
        assertEquals(null, Pets.adoptLock(Store.state.value))
        owning(5)
        assertEquals("Capai Sado I", Pets.adoptLock(Store.state.value)?.goal)
        trainedFor(20, 6000)
        assertEquals(null, Pets.adoptLock(Store.state.value))
        owning(6)
        assertEquals(null, Pets.adoptLock(Store.state.value))
    }

    @Test fun aLevelLostToTimeOffLocksNothing() {
        owning(3)
        trainedFor(8, 300, peak = 2400)
        assertEquals(null, Pets.adoptLock(Store.state.value))
    }

    @Test fun aLockedAnimalExplainsItselfInsteadOfOfferingToAdopt() {
        owning(2)
        Store.update { it.copy(planStart = day - 3) }
        rule.setContent { SadoTheme { CompositionLocalProvider(LocalReduceMotion provides false) { my.sado.app.ui.PetScreen {} } } }
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodesWithContentDescription("Berkunci", useUnmergedTree = true).assertCountEquals(4)
        val third = Pets.kinds[2]
        rule.onNodeWithText(third.animal).performClick()
        repeat(5) { rule.mainClock.advanceTimeBy(100); rule.waitForIdle() }
        rule.onNodeWithText("${third.animal} masih berkunci").assertExists()
        rule.onNodeWithText("Terbuka lepas 2 minggu aktif. Lagi 11 hari.").assertExists()
        rule.onAllNodesWithText("Ya").assertCountEquals(0)
        assertEquals(2, Store.state.value.pets.size)
    }

    @Test fun theSimulationPutsTheRealStateBack() {
        Store.update { s -> s.copy(levelFloor = 0, pets = s.pets.map { it.copy(growth = 77) }) } // levelFloor: as any saved state has it
        val real = Store.state.value
        Store.startSimulation { Pets.simulateFirstWeek(it) }
        assertEquals(true, Store.simulating())
        assertEquals(null, Pets.active(Store.state.value))
        // a second start must not overwrite the real state with the simulated one
        Store.startSimulation { it }
        Store.update { Pets.choose(it, "buaya") }
        // the app restarts in the middle: the real state is still there
        Store.init(ApplicationProvider.getApplicationContext())
        assertEquals(true, Store.simulating())
        Store.endSimulation()
        assertEquals(false, Store.simulating())
        assertEquals(real, Store.state.value)
    }

    @Test fun devFoodPlaysTheFeedingButPaysNothing() = try {
        val food = my.sado.app.data.PetFood.Source.Food
        my.sado.app.ui.PetDev.food = listOf(my.sado.app.data.PetFood(0, "nasi_lemak", 1, food), my.sado.app.data.PetFood(0, "laksa", 1, food))
        rule.setContent { SadoTheme { CompositionLocalProvider(LocalReduceMotion provides false) { HomeScreen({}, {}, {}, {}, {}, {}, {}) } } }
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithContentDescription("Suap Nasi lemak kat Si Belang, 2 menunggu").performClick()
        rule.mainClock.advanceTimeBy(2500)
        rule.onNodeWithText("+1 EXP").assertExists()
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithContentDescription("Suap Laksa kat Si Belang").assertExists()
        assertEquals(0, exp())
        assertEquals(0, Pets.todayExp(Store.state.value).fed)
    } finally { my.sado.app.ui.PetDev.food = emptyList() }

    @Test fun theEvolutionSavesAtTheFlashAndOnlyClosesWhenDone() {
        var evolved = 0
        var closed = 0
        rule.setContent { SadoTheme { CompositionLocalProvider(LocalReduceMotion provides false) { EvolveScene("harimau", "Si Belang", 0, { evolved++ }, { closed++ }) } } }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("Eh? Si Belang macam lain macam…").assertExists()
        rule.onAllNodesWithText("Mantap!").assertCountEquals(0)
        rule.mainClock.advanceTimeBy(3000)
        rule.onNodeWithText("Si Belang tengah berubah!").assertExists()
        assertEquals(0, evolved) // still only flickering
        rule.mainClock.advanceTimeBy(6000)
        assertEquals(1, evolved)
        rule.onNodeWithText("Si Belang dah jadi Remaja!").assertExists()
        rule.onNodeWithText("Mantap!").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(1, closed)
    }

    @Test fun theButtonOnTheGuideRunsItAndSavesTheStage() {
        Store.update { s -> s.copy(pets = s.pets.map { it.copy(growth = 70) }) }
        rule.setContent { SadoTheme { CompositionLocalProvider(LocalReduceMotion provides false) { PetEvolveScreen {} } } }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(0, Pets.stage(Store.state.value.pets.first()))
        rule.onNodeWithText("Evolve sekarang").performClick()
        // a Dialog is its own window: it only starts once the main looper has had a turn
        repeat(120) {
            rule.mainClock.advanceTimeBy(100)
            rule.waitForIdle()
        }
        assertEquals(1, Pets.stage(Store.state.value.pets.first()))
        rule.onNodeWithText("Mantap!").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodesWithText("Mantap!").assertCountEquals(0)
        rule.onAllNodesWithText("Evolve sekarang").assertCountEquals(0)
    }
}
