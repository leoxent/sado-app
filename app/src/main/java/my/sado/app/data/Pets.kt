package my.sado.app.data

import kotlinx.serialization.Serializable
import my.sado.app.R
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One adopted spirit animal. Every animal keeps its own EXP ([growth]); only the one on Home earns more.
 * [stage] is what it has evolved to, which the user triggers once the EXP is there. -1 is a pet from a save made
 * before evolving existed (Store migrates those) or a preview: its stage simply follows its EXP.
 */
@Serializable
data class Pet(val kind: String, val name: String = "", val growth: Int = 0, val since: Long = 0, val stage: Int = -1)

/** What one day has already paid, so no log pays twice, even after deleting and logging it again. */
@Serializable
data class PetDay(val fed: Int = 0, val slept: Boolean = false, val trained: Boolean = false)

/** EXP waiting on Home to be given to the pet: a logged meal's picture, or an energy drink for sleep and workouts. */
data class PetFood(val day: Long, val icon: String, val exp: Int, val source: Source) {
    enum class Source { Food, Sleep, Workout }
}

/**
 * Spirit animals grow from looking after yourself, with a cap on each so spamming logs earns nothing:
 * +1 EXP for each of the first 3 meals of a day, +2 for logging sleep and +2 for a finished workout or sport, each
 * once a day. All of it is paid when the user gives it to the pet on Home, and taken back if the log is deleted.
 * A usual day of ~4.8 EXP reaches Remaja within the first week, to keep a new user going, Dewasa in about 7 weeks
 * and Legenda in about 16. Reaching the EXP
 * only unlocks the evolution: the stage changes when the user starts it, and never goes back down.
 * The pet never dies: left alone it just sleeps.
 */
object Pets {
    data class Kind(
        val id: String,
        val animal: String,
        val defaultName: String,
        val element: String,
        val color: Long,
        val images: List<Int>,
        val sleepImages: List<Int>,
        val openImages: List<Int>,
        val blurb: String,
    )

    /** The head thrown back in a full howl: only the two grown wolves have it. */
    fun howlImage(kind: String, stage: Int): Int? = if (kind != "serigala") null else when (stage) {
        2 -> R.drawable.pet_howl_serigala3
        3 -> R.drawable.pet_howl_serigala4
        else -> null
    }

    /** The eagle's two wing pictures for a stage, raised then pushed down: only the eagle has them. */
    fun flapImages(kind: String, stage: Int): Pair<Int, Int>? = if (kind != "helang") null else when (stage) {
        0 -> R.drawable.pet_flap_up_helang1 to R.drawable.pet_flap_down_helang1
        1 -> R.drawable.pet_flap_up_helang2 to R.drawable.pet_flap_down_helang2
        2 -> R.drawable.pet_flap_up_helang3 to R.drawable.pet_flap_down_helang3
        else -> R.drawable.pet_flap_up_helang4 to R.drawable.pet_flap_down_helang4
    }

    val kinds = listOf(
        Kind("harimau", "Harimau Malaya", "Si Belang", "Kilat", 0xFFFFD43B,
            listOf(R.drawable.pet_harimau1, R.drawable.pet_harimau2, R.drawable.pet_harimau3, R.drawable.pet_harimau4),
            listOf(R.drawable.pet_sleep_harimau1, R.drawable.pet_sleep_harimau2, R.drawable.pet_sleep_harimau3, R.drawable.pet_sleep_harimau4),
            listOf(R.drawable.pet_open_harimau1, R.drawable.pet_open_harimau2, R.drawable.pet_open_harimau3, R.drawable.pet_open_harimau4),
            "Belang kilat, ekor macam petir. Laju dan berani."),
        Kind("helang", "Helang", "Jebat", "Angin", 0xFF7CC8F0,
            listOf(R.drawable.pet_helang1, R.drawable.pet_helang2, R.drawable.pet_helang3, R.drawable.pet_helang4),
            listOf(R.drawable.pet_sleep_helang1, R.drawable.pet_sleep_helang2, R.drawable.pet_sleep_helang3, R.drawable.pet_sleep_helang4),
            listOf(R.drawable.pet_open_helang1, R.drawable.pet_open_helang2, R.drawable.pet_open_helang3, R.drawable.pet_open_helang4),
            "Bulu sayap tajam macam keris. Tenang, mata tajam."),
        Kind("beruang", "Beruang madu", "Madu", "Madu", 0xFFF5A524,
            listOf(R.drawable.pet_beruang1, R.drawable.pet_beruang2, R.drawable.pet_beruang3, R.drawable.pet_beruang4),
            listOf(R.drawable.pet_sleep_beruang1, R.drawable.pet_sleep_beruang2, R.drawable.pet_sleep_beruang3, R.drawable.pet_sleep_beruang4),
            listOf(R.drawable.pet_open_beruang1, R.drawable.pet_open_beruang2, R.drawable.pet_open_beruang3, R.drawable.pet_open_beruang4),
            "Dada sarang madu, tapak berkilat. Kuat makan, kuat badan."),
        Kind("seladang", "Seladang", "Badang", "Batu", 0xFFE0893C,
            listOf(R.drawable.pet_seladang1, R.drawable.pet_seladang2, R.drawable.pet_seladang3, R.drawable.pet_seladang4),
            listOf(R.drawable.pet_sleep_seladang1, R.drawable.pet_sleep_seladang2, R.drawable.pet_sleep_seladang3, R.drawable.pet_sleep_seladang4),
            listOf(R.drawable.pet_open_seladang1, R.drawable.pet_open_seladang2, R.drawable.pet_open_seladang3, R.drawable.pet_open_seladang4),
            "Tanduk dan bonggol batu gunung. Berat, tak goyang."),
        Kind("buaya", "Buaya", "Bujang Senang", "Sungai", 0xFF3EE0C8,
            listOf(R.drawable.pet_buaya1, R.drawable.pet_buaya2, R.drawable.pet_buaya3, R.drawable.pet_buaya4),
            listOf(R.drawable.pet_sleep_buaya1, R.drawable.pet_sleep_buaya2, R.drawable.pet_sleep_buaya3, R.drawable.pet_sleep_buaya4),
            listOf(R.drawable.pet_open_buaya1, R.drawable.pet_open_buaya2, R.drawable.pet_open_buaya3, R.drawable.pet_open_buaya4),
            "Belakang batu sungai berlumut. Sabar, tapi bos sungai."),
        Kind("serigala", "Serigala", "Bayang", "Bayang", 0xFF9C7CF0,
            listOf(R.drawable.pet_serigala1, R.drawable.pet_serigala2, R.drawable.pet_serigala3, R.drawable.pet_serigala4),
            listOf(R.drawable.pet_sleep_serigala1, R.drawable.pet_sleep_serigala2, R.drawable.pet_sleep_serigala3, R.drawable.pet_sleep_serigala4),
            listOf(R.drawable.pet_open_serigala1, R.drawable.pet_open_serigala2, R.drawable.pet_open_serigala3, R.drawable.pet_open_serigala4),
            "Tanda bulan sabit, ekor ungu malam. Senyap dan setia."),
    )

    fun kind(id: String) = kinds.firstOrNull { it.id == id } ?: kinds.first()

    // ── size on Home ─────────────────────────────────────────────────────────────────────────
    // Real-life size of each stage, scaled down to 0.7 next to the character (200 dp = 1.7 m, so 1 m = 83 dp):
    // height to the top of the drawing, or length for the crocodile, which is long and low. A real brahminy
    // kite is only ~50 cm, so the eagle stays small until Legenda. Nothing drops below 40 dp (still easy to
    // see and tap), nothing before Legenda is wider than 240 dp. Legenda is the guardian behind the
    // character: 1.15x the character's height, or 300 dp long for the crocodile.
    // On a phone that still read too small: Anak to Dewasa are now 1.44x (the eagle 1.82x), Legenda 276 dp
    // (1.2x the old 230), the crocodile Legenda 340 dp long so its snout stays inside the hero.
    const val DP_PER_M = 83f
    const val MIN_DP = 40f
    const val MAX_W = 260f
    const val LEGEND_H = 276f
    const val LEGEND_LONG = 340f
    const val MAX_W_ASLEEP = 300f
    const val MIN_W_ASLEEP = 30f

    private val realSize = mapOf(
        "harimau" to listOf(0.35f, 0.65f, 1.0f),
        "helang" to listOf(0.15f, 0.45f, 0.65f), // Remaja and Dewasa a little bigger than real life: they read too small
        "beruang" to listOf(0.3f, 0.6f, 1.2f),
        "seladang" to listOf(0.9f, 1.3f, 1.7f),
        "buaya" to listOf(0.4f, 1.2f, 1.8f), // length (a cartoon croc has a big head, so less than real life)
        "serigala" to listOf(0.3f, 0.6f, 0.9f),
    )
    private val long = setOf("buaya")
    private fun boost(kind: String) = if (kind == "helang") 1.3f * 1.4f else 1.2f * 1.2f

    /** Width and height in dp for a drawing of [aspect] (width / height) at [stage]. */
    fun sizeDp(kind: String, stage: Int, aspect: Float): Pair<Float, Float> {
        var w: Float
        var h: Float
        if (stage >= 3) {
            if (kind in long) { w = LEGEND_LONG; h = w / aspect } else { h = LEGEND_H; w = h * aspect }
            return w to h
        }
        val dp = realSize.getValue(kind)[stage] * DP_PER_M * boost(kind)
        if (kind in long) { w = dp; h = w / aspect } else { h = dp; w = h * aspect }
        if (h < MIN_DP) { h = MIN_DP; w = h * aspect }
        if (w > MAX_W) { w = MAX_W; h = w / aspect }
        return w to h
    }

    // How much of each drawing is the animal (opaque pixels), standing then asleep, per stage. A curled-up drawing
    // fills far more of its picture than a standing one with legs and wings, so matching the pictures' areas made
    // the sleeping animal up to 3x bigger. Measured from the PNGs; redo when the art changes.
    private val fill = mapOf(
        "harimau" to listOf(0.54f to 0.57f, 0.51f to 0.55f, 0.55f to 0.67f, 0.61f to 0.68f),
        "helang" to listOf(0.46f to 0.59f, 0.45f to 0.48f, 0.45f to 0.49f, 0.47f to 0.55f),
        "beruang" to listOf(0.56f to 0.72f, 0.53f to 0.70f, 0.57f to 0.72f, 0.58f to 0.76f),
        "seladang" to listOf(0.42f to 0.57f, 0.44f to 0.57f, 0.47f to 0.62f, 0.48f to 0.62f),
        "buaya" to listOf(0.37f to 0.66f, 0.40f to 0.62f, 0.45f to 0.62f, 0.46f to 0.61f),
        "serigala" to listOf(0.46f to 0.67f, 0.39f to 0.70f, 0.42f to 0.73f, 0.46f to 0.72f),
    )

    /**
     * Width and height in dp for a sleeping drawing of [aspect], given the size it has standing. Lying down the
     * animal itself (not its picture) keeps the same area, so it reads as the same animal: lower and longer,
     * neither puffed up nor shrunk.
     */
    fun sleepSizeDp(kind: String, stage: Int, standW: Float, standH: Float, aspect: Float): Pair<Float, Float> {
        val (awake, asleep) = fill[kind]?.getOrNull(stage) ?: (1f to 1f)
        var w = kotlin.math.sqrt(standW * standH * awake / asleep * aspect)
        w = minOf(w, maxOf(standW * 1.15f, MAX_W_ASLEEP)).coerceAtLeast(MIN_W_ASLEEP)
        return w to w / aspect
    }

    val stageNames = listOf("Anak", "Remaja", "Dewasa", "Legenda")
    val stageAt = listOf(0, 30, 240, 540)
    private val oldStageAt = listOf(0, 14, 50, 150) // before EXP: 1 point a day, so far fewer were needed

    const val FOOD_EXP = 1
    const val FOOD_MAX = 3
    const val SLEEP_EXP = 2
    const val WORKOUT_EXP = 2
    const val ENERGY_ICON = "energy_drink"

    /** The stage this much EXP is enough for. */
    fun reached(growth: Int) = stageAt.indexOfLast { growth >= it }

    /** The stage it has actually evolved to. */
    fun stage(p: Pet) = if (p.stage < 0) reached(p.growth) else p.stage.coerceIn(0, stageAt.lastIndex)

    /** Has the EXP for its next stage and is waiting for the user to start the evolution. */
    fun canEvolve(p: Pet) = stage(p) < stageAt.lastIndex && p.growth >= stageAt[stage(p) + 1]

    /** Moves the pet on Home up one stage. EXP past the next stage stays, ready for the one after. */
    fun evolve(s: AppState): AppState {
        val p = active(s)?.takeIf { canEvolve(it) } ?: return s
        return s.copy(pets = s.pets.map { if (it.kind == p.kind) it.copy(stage = stage(p) + 1) else it })
    }

    /** How full the bar to the next stage is; 1 while an evolution waits, and at Legenda. */
    fun progress(p: Pet): Float {
        val st = stage(p)
        if (st >= stageAt.lastIndex) return 1f
        return ((p.growth - stageAt[st]).toFloat() / (stageAt[st + 1] - stageAt[st])).coerceIn(0f, 1f)
    }

    /** Saves from before EXP: keeps each pet's stage and its place inside that stage on the new, longer scale. */
    fun migrate(s: AppState): AppState {
        if (s.pets.none { it.stage < 0 }) return s
        return s.copy(pets = s.pets.map { p ->
            if (p.stage >= 0) return@map p
            val st = oldStageAt.indexOfLast { p.growth >= it }
            val exp = if (st == oldStageAt.lastIndex) stageAt[st] + (p.growth - oldStageAt[st]) * 2
            else stageAt[st] + (p.growth - oldStageAt[st]) * (stageAt[st + 1] - stageAt[st]) / (oldStageAt[st + 1] - oldStageAt[st])
            p.copy(growth = exp, stage = st)
        })
    }

    fun image(p: Pet) = kind(p.kind).images[stage(p)]
    fun nameOf(p: Pet) = p.name.ifBlank { kind(p.kind).defaultName }

    /** The pet is the gift for coming back: the second day with a full workout or a logged sport. */
    const val UNLOCK_WORKOUTS = 2

    /** The day the user started: the plan, or the first thing they logged. */
    fun startDay(s: AppState): Long? = listOfNotNull(s.planStart, s.logs.minOfOrNull { it.epochDay }, s.meals.minOfOrNull { it.epochDay }).minOrNull()

    /** Days with a full workout (not a Sesi mini, not quit) or a logged sport. */
    fun workoutDays(s: AppState): Set<Long> =
        (s.logs.filter { !it.mini && !it.quit }.map { it.epochDay } + s.sports.map { it.epochDay }).toSet()

    /** Workout days still needed before the pet unlocks; 0 = open. Two on the same day count once. */
    fun workoutsToUnlock(s: AppState): Int = (UNLOCK_WORKOUTS - workoutDays(s).size).coerceAtLeast(0)

    @Suppress("UNUSED_PARAMETER")
    fun unlocked(s: AppState, today: LocalDate = LocalDate.now()) = s.pets.isNotEmpty() || workoutsToUnlock(s) == 0

    /** What stands between the user and adopting one more animal: the goal, and where they are now. */
    data class Lock(val goal: String, val now: String)

    private const val THIRD_PET_DAYS = 14

    // how many animals are owned to the level index the next one needs, the same on both paths:
    // Berisi II / Bangkit II, Fit I, Sado I
    private val levelForNext = mapOf(3 to 4, 4 to 6, 5 to 9)

    /**
     * Null when one more animal can be adopted. The count decides, not the animal, so any of them can be the third.
     * The second is free. Levels go by the best ever reached, so a tier lost to time off never locks anything again,
     * and animals already adopted are never locked. The first week's gift is checked by [unlocked].
     */
    fun adoptLock(s: AppState, today: LocalDate = LocalDate.now()): Lock? {
        val owned = s.pets.size
        if (owned < 2) return null
        if (owned == 2) {
            val days = (startDay(s)?.let { today.toEpochDay() - it } ?: 0) + s.creditWeeks * 7
            return if (days >= THIRD_PET_DAYS) null else Lock("Terbuka lepas 2 minggu aktif", "Lagi ${THIRD_PET_DAYS - days} hari.")
        }
        val need = levelForNext[owned] ?: return null
        val best = Progress.stageFor(s, maxOf(s.peakXp, s.xp))
        return if (best.index >= need) null else Lock("Capai ${Progress.stagesFor(s)[need].name}", "Ko sekarang ${best.name}.")
    }

    fun active(s: AppState): Pet? = s.activePet?.let { id -> s.pets.firstOrNull { it.kind == id } }

    private fun mealsToFeed(s: AppState, day: Long) =
        s.meals.filter { it.epochDay == day && it.items.isNotEmpty() }.sortedBy { it.minute }.take(FOOD_MAX)

    private fun sleptOn(s: AppState, day: Long) = s.sleep.any { it.epochDay == day }
    private fun trainedOn(s: AppState, day: Long) = s.logs.any { it.epochDay == day && !it.quit } || s.sports.any { it.epochDay == day }

    private fun earn(s: AppState, id: String, exp: Int, days: Map<Long, PetDay>, today: Long) = s.copy(
        pets = s.pets.map { if (it.kind == id) it.copy(growth = it.growth + exp) else it },
        petDays = days.filterKeys { it >= today - 14 },
    )

    /**
     * What waits to be given, oldest day first, each day's meals before its energy drinks; Home shows one at a time.
     * Yesterday's stay till the end of today.
     */
    fun foodQueue(s: AppState, today: LocalDate = LocalDate.now()): List<PetFood> {
        if (active(s) == null) return emptyList()
        val t = today.toEpochDay()
        return listOf(t - 1, t).flatMap { day ->
            val d = s.petDays[day] ?: PetDay()
            mealsToFeed(s, day).drop(d.fed).map { PetFood(day, FoodIcons.of(it.items.first()), FOOD_EXP, PetFood.Source.Food) } +
                listOfNotNull(
                    PetFood(day, ENERGY_ICON, SLEEP_EXP, PetFood.Source.Sleep).takeIf { !d.slept && sleptOn(s, day) },
                    PetFood(day, ENERGY_ICON, WORKOUT_EXP, PetFood.Source.Workout).takeIf { !d.trained && trainedOn(s, day) },
                )
        }
    }

    /** Gives the next thing in the queue to the pet on Home and pays its EXP. */
    fun feed(s: AppState, today: LocalDate = LocalDate.now()): AppState {
        val id = active(s)?.kind ?: return s
        val next = foodQueue(s, today).firstOrNull() ?: return s
        val d = s.petDays[next.day] ?: PetDay()
        val paid = when (next.source) {
            PetFood.Source.Food -> d.copy(fed = d.fed + 1)
            PetFood.Source.Sleep -> d.copy(slept = true)
            PetFood.Source.Workout -> d.copy(trained = true)
        }
        return earn(s, id, next.exp, s.petDays + (next.day to paid), today.toEpochDay())
    }

    /**
     * Dev mode: the app as it is after the second workout for someone who has no pet yet, so the gift shows.
     * Store keeps the real state to put back afterwards.
     */
    fun simulateFirstWeek(s: AppState, today: LocalDate = LocalDate.now()): AppState {
        val t = today.toEpochDay()
        val need = (0 until UNLOCK_WORKOUTS).map { t - it }.filter { it !in workoutDays(s) }
        val extra = need.take(workoutsToUnlock(s)).map { WorkoutLog(it, "Full body", 30, emptyList(), 0, false) }
        return s.copy(pets = emptyList(), activePet = null, petDays = emptyMap(), logs = s.logs + extra)
    }

    /**
     * Takes back EXP for logs deleted after they paid; Store runs it on every change. It compares what each kept day
     * paid with the logs still there, so an edit, or deleting one of 4 meals when 3 were fed, takes nothing.
     * The pet on Home pays, the EXP stops at 0, and the stage stays. Only the days in [AppState.petDays] count.
     */
    fun settle(s: AppState): AppState {
        var back = 0
        val days = s.petDays.mapValues { (day, d) ->
            val fed = minOf(d.fed, mealsToFeed(s, day).size)
            val slept = d.slept && sleptOn(s, day)
            val trained = d.trained && trainedOn(s, day)
            back += (d.fed - fed) * FOOD_EXP + (if (d.slept && !slept) SLEEP_EXP else 0) + (if (d.trained && !trained) WORKOUT_EXP else 0)
            PetDay(fed, slept, trained)
        }
        if (back == 0) return s
        return s.copy(
            petDays = days,
            pets = s.pets.map { if (it.kind == s.activePet) it.copy(growth = (it.growth - back).coerceAtLeast(0)) else it },
        )
    }

    /** What today has paid so far. */
    fun todayExp(s: AppState, today: LocalDate = LocalDate.now()): PetDay = s.petDays[today.toEpochDay()] ?: PetDay()

    /** What today's logs have done, paid or still waiting on Home: a task is done when it is logged. */
    fun todayDone(s: AppState, today: LocalDate = LocalDate.now()): PetDay {
        val t = today.toEpochDay()
        return PetDay(mealsToFeed(s, t).size, sleptOn(s, t), trainedOn(s, t))
    }

    /**
     * Puts [kind] on Home, adopting it first if new. EXP a day already gave stays with the old pet.
     * The first pet starts clean: what was logged before it was picked pays nothing.
     */
    fun choose(s: AppState, kind: String, today: LocalDate = LocalDate.now()): AppState {
        val pets = if (s.pets.any { it.kind == kind }) s.pets else s.pets + Pet(kind, since = today.toEpochDay(), stage = 0)
        val t = today.toEpochDay()
        val days = if (s.activePet != null) s.petDays else s.petDays + listOf(t - 1, t).associateWith { day ->
            PetDay(mealsToFeed(s, day).size, sleptOn(s, day), trainedOn(s, day))
        }
        return s.copy(pets = pets, activePet = kind, petDays = days)
    }

    enum class Mood(val label: String) { Lapar("Lapar"), Kenyang("Kenyang"), Tidur("Tidur"), Biasa("Rileks") }

    /** Minute stamp of the newest meal, for the eating animation and the mood. */
    fun lastMeal(s: AppState): Long? = s.meals.maxOfOrNull { it.epochDay * 1440 + it.minute }

    fun mood(s: AppState, now: LocalDateTime = LocalDateTime.now()): Mood {
        val today = now.toLocalDate().toEpochDay()
        val nowMin = today * 1440 + now.hour * 60 + now.minute
        val last = lastMeal(s)
        return when {
            last == null || last / 1440 <= today - 2 -> Mood.Tidur
            nowMin - last <= 180 -> Mood.Kenyang
            // in Ramadan an empty log by afternoon just means fasting
            last / 1440 < today && now.hour >= 14 && Care.active(s, today)?.reason != Care.RAMADAN -> Mood.Lapar
            else -> Mood.Biasa
        }
    }

    fun moodLine(s: AppState, p: Pet, now: LocalDateTime = LocalDateTime.now()): String {
        val n = nameOf(p)
        return when (mood(s, now)) {
            Mood.Tidur -> "$n tidur. Log makan untuk kejut dia."
            Mood.Kenyang -> "$n kenyang. Puas hati!"
            Mood.Lapar -> "$n lapar. Belum log makan hari ni."
            Mood.Biasa -> "$n rileks kat kaki ko."
        }
    }

    /** EXP left to the next stage (0 while an evolution waits) and its name, or null at Legenda. */
    fun toNext(p: Pet): Pair<Int, String>? {
        val st = stage(p)
        if (st >= stageAt.lastIndex) return null
        return (stageAt[st + 1] - p.growth).coerceAtLeast(0) to stageNames[st + 1]
    }
}
