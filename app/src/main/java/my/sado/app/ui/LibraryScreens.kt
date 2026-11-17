package my.sado.app.ui

import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import my.sado.app.data.Catalog
import my.sado.app.data.PlanBuilder
import my.sado.app.data.Progress
import my.sado.app.data.Store
import my.sado.app.data.Templates
import my.sado.app.data.Wger

private fun open(context: android.content.Context, url: String) =
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

private fun youtube(name: String) = "https://www.youtube.com/results?search_query=" + Uri.encode("$name form tutorial")

@Composable
fun WorkoutListScreen(onStart: (String) -> Unit, onLibrary: () -> Unit, onEditTemplate: (String?) -> Unit = {}) {
    val s = Store.state.collectAsState().value
    val tag = Progress.todayTag(s)
    // each template gets its own block colour so the list scans like a game menu
    val palette = listOf(SadoColors.Violet, SadoColors.Sky, SadoColors.Streak, SadoColors.Pink, SadoColors.Accent)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Workout", "Plan ko, template aku, atau template siap")
        if (s.plan != null) {
            val planSession = my.sado.app.data.WeekShift.sessionOn(s) ?: s.plan.sessionOn()
            // the session's scene behind the card; light text on it, the lime card when there's no scene
            val art = CardArt.of(planSession.tag)
            val fg = if (art != null) CardArt.Text else SadoColors.OnAccent
            val top = Modifier.fillMaxWidth().appear(0).pressable { onStart("plan") }.clip(RoundedCornerShape(28.dp))
            val body: @Composable () -> Unit = { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SESI PLAN KAU", color = if (art != null) CardArt.Accent else fg.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                Text(planSession.label.uppercase(), color = fg, fontSize = 30.sp, lineHeight = 32.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${planSession.exercises.size} move · hari ni: " + (if (tag == "Rest") "rest day (boleh buat jugak)" else tag ?: "-"),
                        color = fg.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (art != null) CardArt.Accent else SadoColors.OnAccent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Start", tint = if (art != null) CardArt.OnAccent else SadoColors.Accent)
                    }
                }
            } }
            if (art != null) ArtCard(art, top.background(androidx.compose.ui.graphics.Color.Black).heightIn(min = 170.dp)) { Box(Modifier.align(Alignment.BottomStart)) { body() } }
            else Box(top.background(SadoColors.Accent)) { body() }
        }
        Box(Modifier.padding(top = 8.dp)) { Label("Template aku · ${s.myTemplates.size}") }
        s.myTemplates.forEach { t ->
            Row(
                Modifier.fillMaxWidth().pressable { onStart("my:${t.id}") }.clip(RoundedCornerShape(24.dp)).background(SadoColors.Surface)
                    .border(2.dp, SadoColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(24.dp)).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t.name, color = SadoColors.Text, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    Text("${t.moves.size} move · ${t.moves.sumOf { it.sets }} set", color = SadoColors.TextMuted, fontSize = 13.sp)
                    Text(t.moves.joinToString(" · ") { Catalog.get(it.id).name }, color = SadoColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text("Edit", color = SadoColors.Sky, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onEditTemplate(t.id) }.padding(horizontal = 12.dp, vertical = 14.dp))
            }
        }
        SecondaryButton("+ Buat template sendiri", Modifier.fillMaxWidth(), color = SadoColors.Accent) { onEditTemplate(null) }
        Box(Modifier.padding(top = 8.dp)) { Label("Template asas") }
        Templates.all.forEachIndexed { i, t ->
            val c = palette[i % palette.size]
            Row(
                Modifier.fillMaxWidth().appear(i + 1).pressable { onStart("tpl:${t.id}") }.clip(RoundedCornerShape(24.dp)).background(SadoColors.Surface)
                    .border(1.dp, SadoColors.Outline, RoundedCornerShape(24.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.clip(RoundedCornerShape(18.dp)).background(c.copy(alpha = 0.16f)).padding(6.dp)) {
                    // same session heat as the preview it opens: sets per muscle, helpers counting half
                    val heat = remember(t.id) { Heat.sets(t.moves.map { (id, dose) -> id to (dose.substringBefore('×').toIntOrNull() ?: 3) }) }
                    MuscleHeat(heat, week = false, back = false, modifier = Modifier.size(width = 30.dp, height = 64.dp))
                    MuscleHeat(heat, week = false, back = true, modifier = Modifier.size(width = 30.dp, height = 64.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t.name, color = SadoColors.Text, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    Text("${t.minutes} min · ${t.moves.size} move · ${t.level}", color = SadoColors.TextMuted, fontSize = 13.sp)
                    Text(t.primary.joinToString(" · ") { Catalog.muscleNames.getValue(it) }, color = c, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SadoColors.TextMuted)
            }
        }
        SecondaryButton("LIBRARY MOVE (WGER)", Modifier.fillMaxWidth(), onClick = onLibrary)
    }
}

@Composable
fun ExerciseDetailScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val m = Catalog.get(id)
    val s = Store.state.collectAsState().value
    val w = remember(id) { Wger.find(context, m.wger) }
    var raw by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title(m.name, if (m.need == null) "Tanpa alat" else "Perlu: " + Catalog.equipNames.getValue(m.need), onBack = onBack)
        MoveImage(id)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SadoColors.Surface).padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MuscleMap(m.muscles, m.secondary, back = false, modifier = Modifier.size(width = 96.dp, height = 205.dp))
                Text("Depan", color = SadoColors.TextMuted, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MuscleMap(m.muscles, m.secondary, back = true, modifier = Modifier.size(width = 96.dp, height = 205.dp))
                Text("Belakang", color = SadoColors.TextMuted, fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            // the dot matches the body map: red for the main movers, light orange for helpers
            Box(Modifier.size(10.dp).clip(CircleShape).background(Heat.heavy))
            Text("Utama", color = SadoColors.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
            m.muscles.forEach { Tag(Catalog.muscleNames.getValue(it), SadoColors.Text) }
        }
        if (m.secondary.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Heat.light))
            Text("Membantu", color = SadoColors.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
            m.secondary.forEach { Tag(Catalog.muscleNames.getValue(it), SadoColors.TextMuted) }
        }
        Catalog.ladderOf(id)?.let { key ->
            val steps = Catalog.ladders.getValue(key)
            val prog = s.ladders[key]
            val planMoves = s.plan?.allSessions.orEmpty().flatMap { it.exercises }
            Card {
                Text("Move ladder", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    steps.forEachIndexed { i, sid ->
                        val rung = prog?.rung ?: 0
                        val name = (if (i < rung) "✓ " else "") + Catalog.get(sid).name
                        if (i == rung) Tag(name, filled = true) else Tag(name, if (i < rung) SadoColors.Accent else SadoColors.TextMuted)
                    }
                }
                // the rung's move as today's plan has it, so the card shows its real target
                val rungId = steps.getOrNull(prog?.rung ?: 0)
                val planned = (s.answers?.let { PlanBuilder.refresh(planMoves, it, s.ladders) } ?: planMoves)
                    .firstOrNull { it.id == rungId }
                val d = planned?.let { Progress.dose(it.reps) }
                if (planned != null && d != null && rungId != null && Progress.climbs(rungId)) {
                    val now = Progress.boosted(planned, s.boosts, s.extraSets).target
                    val top = Progress.top(rungId, d)
                    Text("Target sekarang $now rep. Easy +1, Sangat easy +2, Susah −1. Capai $top rep dan jawab Easy, naik ke move seterusnya.", color = SadoColors.TextMuted, fontSize = 13.sp)
                    Bar((now - d.lo).toFloat() / (top - d.lo).coerceAtLeast(1), height = 6)
                } else {
                    Text("Naik ke move seterusnya bila habis semua set dan jawab Easy atau Sangat easy.", color = SadoColors.TextMuted, fontSize = 13.sp)
                }
            }
        }
        Card {
            Text("Tips form", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            m.tips.forEachIndexed { i, t -> Text("${i + 1}. $t", color = SadoColors.TextMuted, fontSize = 13.sp) }
        }
        Card {
            Text(if (w != null) "Penerangan wger #${w.id}" else "Belum dipadankan dengan wger", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            if (w != null && w.description.isNotBlank()) {
                Text(if (raw) "Sembunyi teks asal" else "Tunjuk teks asal (EN)", color = SadoColors.Accent, fontSize = 13.sp, modifier = Modifier.clickable { raw = !raw })
                if (raw) Text(w.description, color = SadoColors.TextMuted, fontSize = 13.sp)
            }
            Text(if (w != null) "wger.de · ${w.author.ifBlank { "penyumbang wger" }} · ${w.license}" else "Tips ditulis oleh Sado", color = SadoColors.TextMuted.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        if (w?.video != null) PrimaryButton("Tengok video wger") { open(context, w.video) }
        SecondaryButton("Cari tutorial di YouTube", Modifier.fillMaxWidth()) { open(context, youtube(m.name)) }
    }
}

@Composable
fun LibraryScreen(onOpen: (Int) -> Unit) {
    val context = LocalContext.current
    val all = remember { Wger.items(context) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    val filters = listOf("Semua" to null, "Tanpa alat" to "none", "Pull-up bar" to "bar", "Dumbbell" to "dumbbell", "Band" to "band", "Bench" to "bench")
    val shown = all.filter { e ->
        (query.isBlank() || e.name.contains(query, ignoreCase = true)) &&
            (filters[filter].second == null || filters[filter].second in e.equipment)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 24.dp)) { Title("Library", "${all.size} move dari wger.de · ${shown.size} dipapar") }
        OutlinedTextField(query, { query = it }, placeholder = { Text("Cari: push-up, squat, plank") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEachIndexed { i, (label, _) ->
                Text(
                    label,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (i == filter) SadoColors.Text else SadoColors.Surface)
                        .clickable { filter = i }.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = if (i == filter) SadoColors.Bg else SadoColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface).clickable { onOpen(e.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name, color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            e.muscles.joinToString(", ") { Catalog.muscleNames[it] ?: it }.ifBlank { e.category },
                            color = SadoColors.TextMuted, fontSize = 13.sp,
                        )
                    }
                    if (e.video != null) Tag("video")
                }
            }
            item { Box(Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
fun WgerDetailScreen(id: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val e = remember(id) { Wger.items(context).first { it.id == id } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title(e.name, listOf(e.category, e.equipment.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · "), onBack = onBack)
        if (e.muscles.isNotEmpty() || e.secondary.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SadoColors.Surface).padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MuscleMap(e.muscles, e.secondary, back = false, modifier = Modifier.size(width = 96.dp, height = 205.dp))
                MuscleMap(e.muscles, e.secondary, back = true, modifier = Modifier.size(width = 96.dp, height = 205.dp))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { RoleLegend() }
        }
        if (e.description.isNotBlank()) Card {
            Text("Penerangan (EN)", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text(e.description, color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        Text("wger.de #${e.id} · ${e.author.ifBlank { "penyumbang wger" }} · ${e.license}", color = SadoColors.TextMuted.copy(alpha = 0.6f), fontSize = 12.sp)
        if (e.video != null) PrimaryButton("Tengok video wger") { open(context, e.video) }
        SecondaryButton("Cari tutorial di YouTube", Modifier.fillMaxWidth()) { open(context, youtube(e.name)) }
    }
}
