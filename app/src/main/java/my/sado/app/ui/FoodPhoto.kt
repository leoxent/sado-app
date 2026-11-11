package my.sado.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

/** A food photo shrunk for the trip to Claude: long side at most 1024 px, JPEG, held in memory only. */
class FoodPhoto(val jpeg: ByteArray, val preview: ImageBitmap) {
    companion object {
        private const val MAX = 1024

        fun read(context: Context, uri: Uri): FoodPhoto? = runCatching {
            // ImageDecoder turns the photo upright from its EXIF and scales while decoding
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                val w = info.size.width; val h = info.size.height
                val k = minOf(1f, MAX.toFloat() / maxOf(w, h))
                d.setTargetSize((w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1))
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            FoodPhoto(out.toByteArray(), bmp.asImageBitmap())
        }.getOrNull()
    }
}
