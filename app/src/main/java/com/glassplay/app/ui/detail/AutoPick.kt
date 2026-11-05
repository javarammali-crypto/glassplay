package com.glassplay.app.ui.detail

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glassplay.app.Graph
import com.glassplay.app.data.StreamChoices
import com.glassplay.app.data.StreamRanking
import com.glassplay.app.player.playStream
import com.glassplay.app.player.playbackContext
import com.glassplay.app.ui.components.GlassButton
import com.glassplay.app.ui.components.GlassDialog
import com.glassplay.app.ui.components.Spinner
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.Ink
import com.glassplay.app.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException

/**
 * Asks the add-ons for streams, ranks them for this phone and connection, and plays the
 * best. If nothing suitable turns up, the full list opens instead.
 */
@Composable
fun AutoPickDialog(target: StreamTarget, onChooseMyself: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Warm the subtitle cache alongside, so the player has them ready.
    LaunchedEffect(target) {
        try {
            Graph.addons.loadSubtitles(target.meta.type, target.videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Subtitles are optional.
        }
    }
    LaunchedEffect(target) {
        val streams = try {
            Graph.addons.loadStreams(target.meta.type, target.videoId).groups.flatMap { it.streams }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        val ranked = StreamRanking.rank(streams, playbackContext(context, target.meta))
        val best = ranked.firstOrNull()?.takeIf { it.score > -20 }
        if (best == null) {
            // Nothing good enough to pick blindly: let the viewer look at what there is.
            onChooseMyself()
            return@LaunchedEffect
        }
        StreamChoices.remember(target.meta, target.video, ranked, best.stream)
        onDismiss()
        Toast.makeText(context, "Playing the best stream for you: ${best.summary}", Toast.LENGTH_SHORT).show()
        playStream(context, target.meta, target.video, best.stream)
    }

    GlassDialog(onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Spinner(size = 34) }
        Spacer(Modifier.height(14.dp))
        Text(
            "Finding the best stream…",
            style = GlassType.title3,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val sub = listOfNotNull(target.meta.name, target.video?.label).joinToString(" · ")
        Text(
            "$sub\nChecking quality, size and sources against your connection.",
            style = GlassType.footnote,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        GlassButton("Choose stream myself", onClick = onChooseMyself, modifier = Modifier.fillMaxWidth())
    }
}
