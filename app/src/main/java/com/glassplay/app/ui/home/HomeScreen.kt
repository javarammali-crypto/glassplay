package com.glassplay.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.glassplay.app.Graph
import com.glassplay.app.data.CatalogDef
import com.glassplay.app.data.InstalledAddon
import com.glassplay.app.data.MAIN_TYPES
import com.glassplay.app.data.MetaPreview
import com.glassplay.app.data.WatchEntry
import com.glassplay.app.data.typeLabel
import com.glassplay.app.player.resumeEntry
import com.glassplay.app.ui.account.Avatar
import com.glassplay.app.ui.components.ContinueCard
import com.glassplay.app.ui.components.GlassButton
import com.glassplay.app.ui.components.GlassDialog
import com.glassplay.app.ui.components.LargeTitle
import com.glassplay.app.ui.components.Loadable
import com.glassplay.app.ui.components.Placeholder
import com.glassplay.app.ui.components.PosterRow
import com.glassplay.app.ui.components.PosterRowPlaceholder
import com.glassplay.app.ui.components.RatingBadge
import com.glassplay.app.ui.components.SectionHeader
import com.glassplay.app.ui.components.Spinner
import com.glassplay.app.ui.components.bounceClick
import com.glassplay.app.ui.theme.Danger
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.Ink
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.TextTertiary
import com.glassplay.app.ui.theme.glass
import kotlinx.coroutines.CancellationException
import java.util.Calendar
import kotlin.math.absoluteValue

@Composable
fun HomeScreen(
    onOpen: (String, String) -> Unit,
    onGoAddons: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val context = LocalContext.current
    val addons by Graph.addons.installed.collectAsState()
    val ready by Graph.addons.ready.collectAsState()
    val entries by Graph.history.entries.collectAsState()

    val continueList = remember(entries) { entries.filter { !(it.completed && it.type == "movie") }.take(20) }
    val rows = remember(addons) {
        addons.flatMap { a ->
            a.manifest.catalogs.filter { it.isHomeRow }.map { a to it }
        }.sortedBy { (_, cat) -> if (cat.type in MAIN_TYPES) 0 else 1 }
    }
    var menuFor by remember { mutableStateOf<WatchEntry?>(null) }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item(key = "title") {
            Box {
                LargeTitle("Watch Now", caption = greeting)
                if (Graph.accounts.enabled) {
                    val profile by Graph.accounts.profile.collectAsState()
                    Avatar(
                        profile,
                        40,
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 14.dp)
                            .bounceClick(0.9f, onClick = onOpenProfile),
                    )
                }
            }
        }

        item(key = "search") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                    .bounceClick(0.97f, onClick = onOpenSearch)
                    .height(50.dp)
                    .glass(RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Search movies, shows and more", style = GlassType.body, color = TextTertiary)
            }
        }

        if (rows.isNotEmpty()) {
            item(key = "hero") { HeroSection(rows.first().first, rows.first().second, onOpen) }
        }

        if (continueList.isNotEmpty()) {
            item(key = "cw-title") { SectionHeader("Continue Watching") }
            item(key = "cw-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(continueList, key = { it.metaId }) { e ->
                        ContinueCard(
                            entry = e,
                            onClick = { if (!resumeEntry(context, e)) onOpen(e.type, e.metaId) },
                            onLongClick = { menuFor = e },
                        )
                    }
                }
            }
        }

        rows.forEachIndexed { index, (addon, cat) ->
            item(key = "row-$index-${addon.manifest.id}-${cat.type}-${cat.id}") {
                CatalogRow(addon, cat, onOpen)
            }
        }

        if (rows.isEmpty()) {
            item(key = "empty") {
                if (!ready) {
                    Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) { Spinner() }
                } else {
                    EmptyHome(onGoAddons)
                }
            }
        }
    }

    menuFor?.let { e ->
        GlassDialog(onDismiss = { menuFor = null }) {
            Text(e.name, style = GlassType.title3, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            e.episodeLabel?.let { Text(it, style = GlassType.subhead, color = TextSecondary) }
            Spacer(Modifier.height(18.dp))
            if (!e.completed && e.streamUrl != null) {
                GlassButton("Resume", onClick = { menuFor = null; resumeEntry(context, e) }, primary = true, modifier = Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.OpenInNew)
                Spacer(Modifier.height(10.dp))
            }
            GlassButton("View details", onClick = { menuFor = null; onOpen(e.type, e.metaId) }, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Info)
            Spacer(Modifier.height(10.dp))
            GlassButton(
                "Remove from Continue Watching",
                onClick = { menuFor = null; Graph.history.remove(e.metaId) },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Delete,
                contentColor = Danger,
            )
        }
    }
}

@Composable
private fun rememberCatalog(addon: InstalledAddon, cat: CatalogDef): Loadable<List<MetaPreview>> {
    val state by produceState<Loadable<List<MetaPreview>>>(Loadable.Loading, addon.transportUrl, cat.type, cat.id) {
        value = try {
            Loadable.Ready(Graph.client.catalog(addon, cat))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Loadable.Error(e.message ?: "Couldn't load")
        }
    }
    return state
}

@Composable
private fun CatalogRow(addon: InstalledAddon, cat: CatalogDef, onOpen: (String, String) -> Unit) {
    val state = rememberCatalog(addon, cat)
    Column {
        SectionHeader("${cat.name} ${typeLabel(cat.type)}", subtitle = addon.manifest.name)
        when (state) {
            is Loadable.Loading -> PosterRowPlaceholder()
            is Loadable.Error -> Text(
                "Couldn't load this row. ${state.message}",
                style = GlassType.footnote,
                color = TextTertiary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            is Loadable.Ready -> if (state.data.isEmpty()) {
                Text("Nothing here yet.", style = GlassType.footnote, color = TextTertiary, modifier = Modifier.padding(horizontal = 20.dp))
            } else {
                PosterRow(state.data) { onOpen(it.type, it.id) }
            }
        }
    }
}

@Composable
private fun HeroSection(addon: InstalledAddon, cat: CatalogDef, onOpen: (String, String) -> Unit) {
    val state = rememberCatalog(addon, cat)
    val heroShape = RoundedCornerShape(30.dp)
    when (state) {
        is Loadable.Loading -> Placeholder(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp).height(440.dp),
            heroShape,
        )
        is Loadable.Error -> Unit
        is Loadable.Ready -> {
            val items = remember(state.data) { state.data.filter { it.poster != null }.take(8) }
            if (items.isEmpty()) return
            val pager = rememberPagerState { items.size }
            Column {
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 28.dp),
                    pageSpacing = 14.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(450.dp),
                ) { page ->
                    val item = items[page]
                    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = lerp(0.9f, 1f, 1f - offset)
                                scaleX = s
                                scaleY = s
                                alpha = lerp(0.55f, 1f, 1f - offset)
                            }
                            .bounceClick(0.97f) { onOpen(item.type, item.id) }
                            .clip(heroShape)
                            .border(1.dp, Color.White.copy(alpha = 0.22f), heroShape)
                    ) {
                        AsyncImage(
                            model = item.poster,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        item.rating?.let { RatingBadge(it, Modifier.align(Alignment.TopEnd).padding(14.dp)) }
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Text(
                                "FEATURED · ${typeLabel(item.type).uppercase()}",
                                style = GlassType.caption,
                                color = TextSecondary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(item.name, style = GlassType.title1, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            item.description?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(it, style = GlassType.subhead, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(items.size) { i ->
                        val selected = pager.currentPage == i
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (selected) 0.9f else 0.3f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHome(onGoAddons: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .glass(RoundedCornerShape(26.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Extension, null, tint = Ink, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("No catalogs yet", style = GlassType.title3, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Install an add-on with catalogs to fill your home screen.",
            style = GlassType.subhead,
            color = TextSecondary,
        )
        Spacer(Modifier.height(16.dp))
        GlassButton("Go to Add-ons", onClick = onGoAddons, primary = true, modifier = Modifier.width(200.dp))
    }
}
