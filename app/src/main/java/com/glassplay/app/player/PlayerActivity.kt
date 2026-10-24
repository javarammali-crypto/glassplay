package com.glassplay.app.player

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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.glassplay.app.Graph
import com.glassplay.app.data.WatchEntry
import com.glassplay.app.ui.components.GlassButton
import com.glassplay.app.ui.components.GlassIconButton
import com.glassplay.app.ui.theme.GlassTheme
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.glassDark
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var req: PlayRequest
    private var saveJob: Job? = null

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
            .setUserAgent("Mozilla/5.0 (Linux; Android) GlassPlay/1.0")
            .setDefaultRequestProperties(req.headers)
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory)))
            .build()
        player = exo

        // Pick up where the viewer left off, unless it was finished.
        val saved = Graph.history.progress(req.videoId)
        val resumeAt = if (saved != null && !saved.completed && saved.positionMs > 5_000 &&
            (saved.durationMs <= 0 || saved.positionMs < saved.durationMs * 0.95)
        ) saved.positionMs else 0L

        exo.setMediaItem(MediaItem.fromUri(req.url), resumeAt)
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) save(ended = true)
            }
        })
        exo.prepare()
        exo.playWhenReady = true

        setContent {
            GlassTheme {
                PlayerScreen(exo, req, onBack = { finish() })
            }
        }

        saveJob = lifecycleScope.launch {
            while (isActive) {
                delay(5_000)
                if (exo.isPlaying) save()
            }
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
        super.onDestroy()
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(player: ExoPlayer, req: PlayRequest, onBack: () -> Unit) {
    var controlsVisible by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "This stream couldn't be played (${e.errorCodeName})."
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
