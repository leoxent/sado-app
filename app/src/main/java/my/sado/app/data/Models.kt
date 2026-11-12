package my.sado.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val name: String = "",
    val sex: Int = 0, // 0 lelaki, 1 perempuan
    val age: Int = 25,
    val heightCm: Float = 170f,
    val weightKg: Float = 65f,
    val activity: Int = 1, // 0 banyak duduk, 1 sederhana, 2 aktif
    val waistCm: Float? = null, // optional; waist-to-height tells muscle from fat where BMI cannot
)

@Serializable
data class GearToday(val epochDay: Long, val equip: List<String>)

@Serializable
data class PlannerAnswers(
    val shape: List<String> = emptyList(),
    val priority: List<String> = emptyList(),
    val body: String = "average",
    val level: Int = 0,
    val pull: Int = -1, // -1 tiada palang, 0 belum boleh, 1 = 1-5, 2 = 6+
    val days: Int = 3,
    val time: Int = 20,
    val equip: List<String> = emptyList(),
    val injury: List<String> = emptyList(),
    val intensity: Int = 1,
    // how much each muscle group matters, 1 to 5 (5 = fokus, 10+ set seminggu); the shapes then tailor it
    val ratings: Map<String, Int> = emptyMap(),
)

@Serializable
data class PlannedExercise(
    val id: String,
    val sets: Int,
    val reps: String,
    val target: Int, // top of the rep range, or seconds for timed moves
    val base: String? = null, // the plan's own dose label when reps is today's earned target
    val baseSets: Int? = null, // the plan's own set count when sets includes the user's change
)

@Serializable
data class FocusItem(val label: String, val pct: Int)

@Serializable
data class Plan(
    val title: String,
    val why: String,
    val focus: List<FocusItem>,
    val week: List<String>, // 7 entries Isnin..Ahad, "Rest" = rest day
    val dayLabel: String, // the first session's, kept for plans saved before sessions existed
    val exercises: List<PlannedExercise>,
    val sessions: List<PlanSession> = emptyList(), // one per workout type in [week]
) {
    // every workout type in the week; an older plan has just its one session
    val allSessions: List<PlanSession>
        get() = sessions.ifEmpty { listOf(PlanSession(week.firstOrNull { it != "Rest" } ?: "", dayLabel, exercises)) }

    // the session for a day; a rest day looks ahead to the next workout day
    fun sessionOn(day: java.time.LocalDate = java.time.LocalDate.now()): PlanSession {
        val i = day.dayOfWeek.value - 1
        val tag = (0..6).map { week[(i + it) % 7] }.firstOrNull { it != "Rest" }
        return allSessions.firstOrNull { it.tag == tag } ?: allSessions.first()
    }

    // weekday indexes (0 = Isnin) that run this session
    fun daysOf(session: PlanSession): List<Int> =
        if (sessions.isEmpty()) week.indices.filter { week[it] != "Rest" } else week.indices.filter { week[it] == session.tag }

    fun mapExercises(f: (List<PlannedExercise>) -> List<PlannedExercise>) =
        copy(exercises = f(exercises), sessions = sessions.map { it.copy(exercises = f(it.exercises)) })
}

@Serializable
data class PlanSession(val tag: String, val label: String, val exercises: List<PlannedExercise>)

@Serializable
data class SetLog(
    val reps: Int,
    val rir: Int? = null,
    val kg: Float? = null, // extra load on a weighted move
    val failed: Boolean = false, // "Tak larat": stopped short; reps are what was actually done
    val secs: Int? = null, // from the set starting to Set done, rest and pauses excluded
    val at: Int? = null, // seconds into the session when the set was done
    val restSkipped: Boolean = false, // the rest after this set was cut well short
    val flagged: Boolean = false, // too quick to be real (Integrity); left out of progression and volume
    val endMs: Long? = null, // wall clock when Set done was pressed, to line the set up with heart rate
)

/** One heart-rate reading: wall clock and beats per minute. */
@Serializable
data class HrPoint(val t: Long, val bpm: Int)

@Serializable
data class ExerciseLog(val id: String, val sets: List<SetLog> = emptyList(), val skipped: Boolean = false)

@Serializable
data class WorkoutLog(
    val epochDay: Long,
    val title: String,
    val minutes: Int,
    val exercises: List<ExerciseLog>,
    val xp: Int,
    val quit: Boolean,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val bonus: Int = 0, // comeback XP, already included in xp
    val seconds: Int? = null, // start to finish, pauses (Info screen) excluded
    val kcal: Int? = null, // estimate for display only; never added to the food target
    val kcalSource: String? = null, // "MET", "HR jam" or "Health Connect"
    val startMs: Long? = null,
    val endMs: Long? = null,
    val doubtful: Boolean = false, // the quick-session check fired and the user said they did it
    val mini: Boolean = false, // Sesi mini: one set per move, counts for streak and pet but not progression
    val hr: List<HrPoint> = emptyList(), // readings during the session, from the broadcast or Health Connect
    val hrSource: String? = null, // "Broadcast" or "Health Connect"
    val restingHr: Int? = null, // resting HR when the readings came in, for the zones
)

@Serializable
data class LadderProgress(val rung: Int = 0)

@Serializable
data class Settings(
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val restSeconds: Int = 90,
    val warmup: Boolean = true,
    val voice: Boolean = true,
    val reminders: Boolean = false,
    val reminderHour: Int = 7,
    val reminderMinute: Int = 0,
    val appLock: Boolean = false,
    val foodAi: Boolean = true,
    val healthConnect: Boolean = false,
    val hrMode: Int = 0, // heart rate during a workout: 0 Strength mode on the watch (Health Connect after sync), 1 Broadcast live
    val hrDevice: String? = null, // MAC of the watch broadcasting heart rate over BLE
    val hrDeviceName: String? = null,
    val cameraReps: Boolean = false,
    val theme: Int = 0, // 0 gelap, 1 cerah, 2 ikut phone
    val background: String? = null, // Backgrounds id for the Home card; null = takde
    val aiVoice: Boolean = false, // ElevenLabs clips instead of phone TTS
    val voiceId: String = "pNInz6obpgDQGcFmaJgB", // the active voice
    val voices: List<VoiceProfile> = listOf(VoiceProfile("Adam", "pNInz6obpgDQGcFmaJgB")),
    val autoBackup: Boolean = false,
    val backupFolder: String? = null, // tree Uri the user granted; its password lives in Secrets, not here
    val backupFolderName: String? = null,
    val backupEveryDays: Int = 7,
    val lastAutoBackup: Long? = null, // epoch day
    val lastAutoBackupError: String? = null,
    val devMode: Boolean = false,
    val honestRestPerWeek: Int = 2, // "Rehat jujur" days a week that keep the streak
    val bodyPath: String? = null, // null = ikut planner/badan, "kurus" or "gempal": which starting character
    val voiceBatch: Boolean = true, // intro names and numbers made in one take and cut apart; off = one request per clip // tap the version 7 times in Settings; tools for building the app itself
)

/** A saved ElevenLabs voice; its clips live in their own folder so each voice keeps its own cache. */
@Serializable
data class VoiceProfile(val name: String, val voiceId: String, val cloned: Boolean = false)

@Serializable
data class FitnessTest(val epochDay: Long, val pushups: Int, val plankSec: Int, val pullups: Int? = null)

@Serializable
data class SportLog(val epochDay: Long, val sport: String, val minutes: Int, val level: Int, val kcal: Int, val xp: Int, val hcId: String? = null)

@Serializable
data class WeightLog(val epochDay: Long, val kg: Float)

@Serializable
data class SleepLog(val epochDay: Long, val hours: Float, val source: String) // source: Jawapan or Health Connect

@Serializable
data class ProgressPhoto(
    val epochDay: Long,
    val angle: Int, // 0 depan, 1 sisi, 2 belakang
    val file: String,
    val ratio: Float? = null, // shoulder:waist from the body outline (ratioKind 1); front photos only
    val shoulderPx: Float? = null, // the raw widths, so the ratio can be worked out again later
    val waistPx: Float? = null,
    val ratioKind: Int = 0, // 0 = the old joint-to-joint shoulder:hip, waiting to be measured again; 1 = outline shoulder:waist
)

@Serializable
data class FoodItem(
    val name: String,
    val portion: String,
    val kcalLow: Int,
    val kcalHigh: Int,
    val protein: Float,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val source: String, // AI, Label, Barcode, Makanan aku, Anggaran
    val icon: String? = null, // a thumbnail the user picked; null = guessed from the name (FoodIcons)
)

@Serializable
data class Meal(val epochDay: Long, val minute: Int, val items: List<FoodItem>)

@Serializable
data class MyFood(val name: String, val items: List<FoodItem>)

/** A workout the user built from the move library: their own moves, sets and reps (seconds for timed moves). */
@Serializable
data class MyTemplate(val id: String, val name: String, val moves: List<MyMove>)

@Serializable
data class MyMove(val id: String, val sets: Int, val reps: Int) {
    fun planned(): PlannedExercise {
        val timed = Catalog.get(id).timed
        return PlannedExercise(id, sets, if (timed) "${reps}s" else "$reps", reps)
    }
}

@Serializable
data class AppState(
    val profile: Profile? = null,
    val checkins: List<CheckIn> = emptyList(), // weekly checks, oldest first
    val kcalAdjust: Int = 0, // calories the weekly checks added (or took off) the computed target
    val gearToday: GearToday? = null, // gear at hand for today's workout when it differs from the quiz; cleared when a workout ends
    val answers: PlannerAnswers? = null,
    val plan: Plan? = null,
    val planStart: Long? = null,
    val planBuilt: Int = 0, // PlanBuilder.VERSION the saved plan was made with
    val xp: Int = 0,
    val logs: List<WorkoutLog> = emptyList(),
    val sports: List<SportLog> = emptyList(),
    val tests: List<FitnessTest> = emptyList(),
    val ladders: Map<String, LadderProgress> = emptyMap(),
    val boosts: Map<String, Int> = emptyMap(), // move id to reps (or 5s steps) earned above the bottom of its range
    val extraSets: Map<String, Int> = emptyMap(), // move id to sets the user added (or took off) before a workout
    val loads: Map<String, Float> = emptyMap(), // weighted move id to the extra kg it is done with now
    val deload: Set<String> = emptySet(), // moves failed two sessions running: one set fewer next time
    val honestRests: List<Long> = emptyList(), // "Rehat jujur" days: keep the streak, a few a week
    val photos: List<ProgressPhoto> = emptyList(),
    val meals: List<Meal> = emptyList(),
    val myFoods: List<MyFood> = emptyList(),
    val sleep: List<SleepLog> = emptyList(),
    val weights: List<WeightLog> = emptyList(),
    val seenAchievements: Set<String> = emptySet(), // celebrated on the Done screen already
    val myTemplates: List<MyTemplate> = emptyList(),
    val settings: Settings = Settings(),
    val levelFloor: Int = -1, // -1 = not yet moved to the 15-level ladder
    val proveAsked: Boolean = false, // the one-time "Buktikan!" placement test was offered at setup
    val placed: Boolean = false, // passed it: starts at level 4 (Berisi I / Bangkit I)
    val creditWeeks: Int = 0, // weeks of training the placement counts as already done
    val peakXp: Int = 0, // highest XP reached; below it, workouts count double (comeback)
    val decayFrom: Long = -1, // last active day the tier drops below were counted from
    val decaySteps: Int = 0, // tiers already dropped since decayFrom
    val breakTold: String = "", // which tired/drop state the coach already announced, so Home says it once
    val pets: List<Pet> = emptyList(), // spirit animals adopted so far, each with its own growth
    val activePet: String? = null, // the one on Home; only it grows
    val petDays: Map<Long, PetDay> = emptyMap(), // what each recent day already paid in EXP, so no log pays twice
    val health: HealthCheck? = null, // answers to the health questions; null = not asked yet
    val nursing: Boolean = false, // breastfeeding: no deficit, and a little more food
    val rest: List<RestSpell> = emptyList(), // rest mode, past and current
    val ramadan: RamadanDates? = null, // this year's dates from JAKIM's calendar
    val ramadanAsked: Int = 0, // the year rest mode was last offered or turned on for Ramadan
)
