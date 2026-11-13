package my.sado.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.sado.app.VoiceClips
import my.sado.app.data.Secrets
import my.sado.app.data.Store

private sealed interface ReportState {
    data object Idle : ReportState
    data object Busy : ReportState
    data object Done : ReportState
    data class Failed(val message: String) : ReportState
}

/**
 * Small pill that pops up when a line played with the phone voice. Tapping it generates that line
 * in the active AI voice and plays it. Hidden while ElevenLabs reports the credit as used up.
 */
@Composable
fun VoiceReportChip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val parts by VoiceClips.missing.collectAsState()
    val line = parts?.takeIf { it.isNotEmpty() }?.joinToString(" ") { it.replace(Regex("^\\[[^\\]]+]\\s*"), "") }
    val chosen = Store.state.collectAsState().value.settings.voiceId
    val voiceId = VoiceClips.missingVoice ?: chosen
    var state by remember { mutableStateOf<ReportState>(ReportState.Idle) }

    // a new line resets the chip; an untouched chip goes away by itself after 8 s
    LaunchedEffect(line) {
        state = ReportState.Idle
        if (line != null) { delay(8000); if (state == ReportState.Idle) VoiceClips.missing.value = null }
    }
    LaunchedEffect(state) {
        when (state) {
            ReportState.Done -> { delay(2000); VoiceClips.missing.value = null }
            is ReportState.Failed -> { delay(4000); VoiceClips.missing.value = null }
            else -> Unit
        }
    }

    AnimatedVisibility(
        visible = line != null && (!VoiceClips.creditOut || state is ReportState.Failed),
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        val s = state
        val color = when (s) { is ReportState.Failed -> SadoColors.Streak; ReportState.Done -> SadoColors.Accent; else -> SadoColors.Violet }
        Row(
            Modifier.widthIn(max = 360.dp).clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface2)
                .border(2.dp, color.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .pressable(enabled = s == ReportState.Idle) {
                    val texts = parts?.takeIf { it.isNotEmpty() } ?: return@pressable
                    val key = Secrets.load(context, "elevenlabs") ?: return@pressable
                    state = ReportState.Busy
                    scope.launch {
                        val error = withContext(Dispatchers.IO) {
                            if (texts.size > 1 && Store.state.value.settings.voiceBatch) VoiceClips.generateBatch(context, key, voiceId, texts)
                            else if (texts.size > 1) texts.firstNotNullOfOrNull { VoiceClips.generate(context, key, voiceId, it, steady = true) }
                            else VoiceClips.generate(context, key, voiceId, texts.first(), steady = texts.first() in my.sado.app.Coach.partLines())
                        }
                        state = if (error == null) { VoiceClips.playChain(context, voiceId, texts); ReportState.Done } else ReportState.Failed(error)
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
                Icon(SadoIcons.Mic, contentDescription = null, tint = SadoColors.OnAccent, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.widthIn(max = 280.dp)) {
                Text(
                    when (s) {
                        ReportState.Idle -> "Suara phone · tekan untuk generate AI"
                        ReportState.Busy -> "Tengah generate…"
                        ReportState.Done -> "Done! Lepas ni guna suara AI"
                        is ReportState.Failed -> s.message
                    },
                    color = SadoColors.Text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (s == ReportState.Idle) Text(
                    line.orEmpty(),
                    color = SadoColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
