package my.sado.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import my.sado.app.Reminders
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.sado.app.data.Settings
import my.sado.app.data.Store
import my.sado.app.data.Wger

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = SadoColors.Accent, checkedThumbColor = SadoColors.Bg))
    }
}

@Composable
fun SettingsScreen(onProfile: () -> Unit, onPlanner: () -> Unit, onReset: () -> Unit, onBackup: () -> Unit, onHealth: () -> Unit, onVoice: () -> Unit, onBack: (() -> Unit)? = null, onRest: () -> Unit = {}, onHealthCheck: () -> Unit = {}) {
    val s = Store.state.collectAsState().value
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    fun set(change: (Settings) -> Settings) = Store.update { it.copy(settings = change(it.settings)) }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        set { it.copy(reminders = granted) }
        Reminders.schedule(context)
    }
    val times = listOf(6 to 0, 7 to 0, 18 to 0, 21 to 0)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Setting", "Sado v" + my.sado.app.BuildConfig.VERSION_NAME + " · rojak + suara", onBack = onBack)
        Card {
            Text("Tema", color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Segmented(listOf("Gelap", "Cerah", "Ikut phone"), s.settings.theme) { v -> set { it.copy(theme = v) } }
        }
        BackgroundCard(s.settings.background)
        PathCard()
        Card {
            Toggle("Bunyi", "Bip rep, kira undur, set siap, suara haiwan", s.settings.sound) { v -> set { it.copy(sound = v) } }
            Toggle("Getaran", "Setiap rep dan butang", s.settings.haptics) { v -> set { it.copy(haptics = v) } }
            Toggle("Warm-up automatik", "Jumping jack 1 minit sebelum setiap session", s.settings.warmup) { v -> set { it.copy(warmup = v) } }
            Toggle("Suara coach", if (s.settings.aiVoice) "Suara AI ElevenLabs" else "TTS phone · Bahasa Melayu kalau ada", s.settings.voice) { v -> set { it.copy(voice = v) } }
            Toggle("Kira rep guna camera AI", "Default bila start move yang support camera", s.settings.cameraReps) { v -> set { it.copy(cameraReps = v) } }
            Toggle("Peringatan character", "Tegur hari workout & bila lama tak datang", s.settings.reminders) { v ->
                if (v && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    set { it.copy(reminders = v) }
                    Reminders.schedule(context)
                }
            }
        }
        if (s.settings.reminders) {
            Label("Waktu peringatan")
            Segmented(times.map { (h, m) -> "%d:%02d".format(h, m) }, times.indexOf(s.settings.reminderHour to s.settings.reminderMinute).coerceAtLeast(0)) { i ->
                set { it.copy(reminderHour = times[i].first, reminderMinute = times[i].second) }
                Reminders.schedule(context)
            }
        }
        Card {
            Toggle("Kunci app", "Cap jari atau PIN phone bila buka Sado", s.settings.appLock) { v -> set { it.copy(appLock = v) } }
            Toggle("Log makan dengan AI", "Claude Haiku · teks dihantar ke Anthropic", s.settings.foodAi) { v -> set { it.copy(foodAi = v) } }
        }
        Label("Rest antara set")
        Segmented(listOf("60s", "90s", "120s"), listOf(60, 90, 120).indexOf(s.settings.restSeconds).coerceAtLeast(0)) { i ->
            set { it.copy(restSeconds = listOf(60, 90, 120)[i]) }
        }
        Text("90s disyorkan untuk bina otot. Lebih dari 90s tak bagi tambahan ketara.", color = SadoColors.TextMuted, fontSize = 13.sp)
        Label("Lagi")
        Card(padding = 12) {
            MenuRow("Suara coach AI", "ElevenLabs · suara santai", SadoColors.Pink, SadoIcons.Mic, onVoice)
            MenuRow("Jam & kesihatan", "Garmin, Health Connect, heart rate", SadoColors.Streak, SadoIcons.Watch, onHealth)
            MenuRow("Backup & pulih", "Fail .sado dengan kata laluan", SadoColors.Sky, Icons.Filled.Refresh, onBackup)
            MenuRow("Ubah profil", "Umur, tinggi, berat", SadoColors.Violet, Icons.Filled.Person, onProfile)
            MenuRow("Ubah plan", "Jawab 10 soalan semula", SadoColors.Accent, SadoIcons.Dumbbell, onPlanner)
            MenuRow("Mod rehat", "Sakit, exam, bersalin, Ramadan", SadoColors.Sky, SadoIcons.Timer, onRest)
            MenuRow("Soalan kesihatan", "Jawab semula kalau kesihatan berubah", SadoColors.Streak, Icons.Filled.Person, onHealthCheck)
        }
        Card(padding = 12) {
            MenuRow("Padam semua data", "Tak boleh undo", SadoColors.Danger, Icons.Filled.Delete) { confirm = true }
        }
        Card {
            Text("Kredit", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text("Data move dan otot: wger.de, lesen CC-BY-SA 4.0, oleh penyumbang wger. Gambar move: free-exercise-db (public domain) dan wger (CC-BY-SA 4.0). Dimuat turun ${Wger.fetched.ifBlank { "2026" }}.", color = SadoColors.TextMuted, fontSize = 13.sp)
            Text("Semua data kau disimpan dalam phone je. Tiada akaun, tiada server.", color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        if (s.settings.devMode) DevCard { set { it.copy(devMode = false) } }
        // seven taps on the version turn on developer mode
        var taps by remember { mutableStateOf(0) }
        Text(
            "Sado v" + my.sado.app.BuildConfig.VERSION_NAME + if (!s.settings.devMode && taps in 3..6) " · ${7 - taps} lagi" else "",
            color = SadoColors.TextMuted, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                if (s.settings.devMode) return@clickable
                taps++
                if (taps >= 7) { set { it.copy(devMode = true) }; taps = 0; Feedback.success() }
            }.padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        containerColor = SadoColors.Surface,
        title = { Text("Padam semua data?", color = SadoColors.Text, fontWeight = FontWeight.ExtraBold) },
        text = { Text("Profil, plan, XP dan sejarah workout akan hilang. Tak boleh undo.", color = SadoColors.TextMuted) },
        confirmButton = { TextButton({ confirm = false }) { Text("Batal", color = SadoColors.Accent) } },
        dismissButton = { TextButton({ confirm = false; Store.reset(); onReset() }) { Text("Padam", color = SadoColors.Danger) } },
    )
}
