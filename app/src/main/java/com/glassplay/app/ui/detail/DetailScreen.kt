package com.glassplay.app.ui.detail

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.glassplay.app.Graph
import com.glassplay.app.data.MetaDetail
import com.glassplay.app.data.Video
import com.glassplay.app.data.VideoProgress
import com.glassplay.app.data.WatchEntry
import com.glassplay.app.player.openLink
import com.glassplay.app.ui.components.GlassButton
import com.glassplay.app.ui.components.GlassChip
import com.glassplay.app.ui.components.GlassIconButton
import com.glassplay.app.ui.components.Loadable
import com.glassplay.app.ui.components.Placeholder
import com.glassplay.app.ui.components.ProgressLine
import com.glassplay.app.ui.components.SectionHeader
import com.glassplay.app.ui.components.bounceClick
import com.glassplay.app.ui.components.formatDuration
import com.glassplay.app.ui.components.noRippleClick
import com.glassplay.app.ui.theme.Background
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.StarYellow
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.TextTertiary
import com.glassplay.app.ui.theme.glass
import kotlinx.coroutines.CancellationException

data class StreamTarget(val meta: MetaDetail, val video: Video?) {
    val videoId: String get() = video?.id ?: meta.id
}

@Composable
fun DetailScreen(type: String, id: String, onBack: () -> Unit) {
    val addons by Graph.addons.installed.collectAsState()
    val meta by produceState<Loadable<MetaDetail>>(Loadable.Loading, type, id, addons.size) {
        value = try {
            Graph.addons.loadMeta(type, id)?.let { Loadable.Ready(it) }
                ?: Loadable.Error("None of your add-ons have details for this title.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Loadable.Error(e.message ?: "Couldn't load details")
        }
    }
    var streamTarget by remember { mutableStateOf<StreamTarget?>(null) }

    Box(Modifier.fillMaxSize().background(Background)) {
        when (val m = meta) {
            is Loadable.Loading -> DetailSkeleton()
            is Loadable.Error -> Column(
                Modifier.align(Alignment.Center).padding(24.dp).glass(RoundedCornerShape(26.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Couldn't open this title", style = GlassType.title3, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(m.message, style = GlassType.subhead, color = TextSecondary)
                Spacer(Modifier.height(16.dp))
                GlassButton("Go back", onClick = onBack, primary = true)
            }
            is Loadable.Ready -> DetailContent(m.data, onPlay = { streamTarget = it })
        }

        GlassIconButton(
            Icons.AutoMirrored.Rounded.ArrowBack,
            "Back",
            onBack,
            modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp),
        )
    }

    streamTarget?.let { target ->
        StreamSheet(target, onDismiss = { streamTarget = null })
    }
}

@Composable
private fun DetailContent(meta: MetaDetail, onPlay: (StreamTarget) -> Unit) {
    val context = LocalContext.current
    val progressMap by Graph.history.videoProgress.collectAsState()
    val entries by Graph.history.entries.collectAsState()
    val entry = entries.firstOrNull { it.metaId == meta.id }

    val episodes = remember(meta) {
        meta.videos.sortedWith(
            compareBy<Video>({ if ((it.season ?: 0) == 0) Int.MAX_VALUE else it.season }, { it.episode ?: 0 })
        )
    }
    val seasons = remember(episodes) { episodes.map { it.season ?: 0 }.distinct() }
    val nextVideo = remember(meta, entry) { if (meta.isSeries) pickNextEpisode(episodes, entry) else null }
    var season by rememberSaveable(meta.id) { mutableIntStateOf(nextVideo?.season ?: seasons.firstOrNull() ?: 1) }
    var expanded by rememberSaveable(meta.id) { mutableStateOf(false) }

    val heroImage = meta.background ?: meta.poster

    Box(Modifier.fillMaxSize()) {
        // Blurred artwork fills the page behind the glass (real blur on Android 12+).
        if (heroImage != null) {
            AsyncImage(
                model = heroImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(60.dp) else Modifier)
                    .alpha(if (Build.VERSION.SDK_INT >= 31) 0.55f else 0.22f),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
            item(key = "hero") { Hero(meta, heroImage) }

            item(key = "actions") {
                val (label, sub, fraction) = playLabel(meta, nextVideo, entry, progressMap)
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassButton(
                            text = label,
                            icon = Icons.Rounded.PlayArrow,
                            primary = true,
                            onClick = { onPlay(StreamTarget(meta, nextVideo)) },
                            modifier = Modifier.weight(1f),
                        )
                        meta.trailerYtId?.let { yt ->
                            Spacer(Modifier.width(10.dp))
                            GlassIconButton(Icons.Rounded.Movie, "Trailer", { openLink(context, "https://www.youtube.com/watch?v=$yt") }, size = 48)
                        }
                    }
                    if (sub != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (fraction != null) {
                                ProgressLine(fraction, Modifier.width(90.dp))
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(sub, style = GlassType.footnote, color = TextSecondary)
                        }
                    }
                }
            }

            meta.description?.let { desc ->
                item(key = "desc") {
                    Text(
                        desc,
                        style = GlassType.body,
                        color = Color.White.copy(alpha = 0.86f),
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 20.dp, end = 20.dp, top = 18.dp)
                            .animateContentSize()
                            .noRippleClick { expanded = !expanded },
                    )
                }
            }

            if (meta.cast.isNotEmpty() || meta.directors.isNotEmpty()) {
                item(key = "credits") {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
                        if (meta.cast.isNotEmpty()) InfoLine("Starring", meta.cast.take(6).joinToString(", "))
                        if (meta.directors.isNotEmpty()) InfoLine("Director", meta.directors.joinToString(", "))
                    }
                }
            }

            if (meta.isSeries) {
                item(key = "ep-title") { SectionHeader("Episodes") }
                if (seasons.size > 1) {
                    item(key = "seasons") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(seasons, key = { it }) { s ->
                                GlassChip(
                                    text = if (s == 0) "Specials" else "Season $s",
                                    selected = s == season,
                                    onClick = { season = s },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                val seasonEpisodes = episodes.filter { (it.season ?: 0) == season }
                items(seasonEpisodes, key = { "ep-" + it.id }) { v ->
                    EpisodeRow(v, progressMap[v.id], isNext = v.id == nextVideo?.id) { onPlay(StreamTarget(meta, v)) }
                }
            }
        }
    }
}

@Composable
private fun Hero(meta: MetaDetail, image: String?) {
    var logoFailed by remember(meta.logo) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(500.dp)) {
        AsyncImage(
            model = image,
            contentDescription = meta.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.28f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.92f),
                )
            )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
        ) {
            if (meta.logo != null && !logoFailed) {
                AsyncImage(
                    model = meta.logo,
                    contentDescription = meta.name,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    onError = { logoFailed = true },
                    modifier = Modifier.fillMaxWidth(0.72f).heightIn(max = 96.dp).height(96.dp),
                )
            } else {
                Text(meta.name, style = GlassType.title1, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            val seasonsCount = meta.videos.mapNotNull { it.season }.filter { it > 0 }.distinct().size
            val facts = listOfNotNull(
                meta.releaseInfo,
                meta.runtime,
                if (meta.isSeries && seasonsCount > 0) "$seasonsCount Season${if (seasonsCount > 1) "s" else ""}" else null,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                meta.rating?.let {
                    Text("★ $it", style = GlassType.footnote, color = StarYellow)
                    if (facts.isNotEmpty()) Text("  ·  ", style = GlassType.footnote, color = TextTertiary)
                }
                Text(facts.joinToString("  ·  "), style = GlassType.footnote, color = TextSecondary, maxLines = 1)
            }
            if (meta.genres.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    meta.genres.take(3).forEach { g ->
                        Text(
                            g,
                            style = GlassType.caption,
                            color = Color.White,
                            modifier = Modifier.glass(CircleShape, 0.8f).padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("$label  ", style = GlassType.footnote, color = TextTertiary)
        Text(value, style = GlassType.footnote, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EpisodeRow(video: Video, progress: VideoProgress?, isNext: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .bounceClick(0.97f, onClick = onClick)
            .glass(shape, if (isNext) 1.4f else 0.75f)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbShape = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .width(132.dp)
                .aspectRatio(16f / 9f)
                .clip(thumbShape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), thumbShape)
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (progress != null && !progress.completed && progress.fraction > 0f) {
                ProgressLine(
                    progress.fraction,
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp),
                )
            }
            if (progress?.completed == true) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                Icon(
                    Icons.Rounded.CheckCircle,
                    "Watched",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(26.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = buildString {
                video.episode?.let { append("$it. ") }
                append(video.title.ifBlank { "Episode ${video.episode ?: ""}".trim() })
            }
            Text(title, style = GlassType.callout, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(
                if (isNext) "Up next" else null,
                video.released?.take(10),
            ).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(sub, style = GlassType.caption, color = if (isNext) Color.White else TextTertiary)
            }
            video.overview?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = GlassType.footnote, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DetailSkeleton() {
    Column(Modifier.fillMaxSize()) {
        Placeholder(Modifier.fillMaxWidth().height(500.dp), RoundedCornerShape(0.dp))
        Spacer(Modifier.height(16.dp))
        Placeholder(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(48.dp), CircleShape)
        Spacer(Modifier.height(18.dp))
        repeat(3) {
            Placeholder(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().height(14.dp), CircleShape)
        }
    }
}

/** For a show: the episode to resume, the one after the last finished, or the first one. */
private fun pickNextEpisode(episodes: List<Video>, entry: WatchEntry?): Video? {
    val ordered = episodes.filter { (it.season ?: 0) != 0 }.ifEmpty { episodes }
    if (ordered.isEmpty()) return null
    val idx = entry?.let { e -> ordered.indexOfFirst { it.id == e.videoId } } ?: -1
    return when {
        idx < 0 -> ordered.first()
        entry?.completed == true -> ordered.getOrNull(idx + 1) ?: ordered[idx]
        else -> ordered[idx]
    }
}

private data class PlayLabel(val label: String, val sub: String?, val fraction: Float?)

private fun playLabel(
    meta: MetaDetail,
    next: Video?,
    entry: WatchEntry?,
    progress: Map<String, VideoProgress>,
): PlayLabel {
    val videoId = next?.id ?: meta.id
    val p = progress[videoId]
    val resumable = p != null && !p.completed && p.positionMs > 10_000 && p.durationMs > 0
    val epLabel = next?.label
    return when {
        resumable -> PlayLabel(
            if (epLabel != null) "Resume $epLabel" else "Resume",
            "${formatDuration(p!!.durationMs - p.positionMs)} left",
            p.fraction,
        )
        meta.isSeries && entry?.completed == true && entry.videoId != videoId -> PlayLabel(
            if (epLabel != null) "Play $epLabel" else "Play",
            "Up next",
            null,
        )
        epLabel != null -> PlayLabel("Play $epLabel", next?.title?.takeIf { it.isNotBlank() }, null)
        else -> PlayLabel("Play", null, null)
    }
}
