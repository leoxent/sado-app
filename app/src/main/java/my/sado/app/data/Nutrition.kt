package my.sado.app.data

import kotlin.math.roundToInt

/**
 * Daily calorie and macro targets from the profile (height, weight, age, activity) and the body goal from the quiz.
 * Energy: Mifflin-St Jeor times activity. Protein 1.6–2.2 g/kg (higher when eating below maintenance, to keep muscle).
 * Fat about a quarter of calories, never under 0.6 g/kg. Carbs take the rest, they fuel the training.
 */
object Nutrition {
    data class Targets(
        val kcal: Int,
        val maintain: Int,
        val protein: Int,
        val fat: Int,
        val carbs: Int,
        val mode: String, // short label: Cut, Lean bulk, Recomp, Kekal
        val weekly: String, // the weight change to expect, e.g. "+0.25 kg seminggu"
        val why: List<String>,
        val atFloor: Boolean = false, // held up by the minimum, so asking for less changes nothing
    )

    fun bmi(p: Profile) = p.weightKg / ((p.heightCm / 100f) * (p.heightCm / 100f))

    fun maintenance(p: Profile): Int {
        val bmr = 10 * p.weightKg + 6.25f * p.heightCm - 5 * p.age + if (p.sex == 0) 5 else -161
        return ((bmr * listOf(1.2f, 1.375f, 1.55f)[p.activity.coerceIn(0, 2)]) / 10).roundToInt() * 10
    }

    /** The user's own targets: their weekly changes, and no deficit while [Care] says the body shouldn't be in one. */
    fun targets(s: AppState, p: Profile): Targets = targets(p, s.answers, s.kcalAdjust, Care.noCut(s), Care.extraKcal(s))

    fun targets(p: Profile, a: PlannerAnswers?, adjust: Int = 0, noCut: Boolean = false, extra: Int = 0): Targets {
        val keep = maintenance(p)
        val bmi = bmi(p)
        val shapes = a?.shape.orEmpty()
        val wantsLean = shapes.any { it == "shredded" || it == "lean" }
        val wantsSize = shapes.any { it == "bulk" || it == "vshape" || it == "aesthetic" }
        val body = a?.body ?: "average"
        val why = mutableListOf<String>()
        why += "BMI ${"%.1f".format(bmi)} (${p.heightCm.roundToInt()} cm, ${"%.1f".format(p.weightKg).removeSuffix(".0")} kg). Kalori kekal berat lebih kurang $keep kcal sehari."
        // waist-to-height, when the waist is given: under 0.5 is lean whatever the BMI, 0.53+ carries fat to lose
        val whtr = p.waistCm?.takeIf { it > 0f }?.let { it / p.heightCm }
        if (whtr != null) why += "Pinggang ${p.waistCm!!.roundToInt()} cm, nisbah pinggang:tinggi ${"%.2f".format(whtr)} (" +
            when { whtr < 0.5f -> "bawah 0.5, lemak rendah"; whtr < 0.53f -> "sempadan"; else -> "lemak perut perlu turun" } + "). Ni lebih tepat dari BMI untuk badan berotot."
        val fatHigh = if (whtr != null) whtr >= 0.53f else body == "heavy" || bmi >= 27f
        val fatSome = if (whtr != null) whtr >= 0.48f else bmi >= 22f

        // the goal decides surplus or deficit, but a lean body never cuts: shredded on a light frame comes from more muscle
        val (mode, factor) = when {
            noCut && (fatHigh || (wantsLean && fatSome)) -> "Kekal" to 1.0f
            fatHigh -> "Cut" to 0.80f
            wantsLean && fatSome -> "Cut" to 0.85f
            bmi < 20.5f && (wantsSize || wantsLean || body == "skinny") -> "Lean bulk" to 1.05f
            wantsLean -> "Recomp" to 1.0f
            wantsSize || body == "skinny" -> "Lean bulk" to 1.05f
            else -> "Kekal" to 1.0f
        }
        why += when (mode) {
            "Cut" -> "Defisit ${((1 - factor) * 100).roundToInt()}%: lemak turun lebih kurang 0.5% berat seminggu, otot dijaga dengan protein tinggi dan workout berat."
            "Lean bulk" -> if (wantsLean) "Badan kau dah kurus. Untuk nampak shredded, yang kurang ialah otot, bukan lemak. Lebihan ${((factor - 1) * 100).roundToInt()}% supaya otot naik perlahan dan lemak kekal rendah."
                else "Lebihan ${((factor - 1) * 100).roundToInt()}%: cukup untuk otot naik tanpa lemak naik banyak."
            "Recomp" -> "Recomp: makan tahap kekal, protein tinggi. Lemak turun dan otot naik serentak, perlahan tapi kemas."
            else -> "Makan tahap kekal, badan kekal macam sekarang."
        }
        // Never below the guideline minimum or too far under upkeep. A small body whose upkeep is under the minimum eats its upkeep.
        val adjusted = adjust.let { if (noCut) it.coerceAtLeast(0) else it }
        val asked = ((keep * factor) / 10).roundToInt() * 10 + adjusted + extra
        val least = maxOf(minOf(Care.kcalFloor(p.sex), keep), keep - Care.MAX_DEFICIT)
        val kcal = maxOf(asked, least)
        if (noCut) why += "Defisit kalori dimatikan buat masa ni: badan kau perlukan tenaga lebih untuk pulih atau jaga kesihatan."
        if (extra > 0) why += "Menyusu: tambah $extra kcal sehari."
        if (kcal > asked) why += "Had minimum: $kcal kcal sehari. Garis panduan obesiti KKM 2023 guna 1,200 kcal untuk wanita, 1,500 kcal untuk lelaki, dan defisit paling banyak 750 kcal. Lebih rendah perlu pengawasan pasukan perubatan."
        if (adjusted != 0) why += "Semakan mingguan: ${if (adjusted > 0) "+" else "−"}${kotlin.math.abs(adjusted)} kcal sebab berat tak ikut sasaran 2 minggu berturut."

        // protein on a sensible weight: a heavy body counts from the weight at BMI 25, not the full weight
        // (a heavy body with a lean waist is muscle, so its full weight counts)
        val base = if (whtr != null && whtr < 0.5f) p.weightKg else minOf(p.weightKg, 25f * (p.heightCm / 100f) * (p.heightCm / 100f))
        val perKg = when (mode) { "Cut", "Recomp" -> 2.2f; "Lean bulk" -> 1.8f; else -> 1.6f }
        val protein = (base * perKg / 5).roundToInt() * 5
        why += "Protein ${"%.1f".format(perKg)} g × ${base.roundToInt()} kg = ${protein}g sehari" + if (mode == "Cut" || mode == "Recomp") ", tinggi sebab nak jaga otot masa lemak turun." else "."
        val fat = maxOf(kcal * 0.25f / 9, p.weightKg * 0.6f).let { (it / 5).roundToInt() * 5 }
        val carbs = maxOf(0, ((kcal - protein * 4 - fat * 9) / 4f / 5).roundToInt() * 5)
        why += "Lemak ${fat}g untuk hormon, karbo ${carbs}g untuk tenaga workout."
        val weekly = when (mode) {
            "Cut" -> "−${"%.1f".format(p.weightKg * 0.005f)} kg seminggu"
            "Lean bulk" -> "+0.1–0.25 kg seminggu"
            "Recomp" -> "berat lebih kurang sama, cermin yang berubah"
            else -> "berat kekal"
        }
        why += "Timbang seminggu sekali. Kalau 2 minggu tak ikut sasaran ($weekly), ubah 150 kcal."
        return Targets(kcal, keep, protein, fat, carbs, mode, weekly, why, kcal > asked)
    }
}
