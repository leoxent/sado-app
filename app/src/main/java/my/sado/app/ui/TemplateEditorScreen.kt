package my.sado.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.sado.app.data.Catalog
import my.sado.app.data.MyMove
import my.sado.app.data.MyTemplate
import my.sado.app.data.Store

private val groupNames = listOf(
    "chest" to "Dada", "side_delts" to "Bahu sisi", "front_delts" to "Bahu depan", "rear_delts" to "Bahu belakang", "lats" to "Belakang",
    "traps" to "Trapezius", "lower_back" to "Pinggang", "arms" to "Lengan",

    "abs" to "Perut", "legs" to "Kaki", "glutes" to "Punggung", "cardio" to "Cardio",
)

/** Build or edit your own workout: pick moves from the library, set sets and reps for each. */
@Composable
fun TemplateEditorScreen(id: String?, onBack: () -> Unit) {
    val existing = remember(id) { Store.state.value.myTemplates.firstOrNull { it.id == id } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    val moves = remember { mutableStateListOf<MyMove>().apply { addAll(existing?.moves.orEmpty()) } }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Title(if (existing == null) "Template baru" else "Edit template", "Pilih move, set dan rep ikut suka ko", onBack = onBack)
        OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nama template (contoh: Push day aku)") }, colors = fieldColors())

        Label("Move · ${moves.size}")
        if (moves.isEmpty()) Text("Belum ada move. Tekan Tambah move untuk pilih dari library.", color = SadoColors.TextMuted, fontSize = 13.sp)
        moves.forEachIndexed { i, mv ->
            MoveDoseRow(
                move = mv,
                canUp = i > 0, canDown = i < moves.lastIndex,
                onChange = { moves[i] = it },
                onUp = { moves.add(i - 1, moves.removeAt(i)) },
                onDown = { moves.add(i + 1, moves.removeAt(i)) },
                onRemove = { moves.removeAt(i) },
            )
        }
        SecondaryButton("+ Tambah move", Modifier.fillMaxWidth(), color = SadoColors.Sky) { picking = true }

        val total = moves.sumOf { it.sets }
        if (moves.isNotEmpty()) Text("${moves.size} move · $total set · lebih kurang ${(total * 2.5).toInt()} minit", color = SadoColors.TextMuted, fontSize = 13.sp)
        PrimaryButton("Save template", enabled = name.isNotBlank() && moves.isNotEmpty()) {
            val t = MyTemplate(existing?.id ?: "t${System.currentTimeMillis()}", name.trim(), moves.toList())
            Store.update { s -> s.copy(myTemplates = if (existing == null) s.myTemplates + t else s.myTemplates.map { if (it.id == t.id) t else it }) }
            Feedback.success()
            onBack()
        }
        if (existing != null) SecondaryButton(if (confirmDelete) "Confirm padam?" else "Padam template", Modifier.fillMaxWidth(), color = SadoColors.Danger) {
            if (!confirmDelete) confirmDelete = true
            else { Store.update { s -> s.copy(myTemplates = s.myTemplates.filterNot { it.id == existing.id }) }; onBack() }
        }
    }

    if (picking) MovePicker(already = moves.map { it.id }.toSet(), onDismiss = { picking = false }) { ids ->
        picking = false
        ids.forEach { mid -> moves += MyMove(mid, sets = 3, reps = if (Catalog.get(mid).timed) 30 else 10) }
    }
}

@Composable
private fun MoveDoseRow(move: MyMove, canUp: Boolean, canDown: Boolean, onChange: (MyMove) -> Unit, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit) {
    val m = Catalog.get(move.id)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface).border(1.dp, SadoColors.Outline, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.name, color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            IconBtn(Icons.Filled.KeyboardArrowUp, "Naik", canUp, onUp)
            IconBtn(Icons.Filled.KeyboardArrowDown, "Turun", canDown, onDown)
            IconBtn(Icons.Filled.Close, "Buang", true, onRemove)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stepper("Set", "${move.sets}", Modifier.weight(1f), onMinus = { onChange(move.copy(sets = (move.sets - 1).coerceAtLeast(1))) }, onPlus = { onChange(move.copy(sets = (move.sets + 1).coerceAtMost(10))) })
            if (m.timed) Stepper("Saat", "${move.reps}s", Modifier.weight(1f), onMinus = { onChange(move.copy(reps = (move.reps - 5).coerceAtLeast(5))) }, onPlus = { onChange(move.copy(reps = (move.reps + 5).coerceAtMost(300))) })
            else Stepper("Rep", "${move.reps}", Modifier.weight(1f), onMinus = { onChange(move.copy(reps = (move.reps - 1).coerceAtLeast(1))) }, onPlus = { onChange(move.copy(reps = (move.reps + 1).coerceAtMost(100))) })
        }
    }
}

@Composable
private fun IconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = if (enabled) SadoColors.TextMuted else SadoColors.Track, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun Stepper(label: String, value: String, modifier: Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface2), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).pressable(onClick = onMinus), contentAlignment = Alignment.Center) { Text("−", color = SadoColors.Accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = SadoColors.Text, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text(label, color = SadoColors.TextMuted, fontSize = 11.sp)
        }
        Box(Modifier.size(44.dp).pressable(onClick = onPlus), contentAlignment = Alignment.Center) { Text("+", color = SadoColors.Accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

// library picker: tick any number of moves, grouped by body part
@Composable
internal fun MovePicker(already: Set<String>, onDismiss: () -> Unit, inDialog: Boolean = true, onAdd: (List<String>) -> Unit) {
    val picked = remember { mutableStateListOf<String>() }
    var group by remember { mutableStateOf<String?>(null) }
    val body: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(28.dp)).background(SadoColors.Surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Pilih move", color = SadoColors.Text, fontSize = 22.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf<Pair<String?, String>>(null to "Semua") + groupNames).forEach { (g, label) ->
                    val on = group == g
                    Text(
                        label,
                        color = if (on) SadoColors.OnAccent else SadoColors.Text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) SadoColors.Accent else SadoColors.Surface2)
                            .clickable { group = g }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            val list = Catalog.moves.filter { group == null || it.group == group }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(list, key = { it.id }) { m ->
                    val has = m.id in already
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = !has) { if (m.id in picked) picked.remove(m.id) else picked += m.id }.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(has || m.id in picked, null, enabled = !has, colors = CheckboxDefaults.colors(checkedColor = SadoColors.Accent, checkmarkColor = SadoColors.OnAccent))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, color = if (has) SadoColors.TextMuted else SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                m.muscles.take(2).joinToString(" · ") { Catalog.muscleNames[it] ?: it } + (m.need?.let { " · perlu " + Catalog.equipNames.getOrDefault(it, it) } ?: "") + (if (has) " · dah ada" else ""),
                                color = SadoColors.TextMuted, fontSize = 12.sp,
                            )
                        }
                        if (MoveImages.has(m.id)) Text("GAMBAR", color = SadoColors.Sky, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Batal", Modifier.weight(1f), onClick = onDismiss)
                PrimaryButton("Tambah ${picked.size}", Modifier.weight(1.4f), enabled = picked.isNotEmpty()) { onAdd(picked.toList()) }
            }
        }
    }
    if (inDialog) androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) { body() } else body()
}

