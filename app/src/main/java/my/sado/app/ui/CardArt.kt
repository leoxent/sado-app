package my.sado.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import my.sado.app.R

/** Gemini-made scenes behind the session cards: the figure stands on the right, the left stays dark for text. */
object CardArt {
    private val art = mapOf(
        "Pull" to R.drawable.card_pull, "Push" to R.drawable.card_push, "Legs" to R.drawable.card_legs,
        "Full body" to R.drawable.card_fullbody, "Upper" to R.drawable.card_upper, "Lower" to R.drawable.card_lower,
        "HIIT" to R.drawable.card_hiit, "Power" to R.drawable.card_power, "Circuit" to R.drawable.card_circuit,
        "Legs+Push" to R.drawable.card_legspush, "Legs+Core" to R.drawable.card_legscore, "Rest" to R.drawable.card_rest,
    )

    /** The scene for a session tag, or null so the card keeps its plain look. */
    fun of(tag: String?): Int? = tag?.let { art[it] }

    // over a scene the card is always dark, whatever the app theme
    val Text = Color(0xFFF6F7F9)
    val Muted = Color(0xC7F6F7F9)
    val Accent = Color(0xFFC8F04A)
    val OnAccent = Color(0xFF0B0C10)
}

/**
 * A card with [art] behind its content: the scene cropped to the card, darkened from the left so text
 * reads, and from the bottom so the move chips sit on shade. [modifier] carries the card's shape and clip.
 */
@Composable
fun ArtCard(art: Int, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        Image(
            painterResource(art), contentDescription = null, contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd, modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.88f), 0.55f to Color.Black.copy(alpha = 0.45f), 1f to Color.Transparent),
            ),
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
        content()
    }
}
