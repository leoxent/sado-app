package my.sado.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import my.sado.app.data.SessionSummary
import my.sado.app.data.Store
import my.sado.app.data.WorkoutLog
import java.time.LocalDate

private val dayShort = listOf("Isn", "Sel", "Rab", "Kha", "Jum", "Sab", "Ahd")
private val monthShort = listOf("Jan", "Feb", "Mac", "Apr", "Mei", "Jun", "Jul", "Ogo", "Sep", "Okt", "Nov", "Dis")

/** "Sel, 11 Nov", with the year only when it isn't this one. */
internal fun dateLabel(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    val year = if (d.year != LocalDate.now().year) " ${d.year}" else ""
    return "${dayShort[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${monthShort[d.monthValue - 1]}$year"
}

private fun timeLabel(log: WorkoutLog) = log.seconds?.let { "%d:%02d".format(it / 60, it % 60) } ?: "${log.minutes} min"

/** One row for a past session; [records] is how many moves set a new best in it. */
@Composable
internal fun LogRow(log: WorkoutLog, records: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface).border(1.dp, SadoColors.Outline, RoundedCornerShape(20.dp))
            .pressable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(SadoColors.Accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(SadoIcons.Dumbbell, null, tint = SadoColors.Accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(log.title, color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                listOfNotNull(
                    dateLabel(log.epochDay), timeLabel(log), log.kcal?.let { "$it kcal" },
                    "stop awal".takeIf { log.quit }, "mini".takeIf { log.mini },
                ).joinToString(" · "),
                color = SadoColors.TextMuted, fontSize = 13.sp,
            )
            if (records > 0) Text("🏆 $records rekod baru", color = SadoColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text("+${log.xp}", color = SadoColors.Accent, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
    }
}

/** Every session, newest first; a tap opens its summary. */
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (Int) -> Unit) {
    val s = Store.state.collectAsState().value
    val records = remember(s.logs) { SessionSummary.recordCounts(s.logs) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Sejarah sesi", "${s.logs.size} sesi setakat ni", onBack = onBack)
        if (s.logs.isEmpty()) Text("Belum ada sesi. Lepas workout pertama, semuanya tersimpan kat sini.", color = SadoColors.TextMuted, fontSize = 14.sp)
        var lastMonth: Pair<Int, Int>? = null
        // newest first by date, whatever order the logs were saved in (a restored backup, a dev simulation)
        val order = remember(s.logs) { s.logs.indices.sortedWith(compareByDescending<Int> { s.logs[it].epochDay }.thenByDescending { s.logs[it].startMs ?: 0L }.thenByDescending { it }) }
        order.forEach { i ->
            val log = s.logs[i]
            val d = LocalDate.ofEpochDay(log.epochDay)
            val month = d.year to d.monthValue
            if (month != lastMonth) {
                lastMonth = month
                Label(monthShort[d.monthValue - 1] + " " + d.year)
            }
            LogRow(log, records.getOrElse(i) { 0 }) { onOpen(i) }
        }
    }
}

/** One past session in full: the numbers, the per-move summary and its heart rate. */
@Composable
fun SessionLogScreen(index: Int, onBack: () -> Unit) {
    val s = Store.state.collectAsState().value
    val log = s.logs.getOrNull(index)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (log == null) {
            Title("Sesi tak jumpa", onBack = onBack)
            return@Column
        }
        Title(log.title, dateLabel(log.epochDay), onBack = onBack)
        Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Stat(timeLabel(log), "masa")
                Stat("${log.exercises.sumOf { e -> e.sets.sumOf { it.reps } }}", "rep")
                Stat("+${log.xp}", "XP", SadoColors.Accent)
                log.kcal?.let { Stat("$it", "kcal", SadoColors.Streak) }
            }
            if (log.quit || log.doubtful || log.mini) Text(
                listOfNotNull("Stop awal".takeIf { log.quit }, "Sesi mini".takeIf { log.mini }, "Ditanda meragukan".takeIf { log.doubtful }).joinToString(" · "),
                color = SadoColors.Streak, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
        }
        ReviewCard(log, s.logs.take(index), s.plan)
        SummaryCard(log, s.logs.take(index))
        // a session from the last few days may still be waiting for the watch to sync
        val recent = log.endMs != null && System.currentTimeMillis() - log.endMs < 3 * 86_400_000L
        HrReportCard(log, waiting = s.settings.healthConnect && recent)
    }
}
