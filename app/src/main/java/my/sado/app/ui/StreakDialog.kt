package my.sado.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import my.sado.app.data.AppState
import my.sado.app.data.Progress
import java.time.LocalDate

/** Tapping the flame: what the number means, the rule that keeps it alive, and where this week stands. */
@Composable
fun StreakDialog(s: AppState, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) { StreakExplainer(s, onDismiss) }
}

@Composable
fun StreakExplainer(s: AppState, onDismiss: () -> Unit, today: LocalDate = LocalDate.now()) {
    val info = Progress.streak(s, today)
    val need = Progress.MIN_PER_WEEK
    val monday = today.minusDays(today.dayOfWeek.value - 1L)
    val active = Progress.activeDays(s)
    val left = (need - info.thisWeek).coerceAtLeast(0)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(SadoColors.Surface)
            .border(1.dp, SadoColors.Outline, RoundedCornerShape(28.dp)).verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(SadoColors.Streak.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(SadoIcons.Flame, null, tint = SadoColors.Streak, modifier = Modifier.size(30.dp))
            }
            Column {
                Text("${info.days} hari", color = SadoColors.Streak, fontSize = 30.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Text(if (info.days == 0) "Streak belum mula" else "Streak ko sekarang", color = SadoColors.TextMuted, fontSize = 13.sp)
            }
        }

        // this week at a glance: lit days count, and whether the week is already safe
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface2).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("MINGGU NI", color = SadoColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("I", "S", "R", "K", "J", "S", "A").forEachIndexed { i, d ->
                    val day = monday.plusDays(i.toLong())
                    val on = day.toEpochDay() in active
                    val isToday = day == today
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape)
                                .background(if (on) SadoColors.Streak else SadoColors.Track)
                                .then(if (isToday) Modifier.border(2.dp, SadoColors.Text, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Icons.Filled.Check, null, tint = SadoColors.OnAccent, modifier = Modifier.size(16.dp)) }
                        Text(d, color = if (isToday) SadoColors.Text else SadoColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            Bar((info.thisWeek / need.toFloat()).coerceAtMost(1f), SadoColors.Streak, height = 8)
            Text(
                if (left == 0) "${info.thisWeek} hari aktif. Minggu ni dah selamat, streak tak putus."
                else "${info.thisWeek} / $need hari. Perlu $left hari lagi sebelum Ahad.",
                color = if (left == 0) SadoColors.Accent else SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            )
        }

        Text("Macam mana streak dikira", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        listOf(
            "Streak kira hari sejak ko mula konsisten. Tak payah workout setiap hari.",
            "Setiap minggu (Isnin hingga Ahad) kena aktif sekurang-kurangnya $need hari. Workout atau log sukan, dua-dua kira.",
            "Minggu yang tengah jalan tak boleh putuskan streak. Dia cuma dinilai bila minggu tu habis.",
            "Kalau satu minggu kurang dari $need hari, streak reset. Dia mula balik pada hari aktif seterusnya.",
            "Rest day pun masuk kiraan, sebab rehat sebahagian dari plan.",
        ).forEachIndexed { i, line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(SadoColors.Streak.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = SadoColors.Streak, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(line, color = SadoColors.TextMuted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
            }
        }
        PrimaryButton("Faham", onClick = onDismiss)
        Text("Tip: 2 hari seminggu dah cukup untuk jaga streak.", color = SadoColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
