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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Background = Color(0xFF06070B)
val Accent = Color(0xFF0A84FF)
val AccentPurple = Color(0xFF5E5CE6)
val TextPrimary = Color.White
val TextSecondary = Color.White.copy(alpha = 0.64f)
val TextTertiary = Color.White.copy(alpha = 0.40f)
val Danger = Color(0xFFFF453A)
val StarYellow = Color(0xFFFFD60A)
val SheetColor = Color(0xFF16171E)

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
fun GlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            secondary = AccentPurple,
            background = Background,
            onBackground = Color.White,
            surface = SheetColor,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF22232B),
            onSurfaceVariant = TextSecondary,
        ),
        content = content,
    )
}

/** Frosted, light glass: translucent fill, soft sheen and a bright hairline edge. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(22.dp), strength: Float = 1f): Modifier =
    this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.15f * strength), Color.White.copy(alpha = 0.06f * strength))
            )
        )
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.20f))
            ),
            shape,
        )

/** Darker glass for bars and sheets that sit over busy content. */
fun Modifier.glassDark(shape: Shape = RoundedCornerShape(22.dp)): Modifier =
    this
        .clip(shape)
        .background(Color(0xFF1A1B22).copy(alpha = 0.80f))
        .background(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f)))
        )
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.14f))
            ),
            shape,
        )

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
    Canvas(modifier.fillMaxSize()) {
        drawRect(Background)
        val w = size.width
        val h = size.height
        fun blob(color: Color, x: Float, y: Float, r: Float) {
            drawCircle(
                brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(x, y), radius = r),
                radius = r,
                center = Offset(x, y),
            )
        }
        blob(Color(0xFF2B3CFF).copy(alpha = 0.30f), w * (0.10f + 0.30f * phase), h * 0.10f, w * 0.95f)
        blob(Color(0xFFA02CFF).copy(alpha = 0.20f), w * (0.95f - 0.35f * phase), h * (0.42f + 0.10f * phase), w * 0.85f)
        blob(Color(0xFF00C2A8).copy(alpha = 0.14f), w * (0.25f + 0.25f * phase), h * (0.92f - 0.12f * phase), w * 0.90f)
    }
}
