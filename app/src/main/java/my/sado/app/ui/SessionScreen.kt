package my.sado.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import my.sado.app.Coach
import my.sado.app.Lines
import my.sado.app.HeartRate
import my.sado.app.ai.Motions
import my.sado.app.R
import my.sado.app.data.AppState
import my.sado.app.data.Catalog
import my.sado.app.data.ExerciseLog
import my.sado.app.data.PlanBuilder
import my.sado.app.data.PlannedExercise
import my.sado.app.data.Progress
import my.sado.app.data.SetLog
import my.sado.app.data.Store
import my.sado.app.data.Templates
import my.sado.app.data.WorkoutLog
import java.time.LocalDate

private enum class Phase { Warmup, Work, Effort, Rest }

// keeps a set/exercise log list across navigation as JSON in the saved state
private fun <T> listSaver(item: kotlinx.serialization.KSerializer<T>) = androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.snapshots.SnapshotStateList<T>, String>(
    save = { kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(item), it.toList()) },
    restore = { mutableStateListOf<T>().apply { addAll(kotlinx.serialization.json.Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(item), it)) } },
)

/** Today's gear when it was changed for this workout, else null (the quiz's gear applies). */
fun gearToday(s: AppState): List<String>? = s.gearToday?.takeIf { it.epochDay == LocalDate.now().toEpochDay() }?.equip

fun sessionFor(source: String, quizGear: Boolean = false): Pair<String, List<PlannedExercise>> {
    val s = Store.state.value
    val mine = s.myTemplates.firstOrNull { it.id == source.removePrefix("my:") }.takeIf { source.startsWith("my:") }
    // the user's own workouts keep the reps they chose; plan and templates climb from the bottom of each range
    val (title, given) = if (mine != null) {
        mine.name to mine.moves.map { it.planned() }
    } else if (source.startsWith("tpl:")) {
        val t = Templates.all.first { it.id == source.removePrefix("tpl:") }
        t.name to Templates.session(t, s.answers, s.ladders).map { Progress.boosted(it, s.boosts, s.extraSets) }
    } else {
        val today = s.plan?.sessionOn()
        val ex = today?.exercises.orEmpty()
        (today?.label ?: "Sesi") to (s.answers?.let { PlanBuilder.refresh(ex, it, s.ladders) } ?: ex).map { Progress.boosted(it, s.boosts, s.extraSets) }
    }
    // gear changed for today: swap the moves that need what is not here, the stand-ins with their own earned reps
    val gear = gearToday(s).takeIf { !quizGear }
    val list = if (gear == null || s.answers == null) given else
        PlanBuilder.adapt(given, PlanBuilder.withGear(s.answers, gear), s.ladders).map { e -> if (given.any { it.id == e.id }) e else Progress.boosted(e, s.boosts, s.extraSets) }
    // deload week and short sleep (<6h) each take one set off every move; recovery drives growth
    val shortSleep = s.sleep.lastOrNull { it.epochDay == LocalDate.now().toEpochDay() }?.let { it.hours < 6f } == true
    val cut = (if (Progress.isDeloadWeek(s)) 1 else 0) + (if (shortSleep) 1 else 0)
    val tag = listOfNotNull("deload week".takeIf { Progress.isDeloadWeek(s) }, "kurang tidur".takeIf { shortSleep })
    return (if (tag.isEmpty()) title else "$title (${tag.joinToString(", ")})") to list.map { it.copy(sets = (it.sets - cut).coerceAtLeast(1)) }
}

@Composable
fun SessionScreen(source: String, onExercise: (String) -> Unit, onFinished: () -> Unit, onExit: () -> Unit) {
    val settings = Store.state.collectAsState().value.settings
    val (title, plan) = remember { sessionFor(source) }
    // everything that tracks progress is saveable, so opening Info and coming back resumes the same set
    val startedAt = rememberSaveable { System.currentTimeMillis() }
    var hrStarted by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    DisposableEffect(Unit) {
        settings.hrDevice?.let { if (HeartRate.hasPermissions(context)) HeartRate.connect(context, it) }
        if (hrStarted) HeartRate.resume() else { HeartRate.startRecording(); hrStarted = true }
        onDispose { HeartRate.pause() }
    }
    if (plan.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Title("Takde move", "Plan atau template ni kosong untuk equipment ko.", onBack = onExit)
        }
        return
    }

    var phase by rememberSaveable { mutableStateOf(if (settings.warmup) Phase.Warmup else Phase.Work) }
    var idx by rememberSaveable { mutableIntStateOf(0) }
    var reps by rememberSaveable { mutableIntStateOf(0) }
    val sets = rememberSaveable(saver = listSaver(SetLog.serializer())) { mutableStateListOf() }
    val logs = rememberSaveable(saver = listSaver(ExerciseLog.serializer())) { mutableStateListOf() }
    var spoken by rememberSaveable { mutableStateOf("") }
    var quitOpen by remember { mutableStateOf(false) }
    var discardOpen by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val cur = plan[idx]
    val move = Catalog.get(cur.id)
    // the extra kg for a weighted move; changing it here carries to the next session
    var kg by rememberSaveable(idx) { mutableFloatStateOf(Progress.load(Store.state.value.loads, cur.id)) }
    LaunchedEffect(idx, phase) {
        // don't repeat the line when coming back from Info
        val key = "$idx-$phase-${sets.size}"
        if (key == spoken) return@LaunchedEffect
        spoken = key
        when (phase) {
            Phase.Warmup -> Coach.say(if (my.sado.app.data.Progress.lowImpact(my.sado.app.data.Store.state.value)) Lines.warmupLow else Lines.warmup)
            Phase.Work -> if (sets.isEmpty()) Coach.sayParts(Coach.intro(move.name, cur.sets, cur.reps)) else Coach.say(Lines.set(sets.size + 1, last = sets.size + 1 == cur.sets))
            Phase.Effort -> Coach.say(Lines.effort)
            Phase.Rest -> Coach.say(Lines.rest(settings.restSeconds))
        }
    }

    fun finish(quit: Boolean) {
        val all = logs.toList() + if (sets.isNotEmpty() && logs.none { it.id == cur.id }) listOf(ExerciseLog(cur.id, sets.toList())) else emptyList()
        val xp = Progress.sessionXp(plan, all)
        val minutes = ((System.currentTimeMillis() - startedAt) / 60000).toInt().coerceAtLeast(1)
        val hr = HeartRate.stopRecording()
        val bonus = Progress.comebackBonus(Store.state.value, xp)
        Store.update { s ->
            val c = Progress.climb(s.ladders, s.boosts, plan, all, s.loads) { PlanBuilder.usable(it, s.answers) }
            // back from a break: XP counts double until the best reached before
            s.copy(
                xp = s.xp + xp + bonus,
                peakXp = maxOf(s.peakXp, s.xp + xp + bonus),
                logs = s.logs + WorkoutLog(LocalDate.now().toEpochDay(), title, minutes, all, xp + bonus, quit, hr?.first, hr?.second, bonus),
                ladders = c.ladders,
                boosts = c.boosts,
                loads = c.loads,
                gearToday = null, // the next workout starts from the quiz's gear again
                plan = s.plan?.let { p -> s.answers?.let { a -> p.mapExercises { PlanBuilder.refresh(it, a, c.ladders) } } } ?: s.plan,
            )
        }
        Feedback.success()
        val s = Store.state.value
        Coach.say(when { xp == 0 -> Lines.zero; bonus > 0 && s.xp >= s.peakXp -> Lines.backAtBest; bonus > 0 -> Lines.comeback; quit -> Lines.quit; else -> Lines.done })
        onFinished()
    }

    fun nextExercise(skipped: Boolean) {
        logs += ExerciseLog(cur.id, sets.toList(), skipped)
        sets.clear(); reps = 0
        if (idx == plan.lastIndex) finish(false) else { idx++; phase = Phase.Work }
    }

    BackHandler { quitOpen = true }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (phase) {
            Phase.Warmup -> Warmup(onDone = { phase = Phase.Work }, onQuit = { quitOpen = true })
            Phase.Work -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // title takes the leftover width and wraps, so long move names never squeeze the Info chip
                    Title(move.name, "Set ${sets.size + 1} / ${cur.sets} · Move ${idx + 1} / ${plan.size}", onBack = { quitOpen = true }, modifier = Modifier.weight(1f))
                    LiveHr(Modifier.padding(end = 8.dp))
                    InfoChip("Info") { onExercise(cur.id) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    plan.indices.forEach { i ->
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i < idx) SadoColors.Accent else if (i == idx) SadoColors.Accent.copy(alpha = 0.5f) else SadoColors.Track))
                    }
                }
                val camSupported = cur.id in Motions.reps || cur.id in Motions.holds
                val camOn = camSupported && settings.cameraReps
                if (camSupported) CameraToggle(settings.cameraReps) { v -> Store.update { it.copy(settings = it.settings.copy(cameraReps = v)) } }
                // camera stays outside key() so it isn't rebound every set
                if (!camOn) MoveImage(cur.id)
                if (camOn) PoseCamera(cur.id, onRep = { if (!move.timed && phase == Phase.Work) { reps++; Feedback.rep() } })
                key(idx, sets.size) {
                    if (move.timed) TimedSet(cur.target) { secs -> reps = secs }
                    else TargetCard(cur.reps, if (camOn) reps else null)
                }
                if (move.weighted) LoadPicker(kg) { kg = it }
                // set pips: done sets fill lime, the current one is outlined
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    repeat(cur.sets) { i ->
                        val done = i < sets.size
                        Box(
                            Modifier.size(width = 36.dp, height = 10.dp).clip(RoundedCornerShape(5.dp))
                                .background(if (done) SadoColors.Accent else SadoColors.Track)
                                .border(2.dp, if (i == sets.size) SadoColors.Accent else Color.Transparent, RoundedCornerShape(5.dp)),
                        )
                    }
                }
                if (toast != null) Text(toast!!, color = SadoColors.Streak, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                // no rep tapping: one press per set, reps count as the target unless the camera or timer measured them
                PrimaryButton(if (sets.size + 1 >= cur.sets) "Set done · last set" else "Set done") {
                    sets += SetLog(if (reps > 0) reps else cur.target, kg = kg.takeIf { move.weighted })
                    reps = 0
                    toast = null
                    Feedback.success()
                    phase = if (sets.size >= cur.sets) Phase.Effort else Phase.Rest
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("Skip move", Modifier.weight(1f)) {
                        toast = "${move.name} di-skip · 0 XP untuk move ni"
                        nextExercise(skipped = true)
                    }
                    SecondaryButton("Stop & save", Modifier.weight(1f), color = SadoColors.Danger) { quitOpen = true }
                }
                Text(
                    "Stop & discard",
                    color = SadoColors.Danger, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { discardOpen = true }.padding(vertical = 12.dp),
                )
                Card {
                    Text("Tips form", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    move.tips.forEachIndexed { i, t -> Text("${i + 1}. $t", color = SadoColors.TextMuted, fontSize = 13.sp) }
                }
            }
            Phase.Effort -> EffortAsk(move.name) { rir ->
                for (i in sets.indices) sets[i] = sets[i].copy(rir = rir)
                if (idx == plan.lastIndex) { logs += ExerciseLog(cur.id, sets.toList()); sets.clear(); finish(false) } else phase = Phase.Rest
            }
            Phase.Rest -> Rest(
                seconds = settings.restSeconds,
                nextLabel = if (sets.size >= cur.sets) "Lepas ni · move ${idx + 2} / ${plan.size}" else "Lepas ni · set ${sets.size + 1} / ${cur.sets}",
                next = if (sets.size >= cur.sets) plan[idx + 1] else cur,
                onDone = { if (sets.size >= cur.sets) nextExercise(skipped = false) else phase = Phase.Work },
            )
        }
    }

    if (quitOpen) {
        val partial = logs.toList() + listOf(ExerciseLog(cur.id, sets.toList()))
        val xpNow = Progress.sessionXp(plan, partial)
        val doneEx = logs.count { !it.skipped }
        AlertDialog(
            onDismissRequest = { quitOpen = false },
            containerColor = SadoColors.Surface,
            icon = { Image(painterResource(R.drawable.face_sad), null, Modifier.size(88.dp)) },
            title = { Text("Stop sekarang?", color = SadoColors.Text, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Siap $doneEx / ${plan.size} move${if (sets.isNotEmpty()) " + ${sets.size} set" else ""}. Satu set pun dikira.\n\nKalau stop & save: +$xpNow XP\nKalau habiskan: lebih banyak XP",
                        color = SadoColors.TextMuted,
                    )
                    Text(
                        "Stop & discard (tak simpan apa-apa)",
                        color = SadoColors.Danger, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clickable { quitOpen = false; discardOpen = true }.padding(vertical = 8.dp),
                    )
                }
            },
            confirmButton = { TextButton({ quitOpen = false }) { Text("Continue", color = SadoColors.Accent, fontWeight = FontWeight.ExtraBold) } },
            dismissButton = { TextButton({ quitOpen = false; if (phase == Phase.Warmup && logs.isEmpty() && sets.isEmpty()) onExit() else finish(true) }) { Text("Stop & save", color = SadoColors.Text) } },
        )
    }

    if (discardOpen) AlertDialog(
        onDismissRequest = { discardOpen = false },
        containerColor = SadoColors.Surface,
        title = { Text("Stop & discard?", color = SadoColors.Text, fontWeight = FontWeight.ExtraBold) },
        text = { Text("Sesi ni takkan disimpan. Tiada XP, tiada rekod, streak tak dikira. Tak boleh undo.", color = SadoColors.TextMuted) },
        confirmButton = { TextButton({ discardOpen = false }) { Text("Continue", color = SadoColors.Accent, fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { TextButton({ discardOpen = false; Coach.say(Lines.cancel); onExit() }) { Text("Discard", color = SadoColors.Danger) } },
    )
}

// the load on a weighted move, in KG_STEP steps; a backpack is loaded with books or water bottles (1 litre = 1 kg)
@Composable
private fun LoadPicker(kg: Float, onChange: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Beban tambahan: +${"%.1f".format(kg)} kg", color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text("Isi beg: 1 botol air 1.5 L ≈ 1.5 kg", color = SadoColors.TextMuted, fontSize = 12.sp)
        }
        SecondaryButton("−", Modifier.size(width = 56.dp, height = 48.dp)) { onChange((kg - Progress.KG_STEP).coerceAtLeast(0f)) }
        SecondaryButton("+", Modifier.size(width = 56.dp, height = 48.dp)) { onChange(kg + Progress.KG_STEP) }
    }
}

@Composable
private fun TargetCard(target: String, camReps: Int?) {

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(SadoColors.Accent.copy(alpha = 0.10f)).border(2.dp, SadoColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(28.dp)).padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TARGET", color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
        Text(target, color = SadoColors.Text, fontSize = 56.sp, lineHeight = 60.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        Text(
            if (camReps != null) "Camera kira: $camReps rep" else "Buat sampai tinggal 1–2 rep, then tekan Set done",
            color = SadoColors.TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
    }
}

// asked once per move after its last set; the answer drives the move ladder
@Composable
private fun EffortAsk(moveName: String, onAnswer: (Int) -> Unit) {
    Title("Macam mana tadi?", moveName)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.face_think), null, Modifier.height(110.dp))
    }
    Text("Jawapan ni tentukan target rep sesi depan.", color = SadoColors.TextMuted, fontSize = 13.sp)
    listOf(
        Triple("Sangat easy", "Boleh buat 5+ rep lagi · target +2", 4),
        Triple("Easy", "Boleh buat 3 rep lagi · target +1", 3),
        Triple("Ngam", "Tinggal 1–2 rep je · target kekal", 1),
        Triple("Susah", "Tak sampai target · target −1", 0),
    ).forEachIndexed { i, (label, sub, rir) ->
        val c = listOf(SadoColors.Accent, SadoColors.Sky, SadoColors.Violet, SadoColors.Streak)[i]
        Row(
            Modifier.fillMaxWidth().pressable { Feedback.tap(); onAnswer(rir) }.clip(RoundedCornerShape(20.dp))
                .background(c.copy(alpha = 0.12f)).border(2.dp, c.copy(alpha = 0.5f), RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = c, fontSize = 22.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Text(sub, color = SadoColors.TextMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TimedSet(target: Int, onSeconds: (Int) -> Unit) {
    var running by remember { mutableStateOf(false) }
    var secs by remember { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            secs++
            onSeconds(secs)
            if (secs == target) Feedback.success()
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(200.dp).clip(CircleShape).background(SadoColors.Accent.copy(alpha = 0.1f)).border(4.dp, SadoColors.Accent, CircleShape)
                .clickable { running = !running; Feedback.tap() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${secs}s", color = SadoColors.Text, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (running) "tekan untuk pause" else "tekan untuk start", color = SadoColors.TextMuted, fontSize = 13.sp)
            }
        }
        Bar(secs.toFloat() / target.coerceAtLeast(1))
    }
}

@Composable
private fun Warmup(onDone: () -> Unit, onQuit: () -> Unit) {
    val items = if (my.sado.app.data.Progress.lowImpact(my.sado.app.data.Store.state.value)) Catalog.warmupLow else Catalog.warmup
    var i by rememberSaveable { mutableIntStateOf(0) }
    var left by rememberSaveable { mutableIntStateOf(items[0].second) }
    LaunchedEffect(i) {
        while (left > 0) {
            delay(1000)
            left--
            if (left in 1..3) Feedback.countdown()
        }
        Feedback.success()
        if (i < items.lastIndex) { left = items[i + 1].second; i++ } else onDone()
    }
    Title("Warm-up", "1 minit je · badan panas, kurang risiko injury") { onQuit() }
    Card(padding = 24) {
        Text(items[i].first, color = SadoColors.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(items[i].third, color = SadoColors.TextMuted, fontSize = 13.sp)
        Text("${left}s", color = SadoColors.Accent, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
        Bar(1f - left.toFloat() / items[i].second)
    }
    if (items.size > 1) items.forEachIndexed { k, (name, secs, _) ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text((if (k < i) "✓ " else "") + name, color = if (k == i) SadoColors.Accent else SadoColors.TextMuted, fontSize = 13.sp)
            Text("${secs}s", color = SadoColors.TextMuted, fontSize = 13.sp)
        }
    }
    PrimaryButton("Skip warm-up", onClick = onDone)
}

@Composable
private fun Rest(seconds: Int, nextLabel: String, next: PlannedExercise, onDone: () -> Unit) {
    var left by rememberSaveable { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
            if (left in 1..3) Feedback.countdown()
            if (left == 10 && seconds > 15) Coach.say(Lines.restEnding)
        }
        Feedback.success()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Title("Rest", "$seconds s · otot membesar masa rest")
        Spacer(Modifier.weight(1f))
        LiveHr()
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.face_cheer), null, Modifier.height(110.dp))
            Text("%d:%02d".format(left / 60, left % 60), color = if (left <= 5) SadoColors.Streak else SadoColors.Text, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (left == 0) "Jom continue!" else "Ambik nafas, shake tangan", color = SadoColors.TextMuted, fontSize = 13.sp)
        }
    }
    Bar(1f - left.toFloat() / seconds, if (left <= 5) SadoColors.Streak else SadoColors.Accent)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("−15s", Modifier.weight(1f)) { left = (left - 15).coerceAtLeast(0) }
        SecondaryButton("+15s", Modifier.weight(1f)) { left += 15 }
    }
    PrimaryButton(if (left == 0) "Jom" else "Skip rest", onClick = onDone)
    NextUp(nextLabel, next)
}

// what comes after the rest: the move's picture, or its muscles lit up when it has none, with name and dose
@Composable
internal fun NextUp(label: String, e: PlannedExercise) {
    val m = Catalog.get(e.id)
    Card {
        Label(label)
        if (MoveImages.ready(LocalContext.current, e.id)) MoveImage(e.id)
        else Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface2).padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MuscleMap(m.muscles, m.secondary, back = false, modifier = Modifier.size(width = 64.dp, height = 136.dp))
            MuscleMap(m.muscles, m.secondary, back = true, modifier = Modifier.size(width = 64.dp, height = 136.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.name, color = SadoColors.Text, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Text(m.muscles.joinToString(" · ") { Catalog.muscleNames.getValue(it) }, color = SadoColors.TextMuted, fontSize = 13.sp)
            }
            Text("${e.sets} × ${e.reps}", color = SadoColors.Accent, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun InfoChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 10.dp),
        color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false,
    )
}

@Composable
fun DoneScreen(onHome: () -> Unit) {
    val s = Store.state.collectAsState().value
    val log = s.logs.lastOrNull() ?: return
    val before = Progress.stageFor(s, s.xp - log.xp)
    val now = Progress.stageFor(s)
    val levelUp = now.index > before.index
    LaunchedEffect(Unit) { if (levelUp) { Feedback.levelUp(); Coach.say(Lines.levelUp(now)) } }
    val next = Progress.next(s)
    val totalReps = log.exercises.sumOf { e -> e.sets.sumOf { it.reps } }
    val skipped = log.exercises.count { it.skipped }
    // nothing done (everything skipped): calm face, no party
    val zero = log.xp == 0
    // badges this workout unlocked: shown once, then remembered as seen
    val fresh = remember { my.sado.app.data.Achievements.fresh(s) }
    LaunchedEffect(Unit) { if (fresh.isNotEmpty()) Store.update { it.copy(seenAchievements = it.seenAchievements + fresh.map { a -> a.id }) } }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (levelUp) Tag("LEVEL UP · ${before.name} → ${now.name}", filled = true)
        // level up shows the new body inside its new aura, with the amazed face beside it
        if (levelUp) Row(Modifier.fillMaxWidth().height(260.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Aura(now, Modifier.weight(1f).fillMaxHeight()) { Floating { Image(painterResource(now.image), null, Modifier.height(230.dp)) } }
            Image(painterResource(R.drawable.face_wow), null, Modifier.height(140.dp))
        } else Box(Modifier.fillMaxWidth().height(260.dp).background(SadoColors.HeroGlow), contentAlignment = Alignment.Center) {
            Floating { Image(painterResource(if (zero) R.drawable.pose1 else R.drawable.pose4), null, Modifier.height(240.dp)) }
        }
        CountUp(log.xp, prefix = "+", suffix = " XP", color = SadoColors.Accent, size = 56)
        if (log.bonus > 0) Tag("COMEBACK · XP x2 (+${log.bonus})", filled = true)
        if (skipped > 0) Text("$skipped move di-skip", color = SadoColors.Streak, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Text(when { zero -> "Hari ni semua skip."; log.quit -> "Stop awal pun tetap dikira."; else -> "Kau muncul hari ni." }, color = SadoColors.Text, fontSize = 28.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        if (zero) Text("Takpe, esok try satu move pun jadi.", color = SadoColors.TextMuted, fontSize = 15.sp, textAlign = TextAlign.Center)
        Text(
            if (next != null) "Ke ${next.name}: " + Progress.requirement(next, s) + "." else "Level maximum. Beast mode!",
            color = SadoColors.TextMuted, fontSize = 16.sp, textAlign = TextAlign.Center,
        )
        Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Stat("${log.minutes}", "minit")
                Stat("$totalReps", "rep")
                Stat("${Progress.streak(s).days}", "streak", SadoColors.Streak)
            }
            if (log.avgHr != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Stat("${log.avgHr}", "HR purata")
                Stat("${log.maxHr}", "Max HR", SadoColors.Danger)
            }
        }
        NewAchievements(fresh)
        PrimaryButton("BALIK KE HOME", onClick = onHome)
    }
    if (!zero) Confetti()
    }
}
