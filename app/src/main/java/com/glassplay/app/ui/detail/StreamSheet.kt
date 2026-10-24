package com.glassplay.app.ui.detail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.glassplay.app.Graph
import com.glassplay.app.data.StreamItem
import com.glassplay.app.data.StreamResults
import com.glassplay.app.data.TorrentLink
import com.glassplay.app.player.openInExternalPlayer
import com.glassplay.app.player.openLink
import com.glassplay.app.player.playDownload
import com.glassplay.app.player.playStream
import com.glassplay.app.ui.components.Spinner
import com.glassplay.app.ui.components.bounceClick
import com.glassplay.app.ui.components.noRippleClick
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.SheetColor
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.TextTertiary
import com.glassplay.app.ui.theme.glass
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSheet(target: StreamTarget, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val downloads by Graph.downloads.items.collectAsState()
    val saved = remember(downloads, target) { Graph.downloads.completedFor(target.videoId) }
    // The download notification needs permission on Android 13 and newer.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val download: (StreamItem) -> Unit = { s ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val started = Graph.downloads.start(target.meta, target.video, s)
        Toast.makeText(
            context,
            if (started) "Downloading. Follow it in the Downloads tab." else "This stream is already in your downloads.",
            Toast.LENGTH_SHORT,
        ).show()
    }
    val results by produceState<StreamResults?>(null, target) {
        value = try {
            Graph.addons.loadStreams(target.meta.type, target.videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StreamResults(emptyList(), 0, 0)
        }
    }
    // Warm the cache so the player can attach subtitles without waiting.
    LaunchedEffect(target) {
        try {
            Graph.addons.loadSubtitles(target.meta.type, target.videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Subtitles are optional.
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetColor.copy(alpha = 0.97f),
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Text("Choose a stream", style = GlassType.title2, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
            val sub = listOfNotNull(target.meta.name, target.video?.label, target.video?.title?.takeIf { it.isNotBlank() })
                .joinToString(" · ")
            Text(sub, style = GlassType.subhead, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(14.dp))

            saved?.let { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .bounceClick(0.97f) {
                            onDismiss()
                            playDownload(context, item)
                        }
                        .glass(RoundedCornerShape(18.dp), 1f)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.DownloadDone, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Play downloaded copy", style = GlassType.callout, color = Color.White)
                        Text(
                            listOfNotNull(item.streamTitle, "On this device · no internet needed").joinToString(" · "),
                            style = GlassType.caption,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            val r = results
            when {
                r == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spinner()
                        Spacer(Modifier.height(10.dp))
                        Text("Asking your add-ons…", style = GlassType.footnote, color = TextTertiary)
                    }
                }
                r.providers == 0 -> Message(
                    "No stream add-ons",
                    "None of your installed add-ons provide streams for this title. You can install add-ons from the Add-ons tab.",
                )
                r.playableCount == 0 && r.unsupported > 0 -> Message(
                    "No playable streams",
                    "Your add-ons returned ${r.unsupported} stream${if (r.unsupported == 1) "" else "s"} in a format GlassPlay can't open.",
                )
                r.playableCount == 0 -> Message(
                    "No streams found",
                    r.groups.joinToString("\n\n") { g ->
                        when {
                            g.error != null -> "${g.addonName}: ${g.error}"
                            else -> "${g.addonName}: no streams. Some add-ons only return streams once you configure them, for example with a debrid account."
                        }
                    }.ifEmpty { "Your add-ons didn't return anything for this title." },
                )
                else -> LazyColumn(Modifier.heightIn(max = 560.dp).nestedScroll(KeepScrollInList)) {
                    r.groups.filter { it.streams.isNotEmpty() }.forEachIndexed { gi, group ->
                        item(key = "h-$gi") {
                            Text(
                                group.addonName.uppercase(),
                                style = GlassType.caption,
                                color = TextTertiary,
                                modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 6.dp),
                            )
                        }
                        items(group.streams) { s ->
                            StreamRow(
                                stream = s,
                                onPlay = {
                                    onDismiss()
                                    playStream(context, target.meta, target.video, s)
                                },
                                onDownload = if (s.url != null || s.isTorrent) { { download(s) } } else null,
                                onExternal = when {
                                    s.url != null -> { { openInExternalPlayer(context, s.url, target.meta.name) } }
                                    s.isTorrent -> TorrentLink.fromStream(s)?.let { link -> { openLink(context, link.toEngineUri()) } }
                                    else -> null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp), 0.8f)
            .padding(20.dp)
    ) {
        Text(title, style = GlassType.headline, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(body, style = GlassType.subhead, color = TextSecondary)
    }
}

@Composable
private fun StreamRow(stream: StreamItem, onPlay: () -> Unit, onDownload: (() -> Unit)?, onExternal: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .bounceClick(0.97f, onClick = onPlay)
            .glass(RoundedCornerShape(18.dp), 0.8f)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayCircle, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stream.name?.replace('\n', ' ') ?: stream.addonName,
                style = GlassType.callout,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            stream.title?.let {
                Text(it, style = GlassType.caption, color = TextTertiary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (stream.isTorrent) {
                Text("Torrent · streams in GlassPlay", style = GlassType.caption, color = TextSecondary)
            } else if (stream.url == null && stream.ytId != null) {
                Text("Opens in YouTube", style = GlassType.caption, color = TextSecondary)
            }
        }
        if (onDownload != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .glass(CircleShape, 0.8f)
                    .noRippleClick(onDownload),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Download, "Download", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        if (onExternal != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .glass(CircleShape, 0.8f)
                    .noRippleClick(onExternal),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open in another player", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Stops a scroll that reaches the top of the stream list from carrying on into the sheet
 * and dragging it closed. The sheet still closes from its handle or by tapping outside.
 */
private object KeepScrollInList : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}
