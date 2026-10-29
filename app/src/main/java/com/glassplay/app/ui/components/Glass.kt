package com.glassplay.app.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.glassplay.app.ui.theme.Accent
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.Ink
import com.glassplay.app.ui.theme.InkInverse
import com.glassplay.app.ui.theme.LocalPalette
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.TextTertiary
import com.glassplay.app.ui.theme.glass
import com.glassplay.app.ui.theme.glassDark
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Loading state for anything fetched from an add-on. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val data: T) : Loadable<T>
    data class Error(val message: String) : Loadable<Nothing>
}

/** Springy press feedback, like iOS. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bounceClick(
    pressedScale: Float = 0.95f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onLongClick = onLongClick?.let { long ->
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    long()
                }
            },
            onClick = {
                // A light tick, like iOS, so taps feel physical.
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        )
}

fun Modifier.noRippleClick(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 44,
    tint: Color = Ink,
) {
    Box(
        modifier
            .bounceClick(0.88f, onClick = onClick)
            .size(size.dp)
            .glassDark(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color? = null,
) {
    val fg = contentColor ?: if (primary) InkInverse else Ink
    Row(
        modifier
            .bounceClick(onClick = { if (enabled) onClick() })
            .height(48.dp)
            .then(
                if (primary) Modifier.clip(CircleShape).background(Ink.copy(alpha = if (enabled) 1f else 0.5f))
                else Modifier.glass(CircleShape)
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = GlassType.headline, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "chip")
    Box(
        modifier
            .bounceClick(0.92f, onClick = onClick)
            .height(36.dp)
            .glass(CircleShape, strength = 0.9f)
            .background(Ink.copy(alpha = 0.92f * bgAlpha), CircleShape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = GlassType.footnote,
            color = if (selected) InkInverse else Ink,
            maxLines = 1,
        )
    }
}

/** iOS-style segmented control with a sliding glass thumb. */
@Composable
fun GlassSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .height(42.dp)
            .glass(CircleShape)
            .padding(3.dp)
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selected, spring(dampingRatio = 0.78f, stiffness = 420f), label = "seg")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Ink.copy(alpha = 0.26f))
                .border(0.5.dp, Ink.copy(alpha = 0.45f), CircleShape)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .noRippleClick { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = GlassType.callout, color = if (i == selected) Ink else TextSecondary)
                }
            }
        }
    }
}

@Composable
fun LargeTitle(
    title: String,
    caption: String? = null,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
) {
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = horizontalPadding, end = horizontalPadding, top = 14.dp, bottom = 10.dp)
    ) {
        if (caption != null) {
            Text(caption.uppercase(), style = GlassType.caption, color = TextSecondary)
            Spacer(Modifier.height(2.dp))
        }
        Text(title, style = GlassType.largeTitle, color = Ink)
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = GlassType.title3, color = Ink, modifier = Modifier.weight(1f, fill = false))
        if (subtitle != null) {
            Spacer(Modifier.width(8.dp))
            Text(subtitle, style = GlassType.footnote, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = Ink) {
    Box(
        modifier
            .height(4.dp)
            .clip(CircleShape)
            .background(Ink.copy(alpha = 0.22f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(color)
        )
    }
}

/** Soft pulsing placeholder while content loads. */
@Composable
fun Placeholder(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)) {
    val t = rememberInfiniteTransition(label = "ph")
    val a by t.animateFloat(0.05f, 0.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pha")
    Box(modifier.clip(shape).background(Ink.copy(alpha = a)))
}

@Composable
fun Spinner(modifier: Modifier = Modifier, size: Int = 26) {
    CircularProgressIndicator(
        modifier = modifier.size(size.dp),
        color = Ink,
        trackColor = Ink.copy(alpha = 0.12f),
        strokeWidth = 2.5.dp,
    )
}

@Composable
fun GlassDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        // Blur the screen behind the dialog (Android 12 and newer).
        val view = LocalView.current
        LaunchedEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null && Build.VERSION.SDK_INT >= 31) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = 48 }
                window.setDimAmount(0.25f)
            }
        }
        val scale = remember { Animatable(0.9f) }
        LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 480f)) }
        Column(
            Modifier
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .fillMaxWidth()
                .glassDark(RoundedCornerShape(30.dp))
                .padding(22.dp),
            content = content,
        )
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    imeAction: ImeAction = ImeAction.Done,
    keyboardType: KeyboardType = KeyboardType.Text,
    onSubmit: () -> Unit = {},
    /** Hides the text, for passwords. */
    secret: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = GlassType.body.copy(color = Ink),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
        keyboardActions = KeyboardActions(onAny = { onSubmit() }),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .glass(RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Icon(leading, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = GlassType.body, color = TextTertiary, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Cancel,
                        "Clear",
                        tint = TextTertiary,
                        modifier = Modifier.size(20.dp).noRippleClick { onValueChange("") },
                    )
                }
            }
        },
    )
}

data class TabItem(val route: String, val label: String, val icon: ImageVector)

/** Floating glass tab bar with a sliding highlight. */
@Composable
fun GlassTabBar(
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    /** Draws the blurred content behind the bar; see [Modifier.frostedBackdrop]. */
    backdrop: Modifier = Modifier,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .shadow(28.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .then(backdrop)
                .glass(CircleShape, strength = 0.9f)
                .padding(5.dp)
        ) {
            val itemWidth = maxWidth / tabs.size
            val x by animateDpAsState(itemWidth * selected, spring(dampingRatio = 0.72f, stiffness = 380f), label = "tab")
            Box(
                Modifier
                    .offset(x = x)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Ink.copy(alpha = 0.15f))
                    .border(0.5.dp, Ink.copy(alpha = 0.25f), CircleShape)
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, tab ->
                    val isSel = i == selected
                    val scale by animateFloatAsState(if (isSel) 1.1f else 1f, spring(dampingRatio = 0.45f, stiffness = 420f), label = "tabScale")
                    val tint = if (isSel) Ink else Ink.copy(alpha = 0.55f)
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .noRippleClick { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            tab.icon,
                            tab.label,
                            tint = tint,
                            modifier = Modifier.size(23.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(tab.label, fontSize = 10.sp, color = tint, style = GlassType.caption)
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalMin = (ms / 60_000).coerceAtLeast(1)
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/**
 * Real frosted glass: blurs whatever scrolls behind this element. The content behind must be
 * marked with `hazeSource` on the same [state]. Android 12+ blurs; older phones get a tint.
 */
fun Modifier.frostedBackdrop(state: HazeState): Modifier = composed {
    val p = LocalPalette.current
    hazeEffect(
        state,
        HazeStyle(
            backgroundColor = p.background,
            tint = HazeTint(p.bar.copy(alpha = if (p.isDark) 0.45f else 0.55f)),
            blurRadius = 26.dp,
            noiseFactor = 0.08f,
        ),
    )
}
