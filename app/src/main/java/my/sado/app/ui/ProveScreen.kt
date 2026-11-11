package my.sado.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import my.sado.app.data.FitnessTest
import my.sado.app.data.Progress
import my.sado.app.data.Store
import java.time.LocalDate

/**
 * One-time placement at setup: "Dah biasa workout? Buktikan!" One unbroken set of push-ups, a rest, then a plank.
 * Pass → start at level 4 (Berisi I or Bangkit I). Either way the result is saved as the first fitness test.
 */
@Composable
fun ProveScreen(onDone: () -> Unit, startStep: Int = 0) {
    val s = Store.state.collectAsState().value
    val bar = Progress.proveBar(s.profile)
    val target = Progress.stagesFor(s)[3]
    var step by remember { mutableIntStateOf(startStep) } // 0 intro, 1 push-up, 2 rest, 3 plank, 4 result
    var push by remember { mutableIntStateOf(0) }
    var plank by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var rest by remember { mutableIntStateOf(60) }
    var passed by remember { mutableStateOf(false) }
    LaunchedEffect(running) { while (running) { delay(1000); plank++; if (plank % 10 == 0) Feedback.countdown() } }
    LaunchedEffect(step) { if (step == 2) { rest = 60; while (rest > 0) { delay(1000); rest-- }; Feedback.success() } }

    fun skip() { Store.update { it.copy(proveAsked = true) }; onDone() }
    fun finish() {
        running = false
        passed = Progress.provePassed(s.profile, push, plank)
        Store.update { st ->
            val tested = st.copy(proveAsked = true, tests = st.tests + FitnessTest(LocalDate.now().toEpochDay(), push, plank, null))
            if (passed) Progress.place(tested) else tested
        }
        if (passed) Feedback.levelUp() else Feedback.success()
        my.sado.app.Coach.say(if (passed) my.sado.app.Lines.provePass else my.sado.app.Lines.proveFail)
        step = 4
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                // the bar stays hidden until the result: knowing "25" makes people stop at 25
                0 -> {
                    Title("Dah biasa workout?", "Buktikan! Sekali je, masa setup")
                    Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        Image(painterResource(Progress.stagesFor(s)[0].image), null, Modifier.height(150.dp))
                        Text("→", color = SadoColors.Accent, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 60.dp))
                        Image(painterResource(target.image), null, Modifier.height(190.dp))
                    }
                    Text(
                        "Lulus, ko terus mula sebagai ${target.name} dengan ${target.at} XP, tak payah tunggu ${target.minWeeks} minggu.",
                        color = SadoColors.Text, fontSize = 16.sp, textAlign = TextAlign.Center,
                    )
                    Card {
                        Text("Cara ujian", color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Rule("1", "Push-up sebanyak boleh", "Satu set tanpa henti. Tak boleh rehat tengah set, tak boleh lutut.")
                        Rule("2", "Rehat 1 minit", "Rehat cuma di sini, sebelum plank.")
                        Rule("3", "Plank selama boleh", "Badan lurus dari kepala ke tumit, punggung tak naik atau jatuh.")
                    }
                    Text("Syarat lulus rahsia. Lagi banyak, lagi gerenti lulus!", color = SadoColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Text("Form kena betul: dada hampir cecah lantai, siku tak lebih 45°, badan lurus. Berhenti bila form rosak.", color = SadoColors.TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Text("Jangan tipu diri sendiri. Haha.", color = SadoColors.Streak, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    PrimaryButton("BUKTIKAN!", Modifier.fillMaxWidth()) { step = 1 }
                    SecondaryButton("Skip, mula dari ${Progress.stagesFor(s)[0].name}", Modifier.fillMaxWidth()) { skip() }
                }
                1 -> {
                    Title("Push-up", "1 / 2 · satu set tanpa henti")
                    Text("Letak phone tepi, buat push-up sampai tak larat atau form rosak. Lepas siap, masukkan berapa rep ko dapat.", color = SadoColors.TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    CountEntry(push, "rep", "Push-up") { push = it }
                    Text("Jangan berhenti selagi form cantik. Kira yang betul je.", color = SadoColors.Streak, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    PrimaryButton("Seterusnya: rehat", Modifier.fillMaxWidth(), enabled = push > 0) { step = 2 }
                }
                2 -> {
                    Title("Rehat", "Lepas ni plank, tahan selama boleh")
                    BigCounter("${rest}s", if (rest > 0) "tarik nafas" else "dah boleh mula", rest == 0) {}
                    Text("Push-up: $push. Simpan tenaga untuk plank.", color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    PrimaryButton(if (rest > 0) "Mula plank sekarang" else "Mula plank", Modifier.fillMaxWidth()) { step = 3 }
                }
                3 -> {
                    Title("Plank", "2 / 2 · selama boleh")
                    Text("Tekan bulatan untuk mula. Tekan lagi bila lutut jatuh atau badan tak lurus.", color = SadoColors.TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    BigCounter("${plank}s", if (running) "tekan untuk stop" else if (plank == 0) "tekan untuk mula" else "berhenti", false) {
                        running = !running; Feedback.tap()
                    }
                    if (running || plank > 0) Text(plankCheer(plank), color = SadoColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    PrimaryButton("Siap", Modifier.fillMaxWidth(), enabled = !running && plank > 0) { finish() }
                }
                else -> {
                    if (passed) {
                        Tag("LULUS · ${target.name.uppercase()}", filled = true)
                        Image(painterResource(target.image), null, Modifier.height(240.dp))
                        Text("Terbukti!", color = SadoColors.Text, fontSize = 30.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    } else {
                        Image(painterResource(Progress.stagesFor(s)[0].image), null, Modifier.height(220.dp))
                        Text("Belum kali ni", color = SadoColors.Text, fontSize = 28.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                    }
                    // now the bar can be shown
                    Card {
                        ResultRow("Push-up", push, bar.pushups, "")
                        ResultRow("Plank", plank, bar.plankSec, "s")
                    }
                    Text(
                        if (passed) "Ko mula sebagai ${target.name} dengan ${target.at} XP. Keputusan ni disimpan sebagai ujian pertama."
                        else "Takpe, ni rekod pertama ko. Ujian 4 minggu nanti, kita tengok berapa banyak ko naik.",
                        color = SadoColors.TextMuted, fontSize = 15.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("Tengok plan aku", Modifier.fillMaxWidth(), onClick = onDone)
                }
            }
        }
        if (step == 4 && passed) Confetti()
    }
}

/** Lines that change as the plank goes on; none of them gives away the bar. */
internal fun plankCheer(sec: Int): String = when {
    sec < 15 -> "Badan lurus, perut ketat."
    sec < 30 -> "Bagus, tahan lagi!"
    sec < 45 -> "Jangan berhenti selagi form cantik."
    sec < 60 -> "Lagi lama, lagi gerenti lulus!"
    sec < 90 -> "Ni la beza orang biasa dengan orang terbukti."
    else -> "Gila! Tahan selagi boleh!"
}

@Composable
private fun ResultRow(name: String, got: Int, need: Int, unit: String) {
    val ok = got >= need
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                when { ok -> "lulus"; need - got <= maxOf(2, need / 10) -> "nyaris!"; else -> "kurang ${need - got}$unit" },
                color = if (ok) SadoColors.Accent else SadoColors.Streak, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
        }
        Text("$got$unit", color = if (ok) SadoColors.Accent else SadoColors.Text, fontSize = 26.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        Text(" / perlu $need$unit", color = SadoColors.TextMuted, fontSize = 14.sp)
    }
}

@Composable
private fun Rule(n: String, title: String, sub: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(SadoColors.Accent), contentAlignment = Alignment.Center) {
            Text(n, color = SadoColors.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = SadoColors.TextMuted, fontSize = 12.sp)
        }
    }
}

/**
 * A max-reps count typed in after the set (or nudged with − / +), so nobody has to tap the phone
 * on every rep. Shared by "Buktikan!" and the 4-week test.
 */
@Composable
fun CountEntry(value: Int, unit: String, name: String, best: Boolean = false, onChange: (Int) -> Unit) {
    val c = if (best) SadoColors.Accent else SadoColors.Sky
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StepCircle("−", "Kurang satu $unit") { if (value > 0) { onChange(value - 1); Feedback.tap() } }
        Box(
            Modifier.size(180.dp).clip(CircleShape).background(c.copy(alpha = 0.12f)).border(5.dp, c, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.text.BasicTextField(
                    value = if (value == 0) "" else "$value",
                    onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(3).toIntOrNull() ?: 0) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(color = SadoColors.Text, fontSize = 60.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(SadoColors.Accent),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (value == 0) Text("0", color = SadoColors.TextMuted, fontSize = 60.sp, fontWeight = FontWeight.ExtraBold)
                            inner()
                        }
                    },
                    modifier = Modifier.size(width = 140.dp, height = 80.dp).semantics { contentDescription = "$name, $unit" },
                )
                Text("tekan untuk taip", color = SadoColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        StepCircle("+", "Tambah satu $unit") { onChange(value + 1); Feedback.tap() }
    }
}

@Composable
private fun StepCircle(glyph: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(SadoColors.Surface2)
            .semantics { contentDescription = label }
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = SadoColors.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
internal fun BigCounter(value: String, sub: String, done: Boolean, onTap: () -> Unit) {
    val c = if (done) SadoColors.Accent else SadoColors.Sky
    Box(
        Modifier.size(220.dp).clip(CircleShape).background(c.copy(alpha = 0.12f)).border(5.dp, c, CircleShape)
            .clickable(role = Role.Button, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = SadoColors.Text, fontSize = 60.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = if (done) SadoColors.Accent else SadoColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
