package my.sado.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import my.sado.app.Coach
import my.sado.app.Lines
import my.sado.app.R
import my.sado.app.data.FitnessTest
import my.sado.app.data.Progress
import my.sado.app.data.SportLog
import my.sado.app.data.Store
import my.sado.app.data.WorkoutLog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun JourneyScreen(onBack: () -> Unit) {
    val s = Store.state.collectAsState().value
    val cur = Progress.stageFor(s)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Title("Perjalanan character", "${s.xp} XP · ${Progress.weeksActive(s)} minggu aktif", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        Progress.stagesFor(s).forEach { st ->
            val past = st.index < cur.index
            val now = st.index == cur.index
            val locked = st.index > cur.index
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (now) SadoColors.Surface else SadoColors.Surface.copy(alpha = 0.6f))
                    .border(2.dp, if (now) SadoColors.Accent else Color.Transparent, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Aura(if (locked) Progress.stagesFor(s)[0] else st, Modifier.size(width = 64.dp, height = 84.dp)) {
                    Image(
                        painterResource(st.image), null,
                        Modifier.size(width = 52.dp, height = 84.dp).alpha(if (locked) 0.45f else if (past) 0.7f else 1f),
                        colorFilter = if (locked) ColorFilter.tint(Color.Black) else null,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (locked) "${st.name} · ???" else st.name, color = SadoColors.Text, fontSize = 19.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    Text(
                        when {
                            past -> "Dicapai"
                            now && Progress.next(s) != null -> "Seterusnya: " + Progress.requirement(Progress.next(s)!!, s)
                            now -> "Level paling tinggi. Beast mode!"
                            else -> "Perlu ${st.at} XP" + (if (st.minWeeks > 0) " · ${st.minWeeks} minggu aktif" else "") + (if (st.needTest) " · lulus ujian" else "") + (if (st.heavy && st.index == 3) ", atau turun 5% berat" else "")
                        },
                        color = SadoColors.TextMuted, fontSize = 13.sp,
                    )
                }
                Tag(if (past) "Lepas" else if (now) "Sekarang" else "Terkunci", if (locked) SadoColors.TextMuted else SadoColors.Accent, filled = now)
            }
        }
        Card {
            Text("Kenapa ada syarat masa?", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text("Badan sebenar ambil berbulan untuk berubah. Setiap level perlukan XP dan minggu aktif: Berisi dari minggu 4, Fit minggu 10, Sado minggu 20, Beast mode minggu 40 dan kena lulus ujian push-up atau pull-up. Character tak boleh lebih laju dari badan kau. Aura makin kuat setiap tier, dan tukar warna bila badan naik tahap.", color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        Card {
            Text("Cara streak jalan", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text("Streak kira hari sejak kau start. Asalkan setiap minggu (Isnin–Ahad) ada at least ${Progress.MIN_PER_WEEK} workout, streak jalan terus. Minggu ni: ${Progress.streak(s).thisWeek} / ${Progress.MIN_PER_WEEK}. XP kau tak pernah ditolak.", color = SadoColors.TextMuted, fontSize = 13.sp)
        }
    }
}

internal val monthNames = listOf("Januari", "Februari", "Mac", "April", "Mei", "Jun", "Julai", "Ogos", "September", "Oktober", "November", "Disember")
private val dayNames = listOf("Isnin", "Selasa", "Rabu", "Khamis", "Jumaat", "Sabtu", "Ahad")

@Composable
fun CalendarScreen(onTest: () -> Unit, onSport: () -> Unit, onAchievements: () -> Unit = {}) {
    val s = Store.state.collectAsState().value
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var picked by remember { mutableStateOf(today) }
    val active = s.logs.groupBy { it.epochDay }
    val sport = s.sports.groupBy { it.epochDay }
    val week = s.plan?.week
    val streak = Progress.streak(s)
    var deleteLog by remember { mutableStateOf<WorkoutLog?>(null) }
    var deleteSport by remember { mutableStateOf<SportLog?>(null) }
    val pendingXp = deleteLog?.xp ?: deleteSport?.xp
    if (pendingXp != null) AlertDialog(
        onDismissRequest = { deleteLog = null; deleteSport = null },
        containerColor = SadoColors.Surface,
        title = { Text("Padam dari sejarah?", color = SadoColors.Text, fontWeight = FontWeight.ExtraBold) },
        text = { Text((deleteLog?.title ?: deleteSport?.sport) + " akan dibuang dan −$pendingXp XP ditolak. Streak dan rekod dikira semula. Tak boleh undo.", color = SadoColors.TextMuted) },
        confirmButton = { TextButton({ deleteLog = null; deleteSport = null }) { Text("Batal", color = SadoColors.Accent, fontWeight = FontWeight.ExtraBold) } },
        dismissButton = {
            TextButton({
                val l = deleteLog; val sp = deleteSport
                Store.update { st ->
                    st.copy(
                        xp = (st.xp - pendingXp).coerceAtLeast(0),
                        // deleted work never happened, so it can't be a best to come back to
                        peakXp = (maxOf(st.peakXp, st.xp) - pendingXp).coerceAtLeast(0),
                        logs = if (l != null) st.logs - l else st.logs,
                        sports = if (sp != null) st.sports - sp else st.sports,
                    )
                }
                deleteLog = null; deleteSport = null
            }) { Text("Padam", color = SadoColors.Danger) }
        },
    )
    val inMonth = { e: Long -> YearMonth.from(LocalDate.ofEpochDay(e)) == month }
    val monthWorkouts = s.logs.count { inMonth(it.epochDay) }
    val monthSports = s.sports.count { inMonth(it.epochDay) }
    val mins = Progress.weeklyActiveMinutes(s)
    var n = 0

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Rekod", "${s.logs.size} workout · ${s.sports.size} sukan sejak mula")

        Row(Modifier.appear(n++), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("${streak.days}", "hari streak", SadoIcons.Flame, SadoColors.Streak, Modifier.weight(1f))
            MiniStat("${streak.thisWeek}/${Progress.MIN_PER_WEEK}", "workout minggu ni", SadoIcons.Dumbbell, SadoColors.Accent, Modifier.weight(1f))
            MiniStat("$mins", "minit aktif minggu ni", SadoIcons.Timer, SadoColors.Sky, Modifier.weight(1f))
        }

        Card(Modifier.appear(n++)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Bulan lepas", true) { month = month.minusMonths(1) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${monthNames[month.monthValue - 1]} ${month.year}", color = SadoColors.Text, fontSize = 19.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    Text("$monthWorkouts workout · $monthSports sukan", color = SadoColors.TextMuted, fontSize = 12.sp)
                }
                // nothing to see past this month yet
                NavArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Bulan depan", month < YearMonth.from(today)) { month = month.plusMonths(1) }
            }
            Row { listOf("I", "S", "R", "K", "J", "S", "A").forEach { Text(it, color = SadoColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) } }
            val first = month.atDay(1)
            val offset = first.dayOfWeek.value - 1
            val cells = offset + month.lengthOfMonth()
            (0 until (cells + 6) / 7).forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0 until 7).forEach { c ->
                        val dayNum = r * 7 + c - offset + 1
                        if (dayNum < 1 || dayNum > month.lengthOfMonth()) Box(Modifier.weight(1f).aspectRatio(1f))
                        else {
                            val d = month.atDay(dayNum)
                            val e = d.toEpochDay()
                            val worked = e in active
                            val played = e in sport
                            val future = d.isAfter(today)
                            val sel = d == picked
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            worked -> SadoColors.Accent
                                            played -> SadoColors.Sky.copy(alpha = 0.22f)
                                            sel -> SadoColors.Surface2
                                            else -> Color.Transparent
                                        },
                                    )
                                    .border(2.dp, if (sel) SadoColors.Text else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !future, role = Role.Button) { picked = d; Feedback.tap() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$dayNum",
                                    color = when { worked -> SadoColors.OnAccent; played -> SadoColors.Sky; d == today -> SadoColors.Accent; future -> SadoColors.TextMuted.copy(alpha = 0.35f); else -> SadoColors.TextMuted },
                                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                                )
                                // the dot under the number only ever means today
                                if (d == today) Box(
                                    Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(5.dp).clip(CircleShape)
                                        .background(if (worked) SadoColors.OnAccent else SadoColors.Accent),
                                )
                                // sport on a workout day: a sky badge in the corner, same colour as the Sukan legend
                                if (worked && played) Box(
                                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(9.dp).clip(CircleShape)
                                        .background(SadoColors.OnAccent).padding(1.5.dp).clip(CircleShape).background(SadoColors.Sky),
                                )
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                Legend(SadoColors.Accent, "Workout")
                Legend(SadoColors.Sky.copy(alpha = 0.4f), "Sukan")
                Legend(SadoColors.Accent, "Hari ni", dot = true)
            }
        }

        val pe = picked.toEpochDay()
        val logs = active[pe].orEmpty()
        val sp = sport[pe].orEmpty()
        Card(Modifier.appear(n++)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${dayNames[picked.dayOfWeek.value - 1]}, ${picked.dayOfMonth} ${monthNames[picked.monthValue - 1].take(3)}",
                    color = SadoColors.Text, fontSize = 18.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f),
                )
                if (picked == today) Tag("Hari ni")
                else if (logs.isNotEmpty() || sp.isNotEmpty()) Tag("+${logs.sumOf { it.xp } + sp.sumOf { it.xp }} XP")
            }
            if (logs.isEmpty() && sp.isEmpty()) {
                val rest = week != null && my.sado.app.data.WeekShift.tagOn(s, picked) == "Rest"
                Text(
                    when { rest -> "Rest day dalam plan. Otot membesar masa rehat."; picked == today -> "Belum ada aktiviti hari ni."; else -> "Tiada aktiviti hari ni." },
                    color = SadoColors.TextMuted, fontSize = 14.sp,
                )
                if (picked == today) SecondaryButton("Main sukan tadi? Log sini", Modifier.fillMaxWidth(), color = SadoColors.Sky, onClick = onSport)
            }
            logs.forEach { l ->
                Activity(SadoIcons.Dumbbell, SadoColors.Accent, l.title, "${l.minutes} min · +${l.xp} XP" + if (l.quit) " · stop awal" else "") { deleteLog = l }
            }
            sp.forEach { l ->
                Activity(SadoIcons.Steps, SadoColors.Sky, l.sport, "${l.minutes} min · ~${l.kcal} kcal · +${l.xp} XP") { deleteSport = l }
            }
        }

        Card(Modifier.appear(n++)) {
            val vol = Progress.weeklySets(s)
            val hit = vol.count { it.value >= 10 }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Set minggu ni", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    Text("Target 10+ set setiap otot", color = SadoColors.TextMuted, fontSize = 12.sp)
                }
                Tag("$hit/${vol.size} otot", if (hit == vol.size) SadoColors.Accent else SadoColors.Streak)
            }
            // this week's sets on the body: helpers count half, colour by the weekly bands
            val monday = LocalDate.now().let { it.minusDays(it.dayOfWeek.value - 1L) }.toEpochDay()
            val muscleSets = Heat.sets(s.logs.filter { it.epochDay >= monday }.flatMap { l -> l.exercises.filter { !it.skipped }.map { it.id to it.sets.size } })
            if (muscleSets.isNotEmpty()) {
                BodyHeat(muscleSets, week = true, height = 200)
                HeatLegend(week = true)
            }
            vol.forEach { (g, v) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(g, color = SadoColors.Text, fontSize = 14.sp, modifier = Modifier.width(76.dp))
                    Box(Modifier.weight(1f)) { Bar(v / 10f, if (v >= 10) SadoColors.Accent else SadoColors.Streak) }
                    Text(if (v >= 10) "$v ✓" else "$v/10", color = if (v >= 10) SadoColors.Accent else SadoColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(SadoColors.Outline))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Minit aktif minggu ni", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("$mins / 150", color = if (mins >= 150) SadoColors.Accent else SadoColors.Sky, fontSize = 15.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            }
            Bar(mins / 150f, if (mins >= 150) SadoColors.Accent else SadoColors.Sky, height = 8)
            Text("WHO cadang 150 minit seminggu.", color = SadoColors.TextMuted, fontSize = 12.sp)
        }

        AchievementTeaser(onAchievements, Modifier.appear(n++))

        Card(Modifier.appear(n++)) {
            Text("Rekod peribadi", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            val recs = Progress.records(s).take(6)
            if (recs.isEmpty()) Text("Habiskan satu session untuk mula rekod.", color = SadoColors.TextMuted, fontSize = 14.sp)
            recs.forEachIndexed { i, (m, best) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(SadoColors.Streak.copy(alpha = if (i == 0) 0.25f else 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Star, null, tint = SadoColors.Streak, modifier = Modifier.size(18.dp))
                    }
                    Text(m.name, color = SadoColors.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(if (m.timed) "${best}s" else "$best rep", color = SadoColors.Text, fontSize = 18.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                }
            }
            s.tests.lastOrNull()?.let { t ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(SadoColors.Outline))
                Text("Ujian terakhir · ${LocalDate.ofEpochDay(t.epochDay).let { "${it.dayOfMonth} ${monthNames[it.monthValue - 1].take(3)}" }}", color = SadoColors.TextMuted, fontSize = 12.sp)
                Text("${t.pushups} push-up · plank ${t.plankSec}s" + (t.pullups?.let { " · $it pull-up" } ?: ""), color = SadoColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (s.tests.size >= 2) Card(Modifier.appear(n++)) {
            Text("Ujian 4 minggu", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            val tests = s.tests.takeLast(6)
            TestBars("Push-up", tests.map { it.pushups }, "", SadoColors.Accent, tests.map { it.epochDay })
            TestBars("Plank", tests.map { it.plankSec }, "s", SadoColors.Sky, tests.map { it.epochDay })
            if (tests.any { it.pullups != null }) TestBars("Pull-up", tests.map { it.pullups ?: 0 }, "", SadoColors.Violet, tests.map { it.epochDay })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Ujian kecergasan", Modifier.weight(1f), onClick = onTest)
            SecondaryButton("Log sukan", Modifier.weight(1f), onClick = onSport)
        }
    }
}

/** One row of bars per fitness test, latest on the right, value printed on top so colour is never the only cue. */
@Composable
private fun TestBars(name: String, values: List<Int>, unit: String, color: Color, days: List<Long>) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val first = values.first()
    val last = values.last()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(
            "$first$unit → $last$unit" + if (last != first) " (${if (last > first) "+" else ""}${last - first})" else "",
            color = if (last > first) color else SadoColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
        )
    }
    Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text("$v", color = SadoColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.fillMaxWidth().height((60f * v / max).coerceAtLeast(3f).dp).clip(RoundedCornerShape(6.dp))
                        .background(if (i == values.lastIndex) color else color.copy(alpha = 0.35f)),
                )
                LocalDate.ofEpochDay(days[i]).let { Text("${it.dayOfMonth}/${it.monthValue}", color = SadoColors.TextMuted, fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(20.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(value, color = SadoColors.Text, fontSize = 22.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = SadoColors.TextMuted, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2)
    }
}

@Composable
private fun NavArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(SadoColors.Surface2).clickable(enabled = enabled, role = Role.Button) { Feedback.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (enabled) SadoColors.Text else SadoColors.TextMuted.copy(alpha = 0.3f)) }
}

@Composable
private fun Legend(color: Color, text: String, dot: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(if (dot) 6.dp else 12.dp).clip(if (dot) CircleShape else RoundedCornerShape(4.dp)).background(color))
        Text(text, color = SadoColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun Activity(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, title: String, sub: String, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface2).padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = SadoColors.OnAccent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { Feedback.tap(); onDelete() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Delete, contentDescription = "Padam $title", tint = SadoColors.TextMuted, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
fun FitnessTestScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val s = Store.state.collectAsState().value
    val hasBar = (s.answers?.pull ?: -1) >= 0
    var step by remember { mutableIntStateOf(0) } // 0 push-up, 1 plank, 2 pull-up, 3 result
    var push by remember { mutableIntStateOf(0) }
    var plank by remember { mutableIntStateOf(0) }
    var pull by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    // the test before this one, kept so the result screen can compare after it is saved
    val prev = remember { Store.state.value.tests.lastOrNull() }
    var reward by remember { mutableStateOf<Progress.TestReward?>(null) }
    LaunchedEffect(running) { while (running) { delay(1000); plank++; if (plank % 10 == 0) Feedback.countdown() } }
    val names = listOf("Push-up max", "Plank max", "Pull-up max")
    val count = if (hasBar) 3 else 2

    fun save() {
        val test = FitnessTest(LocalDate.now().toEpochDay(), push, plank, if (hasBar) pull else null)
        val r = Progress.testReward(prev, test)
        Store.update { it.copy(tests = it.tests + test, xp = it.xp + r.total) }
        reward = r
        if (r.records.isNotEmpty()) { Feedback.levelUp(); Coach.say(Lines.testRecord) } else { Feedback.success(); Coach.say(Lines.testDone) }
        step = 3
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (step < 3) {
                Title("Ujian 4 minggu", "${step + 1} / $count · ${names[step]}", onBack = onBack)
                Text(
                    when (step) {
                        0 -> "Letak phone tepi, buat push-up sebanyak boleh dengan form betul. Lepas siap, masukkan berapa rep ko dapat."
                        1 -> "Hold plank selama boleh. Tekan bulatan untuk start dan stop."
                        else -> "Pull-up penuh sebanyak boleh: dagu atas bar, turun sampai lurus. Lepas siap, masukkan angka."
                    },
                    color = SadoColors.TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
                val before = prev?.let { listOf(it.pushups, it.plankSec, it.pullups ?: 0)[step] }
                when (step) {
                    0 -> CountEntry(push, "rep", "Push-up", best = before != null && push > before) { push = it }
                    1 -> BigCounter("${plank}s", if (running) "tekan untuk stop" else "tekan untuk start", before != null && plank > before) { running = !running; Feedback.tap() }
                    else -> CountEntry(pull, "rep", "Pull-up", best = before != null && pull > before) { pull = it }
                }
                if (step == 1 && (running || plank > 0)) Text(plankCheer(plank), color = SadoColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                before?.let {
                    val value = listOf(push, plank, pull)[step]
                    Text("Ujian lepas: ${if (step == 1) "${it}s" else "$it"}" + if (value > it) " · rekod baru!" else "", color = if (value > it) SadoColors.Accent else SadoColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
                val last = step == count - 1
                PrimaryButton(if (last) "Simpan keputusan" else "Seterusnya", enabled = !running) {
                    if (last) save() else step++
                }
            } else {
                val r = reward ?: Progress.TestReward(Progress.TEST_XP, 0, emptyList())
                val record = r.records.isNotEmpty()
                Image(painterResource(if (record) R.drawable.face_wow else R.drawable.face_cheer), null, Modifier.height(150.dp))
                Text(if (record) "Rekod baru!" else "Ujian siap", color = SadoColors.Text, fontSize = 30.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Card {
                    Compare("Push-up", prev?.pushups, push, "")
                    Compare("Plank", prev?.plankSec, plank, "s")
                    if (hasBar) Compare("Pull-up", prev?.pullups, pull, "")
                }
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Stat("+${r.base}", "XP ujian", SadoColors.Accent)
                        Stat("+${r.bonus}", "bonus naik", if (r.bonus > 0) SadoColors.Accent else SadoColors.TextMuted)
                    }
                    Text(
                        if (prev == null) "Ni ujian pertama ko. Lain kali, setiap rep lebih dapat bonus XP."
                        else "+5 XP setiap rep lebih, +1 XP setiap 5 saat plank lebih (paling banyak +${Progress.TEST_BONUS_CAP}).",
                        color = SadoColors.TextMuted, fontSize = 12.sp,
                    )
                }
                PrimaryButton("Siap", onClick = onDone)
            }
        }
        if (step == 3 && reward?.records?.isNotEmpty() == true) Confetti()
    }
}

@Composable
private fun Compare(name: String, before: Int?, now: Int, unit: String) {
    val up = before != null && now > before
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        if (before != null) Text("$before$unit → ", color = SadoColors.TextMuted, fontSize = 16.sp)
        Text("$now$unit", color = if (up) SadoColors.Accent else SadoColors.Text, fontSize = 24.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        if (before != null && now != before) Text(
            "  (${if (now > before) "+" else ""}${now - before})",
            color = if (up) SadoColors.Accent else SadoColors.Streak, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
fun SportScreen(onBack: () -> Unit, onHealth: () -> Unit) {
    val s = Store.state.collectAsState().value
    var pick by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(60) }
    var level by remember { mutableIntStateOf(1) }
    val sport = Progress.sports[pick]
    val kg = s.profile?.weightKg ?: 65f
    val kcal = Progress.sportKcal(sport, minutes, level, kg)
    val xp = Progress.sportXp(minutes)
    val weekMin = Progress.weeklyActiveMinutes(s) + minutes

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Sukan lain hari ni", "Dikira untuk streak, XP dan minit aktif", onBack = onBack)
        Text(
            "Rekod dalam Garmin atau jam lain? Import dari Health Connect",
            color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.fillMaxWidth().clickable { onHealth() },
        )
        Progress.sports.chunked(4).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chunk.forEachIndexed { c, sp ->
                    val i = row * 4 + c
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (i == pick) SadoColors.Accent.copy(alpha = 0.1f) else SadoColors.Surface)
                            .border(2.dp, if (i == pick) SadoColors.Accent else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable { pick = i; Feedback.tap() },
                        contentAlignment = Alignment.Center,
                    ) { Text(sp.name, color = if (i == pick) SadoColors.Accent else SadoColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center) }
                }
            }
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Berapa lama?", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("−", color = SadoColors.Text, fontSize = 24.sp, modifier = Modifier.clickable { minutes = (minutes - 15).coerceAtLeast(15) }.padding(horizontal = 16.dp))
                Text("$minutes min", color = SadoColors.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("+", color = SadoColors.Text, fontSize = 24.sp, modifier = Modifier.clickable { minutes = (minutes + 15).coerceAtMost(240) }.padding(horizontal = 16.dp))
            }
        }
        Segmented(listOf("Santai", "Sederhana", "Kuat"), level) { level = it }
        Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Stat("~$kcal", "kcal (anggaran)")
                Stat("+$xp", "XP", SadoColors.Accent)
            }
            Text("Minit aktif minggu ni: $weekMin / 150 (WHO)", color = SadoColors.Text, fontSize = 13.sp)
            Bar(weekMin / 150f, height = 6)
            if (sport.legs && minutes >= 45) Text("Kaki dah kerja keras. Kalau esok hari kaki, buat versi ringan atau tukar ke badan atas.", color = SadoColors.Accent, fontSize = 13.sp)
        }
        PrimaryButton("Simpan ${sport.name.lowercase()}") {
            Store.update { it.copy(xp = it.xp + xp, sports = it.sports + SportLog(LocalDate.now().toEpochDay(), sport.name, minutes, level, kcal, xp)) }
            Feedback.success()
            onBack()
        }
    }
}
