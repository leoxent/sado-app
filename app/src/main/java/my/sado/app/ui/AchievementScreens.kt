package my.sado.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.sado.app.data.Achievement
import my.sado.app.data.Achievements
import my.sado.app.data.Store
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

private fun iconOf(key: String): ImageVector = when (key) {
    "dumbbell" -> SadoIcons.Dumbbell
    "bolt" -> SadoIcons.Bolt
    "timer" -> SadoIcons.Timer
    "flame" -> SadoIcons.Flame
    "heart" -> SadoIcons.Heart
    "flex" -> SadoIcons.Flex
    "camera" -> SadoIcons.Camera
    "steps" -> SadoIcons.Steps
    "watch" -> SadoIcons.Watch
    "edit" -> Icons.Filled.Edit
    else -> Icons.Filled.Star
}

private fun groupColor(group: String) = when (group) {
    "Workout" -> SadoColors.Accent
    "Konsisten" -> SadoColors.Streak
    "Kekuatan" -> SadoColors.Violet
    "Level" -> SadoColors.Sky
    "Badan & makan" -> SadoColors.Pink
    else -> SadoColors.Accent
}

// 1,000 rather than 1000 so goals read at a glance
private fun count(n: Int) = "%,d".format(n)

/** Every milestone by group: unlocked ones in colour, locked ones grey with how far along they are. */
@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    val s = Store.state.collectAsState().value
    val all = Achievements.all(s)
    val open = all.count { it.unlocked }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Achievement", "$open / ${all.size} dah buka", onBack = onBack)
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$open", color = SadoColors.Text, fontSize = 40.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
                Text(" / ${all.size}", color = SadoColors.TextMuted, fontSize = 20.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Star, null, tint = SadoColors.Streak, modifier = Modifier.size(36.dp))
            }
            Bar(open / all.size.toFloat(), height = 8)
            closest(all)?.let { Text("Paling dekat: ${it.title} · ${count(it.progress)} / ${count(it.goal)}", color = SadoColors.TextMuted, fontSize = 13.sp) }
        }
        Achievements.groups.forEach { g ->
            val list = all.filter { it.group == g }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Label(g) }
                Text("${list.count { it.unlocked }} / ${list.size}", color = SadoColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            list.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { Badge(it, Modifier.weight(1f)) }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun closest(all: List<Achievement>) = all.filter { !it.unlocked && it.progress > 0 }.maxByOrNull { it.fraction }

@Composable
private fun Badge(a: Achievement, modifier: Modifier) {
    val c = groupColor(a.group)
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(if (a.unlocked) c.copy(alpha = 0.12f) else SadoColors.Surface)
            .border(1.dp, if (a.unlocked) c.copy(alpha = 0.4f) else SadoColors.Outline, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (a.unlocked) c else SadoColors.Surface2), contentAlignment = Alignment.Center) {
            Icon(if (a.unlocked) iconOf(a.icon) else Icons.Filled.Lock, contentDescription = if (a.unlocked) "Dah buka" else "Belum buka", tint = if (a.unlocked) SadoColors.OnAccent else SadoColors.TextMuted, modifier = Modifier.size(22.dp))
        }
        Text(a.title, color = if (a.unlocked) SadoColors.Text else SadoColors.TextMuted, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(a.desc, color = SadoColors.TextMuted, fontSize = 12.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!a.unlocked) {
            Bar(a.fraction, c, height = 6)
            Text("${count(a.progress.coerceAtMost(a.goal))} / ${count(a.goal)}", color = SadoColors.TextMuted, fontSize = 11.sp)
        } else Tag("Dah buka", c)
    }
}

/** Rekod tab entry: count unlocked and the closest next one. */
@Composable
fun AchievementTeaser(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = Store.state.collectAsState().value
    val all = Achievements.all(s)
    val open = all.count { it.unlocked }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SadoColors.Streak.copy(alpha = 0.12f))
            .border(1.dp, SadoColors.Streak.copy(alpha = 0.3f), RoundedCornerShape(24.dp)).pressable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(SadoColors.Streak), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Star, null, tint = SadoColors.OnAccent, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Achievement · $open / ${all.size}", color = SadoColors.Text, fontSize = 17.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold)
            Text(closest(all)?.let { "Hampir: ${it.title} (${count(it.progress)}/${count(it.goal)})" } ?: "Tengok semua milestone", color = SadoColors.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SadoColors.TextMuted)
    }
}

/** Done screen: badges unlocked by this workout, shown once. */
@Composable
fun NewAchievements(list: List<Achievement>) {
    if (list.isEmpty()) return
    Card {
        Text("Achievement baru!", color = SadoColors.Streak, fontSize = 19.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        list.take(3).forEach { a ->
            val c = groupColor(a.group)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
                    Icon(iconOf(a.icon), null, tint = SadoColors.OnAccent, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(a.title, color = SadoColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(a.desc, color = SadoColors.TextMuted, fontSize = 12.sp)
                }
            }
        }
        if (list.size > 3) Text("+${list.size - 3} lagi dalam tab Rekod", color = SadoColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Pops a badge in from the top of the screen the moment it unlocks anywhere in the app (photo, weigh-in, meal,
 * sport, test, own template...). Stays quiet during a workout; the Done screen celebrates those itself.
 */
@Composable
fun AchievementToast(route: String?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val busy = route != null && (route.startsWith("session") || route == "done")
    var showing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<Achievement>>(emptyList()) }
    var visible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(busy) {
        if (busy) return@LaunchedEffect
        Store.state
            .map { s -> Achievements.fresh(s) }
            .distinctUntilChanged { a, b -> a.map { it.id } == b.map { it.id } }
            .filter { it.isNotEmpty() }
            .collect { list ->
                // mark seen first so it shows once, even if the app closes mid-toast
                Store.update { it.copy(seenAchievements = it.seenAchievements + list.map { a -> a.id }) }
                try {
                    showing = list; visible = true
                    Feedback.achievement()
                    kotlinx.coroutines.delay(4500)
                } finally {
                    visible = false
                }
                kotlinx.coroutines.delay(400)
            }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible && !busy, modifier,
        enter = androidx.compose.animation.slideInVertically { -it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut(),
    ) {
        // the list stays set while it slides out
        AchievementBanner(showing) { visible = false; onOpen() }
    }
}

@Composable
internal fun AchievementBanner(list: List<Achievement>, onClick: () -> Unit) {
    if (list.isEmpty()) return
    val a = list.first()
    val c = groupColor(a.group)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(20.dp)).background(SadoColors.Surface)
            .border(2.dp, c, RoundedCornerShape(20.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
            Icon(iconOf(a.icon), null, tint = SadoColors.OnAccent, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(if (list.size > 1) "${list.size} ACHIEVEMENT BARU" else "ACHIEVEMENT BARU", color = SadoColors.Streak, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text(
                if (list.size > 1) list.joinToString(" · ") { it.title } else a.title,
                color = SadoColors.Text, fontSize = 16.sp, fontFamily = Display, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(if (list.size > 1) "Tekan untuk tengok semua" else a.desc, color = SadoColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Buka achievement", tint = SadoColors.TextMuted)
    }
}
