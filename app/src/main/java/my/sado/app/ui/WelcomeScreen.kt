package my.sado.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.sado.app.R

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SADO", color = SadoColors.Accent, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp, modifier = Modifier.appear(0))
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(300.dp).background(SadoColors.HeroGlow).appear(1), contentAlignment = Alignment.Center) {
            Floating {
                Image(
                    painter = painterResource(R.drawable.pose1),
                    contentDescription = "Character kau berdiri dan senyum",
                    modifier = Modifier.height(280.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("WORKOUT,\nDIA MAKIN SADO.",color = SadoColors.Text, fontSize = 40.sp, lineHeight = 40.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.appear(2))
        Spacer(Modifier.height(8.dp))
        Text(
            "Skip lama-lama, dia jadi lembik balik. Jaga dia macam kau jaga diri sendiri.",
            color = SadoColors.TextMuted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            val stages = listOf(R.drawable.tier1, R.drawable.tier4, R.drawable.tier7, R.drawable.tier10, R.drawable.tier13)
            stages.forEachIndexed { i, res ->
                Image(
                    painter = painterResource(res),
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = (30 + i * 4).dp, height = (48 + i * 6).dp)
                        .alpha(0.6f + i * 0.1f),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("JOM BINA PLAN AKU", onClick = onStart)
        Spacer(Modifier.height(12.dp))
        Text("Untuk umur 18 tahun ke atas. Tak perlu akaun, semua data dalam phone kau.", color = SadoColors.TextMuted, fontSize = 13.sp)
    }
}
