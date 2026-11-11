package my.sado.app.ui

import my.sado.app.data.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.sado.app.Coach
import my.sado.app.VoiceClips
import my.sado.app.data.Store

private val tagRegex = Regex("^\\[([^\\]]+)]\\s*")

/** Every clip already generated, grouped by voice and by kind of line, each one playable. */
@Composable
fun VoiceLibraryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val st = Store.state.collectAsState().value.settings
    val groups = remember { Coach.phraseGroups() }
    var refresh by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<String?>(st.voiceId) }
    var playing by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val parts = remember { Coach.partLines() }
    DisposableEffect(Unit) { onDispose { VoiceClips.stop() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Library suara", "Clip yang dah generate, ikut suara", onBack = onBack)
        st.voices.forEach { v ->
            val lines = remember(refresh, v.voiceId) { VoiceClips.lines(context, v.voiceId) }
            val active = v.voiceId == st.voiceId
            val c = if (v.cloned) SadoColors.Pink else SadoColors.Violet
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(if (active) SadoColors.Accent.copy(alpha = 0.10f) else SadoColors.Surface)
                    .border(2.dp, if (active) SadoColors.Accent else SadoColors.Outline, RoundedCornerShape(20.dp))
                    .pressable { open = if (open == v.voiceId) null else v.voiceId }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c), contentAlignment = Alignment.Center) {
                    Icon(SadoIcons.Mic, contentDescription = null, tint = SadoColors.OnAccent, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(v.name, color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    Text((if (v.cloned) "Clone · " else "") + "${lines.size} clip", color = SadoColors.TextMuted, fontSize = 12.sp)
                }
                if (active) Tag("DIGUNA", filled = true)
                Text(if (open == v.voiceId) "Tutup" else "Buka", color = SadoColors.Sky, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (open == v.voiceId) {
                val old = lines.filter { my.sado.app.Lines.isOldIntro(it) }
                if (old.isNotEmpty()) {
                    Text("${old.size} intro lama (yang ada nombor set dan rep) dah tak dipakai lagi.", color = SadoColors.TextMuted, fontSize = 13.sp)
                    SecondaryButton("Buang ${old.size} intro lama", Modifier.fillMaxWidth(), color = SadoColors.Danger) {
                        old.forEach { VoiceClips.delete(context, v.voiceId, it) }; refresh++
                    }
                }
                // clips cut from a take before 0.27 can start or end in the wrong place; this clears them for a fresh generate
                val made = lines.filter { it in parts }
                if (made.isNotEmpty()) {
                    var sure by remember(v.voiceId) { mutableStateOf(false) }
                    SecondaryButton(if (sure) "Tekan lagi: buang ${made.size} klip" else "Buang semua klip intro & nombor (${made.size})", Modifier.fillMaxWidth(), color = SadoColors.Danger) {
                        if (sure) { made.forEach { VoiceClips.delete(context, v.voiceId, it) }; refresh++; sure = false } else sure = true
                    }
                    Text("Guna ni kalau potongan klip intro tak cantik, lepas tu Generate semula.", color = SadoColors.TextMuted, fontSize = 12.sp)
                }
                busy?.let { Text(it, color = SadoColors.Streak, fontSize = 13.sp) }
                if (lines.isEmpty()) Text("Belum ada clip. Tekan Generate kat skrin Suara dulu.", color = SadoColors.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                // known lines keep their group; anything said ad hoc lands in "Lain-lain"
                val known = groups.flatMap { it.lines }.toSet()
                (groups.map { g -> g.title to g.lines.filter { it in lines } } + ("Lain-lain" to lines.filter { it !in known }))
                    .filter { it.second.isNotEmpty() }
                    .forEach { (title, items) ->
                        Label("$title · ${items.size}")
                        items.forEach { line ->
                            ClipRow(
                                line = line,
                                playing = playing == v.voiceId + line,
                                onPlay = { if (VoiceClips.play(context, v.voiceId, line)) playing = v.voiceId + line },
                                onDelete = { VoiceClips.delete(context, v.voiceId, line); refresh++ },
                                onRedo = {
                                    val key = Secrets.load(context, "elevenlabs")
                                    if (key == null) busy = "Save API key ElevenLabs dulu." else {
                                        busy = "Generate semula…"
                                        scope.launch {
                                            VoiceClips.delete(context, v.voiceId, line)
                                            val e = withContext(Dispatchers.IO) { VoiceClips.generate(context, key, v.voiceId, line, steady = line in parts) }
                                            busy = e; refresh++
                                            if (e == null && VoiceClips.play(context, v.voiceId, line)) playing = v.voiceId + line
                                        }
                                    }
                                },
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun ClipRow(line: String, playing: Boolean, onPlay: () -> Unit, onDelete: () -> Unit, onRedo: () -> Unit) {
    val tag = tagRegex.find(line)?.groupValues?.get(1)
    val text = line.replace(tagRegex, "")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (playing) SadoColors.Accent else SadoColors.Surface2).pressable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = if (playing) SadoColors.OnAccent else SadoColors.Accent)
        }
        Column(Modifier.weight(1f)) {
            if (tag != null) Text(tag.uppercase(), color = SadoColors.Violet, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text(text, color = SadoColors.Text, fontSize = 14.sp)
        }
        Box(Modifier.size(44.dp).clip(CircleShape).pressable(onClick = onRedo), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Refresh, contentDescription = "Generate semula", tint = SadoColors.Sky, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.size(44.dp).clip(CircleShape).pressable(onClick = onDelete), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, contentDescription = "Buang clip", tint = SadoColors.TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
