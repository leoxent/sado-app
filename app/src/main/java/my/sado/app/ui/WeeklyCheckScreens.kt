package my.sado.app.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import my.sado.app.data.CheckIn
import my.sado.app.data.WeeklyCheck
import kotlin.math.abs

private fun toneColor(t: WeeklyCheck.Tone): Color = when (t) {
    WeeklyCheck.Tone.Good -> SadoColors.Accent
    WeeklyCheck.Tone.Warn -> SadoColors.Streak
    WeeklyCheck.Tone.Info -> SadoColors.Sky
}

// the headline: a calorie change when there was one, else how the week went
private fun headline(c: CheckIn) = when {
    c.kcalChange > 0 -> "Kalori naik ${c.kcalChange} kcal"
    c.kcalChange < 0 -> "Kalori turun ${abs(c.kcalChange)} kcal"
    c.avgKg != null && c.prevAvgKg != null && c.offTrack == 0 -> "Minggu ni ikut landasan"
    else -> "Semakan minggu ni siap"
}

/** On Home until read: the week's check in one line, tap for the details. */
@Composable
fun WeeklyCheckCard(c: CheckIn, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val warn = c.kcalChange != 0
    val color = if (warn) SadoColors.Streak else SadoColors.Accent
    Row(
        modifier.fillMaxWidth().pressable(onClick = onOpen).clip(RoundedCornerShape(24.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(24.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(if (warn) Icons.Filled.Warning else Icons.Filled.Check, null, tint = color)
        }
        Column(Modifier.weight(1f)) {
            Text("SEMAKAN MINGGUAN", color = color, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text(headline(c), color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text("Berat, sesi dan protein 7 hari lepas", color = SadoColors.TextMuted, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SadoColors.TextMuted)
    }
}

/** The check in full: one line each for weight, training and protein, with the calorie change undoable. */
@Composable
fun WeeklyCheckDialog(c: CheckIn, onUndo: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) { WeeklyCheckBody(c, onUndo, onDismiss) }
}

@Composable
fun WeeklyCheckBody(c: CheckIn, onUndo: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(SadoColors.Surface)
            .border(1.dp, SadoColors.Outline, RoundedCornerShape(28.dp)).verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Semakan mingguan", color = SadoColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Text(headline(c), color = SadoColors.Text, fontSize = 24.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        WeeklyCheck.lines(c).forEach { (tone, text) ->
            val col = toneColor(tone)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(col.copy(alpha = 0.10f)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    when (tone) { WeeklyCheck.Tone.Good -> Icons.Filled.Check; WeeklyCheck.Tone.Warn -> Icons.Filled.Warning; else -> Icons.Filled.Info },
                    null, tint = col, modifier = Modifier.size(20.dp),
                )
                Text(text, color = SadoColors.Text, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        Text("Kalori cuma berubah lepas 2 minggu berturut di luar sasaran, ${WeeklyCheck.STEP} kcal setiap kali, maksimum ${WeeklyCheck.CAP} kcal.", color = SadoColors.TextMuted, fontSize = 12.sp)
        PrimaryButton("Faham", onClick = onDismiss)
        if (c.kcalChange != 0) Text(
            "Batalkan perubahan kalori", color = SadoColors.Sky, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onUndo).padding(12.dp),
        )
    }
}
