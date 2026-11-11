package my.sado.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.sado.app.data.Backgrounds
import my.sado.app.data.Store

/** Settings: the backdrop of the character's card on Home. Free to change any time, "Takde" included. */
@Composable
fun BackgroundCard(selected: String?) {
    Card {
        Text("Background Home", color = SadoColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Text("Latar belakang kad watak. Siang atau malam ikut tema.", color = SadoColors.TextMuted, fontSize = 13.sp)
        val options = listOf<Backgrounds.Bg?>(null) + Backgrounds.all
        options.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { bg ->
                    BackgroundOption(bg, bg?.id == selected, Modifier.weight(1f)) {
                        Store.update { it.copy(settings = it.settings.copy(background = bg?.id)) }
                    }
                }
                // keeps the last row's tiles the same size as the others
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BackgroundOption(bg: Backgrounds.Bg?, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.pressable(role = Role.RadioButton, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(SadoColors.Surface2)
                .border(if (chosen) 3.dp else 1.dp, if (chosen) SadoColors.Accent else SadoColors.Outline, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (bg == null) Icon(Icons.Filled.Close, null, tint = SadoColors.TextMuted)
            else Image(
                painterResource(if (SadoColors.dark) bg.night else bg.day), null,
                Modifier.matchParentSize().clip(shape), contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter,
            )
            if (chosen) Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(SadoColors.Accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, "Dipilih", tint = SadoColors.OnAccent, modifier = Modifier.size(16.dp)) }
        }
        Text(
            bg?.name ?: "Takde", color = if (chosen) SadoColors.Accent else SadoColors.TextMuted,
            fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
        )
    }
}
