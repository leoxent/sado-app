package my.sado.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.sado.app.R
import my.sado.app.data.Catalog
import my.sado.app.data.LadderProgress
import my.sado.app.data.Plan
import my.sado.app.data.PlanBuilder
import my.sado.app.data.PlannedExercise
import my.sado.app.data.PlannerAnswers
import my.sado.app.data.Progress
import my.sado.app.data.Store

private val dayNames = listOf("Isnin", "Selasa", "Rabu", "Khamis", "Jumaat", "Sabtu", "Ahad")

// the planner's muscle groups and what each covers, for the last question
private val groupOptions = listOf(
    "chest" to "Dada", "side_delts" to "Bahu sisi", "front_delts" to "Bahu depan", "rear_delts" to "Bahu belakang", "lats" to "Belakang",
    "traps" to "Trapezius", "arms" to "Lengan", "abs" to "Perut", "legs" to "Kaki", "glutes" to "Punggung", "lower_back" to "Pinggang",
)
private val groupDetail = mapOf(
    "chest" to "Pectoral", "side_delts" to "Delt sisi, yang buat bahu nampak lebar", "front_delts" to "Delt depan, dah banyak kena masa push-up",
    "rear_delts" to "Delt belakang, bahu bulat dan postur", "lats" to "Lats, rahsia V-shape", "traps" to "Atas belakang dan leher",
    "lower_back" to "Otot pinggang belakang",
    "arms" to "Bisep, trisep dan lengan bawah", "abs" to "Six-pack dan sisi perut", "legs" to "Peha depan, peha belakang dan betis",
    "glutes" to "Glutes",
)
private val groupMuscleIds = mapOf(
    "chest" to setOf("chest"), "side_delts" to setOf("delts"), "front_delts" to setOf("delts"), "rear_delts" to setOf("delts"),
    "lats" to setOf("lats"), "traps" to setOf("traps"), "lower_back" to setOf("lowerback"), "arms" to setOf("biceps", "triceps", "forearms"),
    "abs" to setOf("abs", "obliques"), "legs" to setOf("quads", "hamstrings", "calves"), "glutes" to setOf("glutes"),
)

/** Primary muscles across a list of moves, and the supporting ones that are not already primary. */
private fun muscles(moves: List<PlannedExercise>): Pair<List<String>, List<String>> {
    val primary = moves.flatMap { Catalog.get(it.id).muscles }.distinct()
    val secondary = moves.flatMap { Catalog.get(it.id).secondary }.distinct() - primary.toSet()
    return primary to secondary
}

private fun names(ids: List<String>) = ids.joinToString(", ") { Catalog.muscleNames.getValue(it) }

private fun sessionSets(moves: List<PlannedExercise>) = Heat.sets(moves.map { it.id to it.sets })

// a week of the plan: each session's sets times the days it runs
private fun weekSets(plan: Plan, moves: (List<PlannedExercise>) -> List<PlannedExercise> = { it }): Map<String, Float> {
    val out = mutableMapOf<String, Float>()
    plan.allSessions.forEach { s ->
        val days = plan.daysOf(s).size
        sessionSets(moves(s.exercises)).forEach { (m, n) -> out[m] = (out[m] ?: 0f) + n * days }
    }
    return out
}

@Composable
internal fun BodyHeat(sets: Map<String, Float>, week: Boolean, height: Int, labels: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(false to "Depan", true to "Belakang").forEach { (back, label) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MuscleHeat(sets, week, back = back, modifier = Modifier.size(width = (height * 0.42f).dp, height = height.dp))
                if (labels) Text(label, color = SadoColors.TextMuted, fontSize = 12.sp)
            }
        }
    }
}

// muscles from most to fewest sets, each with its heat colour and count
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeatChips(sets: Map<String, Float>, week: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sets.entries.filter { it.value > 0f }.sortedByDescending { it.value }.forEach { (m, n) ->
            val c = Heat.color(n, week) ?: return@forEach
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(SadoColors.Surface2).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                Text(Catalog.muscleNames.getValue(m), color = SadoColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(setCount(n), color = SadoColors.TextMuted, fontSize = 12.sp)
            }
        }
    }
}

// whole sets as "12", half sets from helper muscles as "7.5"
private fun setCount(n: Float) = if (n % 1f == 0f) n.toInt().toString() else "%.1f".format(n)

@Composable
private fun FocusBar(plan: Plan) {
    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val shades = listOf(1f, 0.7f, 0.5f, 0.34f, 0.22f)
        plan.focus.forEachIndexed { i, f -> Box(Modifier.weight(f.pct.toFloat().coerceAtLeast(1f)).height(12.dp).background(SadoColors.Accent.copy(alpha = shades[i]))) }
    }
    Text(plan.focus.joinToString("  ·  ") { "${it.label} ${it.pct}%" }, color = SadoColors.TextMuted, fontSize = 13.sp)
}

/**
 * Last planner step: a first plan from every earlier answer, the muscles it trains on the body map, and a
 * chance to push more groups up. Each pick rebuilds the plan so the map and split change as you tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusStep(base: PlannerAnswers, ladders: Map<String, LadderProgress>, step: Int, total: Int, onBack: () -> Unit, onDone: (PlannerAnswers) -> Unit) {
    var ratings by remember { mutableStateOf(groupOptions.associate { (g, _) -> g to PlanBuilder.rating(base, g) }) }
    val a = base.copy(ratings = ratings, priority = ratings.filterValues { it == 5 }.keys.toList())
    val plan = remember(a) { PlanBuilder.build(a, PlanBuilder.startingLadders(a) + ladders) }
    val week = weekSets(plan)
    val sets = PlanBuilder.groupSets(plan)
    val days = PlanBuilder.groupDays(plan)
    val short = PlanBuilder.short(a, plan)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Title("", null, onBack = onBack)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(total) { i -> Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i <= step) SadoColors.Accent else SadoColors.Track)) }
            }
            Text("${step + 1} / $total", color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.tier7), null, Modifier.size(width = 48.dp, height = 78.dp))
            Card {
                Text("Ni plan awal kau", color = SadoColors.Text, fontSize = 19.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Text("Tengok otot yang kena, lepas tu ubah rating kat bawah. Plan berubah terus.", color = SadoColors.TextMuted, fontSize = 13.sp)
            }
        }
        Card {
            Text(plan.title, color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text("${plan.allSessions.size} jenis sesi · " + plan.allSessions.joinToString(" · ") { it.label }, color = SadoColors.TextMuted, fontSize = 13.sp)
            BodyHeat(week, week = true, height = 230)
            HeatLegend(week = true)
            HeatChips(week, week = true)
            FocusBar(plan)
        }
        if (short.isNotEmpty()) ShortNote(short, sets, a)
        Label("Rating setiap otot")
        Text("5 = fokus: dijamin ${PlanBuilder.FOCUS_SETS}+ set seminggu, 2 hari. Bentuk badan impian kau tambah lagi di atas rating ni.", color = SadoColors.TextMuted, fontSize = 12.sp)
        groupOptions.forEach { (key, label) ->
            val n = sets[key] ?: 0
            val d = days[key] ?: 0
            RatingRow(label, if (n == 0) "Belum ada move khas" else "$n set move ${label.lowercase()} · $d hari seminggu", ratings.getValue(key), warn = key in short) {
                ratings = ratings + (key to it)
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Okay, jana plan aku") { onDone(a) }
    }
}

// a focus that does not fit: say how far short, and the three ways out
@Composable
private fun ShortNote(short: List<String>, sets: Map<String, Int>, a: PlannerAnswers) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Streak.copy(alpha = 0.12f))
            .border(1.dp, SadoColors.Streak.copy(alpha = 0.5f), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Tak muat dalam ${a.days} hari × ${a.time} minit", color = SadoColors.Streak, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            short.joinToString(", ") { "${PlanBuilder.groupNames.getValue(it)} ${sets[it] ?: 0}/${PlanBuilder.FOCUS_SETS} set" } +
                ". Tambah hari atau masa, atau turunkan satu rating 5.",
            color = SadoColors.Text, fontSize = 13.sp,
        )
    }
}

/** One muscle group's rating, 1 to 5, as five big segments. */
@Composable
internal fun RatingRow(label: String, sub: String, value: Int, warn: Boolean = false, onChange: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface)
            .border(if (value == 5) 2.dp else 1.dp, if (value == 5) SadoColors.Accent else SadoColors.Outline, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text(sub, color = if (warn) SadoColors.Streak else SadoColors.TextMuted, fontSize = 12.sp)
            }
            Text(PlanBuilder.ratingNames[value - 1], color = if (value >= 4) SadoColors.Accent else SadoColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { i ->
                val on = i <= value
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) SadoColors.Accent.copy(alpha = 0.35f + 0.13f * i) else SadoColors.Surface2)
                        .clickable(role = Role.Button) { onChange(i) }
                        .semantics { contentDescription = "$label: $i daripada 5, ${PlanBuilder.ratingNames[i - 1]}" },
                    contentAlignment = Alignment.Center,
                ) { Text("$i", color = if (on) SadoColors.OnAccent else SadoColors.TextMuted, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

/** The plan: why it is built this way, then every session in the week with each move's sets, reps and muscles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanResultScreen(onExercise: (String) -> Unit, onUse: () -> Unit, onRedo: () -> Unit) {
    val s = Store.state.collectAsState().value
    val plan = s.plan ?: return
    // the doses as the next workout will set them: ladder rungs reached and reps earned since
    val shown = { list: List<PlannedExercise> ->
        (s.answers?.let { PlanBuilder.refresh(list, it, s.ladders) } ?: list).map { Progress.boosted(it, s.boosts, s.extraSets) }
    }
    val sessions = plan.allSessions.map { it to shown(it.exercises) }
    val week = weekSets(plan, shown)
    val today = my.sado.app.data.WeekShift.sessionOn(s) ?: plan.sessionOn()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Plan kau", color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Text(plan.title, color = SadoColors.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(plan.why, color = SadoColors.TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
        // for everyone before the first workout, and until a flagged answer changes; a lean body or a young age hides nothing
        if (s.logs.isEmpty() || my.sado.app.data.Care.flagged(s)) HealthNote()
        FocusBar(plan)
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("I", "S", "R", "K", "J", "S", "A").forEachIndexed { i, d ->
                val on = plan.week[i] != "Rest"
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(if (on) SadoColors.Accent.copy(alpha = 0.12f) else SadoColors.Surface).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val c = if (on) SadoColors.Accent else SadoColors.TextMuted
                    Text(d, color = c, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text(plan.week[i].replace("+", "+\n"), color = c, fontSize = 10.sp, lineHeight = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }

        Card {
            Text("Otot yang plan ni latih", color = SadoColors.Text, fontSize = 19.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text("Warna ikut jumlah set seminggu. Otot yang membantu dikira separuh set.", color = SadoColors.TextMuted, fontSize = 12.sp)
            BodyHeat(week, week = true, height = 230)
            HeatLegend(week = true)
            HeatChips(week, week = true)
        }

        Label("Semua sesi (${sessions.size})")
        HeatLegend(week = false)
        sessions.forEach { (session, moves) ->
            val sets = sessionSets(moves)
            val top = sets.entries.sortedByDescending { it.value }.take(4).map { it.key }
            val days = plan.daysOf(session)
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(session.label, color = SadoColors.Text, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                        Text(days.joinToString(" · ") { dayNames[it] }, color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    if (session == today) Tag("Seterusnya", filled = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(0.9f)) { BodyHeat(sets, week = false, height = 140, labels = false) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${moves.size} move · ${moves.sumOf { it.sets }} set", color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Paling banyak: " + names(top), color = SadoColors.TextMuted, fontSize = 13.sp)
                    }
                }
                moves.forEachIndexed { i, e -> MoveDetailRow(i + 1, e) { onExercise(e.id) } }
            }
        }
        Text("Set dan rep ni yang workout seterusnya akan guna. Bila ko jawab Easy, rep naik sendiri.", color = SadoColors.TextMuted, fontSize = 12.sp)
        PrimaryButton("Guna plan ni", onClick = onUse)
        SecondaryButton("Jawab semula", Modifier.fillMaxWidth(), onClick = onRedo)
    }
}

// one move: dose up front, then which muscles do the work and which help
@Composable
private fun MoveDetailRow(n: Int, e: PlannedExercise, onClick: () -> Unit) {
    val m = Catalog.get(e.id)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface2)
            .border(1.dp, SadoColors.Outline, RoundedCornerShape(16.dp)).pressable(onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(SadoColors.Accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text("$n", color = SadoColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("${e.sets} × ${e.reps}", color = SadoColors.Accent, fontSize = 15.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            }
            Text(
                "Otot utama: " + names(m.muscles) + if (m.secondary.isNotEmpty()) "\nMembantu: " + names(m.secondary) else "",
                color = SadoColors.TextMuted, fontSize = 12.sp, lineHeight = 17.sp,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SadoColors.TextMuted)
    }
}
