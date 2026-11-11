package my.sado.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.health.connect.client.PermissionController
import my.sado.app.HeartRate
import my.sado.app.data.HcSession
import my.sado.app.data.Health
import my.sado.app.data.HealthToday
import my.sado.app.data.Progress
import my.sado.app.data.SleepLog
import my.sado.app.data.SportLog
import my.sado.app.data.Store
import java.time.LocalDate

private fun fmt(n: Long) = "%,d".format(n)

/** Today's Health Connect numbers; recomputed whenever [key] changes. */
@Composable
fun rememberHealthToday(key: Any = Unit): HealthToday {
    val context = LocalContext.current
    val enabled = Store.state.collectAsState().value.settings.healthConnect
    return produceState(HealthToday(), key, enabled) { if (enabled) value = Health.today(context) }.value
}

/** Compact strip for Home: steps, burned kcal and resting HR from the watch. */
@Composable
fun HealthStrip(onClick: () -> Unit) {
    val h = rememberHealthToday()
    if (h.steps == null && h.totalKcal == null && h.restingHr == null) return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface).clickable { onClick() }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        Stat(h.steps?.let { fmt(it) } ?: "–", "langkah")
        Stat(h.totalKcal?.toString() ?: "–", "kcal dibakar", SadoColors.Streak)
        Stat(h.restingHr?.toString() ?: h.avgHr?.toString() ?: "–", if (h.restingHr != null) "Resting HR" else "HR purata")
    }
}

/** Sleep question before a workout; under 6 hours trims a set from every move (see sessionFor). */
@Composable
fun SleepPicker() {
    val context = LocalContext.current
    val s = Store.state.collectAsState().value
    val today = LocalDate.now().toEpochDay()
    val entry = s.sleep.lastOrNull { it.epochDay == today }
    LaunchedEffect(s.settings.healthConnect) {
        if (entry == null && s.settings.healthConnect) Health.lastNightSleep(context)?.let { h -> saveSleep(h, "Health Connect") }
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
    val askHealth = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { got ->
        Store.update { it.copy(settings = it.settings.copy(healthConnect = got.isNotEmpty())) }
        refresh++
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Jam & kesihatan", "Garmin, Samsung, Fitbit melalui Health Connect", onBack = onBack)

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

        Label("Heart rate live")
        HeartRateCard()
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
    var note by remember { mutableStateOf<String?>(null) }
    val askBle = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) HeartRate.scan(context) else note = "Perlukan kebenaran Bluetooth untuk cari jam."
    }
    DisposableEffect(Unit) { onDispose { HeartRate.stopScan(context) } }

    Card {
        Text(
            "Di jam Garmin: buka menu kawalan (tahan butang LIGHT) atau Setting → Sensor → Wrist Heart Rate → Broadcast Heart Rate. Lepas tu tekan Cari jam.",
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
                            else -> "Auto connect bila workout"
                        },
                        color = SadoColors.TextMuted, fontSize = 13.sp,
                    )
                }
                if (bpm != null) LiveHr()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Uji sambungan", Modifier.weight(1f), color = SadoColors.Accent) {
                    if (!HeartRate.bluetoothOn(context)) note = "Hidupkan Bluetooth dulu."
                    else if (HeartRate.hasPermissions(context)) HeartRate.connect(context, s.settings.hrDevice) else askBle.launch(HeartRate.permissions)
                }
                SecondaryButton("Lupakan", Modifier.weight(1f)) {
                    HeartRate.disconnect()
                    Store.update { it.copy(settings = it.settings.copy(hrDevice = null, hrDeviceName = null)) }
                }
            }
        }
        PrimaryButton(if (status == HeartRate.Status.Scanning) "Mencari…" else "Cari jam", enabled = status != HeartRate.Status.Scanning) {
            note = null
            when {
                !HeartRate.bluetoothOn(context) -> note = "Hidupkan Bluetooth dulu."
                HeartRate.hasPermissions(context) -> HeartRate.scan(context)
                else -> askBle.launch(HeartRate.permissions)
            }
        }
        found.forEach { d ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SadoColors.Surface2).clickable {
                    Store.update { it.copy(settings = it.settings.copy(hrDevice = d.address, hrDeviceName = d.name)) }
                    HeartRate.connect(context, d.address)
                    Feedback.tap()
                }.padding(12.dp),
            ) {
                Text(d.name, color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("Pilih", color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (status == HeartRate.Status.Idle && found.isEmpty() && note == null && s.settings.hrDevice == null)
            Text("Tiada jam dijumpai lagi. Pastikan Broadcast Heart Rate aktif.", color = SadoColors.TextMuted, fontSize = 13.sp)
        note?.let { Text(it, color = SadoColors.Streak, fontSize = 13.sp) }
    }
}
