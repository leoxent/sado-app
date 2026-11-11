package my.sado.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.sado.app.data.AppState
import my.sado.app.data.Store
import my.sado.app.data.WeightLog
import java.time.LocalDate
import kotlin.math.roundToInt

// Asian cut-offs used by Malaysia's Ministry of Health
private val bmiZones = listOf(
    Triple(0f, 18.5f, "Kurang berat"), Triple(18.5f, 23f, "Normal"), Triple(23f, 27.5f, "Lebih berat"), Triple(27.5f, 60f, "Obes"),
)
private fun zoneColor(i: Int) = listOf(SadoColors.Sky, SadoColors.Accent, SadoColors.Streak, SadoColors.Danger)[i]
private fun zoneOf(bmi: Float) = bmiZones.indexOfFirst { bmi < it.second }.coerceAtLeast(0)

/** Weight log: today's BMI, a weight or BMI line over time, and a quick way to log today's weight. */
@Composable
fun WeightCard(s: AppState) {
    val p = s.profile ?: return
    val m = p.heightCm / 100
    if (m <= 0f) return
    // the profile weight stands in as the first point until the user logs one
    val logs = s.weights.ifEmpty { listOf(WeightLog(s.planStart ?: LocalDate.now().toEpochDay(), p.weightKg)) }
    val now = logs.last().kg
    val bmi = now / (m * m)
    val zone = zoneOf(bmi)
    var mode by remember { mutableIntStateOf(0) }
    var draft by remember(now) { mutableFloatStateOf(now) }

    Card {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("BERAT", color = SadoColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text("%.1f kg".format(now), color = SadoColors.Text, fontSize = 34.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("BMI %.1f".format(bmi), color = SadoColors.Text, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Tag(bmiZones[zone].third, zoneColor(zone))
            }
        }
        Segmented(listOf("Berat", "BMI"), mode) { mode = it }
        val values = logs.takeLast(30).map { if (mode == 0) it.kg else it.kg / (m * m) }
        LineChart(values, if (mode == 1) bmiZones.map { it.first to it.second }.zip(bmiZones.indices.map { zoneColor(it) }) else emptyList())
        if (logs.size >= 2) {
            val diff = logs.last().kg - logs.first().kg
            val since = LocalDate.ofEpochDay(logs.first().epochDay)
            Text(
                (if (diff > 0) "+" else if (diff < 0) "−" else "±") + "%.1f kg sejak %d %s".format(kotlin.math.abs(diff), since.dayOfMonth, monthNames[since.monthValue - 1].take(3)),
                color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
            )
        } else Text("Log berat seminggu sekali (pagi, lepas tandas) untuk nampak trend.", color = SadoColors.TextMuted, fontSize = 13.sp)
        Text(
            "Julat sihat (Asia) untuk tinggi ko: %.1f–%.1f kg. BMI tak beza otot dengan lemak, jadi tengok sekali gambar.".format(18.5f * m * m, 22.9f * m * m),
            color = SadoColors.TextMuted, fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Stepper("kg hari ni", "%.1f".format(draft), Modifier.weight(1f),
                onMinus = { draft = ((draft - 0.1f) * 10).roundToInt() / 10f }, onPlus = { draft = ((draft + 0.1f) * 10).roundToInt() / 10f })
            SecondaryButton("Log", Modifier.weight(0.6f), color = SadoColors.Accent) { logWeight(draft) }
        }
    }
}

// one entry a day (a second log the same day replaces it); the profile follows, so protein and calorie targets update
fun logWeight(kg: Float) = Store.update { s ->
    val today = LocalDate.now().toEpochDay()
    s.copy(
        weights = (s.weights.filter { it.epochDay != today } + WeightLog(today, kg)).sortedBy { it.epochDay },
        profile = s.profile?.copy(weightKg = kg),
    )
}

// points joined by a line, min and max labelled; bands paint background zones (BMI) between two values
@Composable
private fun LineChart(values: List<Float>, bands: List<Pair<Pair<Float, Float>, Color>>) {
    val lo0 = values.min(); val hi0 = values.max()
    val pad = maxOf((hi0 - lo0) * 0.25f, 0.5f)
    val lo = lo0 - pad; val hi = hi0 + pad
    val line = SadoColors.Accent
    val grid = SadoColors.Track
    val bg = SadoColors.Surface
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "Graf: " + values.joinToString { "%.1f".format(it) } }) {
            bands.forEach { (r, c) ->
                val top = size.height * (1 - ((r.second.coerceAtMost(hi) - lo) / (hi - lo))).coerceIn(0f, 1f)
                val bottom = size.height * (1 - ((r.first.coerceAtLeast(lo) - lo) / (hi - lo))).coerceIn(0f, 1f)
                if (bottom > top) drawRect(c.copy(alpha = 0.1f), Offset(0f, top), Size(size.width, bottom - top))
            }
            for (k in 0..2) drawLine(grid, Offset(0f, size.height * k / 2), Offset(size.width, size.height * k / 2), 1.dp.toPx())
            // inset so the end dots are not cut by the card edge
            val inset = 8.dp.toPx(); val w = size.width - 2 * inset
            val pts = values.mapIndexed { i, v ->
                Offset(inset + if (values.size == 1) w / 2 else w * i / (values.size - 1), size.height * (1 - (v - lo) / (hi - lo)))
            }
            if (pts.size > 1) drawPath(
                Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } },
                line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            pts.forEachIndexed { i, o ->
                val r = if (i == pts.lastIndex) 6.dp.toPx() else 4.dp.toPx()
                drawCircle(bg, r + 2.dp.toPx(), o); drawCircle(line, r, o)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("min %.1f".format(lo0), color = SadoColors.TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("max %.1f".format(hi0), color = SadoColors.TextMuted, fontSize = 11.sp)
        }
    }
}
