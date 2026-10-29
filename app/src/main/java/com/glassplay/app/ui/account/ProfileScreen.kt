package com.glassplay.app.ui.account

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.glassplay.app.Graph
import com.glassplay.app.account.AccountException
import com.glassplay.app.account.UserProfile
import com.glassplay.app.data.StatsPeriod
import com.glassplay.app.data.TYPE_MOVIE
import com.glassplay.app.data.TitleRecord
import com.glassplay.app.data.Tracking
import com.glassplay.app.data.TypeStats
import com.glassplay.app.data.UpdateState
import com.glassplay.app.data.WatchStatus
import com.glassplay.app.ui.components.GlassButton
import com.glassplay.app.ui.components.GlassChip
import com.glassplay.app.ui.components.GlassDialog
import com.glassplay.app.ui.components.GlassIconButton
import com.glassplay.app.ui.components.GlassSegmented
import com.glassplay.app.ui.components.GlassTextField
import com.glassplay.app.ui.components.SectionHeader
import com.glassplay.app.ui.components.bounceClick
import com.glassplay.app.ui.theme.Accent
import com.glassplay.app.ui.theme.AccentPurple
import com.glassplay.app.ui.theme.Danger
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.TextTertiary
import com.glassplay.app.ui.theme.glass
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Round initials badge for the signed-in viewer. */
@Composable
fun Avatar(profile: UserProfile?, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Accent, AccentPurple))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            profile?.initials ?: "",
            style = if (size >= 60) GlassType.title2 else GlassType.footnote,
            color = Color.White,
        )
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit, onOpen: (String, String) -> Unit) {
    val profile by Graph.accounts.profile.collectAsState()
    val titles by Graph.tracker.titles.collectAsState()
    val videos by Graph.tracker.videos.collectAsState()
    val activity by Graph.tracker.activity.collectAsState()
    val update by Graph.updater.state.collectAsState()
    val scope = rememberCoroutineScope()

    var period by rememberSaveable { mutableStateOf(StatsPeriod.WEEK) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    val stats = remember(activity, period) { Tracking.stats(activity, period, LocalDate.now()) }
    val now = remember(titles, videos) { System.currentTimeMillis() }
    val byStatus = remember(titles, videos) {
        val videosByTitle = videos.values.groupBy { it.metaId }
        titles.values.groupBy { t ->
            when (Tracking.status(t, videosByTitle[t.metaId].orEmpty(), now)) {
                WatchStatus.WATCHING, WatchStatus.UP_TO_DATE -> 0
                WatchStatus.FINISHED -> 1
                WatchStatus.DROPPED -> 2
            }
        }.mapValues { (_, list) -> list.sortedByDescending { maxOf(it.lastActivity, it.manualStatusAt) } }
    }
    val tabs = listOf("Watching", "Finished", "Dropped")
    val shown = byStatus[tab].orEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
        item(key = "header") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(profile, 72)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile?.fullName?.ifBlank { null } ?: "Your profile",
                            style = GlassType.title2,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(profile?.email.orEmpty(), style = GlassType.subhead, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    GlassIconButton(Icons.Rounded.Edit, "Edit name", { editing = true }, size = 40)
                }
            }
        }

        item(key = "periods") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StatsPeriod.entries) { p ->
                    GlassChip(p.label, selected = p == period, onClick = { period = p })
                }
            }
        }

        item(key = "stats") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard("Movies", stats.movies, isShows = false, modifier = Modifier.weight(1f))
                StatCard("TV Shows", stats.shows, isShows = true, modifier = Modifier.weight(1f))
            }
        }

        item(key = "lists-title") { SectionHeader("Your titles") }
        item(key = "tabs") {
            Column(Modifier.padding(horizontal = 20.dp)) {
                GlassSegmented(
                    options = tabs.mapIndexed { i, label -> "$label ${byStatus[i]?.size ?: 0}" },
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                val movies = shown.count { it.type == TYPE_MOVIE }
                val shows = shown.size - movies
                Text(
                    "$movies movie${if (movies == 1) "" else "s"} · $shows show${if (shows == 1) "" else "s"}",
                    style = GlassType.footnote,
                    color = TextTertiary,
                    modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 4.dp),
                )
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    when (tab) {
                        0 -> "Nothing in progress. Titles you start watching show up here."
                        1 -> "Titles you finish show up here."
                        else -> "Titles you stop watching for 15 days, or mark as dropped, show up here."
                    },
                    style = GlassType.subhead,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp),
                )
            }
        }
        items(shown, key = { it.metaId }) { t ->
            TitleRow(t, upToDate = tab == 0 && Graph.tracker.statusOf(t.metaId, now) == WatchStatus.UP_TO_DATE) {
                onOpen(t.type, t.metaId)
            }
        }

        item(key = "app") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .glass(RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("GlassPlay ${Graph.updater.currentName}", style = GlassType.headline, color = Color.White)
                val status = when (val u = update) {
                    UpdateState.Checking -> "Checking for updates…"
                    UpdateState.UpToDate -> "You have the latest version."
                    is UpdateState.Failed -> u.message
                    else -> null
                }
                status?.let { Text(it, style = GlassType.footnote, color = TextSecondary) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Check for updates", onClick = { Graph.updater.check(manual = true) }, modifier = Modifier.weight(1f))
                    GlassButton("Sign out", onClick = { confirmSignOut = true }, contentColor = Danger, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (editing) {
        EditNameDialog(profile, onDismiss = { editing = false })
    }
    if (confirmSignOut) {
        GlassDialog(onDismiss = { confirmSignOut = false }) {
            Text("Sign out?", style = GlassType.title3, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text("Your watch history stays in your account. Sign in again to see it.", style = GlassType.subhead, color = TextSecondary)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Cancel", onClick = { confirmSignOut = false }, modifier = Modifier.weight(1f))
                GlassButton(
                    "Sign out",
                    onClick = {
                        confirmSignOut = false
                        scope.launch { Graph.accounts.signOut() }
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
private fun StatCard(title: String, stats: TypeStats, isShows: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .glass(RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text(title.uppercase(), style = GlassType.caption, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Text("${stats.titles}", style = GlassType.largeTitle, color = Color.White)
        Text(
            if (isShows) "show${if (stats.titles == 1) "" else "s"}" else "movie${if (stats.titles == 1) "" else "s"}",
            style = GlassType.footnote,
            color = TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Text(Tracking.formatHours(stats.seconds), style = GlassType.headline, color = Color.White)
        Text("watched", style = GlassType.caption, color = TextTertiary)
        if (isShows) {
            Spacer(Modifier.height(6.dp))
            Text("${stats.episodes} episode${if (stats.episodes == 1) "" else "s"}", style = GlassType.footnote, color = TextSecondary)
        }
    }
}

@Composable
private fun TitleRow(title: TitleRecord, upToDate: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .bounceClick(0.97f, onClick = onClick)
            .glass(RoundedCornerShape(18.dp), 0.8f)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = title.poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(44.dp)
                .height(66.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title.name, style = GlassType.callout, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val last = maxOf(title.lastActivity, title.manualStatusAt)
            val parts = listOfNotNull(
                if (title.type == TYPE_MOVIE) "Movie" else "TV Show",
                if (upToDate) "Up to date" else null,
                if (last > 0) DateUtils.getRelativeTimeSpanString(last).toString() else null,
            )
            Text(parts.joinToString(" · "), style = GlassType.caption, color = TextTertiary, maxLines = 1)
        }
    }
}

@Composable
private fun EditNameDialog(profile: UserProfile?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf(profile?.firstName.orEmpty()) }
    var last by remember { mutableStateOf(profile?.lastName.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    GlassDialog(onDismiss = onDismiss) {
        Text("Your name", style = GlassType.title3, color = Color.White)
        Spacer(Modifier.height(12.dp))
        GlassTextField(first, { first = it }, "First name", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        GlassTextField(last, { last = it }, "Last name", modifier = Modifier.fillMaxWidth())
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = GlassType.footnote, color = Danger)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            GlassButton(
                "Save",
                primary = true,
                onClick = {
                    if (first.isBlank()) {
                        error = "Enter your first name."
                    } else {
                        scope.launch {
                            try {
                                Graph.accounts.updateName(first, last)
                                onDismiss()
                            } catch (e: AccountException) {
                                error = e.message
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
