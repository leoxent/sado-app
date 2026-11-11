package my.sado.app.ui

import android.graphics.Typeface
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// "Vibrant & block-based": lime stays the brand colour, and four playful block colours tint tiles so each
// stat has its own identity. Dark is a deep base with bright tints; light swaps in deeper shades of the same
// hues so text still keeps 4.5:1+ on white. Reading a colour reads [dark], so screens recolour on a switch.
object SadoColors {
    var dark by mutableStateOf(true)
    private fun pick(d: Long, l: Long) = Color(if (dark) d else l)

    val Bg get() = pick(0xFF0B0C10, 0xFFF3F4EE)
    val Surface get() = pick(0xFF16181F, 0xFFFFFFFF)
    val Surface2 get() = pick(0xFF20232C, 0xFFEBEDE5)
    val Track get() = pick(0xFF2A2E38, 0xFFDCDFD4)
    val Outline get() = pick(0x14FFFFFF, 0x1A000000)
    val Text get() = pick(0xFFF6F7F9, 0xFF14161B)
    val TextMuted get() = pick(0xB3F6F7F9, 0xFF5C6170)
    val Accent get() = pick(0xFFC8F04A, 0xFF4F7A00)
    val AccentDeep get() = pick(0xFF8DB12A, 0xFF35540A) // the "3D" underside of lime buttons
    val OnAccent get() = pick(0xFF0B0C10, 0xFFFFFFFF)
    val Deep get() = pick(0xFF0F1116, 0xFFD3D6CB) // the underside of plain buttons
    val MuscleOff get() = pick(0xFF3A3F4A, 0xFFCDD1C6)
    val Streak get() = pick(0xFFFF9D5C, 0xFFC2540A)
    val Violet get() = pick(0xFFA594FF, 0xFF6A55D9)
    val Sky get() = pick(0xFF5CD6FF, 0xFF0A78A8)
    val Pink get() = pick(0xFFFF7AB6, 0xFFC2307A)
    val Danger get() = pick(0xFFFF8F8F, 0xFFC62828)

    val XpGradient get() = Brush.horizontalGradient(listOf(Accent, pick(0xFF7BE0B0, 0xFF2FA37A), Sky))
    val HeroGlow get() = Brush.radialGradient(listOf(Accent.copy(alpha = if (dark) 0.28f else 0.18f), Color.Transparent))
}

// Condensed, heavy display face for titles and big numbers: sporty without bundling a font file
val Display: FontFamily = FontFamily(Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL), 800, false))

/** True when the user turned animations off in Android settings; entrance and idle motion are skipped. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun SadoTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduce = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    // theme setting: 0 gelap (default), 1 cerah, 2 ikut phone
    val theme = my.sado.app.data.Store.state.collectAsState().value.settings.theme
    val isDark = when (theme) { 1 -> false; 2 -> isSystemInDarkTheme(); else -> true }
    if (SadoColors.dark != isDark) SadoColors.dark = isDark
    // status and nav bar icons follow the app theme, not the phone's
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).run { isAppearanceLightStatusBars = !isDark; isAppearanceLightNavigationBars = !isDark }
        }
    }
    val base = Typography()
    val display = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold)
    androidx.compose.runtime.CompositionLocalProvider(LocalReduceMotion provides reduce) {
        MaterialTheme(
            colorScheme = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
                primary = SadoColors.Accent,
                onPrimary = SadoColors.OnAccent,
                secondary = SadoColors.Violet,
                tertiary = SadoColors.Sky,
                background = SadoColors.Bg,
                surface = SadoColors.Surface,
                surfaceVariant = SadoColors.Surface2,
                onBackground = SadoColors.Text,
                onSurface = SadoColors.Text,
                onSurfaceVariant = SadoColors.TextMuted,
                outline = SadoColors.Track,
                outlineVariant = SadoColors.Track,
                surfaceContainerHighest = SadoColors.Track,
                surfaceContainerHigh = SadoColors.Surface2,
                surfaceContainer = SadoColors.Surface2,
                surfaceContainerLow = SadoColors.Surface,
                surfaceContainerLowest = SadoColors.Surface,
                error = SadoColors.Danger,
            ),
            typography = base.copy(
                displayLarge = base.displayLarge.merge(display),
                headlineLarge = base.headlineLarge.merge(display),
                headlineMedium = base.headlineMedium.merge(display),
                titleLarge = base.titleLarge.merge(display).copy(fontSize = 24.sp),
            ),
            content = content,
        )
    }
}
