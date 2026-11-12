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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableLongStateOf
import my.sado.app.data.AppState
import my.sado.app.data.Burn
import my.sado.app.data.GiveUp
import my.sado.app.data.Health
import my.sado.app.data.HrAnalysis
import my.sado.app.data.SessionSummary
import my.sado.app.data.HonestRest
import my.sado.app.data.Integrity
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
    // "mini:<source>": Sesi mini, one set per move, about 8–10 minutes
    val mini = source.startsWith("mini:")
    val src = source.removePrefix("mini:")
    val mine = s.myTemplates.firstOrNull { it.id == src.removePrefix("my:") }.takeIf { src.startsWith("my:") }
    // the user's own workouts keep the reps they chose; plan and templates climb from the bottom of each range
    val (title, given) = if (mine != null) {
        mine.name to mine.moves.map { it.planned() }
    } else if (src.startsWith("tpl:")) {
        val t = Templates.all.first { it.id == src.removePrefix("tpl:") }
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
    if (mini) return "Sesi mini · $title" to list.map { it.copy(sets = 1) }
    // deload week and short sleep (<6h) each take one set off every move; recovery drives growth.
    // A move failed two sessions running loses one more set, this session only.
    val shortSleep = s.sleep.lastOrNull { it.epochDay == LocalDate.now().toEpochDay() }?.let { it.hours < 6f } == true
    val cut = (if (Progress.isDeloadWeek(s)) 1 else 0) + (if (shortSleep) 1 else 0)
    val eased = list.any { it.id in s.deload }
    val tag = listOfNotNull("deload week".takeIf { Progress.isDeloadWeek(s) }, "kurang tidur".takeIf { shortSleep }, "ringan sikit".takeIf { eased })
    return (if (tag.isEmpty()) title else "$title (${tag.joinToString(", ")})") to
        list.map { it.copy(sets = (it.sets - cut - (if (it.id in s.deload) 1 else 0)).coerceAtLeast(1)) }
}

// when the session screen left composition (for Info), and which session that was, so a later session never inherits it;
// process memory is enough, a killed process loses the session anyway
private var pausedAt = 0L
private var pausedSession = 0L

private class Pending(val quit: Boolean, val logs: List<ExerciseLog>, val hr: List<my.sado.app.data.HrPoint>, val verdict: Integrity.Verdict) {
    val avg get() = hr.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()?.toInt()
}

@Composable
fun SessionScreen(source: String, onExercise: (String) -> Unit, onFinished: () -> Unit, onExit: () -> Unit, onMini: () -> Unit = {}) {
    val settings = Store.state.collectAsState().value.settings
    val (title, initial) = remember { sessionFor(source) }
    val mini = source.startsWith("mini:")
    // everything that tracks progress is saveable, so opening Info and coming back resumes the same set
    val startedAt = rememberSaveable { System.currentTimeMillis() }
    var pausedMs by rememberSaveable { mutableLongStateOf(0L) }
    var hrStarted by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    DisposableEffect(Unit) {
        if (pausedAt > 0 && pausedSession == startedAt) pausedMs += System.currentTimeMillis() - pausedAt
        pausedAt = 0
        if (settings.hrMode == 1) settings.hrDevice?.let { if (HeartRate.hasPermissions(context)) HeartRate.connect(context, it) }
        if (hrStarted) HeartRate.resume() else { HeartRate.startRecording(); hrStarted = true }
        onDispose { HeartRate.pause(); pausedAt = System.currentTimeMillis(); pausedSession = startedAt }
    }
    // resting heart rate from Health Connect, for the flat-HR check
    var restingHr by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        if (settings.healthConnect) restingHr = withContext(Dispatchers.IO) { runCatching { Health.restingEstimate(context) }.getOrNull() }
    }
    if (initial.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Title("Takde move", "Plan atau template ni kosong untuk equipment ko.", onBack = onExit)
        }
        return
    }
    // the moves can change mid-session ("Tak larat" twice: one rung down); the ones swapped out still earn their sets
    val plan = rememberSaveable(saver = listSaver(PlannedExercise.serializer())) { mutableStateListOf<PlannedExercise>().apply { addAll(initial) } }
    val swappedOut = rememberSaveable(saver = listSaver(PlannedExercise.serializer())) { mutableStateListOf() }
    fun activeNow() = System.currentTimeMillis() - startedAt - pausedMs

    var phase by rememberSaveable { mutableStateOf(if (settings.warmup) Phase.Warmup else Phase.Work) }
    var idx by rememberSaveable { mutableIntStateOf(0) }
    var reps by rememberSaveable { mutableIntStateOf(0) }
    val sets = rememberSaveable(saver = listSaver(SetLog.serializer())) { mutableStateListOf() }
    val logs = rememberSaveable(saver = listSaver(ExerciseLog.serializer())) { mutableStateListOf() }
    var spoken by rememberSaveable { mutableStateOf("") }
    var quitOpen by remember { mutableStateOf(false) }
    var discardOpen by remember { mutableStateOf(false) }
    var giveUpOpen by remember { mutableStateOf(false) }
    var dropTo by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var offerOpen by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val cur = plan[idx]
    val move = Catalog.get(cur.id)
    // the extra kg for a weighted move; changing it here carries to the next session
    var kg by rememberSaveable(idx) { mutableFloatStateOf(Progress.load(Store.state.value.loads, cur.id)) }
    // after "Tak larat": this move's next sets ask for less and rest longer
    var tgt by rememberSaveable(idx, cur.id) { mutableStateOf<Int?>(null) }
    var extraRest by rememberSaveable(idx, cur.id) { mutableIntStateOf(0) }
    val target = tgt ?: cur.target
    val label = tgt?.let { if (move.timed) "${it}s" else "$it" } ?: cur.reps
    // when the current set started, on the active clock (pauses left out)
    var setKey by rememberSaveable { mutableStateOf("") }
    var setStart by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(idx, phase, sets.size, cur.id) {
        val k = "$idx-${cur.id}-${sets.size}"
        if (phase == Phase.Work && k != setKey) { setKey = k; setStart = activeNow() }
    }
    LaunchedEffect(idx, phase) {
        // don't repeat the line when coming back from Info
        val key = "$idx-$phase-${sets.size}"
        if (key == spoken) return@LaunchedEffect
        spoken = key
        when (phase) {
            Phase.Warmup -> Coach.say(if (my.sado.app.data.Progress.lowImpact(my.sado.app.data.Store.state.value)) Lines.warmupLow else Lines.warmup)
            Phase.Work -> if (sets.isEmpty()) Coach.sayParts(Coach.intro(move.name, cur.sets, cur.reps)) else Coach.say(Lines.set(sets.size + 1, last = sets.size + 1 == cur.sets))
            Phase.Effort -> Coach.say(Lines.effort)
            Phase.Rest -> Coach.say(Lines.rest(settings.restSeconds + extraRest))
        }
    }

    fun everyMove() = plan.toList() + swappedOut

    fun save(p: Pending, accepted: Boolean) {
        val all = p.logs
        val xp = Progress.sessionXp(everyMove(), all)
        val end = System.currentTimeMillis()
        val seconds = (activeNow() / 1000).toInt().coerceAtLeast(1)
        val minutes = (seconds / 60).coerceAtLeast(1)
        // said yes but the watch barely moved: accepted, without the comeback bonus
        val bonus = if (accepted && p.verdict.hrFlat) 0 else Progress.comebackBonus(Store.state.value, xp)
        val (kcal, kcalSource) = Burn.estimate(all, Store.state.value.profile, seconds, p.avg)
        Store.update { s ->
            // Sesi mini keeps the streak and the pet, but one set per move says nothing about strength
            val c = if (mini) Progress.Climb(s.ladders, s.boosts, s.loads)
            else Progress.climb(s.ladders, s.boosts, everyMove(), all, s.loads, { PlanBuilder.usable(it, s.answers) }, s.logs)
            // back from a break: XP counts double until the best reached before
            s.copy(
                xp = s.xp + xp + bonus,
                peakXp = maxOf(s.peakXp, s.xp + xp + bonus),
                logs = s.logs + WorkoutLog(
                    LocalDate.now().toEpochDay(), title, minutes, all, xp + bonus, p.quit, p.avg, p.hr.maxOfOrNull { it.bpm }, bonus,
                    seconds = seconds, kcal = kcal, kcalSource = kcalSource, startMs = startedAt, endMs = end, doubtful = accepted, mini = mini,
                ).let { if (p.hr.size >= HrAnalysis.MIN_POINTS) HrAnalysis.withHr(it, p.hr, "Broadcast", restingHr, s.profile) else it }
                    .let { SessionSummary.ups(s.ladders, c.ladders, s.loads, c.loads, all).let { u -> it.copy(climbed = u.climbed, kgNext = u.kgNext) } },
                ladders = c.ladders,
                boosts = c.boosts,
                loads = c.loads,
                deload = if (mini) s.deload else GiveUp.nextDeload(s.deload, all, s.logs),
                gearToday = null, // the next workout starts from the quiz's gear again
                plan = s.plan?.let { pl -> s.answers?.let { a -> pl.mapExercises { PlanBuilder.refresh(it, a, c.ladders) } } } ?: s.plan,
            )
        }
        Feedback.success()
        val s = Store.state.value
        Coach.say(when { xp == 0 -> Lines.zero; bonus > 0 && s.xp >= s.peakXp -> Lines.backAtBest; bonus > 0 -> Lines.comeback; p.quit -> Lines.quit; else -> Lines.done })
        onFinished()
    }

    fun finish(quit: Boolean) {
        val raw = logs.toList() + if (sets.isNotEmpty() && logs.none { it.id == cur.id }) listOf(ExerciseLog(cur.id, sets.toList())) else emptyList()
        val timedTarget = { id: String -> everyMove().firstOrNull { it.id == id }?.takeIf { Catalog.get(id).timed }?.target }
        val all = Integrity.flag(raw, timedTarget)
        val hr = HeartRate.stopRecording()
        val p = Pending(quit, all, hr, Integrity.verdict(all, hr.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()?.toInt(), restingHr))
        // too quick to be real: ask kindly before saving anything
        if (p.verdict.doubtful && Progress.sessionXp(everyMove(), all) > 0) pending = p else save(p, accepted = false)
    }

    fun nextExercise(skipped: Boolean) {
        logs += ExerciseLog(cur.id, sets.toList(), skipped)
        sets.clear(); reps = 0
        if (idx == plan.lastIndex) finish(false) else { idx++; phase = Phase.Work }
    }

    fun logSet(done: Int, failed: Boolean) {
        val now = activeNow()
        sets += SetLog(done, kg = kg.takeIf { move.weighted }, failed = failed, secs = ((now - setStart) / 1000).toInt(), at = (now / 1000).toInt(), endMs = System.currentTimeMillis())
        reps = 0
        toast = null
        if (failed) {
            GiveUp.after(cur, target, done)?.let { n -> tgt = n.target; extraRest += n.extraRest }
            if (GiveUp.offerDrop(sets, cur.sets)) dropTo = GiveUp.rungBelow(cur.id, Store.state.value.answers)
        }
        phase = if (sets.size >= cur.sets) Phase.Effort else Phase.Rest
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
                key(idx, sets.size, cur.id) {
                    if (move.timed) TimedSet(target) { secs -> reps = secs }
                    else TargetCard(label, if (camOn) reps else null)
                }
                if (tgt != null) Text("Target diturunkan ke $label lepas set tadi · rest +${extraRest}s", color = SadoColors.Sky, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                if (move.weighted) LoadPicker(kg) { kg = it }
                // set pips: done sets fill lime (a "Tak larat" set orange), the current one is outlined
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    repeat(cur.sets) { i ->
                        val done = i < sets.size
                        Box(
                            Modifier.size(width = 36.dp, height = 10.dp).clip(RoundedCornerShape(5.dp))
                                .background(if (done && sets[i].failed) SadoColors.Streak else if (done) SadoColors.Accent else SadoColors.Track)
                                .border(2.dp, if (i == sets.size) SadoColors.Accent else Color.Transparent, RoundedCornerShape(5.dp)),
                        )
                    }
                }
                if (toast != null) Text(toast!!, color = SadoColors.Streak, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                // no rep tapping: one press per set, reps count as the target unless the camera or timer measured them
                PrimaryButton(if (sets.size + 1 >= cur.sets) "Set done · last set" else "Set done") {
                    Feedback.success()
                    logSet(if (reps > 0) reps else target, failed = false)
                }
                SecondaryButton("Tak larat · give up set ni", Modifier.fillMaxWidth(), color = SadoColors.Streak) { giveUpOpen = true }
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
            Phase.Rest -> {
                val restFor = settings.restSeconds + extraRest
                Rest(
                    seconds = restFor,
                    nextLabel = if (sets.size >= cur.sets) "Lepas ni · move ${idx + 2} / ${plan.size}" else "Lepas ni · set ${sets.size + 1} / ${cur.sets}",
                    next = if (sets.size >= cur.sets) plan[idx + 1] else cur.copy(reps = label, target = target),
                    onDone = { left ->
                        if (sets.isNotEmpty() && Integrity.restSkipped(restFor, left)) sets[sets.lastIndex] = sets.last().copy(restSkipped = true)
                        if (sets.size >= cur.sets) nextExercise(skipped = false) else phase = Phase.Work
                    },
                )
            }
        }
    }

    if (giveUpOpen) GiveUpDialog(move.timed, target, if (move.timed) reps else (target - 1).coerceAtLeast(0), onDismiss = { giveUpOpen = false }) { done ->
        giveUpOpen = false
        Feedback.tap()
        logSet(done, failed = true)
    }

    dropTo?.let { lower ->
        val m = Catalog.get(lower)
        val left = cur.sets - sets.size
        AlertDialog(
            onDismissRequest = { dropTo = null },
            containerColor = SadoColors.Surface,
            title = { Text("Dua set berturut tak larat", color = SadoColors.Text, fontWeight = FontWeight.ExtraBold) },
            text = { Text("Tukar ke ${m.name} untuk $left set selebihnya? Form cantik lagi bagus dari rep yang terpaksa.", color = SadoColors.TextMuted) },
            confirmButton = {
                TextButton({
                    val a = Store.state.value.answers
                    val (_, reps2, target2) = if (a != null) PlanBuilder.dose(m, a) else Triple(left, cur.reps, cur.target)
                    val next = Progress.boosted(PlannedExercise(lower, left, reps2, target2), Store.state.value.boosts)
                    logs += ExerciseLog(cur.id, sets.toList())
                    swappedOut += cur
                    sets.clear()
                    plan[idx] = next
                    dropTo = null
                }) { Text("Ya, turun", color = SadoColors.Accent, fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = { TextButton({ dropTo = null }) { Text("Tak, teruskan", color = SadoColors.Text) } },
        )
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = SadoColors.Surface,
            icon = { Image(painterResource(R.drawable.face_think), null, Modifier.size(88.dp)) },
            title = { Text("Sesi ni laju gila 😅 Betul buat?", color = SadoColors.Text, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "${p.verdict.flagged} / ${p.verdict.total} set siap terlalu cepat" + (if (p.verdict.hrFlat) ", dan heart rate hampir sama macam rehat" else "") +
                        ". Set yang laju tak dikira untuk naik level move, supaya plan tak jadi lagi susah dari yang badan mampu.",
                    color = SadoColors.TextMuted,
                )
            },
            confirmButton = { TextButton({ pending = null; save(p, accepted = true) }) { Text("Ya, buat", color = SadoColors.Accent, fontWeight = FontWeight.ExtraBold) } },
            dismissButton = { TextButton({ pending = null; offerOpen = true }) { Text("Tak, skip je hari ni", color = SadoColors.Text) } },
        )
    }

    if (offerOpen) {
        val s = Store.state.collectAsState().value
        AlertDialog(
            onDismissRequest = { offerOpen = false; onExit() },
            containerColor = SadoColors.Surface,
            title = { Text("Takpe, jujur tu bagus", color = SadoColors.Text, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Takde penalti. Pilih satu:", color = SadoColors.TextMuted)
                    if (!mini) PrimaryButton("Sesi mini · 8–10 minit") { offerOpen = false; onMini() }
                    if (HonestRest.canTake(s)) SecondaryButton("Rehat jujur hari ni · streak selamat (${HonestRest.left(s)} lagi minggu ni)", Modifier.fillMaxWidth()) {
                        Store.update { HonestRest.take(it) }
                        offerOpen = false
                        onExit()
                    }
                }
            },
            confirmButton = { TextButton({ offerOpen = false; onExit() }) { Text("Balik", color = SadoColors.Text) } },
        )
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

// "Tak larat": how many reps (or seconds) were really done before stopping
@Composable
private fun GiveUpDialog(timed: Boolean, target: Int, start: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var done by remember { mutableIntStateOf(start.coerceIn(0, target)) }
    val step = if (timed) 5 else 1
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SadoColors.Surface,
        title = { Text("Tak larat? Takpe.", color = SadoColors.Text, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (timed) "Berapa saat sempat tahan?" else "Berapa rep betul-betul siap?", color = SadoColors.TextMuted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SecondaryButton("−", Modifier.size(width = 56.dp, height = 48.dp)) { done = (done - step).coerceAtLeast(0) }
                    Text(if (timed) "${done}s" else "$done", color = SadoColors.Text, fontSize = 44.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    SecondaryButton("+", Modifier.size(width = 56.dp, height = 48.dp)) { done = (done + step).coerceAtMost(target) }
                }
                Text("Target: ${if (timed) "${target}s" else "$target"}. Set lepas ni akan disesuaikan, dan move ni tak naik level sesi ni.", color = SadoColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = { TextButton({ onSave(done) }) { Text("Simpan set", color = SadoColors.Accent, fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { TextButton(onDismiss) { Text("Batal", color = SadoColors.Text) } },
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
private fun Rest(seconds: Int, nextLabel: String, next: PlannedExercise, onDone: (left: Int) -> Unit) {
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
    PrimaryButton(if (left == 0) "Jom" else "Skip rest") { onDone(left) }
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
    // Health Connect has the real burn for this window when a watch synced it: prefer that and say so
    val context = LocalContext.current
    // the watch's readings arrive once it syncs: look again every 30 s while this screen is open
    LaunchedEffect(log.startMs) {
        if (!s.settings.healthConnect || log.startMs == null) return@LaunchedEffect
        repeat(20) {
            if (Store.state.value.logs.lastOrNull()?.hr?.isNotEmpty() == true) return@LaunchedEffect
            runCatching { HrAnalysis.backfill(context) }
            kotlinx.coroutines.delay(30_000)
        }
    }

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
                Stat(log.seconds?.let { "%d:%02d".format(it / 60, it % 60) } ?: "${log.minutes}", if (log.seconds != null) "masa" else "minit")
                Stat("$totalReps", "rep")
                Stat("${Progress.streak(s).days}", "streak", SadoColors.Streak)
            }
            if (log.kcal != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { Stat("${log.kcal}", "kcal dibakar", SadoColors.Streak) }
                Text(
                    when (log.kcalSource) { "Health Connect" -> "Dari Health Connect"; "HR jam" -> "Anggaran dari heart rate jam"; else -> "Anggaran MET (tanpa jam)" } +
                        " · tak ditambah ke target makan, semakan mingguan dah urus kalori.",
                    color = SadoColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        SummaryCard(log, s.logs.dropLast(1))
        HrReportCard(log, waiting = s.settings.healthConnect)
        NewAchievements(fresh)
        PrimaryButton("BALIK KE HOME", onClick = onHome)
    }
    if (!zero) Confetti()
    }
}

/** Per move: what was done, a record, the change since last time, sets that didn't count, and what goes up next. */
@Composable
private fun SummaryCard(log: WorkoutLog, earlier: List<WorkoutLog>) {
    val rows = remember(log) { SessionSummary.rows(log, earlier) }
    if (rows.isEmpty()) return
    // muscles by the sets that counted; helpers count half, as on the plan
    val worked = remember(log) { Heat.sets(log.exercises.filter { !it.skipped }.map { it.id to Integrity.real(it).size }.filter { it.second > 0 }) }
    Card {
        Text("Ringkasan sesi", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        if (worked.isNotEmpty()) {
            val top = worked.entries.sortedByDescending { it.value }.take(4).map { it.key }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(0.9f)) { BodyHeat(worked, week = false, height = 140, labels = false) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Otot dilatih", color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(top.joinToString(", ") { Catalog.muscleNames[it] ?: it }, color = SadoColors.TextMuted, fontSize = 13.sp)
                }
            }
            HeatLegend(week = false)
        }
        rows.forEach { r ->
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, color = if (r.skipped) SadoColors.TextMuted else SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(if (r.skipped) "skip" else r.sets, color = if (r.skipped) SadoColors.Streak else SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
                val notes = buildList {
                    if (r.pr) add("\uD83C\uDFC6 Rekod baru")
                    if (r.first) add("Kali pertama")
                    r.delta?.let { d -> if (d != 0) add((if (d > 0) "+" else "") + "$d berbanding sesi lepas") else add("Sama macam sesi lepas") }
                    if (r.flagged > 0) add("${r.flagged} set terlalu laju, tak dikira")
                }
                if (notes.isNotEmpty()) Text(
                    notes.joinToString(" · "),
                    color = when { r.pr -> SadoColors.Accent; r.flagged > 0 -> SadoColors.Streak; else -> SadoColors.TextMuted },
                    fontSize = 12.sp, fontWeight = if (r.pr) FontWeight.ExtraBold else FontWeight.Normal,
                )
            }
        }
        val name = { id: String -> Catalog.moves.firstOrNull { it.id == id }?.name ?: id }
        if (log.climbed.isNotEmpty() || log.kgNext.isNotEmpty()) {
            Text("Naik lepas ni", color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            log.climbed.forEach { (from, to) -> Text("${name(from)} \u2192 ${name(to)}", color = SadoColors.Text, fontSize = 13.sp) }
            log.kgNext.forEach { (id, k) -> Text("${name(id)}: tambah berat ke ${if (k % 1f == 0f) k.toInt() else k} kg", color = SadoColors.Text, fontSize = 13.sp) }
        }
    }
}
