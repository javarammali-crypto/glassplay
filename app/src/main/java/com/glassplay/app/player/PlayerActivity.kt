package com.glassplay.app.player

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.glassplay.app.Graph
import com.glassplay.app.data.AddonClient
import com.glassplay.app.data.SubtitleItem
import com.glassplay.app.data.WatchEntry
import com.glassplay.app.ui.components.GlassButton
import com.glassplay.app.ui.components.GlassIconButton
import com.glassplay.app.ui.components.Spinner
import com.glassplay.app.ui.theme.GlassTheme
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.glassDark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var req: PlayRequest
    private var saveJob: Job? = null

    /** Set while a torrent is open, so it can be stopped when the player closes. */
    private var torrentHash: String? = null
    /** What ExoPlayer actually opens; a local address for torrents. */
    private var playUrl: String? = null
    private var subtitles: List<SubtitleItem> = emptyList()
    private var triedHls = false
    private val error = mutableStateOf<String?>(null)
    private val starting = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = PlayRequest.fromIntent(intent)
        if (request == null) {
            finish()
            return
        }
        req = request

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(AddonClient.USER_AGENT)
            .setConnectTimeoutMs(20_000)
            // Torrent reads wait for pieces to arrive from peers, so give them much longer.
            .setReadTimeoutMs(if (req.torrent != null) 120_000 else 30_000)
            .setDefaultRequestProperties(req.headers)
        // FFmpeg decodes audio the device can't (AC3, E-AC3, DTS, TrueHD…), common in MKV releases.
        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val exo = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory)))
            .build()
        player = exo

        // Pick up where the viewer left off, unless it was finished.
        val saved = Graph.history.progress(req.videoId)
        val resumeAt = if (saved != null && !saved.completed && saved.positionMs > 5_000 &&
            (saved.durationMs <= 0 || saved.positionMs < saved.durationMs * 0.95)
        ) saved.positionMs else 0L

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) save(ended = true)
            }

            override fun onPlayerError(e: PlaybackException) {
                onError(exo, e)
            }
        })
        exo.playWhenReady = true

        setContent {
            GlassTheme {
                PlayerScreen(
                    player = exo,
                    req = req,
                    isTorrent = req.torrent != null,
                    starting = starting.value,
                    error = error.value,
                    onBack = { finish() },
                )
            }
        }

        lifecycleScope.launch {
            val fromAddons = async { addonSubtitles() }
            val torrent = req.torrent
            val url = if (torrent == null) {
                req.url
            } else {
                torrentHash = torrent.infoHash
                launch {
                    while (isActive) {
                        Graph.torrents.refresh(torrent.infoHash)
                        delay(1_000)
                    }
                }
                try {
                    Graph.torrents.open(torrent)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fromAddons.cancel()
                    error.value = e.message ?: "Couldn't start this torrent."
                    return@launch
                }
            }
            val extra = withTimeoutOrNull(SUBTITLE_WAIT_MS) { fromAddons.await() } ?: emptyList()
            fromAddons.cancel()
            subtitles = pickSubtitles(req.subtitles, extra)
            playUrl = url
            starting.value = false
            exo.setMediaItem(buildMediaItem(), resumeAt)
            exo.prepare()
        }

        saveJob = lifecycleScope.launch {
            while (isActive) {
                delay(5_000)
                if (exo.isPlaying) save()
            }
        }
    }

    private suspend fun addonSubtitles(): List<SubtitleItem> = try {
        Graph.addons.loadSubtitles(req.type, req.videoId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private fun buildMediaItem(): MediaItem {
        val builder = MediaItem.Builder().setUri(playUrl)
        if (triedHls) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        builder.setSubtitleConfigurations(
            subtitles.map { s ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                    .setMimeType(subtitleMimeType(s.url))
                    .setLanguage(s.lang)
                    .setLabel(listOf(languageName(s.lang), s.source).filter { it.isNotBlank() }.joinToString(" · "))
                    .build()
            }
        )
        return builder.build()
    }

    private fun onError(exo: ExoPlayer, e: PlaybackException) {
        val position = exo.currentPosition
        when {
            // Many live and add-on links are HLS without a ".m3u8" in the address.
            !triedHls && torrentHash == null && e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> {
                triedHls = true
                exo.setMediaItem(buildMediaItem(), position)
                exo.prepare()
            }
            // One broken subtitle file shouldn't stop the video, so try again without them.
            subtitles.isNotEmpty() -> {
                subtitles = emptyList()
                exo.setMediaItem(buildMediaItem(), position)
                exo.prepare()
            }
            else -> error.value = "This stream couldn't be played (${e.errorCodeName})."
        }
    }

    private fun save(ended: Boolean = false) {
        val exo = player ?: return
        val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val position = if (ended && duration > 0) duration else exo.currentPosition
        if (position <= 0 && !ended) return
        val completed = ended || (duration > 0 && position >= duration * 0.92)
        Graph.history.record(
            WatchEntry(
                metaId = req.metaId,
                type = req.type,
                name = req.name,
                poster = req.poster,
                background = req.background,
                videoId = req.videoId,
                season = req.season,
                episode = req.episode,
                episodeTitle = req.episodeTitle,
                positionMs = position,
                durationMs = duration,
                completed = completed,
                updatedAt = System.currentTimeMillis(),
                streamUrl = req.url,
                streamHeaders = req.headers,
                streamTitle = req.streamTitle,
            )
        )
    }

    override fun onStop() {
        super.onStop()
        if (player != null) {
            save()
            player?.pause()
        }
    }

    override fun onDestroy() {
        saveJob?.cancel()
        player?.release()
        player = null
        torrentHash?.let { Graph.torrents.close(it) }
        super.onDestroy()
    }

    private companion object {
        const val SUBTITLE_WAIT_MS = 4_000L
        const val MAX_SUBTITLES = 16
        const val PER_LANGUAGE = 4

        fun subtitleMimeType(url: String): String = when (url.substringBefore('?').substringAfterLast('.', "").lowercase()) {
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
            // Stremio subtitle add-ons almost always serve SRT, often without an extension.
            else -> MimeTypes.APPLICATION_SUBRIP
        }

        fun iso3(lang: String): String {
            val l = lang.trim().lowercase()
            if (l.length == 2) return try {
                Locale(l).isO3Language
            } catch (e: Exception) {
                l
            }
            return l
        }

        fun languageName(lang: String): String {
            val code = iso3(lang)
            val name = Locale(code).getDisplayLanguage(Locale.getDefault())
            return if (name.isNullOrBlank() || name.equals(code, ignoreCase = true)) lang else name
        }

        /** Keeps the stream's own subtitles and a few per language the viewer is likely to want. */
        fun pickSubtitles(own: List<SubtitleItem>, fromAddons: List<SubtitleItem>): List<SubtitleItem> {
            val preferred = listOfNotNull(
                try {
                    Locale.getDefault().isO3Language
                } catch (e: Exception) {
                    null
                },
                "eng",
            ).distinct()
            val picked = own.toMutableList()
            for (lang in preferred) {
                picked += fromAddons.filter { iso3(it.lang) == lang }.take(PER_LANGUAGE)
            }
            if (picked.isEmpty()) picked += fromAddons.take(PER_LANGUAGE)
            return picked.distinctBy { it.url }.take(MAX_SUBTITLES)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(
    player: ExoPlayer,
    req: PlayRequest,
    isTorrent: Boolean,
    starting: Boolean,
    error: String?,
    onBack: () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    val torrentStatus by Graph.torrents.status.collectAsState()

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setControllerShowTimeoutMs(3500)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowSubtitleButton(true)
                    setControllerVisibilityListener(object : PlayerView.ControllerVisibilityListener {
                        override fun onVisibilityChanged(visibility: Int) {
                            controlsVisible = visibility == View.VISIBLE
                        }
                    })
                }
            },
        )

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopStart),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier
                        .widthIn(max = 520.dp)
                        .glassDark(RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(req.name, style = GlassType.headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    req.subtitle?.let {
                        Text(it, style = GlassType.footnote, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        val status = req.torrent?.let { torrentStatus[it.infoHash] }
        if (error == null && (starting || (isTorrent && buffering))) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 360.dp)
                    .glassDark(RoundedCornerShape(24.dp))
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spinner()
                Spacer(Modifier.height(10.dp))
                val headline = when {
                    !isTorrent -> "Loading…"
                    status == null || status.peers == 0 -> "Connecting to peers…"
                    !status.hasMetadata -> "Getting torrent info…"
                    else -> "Buffering…"
                }
                Text(headline, style = GlassType.headline, color = Color.White)
                if (status != null && status.peers > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${status.peers} peer${if (status.peers == 1) "" else "s"} · ${formatRate(status.downloadRate)}",
                        style = GlassType.footnote,
                        color = TextSecondary,
                    )
                }
            }
        }

        error?.let { msg ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 420.dp)
                    .glassDark(RoundedCornerShape(24.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Playback problem", style = GlassType.title3, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(msg, style = GlassType.subhead, color = TextSecondary)
                Spacer(Modifier.height(16.dp))
                GlassButton("Choose another stream", onClick = onBack, primary = true)
            }
        }
    }
}

private fun formatRate(bytesPerSecond: Int): String = when {
    bytesPerSecond >= 1024 * 1024 -> "%.1f MB/s".format(bytesPerSecond / (1024f * 1024f))
    else -> "${bytesPerSecond / 1024} KB/s"
}
