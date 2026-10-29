package com.glassplay.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassplay.app.Graph
import com.glassplay.app.data.SubtitleBackground
import com.glassplay.app.data.SubtitleColor
import com.glassplay.app.data.SubtitleSize
import com.glassplay.app.data.SubtitleStyle
import com.glassplay.app.data.UpdateState
import com.glassplay.app.ui.account.Avatar
import com.glassplay.app.ui.components.GlassButton
import com.glassplay.app.ui.components.GlassChip
import com.glassplay.app.ui.components.GlassSegmented
import com.glassplay.app.ui.components.LargeTitle
import com.glassplay.app.ui.components.SectionHeader
import com.glassplay.app.ui.components.bounceClick
import com.glassplay.app.ui.theme.Accent
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.Ink
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.TextTertiary
import com.glassplay.app.ui.theme.ThemeMode
import com.glassplay.app.ui.theme.glass

@Composable
fun SettingsScreen(onOpenProfile: () -> Unit) {
    val theme by Graph.settings.theme.collectAsState()
    val subtitles by Graph.settings.subtitles.collectAsState()
    val profile by Graph.accounts.profile.collectAsState()
    val update by Graph.updater.state.collectAsState()
    fun setSubs(style: SubtitleStyle) = Graph.settings.setSubtitles(style)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item(key = "title") { LargeTitle("Settings") }

        if (Graph.accounts.enabled) {
            item(key = "account") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .bounceClick(0.98f, onClick = onOpenProfile)
                        .glass(RoundedCornerShape(24.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(profile, 52)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile?.fullName?.ifBlank { null } ?: "Your profile",
                            style = GlassType.headline,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("Profile, watch history and stats", style = GlassType.footnote, color = TextSecondary)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextTertiary)
                }
            }
        }

        item(key = "appearance-title") { SectionHeader("Appearance") }
        item(key = "appearance") {
            Card {
                Text("Theme", style = GlassType.callout, color = Ink)
                Spacer(Modifier.height(10.dp))
                GlassSegmented(
                    options = ThemeMode.entries.map { it.label },
                    selected = theme.ordinal,
                    onSelect = { Graph.settings.setTheme(ThemeMode.entries[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "System follows your phone's dark mode. Title pages and the player always stay dark.",
                    style = GlassType.footnote,
                    color = TextTertiary,
                )
            }
        }

        item(key = "subs-title") { SectionHeader("Subtitles") }
        item(key = "subs") {
            Card {
                SubtitlePreview(subtitles)
                Spacer(Modifier.height(16.dp))

                Text("Text colour", style = GlassType.callout, color = Ink)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SubtitleColor.entries.forEach { c ->
                        ColorSwatch(Color(c.argb), selected = c == subtitles.textColor) { setSubs(subtitles.copy(textColor = c)) }
                    }
                }
                Spacer(Modifier.height(16.dp))

                Text("Background", style = GlassType.callout, color = Ink)
                Spacer(Modifier.height(10.dp))
                ChipRow(SubtitleBackground.entries, subtitles.background, { it.label }) { setSubs(subtitles.copy(background = it)) }
                Spacer(Modifier.height(16.dp))

                Text("Size", style = GlassType.callout, color = Ink)
                Spacer(Modifier.height(10.dp))
                ChipRow(SubtitleSize.entries, subtitles.size, { it.label }) { setSubs(subtitles.copy(size = it)) }
            }
        }

        item(key = "about-title") { SectionHeader("About") }
        item(key = "about") {
            Card {
                Text("GlassPlay ${Graph.updater.currentName}", style = GlassType.headline, color = Ink)
                val status = when (val u = update) {
                    UpdateState.Checking -> "Checking for updates…"
                    UpdateState.UpToDate -> "You have the latest version."
                    is UpdateState.Failed -> u.message
                    else -> "Updates are offered automatically when you open the app."
                }
                Text(status, style = GlassType.footnote, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                GlassButton("Check for updates", onClick = { Graph.updater.check(manual = true) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .glass(RoundedCornerShape(24.dp))
            .padding(16.dp),
        content = content,
    )
}

/** A frame from a "film" with a line of subtitles in the chosen style. */
@Composable
private fun SubtitlePreview(style: SubtitleStyle) {
    val shape = RoundedCornerShape(18.dp)
    val boxed = style.background != SubtitleBackground.SHADOW
    val textColor by animateColorAsState(Color(style.textColor.argb), label = "subColor")
    val boxColor by animateColorAsState(Color(style.background.argb), label = "subBox")
    val scale by animateFloatAsState(style.size.scale, label = "subSize")
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF3A2A5C), Color(0xFF1B3A4B), Color(0xFF6B4A2B))))
            .border(1.dp, Color.White.copy(alpha = 0.2f), shape),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            "Preview",
            style = GlassType.caption,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )
        Text(
            "Where are we going?\nSomewhere we've never been.",
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = (15 * scale).sp,
                fontWeight = FontWeight.Medium,
                lineHeight = (20 * scale).sp,
                shadow = if (boxed) null else Shadow(Color.Black, blurRadius = 6f),
            ),
            color = textColor,
            modifier = Modifier
                .padding(bottom = 14.dp, start = 12.dp, end = 12.dp)
                .background(boxColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val grow by animateFloatAsState(if (selected) 1.12f else 1f, label = "swatch")
    Box(
        Modifier
            .scale(grow)
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) Accent else Color.Black.copy(alpha = 0.2f), CircleShape)
            .bounceClick(0.85f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { o -> GlassChip(label(o), selected = o == selected, onClick = { onSelect(o) }) }
    }
}
