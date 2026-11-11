package my.sado.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.sado.app.data.AutoBackup
import my.sado.app.data.Backup
import my.sado.app.data.Secrets
import my.sado.app.data.Store

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = Store.state.collectAsState().value
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var restorePass by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked = it }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Backup & pulih", "Encrypted AES-256 sebelum keluar phone", onBack = onBack)
        Card {
            Text("Apa yang di-backup", color = SadoColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Profil, plan, XP & level, ${s.logs.size} workout, ${s.sports.size} sukan, ${s.tests.size} ujian, ${s.meals.size} log makan, " +
                    "${s.myFoods.size} Makanan aku, ${s.photos.size} gambar progress, setting.",
                color = SadoColors.TextMuted, fontSize = 13.sp,
            )
            Text("Tak ikut: Claude API key. Masukkan semula kat phone baru.", color = SadoColors.TextMuted, fontSize = 13.sp)
        }
        AutoBackupCard()
        Label("Export sekarang")
        OutlinedTextField(pass, { pass = it }, label = { Text("Kata laluan backup (min 6)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        OutlinedTextField(pass2, { pass2 = it }, label = { Text("Ulang kata laluan") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        Text("Kalau lupa kata laluan ni, backup tak boleh dibuka langsung. Simpan baik-baik.", color = SadoColors.Streak, fontSize = 12.sp)
        PrimaryButton(if (busy) "Encrypt..." else "Export & kongsi (PikPak, Drive, Telegram)", enabled = !busy && pass.length >= 6 && pass == pass2) {
            busy = true
            scope.launch {
                val f = runCatching { withContext(Dispatchers.IO) { Backup.export(context, pass) } }.getOrNull()
                busy = false
                if (f == null) { msg = "Export gagal."; return@launch }
                val uri = FileProvider.getUriForFile(context, "my.sado.app.files", f)
                val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Simpan backup Sado").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                msg = "Backup ${f.name} (${f.length() / 1024} KB) sedia untuk dikongsi."
            }
        }
        Label("Pulihkan")
        SecondaryButton(if (picked == null) "Pilih fail .sado" else "Fail dipilih ✓", Modifier.fillMaxWidth()) { open.launch(arrayOf("*/*")) }
        if (picked != null) {
            OutlinedTextField(restorePass, { restorePass = it }, label = { Text("Kata laluan backup") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            PrimaryButton("Pulihkan dari backup", enabled = restorePass.isNotEmpty() && !busy) { confirmRestore = true }
        }
        if (msg != null) Text(msg!!, color = SadoColors.Accent, fontSize = 13.sp)
    }

    if (confirmRestore) AlertDialog(
        onDismissRequest = { confirmRestore = false },
        containerColor = SadoColors.Surface,
        title = { Text("Ganti semua data?", color = SadoColors.Text, fontWeight = FontWeight.ExtraBold) },
        text = { Text("Data dalam phone ni akan diganti dengan isi backup. Tak boleh undo.", color = SadoColors.TextMuted) },
        confirmButton = { TextButton({ confirmRestore = false }) { Text("Batal", color = SadoColors.Accent) } },
        dismissButton = {
            TextButton({
                confirmRestore = false; busy = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Backup.restore(context, picked!!, restorePass) }.getOrNull() }
                    busy = false
                    msg = when (r) {
                        is Backup.Result.Ok -> "Berjaya dipulihkan. ${r.photos} gambar."
                        Backup.Result.WrongPassword -> "Kata laluan salah."
                        else -> "Ini bukan fail backup Sado."
                    }
                }
            }) { Text("Ganti", color = SadoColors.Danger) }
        },
    )
}

/** Turn on: pick a folder and a password once; after that a .sado lands there every few days. */
@Composable
private fun AutoBackupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = Store.state.collectAsState().value.settings
    var setup by remember { mutableStateOf(false) }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            folder = uri
        }
    }
    fun folderName(u: Uri) = android.provider.DocumentsContract.getTreeDocumentId(u).substringAfterLast(':').ifBlank { "Folder dipilih" }
    fun runNow() {
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { AutoBackup.run(context) }
            busy = false
            note = when (r) { is AutoBackup.Result.Ok -> "${r.name} disimpan (${r.kb} KB)."; is AutoBackup.Result.Failed -> r.why }
        }
    }

    Card {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto backup", color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (st.autoBackup) "Setiap ${st.backupEveryDays} hari ke ${st.backupFolderName ?: "folder"} · simpan ${AutoBackup.KEEP} terakhir" else "Simpan .sado sendiri ke folder pilihan kau", color = SadoColors.TextMuted, fontSize = 13.sp)
            }
            androidx.compose.material3.Switch(
                st.autoBackup || setup,
                { on ->
                    if (on) setup = true
                    else {
                        setup = false
                        Store.update { it.copy(settings = it.settings.copy(autoBackup = false)) }
                        Secrets.clear(context, AutoBackup.SECRET)
                        AutoBackup.schedule(context)
                    }
                },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = SadoColors.OnAccent, checkedTrackColor = SadoColors.Accent),
            )
        }
        if (st.autoBackup) {
            val last = st.lastAutoBackup?.let { java.time.LocalDate.ofEpochDay(it) }
            Text("Backup terakhir: " + (last?.let { "${it.dayOfMonth} ${monthNames[it.monthValue - 1]} ${it.year}" } ?: "belum ada"), color = SadoColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            st.lastAutoBackupError?.let { Text("Gagal kali terakhir: $it", color = SadoColors.Danger, fontSize = 12.sp) }
            Segmented(listOf("Setiap hari", "3 hari", "Seminggu"), when (st.backupEveryDays) { 1 -> 0; 3 -> 1; else -> 2 }) { i ->
                Store.update { it.copy(settings = it.settings.copy(backupEveryDays = listOf(1, 3, 7)[i])) }
            }
            SecondaryButton(if (busy) "Tengah backup..." else "Backup sekarang", Modifier.fillMaxWidth()) { if (!busy) runNow() }
        } else if (setup) {
            Text("1. Pilih folder. Folder dalam phone (contoh Documents/Sado) paling selamat. Kalau app cloud kau (OneDrive, Nextcloud dan lain-lain) keluar dalam senarai, boleh pilih terus. Google Drive biasanya tak benarkan pilih folder.", color = SadoColors.TextMuted, fontSize = 12.sp)
            SecondaryButton(folder?.let { "Folder: " + folderName(it) } ?: "Pilih folder", Modifier.fillMaxWidth()) { pickFolder.launch(null) }
            Text("2. Kata laluan backup. Disimpan encrypted dalam phone supaya backup boleh jalan sendiri.", color = SadoColors.TextMuted, fontSize = 12.sp)
            OutlinedTextField(pass, { pass = it }, label = { Text("Kata laluan (min 6)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            OutlinedTextField(pass2, { pass2 = it }, label = { Text("Ulang kata laluan") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            PrimaryButton("Hidupkan auto backup", enabled = folder != null && pass.length >= 6 && pass == pass2) {
                val f = folder!!
                Secrets.save(context, AutoBackup.SECRET, pass)
                Store.update { it.copy(settings = it.settings.copy(autoBackup = true, backupFolder = f.toString(), backupFolderName = folderName(f), lastAutoBackup = null, lastAutoBackupError = null)) }
                AutoBackup.schedule(context)
                setup = false; pass = ""; pass2 = ""
                runNow()
            }
        }
        note?.let { Text(it, color = SadoColors.Accent, fontSize = 13.sp) }
    }
}
