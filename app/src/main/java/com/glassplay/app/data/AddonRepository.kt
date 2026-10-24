package com.glassplay.app.data

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class StreamGroup(val addonName: String, val streams: List<StreamItem>, val error: String?)

data class StreamResults(
    val groups: List<StreamGroup>,
    /** How many installed add-ons offer streams for this title. */
    val providers: Int,
    /** Streams found that GlassPlay can't open (torrent-only links). */
    val unsupported: Int,
) {
    val playableCount: Int get() = groups.sumOf { it.streams.size }
}

class AddonRepository(
    private val prefs: SharedPreferences,
    val client: AddonClient,
    private val scope: CoroutineScope,
) {
    private val _installed = MutableStateFlow(load())
    val installed: StateFlow<List<InstalledAddon>> = _installed

    private val _ready = MutableStateFlow(prefs.getBoolean(KEY_SEEDED, false))
    val ready: StateFlow<Boolean> = _ready

    private fun load(): List<InstalledAddon> = try {
        JSONArray(prefs.getString(KEY_LIST, "[]")).mapObjects { o ->
            val url = o.str("url")
            val raw = o.optString("manifest")
            if (url == null || raw.isEmpty()) null
            else runCatching { InstalledAddon(url, parseManifest(JSONObject(raw)), raw) }.getOrNull()
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun persist(list: List<InstalledAddon>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("url", it.transportUrl).put("manifest", it.raw)) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
        _installed.value = list
    }

    /** Installs the default add-ons on first launch, otherwise refreshes manifests. */
    fun bootstrap() {
        scope.launch {
            if (!prefs.getBoolean(KEY_SEEDED, false)) {
                runCatching { client.fetchManifest(CINEMETA_URL) }
                    .onSuccess {
                        install(it)
                        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
                    }
                _ready.value = true
            } else {
                refreshAll()
            }
            seedSubtitles()
        }
    }

    /**
     * Stremio installs OpenSubtitles v3 for everyone, which is why its add-ons rarely come with
     * subtitles of their own. Add it once, for new and existing installs; if the viewer removes
     * it later, it stays removed.
     */
    private suspend fun seedSubtitles() {
        if (prefs.getBoolean(KEY_SUBTITLES_SEEDED, false)) return
        if (_installed.value.any { it.hasResource("subtitles") }) {
            prefs.edit().putBoolean(KEY_SUBTITLES_SEEDED, true).apply()
            return
        }
        runCatching { client.fetchManifest(OPENSUBTITLES_URL) }
            .onSuccess {
                install(it)
                prefs.edit().putBoolean(KEY_SUBTITLES_SEEDED, true).apply()
            }
    }

    fun install(addon: InstalledAddon) {
        val current = _installed.value
        val idx = current.indexOfFirst { it.manifest.id == addon.manifest.id || it.transportUrl == addon.transportUrl }
        persist(if (idx >= 0) current.toMutableList().also { it[idx] = addon } else current + addon)
    }

    fun remove(transportUrl: String) {
        persist(_installed.value.filterNot { it.transportUrl == transportUrl })
    }

    fun moveUp(transportUrl: String) {
        val list = _installed.value.toMutableList()
        val i = list.indexOfFirst { it.transportUrl == transportUrl }
        if (i > 0) {
            val a = list.removeAt(i)
            list.add(i - 1, a)
            persist(list)
        }
    }

    private suspend fun refreshAll() {
        val updated = _installed.value.map { a ->
            try {
                client.fetchManifest(a.transportUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                a
            }
        }
        if (updated != _installed.value) persist(updated)
    }

    /** Details come from the first add-on (in priority order) that has them. */
    suspend fun loadMeta(type: String, id: String): MetaDetail? {
        var lastError: Exception? = null
        for (a in _installed.value.filter { it.supports("meta", type, id) }) {
            try {
                client.meta(a, type, id)?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (lastError != null) throw lastError
        return null
    }

    suspend fun loadStreams(type: String, videoId: String): StreamResults = coroutineScope {
        val providers = _installed.value.filter { it.supports("stream", type, videoId) }
        val results = providers.map { a ->
            async {
                try {
                    a.manifest.name to Result.success(client.streams(a, type, videoId))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    a.manifest.name to Result.failure<List<StreamItem>>(e)
                }
            }
        }.awaitAll()
        var unsupported = 0
        val groups = results.map { (name, res) ->
            val all = res.getOrDefault(emptyList())
            unsupported += all.count { !it.playable }
            StreamGroup(name, all.filter { it.playable }, res.exceptionOrNull()?.let { it.message ?: "Couldn't load" })
        }
        StreamResults(groups, providers.size, unsupported)
    }

    /** Subtitles from every subtitle add-on; ones that fail are skipped. */
    suspend fun loadSubtitles(type: String, videoId: String): List<SubtitleItem> = coroutineScope {
        _installed.value.filter { it.supports("subtitles", type, videoId) }.map { a ->
            async {
                try {
                    client.subtitles(a, type, videoId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }.awaitAll().flatten().distinctBy { it.url }
    }

    companion object {
        const val CINEMETA_URL = "https://v3-cinemeta.strem.io/manifest.json"
        const val OPENSUBTITLES_URL = "https://opensubtitles-v3.strem.io/manifest.json"
        private const val KEY_SUBTITLES_SEEDED = "seeded_subtitles"
        private const val KEY_LIST = "installed_addons"
        private const val KEY_SEEDED = "seeded_defaults"
    }
}
