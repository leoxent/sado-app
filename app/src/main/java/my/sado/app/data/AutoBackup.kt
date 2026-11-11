package my.sado.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.time.LocalDate

/**
 * Writes an encrypted .sado into a folder the user picked, every few days, and keeps only the newest few.
 * The backup password sits in the Keystore-encrypted Secrets, never in the state file or the backup itself.
 * Runs from a daily alarm and when the app opens; both only act when a backup is due.
 */
object AutoBackup {
    const val KEEP = 4
    const val PREFIX = "sado-auto-"
    const val SECRET = "backup_pass"

    fun due(s: Settings, today: Long = LocalDate.now().toEpochDay()): Boolean =
        s.autoBackup && s.backupFolder != null && (s.lastAutoBackup == null || today - s.lastAutoBackup >= s.backupEveryDays)

    /** Older auto files to delete once a new one is written, oldest first; names sort by date. */
    fun toPrune(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter { it.startsWith(PREFIX) && it.endsWith(".sado") }.sorted().dropLast(keep)

    sealed class Result {
        data class Ok(val name: String, val kb: Long) : Result()
        data class Failed(val why: String) : Result()
    }

    @Synchronized
    fun run(context: Context): Result {
        val st = Store.state.value.settings
        val folder = st.backupFolder ?: return fail("Folder belum dipilih.")
        val pass = Secrets.load(context, SECRET) ?: return fail("Kata laluan backup tak jumpa. Set semula auto backup.")
        return runCatching {
            val tree = Uri.parse(folder)
            val r = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = "$PREFIX${LocalDate.now()}.sado"
            // same day twice: replace the earlier file rather than pile up copies
            children(context, tree).filter { it.first == name }.forEach { DocumentsContract.deleteDocument(r, it.second) }
            val doc = DocumentsContract.createDocument(r, parent, "application/octet-stream", name) ?: error("Tak boleh tulis dalam folder tu.")
            val bytes = Backup.exportBytes(context, pass)
            r.openOutputStream(doc, "w")?.use { it.write(bytes) } ?: error("Tak boleh tulis dalam folder tu.")
            val all = children(context, tree)
            val old = toPrune(all.map { it.first })
            all.filter { it.first in old }.forEach { runCatching { DocumentsContract.deleteDocument(r, it.second) } }
            Store.update { s -> s.copy(settings = s.settings.copy(lastAutoBackup = LocalDate.now().toEpochDay(), lastAutoBackupError = null)) }
            Result.Ok(name, bytes.size / 1024L)
        }.getOrElse { fail(it.message ?: "Backup gagal.") }
    }

    private fun fail(why: String): Result {
        Store.update { s -> s.copy(settings = s.settings.copy(lastAutoBackupError = why)) }
        return Result.Failed(why)
    }

    private fun children(context: Context, tree: Uri): List<Pair<String, Uri>> {
        val list = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = mutableListOf<Pair<String, Uri>>()
        context.contentResolver.query(list, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) out += c.getString(1) to DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
        }
        return out
    }

    /** A daily wake-up; the receiver checks whether a backup is due. */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(context, 2, Intent(context, AutoBackupReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        if (!Store.state.value.settings.autoBackup) return
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR, AlarmManager.INTERVAL_DAY, pi)
    }

    /** Called on app start: back up in the background when one is due. */
    fun runIfDue(context: Context) {
        if (!due(Store.state.value.settings) || Store.simulating()) return // a dev simulation is not worth keeping
        Thread { run(context.applicationContext) }.start()
    }
}

class AutoBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context.applicationContext)
        if (!AutoBackup.due(Store.state.value.settings) || Store.simulating()) return
        val pending = goAsync()
        Thread { try { AutoBackup.run(context.applicationContext) } finally { pending.finish() } }.start()
    }
}
