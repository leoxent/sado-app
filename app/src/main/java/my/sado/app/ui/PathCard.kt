package my.sado.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.sado.app.data.Progress
import my.sado.app.data.Store

/** Settings card: which starting character the user follows, Kurus → Berisi or Gempal → Bangkit. */
@Composable
fun PathCard() {
    val s = Store.state.collectAsState().value
    val chosen = s.settings.bodyPath
    val heavy = Progress.heavyPath(s)
    fun pick(v: String?) = Store.update { it.copy(settings = it.settings.copy(bodyPath = v)) }
    Card {
        Text("Watak permulaan", color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Text("Pilih badan yang paling dekat dengan ko masa mula. Dua-dua sampai Fit pada minggu ke-10.", color = SadoColors.TextMuted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PathOption(Modifier.weight(1f), Progress.stages[0].image, "Kurus", "→ Berisi → Fit", !heavy) { pick("kurus") }
            PathOption(Modifier.weight(1f), Progress.heavyStages[0].image, "Gempal", "→ Bangkit → Fit", heavy) { pick("gempal") }
        }
        val note = if (heavy) " Gempal: awal lebih lama, lepas Bangkit lebih laju. Turun 5% berat terus buka Bangkit I." else ""
        Text((if (chosen == null) "Auto: dipilih ikut jawapan planner, pinggang dan BMI." else "Ko pilih sendiri.") + note, color = SadoColors.TextMuted, fontSize = 12.sp)
        if (chosen != null) Text(
            "Balik ke auto", color = SadoColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.pressable { pick(null) }.padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun PathOption(modifier: Modifier, image: Int, title: String, sub: String, on: Boolean, onClick: () -> Unit) {
    Column(
        modifier
            .pressable(role = Role.RadioButton, onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(if (on) SadoColors.Accent.copy(alpha = 0.12f) else SadoColors.Surface2)
            .border(2.dp, if (on) SadoColors.Accent else Color.Transparent, RoundedCornerShape(18.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.height(110.dp), contentAlignment = Alignment.BottomCenter) {
            Image(painterResource(image), contentDescription = null, modifier = Modifier.height(110.dp))
        }
        Text(title, color = if (on) SadoColors.Accent else SadoColors.Text, fontSize = 15.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
        Text(sub, color = SadoColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
