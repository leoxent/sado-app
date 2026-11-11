package my.sado.app.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import my.sado.app.R
import kotlin.random.Random

/**
 * Closed eyes for a character or pet picture, so it can blink. Each overlay holds only the eyes (tools/blink-patch.ps1
 * cuts them from an AI sheet), the same size as the picture: swapping the whole picture would make it jump.
 */
object Blink {
    val overlays: Map<Int, Int> = mapOf(
        R.drawable.tier1 to R.drawable.blink_tier1,
        R.drawable.tier2 to R.drawable.blink_tier2,
        R.drawable.tier3 to R.drawable.blink_tier3,
        R.drawable.tier4 to R.drawable.blink_tier4,
        R.drawable.tier5 to R.drawable.blink_tier5,
        R.drawable.tier6 to R.drawable.blink_tier6,
        R.drawable.tier7 to R.drawable.blink_tier7,
        R.drawable.tier8 to R.drawable.blink_tier8,
        R.drawable.tier9 to R.drawable.blink_tier9,
        R.drawable.tier10 to R.drawable.blink_tier10,
        R.drawable.tier11 to R.drawable.blink_tier11,
        R.drawable.tier12 to R.drawable.blink_tier12,
        R.drawable.tier13 to R.drawable.blink_tier13,
        R.drawable.tier14 to R.drawable.blink_tier14,
        R.drawable.tier15 to R.drawable.blink_tier15,
        R.drawable.gempal1 to R.drawable.blink_gempal1,
        R.drawable.gempal2 to R.drawable.blink_gempal2,
        R.drawable.gempal3 to R.drawable.blink_gempal3,
        R.drawable.bangkit1 to R.drawable.blink_bangkit1,
        R.drawable.bangkit2 to R.drawable.blink_bangkit2,
        R.drawable.bangkit3 to R.drawable.blink_bangkit3,
        R.drawable.pet_harimau1 to R.drawable.blink_pet_harimau1,
        R.drawable.pet_harimau2 to R.drawable.blink_pet_harimau2,
        R.drawable.pet_harimau3 to R.drawable.blink_pet_harimau3,
        R.drawable.pet_harimau4 to R.drawable.blink_pet_harimau4,
        R.drawable.pet_helang1 to R.drawable.blink_pet_helang1,
        R.drawable.pet_helang2 to R.drawable.blink_pet_helang2,
        R.drawable.pet_helang3 to R.drawable.blink_pet_helang3,
        R.drawable.pet_helang4 to R.drawable.blink_pet_helang4,
        R.drawable.pet_beruang1 to R.drawable.blink_pet_beruang1,
        R.drawable.pet_beruang2 to R.drawable.blink_pet_beruang2,
        R.drawable.pet_beruang3 to R.drawable.blink_pet_beruang3,
        R.drawable.pet_beruang4 to R.drawable.blink_pet_beruang4,
        R.drawable.pet_seladang1 to R.drawable.blink_pet_seladang1,
        R.drawable.pet_seladang2 to R.drawable.blink_pet_seladang2,
        R.drawable.pet_seladang3 to R.drawable.blink_pet_seladang3,
        R.drawable.pet_seladang4 to R.drawable.blink_pet_seladang4,
        R.drawable.pet_buaya1 to R.drawable.blink_pet_buaya1,
        R.drawable.pet_buaya2 to R.drawable.blink_pet_buaya2,
        R.drawable.pet_buaya3 to R.drawable.blink_pet_buaya3,
        R.drawable.pet_buaya4 to R.drawable.blink_pet_buaya4,
        R.drawable.pet_serigala1 to R.drawable.blink_pet_serigala1,
        R.drawable.pet_serigala2 to R.drawable.blink_pet_serigala2,
        R.drawable.pet_serigala3 to R.drawable.blink_pet_serigala3,
        R.drawable.pet_serigala4 to R.drawable.blink_pet_serigala4,
    )
}

/**
 * Goes over the picture, the same size as it, inside the same box. Does nothing for a picture with no overlay yet.
 * The eyes snap shut and open like a cartoon: a fade would show the open and closed eyes on top of each other.
 * hold keeps them shut or open, for checking the drawing in a screenshot.
 */
@Composable
fun BlinkLids(picture: Int, modifier: Modifier = Modifier, hold: Boolean? = null) {
    val overlay = Blink.overlays[picture] ?: return
    val still = LocalReduceMotion.current
    var shut by remember(picture) { mutableStateOf(false) }
    LaunchedEffect(picture, still, hold) {
        shut = false
        if (still || hold != null) return@LaunchedEffect
        suspend fun once() {
            shut = true
            delay(110)
            shut = false
        }
        while (true) {
            delay(Random.nextLong(3000, 6000))
            once()
            if (Random.nextInt(5) == 0) { delay(140); once() } // now and then a double blink
        }
    }
    Image(painterResource(overlay), null, modifier.graphicsLayer { alpha = if (hold ?: shut) 1f else 0f })
}
