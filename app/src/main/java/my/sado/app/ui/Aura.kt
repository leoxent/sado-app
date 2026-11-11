package my.sado.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import my.sado.app.data.Stage

/**
 * Glow behind the character. Each body has its own colour, so moving up a body upgrades the aura
 * instead of taking something away; inside a body the tier makes it stronger, and tier III adds
 * rising sparks. Kurus I has none: that is where everyone starts.
 */
object AuraStyle {
    val colors = listOf(Color(0xFFB4BAC8), Color(0xFFC8F04A), Color(0xFF5AC8FA), Color(0xFFA78BFA), Color(0xFFFFC94A))

    fun strength(st: Stage): Float = when {
        st.body == 0 -> listOf(0f, 0.30f, 0.45f)[st.tier]
        else -> listOf(0.45f, 0.70f, 0.95f)[st.tier]
    }

    fun sparks(st: Stage): Int = when {
        st.tier < 2 -> 0
        st.body == 0 -> 0
        st.body == 4 -> 18
        else -> 12
    }
}

@Composable
fun Aura(stage: Stage, modifier: Modifier = Modifier, dim: Boolean = false, glow: Float = 1f, content: @Composable BoxScope.() -> Unit) {
    val color = AuraStyle.colors[stage.body]
    // a tired character keeps a faint glow, no sparks
    val strength = AuraStyle.strength(stage) * (if (dim) 0.35f else 1f) * glow
    val sparks = if (dim) 0 else AuraStyle.sparks(stage)
    val still = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "aura")
    // pulse only from tier II up, so a stronger aura also feels more alive
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "pulse")
    val rise by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "rise")

    Box(modifier, contentAlignment = Alignment.Center) {
        if (strength > 0f) Canvas(Modifier.matchParentSize()) {
            val beat = if (still || stage.tier == 0 || dim) 0f else pulse
            val a = strength * (0.85f + 0.15f * beat)
            // stronger tiers also reach further out
            val r = size.minDimension * (0.44f + 0.06f * stage.tier + 0.04f * beat)
            val c = Offset(size.width / 2, size.height * 0.55f)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a * 0.7f), color.copy(alpha = a * 0.35f), Color.Transparent), c, r), r, c)
            // body-shaped inner glow, taller than wide like the character; its edge shows on a picture, so not there
            if (glow >= 1f) drawOval(
                Brush.radialGradient(listOf(color.copy(alpha = a * 0.9f), Color.Transparent), c, r * 0.7f),
                Offset(c.x - r * 0.38f, c.y - r * 0.75f), Size(r * 0.76f, r * 1.5f),
            )
            for (i in 0 until sparks) {
                val phase = if (still) i / sparks.toFloat() else (rise + i / sparks.toFloat()) % 1f
                val x = c.x + (((i * 37) % 100) / 100f - 0.5f) * r * 1.2f
                val y = c.y + r * 0.8f - phase * r * 1.7f
                val fade = (1f - phase) * phase * 4f
                drawCircle(color.copy(alpha = (0.9f * fade).coerceIn(0f, 1f)), size.minDimension * (0.012f + 0.008f * (i % 3)), Offset(x, y))
            }
        }
        content()
    }
}

/** Three z's drifting up from the character's head while he is tired. */
@Composable
fun Zzz(modifier: Modifier = Modifier) {
    val still = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "zzz")
    val rise by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "rise")
    Box(modifier.size(width = 70.dp, height = 90.dp)) {
        for (i in 0 until 3) {
            val phase = if (still) 0.3f + i * 0.25f else (rise + i / 3f) % 1f
            Text(
                "z", color = SadoColors.Text.copy(alpha = (1f - phase) * 0.9f), fontSize = (14 + 6 * i).sp,
                fontFamily = Display, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.BottomStart).graphicsLayer { translationX = phase * 70f + i * 18f; translationY = -phase * 200f },
            )
        }
    }
}
