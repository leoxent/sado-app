package my.sado.app.ui

import android.content.Context
import my.sado.app.R
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * How-to pictures per move. Most come from free-exercise-db (public domain), which has a start and an
 * end frame we flip between; a few come from wger (CC-BY-SA). Downloaded once, then cached on the phone.
 */
object MoveImages {
    private const val FEDB = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
    private fun fedb(id: String) = listOf("$FEDB$id/0.jpg", "$FEDB$id/1.jpg")
    private fun wger(path: String) = listOf("https://wger.de/media/exercise-images/$path")

    private val urls: Map<String, List<String>> = mapOf(
        "decline" to fedb("Decline_Push-Up"),
        "push" to fedb("Pushups"),
        "kneepush" to fedb("Pushups"),
        "wide" to fedb("Push-Up_Wide"),
        "floorpress" to fedb("Dumbbell_Floor_Press"),
        "archer" to fedb("Pushups_Close_and_Wide_Hand_Positions"),
        "onearm" to fedb("Single-Arm_Push-Up"),
        "lateral" to fedb("Side_Lateral_Raise"),
        "bandlat" to fedb("Lateral_Raise_-_With_Bands"),
        "bottlelat" to fedb("Side_Lateral_Raise"),
        "ytw" to fedb("Lying_Rear_Delt_Raise"),
        "pike" to wger("454/447f3c17-405f-46e0-b138-65c2a8caaab0.png"),
        "pullapart" to fedb("Band_Pull_Apart"),
        "pullup" to fedb("Pullups"),
        "negative" to fedb("Pullups"),
        "invrow" to fedb("Inverted_Row"),
        "dbrow" to fedb("One-Arm_Dumbbell_Row"),
        "snow" to fedb("Superman"),
        "chinup" to fedb("Chin-Up"),
        "diamond" to fedb("Push-Ups_-_Close_Triceps_Position"),
        "dips" to fedb("Bench_Dips"),
        "curl" to fedb("Dumbbell_Bicep_Curl"),
        "hammer" to fedb("Hammer_Curls"),
        "bandcurl" to fedb("Dumbbell_Bicep_Curl"),
        "kneeraise" to wger("978/d3ffe51f-7eb8-4cc9-9eae-105847af3005.png"),
        "hollow" to wger("297/b10d3341-baa8-49ab-b462-5b3529389aac.png"),
        "plank" to fedb("Plank"),
        "deadbug" to fedb("Dead_Bug"),
        "bicycle" to fedb("Air_Bike"),
        "legraise" to fedb("Flat_Bench_Lying_Leg_Raise"),
        "split" to fedb("Split_Squats"),
        "squat" to fedb("Bodyweight_Squat"),
        "lunge" to wger("999/d0931eb3-8db0-4049-bb08-aa4036072056.jfif"),
        "pistol" to fedb("Kettlebell_Pistol_Squat"),
        "calf" to fedb("Standing_Calf_Raises"),
        "slcalf" to fedb("Standing_Calf_Raises"),
        "slidecurl" to fedb("Platform_Hamstring_Slides"),
        "nordic" to fedb("Natural_Glute_Ham_Raise"),
        "slbridge" to fedb("Single_Leg_Glute_Bridge"),
        "bridge" to fedb("Butt_Lift_Bridge"),
        "donkey" to fedb("Glute_Kickback"),
        "climber" to fedb("Mountain_Climbers"),
        "jack" to fedb("Star_Jump"),
        "dbpress" to fedb("Dumbbell_Shoulder_Press"),
        "arnold" to fedb("Arnold_Dumbbell_Press"),
        "reardelt" to fedb("Seated_Bent-Over_Rear_Delt_Raise"),
        "dbrow2" to fedb("Bent_Over_Two-Dumbbell_Row"),
        "shrug" to fedb("Dumbbell_Shrug"),
        "bpshrug" to fedb("Dumbbell_Shrug"),
        "ohext" to fedb("Standing_Dumbbell_Triceps_Extension"),
        "russian" to fedb("Russian_Twist"),
        "goblet" to fedb("Goblet_Squat"),
        "dblunge" to fedb("Dumbbell_Lunges"),
        "stepup" to fedb("Dumbbell_Step_Ups"),
        "rdl" to fedb("Stiff-Legged_Dumbbell_Deadlift"),
        // same motion as a dumbbell move, so its pictures show the form
        "superman" to fedb("Superman"),
        "closepush" to fedb("Push-Ups_-_Close_Triceps_Position"),
        "floordip" to fedb("Bench_Dips"),
        "bppress" to fedb("Dumbbell_Shoulder_Press"),
        "bprow" to fedb("Bent_Over_Two-Dumbbell_Row"),
        "bpcurl" to fedb("Dumbbell_Bicep_Curl"),
        "bpsquat" to fedb("Goblet_Squat"),
        "bprdl" to fedb("Stiff-Legged_Dumbbell_Deadlift"),
        "highknee" to wger("983/16245344-9957-4a24-8d61-f9939ed5f964.png"),
        // harder or homemade versions of a move that has pictures: same form, so the same pictures
        "wpush" to fedb("Pushups"),
        "tempopush" to fedb("Pushups"),
        "wpullup" to fedb("Weighted_Pull_Ups"),
        "archerpull" to fedb("Pullups"),
        "bandpull" to fedb("Wide-Grip_Lat_Pulldown"),
        "towelrow" to fedb("Inverted_Row_with_Straps"),
        "doorrow" to fedb("Inverted_Row_with_Straps"),
        "towelcurl" to fedb("Dumbbell_Bicep_Curl"),
        "wpistol" to fedb("Kettlebell_Pistol_Squat"),
    )

    // our own drawings, shipped in the APK; several frames flip like the downloaded ones
    val local: Map<String, List<Int>> = mapOf(
        "squat" to listOf(R.drawable.char_squat),
        "plank" to listOf(R.drawable.char_plank),
        "bridge" to listOf(R.drawable.char_bridge),
        "burpee" to listOf(R.drawable.move_burpee_1, R.drawable.move_burpee_2, R.drawable.move_burpee_3, R.drawable.move_burpee_4),
        "wallsit" to listOf(R.drawable.move_wallsit_1),
        "shrimp" to listOf(R.drawable.move_shrimp_1, R.drawable.move_shrimp_2),
        "boxing" to listOf(R.drawable.move_boxing_1, R.drawable.move_boxing_2, R.drawable.move_boxing_1, R.drawable.move_boxing_3),
    )

    fun has(id: String) = id in urls || id in local

    /** Blocking: cached file if present, else downloads it. Null when offline or the server fails. */
    fun load(context: Context, url: String): Bitmap? {
        val file = File(File(context.filesDir, "moveimg").apply { mkdirs() }, fileName(url))
        if (!file.exists()) runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000; conn.readTimeout = 20000
            try {
                if (conn.responseCode != 200) return null
                val tmp = File(file.path + ".tmp")
                conn.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                tmp.renameTo(file)
            } finally { conn.disconnect() }
        }
        return runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
    }

    fun urlsFor(id: String) = urls[id].orEmpty()

    // shipped in the APK, or every frame already downloaded: shows at once, even offline
    fun ready(context: Context, id: String) = id in local ||
        urlsFor(id).let { u -> u.isNotEmpty() && u.all { url -> File(File(context.filesDir, "moveimg"), fileName(url)).exists() } }

    private fun fileName(url: String) = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Picture of the move; two-frame pictures flip between start and end position so the motion is clear. */
@Composable
fun MoveImage(id: String, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    MoveImages.local[id]?.let { res ->
        var frame by remember(id) { mutableIntStateOf(0) }
        LaunchedEffect(id, reduce) {
            while (res.size > 1 && !reduce) { delay(900); frame = (frame + 1) % res.size }
        }
        Box(modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(24.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            Image(androidx.compose.ui.res.painterResource(res[frame % res.size]), contentDescription = "Cara buat move ni", Modifier.fillMaxWidth().padding(16.dp), contentScale = ContentScale.Fit)
        }
        return
    }
    val urls = MoveImages.urlsFor(id)
    if (urls.isEmpty()) return
    val context = LocalContext.current
    var frames by remember(id) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var failed by remember(id) { mutableStateOf(false) }
    var frame by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(id) {
        frames = withContext(Dispatchers.IO) { urls.mapNotNull { MoveImages.load(context, it) } }
        failed = frames.isEmpty()
    }
    LaunchedEffect(frames, reduce) {
        while (frames.size > 1 && !reduce) { delay(900); frame = (frame + 1) % frames.size }
    }
    // the source pictures sit on white, so the frame is white too
    Box(
        modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(24.dp)).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = frames.getOrNull(frame % frames.size.coerceAtLeast(1))
        when {
            bmp != null -> Image(bmp.asImageBitmap(), contentDescription = "Cara buat move ni", Modifier.fillMaxWidth().padding(8.dp), contentScale = ContentScale.Fit)
            failed -> Text("Gambar tak dapat load · check internet", color = Color(0xFF555555), fontSize = 13.sp)
            else -> Text("Loading gambar…", color = Color(0xFF555555), fontSize = 13.sp)
        }
    }
}
