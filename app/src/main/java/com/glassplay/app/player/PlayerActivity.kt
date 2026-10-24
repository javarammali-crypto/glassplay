package com.glassplay.app.player

import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.glassplay.app.Graph
import com.glassplay.app.data.AddonClient
import com.glassplay.app.data.SubtitleItem
import com.glassplay.app.data.WatchEntry
import com.glassplay.app.torrent.TorrentStatus
import com.glassplay.app.ui.components.GlassButton
import com.glassplay.app.ui.components.GlassIconButton
import com.glassplay.app.ui.components.Spinner
import com.glassplay.app.ui.downloads.formatBytes
import com.glassplay.app.ui.theme.GlassTheme
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.TextTertiary
import com.glassplay.app.ui.theme.glassDark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
            .setUserAgent(AddonClient.PLAYER_USER_AGENT)
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
            // Subtitles are found and downloaded while the stream gets ready. Whatever has
            // arrived when the wait runs out is used; a slow subtitle never holds up the video.
            val readySubtitles = ConcurrentHashMap<Int, SubtitleItem>()
            val fromAddons = async { prepareSubtitles(readySubtitles) }
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
            withTimeoutOrNull(SUBTITLE_WAIT_MS) { fromAddons.await() }
            fromAddons.cancel()
            subtitles = readySubtitles.entries.sortedBy { it.key }.map { it.value }
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

    /**
     * Downloads the chosen subtitles to local files and keeps the ones that arrive intact.
     * Loading them straight from the internet inside the player meant one broken file
     * failed playback and every subtitle had to be dropped.
     */
    private suspend fun prepareSubtitles(into: MutableMap<Int, SubtitleItem>) = coroutineScope {
        val dir = File(cacheDir, "subtitles").apply { mkdirs() }
        // Clear out files from earlier days.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        pickSubtitles(req.subtitles, addonSubtitles()).mapIndexed { i, s ->
            launch {
                val text = Graph.client.downloadText(s.url, SUBTITLE_FILE_MAX_BYTES) ?: return@launch
                val ext = subtitleExtension(text) ?: return@launch
                val file = File(dir, "${s.url.hashCode().toUInt()}.$ext")
                try {
                    file.writeText(text)
                    into[i] = s.copy(url = Uri.fromFile(file).toString())
                } catch (e: java.io.IOException) {
                    // Out of space or similar: skip this one.
                }
            }
        }.joinAll()
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
        const val SUBTITLE_WAIT_MS = 8_000L
        const val SUBTITLE_FILE_MAX_BYTES = 4L * 1024 * 1024

        /** Works out the subtitle format from its contents; null if it isn't a subtitle at all. */
        fun subtitleExtension(text: String): String? {
            val head = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t').take(400)
            return when {
                head.startsWith("WEBVTT") -> "vtt"
                head.startsWith("[Script Info]", ignoreCase = true) -> "ass"
                head.startsWith("<?xml") || head.startsWith("<tt") -> "ttml"
                head.contains("-->") -> "srt"
                else -> null
            }
        }
        const val MAX_SUBTITLES = 30
        const val PER_LANGUAGE = 3

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

        /**
         * Keeps the stream's own subtitles, a few choices in the languages the viewer most likely
         * wants (the phone's language, English, and Hindi in India), and one of every other language.
         */
        fun pickSubtitles(own: List<SubtitleItem>, fromAddons: List<SubtitleItem>): List<SubtitleItem> {
            val locale = Locale.getDefault()
            val preferred = listOfNotNull(
                try {
                    locale.isO3Language
                } catch (e: Exception) {
                    null
                },
                "eng",
                "hin".takeIf { locale.country.equals("IN", ignoreCase = true) },
            ).distinct()
            val picked = own.toMutableList()
            for (lang in preferred) {
                picked += fromAddons.filter { iso3(it.lang) == lang }.take(PER_LANGUAGE)
            }
            picked += fromAddons.filter { iso3(it.lang) !in preferred }.distinctBy { iso3(it.lang) }
            return picked.distinctBy { it.url }.take(MAX_SUBTITLES)
        }
    }
}

/** How the video fills the screen; pinch or the button in the top bar switches between them. */
private enum class VideoFit(val label: String, val mode: Int, val icon: ImageVector) {
    FIT("Fit to screen", AspectRatioFrameLayout.RESIZE_MODE_FIT, Icons.Rounded.FitScreen),
    ZOOM("Zoom to fill", AspectRatioFrameLayout.RESIZE_MODE_ZOOM, Icons.Rounded.ZoomOutMap),
    STRETCH("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL, Icons.Rounded.AspectRatio),
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
    var started by remember { mutableStateOf(false) }
    var fit by remember { mutableStateOf(VideoFit.FIT) }
    var fitHint by remember { mutableStateOf<String?>(null) }
    val torrentStatus by Graph.torrents.status.collectAsState()
    val status = req.torrent?.let { torrentStatus[it.infoHash] }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                if (playbackState == Player.STATE_READY) started = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(fitHint) {
        if (fitHint != null) {
            delay(1_200)
            fitHint = null
        }
    }
    val setFit: (VideoFit) -> Unit = {
        fit = it
        fitHint = it.label
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
                    // Pinch out to zoom the video to fill the screen, pinch in to fit it again.
                    var pinched = false
                    var scale = 1f
                    val detector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                            pinched = true
                            scale = 1f
                            return true
                        }

                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            scale *= detector.scaleFactor
                            return true
                        }

                        override fun onScaleEnd(detector: ScaleGestureDetector) {
                            if (scale > 1.12f) setFit(VideoFit.ZOOM) else if (scale < 0.9f) setFit(VideoFit.FIT)
                        }
                    })
                    setOnTouchListener { _, event ->
                        detector.onTouchEvent(event)
                        when {
                            event.actionMasked == MotionEvent.ACTION_DOWN -> {
                                pinched = false
                                false
                            }
                            // Don't let the end of a pinch count as a tap that toggles the controls.
                            pinched || event.pointerCount > 1 -> true
                            else -> false
                        }
                    }
                }
            },
            update = { it.resizeMode = fit.mode },
        )

        AnimatedVisibility(
            visible = controlsVisible && started,
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
                        .weight(1f, fill = false)
                        .widthIn(max = 520.dp)
                        .glassDark(RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(req.name, style = GlassType.headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    req.subtitle?.let {
                        Text(it, style = GlassType.footnote, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.weight(1f))
                GlassIconButton(fit.icon, "Video size: ${fit.label}", {
                    setFit(VideoFit.entries[(fit.ordinal + 1) % VideoFit.entries.size])
                })
            }
        }

        AnimatedVisibility(
            visible = fitHint != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                fitHint.orEmpty(),
                style = GlassType.headline,
                color = Color.White,
                modifier = Modifier.glassDark(CircleShape).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        // Rebuffering a torrent mid-film: show how the download is doing.
        if (error == null && started && isTorrent && buffering) {
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
                Text("Buffering…", style = GlassType.headline, color = Color.White)
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

        AnimatedVisibility(
            visible = error == null && (starting || !started),
            enter = fadeIn(),
            exit = fadeOut(tween(600)),
        ) {
            LoadingScreen(req, isTorrent, status, onBack)
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

/** Full-screen loading state with the title's artwork while the stream gets ready. */
@Composable
private fun LoadingScreen(req: PlayRequest, isTorrent: Boolean, status: TorrentStatus?, onBack: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "loading")
    val zoom by transition.animateFloat(
        1.04f, 1.16f,
        infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "zoom",
    )
    val sweep by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1_400, easing = FastOutSlowInEasing)),
        label = "sweep",
    )
    val stage = when {
        !isTorrent -> "Loading your stream…"
        status == null || status.peers == 0 -> "Connecting to peers…"
        !status.hasMetadata -> "Fetching torrent details…"
        else -> "Buffering the first minutes…"
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = req.background ?: req.poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = zoom; scaleY = zoom }
                .blur(8.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.92f),
                        0.6f to Color.Black.copy(alpha = 0.6f),
                        1f to Color.Black.copy(alpha = 0.3f),
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)))
        )

        GlassIconButton(
            Icons.AutoMirrored.Rounded.ArrowBack,
            "Back",
            onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 24.dp, vertical = 16.dp),
        )

        Row(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (req.poster != null) {
                AsyncImage(
                    model = req.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(124.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.width(28.dp))
            }
            Column(Modifier.widthIn(max = 540.dp)) {
                Text("NOW PLAYING", style = GlassType.caption, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                Text(req.name, style = GlassType.title1, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                req.subtitle?.let {
                    Text(it, style = GlassType.headline, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                req.streamTitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it.replace('\n', ' '), style = GlassType.footnote, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(22.dp))
                // An indeterminate bar: a highlight sweeping across a faint track.
                Box(
                    Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .drawWithContent {
                            val w = size.width * 0.35f
                            val x = (size.width + w) * sweep - w
                            drawRect(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color.White, Color.Transparent),
                                    startX = x,
                                    endX = x + w,
                                ),
                            )
                        }
                )
                Spacer(Modifier.height(12.dp))
                Text(stage, style = GlassType.callout, color = Color.White)
                if (isTorrent && status != null && status.peers > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill(Icons.Rounded.People, "${status.peers} peer${if (status.peers == 1) "" else "s"}")
                        StatPill(Icons.Rounded.Speed, formatRate(status.downloadRate))
                        if (status.downloaded > 0) StatPill(Icons.Rounded.Download, formatBytes(status.downloaded))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(icon: ImageVector, text: String) {
    Row(
        Modifier
            .glassDark(CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = GlassType.footnote, color = Color.White)
    }
}

private fun formatRate(bytesPerSecond: Int): String = when {
    bytesPerSecond >= 1024 * 1024 -> "%.1f MB/s".format(bytesPerSecond / (1024f * 1024f))
    else -> "${bytesPerSecond / 1024} KB/s"
}
