package my.sado.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import my.sado.app.data.FoodIcons
import my.sado.app.data.FoodItem
import my.sado.app.data.Measures
import my.sado.app.data.Portion
import kotlin.math.roundToInt

@Composable
fun FoodEditDialog(item: FoodItem, onSave: (FoodItem) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FoodEditBody(item, onSave, onDelete, onDismiss)
    }
}

private fun dec(s: String) = s.replace(',', '.').toFloatOrNull()
private fun numText(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(java.util.Locale.US, v)

/**
 * Every part of one food line: picture, name, how many, calories and macros, or take it out.
 * Changing the quantity scales the numbers with it, so "1 → 2 keping" doubles the calories;
 * numbers typed by hand stay as typed until the quantity moves again.
 */
@Composable
fun FoodEditBody(item: FoodItem, onSave: (FoodItem) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val start = remember { Portion.parse(item.portion) }
    var picked by remember { mutableStateOf(item.icon) }
    var picking by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(item.name) }
    // an icon the user chose stays; otherwise it follows the name as they retype it
    var chose by remember { mutableStateOf(false) }
    val renamed = name.trim() != item.name
    val icon = if (chose || !renamed) picked ?: FoodIcons.match(name.trim()) else FoodIcons.match(name.trim())
    var qty by remember { mutableStateOf(start.qty) }
    var unit by remember { mutableStateOf(start.unit) }
    var lo by remember { mutableStateOf(item.kcalLow.toString()) }
    var hi by remember { mutableStateOf(item.kcalHigh.toString()) }
    var p by remember { mutableStateOf(numText(item.protein)) }
    var c by remember { mutableStateOf(numText(item.carbs)) }
    var f by remember { mutableStateOf(numText(item.fat)) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun setQty(q: Float) {
        val next = q.coerceIn(0.5f, 9999f)
        val k = next / qty
        if (k == 1f) return
        lo = dec(lo)?.let { (it * k).roundToInt().toString() } ?: lo
        hi = dec(hi)?.let { (it * k).roundToInt().toString() } ?: hi
        p = dec(p)?.let { numText(((it * k) * 10).roundToInt() / 10f) } ?: p
        c = dec(c)?.let { numText(((it * k) * 10).roundToInt() / 10f) } ?: c
        f = dec(f)?.let { numText(((it * k) * 10).roundToInt() / 10f) } ?: f
        unit = Portion.scaleWeight(unit, k)
        qty = next
    }

    if (picking) FoodIconPicker(icon, onPick = { picked = it; chose = true; picking = false }) { picking = false }
    val lv = dec(lo); val hv = dec(hi)
    val ok = name.isNotBlank() && lv != null && hv != null && dec(p) != null && dec(c) != null && dec(f) != null

    Column(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(SadoColors.Surface)
            .border(1.dp, SadoColors.Outline, RoundedCornerShape(28.dp)).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Edit makanan", color = SadoColors.Text, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FoodIconButton(item.copy(icon = icon), 56.dp) { picking = true }
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors())
        }

        Label("Kuantiti")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepButton("−", "Kurang") { setQty(qty - Portion.step(qty - 0.01f)) }
            Text(Portion.num(qty), color = SadoColors.Text, fontSize = 24.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            StepButton("+", "Tambah") { setQty(qty + Portion.step(qty)) }
            OutlinedTextField(unit, { unit = it.take(40) }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors())
        }
        Text("Tukar kuantiti, kalori dan makro ikut sekali.", color = SadoColors.TextMuted, fontSize = 12.sp)
        MeasureGuide()

        Label("Kalori (kcal)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(lo, { lo = it }, "Dari", Modifier.weight(1f))
            NumField(hi, { hi = it }, "Hingga", Modifier.weight(1f))
        }
        Label("Makro (g)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(p, { p = it }, "Protein", Modifier.weight(1f))
            NumField(c, { c = it }, "Karbo", Modifier.weight(1f))
            NumField(f, { f = it }, "Lemak", Modifier.weight(1f))
        }

        PrimaryButton("Simpan", enabled = ok) {
            val a = lv!!.roundToInt(); val b = hv!!.roundToInt()
            onSave(item.copy(
                name = name.trim(), portion = Portion.format(start.copy(qty = qty, unit = unit.trim())),
                kcalLow = minOf(a, b), kcalHigh = maxOf(a, b), protein = dec(p)!!, carbs = dec(c)!!, fat = dec(f)!!,
                icon = if (chose || !renamed) picked else null,
            ))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(if (confirmDelete) "Tekan lagi untuk buang" else "Buang item", Modifier.weight(1f), color = SadoColors.Danger) {
                if (confirmDelete) onDelete() else confirmDelete = true
            }
            SecondaryButton("Batal", Modifier.weight(1f)) { onDismiss() }
        }
    }
}

@Composable
private fun StepButton(symbol: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(SadoColors.Surface2).clickable(role = Role.Button, onClickLabel = label) { Feedback.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, color = SadoColors.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { contentDescription = label }) }
}

@Composable
private fun NumField(v: String, set: (String) -> Unit, label: String, modifier: Modifier) =
    OutlinedTextField(
        v, { s -> set(s.filter { it.isDigit() || it == '.' || it == ',' }.take(6)) }, label = { Text(label, maxLines = 1) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier, colors = fieldColors(),
    )

/** "Sudu, senduk, genggam..." in one tap, so people write the unit the AI reads the same way. */
@Composable
fun MeasureGuide(modifier: Modifier = Modifier, startOpen: Boolean = false) {
    var open by remember { mutableStateOf(startOpen) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SadoColors.Sky.copy(alpha = 0.10f)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Info, null, tint = SadoColors.Sky, modifier = Modifier.size(20.dp))
            Text("Beza sudu, senduk, genggam, biji...", color = SadoColors.Text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, if (open) "Tutup" else "Buka", tint = SadoColors.TextMuted)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Measures.guide.forEach { (w, d) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(w, color = SadoColors.Sky, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(92.dp))
                        Text(d, color = SadoColors.Text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                    }
                }
                Text("Tak pasti? Tulis je berat kalau tahu, contoh \"ayam 150 g\".", color = SadoColors.TextMuted, fontSize = 12.sp)
            }
        }
    }
}
