package my.sado.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.sado.app.data.ProgressPhoto
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Progress photos to an MP4, made on the phone with the platform H.264 encoder: each photo holds,
 * then crossfades into the next; the last one holds longer. The file sits in the app's cache until shared.
 */
object Timelapse {
    const val W = 720
    const val H = 1280
    const val FPS = 30
    const val HOLD = 24 // 0.8 s per photo
    const val FADE = 12 // 0.4 s crossfade
    const val END = 36 // the last photo stays 1.2 s more

    fun frameCount(n: Int) = if (n <= 0) 0 else n * HOLD + (n - 1) * FADE + END

    /** Which photo a frame shows, and how far it has faded into the next one (0 = not at all). */
    fun at(frame: Int, n: Int): Pair<Int, Float> {
        val step = HOLD + FADE
        val i = frame / step
        if (i >= n - 1) return (n - 1) to 0f
        val into = frame % step
        return i to if (into < HOLD) 0f else (into - HOLD + 1).toFloat() / (FADE + 1)
    }

    fun seconds(n: Int) = frameCount(n) / FPS.toFloat()

    fun outFile(context: Context) = File(context.cacheDir, "timelapse").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        .let { File(it, "sado-timelapse-${LocalDate.now()}.mp4") }

    /** One photo laid out on a 9:16 card with its caption lines at the bottom. */
    private fun card(context: Context, p: ProgressPhoto, lines: List<String>): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFF0E0F12.toInt())
        Photos.load(context, p.file, W)?.let { src ->
            // centre-crop to fill the frame
            val k = maxOf(W / src.width.toFloat(), H / src.height.toFloat())
            val w = W / k; val h = H / k
            val l = (src.width - w) / 2f; val t = (src.height - h) / 2f
            c.drawBitmap(src, Rect(l.toInt(), t.toInt(), (l + w).toInt(), (t + h).toInt()), RectF(0f, 0f, W.toFloat(), H.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        if (lines.isNotEmpty()) {
            val band = Paint().apply { shader = LinearGradient(0f, H - 300f, 0f, H.toFloat(), 0x000E0F12, 0xE60E0F12.toInt(), Shader.TileMode.CLAMP) }
            c.drawRect(0f, H - 300f, W.toFloat(), H.toFloat(), band)
            val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC8F04A.toInt(); textSize = 56f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 34f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            var y = H - 60f - (lines.size - 1) * 48f
            lines.forEachIndexed { i, s -> c.drawText(s, 44f, y, if (i == 0) big else small); y += if (i == 0) 58f else 46f }
        }
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); textSize = 28f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        c.drawText("SADO", W - 120f, 64f, mark)
        return out
    }

    fun render(context: Context, photos: List<ProgressPhoto>, captions: List<List<String>>, out: File, onProgress: (Float) -> Unit) {
        val n = photos.size
        val total = frameCount(n)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        val info = MediaCodec.BufferInfo()

        fun drain(untilEnd: Boolean) {
            while (true) {
                val i = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEnd) return
                    i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start() }
                    i >= 0 -> {
                        val buf = codec.getOutputBuffer(i)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (buf != null && info.size > 0 && track >= 0) {
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(i, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        val frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        val fade = Paint()
        val px = IntArray(W * H)
        var shown = -1
        var a: Bitmap? = null
        var b: Bitmap? = null
        try {
            for (f in 0 until total) {
                val (i, t) = at(f, n)
                if (i != shown) {
                    a = if (i == shown + 1 && b != null) b else card(context, photos[i], captions[i])
                    b = if (i + 1 < n) card(context, photos[i + 1], captions[i + 1]) else null
                    shown = i
                }
                canvas.drawBitmap(a!!, 0f, 0f, null)
                if (t > 0f && b != null) { fade.alpha = (t * 255).roundToInt(); canvas.drawBitmap(b, 0f, 0f, fade) }
                frame.getPixels(px, 0, W, 0, 0, W, H)
                var idx: Int
                do { idx = codec.dequeueInputBuffer(10_000); if (idx < 0) drain(false) } while (idx < 0)
                codec.getInputImage(idx)?.let { writeYuv(it, px) }
                codec.queueInputBuffer(idx, 0, W * H * 3 / 2, f * 1_000_000L / FPS, 0)
                drain(false)
                if (f % 6 == 0) onProgress(f / total.toFloat())
            }
            var idx: Int
            do { idx = codec.dequeueInputBuffer(10_000); if (idx < 0) drain(false) } while (idx < 0)
            codec.queueInputBuffer(idx, 0, 0, total * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
            onProgress(1f)
        } finally {
            runCatching { codec.stop() }; codec.release()
            runCatching { muxer.stop() }; muxer.release()
        }
    }

    // BT.601 limited range; the encoder's own plane layout (strides) is honoured, so it works on any device
    private fun writeYuv(img: Image, px: IntArray) {
        val y = img.planes[0]; val u = img.planes[1]; val v = img.planes[2]
        val yb = y.buffer; val ub = u.buffer; val vb = v.buffer
        for (r in 0 until H) {
            val row = r * y.rowStride
            for (c in 0 until W) {
                val p = px[r * W + c]
                val R = p shr 16 and 255; val G = p shr 8 and 255; val B = p and 255
                yb.put(row + c * y.pixelStride, (((66 * R + 129 * G + 25 * B + 128) shr 8) + 16).toByte())
            }
        }
        for (r in 0 until H / 2) for (c in 0 until W / 2) {
            val p = px[(r * 2) * W + c * 2]
            val R = p shr 16 and 255; val G = p shr 8 and 255; val B = p and 255
            ub.put(r * u.rowStride + c * u.pixelStride, (((-38 * R - 74 * G + 112 * B + 128) shr 8) + 128).toByte())
            vb.put(r * v.rowStride + c * v.pixelStride, (((112 * R - 94 * G - 18 * B + 128) shr 8) + 128).toByte())
        }
    }
}

/** Pick the weeks and captions, make the video, then play or share it. */
@Composable
fun TimelapseDialog(angle: Int, photos: List<ProgressPhoto>, start: Long, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TimelapseBody(angle, photos, start, onDismiss)
    }
}

@Composable
fun TimelapseBody(angle: Int, photos: List<ProgressPhoto>, start: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = photos.sortedBy { it.epochDay }
    val last = (list.size - 1).coerceAtLeast(1)
    var range by remember { mutableStateOf(0f..last.toFloat()) }
    var dates by remember { mutableStateOf(true) }
    var ratio by remember { mutableStateOf(angle == 0) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var done by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }
    val from = range.start.roundToInt(); val to = range.endInclusive.roundToInt()
    val chosen = list.subList(from, (to + 1).coerceAtMost(list.size))
    fun week(p: ProgressPhoto) = (p.epochDay - start) / 7 + 1
    fun day(p: ProgressPhoto) = LocalDate.ofEpochDay(p.epochDay).let { "${it.dayOfMonth} ${monthNames[it.monthValue - 1].take(3)} ${it.year}" }
    fun share(f: File, view: Boolean) {
        val uri = FileProvider.getUriForFile(context, "my.sado.app.files", f)
        val i = if (view) Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
        else Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri), "Kongsi timelapse")
        context.startActivity(i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Column(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(SadoColors.Surface)
            .border(1.dp, SadoColors.Outline, RoundedCornerShape(28.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Video timelapse", color = SadoColors.Text, fontSize = 22.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        Text("${chosen.size} gambar ${angleNames[angle].lowercase()} · lebih kurang ${"%.0f".format(Timelapse.seconds(chosen.size))} saat", color = SadoColors.TextMuted, fontSize = 13.sp)
        val f = done
        when {
            f != null -> {
                Text("Siap! ${f.length() / 1024} KB", color = SadoColors.Accent, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                PrimaryButton("Main video") { share(f, true) }
                SecondaryButton("Kongsi", Modifier.fillMaxWidth()) { share(f, false) }
            }
            progress >= 0f -> {
                Text("Tengah buat video dalam phone...", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(10.dp)), color = SadoColors.Accent, trackColor = SadoColors.Track)
                Text("${(progress * 100).roundToInt()}%", color = SadoColors.TextMuted, fontSize = 12.sp)
            }
            else -> {
                if (list.size > 2) {
                    Text("Dari minggu ${week(list[from])} (${day(list[from])}) hingga minggu ${week(list[to])} (${day(list[to])})", color = SadoColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    RangeSlider(range, { r -> if (r.endInclusive - r.start >= 1f) range = r }, valueRange = 0f..last.toFloat(), steps = (last - 1).coerceAtLeast(0),
                        colors = SliderDefaults.colors(thumbColor = SadoColors.Accent, activeTrackColor = SadoColors.Accent))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tunjuk minggu & tarikh", color = SadoColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(dates, { dates = it }, colors = SwitchDefaults.colors(checkedThumbColor = SadoColors.OnAccent, checkedTrackColor = SadoColors.Accent))
                }
                if (angle == 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tunjuk nisbah bahu : pinggang", color = SadoColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(ratio, { ratio = it }, colors = SwitchDefaults.colors(checkedThumbColor = SadoColors.OnAccent, checkedTrackColor = SadoColors.Accent))
                }
                if (failed) Text("Tak berjaya buat video kat phone ni.", color = SadoColors.Danger, fontSize = 13.sp)
                PrimaryButton("Jana video", enabled = chosen.size >= 2) {
                    progress = 0f; failed = false
                    val caps = chosen.map { p ->
                        buildList {
                            if (dates) { add("Minggu ${week(p)}"); add(day(p)) }
                            if (ratio && p.ratio != null && p.ratioKind == 1) add("Bahu : pinggang ${"%.2f".format(p.ratio)}")
                        }
                    }
                    scope.launch {
                        val out = Timelapse.outFile(context)
                        val ok = withContext(Dispatchers.Default) {
                            runCatching { Timelapse.render(context, chosen, caps, out) { p -> progress = p } }.isSuccess
                        }
                        progress = -1f
                        if (ok && out.length() > 0) done = out else failed = true
                    }
                }
                Text("Video dibuat dalam phone dan disimpan private. Dia keluar dari phone hanya bila kau tekan Kongsi.", color = SadoColors.TextMuted, fontSize = 12.sp)
            }
        }
        Text(
            "Tutup", color = SadoColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(12.dp)).clickableRole(onDismiss).padding(12.dp),
        )
    }
}

private fun Modifier.clickableRole(onClick: () -> Unit) = this.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
