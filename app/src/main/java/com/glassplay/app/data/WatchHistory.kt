package com.glassplay.app.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One title in Continue Watching. For shows, it tracks the latest episode played. */
data class WatchEntry(
    val metaId: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val videoId: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
    val streamUrl: String?,
    val streamHeaders: Map<String, String>,
    val streamTitle: String?,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val episodeLabel: String? get() = if (season != null && episode != null) "S$season E$episode" else null

    fun toJson(): JSONObject = JSONObject().apply {
        put("metaId", metaId); put("type", type); put("name", name)
        put("poster", poster ?: JSONObject.NULL); put("background", background ?: JSONObject.NULL)
        put("videoId", videoId)
        put("season", season ?: JSONObject.NULL); put("episode", episode ?: JSONObject.NULL)
        put("episodeTitle", episodeTitle ?: JSONObject.NULL)
        put("positionMs", positionMs); put("durationMs", durationMs)
        put("completed", completed); put("updatedAt", updatedAt)
        put("streamUrl", streamUrl ?: JSONObject.NULL)
        put("streamTitle", streamTitle ?: JSONObject.NULL)
        put("streamHeaders", JSONObject(streamHeaders as Map<*, *>))
    }

    companion object {
        fun fromJson(o: JSONObject): WatchEntry? {
            val metaId = o.str("metaId") ?: return null
            val headers = mutableMapOf<String, String>()
            o.optJSONObject("streamHeaders")?.let { h -> h.keys().forEach { k -> h.str(k)?.let { headers[k] = it } } }
            return WatchEntry(
                metaId = metaId,
                type = o.str("type") ?: TYPE_MOVIE,
                name = o.str("name") ?: "",
                poster = o.str("poster"),
                background = o.str("background"),
                videoId = o.str("videoId") ?: metaId,
                season = if (o.isNull("season")) null else o.optInt("season"),
                episode = if (o.isNull("episode")) null else o.optInt("episode"),
                episodeTitle = o.str("episodeTitle"),
                positionMs = o.optLong("positionMs"),
                durationMs = o.optLong("durationMs"),
                completed = o.optBoolean("completed"),
                updatedAt = o.optLong("updatedAt"),
                streamUrl = o.str("streamUrl"),
                streamHeaders = headers,
                streamTitle = o.str("streamTitle"),
            )
        }
    }
}

data class VideoProgress(val positionMs: Long, val durationMs: Long, val completed: Boolean) {
    val fraction: Float get() = if (completed) 1f else if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

class WatchHistory(private val prefs: SharedPreferences) {
    private val _entries = MutableStateFlow(loadEntries())
    val entries: StateFlow<List<WatchEntry>> = _entries

    private val _videoProgress = MutableStateFlow(loadProgress())
    val videoProgress: StateFlow<Map<String, VideoProgress>> = _videoProgress

    fun progress(videoId: String): VideoProgress? = _videoProgress.value[videoId]

    @Synchronized
    fun record(entry: WatchEntry) {
        _entries.value = (listOf(entry) + _entries.value.filterNot { it.metaId == entry.metaId }).take(MAX_ENTRIES)
        _videoProgress.value = _videoProgress.value + (entry.videoId to VideoProgress(entry.positionMs, entry.durationMs, entry.completed))
        save()
    }

    @Synchronized
    fun remove(metaId: String) {
        _entries.value = _entries.value.filterNot { it.metaId == metaId }
        save()
    }

    private fun save() {
        val arr = JSONArray()
        _entries.value.forEach { arr.put(it.toJson()) }
        val prog = JSONObject()
        _videoProgress.value.forEach { (k, v) ->
            prog.put(k, JSONObject().put("p", v.positionMs).put("d", v.durationMs).put("c", v.completed))
        }
        prefs.edit().putString(KEY_ENTRIES, arr.toString()).putString(KEY_PROGRESS, prog.toString()).apply()
    }

    private fun loadEntries(): List<WatchEntry> = try {
        JSONArray(prefs.getString(KEY_ENTRIES, "[]")).mapObjects { WatchEntry.fromJson(it) }
            .sortedByDescending { it.updatedAt }
    } catch (e: Exception) {
        emptyList()
    }

    private fun loadProgress(): Map<String, VideoProgress> = try {
        val o = JSONObject(prefs.getString(KEY_PROGRESS, "{}") ?: "{}")
        val out = HashMap<String, VideoProgress>()
        o.keys().forEach { k ->
            o.optJSONObject(k)?.let { v -> out[k] = VideoProgress(v.optLong("p"), v.optLong("d"), v.optBoolean("c")) }
        }
        out
    } catch (e: Exception) {
        emptyMap()
    }

    companion object {
        private const val KEY_ENTRIES = "watch_entries"
        private const val KEY_PROGRESS = "video_progress"
        private const val MAX_ENTRIES = 200
    }
}

class RecentSearches(private val prefs: SharedPreferences) {
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<String>> = _items

    fun add(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        _items.value = (listOf(q) + _items.value.filterNot { it.equals(q, ignoreCase = true) }).take(10)
        prefs.edit().putString(KEY, JSONArray(_items.value).toString()).apply()
    }

    fun clear() {
        _items.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    private fun load(): List<String> = try {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        private const val KEY = "recent_searches"
    }
}
