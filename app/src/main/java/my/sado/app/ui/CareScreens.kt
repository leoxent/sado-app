package my.sado.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.sado.app.data.Care
import my.sado.app.data.HealthCheck
import my.sado.app.data.Store
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Shown instead of Home when the saved age is under 18, say from an older version or a restored backup. */
@Composable
fun AgeGateScreen(onFixAge: () -> Unit, onSettings: () -> Unit, onAdult: () -> Unit) {
    val s = Store.state.collectAsState().value
    LaunchedEffect(s.profile?.age) { if (!Care.underAge(s.profile)) onAdult() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Sado untuk umur ${Care.MIN_AGE}+", color = SadoColors.Text, fontSize = 34.sp, lineHeight = 36.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        Text(AGE_WHY, color = SadoColors.TextMuted, fontSize = 15.sp, lineHeight = 22.sp)
        Text("Kalau umur dalam profil tersilap, betulkan kat sini. Data kau masih selamat dalam phone.", color = SadoColors.TextMuted, fontSize = 15.sp, lineHeight = 22.sp)
        PrimaryButton("Betulkan umur", onClick = onFixAge)
        SecondaryButton("Setting, backup dan data", onClick = onSettings)
    }
}

const val AGE_WHY = "Sasaran kalori dan latihan dalam app ni dikira untuk badan dewasa. Badan yang tengah membesar tak patut diet. " +
    "Kalau kau bawah ${Care.MIN_AGE} tahun dan nak mula bersenam, bincang dengan doktor, ibu bapa atau cikgu PJ."

private class HealthQ(val key: String, val text: String, val hint: String? = null)

private val healthQs = listOf(
    HealthQ("heart", "Doktor pernah kata kau ada sakit jantung atau darah tinggi?"),
    HealthQ("chest", "Dada kau sakit masa rehat, buat kerja harian, atau bersenam?"),
    HealthQ("dizzy", "Dalam 12 bulan lepas, pernah pening sampai hilang imbangan, atau pengsan?", "Jawab tak kalau pening tu sebab termengah masa senaman berat."),
    HealthQ("chronic", "Ada penyakit kronik lain, atau makan ubat untuk penyakit kronik?", "Contohnya kencing manis, asma atau buah pinggang."),
    HealthQ("joint", "Ada masalah tulang, sendi atau otot yang boleh jadi teruk kalau lebih aktif?"),
    HealthQ("doctorOnly", "Doktor pernah suruh bersenam dengan pengawasan perubatan je?"),
    HealthQ("eating", "Pernah ada, atau rasa ada, masalah dengan makan?", "Contohnya anoreksia, bulimia, atau rasa bersalah dan takut pada makanan."),
)
private val womenQs = listOf(
    HealthQ("pregnant", "Tengah mengandung?"),
    HealthQ("nursing", "Tengah menyusu?"),
)

@Composable
fun HealthCheckScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val s = Store.state.collectAsState().value
    val female = s.profile?.sex == 1
    val qs = if (female) healthQs + womenQs else healthQs
    val saved = s.health
    val picked = remember {
        mutableStateMapOf<String, Boolean>().apply {
            if (saved != null) {
                put("heart", saved.heart); put("chest", saved.chest); put("dizzy", saved.dizzy); put("chronic", saved.chronic)
                put("joint", saved.joint); put("doctorOnly", saved.doctorOnly); put("eating", saved.eating)
                if (female) { put("pregnant", saved.pregnant); put("nursing", s.nursing) }
            }
        }
    }
    var result by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (result) {
            val h = s.health
            Title("Hasil kau", "Panduan awal, bukan diagnosis", onBack = { result = false })
            if (h != null) Care.advice(h, s.profile?.age ?: 0, s.nursing).forEach { (level, text) ->
                when (level) {
                    Care.Level.Stop -> Callout("Berhenti dulu", text, SadoColors.Danger)
                    Care.Level.Ask -> Callout("Tanya doktor", text, SadoColors.Streak)
                    Care.Level.Info -> Callout("Nota", text, SadoColors.Sky)
                    Care.Level.Clear -> Callout("Boleh mula", text, SadoColors.Accent)
                }
            }
            HealthNote()
            PrimaryButton("Aku faham, teruskan", onClick = onDone)
            return@Column
        }
        Title("Soalan kesihatan", "Disimpan dalam phone je", onBack = onBack)
        Text("Berpandukan soal selidik PAR-Q+. App tak halang kau, tapi jawapan ni bagitau kalau elok jumpa doktor dulu.", color = SadoColors.TextMuted, fontSize = 13.sp)
        qs.forEach { q ->
            Card {
                Text(q.text, color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                q.hint?.let { Text(it, color = SadoColors.TextMuted, fontSize = 12.sp) }
                Segmented(listOf("Tak", "Ya"), when (picked[q.key]) { false -> 0; true -> 1; null -> -1 }) { picked[q.key] = it == 1 }
            }
        }
        val ready = qs.all { it.key in picked }
        if (!ready) Text("Jawab semua soalan untuk teruskan.", color = SadoColors.Streak, fontSize = 13.sp)
        PrimaryButton("Tengok hasil", enabled = ready) {
            val day = LocalDate.now().toEpochDay()
            val yes = { k: String -> picked[k] == true }
            Store.update {
                it.copy(
                    health = HealthCheck(day, yes("heart"), yes("chest"), yes("dizzy"), yes("chronic"), yes("joint"), yes("doctorOnly"), yes("pregnant"), yes("eating")),
                    nursing = yes("nursing"),
                )
            }
            result = true
        }
    }
}

/** The note everyone sees before training, whatever the body or the answers. */
@Composable
fun HealthNote(modifier: Modifier = Modifier) = Callout(
    "Nota kesihatan",
    "Kalau ada darah tinggi, kencing manis, sakit jantung, sakit dada bila bergerak, atau sakit sendi, jumpa doktor dulu sebelum mula. " +
        "Mula perlahan: kalau pening, sesak nafas teruk atau sakit dada, berhenti terus.",
    SadoColors.Streak, modifier,
)

private val dayFormat = DateTimeFormatter.ofPattern("d/M/yyyy")

@Composable
fun RestScreen(onBack: () -> Unit) {
    val s = Store.state.collectAsState().value
    val today = LocalDate.now().toEpochDay()
    val cur = Care.active(s, today)
    var pick by remember { mutableStateOf<String?>(null) }
    var weeks by remember { mutableIntStateOf(1) }
    var nursing by remember { mutableIntStateOf(if (s.nursing) 1 else -1) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Mod rehat", "Hidup kadang-kadang lain macam", onBack = onBack)
        if (cur != null) {
            val day = (today - cur.from).toInt() + 1
            Card {
                Text(Care.label(cur.reason), color = SadoColors.Accent, fontSize = 26.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Text("Hari ke-$day" + (cur.until?.let { ", sampai ${LocalDate.ofEpochDay(it).format(dayFormat)}" } ?: ""), color = SadoColors.TextMuted, fontSize = 13.sp)
                Text("Level dan streak tak turun, dan watak tak jadi letih. Semakan mingguan tak ubah kalori.", color = SadoColors.Text, fontSize = 14.sp)
            }
            Label("Apa nak buat")
            Card { Care.tips(cur, today).forEach { Text(it, color = SadoColors.Text, fontSize = 14.sp, lineHeight = 20.sp) } }
            if (Care.noCut(s, today)) Callout("Kalori", "Defisit kalori dimatikan masa ni. Sasaran kau tahap kekal berat.", SadoColors.Sky)
            PrimaryButton(if (cur.reason == Care.SICK) "Dah sihat, sambung balik" else "Tamatkan mod rehat") { Store.update { Care.end(it, today) } }
            return@Column
        }
        Text("Pilih sebab. Level dan streak tak turun selagi mod ni hidup. Workout masih boleh bila-bila.", color = SadoColors.TextMuted, fontSize = 14.sp)
        val options = listOfNotNull(
            Triple(Care.SICK, "Sakit", "Sampai kau kata dah sihat"),
            Triple(Care.EXAM, "Exam", "Pilih 1 hingga 4 minggu"),
            Triple(Care.BIRTH, "Lepas bersalin", "12 minggu, dengan panduan ikut minggu").takeIf { s.profile?.sex == 1 },
            Triple(Care.RAMADAN, "Ramadan", s.ramadan?.takeIf { today in it.start..it.end }?.let { "Sampai ${LocalDate.ofEpochDay(it.end).format(dayFormat)}, ikut takwim JAKIM" } ?: "30 hari. Boleh tamatkan bila-bila"),
        )
        options.forEach { (key, label, sub) -> OptionRow(label, sub, pick == key) { pick = key } }
        if (pick == Care.EXAM) {
            Label("Berapa lama")
            Segmented(listOf("1 mg", "2 mg", "3 mg", "4 mg"), weeks - 1) { weeks = it + 1 }
        }
        if (pick == Care.BIRTH) {
            Label("Tengah menyusu?")
            Segmented(listOf("Tak", "Ya"), nursing) { nursing = it }
        }
        val ready = pick != null && (pick != Care.BIRTH || nursing >= 0)
        PrimaryButton("Hidupkan mod rehat", enabled = ready) {
            val reason = pick ?: return@PrimaryButton
            Store.update {
                val on = Care.start(it, reason, today, if (reason == Care.EXAM) weeks * 7 else null)
                val asked = if (reason == Care.RAMADAN) on.copy(ramadanAsked = on.ramadan?.year ?: on.ramadanAsked) else on
                if (reason == Care.BIRTH) asked.copy(nursing = nursing == 1) else asked
            }
        }
    }
}

/** Home's offer when JAKIM's calendar says Ramadan has begun. */
@Composable
fun RamadanOfferCard(modifier: Modifier = Modifier) {
    Card(modifier) {
        Text("Ramadan dah mula", color = SadoColors.Accent, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        Text("Nak hidupkan mod rehat? Level dan streak tak turun walaupun kurang sesi, dan pet tak merungut lapar siang hari.", color = SadoColors.TextMuted, fontSize = 13.sp)
        val today = LocalDate.now().toEpochDay()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Tak perlu", Modifier.weight(1f)) { Store.update { it.copy(ramadanAsked = it.ramadan?.year ?: 0) } }
            PrimaryButton("Hidupkan", Modifier.weight(1f)) { Store.update { Care.start(it, Care.RAMADAN, today).copy(ramadanAsked = it.ramadan?.year ?: 0) } }
        }
    }
}
