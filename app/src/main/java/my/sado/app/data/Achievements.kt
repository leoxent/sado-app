package my.sado.app.data

import kotlin.math.roundToInt

/**
 * Milestones and badges, all worked out from what the app already records, so old history counts too.
 * Each has a goal and a progress number; it is unlocked once progress reaches the goal.
 */
data class Achievement(val id: String, val group: String, val title: String, val desc: String, val icon: String, val goal: Int, val progress: Int) {
    val unlocked get() = progress >= goal
    val fraction get() = (progress.toFloat() / goal).coerceIn(0f, 1f)
}

object Achievements {
    val groups = listOf("Workout", "Konsisten", "Kekuatan", "Level", "Badan & makan", "Lain-lain")

    fun all(s: AppState): List<Achievement> {
        val done = s.logs.filter { it.xp > 0 }
        val reps = s.logs.sumOf { l -> l.exercises.filter { !it.skipped }.sumOf { e -> Integrity.real(e).sumOf { if (Catalog.moves.firstOrNull { m -> m.id == e.id }?.timed == true) 0 else it.reps } } }

        val minutes = s.logs.sumOf { it.minutes } + s.sports.sumOf { it.minutes }
        val streak = Progress.streak(s).days
        val stage = Progress.stageFor(s, s.peakXp.coerceAtLeast(s.xp)).index
        val rung = { key: String -> s.ladders[key]?.rung ?: 0 }
        val target = (s.profile?.weightKg ?: 65f) * 1.6f
        val proteinDays = s.meals.groupBy { it.epochDay }.count { (_, m) -> m.sumOf { meal -> meal.items.sumOf { it.protein.toDouble() } } >= target }
        val maxPush = s.tests.maxOfOrNull { it.pushups } ?: 0
        val maxPlank = s.tests.maxOfOrNull { it.plankSec } ?: 0
        val lost = s.weights.takeIf { it.size >= 2 }?.let { ((it.first().kg - it.minOf { w -> w.kg }) * 10).roundToInt() } ?: 0
        // share of the starting weight lost, in tenths of a percent: 5% already lowers blood pressure and sugar
        val lostPct = s.weights.takeIf { it.size >= 2 }?.let { w -> ((w.first().kg - w.minOf { it.kg }) / w.first().kg * 1000).roundToInt().coerceAtLeast(0) } ?: 0
        val petTop = s.pets.maxOfOrNull { Pets.stage(it) } ?: -1

        fun a(id: String, group: String, title: String, desc: String, icon: String, goal: Int, progress: Int) = Achievement(id, group, title, desc, icon, goal, progress)
        val stageName = { i: Int -> Progress.stagesFor(s)[i].name }
        return listOf(
            a("w1", "Workout", "Langkah pertama", "Habiskan workout pertama", "dumbbell", 1, done.size),
            a("w10", "Workout", "Sepuluh jari", "10 workout siap", "dumbbell", 10, done.size),
            a("w25", "Workout", "Dah jadi rutin", "25 workout siap", "dumbbell", 25, done.size),
            a("w50", "Workout", "Separuh century", "50 workout siap", "dumbbell", 50, done.size),
            a("w100", "Workout", "Century", "100 workout siap", "dumbbell", 100, done.size),
            a("r1k", "Workout", "Seribu rep", "1,000 rep dikira", "bolt", 1000, reps),
            a("r10k", "Workout", "Mesin rep", "10,000 rep dikira", "bolt", 10000, reps),
            a("m10h", "Workout", "10 jam peluh", "600 minit aktif (workout + sukan)", "timer", 600, minutes),
            a("m50h", "Workout", "50 jam peluh", "3,000 minit aktif", "timer", 3000, minutes),

            a("s7", "Konsisten", "Seminggu kental", "Streak 7 hari", "flame", 7, streak),
            a("s30", "Konsisten", "Sebulan tak putus", "Streak 30 hari", "flame", 30, streak),
            a("s100", "Konsisten", "100 hari", "Streak 100 hari", "flame", 100, streak),
            a("s365", "Konsisten", "Setahun penuh", "Streak 365 hari", "flame", 365, streak),
            a("back", "Konsisten", "Comeback", "Balik workout lepas rehat lama", "heart", 1, s.logs.count { it.bonus > 0 }),

            a("pullup", "Kekuatan", "Pull-up pertama", "Naik ladder tarik sampai pull-up", "flex", 2, rung("pull")),
            a("decline", "Kekuatan", "Push-up kaki atas", "Naik ladder tolak sampai decline push-up", "flex", 2, rung("push")),
            a("onearm", "Kekuatan", "Satu tangan", "Sampai one-arm push-up", "flex", 4, rung("push")),
            a("pistol", "Kekuatan", "Pistol squat", "Sampai pistol squat", "flex", 3, rung("squat")),
            a("push30", "Kekuatan", "30 push-up", "30 push-up dalam ujian kecergasan", "star", 30, maxPush),
            a("plank2", "Kekuatan", "Plank 2 minit", "Plank 120 saat dalam ujian", "timer", 120, maxPlank),

            a("lv3", "Level", stageName(3), "Capai level ${stageName(3)}", "star", 3, stage),
            a("lv6", "Level", stageName(6), "Capai level ${stageName(6)}", "star", 6, stage),
            a("lv9", "Level", stageName(9), "Capai level ${stageName(9)}", "star", 9, stage),
            a("lv12", "Level", stageName(12), "Capai level ${stageName(12)}", "star", 12, stage),
            a("lv14", "Level", stageName(14), "Level maksimum", "star", 14, stage),

            a("photo1", "Badan & makan", "Gambar pertama", "Ambil gambar progress pertama", "camera", 1, s.photos.size),
            a("photo12", "Badan & makan", "Album transformasi", "12 gambar progress", "camera", 12, s.photos.size),
            a("weigh4", "Badan & makan", "Rajin timbang", "Log berat 4 kali", "steps", 4, s.weights.size),
            a("lost2", "Badan & makan", "Turun 2 kg", "Berat turun 2 kg dari log pertama", "steps", 20, lost),
            *(if (Progress.heavyPath(s)) arrayOf(
                a("lost5p", "Badan & makan", "Turun 5%", "Berat turun 5% dari log pertama. Tekanan darah dan gula pun ikut turun.", "steps", 50, lostPct),
                a("lost10p", "Badan & makan", "Turun 10%", "Berat turun 10% dari log pertama", "steps", 100, lostPct),
            ) else emptyArray()),
            a("prot1", "Badan & makan", "Cukup protein", "Capai target protein sehari", "heart", 1, proteinDays),
            a("prot7", "Badan & makan", "Seminggu protein", "Capai target protein 7 hari", "heart", 7, proteinDays),

            a("sport1", "Lain-lain", "Bukan gym je", "Log sukan pertama", "steps", 1, s.sports.size),
            a("sport10", "Lain-lain", "Kaki sukan", "Log 10 sesi sukan", "steps", 10, s.sports.size),
            a("test1", "Lain-lain", "Uji diri", "Buat ujian kecergasan pertama", "watch", 1, s.tests.size),
            a("test3", "Lain-lain", "Bukti progress", "3 ujian kecergasan", "watch", 3, s.tests.size),
            a("pet1", "Lain-lain", "Kawan baru", "Pilih spirit animal", "heart", 1, s.pets.size),
            a("pet3", "Lain-lain", "Dah dewasa", "Spirit animal sampai Dewasa", "heart", 3, petTop + 1),
            a("pet4", "Lain-lain", "Legenda", "Spirit animal sampai Legenda", "star", 4, petTop + 1),
            a("custom", "Lain-lain", "Coach sendiri", "Buat workout sendiri", "edit", 1, s.myTemplates.size),
        )
    }

    /** Unlocked but not yet celebrated; the Done screen shows these once. */
    fun fresh(s: AppState) = all(s).filter { it.unlocked && it.id !in s.seenAchievements }
}
