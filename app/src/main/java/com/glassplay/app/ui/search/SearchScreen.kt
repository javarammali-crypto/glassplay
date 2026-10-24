package com.glassplay.app.ui.search

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.glassplay.app.Graph
import com.glassplay.app.data.CatalogDef
import com.glassplay.app.data.InstalledAddon
import com.glassplay.app.data.MAIN_TYPES
import com.glassplay.app.data.MetaPreview
import com.glassplay.app.data.typeLabel
import com.glassplay.app.ui.components.GlassTextField
import com.glassplay.app.ui.components.LargeTitle
import com.glassplay.app.ui.components.PosterRow
import com.glassplay.app.ui.components.SectionHeader
import com.glassplay.app.ui.components.Spinner
import com.glassplay.app.ui.components.bounceClick
import com.glassplay.app.ui.components.noRippleClick
import com.glassplay.app.ui.theme.Accent
import com.glassplay.app.ui.theme.GlassType
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

@Composable
fun SearchScreen(onOpen: (String, String) -> Unit) {
    val addons by Graph.addons.installed.collectAsState()
    val recents by Graph.recents.items.collectAsState()
    val focus = LocalFocusManager.current

    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }

    val searchCatalogs = remember(addons) {
        addons.flatMap { a -> a.manifest.catalogs.filter { it.supportsSearch && it.type in MAIN_TYPES }.map { a to it } }
            .sortedBy { if (it.second.type == "movie") 0 else 1 }
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item(key = "title") { LargeTitle("Search") }
        item(key = "field") {
            GlassTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Movies, shows and more",
                leading = Icons.Rounded.Search,
                imeAction = ImeAction.Search,
                onSubmit = {
                    Graph.recents.add(query)
                    focus.clearFocus()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
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
                        onPick = { query = it },
                        onClear = { Graph.recents.clear() },
                    )
                    SearchState.Searching -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { Spinner() }
                    is SearchState.Done -> if (s.sections.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No results", style = GlassType.title3, color = Color.White)
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
            Text("Recent", style = GlassType.title3, color = Color.White, modifier = Modifier.weight(1f))
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
                    Text(r, style = GlassType.body, color = Color.White)
                }
            }
        }
    }
}
