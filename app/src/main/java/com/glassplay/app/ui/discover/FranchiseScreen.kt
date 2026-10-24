package com.glassplay.app.ui.discover

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.glassplay.app.Graph
import com.glassplay.app.data.FRANCHISES
import com.glassplay.app.data.Franchise
import com.glassplay.app.data.MetaPreview
import com.glassplay.app.ui.components.GlassButton
import com.glassplay.app.ui.components.GlassIconButton
import com.glassplay.app.ui.components.PosterCard
import com.glassplay.app.ui.components.Spinner
import com.glassplay.app.ui.components.bounceClick
import com.glassplay.app.ui.theme.Background
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.glassDark
import kotlinx.coroutines.CancellationException

private fun Franchise.brush() = Brush.linearGradient(listOf(Color(colors.first), Color(colors.second)))

/** A franchise "box" in Discover: its colours, name, a few posters and how many movies it has. */
@Composable
fun FranchiseCard(franchise: Franchise, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val known = remember(franchise.id) { Graph.franchises.cached(franchise) }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .bounceClick(0.97f, onClick = onClick)
            .fillMaxWidth()
            .height(132.dp)
            .clip(shape)
            .background(franchise.brush())
            .border(0.5.dp, Color.White.copy(alpha = 0.18f), shape)
    ) {
        known.firstOrNull { it.background != null }?.let { first ->
            AsyncImage(
                model = first.background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.35f),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.7f to Color.Transparent))
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 18.dp, end = 120.dp)
        ) {
            Text(franchise.name, style = GlassType.title3, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(franchise.tagline, style = GlassType.footnote, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Text(
                "${franchise.movies.size} movies",
                style = GlassType.caption,
                color = Color.White,
                modifier = Modifier.glassDark(CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        // A fanned stack of posters once the movies have been looked up.
        val posters = known.mapNotNull { it.poster }.take(3)
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)) {
            posters.forEachIndexed { i, url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .zIndex((3 - i).toFloat())
                        .offset(x = (-i * 22).dp)
                        .width(62.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                )
            }
        }
    }
}

/** All the movies of one franchise, in release order. */
@Composable
fun FranchiseScreen(id: String, onBack: () -> Unit, onOpen: (String, String) -> Unit) {
    val franchise = remember(id) { Graph.franchises.find(id) ?: FRANCHISES.first() }
    var attempt by remember { mutableStateOf(0) }
    val state by produceState(Pair(Graph.franchises.cached(franchise), true), franchise, attempt) {
        value = try {
            Graph.franchises.movies(franchise) to false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            value.first to false
        }
    }
    val (movies, loading) = state

    LazyVerticalGrid(
        columns = GridCells.Adaptive(106.dp),
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            FranchiseHeader(franchise, movies, onBack)
        }
        items(movies, key = { it.id }) { item ->
            PosterCard(item, onClick = { onOpen(item.type, item.id) }, modifier = Modifier.fillMaxWidth())
        }
        if (loading) {
            item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spinner()
                    Spacer(Modifier.height(8.dp))
                    Text("Gathering the movies…", style = GlassType.footnote, color = TextSecondary)
                }
            }
        } else if (movies.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load these movies. Check your connection.", style = GlassType.subhead, color = TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    GlassButton("Try again", onClick = { attempt++ })
                }
            }
        }
    }
}

@Composable
private fun FranchiseHeader(franchise: Franchise, movies: List<MetaPreview>, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(franchise.brush())
    ) {
        movies.firstOrNull { it.background != null }?.let {
            AsyncImage(
                model = it.background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().alpha(0.4f),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f)))
        )
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.height(48.dp))
            Text("FRANCHISE", style = GlassType.caption, color = TextSecondary)
            Text(franchise.name, style = GlassType.title1, color = Color.White)
            Text(franchise.tagline, style = GlassType.subhead, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${franchise.movies.size} movies",
                    style = GlassType.caption,
                    color = Color.White,
                    modifier = Modifier.glassDark(CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                )
                val years = franchise.movies.map { it.year }
                Text(
                    "${years.min()}–${years.max()}",
                    style = GlassType.caption,
                    color = Color.White,
                    modifier = Modifier.glassDark(CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}
