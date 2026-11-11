package my.sado.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.sado.app.R
import my.sado.app.data.Catalog
import my.sado.app.data.Progress
import my.sado.app.data.Store
import java.time.LocalDate

@Composable
fun HomeScreen(onStart: () -> Unit, onPlanner: () -> Unit, onPlanView: () -> Unit, onJourney: () -> Unit, onSport: () -> Unit, onTest: () -> Unit, onHealth: () -> Unit, onSettings: () -> Unit = {}, onPet: () -> Unit = {}, onRest: () -> Unit = {}, onHealthCheck: () -> Unit = {}, onPetEvolve: () -> Unit = {}, onMini: () -> Unit = {}) {
    val s = Store.state.collectAsState().value
    var feeding by remember { mutableStateOf<PetFeed?>(null) }
    var heroWidth by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val stage = Progress.stageFor(s)
    val next = Progress.next(s)
    val streak = Progress.streak(s).days
    var streakHelp by remember { mutableStateOf(false) }
    if (streakHelp) StreakDialog(s) { streakHelp = false }
    // the weekly check: runs once a week on opening, its card stays until read
    val check = s.checkins.lastOrNull()?.takeIf { !it.seen }
    var checkOpen by remember { mutableStateOf(false) }
    if (checkOpen && check != null) WeeklyCheckDialog(check, onUndo = { Store.update { my.sado.app.data.WeeklyCheck.undo(it, check) }; checkOpen = false }) {
        Store.update { st -> st.copy(checkins = st.checkins.map { if (it == check) it.copy(seen = true) else it }) }
        checkOpen = false
    }
    val tag = Progress.todayTag(s)
    val today = LocalDate.now()
    val doneToday = s.logs.any { it.epochDay == today.toEpochDay() }
    // the character's mood: cheering after a workout, sleepy on rest days or after 2+ days off, else calling you in
    val restDay = s.plan?.week?.getOrNull(today.dayOfWeek.value - 1) == "Rest"
    val idleDays = Progress.idleDays(s, today) ?: 0
    val idle = idleDays >= 2
    val tired = Progress.isTired(s, today)
    val dropIn = Progress.daysToDrop(s, today)
    val best = Progress.stageFor(s, s.peakXp)
    val resting = my.sado.app.data.Care.active(s, today.toEpochDay())
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // Ramadan's dates for this year, once; no network or a changed feed just means manual rest mode
        val year = LocalDate.now().year
        if (Store.state.value.ramadan?.year != year) {
            val got = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { my.sado.app.data.Care.fetchRamadan(year) }
            if (got != null) Store.update { it.copy(ramadan = got) }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        Store.dropTiers()
        val day = LocalDate.now().toEpochDay()
        if (my.sado.app.data.WeeklyCheck.due(Store.state.value, day)) Store.update { my.sado.app.data.WeeklyCheck.run(it, day) }
        // the coach says it once per tired spell and once per tier lost, not on every visit
        val st = Store.state.value
        if (!Progress.isTired(st)) return@LaunchedEffect
        val key = "${st.decayFrom}:${st.decaySteps}:" + Progress.activeDays(st).maxOrNull()
        if (st.breakTold == key) return@LaunchedEffect
        Store.update { it.copy(breakTold = key) }
        my.sado.app.Coach.say(if (Progress.dropped(st)) my.sado.app.Lines.dropped(Progress.stageFor(st)) else my.sado.app.Lines.tired)
    }
    val face = when { doneToday -> R.drawable.face_cheer; restDay || idle -> R.drawable.face_sleepy; else -> R.drawable.face_point }
    val days = listOf("I", "S", "R", "K", "J", "S", "A")
    val monday = today.minusDays(today.dayOfWeek.value - 1L)
    val health = rememberHealthToday()
    var n = 0 // stagger order for entrance animation

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // header
        Row(Modifier.fillMaxWidth().appear(n++), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(face), contentDescription = null, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(SadoColors.Surface))
            Column(Modifier.weight(1f)) {
                Text(when { doneToday -> "Hari ni dah settle"; resting != null -> "Mod rehat,"; restDay -> "Hari rest,"; idle -> "Lama tak nampak ko,"; else -> "Jom gerak," }, color = SadoColors.TextMuted, fontSize = 14.sp)
                Text(s.profile?.name?.ifBlank { null } ?: "Kau", color = SadoColors.Text, fontSize = 34.sp, lineHeight = 36.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            }
            Row(
                Modifier.pressable { streakHelp = true }.clip(RoundedCornerShape(20.dp)).background(SadoColors.Streak.copy(alpha = 0.14f))
                    .border(1.dp, SadoColors.Streak.copy(alpha = 0.3f), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(SadoIcons.Flame, contentDescription = null, tint = SadoColors.Streak, modifier = Modifier.size(22.dp))
                Text("$streak", color = SadoColors.Streak, fontSize = 22.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            }
            // settings left the tab bar to make room for the flex button
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface).border(1.dp, SadoColors.Outline, RoundedCornerShape(16.dp))
                    .pressable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Settings, contentDescription = "Setting", tint = SadoColors.TextMuted) }
        }

        if (check != null) WeeklyCheckCard(check, Modifier.appear(n++)) { checkOpen = true }
        if (my.sado.app.data.Care.ramadanOffer(s)) RamadanOfferCard(Modifier.appear(n++))
        if (resting != null) Callout(
            "Mod rehat: " + my.sado.app.data.Care.label(resting.reason),
            "Hari ke-${today.toEpochDay() - resting.from + 1}. Level dan streak selamat. Tekan untuk panduan.",
            SadoColors.Sky, Modifier.appear(n++), onRest,
        )
        if (my.sado.app.data.Care.healthDue(s)) Callout(
            "Soalan kesihatan",
            if (s.health == null) "Ambil seminit sebelum sambung: app nak pastikan senaman ni selamat untuk kau." else "Dah setahun. Jawab semula kalau-kalau ada yang berubah.",
            SadoColors.Streak, Modifier.appear(n++), onHealthCheck,
        )

        // hero: the character floats over a lime glow
        Box(
            Modifier.fillMaxWidth().appear(n++).pressable(onClick = onJourney).clip(RoundedCornerShape(32.dp))
                .background(SadoColors.Surface).border(1.dp, SadoColors.Outline, RoundedCornerShape(32.dp)),
        ) {
            // the chosen backdrop fills the card from its top edge down to the character's floor, stuck to the
            // bottom so the floor stays put whatever the height; the name and progress sit below it
            val backdrop = my.sado.app.data.Backgrounds.image(s.settings.background, SadoColors.dark)
            Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
            if (backdrop != null) Image(painterResource(backdrop), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter)
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, end = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Tag("LEVEL ${stage.index + 1} / ${Progress.stages.size}", filled = true)
                    Box(Modifier.weight(1f))
                    // on a picture the link gets a plate, so it reads over any sky
                    Row(
                        (if (backdrop != null) Modifier.clip(RoundedCornerShape(12.dp)).background(SadoColors.Surface.copy(alpha = 0.85f)).padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp) else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Perjalanan", color = SadoColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SadoColors.TextMuted)
                    }
                }
                // a Legenda pet stands taller than the character, so the hero grows to fit him
                val petStage = PetDev.shown(s)?.let { my.sado.app.data.Pets.stage(it.first) } ?: 0
                // dev mode "Uji watak" can show any level, tired or not, without touching progress
                val dev = CharDev.override
                val shown = dev?.stage ?: stage
                val tiredShown = dev?.tired ?: tired
                Aura(shown, Modifier.fillMaxWidth().height(if (petStage == 3) 320.dp else if (petStage == 2) 250.dp else 230.dp).onSizeChanged { heroWidth = it.width }, dim = tiredShown, glow = if (backdrop != null) 0.5f else 1f) {
                    val pic = if (tiredShown) Progress.tiredImage(shown) else shown.image
                    // feet stay on the same floor as the pet, whatever the box height
                    val picPainter = painterResource(pic)
                    val picW = 200.dp * picPainter.intrinsicSize.let { if (it.height > 0f) it.width / it.height else 0.5f }
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).zIndex(-2f)) { Floating { CastShadow(picPainter, picW, 200.dp) } }
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) { Floating { Box { Image(picPainter, contentDescription = "Character ko, level ${shown.name}" + if (tiredShown) ", letih" else "", modifier = Modifier.height(200.dp)); BlinkLids(pic, Modifier.matchParentSize()) } } }
                    if (tiredShown) Zzz(Modifier.align(Alignment.TopEnd).padding(end = 48.dp, top = 4.dp))
                    // the food button's middle, as PetBuddy measures it: dp from the centre, dp above its floor
                    val foodFrom = androidx.compose.ui.geometry.Offset(heroWidth / androidx.compose.ui.platform.LocalDensity.current.density / 2 - 28f, 16f)
                    PetBuddy(s, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), feed = feeding, foodFrom = foodFrom, onFed = { feeding = null })
                    // one logged meal at a time; the next comes up once this one is eaten
                    val dev = PetDev.food.isNotEmpty()
                    val queue = if (dev) PetDev.food else if (PetDev.override == null) my.sado.app.data.Pets.foodQueue(s) else emptyList()
                    val waiting = queue.firstOrNull()
                    val fed = PetDev.shown(s)?.first
                    if (waiting != null && fed != null && feeding == null) PetFoodButton(
                        waiting, queue.size, my.sado.app.data.Pets.nameOf(fed), Modifier.align(Alignment.BottomEnd).zIndex(6f),
                    ) {
                        // paid at the tap, so leaving Home during the animation loses nothing; dev mode's food pays nothing
                        if (dev) PetDev.food = PetDev.food.drop(1) else Store.update { my.sado.app.data.Pets.feed(it) }
                        feeding = PetFeed(waiting.icon, waiting.exp)
                    }
                }
            }
            }
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = if (backdrop != null) 16.dp else 10.dp, end = 20.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stage.name.uppercase(), color = SadoColors.Text, fontSize = 36.sp, lineHeight = 38.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                if (next != null) {
                    Bar((s.xp - stage.at).toFloat() / (next.at - stage.at).coerceAtLeast(1), height = 12)
                    Text("${s.xp} XP · ke ${next.name}: " + Progress.requirement(next, s), color = SadoColors.TextMuted, fontSize = 13.sp)
                } else Text("${s.xp} XP · level maximum", color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            }
        }

        PetCard(s, Modifier.appear(n++), onEvolve = onPetEvolve, onOpen = onPet)

        // bento stats
        Row(Modifier.appear(n++), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // header already shows the streak, so this tile tracks the week instead
            val weekDone = s.logs.count { it.epochDay >= monday.toEpochDay() }
            val weekPlan = s.plan?.week?.count { it != "Rest" } ?: 0
            Tile("$weekDone/$weekPlan", "sesi minggu ni", SadoColors.Streak, SadoIcons.Dumbbell, Modifier.weight(1f), onClick = onJourney)
            Tile("${s.xp}", "jumlah XP", SadoColors.Accent, SadoIcons.Bolt, Modifier.weight(1f), onClick = onJourney)
        }
        Row(Modifier.appear(n++), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("${Progress.weeklyActiveMinutes(s)}/150", "minit aktif minggu ni", SadoColors.Sky, SadoIcons.Timer, Modifier.weight(1f), onClick = onSport)
            if (s.settings.healthConnect && health.steps != null)
                Tile("%,d".format(health.steps), "langkah hari ni", SadoColors.Violet, SadoIcons.Steps, Modifier.weight(1f), onClick = onHealth)
            else
                Tile("${s.logs.size}", "jumlah sesi", SadoColors.Violet, Icons.Filled.Star, Modifier.weight(1f), onClick = onJourney)
        }

        // time off: tired after a week, a tier lost from day 21; coming back counts double
        when {
            tired && Progress.dropped(s) -> Callout(
                "Turun ke ${stage.name}",
                "Dah $idleDays hari tak gerak. Satu workout je, XP x2 sampai balik ke ${best.name}." + (dropIn?.let { " Turun lagi dalam $it hari." } ?: ""),
                SadoColors.Streak, Modifier.appear(n++), onStart,
            )
            tired -> Callout(
                "Dia dah lembik",
                "Dah $idleDays hari tak gerak." + (dropIn?.let { " $it hari lagi sebelum turun level." } ?: "") + " Satu workout pun cukup untuk segar balik.",
                SadoColors.Streak, Modifier.appear(n++), onStart,
            )
            Progress.dropped(s) -> Callout(
                "Comeback: XP x2",
                "Setiap workout dapat XP double sampai ko balik ke ${best.name}.",
                SadoColors.Accent, Modifier.appear(n++),
            )
        }
        if (Progress.isDeloadWeek(s)) Callout("Deload week", "Setiap minggu ke-5, satu set dikurangkan supaya sendi dan otot pulih. Bukan malas, ini strategi.", SadoColors.Sky, Modifier.appear(n++))
        if (resting == null && Progress.testDue(s)) Callout(
            "Ujian 4 minggu dah tiba",
            "Push-up max, plank max" + (if ((s.answers?.pull ?: -1) >= 0) ", pull-up max" else "") + ". Bukti progress paling jujur.",
            SadoColors.Pink, Modifier.appear(n++), onTest,
        )

        if (s.plan != null) {
            // week strip
            Row(Modifier.fillMaxWidth().appear(n++), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEachIndexed { i, d ->
                    val date = monday.plusDays(i.toLong())
                    val logged = s.logs.any { it.epochDay == date.toEpochDay() }
                    val rest = s.plan.week[i] == "Rest"
                    val isToday = date == today
                    Column(
                        Modifier.weight(1f).aspectRatio(0.72f).clip(RoundedCornerShape(16.dp))
                            .background(if (logged) SadoColors.Accent else SadoColors.Surface)
                            .border(2.dp, if (isToday && !logged) SadoColors.Accent else SadoColors.Outline, RoundedCornerShape(16.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(d, color = if (logged) SadoColors.OnAccent else SadoColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("${date.dayOfMonth}", color = if (logged) SadoColors.OnAccent else if (rest) SadoColors.TextMuted else SadoColors.Text, fontSize = 18.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            // today's session
            Column(
                Modifier.fillMaxWidth().appear(n++).pressable(onClick = onPlanView).clip(RoundedCornerShape(28.dp))
                    .background(SadoColors.Surface).border(1.dp, SadoColors.Outline, RoundedCornerShape(28.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label(if (tag == "Rest") "Rest day" else "Sesi hari ni")
                        Text(s.plan.sessionOn().label, color = SadoColors.Text, fontSize = 26.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(
                        "Ubah plan", color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.pressable(onClick = onPlanner).clip(RoundedCornerShape(14.dp)).background(SadoColors.Accent.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                // move chips
                s.plan.sessionOn().exercises.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { e ->
                            Row(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(SadoColors.Surface2).padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("${e.sets}×", color = SadoColors.Accent, fontSize = 14.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                                Text(Catalog.get(e.id).name, color = SadoColors.Text, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
            if (s.answers?.pull == -1) Callout("Nak belakang lebar?", "Pull-up bar pintu (RM40–80) paling berbaloi untuk V-shape. Untuk kaki, isi beg galas dengan buku sebagai beban.", SadoColors.Violet, Modifier.appear(n++))
            Box(Modifier.appear(n++)) { PrimaryButton(if (doneToday) "WORKOUT LAGI" else "START WORKOUT", onClick = onStart) }
            // a hard day: a short real session, or an honest rest, both better than tapping through
            if (!doneToday) Row(Modifier.fillMaxWidth().appear(n++), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Sesi mini · 10 min", Modifier.weight(1f), onClick = onMini)
                val rested = today.toEpochDay() in s.honestRests
                SecondaryButton(
                    if (rested) "Rehat jujur ✓" else "Rehat jujur (${my.sado.app.data.HonestRest.left(s)})", Modifier.weight(1f),
                    color = if (my.sado.app.data.HonestRest.canTake(s)) SadoColors.Sky else SadoColors.TextMuted,
                ) { if (my.sado.app.data.HonestRest.canTake(s)) Store.update { my.sado.app.data.HonestRest.take(it) } }
            }

            Text(
                "Main futsal atau jogging hari ni? Log sukan",
                color = SadoColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().pressable(onClick = onSport).padding(vertical = 12.dp),
            )
        } else {
            Callout("Belum ada plan", "Jawab 10 soalan, plan terus siap ikut badan kau.", SadoColors.Accent, Modifier.appear(n++))
            PrimaryButton("BINA PLAN", onClick = onPlanner)
        }

        if (s.logs.isNotEmpty()) {
            Label("Sesi terkini")
            s.logs.takeLast(3).reversed().forEach { log ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface).border(1.dp, SadoColors.Outline, RoundedCornerShape(20.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(SadoColors.Accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(SadoIcons.Dumbbell, null, tint = SadoColors.Accent, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(log.title, color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${LocalDate.ofEpochDay(log.epochDay)} · ${log.minutes} min" + if (log.quit) " · stop awal" else "", color = SadoColors.TextMuted, fontSize = 13.sp)
                    }
                    Text("+${log.xp}", color = SadoColors.Accent, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Box(Modifier.height(8.dp))
    }
}

/** Gentle idle bob so the character feels alive. */
@Composable
fun Floating(content: @Composable () -> Unit) {
    if (LocalReduceMotion.current) { content(); return }
    // idle breathing with the feet planted: a slight even swell from the floor up, no bobbing in the air
    val t = rememberInfiniteTransition(label = "breathe")
    val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    Box(Modifier.graphicsLayer { transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f); scaleX = 1f + 0.012f * y; scaleY = 1f + 0.012f * y }) { content() }
}

/** Coloured info block with a side stripe; tappable when [onClick] is given. */
@Composable
fun Callout(title: String, body: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.10f)).border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
    ) {
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = color, fontSize = 16.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text(body, color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = color, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 12.dp))
    }
}
