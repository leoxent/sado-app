package my.sado.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import my.sado.app.data.AppState
import my.sado.app.data.Nutrition
import my.sado.app.data.Profile
import my.sado.app.data.Store
import kotlin.math.roundToInt

private val activityNames = listOf("Banyak duduk", "Sederhana", "Aktif")

/** Top of the Badan tab: height, age, waist and activity at a glance, the targets they give, and an edit button. */
@Composable
fun BodyProfileCard(s: AppState, onFood: () -> Unit, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf(false) }
    if (editing) BodyProfileDialog(s.profile ?: Profile(), s) { editing = false }
    val p = s.profile
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Profil badan", color = SadoColors.Text, fontSize = 19.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Text("Asas kiraan kalori dan protein", color = SadoColors.TextMuted, fontSize = 12.sp)
            }
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(SadoColors.Accent.copy(alpha = 0.14f))
                    .border(1.dp, SadoColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { editing = true }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = SadoColors.Accent, modifier = Modifier.size(16.dp))
                Text(if (p == null) "Isi" else "Edit", color = SadoColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (p == null) {
            Text("Isi tinggi, berat dan umur supaya kalori dan protein dikira ikut badan kau.", color = SadoColors.TextMuted, fontSize = 13.sp)
            return@Card
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileStat("${p.heightCm.roundToInt()}", "cm tinggi", Modifier.weight(1f))
            ProfileStat("${p.age}", "tahun", Modifier.weight(1f))
            // the optional waist: an open slot that invites filling, not a dash
            if (p.waistCm != null) ProfileStat("${p.waistCm.roundToInt()}", "cm pinggang", Modifier.weight(1f))
            else ProfileStat("+", "pinggang", Modifier.weight(1f), muted = true) { editing = true }
        }
        Text(activityNames[p.activity.coerceIn(0, 2)] + " · " + if (p.sex == 0) "Lelaki" else "Perempuan", color = SadoColors.TextMuted, fontSize = 13.sp)
        val t = Nutrition.targets(s, p)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface2).clickable(onClick = onFood).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Tag(t.mode, SadoColors.Accent)
            Text("${t.kcal} kcal · protein ${t.protein}g", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SadoColors.TextMuted)
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier, muted: Boolean = false, onClick: (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(SadoColors.Surface2)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = if (muted) SadoColors.TextMuted else SadoColors.Text, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        Text(label, color = SadoColors.TextMuted, fontSize = 12.sp)
    }
}

/** Edit the body numbers in place; the targets underneath update as you type. A new weight is logged for today too. */
@Composable
fun BodyProfileDialog(saved: Profile, s: AppState, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BodyProfileEditor(saved, s, onClose)
    }
}

@Composable
fun BodyProfileEditor(saved: Profile, s: AppState, onClose: () -> Unit) {
    var sex by remember { mutableIntStateOf(saved.sex) }
    var age by remember { mutableStateOf(saved.age.toString()) }
    var height by remember { mutableStateOf(saved.heightCm.roundToInt().toString()) }
    var weight by remember { mutableStateOf(saved.weightKg.toString().removeSuffix(".0")) }
    var waist by remember { mutableStateOf(saved.waistCm?.roundToInt()?.toString() ?: "") }
    var act by remember { mutableIntStateOf(saved.activity) }
    val a = age.toIntOrNull() ?: 0
    val h = height.toFloatOrNull() ?: 0f
    val w = weight.toFloatOrNull() ?: 0f
    val wc = waist.toFloatOrNull()?.takeIf { it in 40f..200f }
    val valid = w in 25f..300f && h in 100f..250f && a in my.sado.app.data.Care.MIN_AGE..100 && (waist.isBlank() || wc != null)
    val next = saved.copy(sex = sex, age = a, heightCm = h, weightKg = w, activity = act, waistCm = wc)
    val t = if (valid) Nutrition.targets(s, next) else null

    Column(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(SadoColors.Surface)
            .border(1.dp, SadoColors.Outline, RoundedCornerShape(28.dp)).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Edit profil badan", color = SadoColors.Text, fontSize = 22.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        Segmented(listOf("Lelaki", "Perempuan"), sex) { sex = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Tinggi cm", height, Modifier.weight(1f)) { height = it }
            NumField("Berat kg", weight, Modifier.weight(1f)) { weight = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Umur", age, Modifier.weight(1f)) { age = it }
            NumField("Pinggang cm", waist, Modifier.weight(1f)) { waist = it }
        }
        Text("Pinggang tak wajib. Ukur tengah-tengah antara rusuk paling bawah dan bucu tulang pinggul (biasanya sikit atas pusat), pagi, lepas hembus nafas biasa.", color = SadoColors.TextMuted, fontSize = 12.sp)
        Label("Harian kau (selain workout)")
        Segmented(activityNames, act) { act = it }
        // what these numbers give, before saving
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SadoColors.Accent.copy(alpha = 0.10f)).padding(12.dp)) {
            Text(
                t?.let { "${it.mode} · ${it.kcal} kcal · protein ${it.protein}g" } ?: "Tinggi 100–250 cm, berat 25–300 kg, umur ${my.sado.app.data.Care.MIN_AGE}–100, pinggang 40–200 cm.",
                color = if (t != null) SadoColors.Accent else SadoColors.Streak, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            )
        }
        PrimaryButton("Simpan", enabled = valid) {
            Store.update { st -> st.copy(profile = (st.profile ?: next).copy(sex = sex, age = a, heightCm = h, weightKg = w, activity = act, waistCm = wc)) }
            if (w != saved.weightKg) logWeight(w)
            Feedback.success()
            onClose()
        }
        Text(
            "Batal", color = SadoColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClose).padding(12.dp),
        )
    }
}
