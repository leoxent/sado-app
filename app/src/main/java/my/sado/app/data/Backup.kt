package my.sado.app.data

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// .sado file = "SADO1" + 16-byte salt + 12-byte IV + AES-256-GCM(zip of state.json and photos/*).
// The key comes from the user's backup password, so Drive, PikPak or Telegram only ever hold ciphertext.
object Backup {
    private val MAGIC = "SADO1".toByteArray()
    private const val ITERATIONS = 120_000

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    fun export(context: Context, password: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        return File(dir, "sado-backup-${LocalDate.now()}.sado").apply { writeBytes(exportBytes(context, password)) }
    }

    /** The whole encrypted .sado in memory, for the share sheet or the auto-backup folder. */
    fun exportBytes(context: Context, password: String): ByteArray {
        val zipped = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("state.json")); zip.write(Store.exportText().toByteArray()); zip.closeEntry()
                File(context.filesDir, "photos").listFiles()?.forEach { f ->
                    zip.putNextEntry(ZipEntry("photos/${f.name}")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        }.toByteArray()
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
        return MAGIC + salt + iv + c.doFinal(zipped)
    }

    sealed class Result {
        data class Ok(val photos: Int) : Result()
        data object WrongPassword : Result()
        data object NotSado : Result()
    }

    fun restore(context: Context, uri: Uri, password: String): Result {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return Result.NotSado
        if (bytes.size < 33 || !bytes.copyOfRange(0, 5).contentEquals(MAGIC)) return Result.NotSado
        val salt = bytes.copyOfRange(5, 21)
        val iv = bytes.copyOfRange(21, 33)
        val zipped = runCatching {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
                .doFinal(bytes, 33, bytes.size - 33)
        }.getOrElse { return Result.WrongPassword }
        var state: String? = null
        val photos = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipped)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { e ->
                val data = zip.readBytes()
                when {
                    e.name == "state.json" -> state = String(data)
                    // keep only plain file names so a crafted archive can't write outside photos/
                    e.name.startsWith("photos/") && !e.name.contains("..") -> photos[e.name.removePrefix("photos/").substringAfterLast('/')] = data
                }
            }
        }
        val text = state ?: return Result.NotSado
        Store.restore(text)
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        photos.forEach { (name, data) -> if (name.isNotBlank()) File(dir, name).writeBytes(data) }
        return Result.Ok(photos.size)
    }
}
