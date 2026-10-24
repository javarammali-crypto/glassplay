package com.glassplay.app.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassplay.app.Graph
import com.glassplay.app.data.CatalogDef
import com.glassplay.app.data.InstalledAddon
import com.glassplay.app.data.MetaPreview
import com.glassplay.app.data.TYPE_MOVIE
import com.glassplay.app.data.TYPE_SERIES
import com.glassplay.app.ui.components.GlassButton
import com.glassplay.app.ui.components.GlassChip
import com.glassplay.app.ui.components.GlassSegmented
import com.glassplay.app.ui.components.LargeTitle
import com.glassplay.app.ui.components.PosterCard
import com.glassplay.app.ui.components.Spinner
import com.glassplay.app.ui.theme.GlassType
import com.glassplay.app.ui.theme.TextSecondary
import com.glassplay.app.ui.theme.TextTertiary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Pages through one catalog (optionally filtered by genre). */
class CatalogPager(
    private val addon: InstalledAddon,
    private val cat: CatalogDef,
    private val genre: String?,
) {
    val items = mutableStateListOf<MetaPreview>()
    var loading by mutableStateOf(false)
        private set
    var endReached by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private val seen = HashSet<String>()

    suspend fun loadMore() {
        if (loading || endReached) return
        loading = true
        error = null
        try {
            val extra = buildMap<String, String> {
                genre?.let { put("genre", it) }
                if (items.isNotEmpty()) put("skip", items.size.toString())
            }
            val page = Graph.client.catalog(addon, cat, extra)
            val fresh = page.filter { seen.add(it.type + ":" + it.id) }
            items.addAll(fresh)
            if (fresh.isEmpty() || !cat.supports("skip")) endReached = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load"
        } finally {
            loading = false
        }
    }
}

class DiscoverViewModel : ViewModel() {
    var typeIndex by mutableStateOf(0)
    val catalogIndex = mutableStateMapOf<Int, Int>()
    val genreSel = mutableStateMapOf<String, String?>()
    private val pagers = HashMap<String, CatalogPager>()

    fun pager(key: String, create: () -> CatalogPager): CatalogPager = pagers.getOrPut(key, create)
}

@Composable
fun DiscoverScreen(onOpen: (String, String) -> Unit) {
    val vm: DiscoverViewModel = viewModel()
    val addons by Graph.addons.installed.collectAsState()
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    val type = if (vm.typeIndex == 0) TYPE_MOVIE else TYPE_SERIES
    val catalogs = remember(addons, type) {
        addons.flatMap { a -> a.manifest.catalogs.filter { it.type == type && it.isBrowsable }.map { a to it } }
    }
    val multipleAddons = remember(catalogs) { catalogs.map { it.first.transportUrl }.distinct().size > 1 }

    val catIdx = (vm.catalogIndex[vm.typeIndex] ?: 0).coerceIn(0, (catalogs.size - 1).coerceAtLeast(0))
    val current = catalogs.getOrNull(catIdx)
    val catKey = current?.let { "${it.first.transportUrl}|${it.second.type}|${it.second.id}" }
    val genres = current?.second?.genres.orEmpty()
    val genreRequired = current?.second?.genreRequired == true
    val chosenGenre = catKey?.let { vm.genreSel[it] }
    val genre = chosenGenre ?: if (genreRequired) genres.firstOrNull() else null

    val pager = remember(catKey, genre) {
        current?.let { (a, c) -> vm.pager("$catKey|$genre") { CatalogPager(a, c, genre) } }
    }

    LaunchedEffect(pager) {
        if (pager != null && pager.items.isEmpty()) pager.loadMore()
    }
    LaunchedEffect(gridState, pager) {
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (last, total) ->
            if (pager != null && total > 0 && last >= total - 12) pager.loadMore()
        }
    }

    fun scrollTop() = scope.launch { gridState.scrollToItem(0) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(106.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                LargeTitle("Discover", horizontalPadding = 4.dp)
                GlassSegmented(
                    options = listOf("Movies", "TV Shows"),
                    selected = vm.typeIndex,
                    onSelect = { vm.typeIndex = it; scrollTop() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
                if (catalogs.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        itemsIndexed(catalogs, key = { i, it -> "$i-${it.first.transportUrl}-${it.second.id}" }) { i, (a, c) ->
                            GlassChip(
                                text = if (multipleAddons) "${c.name} · ${a.manifest.name}" else c.name,
                                selected = i == catIdx,
                                onClick = {
                                    vm.catalogIndex[vm.typeIndex] = i
                                    scrollTop()
                                },
                            )
                        }
                    }
                }
                if (genres.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    val options: List<String?> = (if (genreRequired) emptyList() else listOf(null)) + genres
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        itemsIndexed(options, key = { i, g -> "$i-${g ?: "all"}" }) { _, g ->
                            GlassChip(
                                text = g ?: "All genres",
                                selected = g == genre,
                                onClick = {
                                    if (catKey != null) vm.genreSel[catKey] = g
                                    scrollTop()
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        if (current == null) {
            item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing to browse", style = GlassType.title3, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "None of your add-ons offer a catalog for this category.",
                        style = GlassType.subhead,
                        color = TextSecondary,
                    )
                }
            }
        } else if (pager != null) {
            items(pager.items, key = { it.type + ":" + it.id }) { item ->
                PosterCard(item, onClick = { onOpen(item.type, item.id) }, modifier = Modifier.fillMaxWidth())
            }
            if (pager.loading) {
                item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Spinner() }
                }
            }
            pager.error?.let { msg ->
                item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Couldn't load: $msg", style = GlassType.footnote, color = TextTertiary)
                        Spacer(Modifier.height(10.dp))
                        GlassButton("Try again", onClick = { scope.launch { pager.loadMore() } })
                    }
                }
            }
            if (!pager.loading && pager.error == null && pager.items.isEmpty() && pager.endReached) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No titles here.",
                        style = GlassType.subhead,
                        color = TextSecondary,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}
