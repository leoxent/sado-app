package my.sado.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.sado.app.Coach
import my.sado.app.VoiceClips
import my.sado.app.data.Secrets
import my.sado.app.data.Store
import my.sado.app.data.VoiceProfile
import java.io.File

private fun setVoices(change: (List<VoiceProfile>) -> List<VoiceProfile>) =
    Store.update { it.copy(settings = it.settings.copy(voices = change(it.settings.voices))) }

@Composable
fun VoiceScreen(onBack: () -> Unit, onLibrary: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = Store.state.collectAsState().value.settings
    var hasKey by remember { mutableStateOf(Secrets.load(context, "elevenlabs") != null) }
    var keyInput by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) } // bumps clip counts after generate/delete
    var addTab by remember { mutableIntStateOf(0) }
    val groups = remember { Coach.phraseGroups() }
    val phrases = remember(groups) { groups.flatMap { it.lines }.distinct() }
    var pickFor by remember { mutableStateOf<VoiceProfile?>(null) }
    DisposableEffect(Unit) { onDispose { VoiceClips.stop() } }

    fun generateAll(voice: VoiceProfile, phrases: List<String>) {
        val key = Secrets.load(context, "elevenlabs") ?: return
        message = null
        scope.launch {
            var error: String? = null
            // intro names and numbers share one take per batch of ten so their tone matches; the rest go one by one
            val parts = Coach.partLines()
            val jobs = (if (st.voiceBatch) phrases.filter { it in parts }.chunked(10) else phrases.filter { it in parts }.map { listOf(it) }) +
                phrases.filterNot { it in parts }.map { listOf(it) }
            var done = 0
            for (job in jobs) {
                if (error != null) break
                progress = done to phrases.size
                error = withContext(Dispatchers.IO) {
                    if (job.size > 1) VoiceClips.generateBatch(context, key, voice.voiceId, job)
                    else VoiceClips.generate(context, key, voice.voiceId, job.first(), steady = job.first() in parts)
                }
                // a batch of ten lands at once; count it up one clip at a time anyway
                if (job.size == 1) done++ else repeat(job.size) { done++; progress = done to phrases.size; kotlinx.coroutines.delay(90) }
            }
            progress = null
            refresh++
            message = error ?: "Done. ${phrases.size} ayat dah ada suara ${voice.name}."
        }
    }

    fun test(voice: VoiceProfile) {
        val line = Coach.words(my.sado.app.Lines.done.first())
        if (VoiceClips.play(context, voice.voiceId, line)) return
        val key = Secrets.load(context, "elevenlabs") ?: run { message = "Save API key dulu."; return }
        scope.launch {
            message = "Generate sample…"
            message = withContext(Dispatchers.IO) { VoiceClips.generate(context, key, voice.voiceId, line) }
            if (message == null) { VoiceClips.play(context, voice.voiceId, line); refresh++ }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Suara coach AI", "ElevenLabs · lagi santai dari suara phone", onBack = onBack)
        Card {
            Text(
                "Guna akaun ElevenLabs ko sendiri. Setiap ayat di-generate sekali je, lepas tu save dalam phone dan main offline. " +
                    "Yang dihantar ke ElevenLabs cuma text ayat coach (dan sample suara kalau ko clone), bukan data badan atau gambar.",
                color = SadoColors.TextMuted, fontSize = 13.sp,
            )
        }

        Label("API key")
        if (hasKey) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Key dah save (encrypted dalam phone)", color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("Buang", color = SadoColors.Danger, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable {
                Secrets.clear(context, "elevenlabs"); hasKey = false
                Store.update { it.copy(settings = it.settings.copy(aiVoice = false)) }
            }.padding(8.dp))
        } else {
            OutlinedTextField(
                keyInput, { keyInput = it.trim() }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("xi-api-key dari elevenlabs.io → Profile → API keys") },
                visualTransformation = PasswordVisualTransformation(), colors = fieldColors(),
            )
            PrimaryButton("Save key", enabled = keyInput.length > 10) {
                Secrets.save(context, "elevenlabs", keyInput); keyInput = ""; hasKey = true; VoiceClips.creditOut = false
            }
        }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Guna suara AI", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (hasKey) "Ayat baru guna suara phone dulu, clip AI di-generate untuk lain kali" else "Save API key dulu", color = SadoColors.TextMuted, fontSize = 13.sp)
                }
                Switch(st.aiVoice && hasKey, { v -> Store.update { it.copy(settings = it.settings.copy(aiVoice = v)) } }, enabled = hasKey,
                    colors = SwitchDefaults.colors(checkedTrackColor = SadoColors.Accent, checkedThumbColor = SadoColors.Bg))
            }
        }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Intro & nombor dalam satu take", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (st.voiceBatch) "10 ayat satu request, dipotong ikut senyap. Jimat credit, nada sekata."
                        else "Setiap klip dibuat satu-satu. Paling kemas, tapi guna lebih credit.",
                        color = SadoColors.TextMuted, fontSize = 13.sp,
                    )
                }
                Switch(st.voiceBatch, { v -> Store.update { it.copy(settings = it.settings.copy(voiceBatch = v)) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = SadoColors.Accent, checkedThumbColor = SadoColors.Bg))
            }
        }

        Label("Suara aku · ${st.voices.size}")
        Text("Tekan suara untuk guna dalam app. Generate untuk pilih ayat mana nak buat clip (${phrases.size} ayat semua).", color = SadoColors.TextMuted, fontSize = 13.sp)
        progress?.let { (done, total) ->
            Bar(done.toFloat() / total)
            Text("Generate $done / $total…", color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        st.voices.forEach { v ->
            VoiceRow(
                voice = v,
                active = v.voiceId == st.voiceId,
                clips = remember(refresh, v.voiceId) { VoiceClips.count(context, v.voiceId) },
                total = phrases.size,
                busy = progress != null,
                canDelete = st.voices.size > 1,
                onPick = { Store.update { it.copy(settings = it.settings.copy(voiceId = v.voiceId)) }; Feedback.tap() },
                onTest = { test(v) },
                onGenerate = { if (hasKey) pickFor = v else message = "Save API key dulu." },
                onDelete = {
                    VoiceClips.clear(context, v.voiceId); refresh++
                    setVoices { list -> list.filterNot { it.voiceId == v.voiceId } }
                    if (v.voiceId == st.voiceId) Store.update { it.copy(settings = it.settings.copy(voiceId = it.settings.voices.first().voiceId)) }
                },
            )
        }
        message?.let { Text(it, color = SadoColors.Streak, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        SecondaryButton("Library suara · play setiap clip", Modifier.fillMaxWidth(), color = SadoColors.Sky, onClick = onLibrary)
        pickFor?.let { v -> GeneratePicker(v, groups, onDismiss = { pickFor = null }) { chosen -> pickFor = null; generateAll(v, chosen) } }

        Label("Tambah suara")
        Segmented(listOf("ElevenLabs", "Voice ID", "Clone"), addTab) { addTab = it }
        when (addTab) {
            0 -> PickFromElevenLabs(hasKey, st.voices) { r -> setVoices { it + VoiceProfile(r.name, r.id) }; message = "${r.name} masuk library. Tekan dia untuk guna." }
            1 -> ManualVoice(st.voices) { name, id -> setVoices { it + VoiceProfile(name, id) }; message = "$name masuk library." }
            else -> CloneVoice(hasKey) { name, id ->
                setVoices { it + VoiceProfile(name, id, cloned = true) }
                Store.update { it.copy(settings = it.settings.copy(voiceId = id)) }
                message = "Suara $name dah siap clone dan tengah guna. Tekan Generate untuk ayat biasa."
            }
        }
    }
}

// choose which lines to generate; everything is ticked except form cues, and lines that already have a clip are skipped
@Composable
internal fun GeneratePicker(voice: VoiceProfile, groups: List<my.sado.app.PhraseGroup>, onDismiss: () -> Unit, inDialog: Boolean = true, onGo: (List<String>) -> Unit) {
    val context = LocalContext.current
    val have = remember(voice.voiceId) { groups.flatMap { it.lines }.filter { VoiceClips.has(context, voice.voiceId, it) }.toSet() }
    val picked = remember { mutableStateListOf<String>().apply { addAll(groups.filter { it.picked }.flatMap { it.lines }.filter { it !in have }.distinct()) } }
    var open by remember { mutableStateOf<String?>(null) }
    val chars = picked.sumOf { it.length }
    val body: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(28.dp)).background(SadoColors.Surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Generate untuk ${voice.name}", color = SadoColors.Text, fontSize = 22.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text("Tick ayat yang nak dibuat clip. Tekan nama kumpulan untuk tengok setiap ayat.", color = SadoColors.TextMuted, fontSize = 13.sp)
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { g ->
                    val todo = g.lines.filter { it !in have }
                    val on = todo.count { it in picked }
                    item(key = "g-" + g.title) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface2)
                                .clickable { open = if (open == g.title) null else g.title }.padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.TriStateCheckbox(
                                when { todo.isEmpty() || on == todo.size -> androidx.compose.ui.state.ToggleableState.On; on == 0 -> androidx.compose.ui.state.ToggleableState.Off; else -> androidx.compose.ui.state.ToggleableState.Indeterminate },
                                onClick = { if (on == todo.size) picked.removeAll(todo) else todo.filter { it !in picked }.forEach { picked += it } },
                                enabled = todo.isNotEmpty(),
                                colors = CheckboxDefaults.colors(checkedColor = SadoColors.Accent, checkmarkColor = SadoColors.OnAccent),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(g.title, color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                Text(if (todo.isEmpty()) "Semua dah ada clip" else "$on / ${todo.size} dipilih" + if (todo.size < g.lines.size) " · ${g.lines.size - todo.size} dah ada" else "", color = SadoColors.TextMuted, fontSize = 12.sp)
                            }
                            Text(if (open == g.title) "Tutup" else "Tengok", color = SadoColors.Sky, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    if (open == g.title) items(g.lines.size, key = { "l-" + g.title + it }) { i ->
                        val line = g.lines[i]
                        val done = line in have
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !done) { if (line in picked) picked.remove(line) else picked += line }.padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(done || line in picked, null, enabled = !done, colors = CheckboxDefaults.colors(checkedColor = SadoColors.Accent, checkmarkColor = SadoColors.OnAccent))
                            Text(line, color = if (done) SadoColors.TextMuted else SadoColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Text("${picked.size} ayat · ≈ $chars aksara credit", color = SadoColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Batal", Modifier.weight(1f), onClick = onDismiss)
                PrimaryButton("Generate", Modifier.weight(1.4f), enabled = picked.isNotEmpty()) { onGo(picked.toList()) }
            }
        }
    }
    // screenshot tests render the card without the dialog window
    if (inDialog) androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) { body() } else body()
}

@Composable
private fun VoiceRow(
    voice: VoiceProfile, active: Boolean, clips: Int, total: Int, busy: Boolean, canDelete: Boolean,
    onPick: () -> Unit, onTest: () -> Unit, onGenerate: () -> Unit, onDelete: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    val c = if (voice.cloned) SadoColors.Pink else SadoColors.Violet
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(if (active) SadoColors.Accent.copy(alpha = 0.10f) else SadoColors.Surface)
            .border(2.dp, if (active) SadoColors.Accent else SadoColors.Outline, RoundedCornerShape(20.dp))
            .pressable(onClick = onPick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c), contentAlignment = Alignment.Center) {
                Icon(SadoIcons.Mic, contentDescription = null, tint = SadoColors.OnAccent, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(voice.name, color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Text((if (voice.cloned) "Clone · " else "") + "$clips / $total clip", color = SadoColors.TextMuted, fontSize = 12.sp)
            }
            if (active) Tag("DIGUNA", filled = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Test", Modifier.weight(1f), onClick = onTest)
            SecondaryButton(if (clips >= total) "Lengkap" else "Generate", Modifier.weight(1.3f), color = SadoColors.Accent) { if (!busy) onGenerate() }
            if (canDelete) SecondaryButton(if (confirm) "Confirm?" else "Buang", Modifier.weight(1f), color = SadoColors.Danger) {
                if (confirm) onDelete() else confirm = true
            }
        }
    }
}

@Composable
private fun PickFromElevenLabs(hasKey: Boolean, saved: List<VoiceProfile>, onAdd: (VoiceClips.Remote) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<VoiceClips.Remote>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    Text("Pilih dari suara dalam akaun ElevenLabs ko (premade + yang ko dah add dari Voice Library). Preview tak guna credit.", color = SadoColors.TextMuted, fontSize = 13.sp)
    if (list == null) PrimaryButton(if (loading) "Loading…" else "Tunjuk suara ElevenLabs", enabled = hasKey && !loading) {
        val key = Secrets.load(context, "elevenlabs") ?: return@PrimaryButton
        loading = true
        scope.launch {
            val (l, e) = withContext(Dispatchers.IO) { VoiceClips.listVoices(key) }
            list = if (e == null) l else null; error = e; loading = false
        }
    }
    if (!hasKey) Text("Save API key dulu.", color = SadoColors.Streak, fontSize = 13.sp)
    error?.let { Text(it, color = SadoColors.Streak, fontSize = 13.sp) }
    list?.forEach { r ->
        val have = saved.any { it.voiceId == r.id }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(r.name, color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                if (r.about.isNotBlank()) Text(r.about, color = SadoColors.TextMuted, fontSize = 12.sp)
            }
            r.previewUrl?.let { url ->
                Box(Modifier.size(44.dp).clip(CircleShape).background(SadoColors.Surface2).pressable { VoiceClips.preview(url) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Preview ${r.name}", tint = SadoColors.Sky)
                }
            }
            Text(
                if (have) "Ada" else "+ Save",
                color = if (have) SadoColors.TextMuted else SadoColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = !have) { onAdd(r) }.padding(horizontal = 10.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun ManualVoice(saved: List<VoiceProfile>, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    Text("Jumpa suara best dalam Voice Library ElevenLabs? Copy ID dia, paste sini.", color = SadoColors.TextMuted, fontSize = 13.sp)
    OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nama suara (contoh: Abang gym)") }, colors = fieldColors())
    OutlinedTextField(id, { id = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Voice ID") }, colors = fieldColors())
    val dup = saved.any { it.voiceId == id }
    if (dup) Text("Voice ID ni dah ada dalam library.", color = SadoColors.Streak, fontSize = 13.sp)
    PrimaryButton("Save suara", enabled = name.isNotBlank() && id.length >= 10 && !dup) { onAdd(name.trim(), id); name = ""; id = "" }
}

// Five short lines to read, one recording each. Together they run about a minute, the length ElevenLabs asks for,
// and they cover the tones the coach actually uses: normal, hyped, pushing, calm and happy.
internal val cloneScript = listOf(
    "Nada biasa" to "Hai, ni suara aku. Aku tengah rakam untuk jadi coach dalam Sado. Aku cakap macam biasa je, tak laju, tak perlahan, macam borak dengan kawan.",
    "Bersemangat" to "Okay, jom mula! Set pertama, sepuluh rep push-up. Badan lurus, turun perlahan, tolak kuat. Satu, dua, tiga, empat, lima. Bagus, teruskan!",
    "Bagi semangat" to "Jangan berhenti lagi, tinggal dua rep je. Aku tahu penat, tapi ko lagi kuat dari semalam. Tarik nafas, fokus, habiskan!",
    "Tenang" to "Okay, rehat sembilan puluh saat. Minum air sikit, goyangkan tangan, relakskan bahu. Set seterusnya kita buat lebih kemas.",
    "Gembira" to "Siap! Workout hari ni dah habis. Streak ko jalan lagi satu hari, dan character ko makin sado. Esok kita sambung, okay?",
)

@Composable
internal fun CloneVoice(hasKey: Boolean, onCloned: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    val dir = remember { File(context.cacheDir, "clone").apply { mkdirs() } }
    fun lineFile(i: Int) = File(dir, "baris-${i + 1}.m4a")
    // recordings live on disk, so leaving the screen halfway keeps the lines already done
    val lines = remember { mutableStateListOf<File?>().apply { addAll(cloneScript.indices.map { i -> lineFile(i).takeIf { it.length() > 0 } }) } }
    val lengths = remember { mutableStateListOf<Int?>().apply { addAll(lines.map { f -> f?.let { secondsOf(it) } }) } }
    val files = remember { mutableStateListOf<File>() } // picked audio files, the other way in
    var recording by remember { mutableIntStateOf(-1) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    var pending by remember { mutableIntStateOf(-1) }
    var playing by remember { mutableIntStateOf(-1) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var consent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun stopPlay() { player?.release(); player = null; playing = -1 }
    fun play(i: Int) {
        stopPlay()
        val f = lines[i] ?: return
        player = runCatching {
            MediaPlayer().apply { setDataSource(f.path); setOnCompletionListener { stopPlay() }; prepare(); start() }
        }.getOrNull()
        if (player != null) playing = i
    }
    fun startRec(i: Int) {
        stopPlay()
        val f = File(dir, "baris-${i + 1}-baru.m4a")
        val r = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000); setAudioSamplingRate(44_100)
            setOutputFile(f.path)
        }
        runCatching { r.prepare(); r.start() }.onFailure { error = "Mic tak dapat start."; r.release(); return }
        error = null; seconds = 0; recorder = r; recording = i
    }
    fun stopRec() {
        val i = recording
        val r = recorder ?: return
        val f = File(dir, "baris-${i + 1}-baru.m4a")
        // a take only replaces the old one once it saved cleanly
        if (runCatching { r.stop() }.isSuccess && seconds >= 2) {
            f.renameTo(lineFile(i).apply { delete() }); lines[i] = lineFile(i); lengths[i] = seconds
        } else { f.delete(); error = "Rakaman baris ${i + 1} terlalu pendek. Cuba lagi." }
        r.release(); recorder = null; recording = -1
    }
    DisposableEffect(Unit) { onDispose { stopRec(); stopPlay() } }
    LaunchedEffect(recording) { while (recording >= 0) { delay(1000); seconds++; if (seconds >= 45) stopRec() } }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok && pending >= 0) startRec(pending) else if (!ok) error = "Perlukan permission mic untuk rakam." }
    fun record(i: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRec(i)
        else { pending = i; askMic.launch(Manifest.permission.RECORD_AUDIO) }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        uris.take(5).forEachIndexed { i, uri ->
            val type = context.contentResolver.getType(uri).orEmpty()
            val ext = when { "mpeg" in type || "mp3" in type -> "mp3"; "wav" in type -> "wav"; else -> "m4a" }
            val f = File(dir, "fail-${System.currentTimeMillis()}-$i.$ext")
            runCatching { context.contentResolver.openInputStream(uri)!!.use { inp -> f.outputStream().use { inp.copyTo(it) } }; files += f }
                .onFailure { error = "Tak dapat baca fail audio." }
        }
    }

    val done = lines.count { it != null }
    val next = lines.indexOfFirst { it == null }
    Text(
        "Baca 5 baris bawah ni, satu rakaman setiap baris. Ikut nada yang ditulis supaya coach AI boleh bersemangat dan boleh tenang. " +
            "Clone perlukan plan berbayar ElevenLabs (Starter ke atas).",
        color = SadoColors.TextMuted, fontSize = 13.sp,
    )
    OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nama suara (contoh: Suara aku)") }, colors = fieldColors())

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Dah rakam $done / ${cloneScript.size} baris", color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            lengths.filterNotNull().sum().takeIf { it > 0 }?.let { Text("${it}s", color = SadoColors.TextMuted, fontSize = 13.sp) }
        }
        Bar(done / cloneScript.size.toFloat(), height = 6)
        Text("Tips: tempat senyap, phone sejengkal dari mulut, baca dengan suara biasa ko.", color = SadoColors.TextMuted, fontSize = 12.sp)
    }

    cloneScript.forEachIndexed { i, (tone, line) ->
        ScriptLine(
            n = i + 1, tone = tone, line = line,
            recorded = lines[i] != null, length = lengths[i],
            recording = recording == i, seconds = seconds,
            current = i == next && recording < 0,
            locked = recording >= 0 && recording != i || busy,
            playing = playing == i,
            onRecord = { if (recording == i) stopRec() else record(i) },
            onPlay = { if (playing == i) stopPlay() else play(i) },
        )
    }

    // the other way in: audio already on the phone
    SecondaryButton("Atau pilih fail audio", Modifier.fillMaxWidth(), color = SadoColors.Sky) { pick.launch("audio/*") }
    files.toList().forEach { f ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("• ${f.name} · ${f.length() / 1024} KB", color = SadoColors.TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("Buang", color = SadoColors.Danger, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clickable { f.delete(); files.remove(f) }.padding(12.dp))
        }
    }

    Row(Modifier.fillMaxWidth().clickable { consent = !consent }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(consent, { consent = it }, colors = CheckboxDefaults.colors(checkedColor = SadoColors.Accent, checkmarkColor = SadoColors.OnAccent))
        Text("Ni suara aku sendiri, atau aku ada izin tuan punya suara.", color = SadoColors.Text, fontSize = 13.sp)
    }
    error?.let { Text(it, color = SadoColors.Streak, fontSize = 13.sp) }
    // three lines (about half a minute) is the least that still sounds like the person
    val enough = done >= 3 || files.isNotEmpty()
    PrimaryButton(if (busy) "Tengah clone…" else "Clone suara", enabled = hasKey && consent && name.isNotBlank() && enough && recording < 0 && !busy) {
        val key = Secrets.load(context, "elevenlabs") ?: return@PrimaryButton
        stopPlay(); busy = true; error = null
        scope.launch {
            val samples = lines.filterNotNull() + files
            val (id, e) = withContext(Dispatchers.IO) { VoiceClips.clone(key, name.trim(), samples) }
            busy = false
            if (id != null) {
                samples.forEach { it.delete() }
                for (i in lines.indices) { lines[i] = null; lengths[i] = null }
                files.clear()
                onCloned(name.trim(), id); name = ""; consent = false
            } else error = e
        }
    }
    when {
        !hasKey -> Text("Save API key dulu.", color = SadoColors.Streak, fontSize = 13.sp)
        !enough -> Text("Rakam sekurang-kurangnya 3 baris. Lagi banyak, lagi mirip.", color = SadoColors.TextMuted, fontSize = 13.sp)
        done in 3 until cloneScript.size -> Text("Boleh clone dah, tapi 5 baris bagi hasil paling mirip.", color = SadoColors.TextMuted, fontSize = 13.sp)
    }
}

private fun secondsOf(f: File): Int? = runCatching {
    android.media.MediaMetadataRetriever().run {
        try { setDataSource(f.path); extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt()?.div(1000) } finally { release() }
    }
}.getOrNull()

// one line of the script: what to read, in which tone, and its own record / listen / redo controls
@Composable
private fun ScriptLine(
    n: Int, tone: String, line: String, recorded: Boolean, length: Int?, recording: Boolean, seconds: Int,
    current: Boolean, locked: Boolean, playing: Boolean, onRecord: () -> Unit, onPlay: () -> Unit,
) {
    val edge = when { recording -> SadoColors.Pink; current -> SadoColors.Accent; else -> SadoColors.Outline }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface)
            .border(if (recording || current) 2.dp else 1.dp, edge, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(if (recorded) SadoColors.Accent else SadoColors.Surface2),
                contentAlignment = Alignment.Center,
            ) {
                if (recorded) Icon(Icons.Filled.Check, contentDescription = "Dah rakam", tint = SadoColors.OnAccent, modifier = Modifier.size(18.dp))
                else Text("$n", color = SadoColors.TextMuted, fontSize = 14.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            }
            Text("Baris $n", color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Tag(tone, if (recording) SadoColors.Pink else SadoColors.Sky)
        }
        Text("“$line”", color = if (recorded && !recording) SadoColors.TextMuted else SadoColors.Text, fontSize = 17.sp, lineHeight = 25.sp)
        when {
            recording -> Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(SadoColors.Pink)
                    .clickable(role = Role.Button, onClick = onRecord).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(SadoColors.OnAccent))
                Text("Stop", color = SadoColors.OnAccent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("0:%02d".format(seconds), color = SadoColors.OnAccent, fontSize = 15.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            }
            recorded -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(SadoColors.Accent.copy(alpha = 0.12f))
                        .clickable(enabled = !locked, role = Role.Button, onClick = onPlay).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (playing) Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(SadoColors.Accent))
                    else Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = SadoColors.Accent)
                    Text(if (playing) "Stop" else "Dengar" + (length?.let { " · ${it}s" } ?: ""), color = SadoColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
                Row(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface2)
                        .clickable(enabled = !locked, role = Role.Button, onClick = onRecord).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = SadoColors.TextMuted)
                    Text("Rakam semula", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            else -> Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (current) SadoColors.Accent else SadoColors.Accent.copy(alpha = 0.12f))
                    .clickable(enabled = !locked, role = Role.Button, onClick = onRecord).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(SadoIcons.Mic, contentDescription = null, tint = if (current) SadoColors.OnAccent else SadoColors.Accent, modifier = Modifier.size(22.dp))
                Text("Rakam baris ni", color = if (current) SadoColors.OnAccent else SadoColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
