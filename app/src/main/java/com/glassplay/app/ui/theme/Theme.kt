package com.glassplay.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Accent = Color(0xFF0A84FF)
val AccentPurple = Color(0xFF5E5CE6)
val Danger = Color(0xFFFF453A)
val StarYellow = Color(0xFFFFD60A)

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Every colour that changes between dark and light mode. */
@Immutable
data class Palette(
    val isDark: Boolean,
    val background: Color,
    /** Text and icons. */
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    /** Text on an [ink]-coloured fill, such as a primary button. */
    val inverse: Color,
    val sheet: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val edgeBright: Color,
    val edgeDim: Color,
    /** Fill of bars and floating controls. */
    val bar: Color,
    val aurora: List<Color>,
)

val DarkPalette = Palette(
    isDark = true,
    background = Color(0xFF06070B),
    ink = Color.White,
    inkSecondary = Color.White.copy(alpha = 0.64f),
    inkTertiary = Color.White.copy(alpha = 0.40f),
    inverse = Color.Black,
    sheet = Color(0xFF16171E),
    glassTop = Color.White.copy(alpha = 0.15f),
    glassBottom = Color.White.copy(alpha = 0.06f),
    edgeBright = Color.White.copy(alpha = 0.40f),
    edgeDim = Color.White.copy(alpha = 0.05f),
    bar = Color(0xFF1A1B22).copy(alpha = 0.72f),
    aurora = listOf(Color(0xFF2B3CFF).copy(alpha = 0.30f), Color(0xFFA02CFF).copy(alpha = 0.20f), Color(0xFF00C2A8).copy(alpha = 0.14f)),
)

val LightPalette = Palette(
    isDark = false,
    background = Color(0xFFEFF1F7),
    ink = Color(0xFF0B0C10),
    inkSecondary = Color(0xFF0B0C10).copy(alpha = 0.62f),
    inkTertiary = Color(0xFF0B0C10).copy(alpha = 0.42f),
    inverse = Color.White,
    sheet = Color(0xFFF7F8FB),
    glassTop = Color.White.copy(alpha = 0.78f),
    glassBottom = Color.White.copy(alpha = 0.46f),
    edgeBright = Color.White,
    edgeDim = Color(0xFF0B0C10).copy(alpha = 0.06f),
    bar = Color.White.copy(alpha = 0.70f),
    aurora = listOf(Color(0xFF6E8BFF).copy(alpha = 0.38f), Color(0xFFFF7EC8).copy(alpha = 0.30f), Color(0xFF36D6C3).copy(alpha = 0.28f)),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

val Background: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.background
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.inkSecondary
val TextTertiary: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.inkTertiary
val SheetColor: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.sheet
/** Main text and icon colour. */
val Ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
/** Text on an [Ink] fill. */
val InkInverse: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.inverse

/** Whether the app is showing its dark palette, following the setting and the phone. */
@Composable
fun rememberDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

/** Shows [content] in the dark palette whatever the setting, for artwork-led screens. */
@Composable
fun ForceDark(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides DarkPalette, content = content)
}

object GlassType {
    val largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, lineHeight = 40.sp)
    val title1 = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, lineHeight = 34.sp)
    val title2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, lineHeight = 28.sp)
    val title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 25.sp)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 22.sp)
    val body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)
    val callout = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
    val subhead = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 19.sp)
    val footnote = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp)
    val caption = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp, lineHeight = 14.sp)
}

@Composable
fun GlassTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    val colors = if (dark) {
        darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            secondary = AccentPurple,
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.sheet,
            onSurface = palette.ink,
            surfaceVariant = Color(0xFF22232B),
            onSurfaceVariant = palette.inkSecondary,
        )
    } else {
        lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            secondary = AccentPurple,
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.sheet,
            onSurface = palette.ink,
            surfaceVariant = Color(0xFFE4E6EE),
            onSurfaceVariant = palette.inkSecondary,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

/** Frosted glass: translucent fill, a soft sheen and a bright specular edge. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(22.dp), strength: Float = 1f): Modifier = composed {
    val p = LocalPalette.current
    this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(p.glassTop.copy(alpha = (p.glassTop.alpha * strength).coerceAtMost(1f)), p.glassBottom.copy(alpha = (p.glassBottom.alpha * strength).coerceAtMost(1f)))
            )
        )
        .border(1.dp, Brush.linearGradient(listOf(p.edgeBright, p.edgeDim, p.edgeBright.copy(alpha = p.edgeBright.alpha * 0.5f))), shape)
}

/** Denser glass for bars and controls that sit over busy content. */
fun Modifier.glassDark(shape: Shape = RoundedCornerShape(22.dp)): Modifier = composed {
    val p = LocalPalette.current
    this
        .clip(shape)
        .background(p.bar)
        .background(Brush.verticalGradient(listOf(p.glassTop.copy(alpha = p.glassTop.alpha * 0.6f), Color.Transparent)))
        .border(1.dp, Brush.linearGradient(listOf(p.edgeBright.copy(alpha = p.edgeBright.alpha * 0.8f), p.edgeDim, p.edgeBright.copy(alpha = p.edgeBright.alpha * 0.35f))), shape)
}

/** Slowly drifting colour light behind the glass, like the iOS wallpaper glow. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "phase",
    )
    val palette = LocalPalette.current
    Canvas(modifier.fillMaxSize()) {
        drawRect(palette.background)
        val w = size.width
        val h = size.height
        fun blob(color: Color, x: Float, y: Float, r: Float) {
            drawCircle(
                brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(x, y), radius = r),
                radius = r,
                center = Offset(x, y),
            )
        }
        blob(palette.aurora[0], w * (0.10f + 0.30f * phase), h * 0.10f, w * 0.95f)
        blob(palette.aurora[1], w * (0.95f - 0.35f * phase), h * (0.42f + 0.10f * phase), w * 0.85f)
        blob(palette.aurora[2], w * (0.25f + 0.25f * phase), h * (0.92f - 0.12f * phase), w * 0.90f)
    }
}
