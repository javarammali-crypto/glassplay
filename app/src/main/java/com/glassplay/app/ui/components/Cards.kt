package com.glassplay.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.glassplay.app.data.MetaPreview
import com.glassplay.app.data.WatchEntry
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.Ink
import com.glassplay.app.ui.theme.StarYellow
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.TextTertiary
import com.glassplay.app.ui.theme.glassDark

private val PosterShape = RoundedCornerShape(16.dp)

@Composable
fun PosterCard(item: MetaPreview, onClick: () -> Unit, modifier: Modifier = Modifier.width(116.dp)) {
    // Posters fade and settle in as their image arrives instead of popping in.
    var loaded by remember(item.poster) { mutableStateOf(false) }
    val reveal by animateFloatAsState(if (loaded) 1f else 0f, tween(420), label = "poster")
    Column(modifier.bounceClick(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(PosterShape)
                .background(Ink.copy(alpha = 0.06f))
                .border(0.5.dp, Ink.copy(alpha = 0.16f), PosterShape)
        ) {
            if (item.poster == null) {
                Text(
                    item.name,
                    style = GlassType.footnote,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(10.dp),
                )
            }
            AsyncImage(
                model = item.poster,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = reveal
                    val s = 1.06f - 0.06f * reveal
                    scaleX = s
                    scaleY = s
                },
            )
            item.rating?.let { RatingBadge(it, Modifier.align(Alignment.TopEnd).padding(6.dp)) }
        }
        Spacer(Modifier.height(7.dp))
        Text(item.name, style = GlassType.footnote, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.releaseInfo?.let {
            Text(it, style = GlassType.caption, color = TextTertiary, maxLines = 1)
        }
    }
}

@Composable
fun RatingBadge(rating: String, modifier: Modifier = Modifier) {
    Row(
        modifier.glassDark(CircleShape).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Star, null, tint = StarYellow, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text(rating, style = GlassType.caption, color = Color.White)
    }
}

@Composable
fun PosterRowPlaceholder() {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false) {
        items(6) {
            Column(Modifier.width(116.dp)) {
                Placeholder(Modifier.fillMaxWidth().aspectRatio(2f / 3f), PosterShape)
                Spacer(Modifier.height(7.dp))
                Placeholder(Modifier.width(80.dp).height(10.dp), CircleShape)
            }
        }
    }
}

@Composable
fun PosterRow(items: List<MetaPreview>, onOpen: (MetaPreview) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.type + ":" + it.id }) { item ->
            PosterCard(item, onClick = { onOpen(item) })
        }
    }
}

@Composable
fun ContinueCard(entry: WatchEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.width(264.dp).bounceClick(onLongClick = onLongClick, onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(Ink.copy(alpha = 0.06f))
                .border(0.5.dp, Ink.copy(alpha = 0.18f), shape)
        ) {
            AsyncImage(
                model = entry.background ?: entry.poster,
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f)))
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(entry.name, style = GlassType.headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = when {
                    entry.completed -> listOfNotNull(entry.episodeLabel, "Finished").joinToString(" · ")
                    entry.durationMs > 0 -> listOfNotNull(entry.episodeLabel, "${formatDuration(entry.durationMs - entry.positionMs)} left").joinToString(" · ")
                    else -> entry.episodeLabel ?: "In progress"
                }
                Text(detail, style = GlassType.caption, color = TextSecondary, maxLines = 1)
                if (!entry.completed && entry.durationMs > 0) {
                    Spacer(Modifier.height(8.dp))
                    ProgressLine(entry.progress, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
