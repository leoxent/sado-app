package my.sado.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.sado.app.R
import my.sado.app.data.PlanBuilder
import my.sado.app.data.PlannerAnswers
import my.sado.app.data.Profile
import my.sado.app.data.Store
import kotlin.math.roundToInt

@Composable
internal fun NumField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        colors = fieldColors(),
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SadoColors.Accent, unfocusedBorderColor = SadoColors.Track,
    focusedLabelColor = SadoColors.Accent, unfocusedLabelColor = SadoColors.TextMuted,
    focusedTextColor = SadoColors.Text, unfocusedTextColor = SadoColors.Text, cursorColor = SadoColors.Accent,
)

@Composable
fun ProfileSetupScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val saved = Store.state.collectAsState().value.profile ?: Profile()
    var name by remember { mutableStateOf(saved.name) }
    var sex by remember { mutableIntStateOf(saved.sex) }
    var age by remember { mutableStateOf(saved.age.toString()) }
    var height by remember { mutableStateOf(saved.heightCm.roundToInt().toString()) }
    var weight by remember { mutableStateOf(saved.weightKg.toString().removeSuffix(".0")) }
    var act by remember { mutableIntStateOf(saved.activity) }
    var waist by remember { mutableStateOf(saved.waistCm?.roundToInt()?.toString() ?: "") }
    val state = Store.state.collectAsState().value

    val w = weight.toFloatOrNull() ?: 0f
    val h = height.toFloatOrNull() ?: 0f
    val a = age.toIntOrNull() ?: 0
    // Mifflin-St Jeor BMR x daily activity factor; workouts are counted separately
    val bmr = 10 * w + 6.25f * h - 5 * a + if (sex == 0) 5 else -161
    val tdee = if (w > 0 && h > 0 && a > 0) ((bmr * listOf(1.2f, 1.375f, 1.55f)[act]) / 10).roundToInt() * 10 else null
    val bmi = if (w > 0 && h > 0) w / ((h / 100) * (h / 100)) else null
    val wc = waist.toFloatOrNull()?.takeIf { it in 40f..200f }
    val valid = name.isNotBlank() && w in 25f..300f && h in 100f..250f && a in my.sado.app.data.Care.MIN_AGE..100 && (waist.isBlank() || wc != null)
    val goal = if (valid) my.sado.app.data.Nutrition.targets(state, Profile(name, sex, a, h, w, act, wc)) else null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Kenal kau sikit", "Bukan akaun · disimpan dalam phone je", onBack = onBack)
        OutlinedTextField(name, { name = it.take(20) }, label = { Text("Nama panggilan") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        Label("Jantina · untuk kira kalori")
        Segmented(listOf("Lelaki", "Perempuan"), sex) { sex = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Umur", age, Modifier.weight(1f)) { age = it }
            NumField("Tinggi cm", height, Modifier.weight(1f)) { height = it }
            NumField("Berat kg", weight, Modifier.weight(1f)) { weight = it }
        }
        NumField("Lilit pinggang cm (tak wajib)", waist, Modifier.fillMaxWidth()) { waist = it }
        Text("Ukur tengah-tengah antara rusuk paling bawah dan bucu tulang pinggul (biasanya sikit atas pusat), pagi, lepas hembus nafas biasa. Bantu app beza berat sebab otot dengan berat sebab lemak. Boleh kosongkan.", color = SadoColors.TextMuted, fontSize = 12.sp)
        Label("Harian kau (selain workout)")
        listOf("Banyak duduk" to "Kerja ofis, jarang jalan", "Sederhana" to "Jalan kaki sikit setiap hari", "Aktif" to "Kerja berdiri / banyak bergerak")
            .forEachIndexed { i, (t, s) -> OptionRow(t, s, act == i) { act = i } }
        Card {
            Text("TARGET HARIAN KAU", color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Stat(goal?.kcal?.toString() ?: tdee?.toString() ?: "–", "kcal")
                Stat(goal?.let { "${it.protein}g" } ?: "–", "protein")
                Stat(if (wc != null && h > 0) "%.2f".format(wc / h) else bmi?.let { "%.1f".format(it) } ?: "–", if (wc != null) "pinggang:tinggi" else "BMI")
            }
            Text(goal?.let { "Mod ${it.mode}, ikut badan dan goal kau. Kekal berat ~${it.maintain} kcal." } ?: "Kalori guna Mifflin-St Jeor.", color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        if (a in 1 until my.sado.app.data.Care.MIN_AGE) Callout("Sado untuk umur ${my.sado.app.data.Care.MIN_AGE}+", AGE_WHY, SadoColors.Streak)
        else if (!valid) Text("Isi nama, umur ${my.sado.app.data.Care.MIN_AGE}–100, tinggi 100–250 cm dan berat 25–300 kg.", color = SadoColors.Streak, fontSize = 13.sp)
        PrimaryButton(if (state.plan == null) "Seterusnya" else "Simpan", enabled = valid) {
            Store.update { it.copy(profile = Profile(name.trim(), sex, a, h, w, act, wc)) }
            onDone()
        }
    }
}

@Composable
fun Stat(value: String, label: String, color: androidx.compose.ui.graphics.Color = SadoColors.Text) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 28.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        Text(label, color = SadoColors.TextMuted, fontSize = 13.sp)
    }
}

private data class Q(
    val key: String,
    val title: String,
    val hint: String,
    val options: List<Triple<String, String, String>>, // value, label, sub
    val multi: Boolean = false,
    val max: Int? = null,
    val required: Boolean = true,
    val rating: Boolean = false, // each option rated 1 to 5, stored as "value:n"
)

private val questions = listOf(
    Q("shape", "Bentuk badan impian kau?", "Pilih sampai 3. Kami gabungkan.", listOf(
        Triple("vshape", "V-shape", "Bahu dan belakang lebar, pinggang kecil"),
        Triple("aesthetic", "Aesthetic", "Lean, seimbang, abs nampak"),
        Triple("bulk", "Besar & kuat", "Size otot & strength dulu"),
        Triple("athletic", "Atletik", "Laju, tangkas, tahan lasak"),
        Triple("shredded", "Shredded", "Otot tajam, lemak rendah, urat nampak"),
        Triple("lean", "Kurus & tone", "Buang lemak dulu, otot nampak kemas")), multi = true, max = 3),
    Q("priority", "Rate setiap bahagian badan", "5 = paling nak tonjol, 3 = biasa, 1 = sikit je. Bentuk badan impian tadi tailor lagi di atas rating ni.", listOf(
        Triple("chest", "Dada", "Pectoral"), Triple("side_delts", "Bahu sisi", "Delt sisi, bagi bahu nampak lebar"), Triple("front_delts", "Bahu depan", "Delt depan"),
        Triple("rear_delts", "Bahu belakang", "Delt belakang, postur"), Triple("lats", "Belakang", "Lats, rahsia V-shape"),
        Triple("arms", "Lengan", "Bisep dan trisep"), Triple("abs", "Perut", "Six-pack dan pinggang"), Triple("legs", "Kaki", "Peha dan betis"),
        Triple("glutes", "Punggung", "Glutes")), multi = true, required = false, rating = true),
    Q("body", "Badan kau sekarang macam mana?", "Tiada jawapan salah.", listOf(
        Triple("skinny", "Kurus", "Susah naik berat"), Triple("average", "Sederhana", "Ada sikit otot, ada sikit lemak"), Triple("heavy", "Berisi", "Ada lemak perut nak buang"))),
    Q("level", "Berapa push-up kau boleh buat?", "Sekali harung, form betul.", listOf(
        Triple("0", "Bawah 5", "Baru nak start"), Triple("1", "5 hingga 20", "Dah ada asas"), Triple("2", "Lebih 20", "Dah selalu workout"))),
    Q("equip", "Ada alat apa kat rumah?", "Pilih semua yang ada. Takde langsung pun takpe, plan guna berat badan.", listOf(
        Triple("bar", "Pull-up bar", "Paling penting untuk belakang dan V-shape"),
        Triple("chair", "Meja atau kerusi kukuh", "Untuk dips, inverted row, split squat"),
        Triple("towel", "Tuala + pintu", "Towel row: ganti pull-up bar untuk belakang"),
        Triple("backpack", "Beg galas", "Isi buku atau botol air, jadi beban"),
        Triple("dumbbell", "Dumbbell atau kettlebell", "Apa-apa berat pun okay"),
        Triple("band", "Resistance band", "Getah senaman")), multi = true, required = false),
    Q("pull", "Berapa pull-up kau boleh buat?", "Sekali harung, dagu lepas bar.", listOf(
        Triple("0", "Belum boleh", "Kita mula dengan negative pull-up"),
        Triple("1", "1 hingga 5", "Boleh sikit"), Triple("2", "6 ke atas", "Dah kuat"))),
    Q("days", "Berapa hari seminggu boleh workout?", "Konsisten lagi penting dari lama.", listOf(
        Triple("2", "2 hari", "Minggu sibuk"), Triple("3", "3 hari", "Paling seimbang"), Triple("4", "4 hari", "Serius"), Triple("5", "5 hari", "Full commit"))),
    Q("time", "Berapa lama setiap sesi?", "Termasuk warm-up.", listOf(
        Triple("10", "10 minit", "3 move"), Triple("20", "20 minit", "4 move"), Triple("30", "30 minit", "5 move"), Triple("45", "45 minit", "6 move"))),
    Q("injury", "Ada sakit atau kecederaan?", "Kitorang buang move yang risky.", listOf(
        Triple("knee", "Lutut", "Elak lompat dan lunge"), Triple("back", "Belakang", "Elak tekanan pada pinggang"),
        Triple("shoulder", "Bahu", "Elak overhead press")), multi = true, required = false),
    Q("intensity", "Kau suka workout macam mana?", "Boleh ubah bila-bila.", listOf(
        Triple("0", "Santai", "Rest panjang, set sikit"), Triple("1", "Sederhana", "Penat tapi masih senyum"), Triple("2", "Push habis", "Set terakhir sampai tak larat"))),
)

@Composable
fun PlannerScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val prev = Store.state.collectAsState().value.answers
    var step by remember { mutableIntStateOf(0) }
    val answers = remember {
        val p = prev ?: PlannerAnswers()
        mutableStateOf(
            mapOf(
                "shape" to p.shape, "priority" to PlanBuilder.groupNames.keys.filter { it != "cardio" }.map { "$it:${PlanBuilder.rating(p, it)}" }, "equip" to (p.equip + listOfNotNull("bar".takeIf { prev != null && prev.pull >= 0 })).distinct(), "injury" to (p.injury - "impact"),
                "body" to listOfNotNull(prev?.body), "level" to listOfNotNull(prev?.level?.toString()),
                "pull" to listOfNotNull(prev?.pull?.takeIf { it >= 0 }?.toString()), "days" to listOfNotNull(prev?.days?.toString()),
                "time" to listOfNotNull(prev?.time?.toString()), "intensity" to listOfNotNull(prev?.intensity?.toString()),
            )
        )
    }
    val ladders = Store.state.collectAsState().value.ladders
    // after the last question: a first plan and the muscles it trains, then one more pick
    var review by remember { mutableStateOf<PlannerAnswers?>(null) }
    val total = questions.size + 1
    review?.let { a ->
        FocusStep(a, ladders, step = questions.size, total = total, onBack = { review = null }) { final ->
            Store.update { s ->
                val l = PlanBuilder.startingLadders(final) + s.ladders
                s.copy(answers = final, ladders = l, plan = PlanBuilder.build(final, l), planStart = java.time.LocalDate.now().toEpochDay())
            }
            onDone()
        }
        return
    }
    // the pull-up question only matters with a bar
    val skip = { i: Int -> questions[i].key == "pull" && "bar" !in answers.value["equip"].orEmpty() }
    val next = (step + 1..questions.lastIndex).firstOrNull { !skip(it) }
    val prevStep = (step - 1 downTo 0).firstOrNull { !skip(it) }
    val q = questions[step]
    val sel = answers.value[q.key].orEmpty()
    val ready = !q.required || sel.isNotEmpty()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Title("", null) { if (prevStep == null) onBack() else step = prevStep }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(total) { i ->
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i <= step) SadoColors.Accent else SadoColors.Track))
                }
            }
            Text("${step + 1} / $total", color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.tier7), null, Modifier.size(width = 48.dp, height = 78.dp))
            Card {
                Text(q.title, color = SadoColors.Text, fontSize = 19.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Text(q.hint, color = SadoColors.TextMuted, fontSize = 13.sp)
            }
        }
        if (q.rating) q.options.forEach { (value, label, sub) ->
            val r = sel.firstOrNull { it.startsWith("$value:") }?.substringAfter(":")?.toIntOrNull() ?: 3
            RatingRow(label, sub, r) { n -> answers.value = answers.value + (q.key to (sel.filterNot { it.startsWith("$value:") } + "$value:$n")) }
        } else q.options.forEach { (value, label, sub) ->
            val on = value in sel
            val full = q.multi && q.max != null && !on && sel.size >= q.max
            Box(Modifier.then(if (full) Modifier.clickable(enabled = false) {} else Modifier)) {
                OptionRow(label, sub, on, q.multi) {
                    if (full) return@OptionRow
                    val next = if (q.multi) (if (on) sel - value else sel + value) else listOf(value)
                    answers.value = answers.value + (q.key to next)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val last = next == null
        PrimaryButton(
            when { last -> "Tengok plan awal"; q.multi && !q.rating && sel.isEmpty() && !q.required -> "Tiada, seterusnya"; else -> "Seterusnya" },
            enabled = ready,
        ) {
            if (next != null) { step = next; return@PrimaryButton }
            val m = answers.value
            val a = PlannerAnswers(
                shape = m.getValue("shape"),
                ratings = m.getValue("priority").associate { it.substringBefore(":") to it.substringAfter(":").toInt() },
                priority = m.getValue("priority").filter { it.endsWith(":5") }.map { it.substringBefore(":") }, body = m.getValue("body").first(),
                level = m.getValue("level").first().toInt(),
                pull = if ("bar" in m.getValue("equip")) m.getValue("pull").first().toInt() else -1,
                days = m.getValue("days").first().toInt(), time = m.getValue("time").first().toInt(),
                equip = m.getValue("equip"), injury = m.getValue("injury") + listOfNotNull("impact".takeIf { my.sado.app.data.Progress.lowImpact(Store.state.value.profile) }), intensity = m.getValue("intensity").first().toInt(),
            )
            review = a
        }
    }
}

@Composable
fun ExerciseRow(id: String, dose: String, onClick: () -> Unit) {
    val m = my.sado.app.data.Catalog.get(id)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SadoColors.Surface2).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.name, color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text("$dose · " + m.muscles.take(2).joinToString(", ") { my.sado.app.data.Catalog.muscleNames.getValue(it) }, color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        Text("›", color = SadoColors.TextMuted, fontSize = 20.sp)
    }
}
