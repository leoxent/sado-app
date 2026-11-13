package my.sado.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.health.connect.client.PermissionController
import my.sado.app.HeartRate
import my.sado.app.data.HcSession
import my.sado.app.data.HrAnalysis
import my.sado.app.data.HrPoint
import my.sado.app.data.WorkoutLog
import my.sado.app.data.Catalog
import my.sado.app.data.Health
import my.sado.app.data.HealthToday
import my.sado.app.data.Progress
import my.sado.app.data.SleepLog
import my.sado.app.data.SportLog
import my.sado.app.data.Store
import java.time.LocalDate

private fun fmt(n: Long) = "%,d".format(n)

/** Counts each time the screen comes back to the front, so reads made on it can run again. */
@Composable
fun rememberResumes(): Int {
    val owner = LocalLifecycleOwner.current
    var n by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) n++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return n
}

/** Today's Health Connect numbers; read again whenever [key] changes or the app comes back to the front. */
@Composable
fun rememberHealthToday(key: Any = Unit): HealthToday {
    val context = LocalContext.current
    val enabled = Store.state.collectAsState().value.settings.healthConnect
    val resumes = rememberResumes()
    return produceState(HealthToday(), key, enabled, resumes) { if (enabled) value = Health.today(context) }.value
}

/** Compact strip for Home: steps, burned kcal and resting HR from the watch. */
@Composable
fun HealthStrip(onClick: () -> Unit) {
    val context = LocalContext.current
    val s = Store.state.collectAsState().value
    val h = rememberHealthToday()
    val resumes = rememberResumes()
    // newest reading and resting rate for the heart line; recent sessions pick up their heart rate here too
    val live by produceState<Pair<HrPoint?, Int?>>(null to null, resumes, s.settings.healthConnect) {
        if (s.settings.healthConnect) value = runCatching { Health.latestHr(context) to Health.restingEstimate(context) }.getOrDefault(null to null)
    }
    LaunchedEffect(resumes, s.settings.healthConnect) { runCatching { HrAnalysis.backfill(context) } }
    val (pt, resting) = live
    if (pt == null && h.steps == null && h.totalKcal == null) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface).clickable { onClick() }.padding(vertical = 12.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (pt != null) {
            val ago = System.currentTimeMillis() - pt.t
            val st = HrAnalysis.state(pt.bpm, resting, s.profile?.age ?: 25)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("❤️ ${pt.bpm} bpm", color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                if (ago > HrAnalysis.STALE_MS) Text("data lama · buka Garmin Connect", color = SadoColors.TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                else {
                    Text(st.label, color = stateColor(st), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text(agoText(ago), color = SadoColors.TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Stat(h.steps?.let { fmt(it) } ?: "–", "langkah")
            Stat(h.totalKcal?.toString() ?: "–", "kcal dibakar", SadoColors.Streak)
            Stat(resting?.toString() ?: "–", "Resting HR")
        }
    }
}

/** Home's heart line: the watch's newest reading from Health Connect and what it means right now. */
@Composable
fun HrBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val s = Store.state.collectAsState().value
    if (!s.settings.healthConnect) return
    val resumes = rememberResumes()
    var loaded by remember { mutableStateOf(false) }
    val live by produceState<Pair<HrPoint?, Int?>>(null to null, resumes) {
        value = runCatching { Health.latestHr(context) to Health.restingEstimate(context) }.getOrDefault(null to null)
        loaded = true
    }
    // recent sessions pick up their heart rate whenever Home comes back
    LaunchedEffect(resumes) { runCatching { HrAnalysis.backfill(context) } }
    if (!loaded) return
    val (pt, resting) = live
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface).clickable { onClick() }.padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("\u2764\ufe0f " + (pt?.let { "${it.bpm} bpm" } ?: "–"), color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        val ago = pt?.let { System.currentTimeMillis() - it.t }
        when {
            pt == null -> Text("Tiada bacaan HR · buka Garmin Connect untuk sync", color = SadoColors.TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            ago!! > HrAnalysis.STALE_MS -> Text("data lama (${agoText(ago)}) · buka Garmin Connect", color = SadoColors.TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            else -> {
                val st = HrAnalysis.state(pt.bpm, resting, s.profile?.age ?: 25)
                Text(st.label, color = stateColor(st), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text(agoText(ago) + (resting?.let { " · rehat $it" } ?: ""), color = SadoColors.TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
    }
}

private fun stateColor(st: HrAnalysis.State) = when (st) {
    HrAnalysis.State.Rest -> SadoColors.Sky
    HrAnalysis.State.Active -> SadoColors.Accent
    HrAnalysis.State.Workout -> SadoColors.Streak
}

private fun agoText(ms: Long): String {
    val min = ms / 60_000
    return when { min < 1 -> "baru tadi"; min < 60 -> "$min min lepas"; else -> "${min / 60} jam lepas" }
}

/** A session's heart rate: average and peak, time in each state, and per set when the readings are dense enough. */
@Composable
fun HrReportCard(log: WorkoutLog, waiting: Boolean) {
    val p = Store.state.collectAsState().value.profile
    val pts = remember(log) { my.sado.app.data.HrStore.points(log) }
    val r = HrAnalysis.analyze(pts, log.exercises, log.restingHr, p?.age ?: 25)
    if (r == null) {
        if (waiting && log.startMs != null) Card {
            Text("Heart rate", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Menunggu data jam. Stop aktiviti kat jam dan buka Garmin Connect untuk sync, lepas tu Sado isi analisis sendiri.",
                color = SadoColors.TextMuted, fontSize = 13.sp,
            )
        }
        return
    }
    Card {
        Text(
            "Heart rate · " + if (log.hrSource == "Broadcast") "live dari jam" else "dari Health Connect",
            color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Stat("${r.avg}", "HR purata")
            Stat("${r.max}", "Max HR", SadoColors.Danger)
            r.rose?.let { Stat(if (it) "✓" else "✗", "HR naik", if (it) SadoColors.Accent else SadoColors.Danger) }
        }
        val states = HrAnalysis.State.entries
        if (r.zones.values.sum() > 0) Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            states.forEach { st -> val v = r.zones[st] ?: 0; if (v > 0) Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(stateColor(st))) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            states.forEach { st -> Text("${st.label} ${((r.zones[st] ?: 0) + 30) / 60} min", color = stateColor(st), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        if (r.rose == false) Text("HR hampir tak naik dari rehat, jadi sesi ni ditanda meragukan.", color = SadoColors.Streak, fontSize = 13.sp)
        if (r.dense && r.sets.isNotEmpty()) {
            Text("Setiap set", color = SadoColors.Text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            r.sets.forEach { st ->
                Row(Modifier.fillMaxWidth()) {
                    Text("${runCatching { Catalog.get(st.move).name }.getOrDefault(st.move)} · set ${st.set}", color = SadoColors.TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("${st.peak} bpm" + (st.drop?.let { " · turun $it" } ?: ""), color = SadoColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Turun = berapa banyak HR jatuh seminit lepas set. Lagi besar, lagi cepat badan pulih.", color = SadoColors.TextMuted, fontSize = 12.sp)
        } else if (!r.dense) Text(
            "Data jarang (${r.points} bacaan). Start Strength kat jam masa workout untuk analisis setiap set.",
            color = SadoColors.TextMuted, fontSize = 12.sp,
        )
    }
}

/** Sleep question before a workout; under 6 hours trims a set from every move (see sessionFor). */
@Composable
fun SleepPicker() {
    val context = LocalContext.current
    val s = Store.state.collectAsState().value
    val today = LocalDate.now().toEpochDay()
    val entry = s.sleep.lastOrNull { it.epochDay == today }
    val resumes = rememberResumes()
    // the watch's figure keeps updating as Garmin syncs; a sleep the user answered is left alone
    LaunchedEffect(s.settings.healthConnect, resumes) {
        if (s.settings.healthConnect && (entry == null || entry.source == "Health Connect"))
            Health.lastNightSleep(context)?.let { h -> if (entry == null || kotlin.math.abs(entry.hours - h) > 0.05f) saveSleep(h, "Health Connect") }
    }
    val pick = entry?.hours?.let { if (it < 6f) 0 else if (it < 7f) 1 else 2 }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Tidur semalam?" + (if (entry?.source == "Health Connect") " · dari jam: %.1f jam".format(entry.hours) else ""),
            color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("< 6 jam" to 5f, "6–7 jam" to 6.5f, "7+ jam" to 7.5f).forEachIndexed { i, (label, hours) ->
                Text(
                    label,
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (pick == i) SadoColors.Accent.copy(alpha = 0.1f) else SadoColors.Surface2)
                        .border(2.dp, if (pick == i) SadoColors.Accent else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { saveSleep(hours, "Jawapan"); Feedback.tap() }
                        .padding(vertical = 10.dp),
                    color = if (pick == i) SadoColors.Accent else SadoColors.Text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                )
            }
        }
        if (pick == 0) Text("Kurang tidur: setiap move kurang 1 set hari ni. Otot membesar masa kau tidur, bukan masa workout.", color = SadoColors.Streak, fontSize = 13.sp)
    }
}

private fun saveSleep(hours: Float, source: String) {
    val today = LocalDate.now().toEpochDay()
    Store.update { st -> st.copy(sleep = st.sleep.filter { it.epochDay != today }.takeLast(60) + SleepLog(today, hours, source)) }
}

@Composable
fun LiveHr(modifier: Modifier = Modifier) {
    val bpm = HeartRate.bpm.collectAsState().value ?: return
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(SadoColors.Pink.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.material3.Icon(SadoIcons.Heart, contentDescription = "Heart rate", tint = SadoColors.Pink, modifier = Modifier.size(18.dp))
        Text("$bpm", color = SadoColors.Pink, fontSize = 16.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun HealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = Store.state.collectAsState().value
    var refresh by remember { mutableIntStateOf(0) }
    val sdk = remember(refresh) { Health.status(context) }
    val granted by produceState(emptySet<String>(), refresh) { value = Health.granted(context) }
    val h = rememberHealthToday(refresh to granted.size)
    val sessions by produceState(emptyList<HcSession>(), refresh, granted.size) { value = if (granted.isNotEmpty()) Health.sessions(context) else emptyList() }
    val scope = rememberCoroutineScope()
    val askHealth = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { got ->
        Store.update { it.copy(settings = it.settings.copy(healthConnect = got.isNotEmpty())) }
        refresh++
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Jam & kesihatan", "Garmin, Samsung, Fitbit melalui Health Connect", onBack = onBack)

        Label("Jam aku")
        MyWatchCard()

        Label("Health Connect")
        when {
            sdk == HealthConnectClient.SDK_UNAVAILABLE -> Card {
                Text("Phone ni tak menyokong Health Connect.", color = SadoColors.TextMuted, fontSize = 13.sp)
            }
            sdk == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Card {
                Text("Pasang atau kemas kini Health Connect dulu", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text("Android 13 ke bawah perlukan app Health Connect dari Play Store.", color = SadoColors.TextMuted, fontSize = 13.sp)
                PrimaryButton("Buka Play Store") {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${Health.PROVIDER}&url=healthconnect%3A%2F%2Fonboarding")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                SecondaryButton("Dah pasang, semak semula", Modifier.fillMaxWidth()) { refresh++ }
            }
            granted.isEmpty() -> Card {
                Text("Connect jam ko", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Sado cuma BACA langkah, kalori, heart rate, tidur dan sesi senaman. Tak tulis apa-apa, tak hantar ke mana-mana.\n\n" +
                        "Untuk Garmin: dalam app Garmin Connect, hidupkan perkongsian ke Health Connect. Kalau Garmin Connect kau tiada pilihan tu, app seperti Health Sync boleh jadi jambatan.",
                    color = SadoColors.TextMuted, fontSize = 13.sp,
                )
                PrimaryButton("Benarkan akses") { askHealth.launch(Health.permissions) }
            }
            else -> {
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Stat(h.steps?.let { fmt(it) } ?: "–", "langkah")
                        Stat(h.totalKcal?.toString() ?: "–", "kcal total", SadoColors.Streak)
                        Stat(h.activeKcal?.toString() ?: "–", "kcal aktif")
                    }
                    Bar(((h.steps ?: 0L) / 8000f).coerceAtMost(1f), height = 6)
                    Text("Target 8,000 steps · kajian tunjuk benefit besar sampai 7–8 ribu sehari", color = SadoColors.TextMuted, fontSize = 13.sp)
                }
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Stat(h.restingHr?.toString() ?: "–", "Resting HR")
                        Stat(h.avgHr?.toString() ?: "–", "HR purata")
                        Stat(if (h.minHr != null && h.maxHr != null) "${h.minHr}–${h.maxHr}" else "–", "julat")
                        Stat(h.sleepHours?.let { "%.1f".format(it) } ?: "–", "jam tidur")
                    }
                    val est = tdee(s.profile)
                    if (h.totalKcal != null && est != null) Text(
                        "Jam kata kau bakar ${h.totalKcal} kcal setakat ni. Anggaran Sado untuk sehari penuh: ~$est kcal. Jam pun boleh lari 10–20%, guna sebagai panduan je.",
                        color = SadoColors.TextMuted, fontSize = 13.sp,
                    )
                }
                if (granted.size < Health.permissions.size) Text(
                    "Sebahagian akses belum dibenarkan, jadi ada nombor kosong. Tekan sini untuk benarkan.",
                    color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clickable { askHealth.launch(Health.permissions) },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Muat semula", Modifier.weight(1f), color = SadoColors.Accent) { refresh++; Feedback.tap() }
                    SecondaryButton("Putuskan", Modifier.weight(1f)) {
                        scope.launch {
                            Health.disconnect(context)
                            Store.update { it.copy(settings = it.settings.copy(healthConnect = false)) }
                            refresh++
                        }
                    }
                }

                Label("Import sukan (7 hari)")
                val imported = s.sports.mapNotNull { it.hcId }.toSet()
                val fresh = sessions.filter { it.id !in imported }
                if (fresh.isEmpty()) Text(
                    if (sessions.isEmpty()) "Tiada sesi senaman dalam Health Connect minggu ni." else "Semua sesi dah diimport.",
                    color = SadoColors.TextMuted, fontSize = 13.sp,
                )
                fresh.forEach { ses -> ImportRow(ses) }
                SecondaryButton("Muat semula", Modifier.fillMaxWidth()) { refresh++ }
                Text(
                    "Urus akses Health Connect",
                    color = SadoColors.TextMuted, fontSize = 13.sp,
                    modifier = Modifier.clickable { runCatching { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                )
            }
        }

        val myWatch = my.sado.app.data.Watch.brand(s.settings.watch)
        if (s.settings.watch != my.sado.app.data.Watch.NONE) {
            Label("HR masa workout")
            val broadcast = myWatch == null || myWatch.canBroadcast || myWatch.id == "other"
            if (broadcast) Segmented(listOf("Mod aktiviti", "Broadcast"), s.settings.hrMode.coerceIn(0, 1)) { i ->
                Store.update { it.copy(settings = it.settings.copy(hrMode = i)) }
                if (i == 0) HeartRate.disconnect()
            }
            val name = myWatch?.label ?: "jam"
            Text(
                if (!broadcast || s.settings.hrMode == 0) "Mula aktiviti senaman kat $name masa workout. Lepas jam sync, Sado isi analisis HR sendiri. Tak makan bateri lebih, takde pairing. Perlukan Health Connect di atas."
                else "On Broadcast Heart Rate kat jam (paling senang: dalam setting aktiviti Strength). HR live masa sesi dan analisis terus lepas sesi. Makan bateri jam masa broadcast.",
                color = SadoColors.TextMuted, fontSize = 13.sp,
            )
            if (broadcast && s.settings.hrMode == 1) HeartRateCard()
        }
        s.logs.lastOrNull()?.let { last ->
            Label("Sesi terakhir · ${last.title}")
            HrReportCard(last, waiting = s.settings.healthConnect)
        }
    }
}

@Composable
private fun ImportRow(ses: HcSession) {
    val s = Store.state.collectAsState().value
    val kcal = ses.kcal ?: Progress.sportKcal(ses.sport, ses.minutes, 1, s.profile?.weightKg ?: 65f)
    val xp = Progress.sportXp(ses.minutes)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(ses.title, color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text("${LocalDate.ofEpochDay(ses.epochDay)} · ${ses.minutes} min · ${if (ses.kcal != null) "" else "~"}$kcal kcal · ${ses.app}", color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        Text(
            "+$xp XP",
            Modifier.clip(RoundedCornerShape(12.dp)).background(SadoColors.Accent).clickable {
                Store.update { st ->
                    if (st.sports.any { it.hcId == ses.id }) st
                    else st.copy(xp = st.xp + xp, sports = st.sports + SportLog(ses.epochDay, ses.sport.name, ses.minutes, 1, kcal, xp, ses.id))
                }
                Feedback.success()
            }.padding(horizontal = 12.dp, vertical = 8.dp),
            color = SadoColors.OnAccent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun HeartRateCard() {
    val context = LocalContext.current
    val s = Store.state.collectAsState().value
    val status = HeartRate.status.collectAsState().value
    val found = HeartRate.found.collectAsState().value
    val bpm = HeartRate.bpm.collectAsState().value
    val problem = HeartRate.problem.collectAsState().value
    var manual by remember { mutableStateOf(false) }
    var then by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun save(d: HeartRate.Found) {
        Store.update { it.copy(settings = it.settings.copy(hrDevice = d.address, hrDeviceName = d.name)) }
        HeartRate.connect(context, d.address)
        Feedback.tap()
    }
    // the system picker's answer: the watch chosen, saved, then connected
    val pickResult = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) HeartRate.picked(r.data)?.let { save(it) }
    }
    // permission first; the step that needed it runs once it is granted
    val askBle = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) then?.invoke() else HeartRate.denied()
        then = null
    }
    fun withPermission(step: () -> Unit) {
        if (HeartRate.hasPermissions(context)) step() else { then = step; askBle.launch(HeartRate.permissions) }
    }
    fun pick() = withPermission {
        HeartRate.pick(context, launch = { sender -> pickResult.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build()) }) {
            manual = true
            HeartRate.scan(context)
        }
    }
    // a watch picked before reconnects when this opens, so the pick is a one-time thing
    LaunchedEffect(Unit) { HeartRate.reconnectSaved(context, s.settings.hrDevice) }
    DisposableEffect(Unit) { onDispose { HeartRate.stopScan(context) } }

    Card {
        Text(
            "Di jam Garmin: buka menu kawalan (tahan butang LIGHT) atau Setting → Sensor → Wrist Heart Rate → Broadcast Heart Rate. Lepas tu tekan Pilih jam.",
            color = SadoColors.TextMuted, fontSize = 13.sp,
        )
        if (s.settings.hrDevice != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.settings.hrDeviceName ?: "Jam", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        when (status) {
                            HeartRate.Status.Connected -> "Bersambung"
                            HeartRate.Status.Connecting -> "Menyambung…"
                            else -> "Auto sambung bila buka app atau workout"
                        },
                        color = if (status == HeartRate.Status.Connected) SadoColors.Accent else SadoColors.TextMuted, fontSize = 13.sp,
                    )
                }
                if (bpm != null) LiveHr()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status == HeartRate.Status.Connected || status == HeartRate.Status.Connecting) {
                    SecondaryButton("Putuskan", Modifier.weight(1f), color = SadoColors.Accent) { HeartRate.disconnect() }
                } else {
                    SecondaryButton("Sambung semula", Modifier.weight(1f), color = SadoColors.Accent) {
                        withPermission { HeartRate.connect(context, s.settings.hrDevice) }
                    }
                }
                SecondaryButton("Lupakan", Modifier.weight(1f)) {
                    HeartRate.disconnect()
                    Store.update { it.copy(settings = it.settings.copy(hrDevice = null, hrDeviceName = null)) }
                }
            }
        }
        PrimaryButton(if (s.settings.hrDevice == null) "Pilih jam" else "Tukar jam", enabled = status != HeartRate.Status.Scanning) { pick() }
        Text(
            if (status == HeartRate.Status.Scanning) "Mencari…" else "Tak jumpa dalam senarai? Cari manual",
            color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().clickable { manual = true; withPermission { HeartRate.scan(context) } }.padding(vertical = 6.dp),
        )
        if (manual) found.forEach { d ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SadoColors.Surface2).clickable { save(d) }.padding(12.dp),
            ) {
                Text(d.name, color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("Pilih", color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        problem?.let { p ->
            Text(p.text, color = SadoColors.Streak, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (p == HeartRate.Problem.BluetoothOff) SecondaryButton("Buka setting Bluetooth", Modifier.fillMaxWidth()) {
                runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
            }
            if (p == HeartRate.Problem.NoPermission) SecondaryButton("Buka kebenaran app", Modifier.fillMaxWidth()) {
                runCatching {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)))
                }
            }
        }
    }
}
