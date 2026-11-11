package my.sado.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import my.sado.app.data.Silhouette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import my.sado.app.Coach
import my.sado.app.Lines
import my.sado.app.data.ProgressPhoto
import my.sado.app.data.Store
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlin.math.roundToInt

val angleNames = listOf("Depan", "Sisi", "Belakang")

object Photos {
    fun dir(context: Context) = File(context.filesDir, "photos").apply { mkdirs() }
    fun file(context: Context, p: ProgressPhoto) = File(dir(context), p.file)

    // decode at roughly the requested width and apply the EXIF rotation CameraX writes
    fun load(context: Context, name: String, width: Int): Bitmap? = runCatching {
        val f = File(dir(context), name)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val deg = when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (deg == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
    }.getOrNull()

    // shoulder:waist from the body outline (selfie segmentation), on the device; pose landmarks only find the rows.
    // Front photos only: a back photo's outline is the same width but reads the lats differently.
    suspend fun measure(context: Context, name: String): Silhouette.Measure? = runCatching {
        val bmp = load(context, name, 720) ?: return null // EXIF rotation applied, so pose and mask share one frame
        val image = InputImage.fromBitmap(bmp, 0)
        val detector = PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build())
        val pose = suspendCancellableCoroutine<Pose?> { cont ->
            detector.process(image).addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resume(null) }
        }.also { detector.close() } ?: return null
        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: return null
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return null
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP) ?: return null
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) ?: return null
        if (listOf(ls, rs, lh, rh).any { it.inFrameLikelihood < 0.5f }) return null
        val segmenter = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
        val mask = suspendCancellableCoroutine<SegmentationMask?> { cont ->
            segmenter.process(image).addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resume(null) }
        }.also { segmenter.close() } ?: return null
        val buf = mask.buffer.apply { rewind() }.asFloatBuffer()
        val px = FloatArray(mask.width * mask.height).also { buf.get(it) }
        // the mask can come back at another size than the photo: scale the landmark rows into it
        val sx = mask.width / bmp.width.toFloat()
        val sy = mask.height / bmp.height.toFloat()
        val shoulderY = (ls.position.y + rs.position.y) / 2 * sy
        val hipY = (lh.position.y + rh.position.y) / 2 * sy
        val cx = (ls.position.x + rs.position.x + lh.position.x + rh.position.x) / 4 * sx
        Silhouette.measure(px, mask.width, mask.height, shoulderY, hipY, cx)
    }.getOrNull()

    /** Front photos still on the old joint ratio get measured from their outline; back photos lose theirs. */
    suspend fun remeasure(context: Context) {
        val old = Store.state.value.photos.filter { it.ratioKind == 0 }
        old.forEach { p ->
            val m = if (p.angle == 0) measure(context, p.file) else null
            Store.update { s -> s.copy(photos = s.photos.map { if (it.file == p.file) it.copy(ratio = m?.ratio, shoulderPx = m?.shoulderPx, waistPx = m?.waistPx, ratioKind = 1) else it }) }
        }
    }

    // removes the record and the private file; ratio trend recomputes from what's left
    fun delete(context: Context, file: String) {
        Store.update { s -> s.copy(photos = s.photos.filter { it.file != file }) }
        runCatching { File(dir(context), file).delete() }
    }

    // trend over front photos: rolling mean of the last 3 so one slouchy photo doesn't swing it
    fun trend(photos: List<ProgressPhoto>): List<Float> {
        val r = photos.filter { it.angle == 0 && it.ratioKind == 1 && it.ratio != null }.sortedBy { it.epochDay }.map { it.ratio!! }
        return r.indices.map { i -> r.subList(maxOf(0, i - 2), i + 1).average().toFloat() }
    }
}

@Composable
fun PhotoThumb(name: String, modifier: Modifier, width: Int = 300) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, name) { value = withContext(Dispatchers.IO) { Photos.load(context, name, width) } }
    Box(modifier.background(SadoColors.Surface2), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
fun BodyScreen(onCamera: () -> Unit, onGallery: () -> Unit, onFood: () -> Unit) {
    val s = Store.state.collectAsState().value
    val context = LocalContext.current
    // photos from before the outline ratio get measured again once, in the background
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { Photos.remeasure(context) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Badan", "Berat, BMI, gambar dan makan · semua dalam phone")

        BodyProfileCard(s, onFood)
        WeightCard(s)

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gambar progress", color = SadoColors.Text, fontSize = 19.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("Gallery ›", color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable { onGallery() })
            }
            val latest = (0..2).map { a -> s.photos.filter { it.angle == a }.maxByOrNull { it.epochDay } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                latest.forEachIndexed { a, ph ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (ph != null) PhotoThumb(ph.file, Modifier.fillMaxWidth().aspectRatio(0.66f).clip(RoundedCornerShape(12.dp)))
                        else Box(Modifier.fillMaxWidth().aspectRatio(0.66f).clip(RoundedCornerShape(16.dp)).background(SadoColors.Surface2).pressable(onClick = onCamera), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(SadoIcons.Camera, contentDescription = "Ambil gambar " + angleNames[a].lowercase(), tint = SadoColors.TextMuted, modifier = Modifier.size(28.dp))
                        }
                        Text(angleNames[a], color = SadoColors.TextMuted, fontSize = 12.sp)
                    }
                }
            }
            Text("Disimpan private, tak masuk galeri phone.", color = SadoColors.TextMuted, fontSize = 12.sp)
            PrimaryButton("Ambil gambar minggu ni", onClick = onCamera)
        }

        val trend = Photos.trend(s.photos)
        Card {
            Text("Trend bahu : pinggang (depan)", color = SadoColors.Text, fontSize = 19.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            if (trend.size < 2) Text("Perlu sekurang-kurangnya 2 gambar depan yang badan nampak penuh.", color = SadoColors.TextMuted, fontSize = 13.sp)
            else {
                val change = ((trend.last() / trend.first() - 1) * 100).roundToInt()
                Text((if (change >= 0) "+" else "") + "$change% sejak gambar pertama", color = if (change >= 0) SadoColors.Accent else SadoColors.Streak, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                val lo = trend.min(); val hi = trend.max()
                Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    trend.takeLast(12).forEach { v ->
                        val f = if (hi - lo < 0.001f) 0.6f else 0.25f + 0.75f * (v - lo) / (hi - lo)
                        Box(Modifier.weight(1f).fillMaxHeight(f).clip(RoundedCornerShape(4.dp)).background(SadoColors.Accent))
                    }
                }
                Text("Purata bergerak 3 gambar · anggaran AI dalam phone, bukan ukuran pita. Postur dan cahaya boleh ubah ±5%.", color = SadoColors.TextMuted, fontSize = 12.sp)
            }
        }

        Card { SleepPicker() }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SadoColors.Accent.copy(alpha = 0.08f)).clickable { onFood() }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Makan hari ni ›", color = SadoColors.Accent, fontSize = 19.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text("Protein, kalori, Makanan aku, scan barcode, log AI", color = SadoColors.TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun CameraScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    var angle by remember { mutableIntStateOf(0) }
    var front by remember { mutableStateOf(false) }
    var ghost by remember { mutableFloatStateOf(0.35f) }
    var timer by remember { mutableIntStateOf(0) } // off by default; one tap for 3s, another for 10s
    var count by remember { mutableIntStateOf(-1) }
    var busy by remember { mutableStateOf(false) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val last = Store.state.collectAsState().value.photos.filter { it.angle == angle }.maxByOrNull { it.epochDay }

    DisposableEffect(granted, front) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!granted) return@addListener
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            runCatching {
                provider.bindToLifecycle(owner, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { future.get().unbindAll() } }
    }

    fun shoot() {
        val name = "${LocalDate.now()}_${angle}_${System.currentTimeMillis() % 100000}.jpg"
        val f = File(Photos.dir(context), name)
        busy = true
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(f).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Feedback.success()
                scope.launch {
                    val m = if (angle != 0) null else withContext(Dispatchers.Default) { Photos.measure(context, name) }
                    Store.update { it.copy(photos = it.photos + ProgressPhoto(LocalDate.now().toEpochDay(), angle, name, m?.ratio, m?.shoulderPx, m?.waistPx, ratioKind = 1)) }
                    Coach.say(if (m == null && angle == 0) Lines.photoPartial else Lines.photoSaved)
                    busy = false
                    onSaved()
                }
            }
            override fun onError(e: ImageCaptureException) { busy = false; Coach.say(Lines.photoFail) }
        })
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (granted) AndroidView({ previewView }, Modifier.fillMaxSize())
            else Text("Perlukan kebenaran camera.", color = SadoColors.Text, modifier = Modifier.align(Alignment.Center))
            if (granted && angle != 1) FramingGuide(Modifier.fillMaxSize())
            if (last != null && ghost > 0.02f) {
                // ghost of last week's photo, mirrored like the selfie preview, so you stand in the same spot
                PhotoThumb(last.file, Modifier.fillMaxSize().alpha(ghost).graphicsLayer { scaleX = if (front) -1f else 1f }, 600)
            }
            Column(Modifier.align(Alignment.TopCenter).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Tutup", tint = SadoColors.Text,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xB30E0F12)).clickable { onBack() }.padding(8.dp))
                    angleNames.forEachIndexed { i, n ->
                        Text(n, color = if (i == angle) SadoColors.Bg else SadoColors.Text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (i == angle) SadoColors.Text else Color(0xB30E0F12)).clickable { angle = i }.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
                Text("Gambar disimpan private, tak masuk galeri", color = SadoColors.Accent, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xB30E0F12)).padding(horizontal = 10.dp, vertical = 4.dp))
            }
            SnapTip(angle, Modifier.align(Alignment.BottomCenter).padding(12.dp))
            if (count > 0) Text("$count", color = SadoColors.Accent, fontSize = 96.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.fillMaxWidth().background(SadoColors.Bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (last != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ghost ${LocalDate.ofEpochDay(last.epochDay)}", color = SadoColors.TextMuted, fontSize = 12.sp, modifier = Modifier.width(120.dp))
                Slider(ghost, { ghost = it }, Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = SadoColors.Accent, activeTrackColor = SadoColors.Accent))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(if (front) "Depan cam" else "Belakang cam", Modifier.weight(1f)) { front = !front }
                SecondaryButton(if (timer == 0) "Timer: off" else "Timer: ${timer}s", Modifier.weight(1f)) { timer = when (timer) { 0 -> 3; 3 -> 10; else -> 0 } }
            }
            PrimaryButton(if (busy) "Menyimpan..." else if (count > 0) "Berdiri diam..." else "Ambil gambar", enabled = granted && !busy && count <= 0) {
                scope.launch {
                    if (timer > 0) Coach.say(Lines.countdown(timer))
                    for (i in timer downTo 1) { count = i; if (i <= 3) Feedback.countdown(); delay(1000) }
                    count = -1
                    shoot()
                }
            }
            Text("Lampu sama, baju sama, jarak sama setiap minggu.", color = SadoColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(onBack: () -> Unit, onCompare: (String, String) -> Unit, onViewer: (Int, String?) -> Unit) {
    val s = Store.state.collectAsState().value
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var angle by remember { mutableIntStateOf(0) }
    var video by remember { mutableStateOf(false) }
    // long-press turns on select mode; while on, taps toggle photos instead of opening them
    var selecting by remember { mutableStateOf(false) }
    val sel = remember { mutableStateListOf<String>() }
    val list = s.photos.filter { it.angle == angle }.sortedByDescending { it.epochDay }
    val start = s.photos.minOfOrNull { it.epochDay }
    fun exitSelect() { selecting = false; sel.clear() }
    BackHandler(enabled = selecting) { exitSelect() }
    if (video && start != null) TimelapseDialog(angle, list, start) { video = false }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Title("Gallery", "Private · ${s.photos.size} gambar", onBack = onBack)
            Segmented(angleNames, angle) { angle = it; exitSelect() }
            if (list.size >= 2 && !selecting) {
                PrimaryButton("Tengok perubahan (slaid)") { onViewer(angle, null) }
                SecondaryButton("Jana video timelapse", Modifier.fillMaxWidth()) { video = true }
            }
            Text(
                if (selecting) "Tekan gambar untuk pilih. Pilih 2 untuk banding." else "Tekan gambar untuk buka. Tekan lama untuk pilih banyak.",
                color = SadoColors.TextMuted, fontSize = 13.sp,
            )
            if (list.isEmpty()) Text("Belum ada gambar ${angleNames[angle].lowercase()}.", color = SadoColors.TextMuted, fontSize = 13.sp)
            list.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { ph ->
                        val picked = ph.file in sel
                        fun toggle() { if (picked) sel.remove(ph.file) else sel.add(ph.file); Feedback.tap() }
                        Box(
                            Modifier.weight(1f).aspectRatio(0.66f).clip(RoundedCornerShape(12.dp))
                                .border(2.dp, if (picked) SadoColors.Accent else Color.Transparent, RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = { if (selecting) toggle() else onViewer(angle, ph.file) },
                                    onLongClick = { if (!selecting) { selecting = true; sel.clear() }; if (ph.file !in sel) toggle() },
                                )
                        ) {
                            PhotoThumb(ph.file, Modifier.fillMaxSize().alpha(if (selecting && !picked) 0.6f else 1f))
                            val week = if (start != null) (ph.epochDay - start) / 7 + 1 else 1
                            Text("M$week", color = SadoColors.Text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC0E0F12)).padding(horizontal = 6.dp, vertical = 2.dp))
                            if (selecting) Box(
                                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                                    .background(if (picked) SadoColors.Accent else Color(0x990E0F12))
                                    .border(2.dp, if (picked) SadoColors.Accent else SadoColors.Text, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { if (picked) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Check, contentDescription = null, tint = SadoColors.OnAccent, modifier = Modifier.size(14.dp)) }
                        }
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        if (selecting) Column(Modifier.fillMaxWidth().background(SadoColors.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${sel.size} dipilih", color = SadoColors.Text, fontSize = 19.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text(if (sel.size == list.size) "Nyahpilih semua" else "Pilih semua", color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clickable { if (sel.size == list.size) sel.clear() else { sel.clear(); sel.addAll(list.map { it.file }) } }.padding(8.dp))
                Text("Batal", color = SadoColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable { exitSelect() }.padding(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Banding", Modifier.weight(1f), color = if (sel.size == 2) SadoColors.Accent else SadoColors.TextMuted) {
                    if (sel.size == 2) {
                        val (a, b) = sel.map { f -> s.photos.first { it.file == f } }.sortedBy { it.epochDay }
                        exitSelect()
                        onCompare(a.file, b.file)
                    }
                }
                SecondaryButton("Padam (${sel.size})", Modifier.weight(1f), color = if (sel.isNotEmpty()) SadoColors.Danger else SadoColors.TextMuted) {
                    if (sel.isNotEmpty()) confirmDelete = true
                }
            }
        }
    }
    if (confirmDelete) DeletePhotoDialog(sel.size, onDismiss = { confirmDelete = false }) {
        sel.forEach { Photos.delete(context, it) }
        confirmDelete = false
        exitSelect()
    }
}

@Composable
private fun DeletePhotoDialog(count: Int, onDismiss: () -> Unit, onDelete: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = SadoColors.Surface,
    title = { Text("Padam $count gambar?", color = SadoColors.Text, fontWeight = FontWeight.ExtraBold) },
    text = { Text("Gambar dibuang terus dari phone dan trend bahu : pinggang dikira semula. Tak boleh undo, kecuali ada dalam backup .sado lama.", color = SadoColors.TextMuted) },
    confirmButton = { TextButton(onDismiss) { Text("Batal", color = SadoColors.Accent, fontWeight = FontWeight.ExtraBold) } },
    dismissButton = { TextButton(onDelete) { Text("Padam", color = SadoColors.Danger) } },
)

/** Full-screen swipe through one angle, oldest to newest; the dot bar scrubs instantly. */
@Composable
fun PhotoViewerScreen(angle: Int, startFile: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = Store.state.collectAsState().value
    val list = s.photos.filter { it.angle == angle }.sortedBy { it.epochDay }
    val start = s.photos.minOfOrNull { it.epochDay }
    if (list.isEmpty()) { LaunchedEffect(Unit) { onBack() }; return }
    // open on the tapped photo, else the newest so the first swipe goes back in time
    val pager = rememberPagerState(initialPage = list.indexOfFirst { it.file == startFile }.takeIf { it >= 0 } ?: list.lastIndex) { list.size }
    var confirmDelete by remember { mutableStateOf(false) }
    val cur = list[pager.currentPage.coerceAtMost(list.lastIndex)]
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.drop(1).collect { Feedback.tap() } }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().background(SadoColors.Bg).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Tutup", tint = SadoColors.Text,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).clickable { onBack() }.padding(12.dp))
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                val week = if (start != null) (cur.epochDay - start) / 7 + 1 else 1
                Text("Minggu $week · ${angleNames[angle]}", color = SadoColors.Text, fontSize = 19.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Text("${LocalDate.ofEpochDay(cur.epochDay)} · ${pager.currentPage + 1} / ${list.size}", color = SadoColors.TextMuted, fontSize = 13.sp)
            }
            Text("Padam", color = SadoColors.Danger, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { confirmDelete = true }.padding(horizontal = 12.dp, vertical = 8.dp))
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 2, key = { list[it].file }) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PhotoFit(list[page].file)
            }
        }
        Column(Modifier.fillMaxWidth().background(SadoColors.Bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val first = list.firstOrNull { it.ratioKind == 1 && it.ratio != null } ?: list.first()
            if (angle == 0 && first.ratioKind == 1 && cur.ratioKind == 1 && first.ratio != null && cur.ratio != null && cur != first) {
                val change = ((cur.ratio / first.ratio - 1) * 100).roundToInt()
                Text("Bahu : pinggang " + (if (change >= 0) "+" else "") + "$change% berbanding gambar pertama", color = if (change >= 0) SadoColors.Accent else SadoColors.Streak, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            DotScrubber(list.size, pager.currentPage) { i -> scope.launch { pager.scrollToPage(i) } }
            Text("Leret gambar, atau tekan dan leret pada titik untuk tengok perubahan laju.", color = SadoColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
    if (confirmDelete) DeletePhotoDialog(1, onDismiss = { confirmDelete = false }) {
        confirmDelete = false
        Photos.delete(context, cur.file)
    }
}

// whole photo, letterboxed, at screen resolution
@Composable
private fun PhotoFit(name: String) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, name) { value = withContext(Dispatchers.IO) { Photos.load(context, name, 1080) } }
    bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
}

// one slot per photo across the full width: tap a slot to jump, drag along the bar to flip through fast
@Composable
private fun DotScrubber(count: Int, current: Int, onPick: (Int) -> Unit) {
    var width by remember { mutableIntStateOf(1) }
    fun at(x: Float) = (x / width * count).toInt().coerceIn(0, count - 1)
    Row(
        Modifier.fillMaxWidth().height(36.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(count) { detectTapGestures { onPick(at(it.x)) } }
            .pointerInput(count) {
                detectHorizontalDragGestures(onDragStart = { onPick(at(it.x)) }) { change, _ -> change.consume(); onPick(at(change.position.x)) }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val on = i == current
                val size = if (count > 24) (if (on) 14.dp else 6.dp) else (if (on) 14.dp else 8.dp)
                Box(Modifier.size(width = if (count > 40) 3.dp.coerceAtLeast(size / 2) else size, height = size).clip(CircleShape).background(if (on) SadoColors.Accent else SadoColors.Track))
            }
        }
    }
}

@Composable
fun CompareScreen(a: String, b: String, onBack: () -> Unit) {
    val s = Store.state.collectAsState().value
    val pa = s.photos.firstOrNull { it.file == a } ?: return
    val pb = s.photos.firstOrNull { it.file == b } ?: return
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Banding", "${pb.epochDay - pa.epochDay} hari beza", onBack = onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(pa, pb).forEach { ph ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PhotoThumb(ph.file, Modifier.fillMaxWidth().aspectRatio(0.66f).clip(RoundedCornerShape(12.dp)), 700)
                    Text("${LocalDate.ofEpochDay(ph.epochDay)}", color = SadoColors.TextMuted, fontSize = 12.sp)
                }
            }
        }
        if (pa.ratio != null && pb.ratio != null && pa.ratioKind == 1 && pb.ratioKind == 1) {
            val change = ((pb.ratio / pa.ratio - 1) * 100).roundToInt()
            Text("Bahu : pinggang " + (if (change >= 0) "+" else "") + "$change%", color = if (change >= 0) SadoColors.Accent else SadoColors.Streak, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** What the shoulder:hip trend needs: both shoulders and both hip joints well inside the frame. */
@Composable
internal fun SnapTip(angle: Int, modifier: Modifier = Modifier) {
    val lines = if (angle == 1) listOf("Sisi: berdiri tegak, tangan ke bawah, pandang depan.")
    else listOf(
        "Snap kepala sampai lutut",
        "Paling kurang bahu sampai tengah peha. Tangan jauh sikit dari pinggang (jangan rapat ke badan), badan rileks, phone sama jarak setiap kali.",
    )
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xCC0E0F12)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEachIndexed { i, s -> Text(s, color = if (i == 0) SadoColors.Text else SadoColors.TextMuted, fontSize = if (i == 0) 14.sp else 12.sp, fontWeight = if (i == 0) FontWeight.ExtraBold else FontWeight.Normal) }
    }
}

/** Faint dashed lines where shoulders, hips and knees sit in a head-to-knee shot. */
@Composable
internal fun FramingGuide(modifier: Modifier = Modifier) {
    val marks = listOf(0.22f to "bahu", 0.50f to "pinggul", 0.76f to "lutut")
    Box(modifier) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(18f, 14f))
            marks.forEach { (y, _) ->
                drawLine(Color.White.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * y),
                    androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * y), strokeWidth = 3f, pathEffect = dash)
            }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            marks.forEach { (y, label) ->
                Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp).offset(y = maxHeight * y - 11.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x990E0F12)).padding(horizontal = 6.dp, vertical = 1.dp))
            }
        }
    }
}
