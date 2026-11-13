package my.sado.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import my.sado.app.data.Watch
import kotlin.math.cos
import kotlin.math.sin

private const val STEP_MS = 2_000L

/**
 * A watch drawn in 2.5D, tilted and gently turning, that walks through [steps]: the button to press lights up and
 * sinks in, a tap on the screen ripples, and the screen shows where it ends up. The steps are listed beneath it.
 * [still] holds the first step (screenshots).
 */
@Composable
fun WatchGuide(brand: Watch.Brand, steps: List<Watch.Step>, modifier: Modifier = Modifier, still: Boolean = false) {
    var step by remember(steps) { mutableIntStateOf(0) }
    val press = remember { Animatable(0f) }
    LaunchedEffect(steps, still) {
        if (still) { press.snapTo(1f); return@LaunchedEffect }
        while (true) {
            press.snapTo(0f)
            delay(350)
            press.animateTo(1f, tween(220))
            press.animateTo(0f, tween(420))
            delay(STEP_MS - 990)
            step = (step + 1) % steps.size
        }
    }
    val spin = rememberInfiniteTransition(label = "tilt")
    val tilt by spin.animateFloat(-9f, 9f, infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Reverse), label = "tiltY")
    val measurer = rememberTextMeasurer()
    val accent = SadoColors.Accent
    val cur = steps[step.coerceIn(0, steps.lastIndex)]

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.fillMaxWidth().height(190.dp).graphicsLayer {
                rotationX = 14f
                rotationY = if (still) -6f else tilt
                cameraDistance = 14f * density
            },
        ) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.height * 0.36f
            drawStraps(c, r, brand.round)
            // soft shadow under the case
            drawOval(Color.Black.copy(alpha = 0.35f), topLeft = Offset(c.x - r * 1.05f, c.y + r * 0.86f), size = Size(r * 2.1f, r * 0.34f))
            drawButtons(c, r, brand, cur.button, press.value, accent)
            drawCase(c, r, brand.round)
            // the screen: what the watch shows at this step
            val label = measurer.measure(cur.screen, TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold))
            drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f))
            drawArc(accent, -90f, 90f + 270f * ((step + 1f) / steps.size), false, topLeft = Offset(c.x - r * 0.72f, c.y - r * 0.72f), size = Size(r * 1.44f, r * 1.44f), style = Stroke(width = r * 0.045f))
            if (cur.button == null) drawTap(Offset(c.x, c.y + r * 0.42f), r, press.value, accent)
            // gloss across the glass
            drawArc(Color.White.copy(alpha = 0.07f), 200f, 70f, true, topLeft = Offset(c.x - r * 0.82f, c.y - r * 0.82f), size = Size(r * 1.64f, r * 1.64f))
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(if (i == step) accent else SadoColors.Surface2),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", color = if (i == step) SadoColors.Bg else SadoColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
                    Text(
                        s.text, color = if (i == step) SadoColors.Text else SadoColors.TextMuted, fontSize = 14.sp,
                        fontWeight = if (i == step) FontWeight.ExtraBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawStraps(c: Offset, r: Float, round: Boolean) {
    val w = r * (if (round) 1.05f else 1.25f)
    val strap = Brush.verticalGradient(listOf(Color(0xFF2A2E35), Color(0xFF15181C)))
    // tapered a little towards the ends, which reads as depth
    listOf(-1f, 1f).forEach { dir ->
        val p = Path().apply {
            moveTo(c.x - w / 2, c.y + dir * r * 0.7f)
            lineTo(c.x + w / 2, c.y + dir * r * 0.7f)
            lineTo(c.x + w * 0.42f, c.y + dir * r * 1.55f)
            lineTo(c.x - w * 0.42f, c.y + dir * r * 1.55f)
            close()
        }
        drawPath(p, strap)
        drawLine(Color.White.copy(alpha = 0.05f), Offset(c.x - w * 0.3f, c.y + dir * r * 1.1f), Offset(c.x + w * 0.3f, c.y + dir * r * 1.1f), strokeWidth = r * 0.02f)
    }
}

private fun DrawScope.drawCase(c: Offset, r: Float, round: Boolean) {
    val metal = Brush.linearGradient(listOf(Color(0xFF8A9099), Color(0xFF3B4048), Color(0xFF6B717A)), start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r))
    if (round) {
        drawCircle(metal, r, c)
        drawCircle(Color(0xFF0B0D10), r * 0.86f, c)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF1C2128), Color(0xFF07080A)), c, r * 0.8f), r * 0.8f, c)
    } else {
        val s = Size(r * 1.5f, r * 1.9f)
        drawRoundRect(metal, Offset(c.x - s.width / 2, c.y - s.height / 2), s, CornerRadius(r * 0.35f))
        val i = Size(s.width * 0.86f, s.height * 0.88f)
        drawRoundRect(Color(0xFF07080A), Offset(c.x - i.width / 2, c.y - i.height / 2), i, CornerRadius(r * 0.28f))
    }
}

// side buttons on the rim; Garmin also has its three on the left. The pressed one sinks in and glows.
private fun DrawScope.drawButtons(c: Offset, r: Float, brand: Watch.Brand, pressed: Int?, t: Float, accent: Color) {
    val right = when (brand.buttons) { 1 -> listOf(0f); else -> listOf(-32f, 32f) }
    val left = if (brand.id == "garmin") listOf(148f, 180f, 212f) else emptyList()
    fun button(deg: Float, active: Boolean) {
        val inset = if (active) r * 0.05f * t else 0f
        val rad = Math.toRadians(deg.toDouble())
        val at = Offset(c.x + (r + r * 0.07f - inset) * cos(rad).toFloat(), c.y + (r + r * 0.07f - inset) * sin(rad).toFloat())
        if (active) drawCircle(accent.copy(alpha = 0.25f + 0.35f * t), r * (0.16f + 0.1f * t), at)
        rotate(deg, at) {
            drawRoundRect(if (active) accent else Color(0xFF5A6068), Offset(at.x - r * 0.07f, at.y - r * 0.09f), Size(r * 0.14f, r * 0.18f), CornerRadius(r * 0.04f))
        }
    }
    left.forEach { button(it, false) }
    right.forEachIndexed { i, d -> button(d, pressed == i) }
}

private fun DrawScope.drawTap(at: Offset, r: Float, t: Float, accent: Color) {
    drawCircle(accent.copy(alpha = 0.5f * (1 - t * 0.6f)), r * (0.08f + 0.12f * t), at)
    drawCircle(accent.copy(alpha = 0.6f * (1 - t)), r * (0.1f + 0.28f * t), at, style = Stroke(width = r * 0.025f))
}


/** Before a workout: start the activity on the watch, with the guide. */
@Composable
fun WatchStartDialog(brand: Watch.Brand, onGo: () -> Unit, onSkip: () -> Unit, onNever: () -> Unit, still: Boolean = false) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onSkip) {
        WatchStartCard(brand, onGo, onSkip, onNever, still)
    }
}

@Composable
fun WatchStartCard(brand: Watch.Brand, onGo: () -> Unit, onSkip: () -> Unit, onNever: () -> Unit, still: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).background(SadoColors.Surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("On aktiviti kat jam dulu", color = SadoColors.Text, fontSize = 22.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        Text("Supaya ${brand.label} rekod heart rate sesi ni. Sado ambil datanya lepas jam sync.", color = SadoColors.TextMuted, fontSize = 13.sp)
        WatchGuide(brand, brand.start, still = still)
        PrimaryButton("DAH ON, MULA", onClick = onGo)
        SecondaryButton("Skip kali ni", Modifier.fillMaxWidth(), onClick = onSkip)
        Text(
            "Jangan tunjuk lagi", color = SadoColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().pressable(onClick = onNever).padding(vertical = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** After a workout: stop and save the activity on the watch, so it doesn't keep recording. */
@Composable
fun WatchStopCard(brand: Watch.Brand, onDone: () -> Unit, onNever: () -> Unit, still: Boolean = false) {
    Card {
        Text("Stop aktiviti kat jam", color = SadoColors.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text("Kalau tak stop, jam terus rekod dan bateri terbazir.", color = SadoColors.TextMuted, fontSize = 13.sp)
        WatchGuide(brand, brand.stop, still = still)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Dah stop", Modifier.weight(1f), color = SadoColors.Accent, onClick = onDone)
            SecondaryButton("Jangan ingatkan", Modifier.weight(1f), onClick = onNever)
        }
    }
}

/** "Jam aku": the watch Sado found or the user picked, a way to change it, and the two reminders. */
@Composable
fun MyWatchCard(still: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val s = my.sado.app.data.Store.state.collectAsState().value.settings
    var picking by remember { mutableStateOf(false) }
    var looking by remember { mutableIntStateOf(0) }
    var showGuide by remember { mutableStateOf(false) }
    val found by androidx.compose.runtime.produceState(emptyList<Watch.Found>(), looking) {
        if (!still) value = runCatching { my.sado.app.data.WatchDetect.all(context) }.getOrDefault(emptyList())
    }
    LaunchedEffect(looking) { if (!still) runCatching { my.sado.app.data.WatchDetect.autoDetect(context) } }
    fun pick(brand: String, model: String?) {
        my.sado.app.data.Store.update { st ->
            st.copy(settings = st.settings.copy(
                watch = brand, watchModel = model, watchConfirmed = true,
                hrMode = if (Watch.brand(brand)?.canBroadcast == true) st.settings.hrMode else 0,
            ))
        }
        picking = false
    }
    val brand = Watch.brand(s.watch)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (s.watch != null && !s.watchConfirmed) "Jumpa: " + Watch.label(s.watch, s.watchModel) else Watch.label(s.watch, s.watchModel),
                    color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    when {
                        s.watch == null -> "Tak jumpa jam. Pilih sendiri, atau Takde jam."
                        s.watch == Watch.NONE -> "Semua jalan tanpa jam. Kcal guna anggaran."
                        !s.watchConfirmed -> "Dikesan dalam phone ni. Betul?"
                        else -> "Arahan dan peringatan ikut jam ni."
                    },
                    color = SadoColors.TextMuted, fontSize = 13.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.watch != null && !s.watchConfirmed) SecondaryButton("Betul", Modifier.weight(1f), color = SadoColors.Accent) { pick(s.watch, s.watchModel) }
            SecondaryButton(if (picking) "Tutup" else "Tukar", Modifier.weight(1f)) { picking = !picking }
            SecondaryButton("Kesan semula", Modifier.weight(1f)) { looking++ }
        }
        if (picking) {
            if (found.isNotEmpty()) Text("Dijumpai dalam phone", color = SadoColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            found.forEach { f -> ChoiceRow(Watch.label(f.brand, f.model), "Dari " + f.how) { pick(f.brand, f.model) } }
            Text("Atau pilih", color = SadoColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Watch.brands.forEach { b -> ChoiceRow(b.label, null) { pick(b.id, null) } }
            ChoiceRow("Takde jam", null) { pick(Watch.NONE, null) }
        }
        if (brand != null) {
            ReminderToggle("Ingatkan on aktiviti", "Sebelum workout, dengan cara on kat ${brand.label}", s.remindWatchStart) { on ->
                my.sado.app.data.Store.update { it.copy(settings = it.settings.copy(remindWatchStart = on)) }
            }
            ReminderToggle("Ingatkan stop aktiviti", "Lepas workout, supaya jam tak terus rekod", s.remindWatchStop) { on ->
                my.sado.app.data.Store.update { it.copy(settings = it.settings.copy(remindWatchStop = on)) }
            }
            Text(
                if (showGuide) "Tutup cara on" else "Tengok cara on kat ${brand.label}", color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().pressable { showGuide = !showGuide }.padding(vertical = 6.dp),
            )
            if (showGuide) WatchGuide(brand, brand.start, still = still)
        }
    }
}

@Composable
private fun ChoiceRow(title: String, sub: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(SadoColors.Surface2).pressable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            if (sub != null) Text(sub, color = SadoColors.TextMuted, fontSize = 12.sp)
        }
        Text("Pilih", color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ReminderToggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = SadoColors.TextMuted, fontSize = 12.sp)
        }
        androidx.compose.material3.Switch(on, onChange, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = SadoColors.Accent, checkedThumbColor = SadoColors.Bg))
    }
}
