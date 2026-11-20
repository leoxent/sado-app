package my.sado.app

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import my.sado.app.data.AppState
import my.sado.app.data.FocusItem
import my.sado.app.data.Plan
import my.sado.app.data.PlannedExercise
import my.sado.app.data.PlannerAnswers
import my.sado.app.data.Profile
import my.sado.app.data.SetLog
import my.sado.app.data.ExerciseLog
import my.sado.app.data.Store
import my.sado.app.data.WorkoutLog
import my.sado.app.ui.DoneScreen
import my.sado.app.ui.HomeScreen
import my.sado.app.ui.SadoTheme
import my.sado.app.ui.WelcomeScreen
import my.sado.app.ui.WorkoutListScreen
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

// Renders key screens with sample data to app/build/screens/*.png for visual review.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1400dp-xxhdpi")
class ScreensTest {
    @Before
    fun seed() {
        // screenshots show the settled state: turn system animations off so entrance/idle motion is skipped
        android.provider.Settings.Global.putFloat(ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        Store.init(ApplicationProvider.getApplicationContext())
        val today = LocalDate.now().toEpochDay()
        val ex = listOf(PlannedExercise("push", 3, "8–12", 12), PlannedExercise("squat", 3, "12–15", 15), PlannedExercise("invrow", 3, "8–12", 12), PlannedExercise("plank", 3, "30s", 30))
        Store.update {
            AppState(
                profile = Profile("Faiq", 0, 28, 172f, 70f, 1),
                answers = PlannerAnswers(pull = 0),
                plan = Plan("V-shape", "Bahu lebar", listOf(FocusItem("Belakang", 40)), listOf("Tarik", "Rehat", "K+T", "Rehat", "Tarik", "K+C", "Rehat"), "Tarik + badan atas", ex),
                planStart = today - 20,
                xp = 740,
                ladders = mapOf("push" to my.sado.app.data.LadderProgress(1)),
                boosts = mapOf("push" to 2, "plank" to 1),
                weights = listOf(72.4f, 72.0f, 71.6f, 71.8f, 71.1f, 70.6f, 70.2f).mapIndexed { i, kg -> my.sado.app.data.WeightLog(today - 42 + i * 7L, kg) },
                logs = (0 until 9).map { i ->
                    WorkoutLog(today - i * 2, "Tarik + badan atas", 24, listOf(ExerciseLog("push", listOf(SetLog(10), SetLog(9)))), 120, false)
                }.reversed(),
                sports = listOf(my.sado.app.data.SportLog(today - 1, "Futsal", 60, 1, 480, 40)),
                meals = listOf(my.sado.app.data.Meal(today, 720, listOf(my.sado.app.data.FoodItem("Nasi ayam", "1 pinggan", 550, 700, 30f, 80f, 18f, "Anggaran")))),
                settings = my.sado.app.data.Settings(warmup = false),
            )
        }
    }

    private fun shot(name: String, setup: () -> Unit = {}, content: @Composable () -> Unit): Unit {
        setup()
        captureRoboImage("build/screens/$name.png") { SadoTheme { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(my.sado.app.ui.SadoColors.Bg)) { content() } } }
    }

    @Test fun healthCheck() = shot("healthcheck") { my.sado.app.ui.HealthCheckScreen({}, {}) }
    @Test fun restPick() = shot("restpick") { my.sado.app.ui.RestScreen {} }
    @Test fun restOn() = shot("reston", { Store.update { my.sado.app.data.Care.start(it, my.sado.app.data.Care.RAMADAN, LocalDate.now().toEpochDay()) } }) { my.sado.app.ui.RestScreen {} }
    @Test fun restBirth() = shot("restbirth", { Store.update { my.sado.app.data.Care.start(it, my.sado.app.data.Care.BIRTH, LocalDate.now().toEpochDay() - 35) } }) { my.sado.app.ui.RestScreen {} }
    @Test fun ageGate() = shot("agegate", { Store.update { it.copy(profile = it.profile?.copy(age = 15)) } }) { my.sado.app.ui.AgeGateScreen({}, {}, {}) }
    @Test fun homeRest() = shot("homerest", { Store.update { my.sado.app.data.Care.start(it, my.sado.app.data.Care.SICK, LocalDate.now().toEpochDay() - 2) } }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun ramadanOffer() = shot("ramadanoffer") { my.sado.app.ui.RamadanOfferCard() }
    @Test fun home() = shot("home") { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    // a 20-letter nickname: one line, the type shrinks to fit
    @Test fun homeLongName() = shot("homelongname", { Store.update { it.copy(profile = it.profile?.copy(name = "Muhammad Faiq Kadirr")) } }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    // 0.49 session bits: the 15 s get-ready, a timed set, a max-rep target and its dialog, the glowing button
    @Test fun warmupReady() = shot("warmupready") { my.sado.app.ui.Warmup({}, {}) }
    @Test fun timedSet() = shot("timedset") { my.sado.app.ui.TimedSet(30) {} }
    @Test fun maxRepTarget() = shot("maxreptarget") { my.sado.app.ui.TargetCard("maximum rep", null, maxNote = my.sado.app.ui.maxNote(4, null)) }
    @Test fun glowButton() = shot("glowbutton") { my.sado.app.ui.PrimaryButton("Jom", glow = true) {} }
    @Test fun welcome() = shot("welcome") { WelcomeScreen(onStart = {}) }
    @Test fun done() = shot("done") { DoneScreen(onHome = {}) }
    @Test fun doneZero() = shot("donezero", { Store.update { it.copy(logs = it.logs + WorkoutLog(LocalDate.now().toEpochDay(), "Push day", 3, listOf(ExerciseLog("push", emptyList(), true)), 0, false)) } }) {
        DoneScreen(onHome = {})
    }
    @Test fun doneSummary() = shot("donesummary", {
        Store.update {
            val today = LocalDate.now().toEpochDay()
            it.copy(logs = it.logs + WorkoutLog(today - 3, "Push day", 20, listOf(ExerciseLog("push", listOf(SetLog(12), SetLog(10)))), 30, false) +
                WorkoutLog(
                    today, "Push day", 22,
                    listOf(
                        ExerciseLog("push", listOf(SetLog(14), SetLog(12), SetLog(10))),
                        ExerciseLog("plank", listOf(SetLog(40), SetLog(40))),
                        ExerciseLog("lateral", listOf(SetLog(12, flagged = true), SetLog(10))),
                        ExerciseLog("squat", emptyList(), true),
                    ),
                    40, false, climbed = mapOf("push" to "decline"),
                ))
        }
    }) { DoneScreen(onHome = {}) }
    private fun upperLowerPlan(week: List<String>) = my.sado.app.data.Plan(
        "t", "w", emptyList(), week, "Upper", emptyList(),
        listOf(
            my.sado.app.data.PlanSession("Upper", "Upper body", listOf(my.sado.app.data.PlannedExercise("push", 3, "10", 10), my.sado.app.data.PlannedExercise("pullup", 3, "6", 6))),
            my.sado.app.data.PlanSession("Lower", "Lower body", listOf(my.sado.app.data.PlannedExercise("squat", 3, "15", 15), my.sado.app.data.PlannedExercise("calf", 3, "20", 20))),
        ),
    )
    @Test fun reviewCard() = shot("review") {
        val today = LocalDate.now()
        val earlier = listOf(WorkoutLog(today.toEpochDay() - 7, "Upper", 22, listOf(ExerciseLog("push", listOf(SetLog(10), SetLog(10))), ExerciseLog("pullup", listOf(SetLog(7), SetLog(6)))), 30, false))
        val log = WorkoutLog(
            today.toEpochDay(), "Upper body", 24,
            listOf(
                ExerciseLog("push", listOf(SetLog(13), SetLog(11), SetLog(10))),
                ExerciseLog("pullup", listOf(SetLog(6), SetLog(5))),
                ExerciseLog("pike", emptyList(), true),
                ExerciseLog("lateral", listOf(SetLog(12, flagged = true), SetLog(12))),
            ),
            40, false, seconds = 1_480, climbed = mapOf("push" to "decline"),
        )
        val hr = my.sado.app.data.HrAnalysis.Report(
            134, 171, 300, true,
            mapOf(my.sado.app.data.HrAnalysis.State.Workout to 1000, my.sado.app.data.HrAnalysis.State.Active to 400),
            listOf(my.sado.app.data.HrAnalysis.SetHr("push", 1, 160, 27), my.sado.app.data.HrAnalysis.SetHr("push", 2, 166, 22)), true,
        )
        val plan = upperLowerPlan(List(7) { i -> if (i == today.plusDays(1).dayOfWeek.value - 1) "Lower" else "Upper" })
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(16.dp)) {
            my.sado.app.ui.ReviewLines(my.sado.app.data.SessionReview.sentences(log, earlier, plan, hr))
        }
    }
    @Test fun extraPicker() = shot("extrapicker", {
        Store.update {
            val today = LocalDate.now()
            val week = List(7) { i -> when (i) { today.dayOfWeek.value - 1 -> "Rest"; today.plusDays(1).dayOfWeek.value - 1 -> "Upper"; else -> "Lower" } }
            it.copy(
                plan = upperLowerPlan(week),
                logs = it.logs + WorkoutLog(today.toEpochDay() - 1, "Lower body", 20, listOf(ExerciseLog("squat", listOf(SetLog(15), SetLog(15)))), 30, false),
            )
        }
    }) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(16.dp)) { my.sado.app.ui.ExtraSessionPicker(Store.state.value, onPick = {}) } }
    private fun historySetup() = Store.update {
        val today = LocalDate.now().toEpochDay()
        val start = System.currentTimeMillis() - 3_600_000L
        val hr = (0 until 240).map { my.sado.app.data.HrPoint(start + it * 5_000L, 95 + (it % 24) * 3) }
        val last = my.sado.app.data.HrStore.put(
            WorkoutLog(
                today, "Pull day", 25,
                listOf(ExerciseLog("pullup", listOf(SetLog(8), SetLog(7), SetLog(6))), ExerciseLog("lateral", listOf(SetLog(12), SetLog(12)))),
                45, false, kcal = 180, kcalSource = "HR jam", seconds = 1_520, startMs = start, endMs = start + 1_200_000, hrSource = "Broadcast", restingHr = 62,
            ),
            hr,
        )
        it.copy(logs = it.logs + WorkoutLog(today - 40, "Push day", 20, listOf(ExerciseLog("push", listOf(SetLog(12)))), 30, false) +
            WorkoutLog(today - 2, "Legs + Core", 18, listOf(ExerciseLog("squat", listOf(SetLog(20), SetLog(18)))), 35, true) + last)
    }
    @Test fun history() = shot("history", ::historySetup) { my.sado.app.ui.HistoryScreen({}, {}) }
    @Test fun sessionLog() = shot("sessionlog", ::historySetup) { my.sado.app.ui.SessionLogScreen(Store.state.value.logs.lastIndex) {} }
    private fun garmin() = Store.update { it.copy(settings = it.settings.copy(watch = "garmin", watchModel = "Forerunner 165", watchConfirmed = false)) }
    @Test fun watchStart() = shot("watchstart", ::garmin) {
        my.sado.app.ui.WatchStartCard(my.sado.app.data.Watch.brand("garmin")!!, {}, {}, {}, still = true)
    }
    @Test fun watchStartSamsung() = shot("watchstartsamsung") {
        my.sado.app.ui.WatchStartCard(my.sado.app.data.Watch.brand("samsung")!!, {}, {}, {}, still = true)
    }
    @Test fun watchStop() = shot("watchstop", ::garmin) {
        my.sado.app.ui.WatchStopCard(my.sado.app.data.Watch.brand("garmin")!!, {}, {}, still = true)
    }
    @Test fun myWatch() = shot("mywatch", ::garmin) { my.sado.app.ui.MyWatchCard(still = true) }
    @Test fun squatPic() = shot("squatpic") { my.sado.app.ui.ExerciseDetailScreen("squat") {} }
    @Test fun session() = shot("session") { my.sado.app.ui.SessionScreen("plan", {}, {}, {}) }
    @Test fun sessionLong() = shot("sessionlong", { my.sado.app.data.Store.update { s -> s.copy(plan = s.plan?.mapExercises { ex -> listOf(ex.first().copy(id = "kneeraise")) + ex.drop(1) }) } }) {
        my.sado.app.ui.SessionScreen("plan", {}, {}, {})
    }
    @Test fun report() = shot("report") {
        my.sado.app.VoiceClips.missing.value = listOf("[pumped] Set dua! Jom!")
        my.sado.app.ui.VoiceReportChip()
    }
    @Test fun body() = shot("body") { my.sado.app.ui.BodyScreen({}, {}, {}) }
    @Test fun rekod() = shot("rekod") { my.sado.app.ui.CalendarScreen({}, {}) }
    @Test fun journey() = shot("journey") { my.sado.app.ui.JourneyScreen {} }
    @Test fun prove() = shot("prove") { my.sado.app.ui.ProveScreen(onDone = {}) }
    @Test fun provePush() = shot("prove-push") { my.sado.app.ui.ProveScreen(onDone = {}, startStep = 1) }
    private fun withPet(growth: Int, kind: String = "harimau") = Store.update { s ->
        my.sado.app.data.Pets.choose(s.copy(planStart = LocalDate.now().toEpochDay() - 10), kind).let { st -> st.copy(pets = st.pets.map { it.copy(growth = growth, stage = my.sado.app.data.Pets.reached(growth)) }, sleep = listOf(my.sado.app.data.SleepLog(LocalDate.now().toEpochDay(), 7.5f, "Jawapan")), petDays = st.petDays + (LocalDate.now().toEpochDay() to my.sado.app.data.PetDay(fed = 1, slept = true, trained = true))) }
    }
    // every animal at one stage next to the character, same floor and sizes as the Home hero
    private fun bg(id: String?) = Store.update { it.copy(settings = it.settings.copy(background = id)) }
    @Test fun bgKampung() = shot("bg-kampung", { bg("kampung"); withPet(240, "harimau") }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun bgPadangLight() = shot("bg-padang-light", { light(); bg("padang"); withPet(240, "serigala") }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun bgRooftopLegend() = shot("bg-rooftop-legend", { bg("rooftop"); withPet(540, "seladang") }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun bgSawahLight() = shot("bg-sawah-light", { light(); bg("sawah"); withPet(70, "beruang") }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun bgBeast() = try { shot("bg-beast", { bg("sawah"); withPet(240, "buaya") }) {
        my.sado.app.ui.CharDev.override = my.sado.app.ui.CharDev.Override(my.sado.app.data.Progress.stages[14], false)
        HomeScreen({}, {}, {}, {}, {}, {}, {})
    } } finally { my.sado.app.ui.CharDev.override = null }
    @Test fun bgPicker() = shot("bg-picker", { bg("sawah") }) {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) { my.sado.app.ui.BackgroundCard("sawah") }
    }
    // a pet walked behind the character: its feet are above his, and he covers it
    @Test fun petBehind() = shot("pet-behind", { light() }) {
        val s = Store.state.value
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(360.dp, 260.dp)) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(my.sado.app.data.Progress.stageFor(s).image), null,
                androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 14.dp).height(200.dp),
            )
            my.sado.app.ui.PetBuddy(
                s, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 10.dp), startX = 30f, startBack = true,
                show = my.sado.app.data.Pet("harimau", growth = 70) to my.sado.app.data.Pets.Mood.Biasa,
            )
        }
    }
    // the crocodile at each stage: its body must sit on its shadow
    @Test fun crocShadow() = shot("croc-shadow", { light() }) {
        val s = Store.state.value
        androidx.compose.foundation.layout.Column {
            my.sado.app.data.Pets.stageAt.forEach { g ->
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(400.dp, if (g == 540) 260.dp else 170.dp)) {
                    my.sado.app.ui.PetBuddy(
                        s, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 10.dp), startX = 0f,
                        show = my.sado.app.data.Pet("buaya", growth = g) to my.sado.app.data.Pets.Mood.Biasa,
                    )
                }
            }
        }
    }
    @Test fun lightPet() = shot("light-pet", { light(); withPet(240, "beruang") }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun lightLegend() = shot("light-legend", { light(); withPet(540, "seladang") }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun lightSizes() = sizes(2, "light-size-2") { light() }
    private fun sizes(stage: Int, name: String = "pet-size-$stage", setup: () -> Unit = {}) = shot(name, setup) {
        val s = Store.state.value
        val img = my.sado.app.data.Progress.stageFor(s).image
        androidx.compose.foundation.layout.Column {
            my.sado.app.data.Pets.kinds.chunked(2).forEach { pair ->
                androidx.compose.foundation.layout.Row {
                    pair.forEach { k ->
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(205.dp, 170.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            androidx.compose.foundation.layout.Box(
                                androidx.compose.ui.Modifier.requiredSize(330.dp, 270.dp).graphicsLayer { scaleX = 0.6f; scaleY = 0.6f },
                            ) {
                                val pic = androidx.compose.ui.res.painterResource(img)
                                my.sado.app.ui.CastShadow(pic, 200.dp * (pic.intrinsicSize.width / pic.intrinsicSize.height), 200.dp, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 14.dp))
                                androidx.compose.foundation.Image(
                                    pic, null,
                                    androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 14.dp).height(200.dp),
                                )
                                my.sado.app.ui.PetBuddy(
                                    s, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 10.dp),
                                    show = my.sado.app.data.Pet(k.id, growth = my.sado.app.data.Pets.stageAt[stage]) to my.sado.app.data.Pets.Mood.Biasa,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    @Test fun petSize0() = sizes(0)
    @Test fun petSize1() = sizes(1)
    @Test fun petSize2() = sizes(2)
    @Test fun petSize3() = sizes(3)
    @Test fun homeLegend() = shot("home-legend", { withPet(540, "seladang") }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun devChar() = try { shot("devchar") {
        my.sado.app.ui.CharDev.override = my.sado.app.ui.CharDev.Override(my.sado.app.data.Progress.stages[9], true)
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) { my.sado.app.ui.DevCard {} }
    } } finally { my.sado.app.ui.CharDev.override = null }
    @Test fun homeChar() = try { shot("home-char", { withPet(540, "buaya") }) {
        my.sado.app.ui.CharDev.override = my.sado.app.ui.CharDev.Override(my.sado.app.data.Progress.stages[14], false)
        HomeScreen({}, {}, {}, {}, {}, {}, {})
    } } finally { my.sado.app.ui.CharDev.override = null }
    @Test fun homeTiredKurus() = try { shot("home-tired-kurus") {
        my.sado.app.ui.CharDev.override = my.sado.app.ui.CharDev.Override(my.sado.app.data.Progress.stages[0], true)
        HomeScreen({}, {}, {}, {}, {}, {}, {})
    } } finally { my.sado.app.ui.CharDev.override = null }
    @Test fun homeTiredGempal() = try { shot("home-tired-gempal") {
        my.sado.app.ui.CharDev.override = my.sado.app.ui.CharDev.Override(my.sado.app.data.Progress.heavyStages[0], true)
        HomeScreen({}, {}, {}, {}, {}, {}, {})
    } } finally { my.sado.app.ui.CharDev.override = null }
    @Test fun blinkFrames() = shot("blink") {
        androidx.compose.foundation.layout.Column {
            for (amount in listOf(false, true)) androidx.compose.foundation.layout.Row {
                for (pic in listOf(my.sado.app.R.drawable.gempal2, my.sado.app.R.drawable.bangkit3)) androidx.compose.foundation.layout.Box {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(pic), null, androidx.compose.ui.Modifier.height(200.dp))
                    my.sado.app.ui.BlinkLids(pic, androidx.compose.ui.Modifier.matchParentSize(), hold = amount)
                }
            }
        }
    }
    @Test fun petBlinkFrames() = shot("blink-pet") {
        androidx.compose.foundation.layout.Column {
            for (kind in my.sado.app.data.Pets.kinds) androidx.compose.foundation.layout.Row {
                for (pic in kind.images) androidx.compose.foundation.layout.Box {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(pic), null, androidx.compose.ui.Modifier.height(90.dp))
                    my.sado.app.ui.BlinkLids(pic, androidx.compose.ui.Modifier.matchParentSize(), hold = true)
                }
            }
        }
    }
    @Test fun devPet() = try { shot("devpet") {
        my.sado.app.ui.PetDev.override = my.sado.app.ui.PetDev.Override("buaya", 2, my.sado.app.data.Pets.Mood.Lapar)
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) { my.sado.app.ui.DevCard {} }
    } } finally { my.sado.app.ui.PetDev.override = null }
    // the standing picture, the open mouth and the howl must keep the same feet
    @Test fun petCallAll() = shot("pet-call") {
        androidx.compose.foundation.layout.Column {
            for (kind in my.sado.app.data.Pets.kinds) androidx.compose.foundation.layout.Row {
                for (stage in 0..3) {
                    val pics = listOf(kind.images[stage], kind.openImages[stage]) + listOfNotNull(my.sado.app.data.Pets.howlImage(kind.id, stage))
                    for (pic in pics) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(pic), null, androidx.compose.ui.Modifier.height(50.dp))
                }
            }
        }
    }
    @Test fun homeCallLegend() = try { shot("home-call-legend", {
        withPet(540, "buaya")
        my.sado.app.ui.PetDev.holdCall = 1
    }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) } } finally { my.sado.app.ui.PetDev.holdCall = 0 }
    @Test fun homeCallCub() = try { shot("home-call-cub", {
        withPet(1, "harimau")
        my.sado.app.ui.PetDev.holdCall = 1
    }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) } } finally { my.sado.app.ui.PetDev.holdCall = 0 }
    @Test fun petScreenHowl() = try { shot("pet-howl", {
        withPet(540, "serigala")
        my.sado.app.ui.PetDev.holdCall = 2
    }) { my.sado.app.ui.PetScreen {} } } finally { my.sado.app.ui.PetDev.holdCall = 0 }
    @Test fun petScreenHowlOff() = shot("pet-howl-off", { withPet(540, "serigala") }) { my.sado.app.ui.PetScreen {} }
    private fun flapShot(name: String, growth: Int, wings: Int) = try { shot(name, {
        withPet(growth, "helang")
        my.sado.app.ui.PetDev.holdFlap = wings
    }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) } } finally { my.sado.app.ui.PetDev.holdFlap = 0 }
    @Test fun homeFlapLegendUp() = flapShot("home-flap-legend-up", 540, 1)
    @Test fun homeFlapLegendDown() = flapShot("home-flap-legend-down", 540, 2)
    @Test fun homeFlapAdultUp() = flapShot("home-flap-adult-up", 240, 1)
    @Test fun homeFlapCubDown() = flapShot("home-flap-cub-down", 1, 2)
    @Test fun petFlapAll() = shot("pet-flap") {
        androidx.compose.foundation.layout.Column {
            for (stage in 0..3) androidx.compose.foundation.layout.Row {
                val (up, down) = my.sado.app.data.Pets.flapImages("helang", stage)!!
                val kind = my.sado.app.data.Pets.kind("helang")
                for (pic in listOf(kind.images[stage], up, down)) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(pic), null, androidx.compose.ui.Modifier.height(150.dp))
            }
        }
    }
    @Test fun petSleepAll() = shot("sleep-pet") {
        androidx.compose.foundation.layout.Column {
            for (kind in my.sado.app.data.Pets.kinds) androidx.compose.foundation.layout.Row {
                for (pic in kind.sleepImages) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(pic), null, androidx.compose.ui.Modifier.height(50.dp))
            }
        }
    }
    @Test fun homeSleepLegend() = try { shot("home-sleep-legend", {
        withPet(540, "buaya")
        my.sado.app.ui.PetDev.override = my.sado.app.ui.PetDev.Override("buaya", 3, my.sado.app.data.Pets.Mood.Tidur)
    }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) } } finally { my.sado.app.ui.PetDev.override = null }
    @Test fun homeSleepCub() = try { shot("home-sleep-cub", {
        withPet(1, "harimau")
        my.sado.app.ui.PetDev.override = my.sado.app.ui.PetDev.Override("harimau", 0, my.sado.app.data.Pets.Mood.Tidur)
    }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) } } finally { my.sado.app.ui.PetDev.override = null }
    @Test fun petScreenSleepLegend() = try { shot("pet-sleep-legend", {
        withPet(540, "seladang")
        my.sado.app.ui.PetDev.override = my.sado.app.ui.PetDev.Override("seladang", 3, my.sado.app.data.Pets.Mood.Tidur)
    }) { my.sado.app.ui.PetScreen {} } } finally { my.sado.app.ui.PetDev.override = null }
    // one thing waiting: the red dot alone, here an energy drink for a workout
    @Test fun homeDrink() = shot("home-drink", {
        val day = LocalDate.now().toEpochDay()
        withPet(10, "harimau")
        Store.update { it.copy(petDays = it.petDays + (day to my.sado.app.data.PetDay(fed = 1, slept = true))) }
    }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    // what the dev simulation of the first week leaves on Home
    @Test fun homeGift() = shot("home-gift", {
        withPet(10, "harimau")
        Store.update { my.sado.app.data.Pets.simulateFirstWeek(it) }
    }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun devPetReal() = shot("devpet-real", { withPet(10, "harimau") }) {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) { my.sado.app.ui.PetTester() }
    }
    // food waiting at the hero's corner with the queue's number, and an evolution waiting on the pet card
    @Test fun homeFood() = shot("home-food", {
        val day = LocalDate.now().toEpochDay()
        withPet(29, "harimau")
        Store.update { it.copy(petDays = it.petDays - day, meals = listOf(my.sado.app.data.Meal(day, 480, listOf(my.sado.app.data.FoodItem("Nasi lemak", "1 bungkus", 400, 500, 12f, source = "Anggaran"))))) }
    }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun homeFoodLegend() = shot("home-food-legend", {
        val day = LocalDate.now().toEpochDay()
        withPet(540, "seladang")
        Store.update { it.copy(petDays = it.petDays - day) }
    }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    private fun evolveReady(growth: Int, kind: String, stage: Int) = Store.update { s ->
        my.sado.app.data.Pets.choose(s.copy(planStart = LocalDate.now().toEpochDay() - 10), kind).let { st -> st.copy(pets = st.pets.map { it.copy(growth = growth, stage = stage) }) }
    }
    @Test fun homeEvolveReady() = shot("home-evolve-ready", { evolveReady(72, "serigala", 0) }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun evolveGuide() = shot("evolve-guide", { withPet(20, "harimau") }) { my.sado.app.ui.PetEvolveScreen {} }
    @Test fun evolveGuideLogged() = shot("evolve-guide-logged", { withPet(20, "harimau"); Store.update { it.copy(petDays = emptyMap(), sleep = listOf(my.sado.app.data.SleepLog(LocalDate.now().toEpochDay(), 7f, "Jawapan"))) } }) { my.sado.app.ui.PetEvolveScreen {} }
    @Test fun evolveGuideReady() = shot("evolve-guide-ready", { evolveReady(72, "serigala", 0) }) { my.sado.app.ui.PetEvolveScreen {} }
    @Test fun evolveGuideLegend() = shot("evolve-guide-legend", { withPet(560, "beruang") }) { my.sado.app.ui.PetEvolveScreen {} }
    private fun evolveShot(name: String, kind: String, from: Int, hold: my.sado.app.ui.EvolveHold) = shot(name) {
        my.sado.app.ui.EvolveScene(kind, my.sado.app.data.Pets.kind(kind).defaultName, from, {}, {}, hold)
    }
    @Test fun evolveStart() = evolveShot("evolve-1-start", "harimau", 0, my.sado.app.ui.EvolveHold.Start)
    @Test fun evolveShadowOld() = evolveShot("evolve-2-shadow-old", "harimau", 0, my.sado.app.ui.EvolveHold.ShadowOld)
    @Test fun evolveShadowNew() = evolveShot("evolve-3-shadow-new", "harimau", 0, my.sado.app.ui.EvolveHold.ShadowNew)
    @Test fun evolveDone() = evolveShot("evolve-4-done", "harimau", 0, my.sado.app.ui.EvolveHold.Done)
    @Test fun evolveCrocLegend() = evolveShot("evolve-croc-legend", "buaya", 2, my.sado.app.ui.EvolveHold.ShadowOld)
    @Test fun homePet() = shot("home-pet", { withPet(240, "serigala") }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun petScreen() = shot("pet", { withPet(50) }) { my.sado.app.ui.PetScreen {} }
    @Test fun petLegend() = shot("pet-legend", { withPet(540, "beruang") }) { my.sado.app.ui.PetScreen {} }
    @Test fun petGift() = shot("pet-gift", { Store.update { it.copy(planStart = LocalDate.now().toEpochDay() - 8) } }) {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) { my.sado.app.ui.PetCard(Store.state.value) {} }
    }
    @Test fun petLocks() = shot("pet-locks", {
        val day = LocalDate.now().toEpochDay()
        Store.update { s -> s.copy(planStart = day - 20, activePet = "harimau", pets = my.sado.app.data.Pets.kinds.take(3).map { my.sado.app.data.Pet(it.id, growth = 40, stage = 1) }) }
    }) { my.sado.app.ui.PetScreen {} }
    @Test fun petPick() = shot("pet-pick", { Store.update { it.copy(planStart = LocalDate.now().toEpochDay() - 8) } }) { my.sado.app.ui.PetScreen {} }
    @Test fun fitTest() = shot("fittest") { my.sado.app.ui.FitnessTestScreen({}, {}) }
    @Test fun rekodTests() = shot("rekod-tests", {
        val d = LocalDate.now().toEpochDay()
        Store.update { it.copy(tests = listOf(my.sado.app.data.FitnessTest(d - 56, 12, 45), my.sado.app.data.FitnessTest(d - 28, 18, 60), my.sado.app.data.FitnessTest(d, 24, 80))) }
    }) { my.sado.app.ui.CalendarScreen({}, {}) }
    @Test fun journeyGempal() = shot("journey-gempal", setup = { my.sado.app.data.Store.update { it.copy(settings = it.settings.copy(bodyPath = "gempal")) } }) { my.sado.app.ui.JourneyScreen {} }
    @Test fun sport() = shot("sport") { my.sado.app.ui.SportScreen({}, {}) }
    @Test fun settings() = shot("settings") { my.sado.app.ui.SettingsScreen({}, {}, {}, {}, {}, {}) }
    @Test fun food() = shot("food") { my.sado.app.ui.NutritionScreen({}, {}, {}) }
    // the reported case: 168 cm, 55 kg, V-shape + Shredded
    private fun lightFrame() = Store.update { it.copy(profile = Profile("Faiq", 0, 28, 168f, 55f, 1), answers = PlannerAnswers(shape = listOf("vshape", "shredded"), level = 1, days = 4, time = 30)) }
    @Test fun bodyProfileEdit() = shot("bodyprofileedit", ::lightFrame) { val s = Store.state.value; my.sado.app.ui.BodyProfileEditor(s.profile!!, s) {} }
    @Test fun foodTargets() = shot("foodtargets", ::lightFrame) { my.sado.app.ui.NutritionScreen({}, {}, {}) }
    // food names find their cartoon: the dish first, then the main ingredient, else the plate
    @Test fun foodIconMatch() {
        val m = my.sado.app.data.FoodIcons::match
        org.junit.Assert.assertEquals("nasi_lemak", m("Nasi lemak ayam goreng"))
        org.junit.Assert.assertEquals("ayam_masak_merah", m("Ayam masak kurma"))
        org.junit.Assert.assertEquals("ayam_goreng", m("Ayam"))
        org.junit.Assert.assertEquals("peha_ayam", m("Chicken thigh"))
        org.junit.Assert.assertEquals("milo", m("Air milo ais"))
        org.junit.Assert.assertEquals("air", m("Air"))
        org.junit.Assert.assertEquals("teh_tarik", m("Teh tarik kurang manis"))
        org.junit.Assert.assertEquals("roti_canai", m("Roti telur"))
        org.junit.Assert.assertEquals("telur_rebus", m("Telur"))
        org.junit.Assert.assertEquals("shake", m("Whey"))
        org.junit.Assert.assertEquals("pinggan", m("Xyz"))
        // from a real day log
        org.junit.Assert.assertEquals("sotong_sambal", m("Sotong sambal"))
        org.junit.Assert.assertEquals("rendang", m("Paru goreng sambal"))
        org.junit.Assert.assertEquals("char_kuey_teow", m("Kuey teow goreng"))
        org.junit.Assert.assertEquals("teh_tarik", m("Teh ais"))
        org.junit.Assert.assertEquals("teh_o_limau", m("Teh o ais limau"))
        org.junit.Assert.assertEquals("kopi_ais", m("Kopi ais"))
        org.junit.Assert.assertEquals("mi_segera", m("Maggi cup"))
        org.junit.Assert.assertEquals("isotonik", m("100plus"))
        org.junit.Assert.assertEquals("nasi_briyani", m("Nasi briyani kambing"))
        org.junit.Assert.assertEquals("kurma", m("Kurma"))
        org.junit.Assert.assertEquals("ayam_masak_merah", m("Ayam kurma"))
        org.junit.Assert.assertEquals(176, my.sado.app.data.FoodIcons.all.size)
        // 0.26 icons
        org.junit.Assert.assertEquals("sambal", m("Sambal"))
        org.junit.Assert.assertEquals("kubis_goreng", m("Kubis goreng"))
        org.junit.Assert.assertEquals("sup_lembu", m("Sup lembu"))
        org.junit.Assert.assertEquals("maggi_kari", m("Maggie kari"))
        org.junit.Assert.assertEquals("mi_segera", m("Maggi cup"))
        org.junit.Assert.assertEquals("sotong_goreng", m("Sotong goreng tepung"))
        org.junit.Assert.assertEquals("udang", m("Udang goreng"))
        org.junit.Assert.assertEquals("kangkung", m("Kangkung belacan"))
        org.junit.Assert.assertEquals("kari_ayam", m("Kari ayam"))
        org.junit.Assert.assertEquals("biskut", m("Cream crackers"))
        org.junit.Assert.assertEquals("keropok_lekor", m("Keropok lekor"))
        org.junit.Assert.assertEquals("tomyam", m("Tom yam campur"))
        org.junit.Assert.assertEquals("roti_tisu", m("Roti tisu"))
        org.junit.Assert.assertEquals("sosej", m("Sosej ayam"))
        org.junit.Assert.assertEquals("kopi", my.sado.app.data.FoodIcons.of(my.sado.app.data.FoodItem("Ayam", "1", 1, 1, 1f, source = "AI", icon = "kopi")))
    }
    private fun mealDay() = Store.update {
        val today = java.time.LocalDate.now().toEpochDay()
        fun item(n: String, p: String, k: Int, pr: Float) = my.sado.app.data.FoodItem(n, p, k, k + 60, pr, 20f, 8f, "AI")
        it.copy(meals = listOf(
            my.sado.app.data.Meal(today, 480, listOf(item("Nasi lemak", "1 bungkus", 400, 12f), item("Teh tarik", "1 gelas", 150, 3f))),
            my.sado.app.data.Meal(today, 780, listOf(item("Ayam masak kurma", "1 ketul", 250, 22f), item("Nasi putih", "1 pinggan", 260, 5f), item("Sayur kangkung", "1 senduk", 60, 2f))),
            my.sado.app.data.Meal(today, 1020, listOf(item("Whey", "1 scoop", 120, 24f), item("Pisang", "1 biji", 100, 1f))),
        ))
    }
    @Config(sdk = [34], qualifiers = "w411dp-h3200dp-xxhdpi")
    @Test fun foodLog() = shot("foodlog", ::mealDay) { my.sado.app.ui.NutritionScreen({}, {}, {}) }
    // each photo holds, then fades into the next; the last one stays
    @Test fun timelapsePlan() {
        val t = my.sado.app.ui.Timelapse
        org.junit.Assert.assertEquals(3 * t.HOLD + 2 * t.FADE + t.END, t.frameCount(3))
        org.junit.Assert.assertEquals(0 to 0f, t.at(0, 3))
        org.junit.Assert.assertEquals(0, t.at(t.HOLD, 3).first)
        org.junit.Assert.assertTrue(t.at(t.HOLD, 3).second > 0f)
        org.junit.Assert.assertEquals(1 to 0f, t.at(t.HOLD + t.FADE, 3))
        org.junit.Assert.assertEquals(2 to 0f, t.at(t.frameCount(3) - 1, 3))
    }
    @Test fun timelapseSheet() = shot("timelapse") {
        val ph = (0..5).map { my.sado.app.data.ProgressPhoto(20000L + it * 7, 0, "x$it.jpg", 1.3f + it * 0.01f) }
        my.sado.app.ui.TimelapseBody(0, ph, 20000L) {}
    }
    @Test fun snapTip() = shot("snaptip") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(411.dp).height(700.dp).background(androidx.compose.ui.graphics.Color.DarkGray)) {
            my.sado.app.ui.FramingGuide(androidx.compose.ui.Modifier.fillMaxSize())
            my.sado.app.ui.SnapTip(0, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(12.dp))
        }
    }
    // the editor: roti canai 1 → 2 keping doubles the numbers, weights in brackets follow
    @Test fun portionQty() {
        val P = my.sado.app.data.Portion
        org.junit.Assert.assertEquals("2 keping", P.times("1 keping", 2f))
        org.junit.Assert.assertEquals("2 senduk (~150 g)", P.times("1 senduk (~75 g)", 2f))
        org.junit.Assert.assertEquals("1.5 sudu (~27 g)", P.times("1 sudu (~18 g)", 1.5f))
        org.junit.Assert.assertEquals("×2 · Satu hidangan", P.times("Satu hidangan", 2f))
        val old = P.parse("×2 · 1 keping")
        org.junit.Assert.assertEquals(2f, old.qty); org.junit.Assert.assertEquals("keping", old.unit)
        org.junit.Assert.assertEquals("3 keping", P.format(old.copy(qty = 3f)))
    }
    @Test fun foodEdit() = shot("foodedit") {
        my.sado.app.ui.FoodEditBody(my.sado.app.data.FoodItem("Roti canai", "1 keping (~100 g)", 300, 360, 7f, 40f, 12f, "AI · Sederhana", "roti_canai"), {}, {}) {}
    }
    @Test fun measureGuide() = shot("measureguide") { my.sado.app.ui.MeasureGuide(startOpen = true) }
    @Test fun foodIconPicker() = shot("foodiconpicker") { my.sado.app.ui.FoodIconPickerBody("ayam_goreng", {}) {} }
    // two weeks of flat weight on a lean bulk: the first check waits, the second adds 150 kcal
    private fun flatTwoWeeks(): my.sado.app.data.AppState {
        val w = my.sado.app.data.WeeklyCheck
        val d = LocalDate.now().toEpochDay()
        var s = my.sado.app.data.AppState(
            profile = Profile("Faiq", 0, 28, 168f, 55f, 1), answers = PlannerAnswers(shape = listOf("vshape", "shredded")),
            weights = (0..20).map { my.sado.app.data.WeightLog(d - 20 + it, 55f) }, planStart = d - 21,
        )
        s = w.run(s, d - 7)
        s = w.run(s, d)
        return s
    }
    @Test fun weeklyCheck() {
        val w = my.sado.app.data.WeeklyCheck
        val d = LocalDate.now().toEpochDay()
        val s = flatTwoWeeks()
        org.junit.Assert.assertEquals(0, s.checkins[0].kcalChange)
        org.junit.Assert.assertEquals(-1, s.checkins[0].offTrack)
        org.junit.Assert.assertEquals(w.STEP, s.checkins[1].kcalChange)
        org.junit.Assert.assertEquals(w.STEP, s.kcalAdjust)
        // the change shows in the targets, and undo takes it back
        val p = s.profile!!
        org.junit.Assert.assertEquals(my.sado.app.data.Nutrition.targets(p, s.answers).kcal + w.STEP, my.sado.app.data.Nutrition.targets(p, s.answers, s.kcalAdjust).kcal)
        org.junit.Assert.assertEquals(0, w.undo(s, s.checkins[1]).kcalAdjust)
        // not due again until 7 days pass; the cap holds however long it goes on
        org.junit.Assert.assertFalse(w.due(s, d + 6))
        org.junit.Assert.assertTrue(w.due(s, d + 7))
        var long = s
        repeat(10) { long = w.run(long, d + 7L * (it + 1)) }
        org.junit.Assert.assertTrue(long.kcalAdjust <= w.CAP)
        // on track: gaining 0.3 kg a week changes nothing
        val gain = my.sado.app.data.AppState(profile = p, answers = s.answers, weights = (0..13).map { my.sado.app.data.WeightLog(d - 13 + it, if (it < 7) 55f else 55.3f) })
        org.junit.Assert.assertEquals(0, w.run(gain, d).checkins.last().offTrack)
    }
    @Test fun weeklyCheckDialog() = shot("weeklycheck") { my.sado.app.ui.WeeklyCheckBody(flatTwoWeeks().checkins.last(), {}, {}) }
    @Test fun weeklyCheckHome() = shot("weeklycheckhome", { val f = flatTwoWeeks(); Store.update { it.copy(checkins = f.checkins, kcalAdjust = f.kcalAdjust) } }) { my.sado.app.ui.HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun nutritionTargets() {
        val n = my.sado.app.data.Nutrition
        val lean = n.targets(Profile("", 0, 28, 168f, 55f, 1), PlannerAnswers(shape = listOf("vshape", "shredded")))
        // a light frame wanting to look shredded gains slowly instead of cutting, protein near 1.8 g/kg
        org.junit.Assert.assertEquals("Lean bulk", lean.mode)
        org.junit.Assert.assertTrue(lean.kcal > lean.maintain)
        org.junit.Assert.assertTrue(lean.protein in 95..105)
        org.junit.Assert.assertEquals(lean.kcal.toDouble(), (lean.protein * 4 + lean.carbs * 4 + lean.fat * 9).toDouble(), 30.0)
        // the same goal on a heavier body cuts, with more protein per kg
        val heavy = n.targets(Profile("", 0, 28, 168f, 78f, 1), PlannerAnswers(shape = listOf("shredded")))
        org.junit.Assert.assertEquals("Cut", heavy.mode)
        org.junit.Assert.assertTrue(heavy.kcal < heavy.maintain)
        // protein counts from a healthy weight, not the full heavy one
        org.junit.Assert.assertTrue(heavy.protein <= (25f * 1.68f * 1.68f * 2.2f).toInt() + 5)
        // BMI 28 alone reads as fat; a lean waist (80 cm at 168 cm, ratio 0.48) shows it is muscle, so no cut
        val bulky = PlannerAnswers(shape = listOf("bulk"))
        org.junit.Assert.assertEquals("Cut", n.targets(Profile("", 0, 28, 168f, 80f, 1), bulky).mode)
        val muscle = n.targets(Profile("", 0, 28, 168f, 80f, 1, waistCm = 80f), bulky)
        org.junit.Assert.assertEquals("Lean bulk", muscle.mode)
        org.junit.Assert.assertEquals(145, muscle.protein)
        // and a big waist at a normal BMI still cuts
        org.junit.Assert.assertEquals("Cut", n.targets(Profile("", 0, 28, 168f, 68f, 1, waistCm = 92f), bulky).mode)
    }
    @Test fun planner() = shot("planner") { my.sado.app.ui.PlannerScreen({}, {}) }
    @Test fun planresult() = shot("planresult") { my.sado.app.ui.PlanResultScreen({}, {}, {}) }
    @Test fun exercise() = shot("exercise") { my.sado.app.ui.ExerciseDetailScreen("push") {} }
    @Test fun health() = shot("health") { my.sado.app.ui.HealthScreen {} }
    @Test fun test4w() = shot("test4w") { my.sado.app.ui.FitnessTestScreen({}, {}) }
    @Test fun workouts() = shot("workouts") { WorkoutListScreen(onStart = {}, onLibrary = {}) }
    // a plan in today's tags, so the session cards show their scene; [todayTag] lands on today
    private fun artPlan(todayTag: String) = {
        Store.update { s ->
            val p = s.plan!!
            val i = LocalDate.now().dayOfWeek.value - 1
            val week = MutableList(7) { if (it % 2 == 0) "Pull" else "Rest" }.also { it[i] = todayTag; it[(i + 1) % 7] = "Legs+Push" }
            val tags = week.distinct().filter { it != "Rest" }
            s.copy(plan = p.copy(week = week, sessions = tags.map { my.sado.app.data.PlanSession(it, my.sado.app.data.PlanBuilder.tagLabels[it] ?: it, p.exercises) }))
        }
    }
    @Config(sdk = [34], qualifiers = "w411dp-h2000dp-xxhdpi")
    @Test fun homeArt() = shot("homeart", artPlan("Pull")) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Config(sdk = [34], qualifiers = "w411dp-h2000dp-xxhdpi")
    @Test fun homeArtRest() = shot("homeartrest", artPlan("Rest")) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Config(sdk = [34], qualifiers = "w411dp-h2000dp-xxhdpi")
    @Test fun homeArtLight() = shot("homeartlight", { artPlan("HIIT")(); light() }) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun workoutsArt() = shot("workoutsart", artPlan("Legs+Core")) { WorkoutListScreen(onStart = {}, onLibrary = {}) }
    @Test fun phrases() { java.io.File("build/screens/phrases.txt").writeText(my.sado.app.Coach.commonPhrases().joinToString("\n")) }
    @Test fun dbPlan() {
        val out = StringBuilder()
        for (eq in listOf(emptyList(), listOf("dumbbell"))) for (shape in listOf("vshape", "aesthetic", "lean")) {
            val a = PlannerAnswers(shape = listOf(shape), level = 1, days = 3, time = 30, equip = eq)
            val p = my.sado.app.data.PlanBuilder.build(a, my.sado.app.data.PlanBuilder.startingLadders(a))
            out.append("$shape $eq: " + p.exercises.joinToString { it.id + " " + it.sets + "x" + it.reps }).append('\n')
        }
        java.io.File("build/screens/dbplan.txt").writeText(out.toString())
    }
    // 10 days off on Kurus: no tired picture, just the zzz and a faint aura
    @Test fun homeTired() = shot("hometired", { Store.update { it.copy(sports = emptyList(), logs = it.logs.map { l -> l.copy(epochDay = l.epochDay - 10) }) } }) {
        HomeScreen({}, {}, {}, {}, {}, {}, {})
    }
    // Fit II, 23 days off: dropped to Fit I, tired picture, comeback note
    @Test fun homeDropped() = shot("homedropped", {
        val last = LocalDate.now().toEpochDay() - 23
        Store.update { it.copy(sports = emptyList(), xp = 3900, peakXp = 3900, levelFloor = 0, logs = (0 until 40).map { i -> WorkoutLog(last - i * 2, "Push", 30, emptyList(), 100, false) }.reversed()) }
        Store.dropTiers()
    }) {
        HomeScreen({}, {}, {}, {}, {}, {}, {})
    }
    @Test fun flexIcon() = shot("flexicon") { androidx.compose.material3.Icon(my.sado.app.ui.SadoIcons.Flex, null, tint = androidx.compose.ui.graphics.Color.White, modifier = androidx.compose.ui.Modifier.padding(20.dp).height(200.dp).fillMaxWidth()) }
    @Test fun preview() = shot("preview") { my.sado.app.ui.WorkoutPreviewScreen("plan", {}, {}, {}) }
    @Test fun tabbar() = shot("tabbar") { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(top = 40.dp)) { my.sado.app.TabBar("home", {}, {}) } }
    @Test fun auras() = shot("auras") {
        androidx.compose.foundation.layout.Column {
            my.sado.app.data.Progress.stages.chunked(3).forEach { row ->
                androidx.compose.foundation.layout.Row {
                    row.forEach { st ->
                        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            my.sado.app.ui.Aura(st, androidx.compose.ui.Modifier.fillMaxWidth().height(200.dp)) {
                                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(st.image), null, androidx.compose.ui.Modifier.height(170.dp))
                            }
                            androidx.compose.material3.Text(st.name + " · minggu " + st.minWeeks + " · " + st.at + " XP", color = my.sado.app.ui.SadoColors.Text, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
    // intros are three steady parts; the numbers never sit inside the name clip
    @Test fun introParts() {
        val p = my.sado.app.Coach.intro("Push-up", 3, "12")
        org.junit.Assert.assertEquals(3, p.size)
        org.junit.Assert.assertTrue(p[0] == "[energetic] Next… Push-up!" || p[0] == "[energetic] Okay, Push-up pula!")
        org.junit.Assert.assertEquals("[energetic] tiga set.", my.sado.app.Coach.words(p[1]))
        org.junit.Assert.assertEquals("[energetic] dua belas rep.", my.sado.app.Coach.words(p[2]))
        org.junit.Assert.assertEquals("[energetic] empat puluh lima saat.", my.sado.app.Coach.words(my.sado.app.Lines.reps("45s")))
        val parts = my.sado.app.Coach.partLines()
        p.forEach { org.junit.Assert.assertTrue(it, my.sado.app.Coach.words(it) in parts) }
        org.junit.Assert.assertTrue(my.sado.app.Lines.isOldIntro("[energetic] Next… Push-up! tiga set, sepuluh rep."))
        org.junit.Assert.assertTrue(my.sado.app.Lines.isOldIntro("[calm] Next, Plank. Hari ni ringan sikit… dua set je, empat puluh saat."))
        org.junit.Assert.assertFalse(my.sado.app.Lines.isOldIntro("[energetic] Next… Push-up!"))
        org.junit.Assert.assertFalse(my.sado.app.Coach.commonPhrases().any { "ringan sikit" in it })
    }
    // the cut goes in the real pause, even when the timestamps run late, and a comma pause inside a line is left alone
    @Test fun takeCut() {
        val V = my.sado.app.VoiceClips
        // "Next, Push-up!" 0.10–0.90 s (comma pause 0.30–0.40), sentence gap 0.90–1.25, "Okay, Diamond push-up pula!" 1.25–2.40
        val e = FloatArray(250) { i -> val s = i * 0.01; if (s < 0.10 || s in 0.30..0.40 || s in 0.90..1.25 || s in 1.45..1.55 || s > 2.40) 0.001f else 0.3f }
        // hints late by 0.15 s, the way v3 timestamps came out
        val r = V.plan(e, listOf(0.25 to 1.05, 1.40 to 2.55))!!
        org.junit.Assert.assertEquals(2, r.size)
        org.junit.Assert.assertTrue("first starts before its first word", r[0].first <= 0.10)
        org.junit.Assert.assertTrue("first ends inside the gap", r[0].second in 0.90..1.25)
        org.junit.Assert.assertTrue("second starts inside the gap", r[1].first in 0.90..1.25)
        org.junit.Assert.assertTrue("second keeps all of its words", r[1].second >= 2.40)
        org.junit.Assert.assertTrue(r[0].second <= r[1].first)
        // no pause anywhere near the hint: no cut, so the app makes the clips one by one instead
        org.junit.Assert.assertNull(V.plan(FloatArray(250) { 0.3f }, listOf(0.1 to 1.0, 1.2 to 2.4)))
        val chars = "[energetic] Satu set. Dua set.".mapIndexed { i, c -> Triple(c, i * 0.05, i * 0.05 + 0.05) }
        val sp = V.spans(chars, listOf("Satu set.", "Dua set."))!!
        org.junit.Assert.assertEquals(0.60, sp[0].first, 1e-9)
        org.junit.Assert.assertNull(V.spans(chars, listOf("Tiga set.")))
    }
    @Test fun voiceExportNames() {
        org.junit.Assert.assertEquals("next-push-up", my.sado.app.ui.VoiceExport.slug("[energetic] Next… Push-up!"))
        org.junit.Assert.assertEquals("tiga-set", my.sado.app.ui.VoiceExport.slug("[energetic] tiga set."))
    }
    @Test fun devCard() = shot("devcard") { my.sado.app.ui.DevCard {} }
    @Test fun achievementToast() = shot("achtoast") {
        val all = my.sado.app.data.Achievements.all(my.sado.app.data.Store.state.value)
        androidx.compose.foundation.layout.Column {
            my.sado.app.ui.AchievementBanner(listOf(all.first { it.id == "photo1" }.copy(progress = 1))) {}
            my.sado.app.ui.AchievementBanner(listOf(all.first { it.id == "sport1" }, all.first { it.id == "prot1" })) {}
        }
    }
    @Test fun picker() = shot("picker") { my.sado.app.ui.GeneratePicker(my.sado.app.data.VoiceProfile("Adam", "pNInz6obpgDQGcFmaJgB"), my.sado.app.Coach.phraseGroups(), {}, inDialog = false) {} }
    @Test fun voicelib() = shot("voicelib") {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val g = my.sado.app.Coach.phraseGroups()
        (g[0].lines.take(6) + g[1].lines.take(2) + g[2].lines.take(3) + listOf("[energetic] Next… Push-up! tiga set, sepuluh rep.", "[calm] Next, Plank. Hari ni ringan sikit… dua set je, empat puluh saat.")).forEach { line ->
            val f = my.sado.app.VoiceClips.file(ctx, "pNInz6obpgDQGcFmaJgB", line)
            f.writeBytes(byteArrayOf(0))
            java.io.File(f.parentFile, "lines.txt").appendText(f.nameWithoutExtension + "\t" + line + "\n")
        }
        my.sado.app.ui.VoiceLibraryScreen {}
    }
    private fun seedTemplate() = my.sado.app.data.Store.update { it.copy(myTemplates = listOf(my.sado.app.data.MyTemplate("t1", "Push day aku", listOf(
        my.sado.app.data.MyMove("push", 4, 12), my.sado.app.data.MyMove("pike", 3, 8), my.sado.app.data.MyMove("diamond", 3, 10), my.sado.app.data.MyMove("plank", 3, 45))))) }
    @Test fun workoutsMine() = shot("workoutsmine", ::seedTemplate) { WorkoutListScreen(onStart = {}, onLibrary = {}) }
    @Test fun tplEditor() = shot("tpleditor", ::seedTemplate) { my.sado.app.ui.TemplateEditorScreen("t1") {} }
    @Test fun movePicker() = shot("movepicker") { my.sado.app.ui.MovePicker(setOf("push"), {}, inDialog = false) {} }
    @Test fun voice() = shot("voice", { my.sado.app.data.Store.update { it.copy(settings = it.settings.copy(voices = it.settings.voices + my.sado.app.data.VoiceProfile("Suara aku", "cloneid12345", cloned = true))) } }) {
        my.sado.app.ui.VoiceScreen(onBack = {})
    }
    @Test fun clone() = shot("clone") {
        // two lines already on disk: one done card, the next one highlighted
        val dir = java.io.File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "clone").apply { mkdirs() }
        (1..2).forEach { java.io.File(dir, "baris-$it.m4a").writeBytes(ByteArray(2048)) }
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.padding(20.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        ) { my.sado.app.ui.CloneVoice(hasKey = true) { _, _ -> } }
    }
    @Test fun nextUp() = shot("nextup") {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
            my.sado.app.ui.NextUp("Lepas ni · move 2 / 4", PlannedExercise("squat", 3, "12", 12))
            my.sado.app.ui.NextUp("Lepas ni · set 2 / 3", PlannedExercise("dips", 3, "10", 10))
        }
    }
    private fun light() = Store.update { it.copy(settings = it.settings.copy(theme = 1)) }
    @Test fun homeLight() = shot("home_light", ::light) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
    @Test fun previewLight() = shot("preview_light", ::light) { my.sado.app.ui.WorkoutPreviewScreen("plan", {}, {}, {}) }
    @Test fun bodyLight() = shot("body_light", ::light) { my.sado.app.ui.BodyScreen({}, {}, {}) }
    @Test fun settingsLight() = shot("settings_light", ::light) { my.sado.app.ui.SettingsScreen({}, {}, {}, {}, {}, {}) }
    @Test fun achievements() = shot("achievements") { my.sado.app.ui.AchievementsScreen {} }

    // a V-shape, 4-day plan: Pull, Legs+Push and Legs+Core sessions
    private val vshape4 = PlannerAnswers(shape = listOf("vshape"), priority = listOf("arms"), level = 1, pull = 1, days = 4, time = 30, equip = listOf("chair"))
    private fun useBuiltPlan() = Store.update { s ->
        val l = my.sado.app.data.PlanBuilder.startingLadders(vshape4)
        s.copy(answers = vshape4, ladders = l, plan = my.sado.app.data.PlanBuilder.build(vshape4, l), boosts = emptyMap())
    }
    // body map showcase: every muscle, then a push-up (chest and triceps, shoulders and core helping)
    private val everyMuscle = listOf("chest", "delts", "traps", "biceps", "triceps", "forearms", "abs", "obliques", "lats", "lowerback", "glutes", "hamstrings", "quads", "calves")
    @androidx.compose.runtime.Composable
    private fun BodyShowcase() = androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp)) {
        listOf(everyMuscle to emptyList<String>(), listOf("chest", "triceps") to listOf("delts", "abs")).forEach { (p, s) ->
            androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
                my.sado.app.ui.MuscleMap(p, s, back = false, modifier = androidx.compose.ui.Modifier.height(560.dp).width(210.dp))
                my.sado.app.ui.MuscleMap(p, s, back = true, modifier = androidx.compose.ui.Modifier.height(560.dp).width(210.dp))
            }
        }
    }
    @Test fun bodyMap() = shot("bodymap") { BodyShowcase() }
    @Test fun bodyMapLight() = shot("bodymap_light", ::light) { BodyShowcase() }
    @Test fun streakHelp() = shot("streakhelp") { my.sado.app.ui.StreakExplainer(Store.state.value, {}) }
    @Test fun focusStep() = shot("focusstep") { my.sado.app.ui.FocusStep(vshape4, emptyMap(), 10, 11, {}, {}) }
    // the reported case: three shapes on four days with chest as a priority
    private val chest4 = PlannerAnswers(shape = listOf("vshape", "aesthetic", "athletic"), priority = listOf("chest"), level = 1, pull = 1, days = 4, time = 30, equip = listOf("chair"))
    @Test fun focusChest() = shot("focuschest") { my.sado.app.ui.FocusStep(chest4, emptyMap(), 10, 11, {}, {}) }
    // too many fives for four 10-minute days: the step says so instead of quietly shorting one
    private val tooMany = chest4.copy(time = 10, ratings = mapOf("chest" to 5, "lats" to 5, "delts" to 5, "arms" to 5))
    @Test fun focusShort() = shot("focusshort") { my.sado.app.ui.FocusStep(tooMany, emptyMap(), 10, 11, {}, {}) }
    // gear chips from the quiz (bar and chair), then a day without the bar: pull moves swapped, the rest kept
    private fun noBarToday() { useBuiltPlan(); Store.update { it.copy(gearToday = my.sado.app.data.GearToday(LocalDate.now().toEpochDay(), listOf("chair"))) } }
    @Test fun previewGear() = shot("previewgear", ::useBuiltPlan) { my.sado.app.ui.WorkoutPreviewScreen("plan", {}, {}, {}) }
    @Test fun previewNoBar() = shot("previewnobar", ::noBarToday) { my.sado.app.ui.WorkoutPreviewScreen("plan", {}, {}, {}) }
    @Test fun recalculating() = shot("recalc") { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) { my.sado.app.ui.Recalculating() } }
    @Test fun gearForToday() {
        val b = my.sado.app.data.PlanBuilder
        val l = b.startingLadders(vshape4)
        val moves = b.build(vshape4, l).allSessions.flatMap { it.exercises }
        // nothing at hand: every move that needed gear has a bodyweight stand-in of the same kind, sets kept
        val bare = b.adapt(moves, b.withGear(vshape4, emptyList()), l)
        bare.forEach { org.junit.Assert.assertNull(it.id, my.sado.app.data.Catalog.get(it.id).need) }
        // the week's sets are kept: a move with no stand-in left passes its sets to the same kind of move
        org.junit.Assert.assertEquals(moves.sumOf { it.sets }, bare.sumOf { it.sets })
        org.junit.Assert.assertTrue(bare.any { my.sado.app.data.Catalog.get(it.id).group == "lats" })
        // with all the quiz gear nothing changes
        org.junit.Assert.assertEquals(moves, b.adapt(moves, b.withGear(vshape4, b.gearOf(vshape4)), l))
        // yesterday's change no longer applies
        noBarToday()
        // only a day that has a bar move changes (which day is today depends on the calendar)
        val quiz = my.sado.app.ui.sessionFor("plan", quizGear = true).second
        if (quiz.any { my.sado.app.data.Catalog.get(it.id).need == "bar" }) org.junit.Assert.assertNotEquals(my.sado.app.ui.sessionFor("plan").second, quiz)
        org.junit.Assert.assertTrue(my.sado.app.ui.sessionFor("plan").second.none { my.sado.app.data.Catalog.get(it.id).need == "bar" })
        Store.update { it.copy(gearToday = it.gearToday!!.copy(epochDay = LocalDate.now().toEpochDay() - 1)) }
        org.junit.Assert.assertEquals(my.sado.app.ui.sessionFor("plan").second, my.sado.app.ui.sessionFor("plan", quizGear = true).second)
    }
    @Test fun planFollowsEquipment() {
        val b = my.sado.app.data.PlanBuilder
        val none = PlannerAnswers(shape = listOf("vshape"), level = 1, days = 3, time = 30)
        val back = { a: PlannerAnswers -> b.build(a, b.startingLadders(a)).allSessions.flatMap { it.exercises }.map { it.id }.first { my.sado.app.data.Catalog.get(it).group == "lats" } }
        // nothing at home still trains the back with a real pull, and the plan says what to buy first
        org.junit.Assert.assertEquals("doorrow", back(none))
        org.junit.Assert.assertTrue("pull-up bar" in b.build(none, b.startingLadders(none)).why)
        org.junit.Assert.assertEquals("towelrow", back(none.copy(equip = listOf("towel"))))
        org.junit.Assert.assertEquals("bprow", back(none.copy(equip = listOf("backpack"))))
        org.junit.Assert.assertEquals("pullup", back(none.copy(equip = listOf("bar"), pull = 1)))
        // no move asks for gear the user does not have
        b.build(none, b.startingLadders(none)).allSessions.flatMap { it.exercises }.forEach { org.junit.Assert.assertNull(it.id, my.sado.app.data.Catalog.get(it.id).need) }
    }
    @Test fun focusGetsItsSets() {
        val b = my.sado.app.data.PlanBuilder
        val p = b.build(chest4, b.startingLadders(chest4))
        // a 5/5 group gets 10+ direct sets over 2+ days
        org.junit.Assert.assertTrue(b.groupSets(p).getValue("chest") >= b.FOCUS_SETS)
        org.junit.Assert.assertTrue(b.groupDays(p).getValue("chest") >= 2)
        // with short rests on small moves the week often fits chest without switching to a Push day, so the split is not asserted
        org.junit.Assert.assertEquals(emptyList<String>(), b.short(chest4, p))
        // a 1/5 group gets no more than it did at 3/5
        val low = chest4.copy(ratings = mapOf("chest" to 5, "lats" to 1))
        org.junit.Assert.assertTrue((b.groupSets(b.build(low, b.startingLadders(low)))["lats"] ?: 0) <= b.groupSets(p).getValue("lats"))
        org.junit.Assert.assertTrue(b.short(tooMany, b.build(tooMany, b.startingLadders(tooMany))).isNotEmpty())
    }
    @Test fun planFull() = shot("planfull", ::useBuiltPlan) { my.sado.app.ui.PlanResultScreen({}, {}, {}) }
    @Test fun planFullLight() = shot("planfull_light", { useBuiltPlan(); light() }) { my.sado.app.ui.PlanResultScreen({}, {}, {}) }
    @Test fun planSessions() {
        val p = my.sado.app.data.PlanBuilder.build(vshape4, my.sado.app.data.PlanBuilder.startingLadders(vshape4))
        org.junit.Assert.assertEquals(listOf("Pull", "Legs+Push", "Legs+Core"), p.sessions.map { it.tag })
        p.sessions.forEach { s -> val n = s.exercises.size; org.junit.Assert.assertTrue(n >= my.sado.app.data.PlanBuilder.movesPer(vshape4)); org.junit.Assert.assertEquals(n, s.exercises.map { it.id }.toSet().size) }
        // each session is its own workout, not the same list again
        org.junit.Assert.assertEquals(3, p.sessions.map { s -> s.exercises.map { it.id }.toSet() }.distinct().size)
        // Thursday (Legs+Core) gets its own session; Saturday (rest) looks ahead to Monday's Pull
        val monday = LocalDate.now().let { it.minusDays(it.dayOfWeek.value - 1L) }
        org.junit.Assert.assertEquals("Legs+Core", p.sessionOn(monday.plusDays(4)).tag)
        org.junit.Assert.assertEquals("Pull", p.sessionOn(monday.plusDays(5)).tag)
        java.io.File("build/screens/sessions.txt").writeText(p.sessions.joinToString("\n") { s -> s.label + ": " + s.exercises.joinToString { it.id + " " + it.sets + "x" + it.reps } })
    }
    @Test fun doneBadges() = shot("donebadges") { DoneScreen(onHome = {}) }
}
