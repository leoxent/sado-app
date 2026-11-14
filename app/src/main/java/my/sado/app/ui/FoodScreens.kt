package my.sado.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.graphics.graphicsLayer
import my.sado.app.data.MealTime
import my.sado.app.data.MealWhen
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import my.sado.app.data.FoodAi
import my.sado.app.data.FoodItem
import my.sado.app.data.Meal
import my.sado.app.data.MyFood
import my.sado.app.data.Profile
import my.sado.app.data.Secrets
import my.sado.app.data.Store
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.Executors
import kotlin.math.roundToInt

// Typical values, marked "Anggaran" in the log; scanned labels and saved foods replace them over time.
private val presets = listOf(
    FoodItem("Telur", "1 biji", 70, 80, 6f, 0.5f, 5f, "Anggaran"),
    FoodItem("Dada ayam", "100 g", 160, 180, 31f, 0f, 4f, "Anggaran"),
    FoodItem("Susu", "1 gelas 250ml", 120, 150, 8f, 12f, 5f, "Anggaran"),
    FoodItem("Tempe", "100 g", 190, 200, 19f, 9f, 11f, "Anggaran"),
    FoodItem("Ikan", "100 g", 110, 140, 22f, 0f, 3f, "Anggaran"),
    FoodItem("Tauhu", "100 g", 70, 80, 8f, 2f, 4f, "Anggaran"),
    FoodItem("Nasi putih", "1 senduk", 120, 140, 2.5f, 28f, 0.3f, "Anggaran"),
    FoodItem("Whey", "1 scoop", 110, 130, 24f, 3f, 2f, "Anggaran"),
)

fun tdee(p: Profile?): Int? = p?.let { my.sado.app.data.Nutrition.maintenance(it) }

private fun scaled(f: FoodItem, k: Float) = f.copy(
    portion = my.sado.app.data.Portion.times(f.portion, k),
    kcalLow = (f.kcalLow * k).roundToInt(), kcalHigh = (f.kcalHigh * k).roundToInt(),
    protein = f.protein * k, carbs = f.carbs * k, fat = f.fat * k,
)

private fun addMeal(items: List<FoodItem>, at: MealWhen = MealWhen()): Meal {
    val (day, minute) = MealTime.resolve(at)
    val m = Meal(day, minute, items)
    Store.update { it.copy(meals = it.meals + m) }
    Feedback.add()
    return m
}

// meal times named the way people say them, so the log reads like a day
private fun period(minute: Int) = when {
    minute < 11 * 60 -> "Sarapan"
    minute < 15 * 60 -> "Tengah hari"
    minute < 18 * 60 + 30 -> "Petang"
    else -> "Malam"
}

private fun kcalText(lo: Int, hi: Int) = if (lo == hi) "$lo" else "$lo–$hi"

@Composable
fun NutritionScreen(onBack: () -> Unit, onAi: () -> Unit, onBarcode: () -> Unit) {
    val s = Store.state.collectAsState().value
    val today = LocalDate.now().toEpochDay()
    // which day the screen shows: today, yesterday or the day before
    var dayBack by remember { mutableStateOf(0) }
    val dayName = MealTime.DAYS[dayBack]
    val dayLower = dayName.lowercase()
    val meals = s.meals.filter { it.epochDay == today - dayBack }
    // where one-tap adds go; back to "now" every time the screen opens
    var quickWhen by remember { mutableStateOf(MealWhen()) }
    var pickQuick by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Meal?>(null) }
    val items = meals.flatMap { it.items }
    val protein = items.sumOf { it.protein.toDouble() }.roundToInt()
    val carbs = items.sumOf { it.carbs.toDouble() }.roundToInt()
    val fat = items.sumOf { it.fat.toDouble() }.roundToInt()
    // targets from height, weight and the body goal; without a profile, a plain 1.6 g/kg on a typical weight
    val plan = s.profile?.let { my.sado.app.data.Nutrition.targets(s, it) }
    val target = plan?.protein ?: ((s.profile?.weightKg ?: 65f) * 1.6f).roundToInt()
    val lo = items.sumOf { it.kcalLow }
    val hi = items.sumOf { it.kcalHigh }
    val goal = plan?.kcal
    // the last add or delete, kept a few seconds so a mis-tap can be taken back
    var undo by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    LaunchedEffect(undo) { if (undo != null) { delay(4000); undo = null } }
    fun added(name: String, m: Meal) { undo = "$name masuk log${MealTime.toastSuffix(m.epochDay, m.minute)}" to { Store.update { st -> st.copy(meals = st.meals - m) } } }
    // swap one meal for its edited copy (null = gone); undo swaps it back
    fun swap(old: Meal, new: Meal?) = Store.update { st ->
        val i = st.meals.indexOf(old)
        if (i < 0) st else st.copy(meals = st.meals.toMutableList().apply { if (new == null || new.items.isEmpty()) removeAt(i) else set(i, new) })
    }
    var editing by remember { mutableStateOf<Pair<Meal, Int>?>(null) }
    editing?.let { (m, i) ->
        m.items.getOrNull(i)?.let { f ->
            FoodEditDialog(
                f,
                onSave = { nf ->
                    val nm = m.copy(items = m.items.toMutableList().apply { set(i, nf) })
                    swap(m, nm); editing = null; Feedback.success()
                    undo = "${nf.name} dikemas kini" to { swap(nm, m) }
                },
                onDelete = {
                    val nm = m.copy(items = m.items.filterIndexed { j, _ -> j != i })
                    swap(m, nm); editing = null; Feedback.tap()
                    undo = "${f.name} dibuang" to {
                        if (nm.items.isEmpty()) Store.update { st -> st.copy(meals = st.meals + m) } else swap(nm, m)
                    }
                },
            ) { editing = null }
        }
    }
    if (pickQuick) MealWhenDialog("Masuk ke bila?", "Makanan yang kau tekan lepas ni masuk ke masa ni. Keluar skrin, dia balik ke Sekarang.", quickWhen, "Guna masa ni",
        onConfirm = { quickWhen = it; pickQuick = false }, onDismiss = { pickQuick = false })
    moving?.let { m ->
        MealWhenDialog("Tukar masa makan", "Pindah ke hari atau jam lain. Paling jauh kelmarin.", MealTime.of(m.epochDay, m.minute) ?: MealWhen(), "Pindah",
            onConfirm = { w ->
                val (d, min) = MealTime.resolve(w)
                val nm = m.copy(epochDay = d, minute = min)
                swap(m, nm); moving = null; Feedback.success()
                undo = "Dipindah ke ${MealTime.DAYS[(today - d).toInt()].lowercase()} ${MealTime.clock(min)}" to { swap(nm, m) }
            }, onDismiss = { moving = null })
    }
    var n = 0

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Title("Makan $dayLower", plan?.let { "${it.mode} · ${it.kcal} kcal · protein ${it.protein}g" } ?: "Target protein ${target}g · 1.6 g × 65 kg", onBack = onBack)

            Row(Modifier.appear(n++), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealTime.DAYS.forEachIndexed { i, d -> WhenChip(d, dayBack == i) { dayBack = i } }
            }

            Card(Modifier.appear(n++), padding = 20) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    val f by animateFloatAsState((protein.toFloat() / target).coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), label = "protein")
                    Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val w = 14.dp.toPx()
                            drawArc(SadoColors.Track, 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                            drawArc(SadoColors.Accent, -90f, 360f * f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${protein}g", color = SadoColors.Text, fontSize = 28.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                            Text("dari ${target}g", color = SadoColors.TextMuted, fontSize = 12.sp)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Label("Protein")
                        Text(
                            when { protein >= target -> "Cukup! Otot tengah membesar."; target - protein <= 25 -> "Lagi ${target - protein}g. 2 telur + susu dah lepas."; else -> "Lagi ${target - protein}g nak sampai target." },
                            color = if (protein >= target) SadoColors.Accent else SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 21.sp,
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(SadoColors.Outline))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Label("Kalori")
                        // a range, not one number: estimates run low on oily, santan-heavy food
                        Text("${kcalText(lo, hi)} kcal", color = SadoColors.Text, fontSize = 22.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    }
                    if (goal != null) Text("target $goal", color = SadoColors.TextMuted, fontSize = 13.sp)
                }
                if (goal != null) RangeBar(lo / goal.toFloat(), hi / goal.toFloat())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Macro("Protein", protein, SadoColors.Accent, Modifier.weight(1f), plan?.protein)
                    Macro("Karbo", carbs, SadoColors.Sky, Modifier.weight(1f), plan?.carbs)
                    Macro("Lemak", fat, SadoColors.Streak, Modifier.weight(1f), plan?.fat)
                }
            }
            DailyTargets(plan, s.answers, Modifier.appear(n++))

            Row(Modifier.appear(n++), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.settings.foodAi) LogWay("Tulis / snap", "AI kira kalori", Icons.Filled.Edit, SadoColors.Accent, Modifier.weight(1f), onAi)
                LogWay("Scan barcode", "Makanan berbungkus", SadoIcons.Camera, SadoColors.Sky, Modifier.weight(1f), onBarcode)
            }

            // where the one-tap foods below go: now unless picked otherwise
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (quickWhen.isNow) SadoColors.Surface else SadoColors.Accent.copy(alpha = 0.14f))
                    .border(1.dp, if (quickWhen.isNow) SadoColors.Outline else SadoColors.Accent, RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { Feedback.tap(); pickQuick = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Tekan makanan masuk ke", color = SadoColors.TextMuted, fontSize = 12.sp)
                    Text(MealTime.label(quickWhen), color = if (quickWhen.isNow) SadoColors.Text else SadoColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Tukar masa", tint = SadoColors.TextMuted)
            }

            if (s.myFoods.isNotEmpty()) {
                Label("Makanan aku · tekan untuk tambah")
                s.myFoods.forEach { mf ->
                    val p = mf.items.sumOf { it.protein.toDouble() }.roundToInt()
                    Row(
                        Modifier.fillMaxWidth().pressable { added(mf.name, addMeal(mf.items, quickWhen)) }.clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface)
                            .border(1.dp, SadoColors.Outline, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FoodIcon(mf.items.firstOrNull()?.let { my.sado.app.data.FoodIcons.of(it) } ?: my.sado.app.data.FoodIcons.FALLBACK, 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(mf.name, color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Text("${mf.items.size} item · ~${mf.items.sumOf { it.kcalHigh }} kcal", color = SadoColors.TextMuted, fontSize = 13.sp)
                        }
                        Tag("+${p}g")
                        AddDot()
                    }
                }
            }

            // folds away and stays folded (a setting) until opened again
            val quickOpen = s.settings.quickFoodsOpen
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClickLabel = if (quickOpen) "Tutup" else "Buka") {
                    Feedback.fold(!quickOpen)
                    Store.update { it.copy(settings = it.settings.copy(quickFoodsOpen = !quickOpen)) }
                }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { Label(if (quickOpen) "Tambah cepat · anggaran" else "Tambah cepat · ${presets.size} makanan") }
                val turn by animateFloatAsState(if (quickOpen) 180f else 0f, label = "fold")
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = SadoColors.TextMuted, modifier = Modifier.graphicsLayer { rotationZ = turn })
            }
            AnimatedVisibility(quickOpen) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            presets.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { f ->
                        Column(
                            Modifier.weight(1f).pressable { added(f.name, addMeal(listOf(f), quickWhen)) }.clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface)
                                .border(1.dp, SadoColors.Outline, RoundedCornerShape(20.dp)).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                FoodIcon(f, 52.dp)
                                Box(Modifier.weight(1f))
                                AddDot()
                            }
                            Column {
                                Text(f.name, color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                                Text(f.portion, color = SadoColors.TextMuted, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Tag("${f.protein.roundToInt()}g P")
                                Text("~${f.kcalHigh} kcal", color = SadoColors.TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            } }

            Label("Log $dayLower")
            if (meals.isNotEmpty()) Text("Tekan makanan untuk edit. Tekan jam untuk pindah ke masa lain.", color = SadoColors.TextMuted, fontSize = 12.sp)
            if (meals.isEmpty()) Card {
                Text(if (dayBack == 0) "Belum ada apa-apa lagi" else "Takde log $dayLower", color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (dayBack == 0) "Tekan makanan kat atas, atau tulis je apa kau makan. Satu tekan, terus masuk."
                    else "Terlupa log? Tukar masa \"masuk ke\" kat atas ke $dayLower, lepas tu tekan makanan.",
                    color = SadoColors.TextMuted, fontSize = 13.sp,
                )
            }
            meals.sortedBy { it.minute }.groupBy { period(it.minute) }.forEach { (name, group) ->
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        val g = group.flatMap { it.items }
                        Text("${kcalText(g.sumOf { it.kcalLow }, g.sumOf { it.kcalHigh })} kcal · ${g.sumOf { it.protein.toDouble() }.roundToInt()}g P", color = SadoColors.TextMuted, fontSize = 13.sp)
                    }
                    group.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                MealTime.clock(m.minute), color = SadoColors.Sky, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.width(52.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClickLabel = "Tukar masa") { Feedback.tap(); moving = m }.padding(vertical = 8.dp, horizontal = 4.dp),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                m.items.forEachIndexed { i, f ->
                                    // tap a line to change it; the bin takes out just this one item
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClickLabel = "Edit") { editing = m to i }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        FoodIcon(f, 40.dp)
                                        Column(Modifier.weight(1f)) {
                                            Text(f.name, color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Text(f.portion, color = SadoColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            Text("${kcalText(f.kcalLow, f.kcalHigh)} kcal · ${f.protein.roundToInt()}g protein", color = SadoColors.Text.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Box(
                                            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) {
                                                Feedback.tap()
                                                val nm = m.copy(items = m.items.filterIndexed { j, _ -> j != i })
                                                swap(m, nm)
                                                undo = "${f.name} dibuang" to {
                                                    if (nm.items.isEmpty()) Store.update { st -> st.copy(meals = st.meals + m) } else swap(nm, m)
                                                }
                                            },
                                            contentAlignment = Alignment.Center,
                                        ) { Icon(Icons.Filled.Delete, contentDescription = "Buang ${f.name}", tint = SadoColors.TextMuted, modifier = Modifier.size(20.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(undo != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            val u = undo
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SadoColors.Surface2)
                    .border(1.dp, SadoColors.Outline, RoundedCornerShape(18.dp)).padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Check, null, tint = SadoColors.Accent, modifier = Modifier.size(20.dp))
                Text(u?.first ?: "", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1)
                Text(
                    "UNDO", color = SadoColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clickable(role = Role.Button) { u?.second?.invoke(); undo = null }.padding(horizontal = 18.dp, vertical = 16.dp),
                )
            }
        }
    }
}

// calories eaten against the maintenance number: solid to the low estimate, faded to the high one
@Composable
private fun RangeBar(lo: Float, hi: Float) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(10.dp)).background(SadoColors.Track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(hi.coerceIn(0f, 1f)).clip(RoundedCornerShape(10.dp)).background(SadoColors.Streak.copy(alpha = 0.35f)))
        Box(Modifier.fillMaxHeight().fillMaxWidth(lo.coerceIn(0f, 1f)).clip(RoundedCornerShape(10.dp)).background(SadoColors.Streak))
    }
}

@Composable
private fun Macro(name: String, grams: Int, color: Color, modifier: Modifier, target: Int? = null) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${grams}g", color = color, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            if (target != null) Text(" /${target}", color = SadoColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
        Text(name, color = SadoColors.TextMuted, fontSize = 12.sp)
    }
}

// what to eat a day for the body goal, with the reasoning one tap away
@Composable
private fun DailyTargets(t: my.sado.app.data.Nutrition.Targets?, a: my.sado.app.data.PlannerAnswers?, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Card(modifier) {
        if (t == null) {
            Text("Target harian", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text("Isi tinggi, berat dan umur dalam profil, nanti kalori dan protein dikira ikut badan dan goal kau.", color = SadoColors.TextMuted, fontSize = 13.sp)
            return@Card
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Target harian kau", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Text(a?.shape?.joinToString(" + ") { my.sado.app.data.PlanBuilder.shapeLabels[it] ?: it }?.ifBlank { null } ?: "Ikut badan kau", color = SadoColors.TextMuted, fontSize = 13.sp)
            }
            Tag(t.mode, SadoColors.Accent)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Kalori", "${t.kcal}", SadoColors.Text), Triple("Protein", "${t.protein}g", SadoColors.Accent),
                Triple("Karbo", "${t.carbs}g", SadoColors.Sky), Triple("Lemak", "${t.fat}g", SadoColors.Streak)).forEach { (n, v, c) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(SadoColors.Surface2).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v, color = c, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    Text(n, color = SadoColors.TextMuted, fontSize = 12.sp)
                }
            }
        }
        Text("Jangkaan: ${t.weekly}. Kekal berat ~${t.maintain} kcal.", color = SadoColors.TextMuted, fontSize = 13.sp)
        Text(
            if (open) "Tutup" else "Kenapa nombor ni?", color = SadoColors.Sky, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(vertical = 10.dp),
        )
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                t.why.forEach { Text("• $it", color = SadoColors.Text, fontSize = 13.sp, lineHeight = 19.sp) }
            }
        }
    }
}

// one of the two ways to log something that isn't in the quick list
@Composable
private fun LogWay(title: String, sub: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.pressable(onClick = onClick).clip(RoundedCornerShape(22.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = SadoColors.OnAccent, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(title, color = SadoColors.Text, fontSize = 16.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(sub, color = SadoColors.TextMuted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun AddDot() = Box(Modifier.size(28.dp).clip(CircleShape).background(SadoColors.Accent), contentAlignment = Alignment.Center) {
    Icon(Icons.Filled.Add, contentDescription = null, tint = SadoColors.OnAccent, modifier = Modifier.size(18.dp))
}

@Composable
private fun ConfirmItems(items: MutableList<Pair<FoodItem, Float>>, defaultName: String, onSave: () -> Unit) {
    var save by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(defaultName) }
    var at by remember { mutableStateOf(MealWhen()) }
    var editing by remember { mutableStateOf<Int?>(null) }
    editing?.let { i ->
        items.getOrNull(i)?.let { (f, k) ->
            FoodEditDialog(scaled(f, k), onSave = { nf -> items[i] = nf to 1f; editing = null }, onDelete = { items.removeAt(i); editing = null }) { editing = null }
        }
    }
    Label("Sahkan · betulkan dulu")
    Text("Tekan item untuk tukar nama, kuantiti, kalori, protein atau gambar. Tong sampah buang item yang AI salah teka.", color = SadoColors.TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
    items.forEachIndexed { i, (f, k) ->
        val sf = scaled(f, k)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface).clickable(role = Role.Button, onClickLabel = "Edit") { editing = i }.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FoodIcon(sf, 44.dp)
            Column(Modifier.weight(1f)) {
                Text(sf.name, color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text("${sf.portion} · P ${sf.protein.roundToInt()}g · K ${sf.carbs.roundToInt()}g · L ${sf.fat.roundToInt()}g", color = SadoColors.TextMuted, fontSize = 12.sp)
                Text(sf.source, color = if (sf.source.contains("Rendah")) SadoColors.Streak else SadoColors.Accent, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (sf.kcalLow == sf.kcalHigh) "${sf.kcalHigh}" else "${sf.kcalLow}–${sf.kcalHigh}", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Row {
                    Text("−", color = SadoColors.Text, fontSize = 22.sp, modifier = Modifier.clickable { items[i] = f to (k - 0.5f).coerceAtLeast(0.5f) }.padding(horizontal = 10.dp))
                    Text("+", color = SadoColors.Text, fontSize = 22.sp, modifier = Modifier.clickable { items[i] = f to (k + 0.5f) }.padding(horizontal = 10.dp))
                }
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { Feedback.tap(); items.removeAt(i) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Delete, contentDescription = "Buang ${sf.name}", tint = SadoColors.TextMuted, modifier = Modifier.size(20.dp)) }
        }
    }
    val all = items.map { (f, k) -> scaled(f, k) }
    Text("Jumlah: ${all.sumOf { it.kcalLow }}–${all.sumOf { it.kcalHigh }} kcal · ${all.sumOf { it.protein.toDouble() }.roundToInt()}g protein", color = SadoColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(save, { save = it }, colors = CheckboxDefaults.colors(checkedColor = SadoColors.Accent, checkmarkColor = SadoColors.Bg))
        Text("Simpan makanan ni? Lepas ni pilih je.", color = SadoColors.Text, fontSize = 13.sp)
    }
    if (save) OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nama dalam Makanan aku") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
    Label("Bila makan?")
    MealWhenPicker(at) { at = it }
    PrimaryButton(if (save) "Simpan & ingat" else if (at.isNow) "Simpan ke log" else "Simpan · ${MealTime.label(at)}", enabled = items.isNotEmpty()) {
        addMeal(all, at)
        if (save && name.isNotBlank()) Store.update { it.copy(myFoods = it.myFoods + MyFood(name.trim(), all)) }
        onSave()
    }
}

@Composable
fun AiFoodScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(Secrets.load(context, "anthropic")) }
    var keyInput by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val items = remember { mutableStateListOf<Pair<FoodItem, Float>>() }
    // the food photo lives only in memory until Claude answers; nothing is written to the app's storage
    var photo by remember { mutableStateOf<FoodPhoto?>(null) }
    val camFile = remember { java.io.File(context.cacheDir, "foodcam/snap.jpg").also { it.parentFile?.mkdirs() } }
    val camUri = remember { androidx.core.content.FileProvider.getUriForFile(context, "my.sado.app.files", camFile) }
    val snap = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { photo = withContext(Dispatchers.IO) { FoodPhoto.read(context, camUri) }; camFile.delete() }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { photo = withContext(Dispatchers.IO) { FoodPhoto.read(context, uri) } }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Log makan AI", "Claude Haiku · teks dan gambar makanan dihantar ke Anthropic", onBack = onBack)
        if (key == null) {
            Card {
                Text("Perlu Claude API key", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text("Dapatkan dari console.anthropic.com, dan set had belanja bulanan (contoh \$5). Key disimpan encrypted dalam phone dan tak masuk backup.", color = SadoColors.TextMuted, fontSize = 13.sp)
                OutlinedTextField(keyInput, { keyInput = it.trim() }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                PrimaryButton("Simpan key", enabled = keyInput.length > 20) { Secrets.save(context, "anthropic", keyInput); key = keyInput; keyInput = "" }
            }
            return@Column
        }
        if (items.isEmpty()) {
            val p = photo
            if (p == null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LogWay("Snap makanan", "Guna camera", SadoIcons.Camera, SadoColors.Sky, Modifier.weight(1f)) { snap.launch(camUri) }
                LogWay("Dari galeri", "Pilih gambar", Icons.Filled.Add, SadoColors.Streak, Modifier.weight(1f)) {
                    pick.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            } else Box(Modifier.fillMaxWidth()) {
                androidx.compose.foundation.Image(p.preview, contentDescription = "Gambar makanan", Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Text(
                    "Buang", color = SadoColors.Text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xCC0E0F12))
                        .clickable(role = Role.Button) { photo = null }.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            if (p != null) Text("Gambar ni dihantar ke Anthropic (Claude) untuk dikira je, dan tak disimpan dalam app. Gambar badan tak pernah dihantar.", color = SadoColors.TextMuted, fontSize = 12.sp)
            OutlinedTextField(
                text, { text = it.take(400) }, label = { Text(if (photo != null) "Tambah keterangan (tak wajib)" else "Cerita kau makan apa") },
                placeholder = { Text("nasi lemak telur goreng, sambal lebih, teh tarik kurang manis") },
                minLines = 3, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
            )
            Text(if (photo != null) "Contoh: nasi 2 senduk, sambal sotong 1 sudu. Teks kau menang kalau lain dengan gambar." else "Tulis macam cakap biasa. BM, English, campur pun boleh.", color = SadoColors.TextMuted, fontSize = 12.sp)
            MeasureGuide()
            if (error != null) Text(error!!, color = SadoColors.Danger, fontSize = 13.sp)
            if (loading) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(my.sado.app.R.drawable.face_think), null, Modifier.height(140.dp))
                ThinkingLine()
            }
            PrimaryButton(if (loading) "Tengah kira..." else "Kira dengan AI", enabled = (text.isNotBlank() || photo != null) && !loading) {
                loading = true; error = null
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { FoodAi.parse(key!!, text, photo?.jpeg) } }
                        .onSuccess { r -> items.addAll(r.map { it to 1f }); if (r.isEmpty()) error = "AI tak jumpa makanan dalam ayat atau gambar tu." else photo = null }
                        .onFailure { e -> error = "Tak berjaya: " + (e.message ?: e.javaClass.simpleName).take(160) }
                    loading = false
                }
            }
            Text("Tukar API key", color = SadoColors.TextMuted, fontSize = 12.sp, modifier = Modifier.clickable { Secrets.clear(context, "anthropic"); key = null })
        } else {
            ConfirmItems(items, text.take(30)) { onBack() }
            Text("Ubah ayat", color = SadoColors.Accent, fontSize = 13.sp, modifier = Modifier.clickable { items.clear() })
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

// what the AI is "doing" while it counts: a new line every couple of seconds, never the same one twice in a row
internal val THINKING = listOf(
    "Membaca nutrisi sajian kau…",
    "Judging kalori intake kau…",
    "Kira berapa senduk nasi sebenarnya…",
    "Sambal tu pun dikira, jangan risau…",
    "Mengintai minyak yang menyorok…",
    "Menimbang protein dengan mata kasar…",
    "Teh tarik kurang manis? Kita tengok…",
    "Bertanya nenek pasal santan…",
    "Mengira butir nasi satu per satu…",
    "Haiku pun dah mula lapar…",
    "Memastikan ayam tu bukan goreng tepung…",
    "Scan lemak tersembunyi…",
    "Rundingan dengan kuah kari…",
    "Mengesahkan 'sikit je' tu betul-betul sikit…",
)

@Composable
private fun ThinkingLine() {
    var i by remember { mutableStateOf(kotlin.random.Random.nextInt(THINKING.size)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1800)
            i = (i + 1 + kotlin.random.Random.nextInt(THINKING.size - 1)) % THINKING.size
        }
    }
    androidx.compose.animation.AnimatedContent(i, label = "thinking") { k ->
        Text(THINKING[k], color = SadoColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// Open Food Facts lookup; returns null when the product isn't in the database
private fun lookup(code: String): FoodItem? {
    val c = URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,brands,nutriments,serving_size").openConnection() as HttpURLConnection
    c.setRequestProperty("User-Agent", "Sado/0.4 (personal Android app)")
    c.connectTimeout = 10000; c.readTimeout = 10000
    if (c.responseCode != 200) return null
    val root = json.parseToJsonElement(c.inputStream.bufferedReader().use { it.readText() }).jsonObject
    val p = root["product"]?.jsonObject ?: return null
    val n = p["nutriments"]?.jsonObject ?: JsonObject(emptyMap())
    fun num(k: String) = n[k]?.jsonPrimitive?.doubleOrNull
    val serving = p["serving_size"]?.jsonPrimitive?.content
    val perServing = num("energy-kcal_serving") != null
    val sfx = if (perServing) "_serving" else "_100g"
    val kcal = num("energy-kcal$sfx")?.roundToInt() ?: return null
    val name = listOfNotNull(p["brands"]?.jsonPrimitive?.content?.substringBefore(","), p["product_name"]?.jsonPrimitive?.content).joinToString(" ").ifBlank { "Produk $code" }
    return FoodItem(name, if (perServing) (serving ?: "1 hidangan") else "100 g", kcal, kcal,
        (num("proteins$sfx") ?: 0.0).toFloat(), (num("carbohydrates$sfx") ?: 0.0).toFloat(), (num("fat$sfx") ?: 0.0).toFloat(), "Barcode")
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun BarcodeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    var code by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Halakan ke barcode") }
    val items = remember { mutableStateListOf<Pair<FoodItem, Float>>() }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(granted, code) {
        val future = ProcessCameraProvider.getInstance(context)
        val exec = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient()
        future.addListener({
            if (!granted || code != null) return@addListener
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(exec) { proxy ->
                val media = proxy.image
                if (media == null || code != null) { proxy.close(); return@setAnalyzer }
                scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { list -> list.firstOrNull { it.rawValue?.all(Char::isDigit) == true }?.rawValue?.let { if (code == null) code = it } }
                    .addOnCompleteListener { proxy.close() }
            }
            provider.unbindAll()
            runCatching { provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { future.get().unbindAll() }; exec.shutdown() }
    }

    LaunchedEffect(code) {
        val c = code ?: return@LaunchedEffect
        Feedback.success()
        status = "Cari $c dalam Open Food Facts..."
        val f = runCatching { withContext(Dispatchers.IO) { lookup(c) } }.getOrNull()
        if (f != null) { items.add(f to 1f); status = "Jumpa" } else status = "Tak jumpa $c. Cuba Log dengan AI dan tulis nama penuh + angka dari label."
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Scan barcode", "Data: Open Food Facts", onBack = onBack)
        if (code == null) {
            Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
                if (granted) AndroidView({ previewView }, Modifier.fillMaxSize())
            }
        }
        Text(status, color = SadoColors.TextMuted, fontSize = 13.sp)
        if (items.isNotEmpty()) ConfirmItems(items, items.first().first.name.take(30)) { onBack() }
        if (code != null) SecondaryButton("Scan lagi", Modifier.fillMaxWidth()) { items.clear(); code = null; status = "Halakan ke barcode" }
    }
}
