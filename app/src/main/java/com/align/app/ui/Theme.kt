package com.align.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.align.app.R

/**
 * Align palette. Primary is #FF2541; every gradient in the app only uses shades of that one
 * colour. Buttons are limited to white / black / #FF2541 / green / red.
 */
object FG {
    val Primary = Color(0xFFFF2541)
    val PrimaryLight = Color(0xFFFF5C73)
    val PrimaryPale = Color(0xFFFFA3AF)
    val PrimarySoft = Color(0xFFFFE6EA)
    val PrimaryDark = Color(0xFFC4112D)
    val PrimaryDeep = Color(0xFF8E0B20)

    val Green = Color(0xFF22C55E)
    val Red = Color(0xFFE11D2E)

    /** Pure colours (text on red, white illustration panels, ...). They never change with the theme. */
    val Black = Color(0xFF0B0B0D)
    val White = Color(0xFFFFFFFF)
    val Neutral = Color(0xFF3A3A40)

    // ---- theme-aware tokens: reading them inside composables / draw lambdas tracks the theme state ----
    private var dark by mutableStateOf(false)
    val isDark: Boolean get() = dark
    fun applyDark(value: Boolean) {
        if (dark != value) dark = value
    }

    /** Page background. */
    val Bg: Color get() = if (dark) Color(0xFF0E0E11) else Color(0xFFFFFFFF)
    /** Cards / grey containers. */
    val Surface: Color get() = if (dark) Color(0xFF1B1B20) else Color(0xFFF6F6F8)
    /** Tracks, dividers, empty bars. */
    val Track: Color get() = if (dark) Color(0xFF2C2C34) else Color(0xFFECECF0)
    val TextSecondary: Color get() = if (dark) Color(0xFFA3A3AD) else Color(0xFF6E6E78)
    /** Main text colour. */
    val Ink: Color get() = if (dark) Color(0xFFF4F4F6) else Color(0xFF0B0B0D)
    /** "Black" pills / buttons: near-white in dark mode. */
    val Strong: Color get() = if (dark) Color(0xFFF4F4F6) else Color(0xFF0B0B0D)
    val OnStrong: Color get() = if (dark) Color(0xFF0B0B0D) else Color(0xFFFFFFFF)
    val NavBg: Color get() = if (dark) Color(0xFF24242B) else Color(0xFFFFFFFF)

    /** Hero / header gradient: light -> primary -> dark shade of the same red. */
    val HeroBrush: Brush = Brush.linearGradient(listOf(PrimaryLight, Primary, PrimaryDark))

    val BarBrush: Brush = Brush.verticalGradient(listOf(PrimaryLight, Primary))

    val SoftBarBrush: Brush = Brush.verticalGradient(listOf(PrimaryPale, PrimarySoft))

    val TrackBrush: Brush get() = Brush.verticalGradient(listOf(Track, Track))

    /** Donut slice colours - shades of the primary, then a neutral for "others". */
    val SliceColors = listOf(Primary, PrimaryDark, PrimaryLight, PrimaryDeep, PrimaryPale)
}

/** Custom bold typeface (res/font/bold_font.otf) used for the logo and every bold / semi-bold text. */
val BoldFont = FontFamily(
    Font(R.font.bold_font, FontWeight.SemiBold),
    Font(R.font.bold_font, FontWeight.Bold),
    Font(R.font.bold_font, FontWeight.ExtraBold)
)

private fun fgColors() = if (FG.isDark) {
    darkColorScheme(
        primary = FG.Primary, onPrimary = FG.White, secondary = FG.Strong, onSecondary = FG.OnStrong,
        background = FG.Bg, onBackground = FG.Ink, surface = FG.Bg, onSurface = FG.Ink,
        surfaceVariant = FG.Surface, onSurfaceVariant = FG.TextSecondary,
        error = FG.Red, onError = FG.White, outline = FG.Track
    )
} else {
    lightColorScheme(
        primary = FG.Primary, onPrimary = FG.White, secondary = FG.Black, onSecondary = FG.White,
        background = FG.Bg, onBackground = FG.Ink, surface = FG.Bg, onSurface = FG.Ink,
        surfaceVariant = FG.Surface, onSurfaceVariant = FG.TextSecondary,
        error = FG.Red, onError = FG.White, outline = FG.Track
    )
}

private val FgTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontFamily = BoldFont, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontFamily = BoldFont, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontFamily = BoldFont, fontWeight = FontWeight.SemiBold)
)

/** [themeMode]: 0 = follow the system, 1 = light, 2 = dark. */
@Composable
fun AlignTheme(themeMode: Int = 0, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    FG.applyDark(dark)
    MaterialTheme(
        colorScheme = fgColors(),
        typography = FgTypography,
        content = content
    )
}
