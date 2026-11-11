package my.sado.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import my.sado.app.Coach
import my.sado.app.VoiceClips
import my.sado.app.data.Store
import my.sado.app.data.VoiceProfile
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Developer-only: every generated clip of every voice in one .zip, sorted by group with readable names,
 * plus a manifest.json that keeps the exact line each file says (the line is what the app looks clips up by).
 * Meant for building the default voice that ships in the APK; delete this file when that's done.
 */
object VoiceExport {
    private val tag = Regex("^\\[[^\\]]+]\\s*")

    fun slug(line: String): String = line.replace(tag, "").lowercase()
        .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifBlank { "klip" }

    private fun folder(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** Writes the zip; returns how many clips went in. */
    fun write(context: android.content.Context, out: OutputStream, voices: List<VoiceProfile>): Int {
        val groups = Coach.phraseGroups()
        var n = 0
        ZipOutputStream(out).use { zip ->
            zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION) // mp3 doesn't shrink further
            for (v in voices) {
                val lines = VoiceClips.lines(context, v.voiceId)
                if (lines.isEmpty()) continue
                val root = folder(v.name).ifBlank { v.voiceId }
                val rows = mutableListOf<JsonObject>()
                val byGroup = groups.map { g -> g.title to g.lines.filter { it in lines } } +
                    ("Lain-lain" to lines.filter { l -> groups.none { l in it.lines } })
                for ((title, items) in byGroup) {
                    items.forEachIndexed { i, line ->
                        val path = "$root/${folder(title)}/%03d-%s.mp3".format(i + 1, slug(line))
                        zip.putNextEntry(ZipEntry(path))
                        VoiceClips.file(context, v.voiceId, line).inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        rows += JsonObject(mapOf("file" to JsonPrimitive(path.substringAfter('/')), "group" to JsonPrimitive(title), "text" to JsonPrimitive(line)))
                        n++
                    }
                }
                val manifest = JsonObject(mapOf(
                    "voiceName" to JsonPrimitive(v.name), "voiceId" to JsonPrimitive(v.voiceId), "model" to JsonPrimitive(VoiceClips.MODEL),
                    "count" to JsonPrimitive(rows.size), "clips" to JsonArray(rows),
                ))
                zip.putNextEntry(ZipEntry("$root/manifest.json"))
                zip.write(manifest.toString().toByteArray())
                zip.closeEntry()
            }
        }
        return n
    }
}

/** Settings card shown once developer mode is on: export the clips, see what's still missing. */
@Composable
fun DevCard(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = Store.state.collectAsState().value.settings
    var note by remember { mutableStateOf<String?>(null) }
    val voice = st.voices.firstOrNull { it.voiceId == st.voiceId } ?: st.voices.first()
    val gaps = remember(note) {
        Coach.phraseGroups().map { g -> g.title to g.lines.count { !VoiceClips.has(context, voice.voiceId, it) } }.filter { it.second > 0 }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        note = "Tengah export…"
        scope.launch {
            note = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { VoiceExport.write(context, it, st.voices) } }
                    .fold({ "$it klip dah export." }, { "Export gagal: ${it.message}" })
            }
        }
    }
    Card {
        Text("Mod developer", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        Text("Export semua klip suara dalam satu .zip: folder ikut suara dan kumpulan, nama fail ikut ayat, dan manifest.json yang simpan ayat penuh.", color = SadoColors.TextMuted, fontSize = 13.sp)
        Text(
            if (gaps.isEmpty()) "${voice.name}: semua ayat dah ada klip." else "${voice.name} belum lengkap: " + gaps.joinToString(", ") { "${it.first} ${it.second}" },
            color = if (gaps.isEmpty()) SadoColors.Accent else SadoColors.Streak, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
        PrimaryButton("Export klip suara (.zip)") { save.launch("sado-voice-${java.time.LocalDate.now()}.zip") }
        note?.let { Text(it, color = SadoColors.Accent, fontSize = 13.sp) }
        CharTester()
        PetTester()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("Tutup mod developer", color = SadoColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.pressable(onClick = onClose))
        }
    }
}

/**
 * Dev mode "Uji watak": any level of either path, tired or not, shown on Home. Memory only, like PetDev.
 */
object CharDev {
    data class Override(val stage: my.sado.app.data.Stage, val tired: Boolean)
    var override by androidx.compose.runtime.mutableStateOf<Override?>(null)
}

@Composable
fun CharTester() {
    val o = CharDev.override
    val heavy = o?.stage?.heavy ?: false
    val index = o?.stage?.index ?: 0
    val tired = o?.tired ?: false
    fun set(h: Boolean = heavy, i: Int = index, t: Boolean = tired) {
        val list = if (h) my.sado.app.data.Progress.heavyStages else my.sado.app.data.Progress.stages
        CharDev.override = CharDev.Override(list[i], t)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(SadoColors.Outline))
    Text("Uji watak", color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    Text(
        if (o == null) "Pilih laluan dan level, lepas tu balik ke Home. Dalam memori je, progress sebenar tak berubah."
        else "Aktif: ${o.stage.name}" + (if (o.tired) ", letih" else "") + ". Nama level kat Home kekal yang sebenar.",
        color = if (o == null) SadoColors.TextMuted else SadoColors.Streak, fontSize = 13.sp,
    )
    Segmented(listOf("Kurus", "Gempal"), if (o != null) (if (heavy) 1 else 0) else -1) { set(h = it == 1) }
    (0 until 3).forEach { row ->
        val i = index - row * 5
        Segmented((1..5).map { (row * 5 + it).toString() }, if (o != null && i in 0..4) i else -1) { set(i = row * 5 + it) }
    }
    Segmented(listOf("Segar", "Letih"), if (o != null) (if (tired) 1 else 0) else -1) { set(t = it == 1) }
    SecondaryButton("Reset watak", Modifier.fillMaxWidth(), color = SadoColors.Streak) { CharDev.override = null }
}
