package com.glassplay.app.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.glassplay.app.Graph
import com.glassplay.app.data.CatalogDef
import com.glassplay.app.data.InstalledAddon
import com.glassplay.app.data.MAIN_TYPES
import com.glassplay.app.data.MetaPreview
import com.glassplay.app.data.TYPE_MOVIE
import com.glassplay.app.data.typeLabel
import com.glassplay.app.ui.components.GlassTextField
import com.glassplay.app.ui.components.PosterRow
import com.glassplay.app.ui.components.SectionHeader
import com.glassplay.app.ui.components.Spinner
import com.glassplay.app.ui.components.bounceClick
import com.glassplay.app.ui.components.noRippleClick
import com.glassplay.app.ui.theme.Accent
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.Ink
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.TextTertiary
import com.glassplay.app.ui.theme.glass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

private data class SearchSection(val addon: InstalledAddon, val cat: CatalogDef, val items: List<MetaPreview>)

private sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Done(val query: String, val sections: List<SearchSection>) : SearchState
}

/**
 * Search, opened from the bar on Home: it covers the blurred Home page and shows recent
 * searches until something is typed.
 */
@Composable
fun SearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    val addons by Graph.addons.installed.collectAsState()
    val recents by Graph.recents.items.collectAsState()
    val focus = LocalFocusManager.current

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BackHandler(onBack = onClose)
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }

    val searchCatalogs = remember(addons) {
        addons.flatMap { a -> a.manifest.catalogs.filter { it.supportsSearch }.map { a to it } }
            .sortedBy { (_, cat) -> if (cat.type == TYPE_MOVIE) 0 else if (cat.type in MAIN_TYPES) 1 else 2 }
    }

    LaunchedEffect(query, searchCatalogs) {
        val q = query.trim()
        if (q.length < 2) {
            state = SearchState.Idle
            return@LaunchedEffect
        }
        state = SearchState.Searching
        delay(380)
        val sections = coroutineScope {
            searchCatalogs.map { (a, c) ->
                async {
                    try {
                        SearchSection(a, c, Graph.client.catalog(a, c, mapOf("search" to q)))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        SearchSection(a, c, emptyList())
                    }
                }
            }.awaitAll()
        }.filter { it.items.isNotEmpty() }
        state = SearchState.Done(q, sections)
    }

    val openAndRemember: (MetaPreview) -> Unit = { item ->
        Graph.recents.add(query)
        focus.clearFocus()
        onOpen(item.type, item.id)
    }

    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item(key = "field") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "Movies, shows and more",
                    leading = Icons.Rounded.Search,
                    imeAction = ImeAction.Search,
                    onSubmit = {
                        Graph.recents.add(query)
                        focus.clearFocus()
                    },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
                Text(
                    "Cancel",
                    style = GlassType.callout,
                    color = Accent,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .noRippleClick {
                            focus.clearFocus()
                            onClose()
                        }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
        }

        item(key = "body") {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it::class },
                label = "search",
            ) { s ->
                when (s) {
                    SearchState.Idle -> IdleContent(
                        hasSearchAddons = searchCatalogs.isNotEmpty(),
                        recents = recents,
                        onPick = onQueryChange,
                        onClear = { Graph.recents.clear() },
                    )
                    SearchState.Searching -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { Spinner() }
                    is SearchState.Done -> if (s.sections.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No results", style = GlassType.title3, color = Ink)
                            Spacer(Modifier.height(6.dp))
                            Text("Nothing matched \"${s.query}\".", style = GlassType.subhead, color = TextSecondary)
                        }
                    } else {
                        Column {
                            s.sections.forEach { sec ->
                                SectionHeader(typeLabel(sec.cat.type), subtitle = "${sec.cat.name} · ${sec.addon.manifest.name}")
                                PosterRow(sec.items, openAndRemember)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    hasSearchAddons: Boolean,
    recents: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (!hasSearchAddons || recents.isEmpty()) {
        Text(
            if (!hasSearchAddons) "None of your add-ons support search. Install one from the Add-ons tab."
            else "Search across every installed add-on.",
            style = GlassType.subhead,
            color = if (!hasSearchAddons) TextSecondary else TextTertiary,
            modifier = Modifier.padding(20.dp),
        )
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Recent", style = GlassType.title3, color = Ink, modifier = Modifier.weight(1f))
            Text("Clear", style = GlassType.callout, color = Accent, modifier = Modifier.noRippleClick(onClear))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .glass(RoundedCornerShape(20.dp), strength = 0.8f),
        ) {
            recents.forEach { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bounceClick(0.98f) { onPick(r) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Icon(Icons.Rounded.History, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(r, style = GlassType.body, color = Ink)
                }
            }
        }
    }
}
