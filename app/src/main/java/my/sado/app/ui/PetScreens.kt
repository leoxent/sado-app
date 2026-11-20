package my.sado.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.composed
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import my.sado.app.data.AppState
import my.sado.app.data.Pet
import my.sado.app.data.Pets
import my.sado.app.data.Store
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dev mode "Uji pet": any animal, stage and mood shown on Home without touching the saved pet.
 * Lives in memory only, so closing the app clears it.
 */
object PetDev {
    data class Override(val kind: String, val stage: Int, val mood: Pets.Mood)
    var override by mutableStateOf<Override?>(null)
    var feedTick by mutableIntStateOf(0)
    /** Holds the mouth for a screenshot: 1 open, 2 full howl. */
    var holdCall = 0
    /** Holds the wings for a screenshot: 1 raised, 2 pushed down. */
    var holdFlap = 0
    /** Dev mode "Terbang": the eagle flies, or flaps if it is Legenda, as soon as it is free. */
    var flyTick by mutableIntStateOf(0)
    /** Dev mode "Uji evolve": plays the evolution of the chosen animal without saving anything. */
    var previewEvolve by mutableStateOf(false)
    /** Dev mode "Hantar makanan": food waiting on Home that plays the whole feeding but pays nothing. */
    var food by mutableStateOf(emptyList<my.sado.app.data.PetFood>())
    /** Dev mode "Buka semua haiwan": no animal is locked until the app closes. */
    var openAll by mutableStateOf(false)

    /** The pet to draw and its mood: the dev override first, else the real one on Home. */
    fun shown(s: AppState): Pair<Pet, Pets.Mood>? {
        override?.let { return Pet(it.kind, growth = Pets.stageAt[it.stage]) to it.mood }
        return Pets.active(s)?.let { it to Pets.mood(s) }
    }
}

/**
 * The pet on Home, at the character's feet. It is a "puppet": one still drawing per stage, brought to life
 * in code: breathing, little hops, walking across and sometimes behind the character's legs, turning round,
 * eating the food you feed it, sleeping when nothing is logged, and its element showing as small particles.
 * Sizes follow real life (Pets.sizeDp). Legenda is bigger than the character and stands guard behind him:
 * it does not walk; it breathes, turns, eats and gives a heavy little hop.
 * [fit] caps the height (the pet screen shows every stage at a readable size).
 * [feed] starts the eating animation, the food flying in from [foodFrom] (dp from the centre, dp above the floor);
 * [onFed] says when it is over.
 */
@Composable
fun PetBuddy(s: AppState, modifier: Modifier = Modifier, area: Float = 160f, startX: Float? = null, fit: Dp? = null, show: Pair<Pet, Pets.Mood>? = null, startBack: Boolean = false, feed: PetFeed? = null, foodFrom: Offset = Offset(area, 20f), onFed: () -> Unit = {}) {
    val (pet, mood) = show ?: PetDev.shown(s) ?: return
    val kind = Pets.kind(pet.kind)
    val stage = Pets.stage(pet)
    val legend = stage == 3
    val sleeping = mood == Pets.Mood.Tidur
    val still = LocalReduceMotion.current
    val picture = if (sleeping) kind.sleepImages[stage] else kind.images[stage]
    val painter = painterResource(picture)
    fun aspectOf(size: androidx.compose.ui.geometry.Size) = if (size.height > 0f) size.width / size.height else 1f
    val (standW, standH) = Pets.sizeDp(pet.kind, stage, aspectOf(painterResource(kind.images[stage]).intrinsicSize))
    val (rw, rh) = if (sleeping) Pets.sleepSizeDp(pet.kind, stage, standW, standH, aspectOf(painter.intrinsicSize)) else standW to standH
    // on the pet screen everything is scaled to fit the card, small ones up to 1.8x; asleep it keeps its awake scale
    val k = fit?.let { minOf(it.value / standH, 1.8f) } ?: 1f
    val w = (rw * k).dp
    val h = (rh * k).dp
    val color = Color(kind.color)
    // Legenda is the guardian: further back than the character (its feet sit higher up the floor), walking slowly
    // from one side to the other behind him, and only ever stopping where its whole face shows
    // a pet too wide to fit beside the character (seladang, buaya when grown) does the same, or it would cover him
    val guard = fit == null && (legend || w.value > 130f)
    // where it may stand, in dp from the centre: small pets wander anywhere, even behind the legs; a big one
    // keeps to the sides so it never covers the character (and, with no room left, waits behind him)
    val big = w.value > 70f
    val hi = (area - w.value / 2).coerceAtLeast(0f)
    val lo = if (big) minOf(w.value / 2 + 40f, hi) else 0f
    fun spot(side: Float) = side * (lo + Random.nextFloat() * (hi - lo))
    // the guardian faces outwards on its side, so its head (the front ~third of the drawing, which faces right)
    // must clear the character (~55 dp each side of the centre); its front edge stays inside the hero
    val guardLo = 60f - 0.18f * w.value
    val guardHi = maxOf(area - w.value / 2, guardLo)
    fun guardSpot(side: Float) = side * (guardLo + Random.nextFloat() * (guardHi - guardLo))

    val x = remember(guard, big) { Animatable(if (guard) (if (PetDev.holdFlap > 0) 0f else (guardLo + guardHi) / 2) else startX ?: -(lo + hi) / 2) } // dp from the centre
    val hop = remember { Animatable(0f) } // dp up
    val squash = remember { Animatable(1f) }
    var right by remember { mutableStateOf(true) }
    // how far back up the floor it is, in dp: behind the character its feet are above his, in front they level out
    val depth = remember(guard) { Animatable(if (startBack) GUARD_BACK else 0f) }
    var walking by remember { mutableStateOf(false) }
    var food by remember { mutableStateOf<String?>(null) }
    var eating by remember { mutableStateOf(false) }
    val foodFly = remember { Animatable(0f) } // 0 where it starts, 1 at the mouth
    val foodLeft = remember { Animatable(1f) } // shrinks with each bite
    var gain by remember { mutableStateOf<Int?>(null) } // the "+1 EXP" floating up
    val gainRise = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // 0 mouth shut, 1 open, 2 the full howl. A new tap cuts the sound off and starts again, and so does the mouth.
    var call by remember { mutableStateOf(PetDev.holdCall) }
    var callJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val howlPicture = Pets.howlImage(pet.kind, stage)
    // the eagle's wings: 0 folded (the standing picture), 1 raised, 2 pushed down
    val flapPictures = Pets.flapImages(pet.kind, stage)
    var flap by remember { mutableStateOf(PetDev.holdFlap) }
    fun callOut() {
        callJob?.cancel()
        callJob = scope.launch {
            // it shuts just before the sound ends, which fades out anyway
            val time = (Feedback.petCallMillis(pet.kind, stage) * 0.9f).toLong()
            call = 1
            if (howlPicture != null && !still) {
                delay(HOWL_RISE)
                call = 2
                delay(time - 2 * HOWL_RISE)
                call = 1
                delay(HOWL_RISE)
            } else delay(time)
            call = 0
        }
    }

    val t = rememberInfiniteTransition(label = "pet")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (sleeping || legend) 2600 else 1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")

    suspend fun jump(height: Float) {
        squash.animateTo(0.86f, tween(90))
        squash.animateTo(1.06f, tween(80))
        hop.animateTo(height, tween(200, easing = FastOutSlowInEasing))
        hop.animateTo(0f, tween(200, easing = FastOutLinearInEasing))
        squash.animateTo(0.9f, tween(60))
        squash.animateTo(1f, spring(dampingRatio = 0.4f))
    }

    suspend fun beatWings(beat: Long) {
        while (true) { flap = 1; delay(beat); flap = 2; delay(beat) }
    }

    // a young eagle takes off, crosses over in an arc with its wings beating, and lands
    suspend fun fly(to: Float) {
        val time = (abs(to - x.value) * 14).roundToInt().coerceIn(1400, 2600)
        val height = if (fit != null) 35f else 90f
        right = to > x.value
        squash.animateTo(0.86f, tween(90))
        coroutineScope {
            val wings = launch { beatWings(FLY_BEAT) }
            launch { x.animateTo(to, tween(time, easing = FastOutSlowInEasing)) }
            squash.animateTo(1f, tween(80))
            hop.animateTo(height, tween((time * 0.35f).roundToInt(), easing = FastOutSlowInEasing))
            hop.animateTo(height - 12f, tween((time * 0.3f).roundToInt()))
            hop.animateTo(0f, tween((time * 0.35f).roundToInt(), easing = FastOutSlowInEasing))
            wings.cancel()
        }
        flap = 0
        squash.animateTo(0.9f, tween(60))
        squash.animateTo(1f, spring(dampingRatio = 0.4f))
    }

    // Legenda is too big to fly, so it stays on the ground and flaps
    suspend fun flapOnTheSpot() {
        coroutineScope {
            val wings = launch { beatWings(FLAP_BEAT) }
            delay(FLAP_BEAT * 10)
            wings.cancel()
        }
        flap = 0
    }

    // the guardian's wings are wider than the screen's side, so it walks behind the character first: its head
    // clears his, and a wing spreads out on each side of him. Then it goes back to its side, facing outwards.
    suspend fun guardFlap(side: Float) {
        right = side < 0
        walking = true
        x.animateTo(0f, tween((abs(x.value) * 30).roundToInt().coerceIn(900, 2000), easing = FastOutSlowInEasing))
        walking = false
        flapOnTheSpot()
        val to = guardSpot(side)
        right = side > 0
        walking = true
        x.animateTo(to, tween((abs(to) * 30).roundToInt().coerceIn(900, 2000), easing = FastOutSlowInEasing))
        walking = false
    }

    // wander: a hop, a turn, or a walk to a new spot, sometimes passing behind the legs
    LaunchedEffect(sleeping, still, guard, eating) {
        // a flight cut short by falling asleep must not leave it in the air
        if (PetDev.holdFlap == 0) flap = 0
        hop.snapTo(0f)
        walking = false
        if (still || sleeping || eating) return@LaunchedEffect
        var flySeen = PetDev.flyTick
        // true if dev mode asked for a flight during the pause
        suspend fun pause(ms: Long): Boolean {
            withTimeoutOrNull(ms) { snapshotFlow { PetDev.flyTick }.first { it != flySeen } }
            return (PetDev.flyTick != flySeen).also { flySeen = PetDev.flyTick }
        }
        suspend fun flyAcross() {
            val side = if (x.value < 0) 1f else -1f
            val range = hi.coerceAtLeast(1f)
            fly(side * (range * 0.4f + Random.nextFloat() * range * 0.6f))
        }
        while (true) {
            if (guard) {
                // the guardian: long, uneven pauses, then a slow walk across behind the character; it turns only
                // by walking, so it always ends facing outwards with its face in view
                val asked = pause(Random.nextLong(5000, 16000))
                val side = if (x.value < 0) -1f else 1f
                if (flapPictures != null && (asked || Random.nextInt(3) == 0)) { guardFlap(side); continue }
                if (Random.nextInt(3) == 0) { jump(4f); continue }
                val to = guardSpot(-side)
                right = to > x.value
                walking = true
                x.animateTo(to, tween((abs(to - x.value) * 30).roundToInt().coerceIn(1800, 5000), easing = FastOutSlowInEasing))
                walking = false
                continue
            }
            val asked = pause(Random.nextLong(3000, 7500))
            if (flapPictures != null && (asked || Random.nextInt(3) == 0)) {
                if (legend) flapOnTheSpot() else flyAcross()
                continue
            }
            when (Random.nextInt(5)) {
                0 -> jump(18f)
                1 -> { right = !right; jump(6f) }
                else -> {
                    // a big pet mostly stays on its side, sometimes crossing over behind the character
                    val side = if (x.value < 0) -1f else 1f
                    val to = if (big) spot(if (Random.nextInt(4) == 0) -side else side) else Random.nextInt(-hi.toInt().coerceAtLeast(1), hi.toInt().coerceAtLeast(1)).toFloat()
                    if (abs(to - x.value) < 25f) continue
                    right = to > x.value
                    val time = (abs(to - x.value) * 14).roundToInt().coerceIn(500, 2600)
                    // it walks back up the floor on its way behind the character and down again on its way out,
                    // so it never pops from one level to the other. The pet screen has no character to go behind.
                    val crossing = big && to * x.value < 0
                    val staysBehind = !big && abs(to) < 45f && Random.nextBoolean()
                    walking = true
                    coroutineScope {
                        launch { x.animateTo(to, tween(time, easing = LinearEasing)) }
                        launch {
                            if (fit != null) return@launch
                            val ramp = (time * 0.4f).roundToInt()
                            val rises = staysBehind || crossing
                            if (rises) depth.animateTo(GUARD_BACK, tween(ramp))
                            if (!staysBehind) {
                                delay((time - ramp - if (rises) ramp else 0).toLong())
                                depth.animateTo(0f, tween(ramp))
                            }
                        }
                    }
                    walking = false
                }
            }
        }
    }

    // the food flies to its mouth, it chews with no new pictures (mouth open and shut, body bobbing, the food
    // getting smaller), swallows with a little hop, and the EXP floats up
    suspend fun eat(icon: String, exp: Int) {
        if (!still) {
            eating = true
            callJob?.cancel()
            val facing = right
            right = foodFrom.x > x.value
            foodFly.snapTo(0f)
            foodLeft.snapTo(1f)
            food = icon
            foodFly.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
            repeat(4) { bite ->
                call = 1
                squash.animateTo(0.9f, tween(120))
                call = 0
                foodLeft.animateTo(1f - (bite + 1) / 4f, tween(70))
                squash.animateTo(1.05f, tween(110))
                Feedback.tap()
            }
            food = null
            squash.animateTo(1f, spring(dampingRatio = 0.4f))
            right = facing
            eating = false
        }
        gainRise.snapTo(0f)
        gain = exp
        Feedback.success()
        gainRise.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        gain = null
    }

    LaunchedEffect(feed) {
        val f = feed ?: return@LaunchedEffect
        eat(f.icon, f.exp)
        onFed()
    }
    // dev mode "Bagi makan"
    val devFeed = PetDev.feedTick
    LaunchedEffect(devFeed) {
        if (devFeed == 0) return@LaunchedEffect
        eat("nasi_ayam", Pets.FOOD_EXP)
    }

    val bob = if (walking) abs(sin(x.value / (if (guard) 10f else 7f))) * (if (guard) 2f else 4f) else 0f
    // the guardian stands further back, so its feet are higher up than the character's
    val back = if (guard) GUARD_BACK else depth.value
    // the shadow is its own layer under everything, so it never darkens the character's legs; it stays on the
    // floor when the pet hops, and shrinks a little
    Box(
        modifier
            .zIndex(-3f)
            .offset { IntOffset((x.value * density).roundToInt(), (-back * density).roundToInt()) }
            .size(width = w + 40.dp, height = h + if (legend) 30.dp else 70.dp)
            .graphicsLayer {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                val s = 1f - (hop.value / 60f).coerceIn(0f, 0.4f)
                scaleX = s; scaleY = s
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (pet.kind == "buaya" || sleeping) CastShadow(painter, w, h, flip = !right, flat = 0.2f, lean = 0.3f, lift = 0.12f)
        else CastShadow(painter, w, h, flip = !right)
    }
    Box(
        modifier
            .zIndex(if (guard || depth.value > GUARD_BACK / 2) -1f else 1f)
            .offset { IntOffset((x.value * density).roundToInt(), (-(hop.value + bob + back) * density).roundToInt()) }
            .size(width = w + 40.dp, height = h + if (legend) 30.dp else 70.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Legenda: the app's own aura in the element colour
        if (legend) Box(
            Modifier.align(Alignment.BottomCenter).size(w + 40.dp, h + 30.dp).graphicsLayer { alpha = 0.55f + 0.25f * breath }
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent))),
        )
        // the closed eyes and the open mouths must breathe, squash and turn with the drawing, so all share this
        val moved = Modifier
            .graphicsLayer {
                // breathing and squash pivot on the feet and scale both ways alike, so the drawing never
                // gets squashed out of shape
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                val b = if (still) 0f else breath
                val u = (1f + (squash.value - 1f) * 0.5f) * (1f + 0.015f * b)
                scaleY = u
                scaleX = (if (right) 1f else -1f) * u
            }
        val drawn = Modifier.size(w, h).then(moved)
        val flapping = flap > 0 && !sleeping
        val talking = (call > 0 || flapping) && !sleeping
        Image(
            painter, contentDescription = "${Pets.nameOf(pet)}, ${kind.animal} ${Pets.stageNames[stage]}",
            modifier = drawn.graphicsLayer { alpha = if (talking) 0f else 1f }.pressableNoSound {
                if (eating) return@pressableNoSound // its mouth is busy
                if (!sleeping) callOut()
                // a howl stands its ground
                // it answers in the air with its voice alone. A jump over a running hop would cancel that hop's
                // coroutine, and the wander loop with it, so the pet would never move again.
                scope.launch {
                    Feedback.pet(pet.kind, stage, sleeping)
                    if (flap == 0 && !hop.isRunning && !squash.isRunning) jump(if (sleeping || legend || howlPicture != null) 6f else 24f)
                }
            },
        )
        BlinkLids(picture, drawn, hold = if (talking) false else null) // sleeping pictures have none: their eyes are already shut
        if (!sleeping) {
            PetCallFrame(kind.openImages[stage], painter, w, moved, call == 1 && !flapping)
            if (howlPicture != null) PetCallFrame(howlPicture, painter, w, moved, call == 2 && !flapping)
            flapPictures?.let { (up, down) ->
                PetCallFrame(up, painter, w, moved, flap == 1)
                PetCallFrame(down, painter, w, moved, flap == 2)
            }
        }
        if (!still && !sleeping) PetParticles(kind.element, stage, right, w, h, color, Modifier.align(Alignment.BottomCenter))
        when {
            sleeping -> Box(Modifier.align(if (right) Alignment.TopEnd else Alignment.TopStart).graphicsLayer { scaleX = 0.55f; scaleY = 0.55f }) { Zzz() }
            mood == Pets.Mood.Kenyang && !still -> Hearts(Modifier.align(Alignment.TopCenter))
            mood == Pets.Mood.Lapar -> Text(
                "lapar…", color = SadoColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.TopCenter).clip(RoundedCornerShape(10.dp)).background(SadoColors.Surface2).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
    // over everything, so a guardian's food and EXP aren't hidden behind the character
    food?.let { icon ->
        Box(
            modifier.zIndex(5f).offset {
                // it follows the mouth, about a third of the way along the drawing from its middle and 60% up
                val p = foodFly.value
                val mouthX = x.value + (if (right) 0.3f else -0.3f) * w.value
                val mouthY = hop.value + back + 0.6f * h.value - 20f
                val px = foodFrom.x + (mouthX - foodFrom.x) * p
                val py = foodFrom.y + (mouthY - foodFrom.y) * p + sin(p * Math.PI.toFloat()) * 40f
                IntOffset((px * density).roundToInt(), (-py * density).roundToInt())
            }.graphicsLayer {
                val left = foodLeft.value
                scaleX = left; scaleY = left
                alpha = if (left < 0.05f) 0f else 1f
            },
        ) { FoodIcon(icon, 40.dp) }
    }
    gain?.let { exp ->
        Text(
            "+$exp EXP", color = SadoColors.Accent, fontSize = 18.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold,
            modifier = modifier.zIndex(5f).offset {
                val py = back + minOf(h.value, 190f) + 10f + gainRise.value * 36f
                IntOffset((x.value * density).roundToInt(), (-py * density).roundToInt())
            }.graphicsLayer { alpha = (1f - gainRise.value) * 2f }
                .clip(RoundedCornerShape(12.dp)).background(SadoColors.Surface.copy(alpha = 0.92f)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private val PET_BADGE_RED = Color(0xFFD32F2F) // the same in both themes, white text stays readable on it

/** One meal on its way to the pet. A new one every time, so feeding the same food twice still plays. */
class PetFeed(val icon: String, val exp: Int)

/**
 * The next thing in the queue on Home, waiting to be tapped. A red dot says more EXP is waiting;
 * with more than one in the queue, it says how many.
 */
@Composable
fun PetFoodButton(item: my.sado.app.data.PetFood, count: Int, petName: String, modifier: Modifier = Modifier, onFeed: () -> Unit) {
    val icon = item.icon
    val isFood = item.source == my.sado.app.data.PetFood.Source.Food
    val verb = if (isFood) "Suap" else "Minum"
    val still = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "food")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    Column(
        modifier.graphicsLayer { translationY = if (still) 0f else -6f * density * bob }
            .semantics(mergeDescendants = true) {
                val what = my.sado.app.data.FoodIcons.label(icon)
                contentDescription = (if (isFood) "Suap $what kat $petName" else "Bagi $what kat $petName, +${item.exp} EXP") + if (count > 1) ", $count menunggu" else ""
            }
            .pressable(onClick = onFeed),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp)) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(SadoColors.Surface.copy(alpha = 0.92f)).border(2.dp, SadoColors.Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) { FoodIcon(icon, 42.dp) }
            Box(
                Modifier.align(Alignment.TopEnd).size(if (count > 1) 22.dp else 14.dp).clip(CircleShape).background(PET_BADGE_RED).border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (count > 1) Text("$count", color = Color.White, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold) }
        }
        Text(
            verb, color = SadoColors.OnAccent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.offset(y = (-8).dp).clip(RoundedCornerShape(8.dp)).background(SadoColors.Accent).padding(horizontal = 8.dp, vertical = 1.dp),
        )
    }
}

/** How long the head takes to go from the open mouth to the full howl, and back. */
private const val HOWL_RISE = 150L

/** How long each wing position lasts: a young eagle beats fast, Legenda slow and heavy. */
private const val FLY_BEAT = 100L
private const val FLAP_BEAT = 170L

/**
 * An open-mouth or wing picture (tools/mouth-frame.ps1, tools/fly-frame.ps1). It has room around the drawing for a head that moves, equal left and
 * right, with the feet on the bottom edge, so it keeps the standing picture's scale and may stick out of its box.
 * It is always there, see-through until needed, so nothing has to load at the moment of the tap.
 */
@Composable
private fun BoxScope.PetCallFrame(frame: Int, standing: androidx.compose.ui.graphics.painter.Painter, width: Dp, moved: Modifier, shown: Boolean) {
    val painter = painterResource(frame)
    val scale = if (standing.intrinsicSize.width > 0f) width.value / standing.intrinsicSize.width else 1f
    Image(
        painter, null,
        Modifier.align(Alignment.BottomCenter).wrapContentSize(Alignment.BottomCenter, unbounded = true)
            .size((painter.intrinsicSize.width * scale).dp, (painter.intrinsicSize.height * scale).dp)
            .then(moved).graphicsLayer { alpha = if (shown) 1f else 0f },
    )
}

/** How far back (dp up the floor) a pet behind the character stands, so its feet are above his. */
const val GUARD_BACK = 26f

/**
 * The drawing's own silhouette laid on the floor behind it, from one light: squashed flat and leaning back to
 * the left from the feet, whichever way the drawing faces. [width] x [height] is the drawing's own size, with
 * its feet on the bottom edge. No blur: Modifier.blur clips to its bounds and only works on Android 12+.
 * A low animal with a high belly (the crocodile) needs a flatter, higher shadow that fills the floor under the
 * belly, or the body has nothing under it and looks like it floats.
 */
@Composable
fun CastShadow(
    painter: androidx.compose.ui.graphics.painter.Painter, width: Dp, height: Dp, modifier: Modifier = Modifier, flip: Boolean = false,
    flat: Float = 0.28f, // height of the shadow against the drawing
    lean: Float = 0.5f, // how far its top lands to the left, against the drawing's height
    lift: Float = 0.035f, // starts this far up inside the feet, which hide its base, so nothing shows under them
) {
    val alpha = if (SadoColors.dark) 0.6f else 0.3f
    val tint = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black)
    androidx.compose.foundation.Canvas(modifier.size(width, height)) {
        val w = size.width
        val h = size.height
        val m = androidx.compose.ui.graphics.Matrix()
        m[0, 0] = if (flip) -1f else 1f
        m[1, 0] = lean
        m[3, 0] = (if (flip) w else 0f) - lean * h
        m[1, 1] = flat
        m[3, 1] = h * (1f - flat - lift)
        withTransform({ transform(m) }) {
            with(painter) { draw(size, alpha = alpha, colorFilter = tint) }
        }
    }
}

/** A tap target with no ripple square and no press sound: the pet's jump and its own sound are the feedback. */
private fun Modifier.pressableNoSound(onClick: () -> Unit) = composed {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    semantics { contentDescription = "Tekan pet" }
        .clickable(source, indication = null, role = Role.Button, onClick = onClick)
}

@Composable
private fun Hearts(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "hearts")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "rise")
    Icon(
        Icons.Filled.Favorite, null, tint = SadoColors.Pink.copy(alpha = (1f - p) * 0.9f),
        modifier = modifier.size(14.dp).graphicsLayer { translationY = -p * 40f; translationX = sin(p * 8f) * 8f },
    )
}

private class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, val life: Float, var age: Float = 0f, val seed: Float = Random.nextFloat())

/**
 * Element effects, drawn by the app because a still drawing can't move: sparks off the tiger's tail,
 * wind lines from the eagle, honey drops, the gaur's breath, river drops, the wolf's shadow smoke.
 * More often at each stage, busiest at Legenda.
 */
@Composable
private fun PetParticles(element: String, stage: Int, right: Boolean, w: Dp, h: Dp, color: Color, modifier: Modifier) {
    val parts = remember { mutableStateListOf<Particle>() }
    var tick by remember { mutableLongStateOf(0L) }
    val rate = listOf(0.25f, 0.45f, 0.7f, 1.2f)[stage] // per second
    LaunchedEffect(element, stage) {
        var lastNs = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastNs == 0L) 0f else ((now - lastNs) / 1e9f).coerceAtMost(0.05f)
                lastNs = now
                parts.forEach { it.age += dt; it.x += it.vx * dt; it.y += it.vy * dt }
                parts.removeAll { it.age >= it.life }
                if (Random.nextFloat() < rate * dt) parts += spawn(element)
                tick = now
            }
        }
    }
    Canvas(modifier.size(w, h)) {
        tick // redraw every frame
        val sx = size.width
        val sy = size.height
        parts.forEach { p ->
            val f = p.age / p.life
            val px = (if (right) p.x else 1f - p.x) * sx
            val py = p.y * sy
            val a = (1f - f).coerceIn(0f, 1f)
            when (element) {
                "Kilat" -> {
                    val path = Path().apply {
                        moveTo(px, py); lineTo(px + 6f, py + 8f); lineTo(px + 1f, py + 9f); lineTo(px + 7f, py + 18f)
                    }
                    drawPath(path, Color(0xFFFFE14D).copy(alpha = a), style = Stroke(width = 3f, cap = StrokeCap.Round))
                }
                "Angin" -> drawArc(
                    Color(0xFFBFE6FF).copy(alpha = a * 0.9f), 200f, 100f, false,
                    topLeft = Offset(px - 14f, py - 6f), size = androidx.compose.ui.geometry.Size(28f, 12f), style = Stroke(width = 3f, cap = StrokeCap.Round),
                )
                "Madu" -> {
                    drawCircle(Color(0xFFF5B83D).copy(alpha = a), radius = 4.5f, center = Offset(px, py))
                    if (p.seed > 0.5f) drawCircle(Color.White.copy(alpha = a * 0.8f), radius = 1.6f, center = Offset(px - 1.5f, py - 1.5f))
                }
                "Batu" -> drawCircle(Color(0xFFD9D9D9).copy(alpha = a * 0.55f), radius = 4f + 10f * f, center = Offset(px, py))
                "Sungai" -> drawCircle(color.copy(alpha = a), radius = 3.5f, center = Offset(px, py))
                else -> drawCircle(Color(0xFF7A5CC8).copy(alpha = a * 0.5f), radius = 5f + 9f * f, center = Offset(px, py))
            }
        }
    }
}

// positions in the drawing's own box (0..1, facing right); speeds in box sizes per second
private fun spawn(element: String): Particle = when (element) {
    "Kilat" -> Particle(0.08f + Random.nextFloat() * 0.08f, 0.05f + Random.nextFloat() * 0.15f, -0.25f + Random.nextFloat() * 0.2f, -0.3f, 0.5f)
    "Angin" -> Particle(0.25f, 0.35f + Random.nextFloat() * 0.3f, -0.8f, 0f, 0.8f)
    "Madu" -> Particle(0.55f + Random.nextFloat() * 0.15f, 0.45f, 0f, 0.6f, 0.8f)
    "Batu" -> Particle(0.95f, 0.35f, 0.25f, -0.2f, 1.2f)
    "Sungai" -> Particle(0.2f + Random.nextFloat() * 0.3f, 0.55f, 0f, 0.7f, 0.7f)
    else -> Particle(0.06f + Random.nextFloat() * 0.06f, 0.55f, -0.08f, -0.35f, 1.4f)
}

/** The pet's row on Home: who, how big, how it feels, how far to the next stage. */
@Composable
fun PetCard(s: AppState, modifier: Modifier = Modifier, onEvolve: (() -> Unit)? = null, onOpen: () -> Unit) {
    val dev = PetDev.override
    val pet = PetDev.shown(s)?.first
    if (pet == null) {
        if (Pets.unlocked(s)) Callout("Hadiah workout kedua!", "Ko datang balik. Pilih spirit animal, bagi makan, dan tengok dia membesar.", SadoColors.Pink, modifier, onOpen)
        return
    }
    val kind = Pets.kind(pet.kind)
    val st = Pets.stage(pet)
    val next = Pets.toNext(pet)
    val ready = dev == null && Pets.canEvolve(pet)
    Row(
        modifier.fillMaxWidth().pressable(onClick = if (ready && onEvolve != null) onEvolve else onOpen).clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface)
            .border(if (ready) 2.dp else 1.dp, if (ready) Color(kind.color) else SadoColors.Outline, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(kind.color).copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Image(painterResource(kind.images[st]), null, Modifier.size(44.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Pets.nameOf(pet), color = SadoColors.Text, fontSize = 16.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(Pets.stageNames[st], color = Color(kind.color), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                if (dev != null) Tag("DEV", SadoColors.Streak)
            }
            when {
                dev != null -> Text("Mod uji: ${dev.mood.label}. Progress sebenar tak berubah.", color = SadoColors.TextMuted, fontSize = 13.sp, maxLines = 1)
                ready -> Text("Dah sedia evolve! Tekan sini.", color = Color(kind.color), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                else -> Text(Pets.moodLine(s, pet), color = SadoColors.TextMuted, fontSize = 13.sp, maxLines = 1)
            }
            if (next != null) Bar(Pets.progress(pet), Color(kind.color), height = 6)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SadoColors.TextMuted)
    }
}

@Composable
fun PetScreen(onEvolve: () -> Unit = {}, onBack: () -> Unit) {
    val s = Store.state.collectAsState().value
    val active = Pets.active(s)
    var confirm by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf<Pet?>(null) }
    var lockInfo by remember { mutableStateOf<Pair<Pets.Kind, Pets.Lock>?>(null) }
    val lock = if (PetDev.openAll) null else Pets.adoptLock(s)

    lockInfo?.let { (k, l) ->
        AlertDialog(
            onDismissRequest = { lockInfo = null },
            containerColor = SadoColors.Surface,
            title = { Text("${k.animal} masih berkunci", color = SadoColors.Text, fontWeight = FontWeight.ExtraBold) },
            text = { Text("${l.goal}. ${l.now}", color = SadoColors.TextMuted) },
            confirmButton = { TextButton({ lockInfo = null }) { Text("Ok", color = SadoColors.Accent, fontWeight = FontWeight.ExtraBold) } },
        )
    }

    confirm?.let { id ->
        val k = Pets.kind(id)
        val had = s.pets.any { it.kind == id }
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = SadoColors.Surface,
            title = { Text(if (active == null) "Pilih ${k.animal}?" else "Letak ${k.animal} kat Home?", color = SadoColors.Text, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    if (active == null) "${k.blurb} Dia mula sebagai anak, dan dapat EXP bila ko bagi makan, log tidur dan workout. Boleh tukar haiwan bila-bila, progress setiap satu disimpan."
                    else "${Pets.nameOf(active)} akan berehat, progress dia disimpan. Cuma haiwan kat Home yang dapat EXP.",
                    color = SadoColors.TextMuted,
                )
            },
            confirmButton = {
                TextButton({
                    Store.update { Pets.choose(it, id) }
                    Feedback.success()
                    confirm = null
                    if (!had) rename = Store.state.value.pets.firstOrNull { it.kind == id }
                }) { Text("Ya", color = SadoColors.Accent, fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Batal", color = SadoColors.TextMuted) } },
        )
    }
    rename?.let { p ->
        var name by remember(p.kind) { mutableStateOf(p.name) }
        val k = Pets.kind(p.kind)
        AlertDialog(
            onDismissRequest = { rename = null },
            containerColor = SadoColors.Surface,
            title = { Text("Nama dia?", color = SadoColors.Text, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        name, { name = it.take(18) }, singleLine = true, placeholder = { Text(k.defaultName) },
                        modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    )
                    Text("Biar kosong untuk guna ${k.defaultName}.", color = SadoColors.TextMuted, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton({
                    Store.update { st -> st.copy(pets = st.pets.map { if (it.kind == p.kind) it.copy(name = name.trim()) else it }) }
                    rename = null
                }) { Text("Simpan", color = SadoColors.Accent, fontWeight = FontWeight.ExtraBold) }
            },
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Spirit animal", "Bagi makan, tidur, workout: dia membesar", onBack = onBack)

        if (!Pets.unlocked(s)) {
            Callout(
                "Hadiah workout kedua",
                "Ada kawan baru tunggu ko. Lagi ${Pets.workoutsToUnlock(s)} workout (hari lain) untuk buka.",
                SadoColors.Pink,
            )
            return@Column
        }

        if (active != null) {
            val k = Pets.kind(active.kind)
            val st = Pets.stage(active)
            val next = Pets.toNext(active)
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(Pets.nameOf(active), color = SadoColors.Text, fontSize = 24.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                            Icon(
                                Icons.Filled.Edit, contentDescription = "Tukar nama", tint = SadoColors.TextMuted,
                                modifier = Modifier.size(36.dp).clip(CircleShape).pressable { rename = active }.padding(8.dp),
                            )
                        }
                        Text("${k.animal} · ${k.element}", color = SadoColors.TextMuted, fontSize = 13.sp)
                    }
                    Tag(Pets.stageNames[st].uppercase(), Color(k.color), filled = true)
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    PetBuddy(s, area = 120f, startX = 0f, fit = 190.dp)
                }
                Text(Pets.moodLine(s, active), color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (next != null) {
                    Bar(Pets.progress(active), Color(k.color), height = 8)
                    if (Pets.canEvolve(active)) Text("${active.growth} EXP · dah sedia jadi ${next.second}!", color = Color(k.color), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    else Text("${active.growth} EXP · ${next.first} lagi ke ${next.second}", color = SadoColors.TextMuted, fontSize = 13.sp)
                } else Text("${active.growth} EXP · peringkat paling tinggi", color = Color(k.color), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            MenuRow(
                "Cara evolve",
                if (Pets.canEvolve(active)) "Dah sedia! Tekan untuk mula." else "EXP hari ni dan semua peringkat. Dia tak pernah mati, cuma tidur kalau lama tak log.",
                Color(k.color), SadoIcons.Bolt, onEvolve,
            )
        }

        Label(if (active == null) "Pilih satu" else "Semua haiwan")
        Pets.kinds.chunked(2).forEach { pair ->
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { k ->
                    val mine = s.pets.firstOrNull { it.kind == k.id }
                    val on = active?.kind == k.id
                    val st = mine?.let { Pets.stage(it) } ?: 0
                    val locked = if (mine == null) lock else null
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                            .background(if (on) Color(k.color).copy(alpha = 0.12f) else SadoColors.Surface)
                            .border(2.dp, if (on) Color(k.color) else SadoColors.Outline, RoundedCornerShape(20.dp))
                            .pressable(enabled = !on) { if (locked != null) lockInfo = k to locked else confirm = k.id }.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(painterResource(k.images[st]), null, Modifier.height(86.dp), alpha = if (locked != null) 0.35f else 1f)
                            if (locked != null) Box(
                                Modifier.size(36.dp).clip(CircleShape).background(SadoColors.Surface2).border(1.dp, SadoColors.Outline, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Lock, "Berkunci", tint = SadoColors.Text, modifier = Modifier.size(20.dp)) }
                        }
                        Text(mine?.let { Pets.nameOf(it) } ?: k.defaultName, color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 1)
                        Text(k.animal, color = SadoColors.TextMuted, fontSize = 12.sp, maxLines = 1)
                        Text(
                            when { on -> "Kat Home · ${Pets.stageNames[st]}"; mine != null -> "Berehat · ${Pets.stageNames[st]}"; locked != null -> locked.goal; else -> k.element },
                            color = if (on) Color(k.color) else SadoColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Box(Modifier.height(8.dp))
    }
}

/** Dev mode: show any animal, stage and mood on Home without changing the saved pet. */
@Composable
fun PetTester() {
    val o = PetDev.override
    val kind = o?.kind ?: Pets.kinds.first().id
    val stage = o?.stage ?: 0
    val mood = o?.mood ?: Pets.Mood.Biasa
    fun set(k: String = kind, st: Int = stage, m: Pets.Mood = mood) { PetDev.override = PetDev.Override(k, st, m) }
    Box(Modifier.fillMaxWidth().height(1.dp).background(SadoColors.Outline))
    Text("Uji pet", color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    Text(
        if (o == null) "Pilih apa-apa di bawah, lepas tu balik ke Home. Disimpan dalam memori je: progress sebenar tak berubah, hilang bila tutup app."
        else "Aktif: ${Pets.kind(kind).animal} ${Pets.stageNames[stage]}, ${mood.label}. Kad pet kat Home tunjuk tag DEV.",
        color = if (o == null) SadoColors.TextMuted else SadoColors.Streak, fontSize = 13.sp,
    )
    val names = Pets.kinds.map { it.animal.substringBefore(" ") }
    Pets.kinds.chunked(3).forEachIndexed { row, chunk ->
        val i = Pets.kinds.indexOfFirst { it.id == kind } - row * 3
        Segmented(names.drop(row * 3).take(3), if (o != null && i in 0..2) i else -1) { set(k = chunk[it].id) }
    }
    Segmented(Pets.stageNames, if (o != null) stage else -1) { set(st = it) }
    Segmented(Pets.Mood.entries.map { it.label }, if (o != null) mood.ordinal else -1) { set(m = Pets.Mood.entries[it]) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("Bagi makan", Modifier.weight(1f)) { if (PetDev.override == null) set(); PetDev.feedTick++ }
        SecondaryButton("Terbang", Modifier.weight(1f)) { set(k = "helang", m = if (mood == Pets.Mood.Tidur) Pets.Mood.Kenyang else mood); PetDev.flyTick++ }
        SecondaryButton("Reset", Modifier.weight(1f), color = SadoColors.Streak) { PetDev.override = null; PetDev.food = emptyList() }
    }
    SecondaryButton("Uji evolve (${Pets.stageNames[stage.coerceAtMost(2)]} ke ${Pets.stageNames[stage.coerceAtMost(2) + 1]})", Modifier.fillMaxWidth()) { PetDev.previewEvolve = true }
    if (PetDev.previewEvolve) EvolveOverlay(kind, Pets.kind(kind).defaultName, stage.coerceAtMost(2), onEvolved = {}, onClose = { PetDev.previewEvolve = false })
    SecondaryButton(if (PetDev.food.isEmpty()) "Hantar 3 item ke Home" else "${PetDev.food.size} tengah tunggu kat Home", Modifier.fillMaxWidth()) {
        val food = my.sado.app.data.PetFood.Source.Food
        PetDev.food = listOf(
            my.sado.app.data.PetFood(0, "nasi_lemak", Pets.FOOD_EXP, food),
            my.sado.app.data.PetFood(0, "roti_canai", Pets.FOOD_EXP, food),
            my.sado.app.data.PetFood(0, Pets.ENERGY_ICON, Pets.WORKOUT_EXP, my.sado.app.data.PetFood.Source.Workout),
        )
    }
    Text("Uji evolve dan Hantar item ni tak simpan apa-apa.", color = SadoColors.TextMuted, fontSize = 13.sp)

    // the first-week gift, from the top: the real state waits in a file and comes back afterwards
    val state = Store.state.collectAsState().value
    if (Store.simulating()) {
        Text("Simulasi aktif. Data asal selamat, tapi apa-apa yang ko buat masa simulasi hilang bila pulihkan.", color = SadoColors.Streak, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        SecondaryButton("Pulihkan data asal", Modifier.fillMaxWidth(), color = SadoColors.Streak) {
            PetDev.override = null
            PetDev.food = emptyList()
            Store.endSimulation()
        }
    } else {
        SecondaryButton("Simulasi dah 1 minggu aktif", Modifier.fillMaxWidth()) {
            PetDev.override = null
            PetDev.food = emptyList()
            Store.startSimulation { Pets.simulateFirstWeek(it) }
        }
        Text("Buang pet buat sementara supaya hadiah workout kedua keluar kat Home. Boleh pulihkan lepas tu.", color = SadoColors.TextMuted, fontSize = 13.sp)
    }

    SecondaryButton(if (PetDev.openAll) "Kunci semula haiwan" else "Buka semua haiwan", Modifier.fillMaxWidth()) { PetDev.openAll = !PetDev.openAll }
    Text("Sampai tutup app je. Haiwan yang ko pilih masa ni tetap disimpan, kecuali dalam simulasi.", color = SadoColors.TextMuted, fontSize = 13.sp)

    // the real pet, for walking through the real thing without waiting days
    val real = Pets.active(state) ?: return
    Text(
        "Pet sebenar: ${Pets.nameOf(real)}, ${real.growth} EXP, ${Pets.stageNames[Pets.stage(real)]}. Butang bawah ni ubah progress betul.",
        color = SadoColors.Streak, fontSize = 13.sp, fontWeight = FontWeight.Bold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("+10 EXP", Modifier.weight(1f), color = SadoColors.Streak) {
            Store.update { st -> st.copy(pets = st.pets.map { if (it.kind == real.kind) it.copy(growth = it.growth + 10) else it }) }
        }
        SecondaryButton("Reset had hari ni", Modifier.weight(1f), color = SadoColors.Streak) {
            Store.update { it.copy(petDays = it.petDays - java.time.LocalDate.now().toEpochDay()) }
        }
    }
}
