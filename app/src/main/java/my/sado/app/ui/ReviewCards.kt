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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import my.sado.app.data.AppState
import my.sado.app.data.ExtraSession
import my.sado.app.data.HrAnalysis
import my.sado.app.data.HrStore
import my.sado.app.data.Plan
import my.sado.app.data.SessionReview
import my.sado.app.data.Store
import my.sado.app.data.WorkoutLog
import java.time.LocalDate

/** "Ulasan sesi": the session told in a few sentences, above the per-move numbers. */
@Composable
fun ReviewCard(log: WorkoutLog, earlier: List<WorkoutLog>, plan: Plan?) {
    val age = Store.state.collectAsState().value.profile?.age ?: 25
    val lines = remember(log, earlier.size, plan) {
        val hr = runCatching { HrAnalysis.analyze(HrStore.points(log), log.exercises, log.restingHr, age) }.getOrNull()
        SessionReview.sentences(log, earlier, plan, hr)
    }
    ReviewLines(lines)
}

@Composable
internal fun ReviewLines(lines: List<String>) {
    Card {
        Text("Ulasan sesi", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(SadoColors.Accent))
                Text(line, color = SadoColors.Text, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

/** Rest day: pick which plan session to do as a Sesi tambahan. */
@Composable
fun ExtraSessionDialog(s: AppState, onPick: (String) -> Unit, onDismiss: () -> Unit, today: LocalDate = LocalDate.now()) {
    Dialog(onDismissRequest = onDismiss) { ExtraSessionPicker(s, onPick, today) }
}

@Composable
internal fun ExtraSessionPicker(s: AppState, onPick: (String) -> Unit, today: LocalDate = LocalDate.now()) {
    val plan = s.plan ?: return
    val options = remember(plan, s.logs.size, today) { ExtraSession.options(plan, s.logs, today) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(SadoColors.Surface)
            .border(1.dp, SadoColors.Outline, RoundedCornerShape(28.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sesi tambahan", color = SadoColors.Text, fontSize = 24.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        Text("Hari ni rest day dalam plan. Pilih sesi mana nak buat. Semua tetap dikira untuk streak dan progress.", color = SadoColors.TextMuted, fontSize = 13.sp)
        options.forEach { o ->
            val color = if (o.best) SadoColors.Accent else SadoColors.Outline
            Column(
                Modifier.fillMaxWidth().pressable { onPick(o.tag) }.clip(RoundedCornerShape(18.dp)).background(SadoColors.Surface2)
                    .border(if (o.best) 2.dp else 1.dp, color, RoundedCornerShape(18.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(o.label, color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    if (o.best) Text("PALING SESUAI", color = SadoColors.Accent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("${o.moves} move", color = SadoColors.TextMuted, fontSize = 12.sp)
                o.notes.forEach { Text(it, color = SadoColors.Streak, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
