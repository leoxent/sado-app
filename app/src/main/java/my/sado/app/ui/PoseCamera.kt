package my.sado.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import my.sado.app.Coach
import my.sado.app.ai.Motions
import my.sado.app.ai.RepEngine
import java.util.concurrent.Executors

private val bones = listOf(
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER, PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
    PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
)

/**
 * Live camera that counts reps (or checks form on holds) with on-device ML Kit pose detection.
 * Frames never leave the phone and nothing is saved.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun PoseCamera(moveId: String, onRep: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    // front camera by default so you can see the count while the phone leans on something
    var front by remember { mutableStateOf(true) }
    var pose by remember { mutableStateOf<Pose?>(null) }
    var size by remember { mutableStateOf(480 to 640) }
    var cue by remember { mutableStateOf<String?>(null) }
    var lastSpoken by remember { mutableLongStateOf(0L) }
    val rep by rememberUpdatedState(onRep)
    val engine = remember(moveId) { RepEngine(Motions.reps[moveId], Motions.holds[moveId] ?: my.sado.app.ai.FormCheck.None) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(granted, front) {
        val detector = PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
        val worker = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        analysis.setAnalyzer(worker) { proxy ->
            val media = proxy.image
            if (media == null) { proxy.close(); return@setAnalyzer }
            val rot = proxy.imageInfo.rotationDegrees
            val dims = if (rot % 180 == 0) proxy.width to proxy.height else proxy.height to proxy.width
            detector.process(InputImage.fromMediaImage(media, rot))
                .addOnSuccessListener(main) { p ->
                    size = dims
                    pose = p
                    val (counted, c) = engine.feed(p)
                    if (counted) rep()
                    cue = c
                    // speak a correction at most every 4 s so it doesn't nag every frame
                    val now = System.currentTimeMillis()
                    if (c != null && engine.visible && now - lastSpoken > 4000) { Coach.say(my.sado.app.Lines.cue(c)); lastSpoken = now }
                }
                .addOnCompleteListener { proxy.close() }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!granted) return@addListener
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            runCatching {
                provider.bindToLifecycle(owner, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, main)
        onDispose {
            runCatching { future.get().unbindAll() }
            analysis.clearAnalyzer()
            detector.close()
            worker.shutdown()
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
            if (granted) AndroidView({ previewView }, Modifier.fillMaxSize())
            else Text("Perlukan kebenaran camera.", color = SadoColors.Text, modifier = Modifier.align(Alignment.Center))
            val p = pose
            if (p != null) Canvas(Modifier.fillMaxSize()) {
                val (iw, ih) = size
                // same maths as PreviewView FILL_CENTER, mirrored for the selfie camera
                val scale = maxOf(this.size.width / iw, this.size.height / ih)
                val ox = (this.size.width - iw * scale) / 2
                val oy = (this.size.height - ih * scale) / 2
                fun pt(l: PoseLandmark): Offset {
                    val x = l.position.x * scale + ox
                    return Offset(if (front) this.size.width - x else x, l.position.y * scale + oy)
                }
                val color = if (cue == null) SadoColors.Accent else SadoColors.Streak
                bones.forEach { (a, b) ->
                    val la = p.getPoseLandmark(a); val lb = p.getPoseLandmark(b)
                    if (la != null && lb != null && la.inFrameLikelihood > 0.5f && lb.inFrameLikelihood > 0.5f) drawLine(color, pt(la), pt(lb), strokeWidth = 8f)
                }
            }
            Text(
                cue ?: if (engine.visible) "Form cun" else "Cari badan…",
                Modifier.align(Alignment.TopCenter).padding(12.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xCC0E0F12)).padding(horizontal = 14.dp, vertical = 8.dp),
                color = if (cue == null && engine.visible) SadoColors.Accent else SadoColors.Streak, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "Tukar camera",
                Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xCC0E0F12)).clickable { front = !front }.padding(horizontal = 12.dp, vertical = 8.dp),
                color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold,
            )
        }
        Motions.viewFor(moveId)?.let { Text(it, color = SadoColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
}

/** Compact pill to switch camera rep counting on or off for the current move. */
@Composable
fun CameraToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = SadoColors.Sky
    Row(
        Modifier.fillMaxWidth().pressable(role = androidx.compose.ui.semantics.Role.Switch) { onChange(!on) }.clip(RoundedCornerShape(18.dp))
            .background(c.copy(alpha = if (on) 0.18f else 0.08f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(SadoIcons.Camera, contentDescription = null, tint = SadoColors.OnAccent, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Camera AI kira rep", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Dalam phone je · ketepatan ikut cahaya", color = SadoColors.TextMuted, fontSize = 12.sp)
        }
        androidx.compose.material3.Switch(on, onChange, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c, checkedThumbColor = SadoColors.Bg))
    }
}
