package my.sado.app.ui

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import my.sado.app.data.Store
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------- motion helpers ----------

/** Springy press: shrinks to 96% while held. Put it FIRST in a modifier chain so the whole card scales. */
fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(src, ripple(color = SadoColors.Accent), enabled = enabled, role = role) { Feedback.tap(); onClick() }
}

/** Fade + rise on first show, staggered by [index]; skipped when the system has animations off. */
fun Modifier.appear(index: Int = 0): Modifier = composed {
    if (LocalReduceMotion.current) return@composed this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        a.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }
    graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 36f }
}

// ---------- icons (Material Symbols paths, Apache 2.0) ----------

private fun icon(name: String, path: String, evenOdd: Boolean = false) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.White), pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero)
    .build()

object SadoIcons {
    val Flame = icon("flame", "M19.48,12.35c-1.57,-4.08 -7.16,-4.3 -5.81,-10.23c0.1,-0.44 -0.37,-0.78 -0.75,-0.55C9.29,3.71 6.68,8 8.87,13.62c0.18,0.46 -0.36,0.89 -0.75,0.59c-1.81,-1.37 -2,-3.34 -1.84,-4.75c0.06,-0.52 -0.62,-0.77 -0.91,-0.34C4.69,10.16 4,11.84 4,14.37c0.38,5.6 5.11,7.32 6.81,7.54c2.43,0.31 5.06,-0.14 6.95,-1.87C19.84,18.11 20.6,15.03 19.48,12.35z")
    val Bolt = icon("bolt", "M7,2v11h3v9l7,-12h-4l4,-8z")
    val Timer = icon("timer", "M15,1H9v2h6V1zM11,14h2V8h-2V14zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9s9,-4.03 9,-9C21,10.88 20.26,8.93 19.03,7.39zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7s7,3.13 7,7S15.87,20 12,20z")
    val Steps = icon("steps", "M13.5,5.5c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2s-2,0.9 -2,2S12.4,5.5 13.5,5.5zM9.8,8.9L7,23h2.1l1.8,-8l2.1,2v6h2v-7.5l-2.1,-2l0.6,-3C14.8,12 16.8,13 19,13v-2c-1.9,0 -3.5,-1 -4.3,-2.4l-1,-1.6c-0.4,-0.6 -1,-1 -1.7,-1c-0.3,0 -0.5,0.1 -0.8,0.1L6,8.3V13h2V9.6L9.8,8.9")
    val Heart = icon("heart", "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5C2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3C19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z")
    val Camera = icon("camera", "M12,15.2m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z")
    val Mic = icon("mic", "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z")
    val Watch = icon("watch", "M20,12c0,-2.54 -1.19,-4.81 -3.04,-6.27L16,0H8l-0.95,5.73C5.19,7.19 4,9.45 4,12s1.19,4.81 3.05,6.27L8,24h8l0.96,-5.73C18.81,16.81 20,14.54 20,12zM6,12c0,-3.31 2.69,-6 6,-6s6,2.69 6,6 -2.69,6 -6,6 -6,-2.69 -6,-6z")
    // flexed arm: upper arm along the bottom, bicep bulge, forearm and fist up on the right
    val Flex = icon("flex", "M3,15C3.5,10.5 7.5,8.2 11,9.2C12.6,9.7 13.6,10.6 14.1,11.6L13.2,7.6C12.2,7.2 11.6,6.2 11.9,5.1C12.3,3.4 14.2,2.4 16,2.6C18.1,2.8 19.3,4.4 19.2,6.3L19.6,14.5C19.8,17.8 17.6,20 14.6,20L6,20C4.3,20 3,18.7 3,17ZM6.6,16.4C8.2,13.9 10.9,13.1 13.3,13.9C11,14.1 8.9,15 7.6,16.9Z", evenOdd = true)
    val Dumbbell = icon("dumbbell", "M20.57,14.86L22,13.43L20.57,12L17,15.57L8.43,7L12,3.43L10.57,2L9.14,3.43L7.71,2L5.57,4.14L4.14,2.71L2.71,4.14l1.43,1.43L2,7.71l1.43,1.43L2,10.57L3.43,12L7,8.43L15.57,17L12,20.57L13.43,22l1.43,-1.43L16.29,22l2.14,-2.14l1.43,1.43l1.43,-1.43l-1.43,-1.43L22,16.29L20.57,14.86z")
}

// ---------- buttons ----------

/** Chunky game-style button: a darker block sits underneath and the face sinks into it on press. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, glow: Boolean = false, onClick: () -> Unit) =
    ChunkyButton(text, modifier.fillMaxWidth(), enabled, SadoColors.Accent, SadoColors.AccentDeep, SadoColors.OnAccent, 16, onClick, glow && enabled)

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, color: Color = SadoColors.Text, onClick: () -> Unit) =
    ChunkyButton(text, modifier, true, SadoColors.Surface2, SadoColors.Deep, color, 14, onClick)

@Composable
private fun ChunkyButton(text: String, modifier: Modifier, enabled: Boolean, face: Color, under: Color, content: Color, size: Int, onClick: () -> Unit, glow: Boolean = false) {
    val src = remember { MutableInteractionSource() }
    // "press me now": a halo that breathes around the button and a light sweep across its face
    val pulse = if (glow) androidx.compose.animation.core.rememberInfiniteTransition(label = "glow") else null
    val halo = pulse?.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "halo")
    val sweep = pulse?.animateFloat(-0.4f, 1.4f, androidx.compose.animation.core.infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "sweep")
    val haloColor = face
    val pressed by src.collectIsPressedAsState()
    val depth by animateFloatAsState(if (pressed && enabled) 0f else 4f, tween(70), label = "depth")
    val faceColor by animateColorAsState(if (enabled) face else SadoColors.Surface2, label = "face")
    Box(
        modifier
            .then(
                if (halo == null) Modifier else Modifier.drawBehind {
                    val t = halo.value
                    // two rings a half-beat apart, each growing out and fading
                    for (k in 0..1) {
                        val p = (t + k * 0.5f) % 1f
                        val g = (2f + 14f * p).dp.toPx()
                        drawRoundRect(
                            haloColor.copy(alpha = 0.45f * (1f - p)),
                            topLeft = androidx.compose.ui.geometry.Offset(-g, -g),
                            size = androidx.compose.ui.geometry.Size(this.size.width + 2 * g, this.size.height + 2 * g),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx() + g),
                        )
                    }
                },
            )
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) under else SadoColors.Surface)
            .clickable(src, null, enabled = enabled, role = Role.Button) { Feedback.tap(); onClick() },
    ) {
        Box(
            Modifier.fillMaxWidth().height(56.dp).offset(y = (4f - depth).dp)
                .clip(RoundedCornerShape(20.dp)).background(faceColor)
                .then(
                    if (sweep == null) Modifier else Modifier.drawWithContent {
                        drawContent()
                        val x = this.size.width * sweep.value
                        drawRect(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.55f), 1f to Color.Transparent,
                                start = androidx.compose.ui.geometry.Offset(x - this.size.height, 0f), end = androidx.compose.ui.geometry.Offset(x + this.size.height, this.size.height),
                            ),
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = if (enabled) content else SadoColors.TextMuted, fontSize = size.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Display, letterSpacing = 0.5.sp, maxLines = 1)
        }
    }
}

// ---------- surfaces ----------

@Composable
fun Card(modifier: Modifier = Modifier, padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SadoColors.Surface)
            .border(1.dp, SadoColors.Outline, RoundedCornerShape(24.dp)).padding(padding.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Settings-style row: coloured icon chip, title, optional subtitle, chevron. */
@Composable
fun MenuRow(title: String, sub: String?, color: Color, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).clip(RoundedCornerShape(18.dp)).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (color == SadoColors.Danger) color else SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, color = SadoColors.TextMuted, fontSize = 12.sp)
        }
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SadoColors.TextMuted)
    }
}

/** Bento tile: tinted block with an icon chip, a big number and a label. */
@Composable
fun Tile(value: String, label: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = SadoColors.OnAccent, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(value, color = SadoColors.Text, fontSize = 28.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(label, color = SadoColors.TextMuted, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
fun Label(text: String) = Text(text.uppercase(), color = SadoColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)

@Composable
fun Title(text: String, sub: String? = null, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(
                Modifier.size(48.dp).pressable(onClick = onBack).clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface)
                    .border(1.dp, SadoColors.Outline, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Balik", tint = SadoColors.Text) }
            Box(Modifier.size(12.dp))
        }
        Column {
            Text(text, color = SadoColors.Text, fontSize = 30.sp, lineHeight = 32.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            if (sub != null) Text(sub, color = SadoColors.TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun OptionRow(title: String, sub: String?, selected: Boolean, multi: Boolean = false, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) SadoColors.Accent else SadoColors.Outline, label = "optBorder")
    val shape = if (multi) RoundedCornerShape(8.dp) else CircleShape
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(role = if (multi) Role.Checkbox else Role.RadioButton, onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) SadoColors.Accent.copy(alpha = 0.10f) else SadoColors.Surface)
            .border(2.dp, border, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        Box(
            Modifier.size(24.dp).clip(shape).background(if (selected) SadoColors.Accent else Color.Transparent)
                .border(2.dp, if (selected) SadoColors.Accent else SadoColors.TextMuted.copy(alpha = 0.4f), shape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Filled.Check, null, tint = SadoColors.OnAccent, modifier = Modifier.size(16.dp)) }
    }
}

/** Pill switcher whose lime thumb slides between options. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface)
            .border(1.dp, SadoColors.Outline, RoundedCornerShape(20.dp)).padding(4.dp),
    ) {
        val w = maxWidth / options.size.coerceAtLeast(1)
        val x by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), label = "seg")
        // nothing chosen yet (selected = -1): no thumb at all
        if (selected >= 0) Box(Modifier.offset(x = w * x).size(width = w, height = 44.dp).clip(RoundedCornerShape(16.dp)).background(SadoColors.Accent))
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Tab) { Feedback.tap(); onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (i == selected) SadoColors.OnAccent else SadoColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }
    }
}

/** Progress bar; lime uses the XP gradient, and the fill eases to its new value. */
@Composable
fun Bar(fraction: Float, color: Color = SadoColors.Accent, height: Int = 10) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), label = "bar")
    Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(height.dp)).background(SadoColors.Track)) {
        val fill = Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(height.dp))
        Box(if (color == SadoColors.Accent) fill.background(SadoColors.XpGradient) else fill.background(color))
    }
}

@Composable
fun Tag(text: String, color: Color = SadoColors.Accent, filled: Boolean = false) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = if (filled) SadoColors.OnAccent else color,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
    )
}

/** Number that counts up from 0 when first shown. */
@Composable
fun CountUp(target: Int, prefix: String = "", suffix: String = "", color: Color = SadoColors.Text, size: Int = 44) {
    val reduce = LocalReduceMotion.current
    var shown by remember { mutableIntStateOf(if (reduce) target else 0) }
    LaunchedEffect(target) {
        val a = Animatable(shown.toFloat())
        a.animateTo(target.toFloat(), tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { shown = value.toInt() }
    }
    Text("$prefix$shown$suffix", color = color, fontSize = size.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
}

/** One-shot confetti burst for level-ups and finished sessions. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    if (LocalReduceMotion.current) return
    val colors = listOf(SadoColors.Accent, SadoColors.Streak, SadoColors.Violet, SadoColors.Sky, SadoColors.Pink)
    val bits = remember {
        List(70) {
            val ang = Random.nextDouble(-PI * 0.9, -PI * 0.1)
            val speed = Random.nextDouble(0.5, 1.2).toFloat()
            Triple(Offset(cos(ang).toFloat() * speed, sin(ang).toFloat() * speed), colors[it % colors.size], Random.nextFloat() * 360f)
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2200, easing = LinearEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val origin = Offset(size.width / 2, size.height * 0.45f)
        bits.forEach { (v, c, rot) ->
            // launch outward, then gravity pulls the pieces down
            val x = origin.x + v.x * size.width * 0.6f * p
            val y = origin.y + v.y * size.height * 0.55f * p + size.height * 0.9f * p * p
            drawRect(c.copy(alpha = 1f - p), topLeft = Offset(x, y), size = Size(14f, 4f + 8f * kotlin.math.abs(sin((rot + p * 720f) * PI.toFloat() / 180f))))
        }
    }
}

// Muscle map: res/raw/muscles_*.png labels every body pixel with its muscle (red = index × 16), traced by
// flood fill along the illustration's own outlines, so a tint follows each muscle's drawn shape exactly.
private val muscleIds = listOf("", "chest", "delts", "traps", "biceps", "triceps", "forearms", "abs", "obliques", "lats", "lowerback", "glutes", "hamstrings", "quads", "calves")

/**
 * Heat colours for how hard a muscle works: light orange for a little, orange for solid work, red for a lot
 * or the main mover. Lightness steps down with each level, so the order still reads without colour vision.
 */
object Heat {
    val light = Color(0xFFFDE68A)
    val mid = Color(0xFFFB923C)
    val heavy = Color(0xFFDC2626)

    // one session: 1–2, 3–5, 6+ sets; one week: 1–4, 5–9, 10+ (10+ a week is the productive range)
    fun color(sets: Float, week: Boolean): Color? {
        val (a, b) = if (week) 5f to 10f else 3f to 6f
        return when { sets <= 0f -> null; sets < a -> light; sets < b -> mid; else -> heavy }
    }

    fun bands(week: Boolean) = if (week) listOf("1–4", "5–9", "10+") else listOf("1–2", "3–5", "6+")

    /** Sets per muscle: the main muscles get every set, helpers count half. */
    fun sets(moves: List<Pair<String, Int>>): Map<String, Float> {
        val out = mutableMapOf<String, Float>()
        moves.forEach { (id, n) ->
            val m = my.sado.app.data.Catalog.moves.firstOrNull { it.id == id } ?: return@forEach
            m.muscles.forEach { out[it] = (out[it] ?: 0f) + n }
            (m.secondary - m.muscles.toSet()).forEach { out[it] = (out[it] ?: 0f) + n / 2f }
        }
        return out
    }
}

private object MuscleTint {
    private val labels = HashMap<Boolean, IntArray>()
    private val shades = HashMap<Boolean, IntArray>()
    // the flat body fill (207, 208, 202): a pixel this bright shows the tint at full strength
    private const val FILL_LUM = 207f
    private val sizes = HashMap<Boolean, Pair<Int, Int>>()
    private val cache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 32
    }

    private fun label(context: android.content.Context, back: Boolean): IntArray = labels.getOrPut(back) {
        val opts = android.graphics.BitmapFactory.Options().apply { inScaled = false; inPremultiplied = false }
        val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, if (back) my.sado.app.R.raw.muscles_back else my.sado.app.R.raw.muscles_front, opts)
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        sizes[back] = bmp.width to bmp.height
        IntArray(px.size) { i -> if (android.graphics.Color.alpha(px[i]) < 128) 0 else (android.graphics.Color.red(px[i]) + 8) / 16 }
    }

    // the illustration's own pixels: alpha in the top byte, how dark it is against the fill (0–255) below
    private fun shade(context: android.content.Context, back: Boolean): IntArray = shades.getOrPut(back) {
        val opts = android.graphics.BitmapFactory.Options().apply { inScaled = false; inPremultiplied = false }
        val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, if (back) my.sado.app.R.drawable.body_back else my.sado.app.R.drawable.body_front, opts)
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        IntArray(px.size) { i ->
            val c = px[i]
            val lum = 0.299f * android.graphics.Color.red(c) + 0.587f * android.graphics.Color.green(c) + 0.114f * android.graphics.Color.blue(c)
            (android.graphics.Color.alpha(c) shl 8) or (lum / FILL_LUM * 255f).toInt().coerceIn(0, 255)
        }
    }

    // a same-size layer: each listed muscle in its true colour, darkened along the outlines so they stay drawn
    fun layer(context: android.content.Context, back: Boolean, colors: Map<String, Color>): ImageBitmap = synchronized(this) {
        val key = "$back|" + colors.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value.value}" }
        cache[key]?.let { return it }
        val lab = label(context, back)
        val (w, h) = sizes.getValue(back)
        val sh = shade(context, back)
        val pick = IntArray(muscleIds.size) { i -> colors[muscleIds[i]]?.toArgb() ?: 0 }
        val out = IntArray(lab.size) { i ->
            val c = pick[lab[i].coerceIn(0, muscleIds.lastIndex)]
            if (c == 0) 0 else {
                val f = (sh[i] and 0xFF) / 255f
                android.graphics.Color.argb(sh[i] shr 8, (android.graphics.Color.red(c) * f).toInt(), (android.graphics.Color.green(c) * f).toInt(), (android.graphics.Color.blue(c) * f).toInt())
            }
        }
        android.graphics.Bitmap.createBitmap(out, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap().also { cache[key] = it }
    }
}

// Body illustration with muscles tinted
@Composable
private fun BodyCanvas(colors: Map<String, Color>, back: Boolean, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val img = ImageBitmap.imageResource(if (back) my.sado.app.R.drawable.body_back else my.sado.app.R.drawable.body_front)
    val layer = remember(back, colors) { if (colors.isEmpty()) null else MuscleTint.layer(context, back, colors) }
    Canvas(modifier) {
        val scale = minOf(size.width / img.width, size.height / img.height)
        val at = IntOffset(((size.width - img.width * scale) / 2).roundToInt(), ((size.height - img.height * scale) / 2).roundToInt())
        val fit = IntSize((img.width * scale).roundToInt(), (img.height * scale).roundToInt())
        drawImage(img, dstOffset = at, dstSize = fit, filterQuality = FilterQuality.High)
        if (layer != null) drawImage(layer, dstOffset = at, dstSize = fit, filterQuality = FilterQuality.High)
    }
}

/** One move: the main muscles in red, the helpers in light orange. */
@Composable
fun MuscleMap(primary: List<String>, secondary: List<String>, back: Boolean, modifier: Modifier = Modifier) =
    BodyCanvas(secondary.associateWith { Heat.light } + primary.associateWith { Heat.heavy }, back, modifier)

/** A session or a week: each muscle coloured by how many sets it gets. */
@Composable
fun MuscleHeat(sets: Map<String, Float>, week: Boolean, back: Boolean, modifier: Modifier = Modifier) =
    BodyCanvas(sets.mapNotNull { (m, n) -> Heat.color(n, week)?.let { m to it } }.toMap(), back, modifier)

@Composable
fun HeatLegend(week: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Heat.light, Heat.mid, Heat.heavy).zip(Heat.bands(week)).forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(c))
                Text(t, color = SadoColors.TextMuted, fontSize = 12.sp)
            }
        }
        Text(if (week) "set seminggu" else "set", color = SadoColors.TextMuted, fontSize = 12.sp)
    }
}

/** Main and helper muscles for one move. */
@Composable
fun RoleLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Heat.heavy to "Otot utama", Heat.light to "Membantu").forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(c))
                Text(t, color = SadoColors.TextMuted, fontSize = 12.sp)
            }
        }
    }
}

// Beeps and buzzes; respects the sound and haptics switches in Settings
object Feedback {
    // custom UI sounds made by tools/ui-sounds.js (res/raw/sfx_*.wav), played through SoundPool so they start instantly
    private var pool: android.media.SoundPool? = null
    private val ids = mutableMapOf<String, Int>()
    private var vibrator: Vibrator? = null
    private var petStream = 0
    private val PET_RATES = listOf(1.3f, 1.12f, 1f, 0.84f)
    private val PET_LEGEND_CALLS = setOf("serigala", "harimau")
    // the length of each recording (PetCallTest checks them against the files)
    val PET_CALL_MILLIS = mapOf(
        "pet_beruang" to 1050, "pet_buaya" to 1150, "pet_harimau" to 1500, "pet_harimau_legenda" to 2400,
        "pet_helang" to 1200, "pet_seladang" to 1550, "pet_serigala" to 2000, "pet_serigala_legenda" to 3700,
    )

    fun init(context: Context) {
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_GAME)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        pool = runCatching { android.media.SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build() }.getOrNull()
        pool?.let { p ->
            mapOf(
                "rep" to my.sado.app.R.raw.sfx_rep, "tick" to my.sado.app.R.raw.sfx_tick, "success" to my.sado.app.R.raw.sfx_success,
                "achievement" to my.sado.app.R.raw.sfx_achievement, "levelup" to my.sado.app.R.raw.sfx_levelup,
                "click" to my.sado.app.R.raw.sfx_click, "add" to my.sado.app.R.raw.sfx_add,
                "open" to my.sado.app.R.raw.sfx_open, "close" to my.sado.app.R.raw.sfx_close,
                "pet_harimau" to my.sado.app.R.raw.sfx_pet_harimau, "pet_harimau_legenda" to my.sado.app.R.raw.sfx_pet_harimau_legenda,
                "pet_serigala" to my.sado.app.R.raw.sfx_pet_serigala, "pet_serigala_legenda" to my.sado.app.R.raw.sfx_pet_serigala_legenda,
                "pet_helang" to my.sado.app.R.raw.sfx_pet_helang, "pet_beruang" to my.sado.app.R.raw.sfx_pet_beruang,
                "pet_seladang" to my.sado.app.R.raw.sfx_pet_seladang, "pet_buaya" to my.sado.app.R.raw.sfx_pet_buaya,
            ).forEach { (k, res) -> runCatching { ids[k] = p.load(context, res, 1) } }
        }
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun buzz(ms: Long) {
        if (!Store.state.value.settings.haptics) return
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    /** A short pattern: alternating off/on milliseconds, starting with an off. */
    private fun buzz(pattern: LongArray) {
        if (!Store.state.value.settings.haptics) return
        runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    private fun play(key: String, volume: Float = 0.8f) {
        if (!Store.state.value.settings.sound) return
        val id = ids[key] ?: return
        runCatching { pool?.play(id, volume, volume, 1, 0, 1f) }
    }

    // every button and tappable card: a quiet click under the buzz
    fun tap() { buzz(12); play("click", 0.35f) }
    fun add() { buzz(40); play("add", 0.8f) }
    fun fold(open: Boolean) { buzz(15); play(if (open) "open" else "close", 0.6f) }
    fun rep() { buzz(25); play("rep", 0.7f) }
    fun success() { buzz(60); play("success") }
    fun countdown() = play("tick", 0.7f)
    fun achievement() { buzz(longArrayOf(0, 40, 70, 60)); play("achievement") }
    fun levelUp() { buzz(longArrayOf(0, 80, 90, 80, 90, 160)); play("levelup", 0.9f) }

    /** The animal's call for a stage: young ones play faster and higher, Legenda deeper or from its own recording. */
    fun petCall(kind: String, stage: Int): Pair<String, Float> =
        if (stage == 3 && kind in PET_LEGEND_CALLS) "pet_${kind}_legenda" to 1f
        else "pet_$kind" to PET_RATES[stage]

    /** How long the call lasts at the speed it plays: the pet's mouth stays open for it. */
    fun petCallMillis(kind: String, stage: Int): Long {
        val (key, rate) = petCall(kind, stage)
        return ((PET_CALL_MILLIS[key] ?: 1000) / rate).toLong()
    }

    /** A new tap cuts the last call off, so tapping fast never piles up howls. A sleeping pet only mumbles. */
    fun pet(kind: String, stage: Int, asleep: Boolean) {
        buzz(25)
        if (!Store.state.value.settings.sound) return
        val (key, rate) = petCall(kind, stage)
        val id = ids[key] ?: return
        val volume = if (asleep) 0.3f else 0.85f
        runCatching {
            pool?.stop(petStream)
            petStream = pool?.play(id, volume, volume, 2, 0, if (asleep) rate * 0.9f else rate) ?: 0
        }
    }
}
