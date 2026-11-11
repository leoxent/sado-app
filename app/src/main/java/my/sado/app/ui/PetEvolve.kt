package my.sado.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import my.sado.app.data.PetFood
import my.sado.app.data.Pets
import my.sado.app.data.Store
import kotlin.math.cos
import kotlin.math.sin

/** The place that shows how the pet evolves: how far it is, what today paid, and the button once it is ready. */
@Composable
fun PetEvolveScreen(onHome: () -> Unit = {}, onBack: () -> Unit) {
    val s = Store.state.collectAsState().value
    val pet = Pets.active(s)
    var evolving by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Cara evolve", "Kumpul EXP, lepas tu ko yang mulakan", onBack = onBack)
        if (pet == null) {
            Callout("Belum ada spirit animal", "Pilih satu dulu, lepas tu baru boleh kumpul EXP.", SadoColors.Pink)
            return@Column
        }
        val kind = Pets.kind(pet.kind)
        val color = Color(kind.color)
        val st = Pets.stage(pet)
        val next = Pets.toNext(pet)
        val ready = Pets.canEvolve(pet)
        val name = Pets.nameOf(pet)

        Card {
            Row(Modifier.fillMaxWidth().height(130.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Image(painterResource(kind.images[st]), Pets.stageNames[st], Modifier.weight(1f).height(if (next == null) 130.dp else 90.dp), contentScale = ContentScale.Fit)
                if (next != null) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SadoColors.TextMuted, modifier = Modifier.padding(horizontal = 4.dp).size(28.dp))
                    Image(
                        painterResource(kind.images[st + 1]), "Bayang ${next.second}", Modifier.weight(1f).height(130.dp), contentScale = ContentScale.Fit,
                        // the next stage stays a shadow until it is reached
                        colorFilter = ColorFilter.tint(if (ready) color else SadoColors.Text.copy(alpha = 0.28f)),
                    )
                }
            }
            Text(
                when {
                    next == null -> "$name dah sampai peringkat paling tinggi"
                    ready -> "$name dah sedia jadi ${next.second}!"
                    else -> "${next.first} EXP lagi ke ${next.second}"
                },
                color = if (ready) color else SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
            )
            if (next != null) {
                Bar(Pets.progress(pet), color, height = 10)
                Text("${pet.growth} / ${Pets.stageAt[st + 1]} EXP", color = SadoColors.TextMuted, fontSize = 13.sp)
            } else Text("${pet.growth} EXP", color = SadoColors.TextMuted, fontSize = 13.sp)
            if (ready) PrimaryButton("Evolve sekarang", Modifier.fillMaxWidth()) { evolving = st }
        }

        val today = Pets.todayDone(s)
        val queue = Pets.foodQueue(s)
        val waiting = queue.count { it.source == PetFood.Source.Food }
        fun drink(source: PetFood.Source) = if (queue.any { it.source == source }) " Minuman tenaga tengah tunggu kat Home." else ""
        Label("Hari ni")
        Card {
            ExpRow(
                SadoIcons.Bolt, "Bagi makan", "+${Pets.FOOD_EXP} EXP setiap suap, max ${Pets.FOOD_MAX} kali sehari." + if (waiting > 0) " $waiting tengah tunggu kat Home." else " Log makan, lepas tu suap kat Home.",
                today.fed, Pets.FOOD_MAX, color,
            )
            ExpRow(SadoIcons.Timer, "Log tidur", "+${Pets.SLEEP_EXP} EXP, sekali sehari." + drink(PetFood.Source.Sleep), if (today.slept) 1 else 0, 1, color)
            // the only other place to answer is before a workout, which a rest day never reaches
            SleepPicker()
            ExpRow(SadoIcons.Dumbbell, "Habiskan workout", "+${Pets.WORKOUT_EXP} EXP, sekali sehari. Sukan pun kira." + drink(PetFood.Source.Workout), if (today.trained) 1 else 0, 1, color)
            if (today.fed >= Pets.FOOD_MAX && today.slept && today.trained) Text(if (queue.isEmpty()) "Semua dah siap hari ni. Esok sambung!" else "Semua dah siap hari ni. Tinggal bagi kat $name!", color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            else Text("Lebih dari had tak tambah EXP. Padam log, EXP dia pun ditolak balik.", color = SadoColors.TextMuted, fontSize = 12.sp)
            // a tick means logged, the EXP only lands once it is given: without this a tick reads as already paid
            if (queue.isNotEmpty()) PrimaryButton("Bagi kat $name sekarang", Modifier.fillMaxWidth(), onClick = onHome)
        }

        Label("Peringkat")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pets.stageNames.forEachIndexed { i, stageName ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (i == st) color.copy(alpha = 0.12f) else SadoColors.Surface)
                        .border(if (i == st) 2.dp else 1.dp, if (i == st) color else SadoColors.Outline, RoundedCornerShape(16.dp)).padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Image(
                        painterResource(kind.images[i]), stageName, Modifier.height(52.dp), contentScale = ContentScale.Fit,
                        colorFilter = if (i <= st) null else ColorFilter.tint(SadoColors.Text.copy(alpha = 0.28f)),
                    )
                    Text(stageName, color = SadoColors.Text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text("${Pets.stageAt[i]} EXP", color = SadoColors.TextMuted, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        Box(Modifier.height(8.dp))
    }

    evolving?.let { from ->
        pet?.let {
            EvolveOverlay(it.kind, Pets.nameOf(it), from, onEvolved = { Store.update(Pets::evolve) }, onClose = { evolving = null })
        }
    }
}

@Composable
private fun ExpRow(icon: ImageVector, title: String, sub: String, got: Int, max: Int, color: Color) {
    val full = got >= max
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        if (full) Box(Modifier.size(28.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, "Dah cukup", tint = Color.Black, modifier = Modifier.size(18.dp))
        } else Text("$got/$max", color = SadoColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** The evolution, full screen. It can't be dismissed until it is done: the new stage is saved at the flash. */
@Composable
fun EvolveOverlay(kind: String, name: String, from: Int, onEvolved: () -> Unit, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
    ) { EvolveScene(kind, name, from, onEvolved, onClose) }
}

private val SCENE_BG = Color(0xFF0B0B14)

/** A moment of the evolution held still, for a screenshot. */
enum class EvolveHold { Start, ShadowOld, ShadowNew, Done }

/**
 * Old pet, then it fills with white. The silhouette flips between the old body and the bigger new one, faster and
 * faster while it grows, until a white flash. The new pet stands there in full colour.
 */
@Composable
fun EvolveScene(kind: String, name: String, from: Int, onEvolved: () -> Unit, onClose: () -> Unit, hold: EvolveHold? = null) {
    val k = Pets.kind(kind)
    val to = from + 1
    val color = Color(k.color)
    val still = LocalReduceMotion.current
    val oldPic = painterResource(k.images[from])
    val newPic = painterResource(k.images[to])

    val white = remember { Animatable(if (hold == EvolveHold.ShadowOld || hold == EvolveHold.ShadowNew) 1f else 0f) } // how far the old colours have gone
    val grow = remember { Animatable(if (hold == EvolveHold.ShadowNew) 1f else 0f) }
    val flash = remember { Animatable(0f) }
    val rays = remember { Animatable(if (hold == EvolveHold.ShadowOld || hold == EvolveHold.ShadowNew || hold == EvolveHold.Done) 1f else 0f) }
    val reveal = remember { Animatable(1f) }
    var showNew by remember { mutableStateOf(hold == EvolveHold.ShadowNew) }
    var revealed by remember { mutableStateOf(hold == EvolveHold.Done) }
    var line by remember { mutableStateOf(if (hold == EvolveHold.Done) "$name dah jadi ${Pets.stageNames[to]}!" else if (hold == EvolveHold.Start) "Eh? $name macam lain macam…" else "$name tengah berubah!") }

    LaunchedEffect(Unit) {
        if (hold != null) return@LaunchedEffect
        line = "Eh? $name macam lain macam…"
        if (still) {
            delay(800)
            onEvolved()
            revealed = true
            Feedback.levelUp()
            line = "$name dah jadi ${Pets.stageNames[to]}!"
            return@LaunchedEffect
        }
        delay(1400)
        line = "$name tengah berubah!"
        Feedback.tap()
        coroutineScope {
            launch { rays.animateTo(1f, tween(1200)) }
            white.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        // about 3.7 s: slow flips first, a blur of them at the end
        coroutineScope {
            launch { grow.animateTo(1f, tween(3700, easing = LinearEasing)) }
            var beat = 520f
            while (beat > 55f) {
                showNew = !showNew
                Feedback.tap()
                delay(beat.toLong())
                beat *= 0.86f
            }
        }
        flash.animateTo(1f, tween(200))
        onEvolved() // saved here, so leaving the app from now on loses nothing
        revealed = true
        showNew = true
        Feedback.levelUp()
        reveal.snapTo(0.82f)
        coroutineScope {
            launch { flash.animateTo(0f, tween(700)) }
            launch { reveal.animateTo(1f, spring(dampingRatio = 0.45f)) }
        }
        Feedback.pet(kind, to, false)
        line = "$name dah jadi ${Pets.stageNames[to]}!"
    }

    Box(Modifier.fillMaxSize().background(SCENE_BG), contentAlignment = Alignment.Center) {
        EvolveRays(color, rays.value, still || hold != null, Modifier.fillMaxSize())
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Box(Modifier.size(320.dp, 300.dp), contentAlignment = Alignment.BottomCenter) {
                if (revealed) {
                    EvolvePicture(newPic, 1f, reveal.value, null)
                } else {
                    val pic = if (showNew) newPic else oldPic
                    // the new body starts a bit under its full size and the old one swells: the shape really grows
                    val size = if (showNew) 1f else evolveRatio(kind, from, oldPic, newPic)
                    val swell = if (showNew) 0.8f + 0.2f * grow.value else 1f + 0.25f * grow.value
                    if (white.value < 1f) EvolvePicture(pic, size, swell, null)
                    if (white.value > 0f) EvolvePicture(pic, size, swell, Color.White, white.value)
                }
            }
            Text(line, color = Color.White, fontSize = 22.sp, lineHeight = 28.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) {
                if (revealed) PrimaryButton("Mantap!", Modifier.fillMaxWidth(), onClick = onClose)
            }
        }
        if (revealed && !still && hold == null) Confetti(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))
    }
}

/** How big the old body is against the new one on this screen, by their real sizes, so the change reads as growing. */
private fun evolveRatio(kind: String, from: Int, old: Painter, new: Painter): Float {
    fun aspect(p: Painter) = if (p.intrinsicSize.height > 0f) p.intrinsicSize.width / p.intrinsicSize.height else 1f
    val (ow, oh) = Pets.sizeDp(kind, from, aspect(old))
    val (nw, nh) = Pets.sizeDp(kind, from + 1, aspect(new))
    return maxOf(ow / nw, oh / nh).coerceIn(0.45f, 0.8f)
}

@Composable
private fun EvolvePicture(pic: Painter, size: Float, scale: Float, tint: Color?, alpha: Float = 1f) {
    val box = pic.intrinsicSize
    val fit = if (box.width > 0f && box.height > 0f) minOf(320f / box.width, 300f / box.height) else 1f
    val w: Dp = (box.width * fit * size).dp
    val h: Dp = (box.height * fit * size).dp
    Image(
        pic, null, Modifier.size(w, h).graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 1f)
            scaleX = scale; scaleY = scale; this.alpha = alpha
        },
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}

/** Slow turning beams of the element colour behind the pet. */
@Composable
private fun EvolveRays(color: Color, strength: Float, still: Boolean, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "rays")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "spin")
    Canvas(modifier) {
        if (strength <= 0f) return@Canvas
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.maxDimension
        val beam = Path().apply {
            moveTo(c.x, c.y)
            lineTo(c.x + r * cos(-0.13f), c.y + r * sin(-0.13f))
            lineTo(c.x + r * cos(0.13f), c.y + r * sin(0.13f))
            close()
        }
        rotate(if (still) 0f else spin, c) {
            repeat(10) { i -> rotate(i * 36f, c) { drawPath(beam, color.copy(alpha = 0.16f * strength)) } }
        }
    }
}
