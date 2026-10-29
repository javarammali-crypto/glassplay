package com.glassplay.app.ui.downloads

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.glassplay.app.Graph
import com.glassplay.app.data.DownloadItem
import com.glassplay.app.data.DownloadState
import com.glassplay.app.player.playDownload
import com.glassplay.app.ui.components.GlassButton
import com.glassplay.app.ui.components.GlassDialog
import com.glassplay.app.ui.components.GlassIconButton
import com.glassplay.app.ui.components.LargeTitle
import com.glassplay.app.ui.components.ProgressLine
import com.glassplay.app.ui.theme.Danger
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.Ink
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.TextTertiary
import com.glassplay.app.ui.theme.glass

@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    val items by Graph.downloads.items.collectAsState()
    var confirmDelete by remember { mutableStateOf<DownloadItem?>(null) }
    val sorted = remember(items) { items.sortedByDescending { it.createdAt } }
    val saved = items.filter { it.state == DownloadState.DONE }.sumOf { it.totalBytes }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item(key = "title") {
            LargeTitle("Downloads", caption = if (saved > 0) "${formatBytes(saved)} on this device" else "Watch without streaming")
        }

        if (sorted.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .glass(RoundedCornerShape(26.dp))
                        .padding(20.dp)
                ) {
                    Text("No downloads yet", style = GlassType.headline, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "When you choose a stream, tap its download button to save the video and watch it later without an internet connection.",
                        style = GlassType.subhead,
                        color = TextSecondary,
                    )
                }
            }
        }

        items(sorted, key = { it.id }) { item ->
            DownloadRow(
                modifier = Modifier.animateItem(),
                item = item,
                onPlay = {
                    if (!playDownload(context, item)) {
                        Toast.makeText(context, "This video can be played once it has finished downloading", Toast.LENGTH_SHORT).show()
                    }
                },
                onRetry = { Graph.downloads.retry(item.id) },
                onDelete = { confirmDelete = item },
            )
        }
    }

    confirmDelete?.let { item ->
        GlassDialog(onDismiss = { confirmDelete = null }) {
            Text(if (item.state == DownloadState.DONE) "Delete download?" else "Cancel download?", style = GlassType.title3, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "${listOfNotNull(item.name, item.episodeLabel).joinToString(" · ")} will be removed from this device.",
                style = GlassType.subhead,
                color = TextSecondary,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Keep", onClick = { confirmDelete = null }, modifier = Modifier.weight(1f))
                GlassButton(
                    "Delete",
                    onClick = {
                        Graph.downloads.delete(item.id)
                        confirmDelete = null
                    },
                    primary = true,
                    contentColor = Danger,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, onPlay: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val canPlay = item.state == DownloadState.DONE || (item.isTorrent && item.state == DownloadState.DOWNLOADING)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .glass(RoundedCornerShape(22.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(104.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Ink.copy(alpha = 0.06f))
        ) {
            AsyncImage(
                model = item.background ?: item.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = GlassType.headline, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(item.episodeLabel, item.episodeTitle?.takeIf { it.isNotBlank() }, item.streamTitle).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = GlassType.caption, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            when (item.state) {
                DownloadState.PREPARING -> Text(
                    if (item.isTorrent) "Finding the torrent…" else "Starting…",
                    style = GlassType.footnote,
                    color = TextSecondary,
                )
                DownloadState.DOWNLOADING -> {
                    ProgressLine(item.progress, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(5.dp))
                    val parts = mutableListOf("${(item.progress * 100).toInt()}%")
                    if (item.totalBytes > 0) parts += "${formatBytes(item.doneBytes)} of ${formatBytes(item.totalBytes)}"
                    if (item.bytesPerSecond > 0) parts += "${formatBytes(item.bytesPerSecond)}/s"
                    if (item.isTorrent) parts += "${item.peers} peer${if (item.peers == 1) "" else "s"}"
                    Text(parts.joinToString(" · "), style = GlassType.caption, color = TextSecondary, maxLines = 1)
                }
                DownloadState.DONE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DownloadDone, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Downloaded · ${formatBytes(item.totalBytes)}", style = GlassType.caption, color = TextSecondary)
                }
                DownloadState.FAILED -> Text(item.error ?: "The download failed", style = GlassType.caption, color = Danger, maxLines = 2)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (item.state == DownloadState.FAILED) {
            GlassIconButton(Icons.Rounded.Refresh, "Try again", onRetry, size = 38)
        } else if (canPlay) {
            GlassIconButton(Icons.Rounded.PlayArrow, "Play", onPlay, size = 38)
        }
        Spacer(Modifier.width(6.dp))
        GlassIconButton(Icons.Rounded.Delete, "Delete", onDelete, size = 38, tint = Danger)
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    else -> "${bytes / 1024} KB"
}
