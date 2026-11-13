package my.sado.app.ui

import my.sado.app.data.PlannedExercise
import my.sado.app.data.AppState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.sado.app.data.Catalog
import my.sado.app.data.GearToday
import androidx.compose.animation.core.animateFloat
import my.sado.app.data.PlanBuilder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import my.sado.app.data.Progress
import my.sado.app.data.Store

// what each muscle does and why it matters, in one line for the preview
private val muscleWhy = mapOf(
    "chest" to "Tolak benda dari badan. Dada tebal, baju nampak penuh.",
    "triceps" to "Luruskan siku. Dua pertiga saiz lengan atas.",
    "delts" to "Angkat lengan. Bahu lebar buat badan nampak V.",
    "lats" to "Tarik. Belakang lebar, pinggang nampak kecil.",
    "biceps" to "Bengkokkan siku. Otot flex depan lengan.",
    "forearms" to "Genggam. Grip kuat, tak cepat lepas.",
    "traps" to "Angkat bahu. Leher dan atas belakang tegap.",
    "quads" to "Luruskan lutut. Kuasa untuk naik tangga dan lompat.",
    "glutes" to "Tolak pinggul. Otot paling besar, lindung pinggang.",
    "hamstrings" to "Bengkokkan lutut. Imbang peha depan, kurang cedera.",
    "calves" to "Jengket. Stabil masa lari dan lompat.",
    "abs" to "Bengkokkan badan. Teras kuat, perut lebih rata.",
    "obliques" to "Pusing badan. Pinggang kemas, teras stabil.",
    "lowerback" to "Tegakkan badan. Postur lurus, kurang sakit pinggang.",
)

/** Everything in the session before it starts: moves with sets and reps, and which muscles work. */
@Composable
fun WorkoutPreviewScreen(source: String, onBack: () -> Unit, onExercise: (String) -> Unit, onStart: () -> Unit) {
    val state = Store.state.collectAsState().value
    val st = state.settings
    // the sleep answer below can take a set off every move, so the list follows it
    val (title, plan) = remember(source, state.sleep, state.boosts, state.extraSets, state.myTemplates, state.gearToday) { sessionFor(source) }
    // gear for today: the quiz's by default; a change waits a moment (more taps restart it), then the session is redone
    val quizGear = state.answers?.let { PlanBuilder.gearOf(it) }.orEmpty()
    val today = gearToday(state)
    val original = remember(source, state.sleep, state.boosts, state.extraSets, state.myTemplates) { sessionFor(source, quizGear = true).second }
    var pending by remember { mutableStateOf<List<String>?>(null) }
    var working by remember { mutableStateOf(false) }
    var askWatch by remember { mutableStateOf(false) }
    // the watch reminder: start the activity there first, so its heart rate covers this session
    my.sado.app.data.Watch.brand(st.watch)?.let { b ->
        if (askWatch) WatchStartDialog(
            b, onGo = { askWatch = false; onStart() }, onSkip = { askWatch = false; onStart() },
            onNever = { askWatch = false; Store.update { it.copy(settings = it.settings.copy(remindWatchStart = false)) }; onStart() },
        )
    }
    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        working = true
        kotlinx.coroutines.delay(1500)
        Store.update { it.copy(gearToday = if (p.toSet() == quizGear.toSet()) null else GearToday(java.time.LocalDate.now().toEpochDay(), p)) }
        Feedback.success()
        pending = null
        working = false
    }
    var editing by remember { mutableStateOf<String?>(null) }
    val sets = plan.sumOf { it.sets }
    val minutes = (sets * (1 + st.restSeconds / 60.0)).toInt() + if (st.warmup) 1 else 0
    val moves = plan.map { it to Catalog.get(it.id) }
    // hard sets per muscle: the moves that train it as a main mover
    val worked = moves.flatMap { (e, m) -> m.muscles.map { it to e.sets } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        .entries.sortedByDescending { it.value }
    val support = moves.flatMap { it.second.secondary }.distinct().filter { m -> worked.none { it.key == m } }
    // sets per muscle for the body map, helpers counting half (the same rule as the plan screen)
    val heat = Heat.sets(plan.map { it.id to it.sets })

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Title(title, "${plan.size} move · $sets set · lebih kurang $minutes minit", onBack = onBack)
            if (quizGear.isNotEmpty()) GearCard(
                quizGear, pending ?: today ?: quizGear, changed = (pending ?: today) != null,
                onToggle = { g -> Feedback.tap(); val cur = pending ?: today ?: quizGear; pending = if (g in cur) cur - g else cur + g },
                onReset = { pending = quizGear },
            )
            if (working) Recalculating() else {

            Card {
                Text("Otot yang kerja", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MuscleHeat(heat, week = false, back = false, modifier = Modifier.size(width = 90.dp, height = 192.dp))
                        Text("Depan", color = SadoColors.TextMuted, fontSize = 13.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MuscleHeat(heat, week = false, back = true, modifier = Modifier.size(width = 90.dp, height = 192.dp))
                        Text("Belakang", color = SadoColors.TextMuted, fontSize = 13.sp)
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HeatLegend(week = false) }
            }

            Label("Senarai move")
            if (st.warmup) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Step("0", SadoColors.Sky)
                Column(Modifier.weight(1f)) {
                    Text("Warm-up", color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Jumping jack 1 minit", color = SadoColors.TextMuted, fontSize = 13.sp)
                }
            }
            moves.forEachIndexed { i, (e, m) ->
                val open = editing == e.id
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface)
                        .border(if (open) 2.dp else 0.dp, if (open) SadoColors.Accent else Color.Transparent, RoundedCornerShape(20.dp)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().pressable { onExercise(e.id) }.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Step("${i + 1}", SadoColors.Accent)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(m.name, color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${e.sets} set × ${e.reps}", color = SadoColors.Accent, fontSize = 14.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                                changes(e, state).forEach { (t, up) -> Tag(t, if (up) SadoColors.Accent else SadoColors.Streak) }
                            }
                            Text(
                                m.muscles.joinToString(" · ") { Catalog.muscleNames.getValue(it) },
                                color = SadoColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            // a stand-in for a move whose gear is not here today
                            if (today != null) original.getOrNull(i)?.takeIf { it.id != e.id }?.let {
                                Text("Ganti " + Catalog.get(it.id).name, color = SadoColors.Sky, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        // edit opens the set and rep steppers under the row; the rest of the row opens the move's guide
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(if (open) SadoColors.Accent else SadoColors.Surface2)
                                .clickable(role = Role.Button) { editing = if (open) null else e.id },
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (open) Icons.Filled.Check else Icons.Filled.Edit, contentDescription = if (open) "Siap ubah" else "Ubah set dan rep ${m.name}", tint = if (open) SadoColors.OnAccent else SadoColors.TextMuted, modifier = Modifier.size(20.dp)) }
                    }
                    AnimatedVisibility(open) {
                        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val d = (e.base ?: e.reps).let { Progress.dose(it) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Stepper("Set", "${e.sets}", Modifier.weight(1f), onMinus = { tweak(source, e, sets = -1) }, onPlus = { tweak(source, e, sets = 1) })
                                if (d != null) Stepper(if (d.timed) "Saat" else "Rep", e.reps, Modifier.weight(1f), onMinus = { tweak(source, e, reps = -1) }, onPlus = { tweak(source, e, reps = 1) })
                                else Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface2), contentAlignment = Alignment.Center) {
                                    Text(e.reps, color = SadoColors.TextMuted, fontSize = 13.sp)
                                }
                            }
                            goal(e)?.let { Text("Hala tuju: $it", color = SadoColors.TextMuted, fontSize = 12.sp) }
                            if (e.base != null || e.baseSets != null) Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Asal: ${e.baseSets ?: e.sets} set × ${e.base ?: e.reps}", color = SadoColors.TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (changes(e, state).isNotEmpty()) Text(
                                    "Reset ke asal", color = SadoColors.Streak, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { reset(e.id) }.padding(horizontal = 10.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (moves.any { changes(it.first, state).isNotEmpty() }) Text(
                "Tag +/− tunjuk beza dari plan asal: naik sebab jawapan Easy, atau ko ubah sendiri. Tekan pensel untuk ubah atau reset.",
                color = SadoColors.TextMuted, fontSize = 12.sp,
            )
            Card {
                Text("Apa otot ni buat", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                worked.forEach { (id, n) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Dot(Heat.color(heat[id] ?: 0f, week = false) ?: Heat.light)
                        Column(Modifier.weight(1f)) {
                            Text(Catalog.muscleNames.getValue(id), color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Text(muscleWhy[id] ?: "", color = SadoColors.TextMuted, fontSize = 13.sp)
                        }
                        Text((heat[id] ?: n.toFloat()).let { h -> if (h % 1f == 0f) "${h.toInt()} set" else "%.1f set".format(h) }, color = SadoColors.Accent, fontSize = 14.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    }
                }
                if (support.isNotEmpty()) Text(
                    "Sokongan: " + support.joinToString(", ") { Catalog.muscleNames.getValue(it) },
                    color = SadoColors.TextMuted, fontSize = 13.sp,
                )
            }
            }
            Card { SleepPicker() }
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            PrimaryButton("START WORKOUT", enabled = plan.isNotEmpty() && !working) {
                if (st.remindWatchStart && my.sado.app.data.Watch.brand(st.watch) != null) askWatch = true else onStart()
            }
        }
    }
}

// what today's dose differs from the plan's own, e.g. "+2 rep" (true = up) or "−1 set"
private fun changes(e: PlannedExercise, s: AppState): List<Pair<String, Boolean>> {
    val out = mutableListOf<Pair<String, Boolean>>()
    e.base?.let { Progress.dose(it) }?.let { d ->
        val diff = e.target - d.lo
        val u = if (d.timed) "s" else " rep"
        if (diff != 0) out += (if (diff > 0) "+$diff$u" else "−${-diff}$u") to (diff > 0)
    }
    if (e.baseSets != null) (s.extraSets[e.id] ?: 0).takeIf { it != 0 }?.let { out += (if (it > 0) "+$it set" else "−${-it} set") to (it > 0) }
    return out
}

// the user's own change before a workout: their own workouts change the template itself; plan and
// template moves keep the change as the move's target, the same number the effort answers move
private fun tweak(source: String, e: PlannedExercise, sets: Int = 0, reps: Int = 0) {
    Feedback.tap()
    if (source.startsWith("my:")) {
        val step = if (Catalog.get(e.id).timed) 5 else 1
        Store.update { s ->
            s.copy(myTemplates = s.myTemplates.map { t ->
                if ("my:${t.id}" != source) t
                else t.copy(moves = t.moves.map { mv -> if (mv.id != e.id) mv else mv.copy(sets = (mv.sets + sets).coerceIn(1, Progress.MAX_SETS), reps = (mv.reps + reps * step).coerceIn(1, 300)) })
            })
        }
        return
    }
    Store.update { s ->
        val d = e.base?.let { Progress.dose(it) }
        val boosts = if (d == null || reps == 0) s.boosts else ((s.boosts[e.id] ?: 0) + reps).coerceIn(Progress.stepRange(e.id, d))
            .let { if (it == 0) s.boosts - e.id else s.boosts + (e.id to it) }
        val base = e.baseSets ?: e.sets
        val extra = if (sets == 0) s.extraSets else ((s.extraSets[e.id] ?: 0) + sets).coerceIn(1 - base, Progress.MAX_SETS - base)
            .let { if (it == 0) s.extraSets - e.id else s.extraSets + (e.id to it) }
        s.copy(boosts = boosts, extraSets = extra)
    }
}

private fun reset(id: String) { Feedback.tap(); Store.update { it.copy(boosts = it.boosts - id, extraSets = it.extraSets - id) } }

// where the target is heading: "naik ke 12 → Decline push-up", or "max" once a move with no rung above tops out
private fun goal(e: PlannedExercise): String? {
    val d = e.base?.let { Progress.dose(it) } ?: return null
    val top = Progress.top(e.id, d)
    val unit = if (d.timed) "s" else ""
    if (e.target >= top && !Progress.climbs(e.id)) return "max"
    val next = Catalog.ladderOf(e.id)?.let { k -> Catalog.ladders.getValue(k).let { l -> l.getOrNull(l.indexOf(e.id) + 1) } }
    return if (Progress.climbs(e.id) && next != null) "naik ke $top$unit → ${Catalog.get(next).name}" else "naik ke $top$unit"
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) =
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))

@Composable
private fun Step(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = 15.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
    }
}

// the gear from the quiz, each one tappable to say it is not here today; the session is redone after a short pause
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GearCard(quiz: List<String>, shown: List<String>, changed: Boolean, onToggle: (String) -> Unit, onReset: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alat ada hari ni", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            if (changed) Text(
                "Reset", color = SadoColors.Sky, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onReset).padding(horizontal = 10.dp, vertical = 12.dp),
            )
        }
        Text("Kat luar? Tekan alat yang takde, sesi ni disusun semula. Workout lepas ni kembali ikut jawapan quiz.", color = SadoColors.TextMuted, fontSize = 12.sp)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quiz.forEach { g ->
                val on = g in shown
                Row(
                    Modifier.height(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (on) SadoColors.Accent.copy(alpha = 0.14f) else SadoColors.Surface2)
                        .border(1.dp, if (on) SadoColors.Accent else SadoColors.Outline, RoundedCornerShape(14.dp))
                        .clickable(role = Role.Checkbox) { onToggle(g) }
                        .semantics { contentDescription = Catalog.equipNames.getValue(g) + if (on) ", ada" else ", takde hari ni" }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(if (on) Icons.Filled.Check else Icons.Filled.Close, null, tint = if (on) SadoColors.Accent else SadoColors.TextMuted, modifier = Modifier.size(16.dp))
                    Text(
                        Catalog.equipNames.getValue(g).replaceFirstChar { it.uppercase() },
                        color = if (on) SadoColors.Text else SadoColors.TextMuted.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        textDecoration = if (on) null else androidx.compose.ui.text.style.TextDecoration.LineThrough,
                    )
                }
            }
        }
    }
}

// shown while the session is rebuilt: a spinner, the step it is on, and the move rows pulsing as placeholders
@Composable
internal fun Recalculating() {
    val steps = listOf("Semak alat yang ada…", "Cari move pengganti…", "Susun semula sesi…")
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(450); i = (i + 1).coerceAtMost(steps.lastIndex) } }
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse").animateFloat(
        0.35f, 0.8f, androidx.compose.animation.core.infiniteRepeatable<Float>(androidx.compose.animation.core.tween(600), androidx.compose.animation.core.RepeatMode.Reverse), label = "a",
    )
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            androidx.compose.material3.CircularProgressIndicator(color = SadoColors.Accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            Column {
                Text("Kira semula sesi", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                androidx.compose.animation.AnimatedContent(steps[i], label = "step") { Text(it, color = SadoColors.TextMuted, fontSize = 13.sp) }
            }
        }
    }
    repeat(4) {
        Box(Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface.copy(alpha = pulse)))
    }
}
