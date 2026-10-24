package com.glassplay.app.data

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import com.glassplay.app.downloads.DownloadService
import com.glassplay.app.torrent.TorrentEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class DownloadState { PREPARING, DOWNLOADING, DONE, FAILED }

data class DownloadItem(
    val id: String,
    val isTorrent: Boolean,
    /** A magnet link for torrents, the video URL otherwise. */
    val source: String,
    val headers: Map<String, String>,
    val metaId: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val videoId: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val streamTitle: String?,
    val filePath: String?,
    val totalBytes: Long,
    val doneBytes: Long,
    val state: DownloadState,
    val error: String?,
    /** Android DownloadManager id for direct-link downloads. */
    val systemId: Long,
    val createdAt: Long,
    /** Live stats, not saved. */
    val bytesPerSecond: Long = 0,
    val peers: Int = 0,
) {
    val isActive: Boolean get() = state == DownloadState.PREPARING || state == DownloadState.DOWNLOADING
    val progress: Float get() = if (totalBytes > 0) (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val episodeLabel: String? get() = if (season != null && episode != null) "S$season E$episode" else null
    val file: File? get() = filePath?.let { File(it) }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("isTorrent", isTorrent)
        put("source", source)
        put("headers", JSONObject(headers as Map<*, *>))
        put("metaId", metaId)
        put("type", type)
        put("name", name)
        put("poster", poster ?: JSONObject.NULL)
        put("background", background ?: JSONObject.NULL)
        put("videoId", videoId)
        put("season", season ?: JSONObject.NULL)
        put("episode", episode ?: JSONObject.NULL)
        put("episodeTitle", episodeTitle ?: JSONObject.NULL)
        put("streamTitle", streamTitle ?: JSONObject.NULL)
        put("filePath", filePath ?: JSONObject.NULL)
        put("totalBytes", totalBytes)
        put("doneBytes", doneBytes)
        put("state", state.name)
        put("error", error ?: JSONObject.NULL)
        put("systemId", systemId)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): DownloadItem? {
            val id = o.str("id") ?: return null
            val source = o.str("source") ?: return null
            val headers = mutableMapOf<String, String>()
            o.optJSONObject("headers")?.let { h -> h.keys().forEach { k -> h.str(k)?.let { headers[k] = it } } }
            return DownloadItem(
                id = id,
                isTorrent = o.optBoolean("isTorrent"),
                source = source,
                headers = headers,
                metaId = o.str("metaId") ?: return null,
                type = o.str("type") ?: TYPE_MOVIE,
                name = o.str("name") ?: "",
                poster = o.str("poster"),
                background = o.str("background"),
                videoId = o.str("videoId") ?: return null,
                season = if (o.isNull("season")) null else o.optInt("season"),
                episode = if (o.isNull("episode")) null else o.optInt("episode"),
                episodeTitle = o.str("episodeTitle"),
                streamTitle = o.str("streamTitle"),
                filePath = o.str("filePath"),
                totalBytes = o.optLong("totalBytes"),
                doneBytes = o.optLong("doneBytes"),
                state = runCatching { DownloadState.valueOf(o.optString("state")) }.getOrDefault(DownloadState.FAILED),
                error = o.str("error"),
                systemId = o.optLong("systemId", -1),
                createdAt = o.optLong("createdAt"),
            )
        }
    }
}

/** Videos saved on the device: torrents through the torrent engine, direct links through Android's DownloadManager. */
class DownloadRepository(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val torrents: TorrentEngine,
    private val scope: CoroutineScope,
) {
    private val system = context.getSystemService(DownloadManager::class.java)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<DownloadItem>> = _items
    private var poller: Job? = null

    /** Picks unfinished torrent downloads back up after the app restarts. */
    fun resumeAll() {
        _items.value.filter { it.isTorrent && it.isActive }.forEach { startTorrent(it.id) }
        if (_items.value.any { it.isActive }) poll()
    }

    fun completedFor(videoId: String): DownloadItem? =
        _items.value.firstOrNull { it.videoId == videoId && it.state == DownloadState.DONE && it.file?.exists() == true }

    /** Returns false when this stream is already being downloaded. */
    fun start(meta: MetaDetail, video: Video?, stream: StreamItem): Boolean {
        val videoId = video?.id ?: meta.id
        val torrent = TorrentLink.fromStream(stream)
        val source = stream.url ?: torrent?.toUri() ?: return false
        if (_items.value.any { it.videoId == videoId && it.source == source && it.state != DownloadState.FAILED }) return false

        val base = DownloadItem(
            id = UUID.randomUUID().toString(),
            isTorrent = stream.url == null,
            source = source,
            headers = stream.headers,
            metaId = meta.id,
            type = meta.type,
            name = meta.name,
            poster = meta.poster,
            background = meta.background ?: video?.thumbnail,
            videoId = videoId,
            season = video?.season,
            episode = video?.episode,
            episodeTitle = video?.title,
            streamTitle = stream.name?.replace('\n', ' '),
            filePath = null,
            totalBytes = 0,
            doneBytes = 0,
            state = DownloadState.PREPARING,
            error = null,
            systemId = -1,
            createdAt = System.currentTimeMillis(),
        )
        if (base.isTorrent) {
            save(_items.value + base)
            startTorrent(base.id)
        } else {
            save(_items.value + enqueueDirect(base, stream.filename))
        }
        poll()
        return true
    }

    fun retry(id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.isTorrent) {
            change(id) { it.copy(state = DownloadState.PREPARING, error = null) }
            startTorrent(id)
        } else {
            runCatching { system.remove(item.systemId) }
            val fresh = enqueueDirect(item.copy(state = DownloadState.PREPARING, error = null, doneBytes = 0), item.file?.name)
            change(id) { fresh }
        }
        poll()
    }

    fun delete(id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.isTorrent) {
            TorrentLink.parse(item.source)?.let { torrents.cancelDownload(it.infoHash) }
        } else if (item.systemId >= 0) {
            runCatching { system.remove(item.systemId) }
        }
        item.file?.delete()
        save(_items.value.filterNot { it.id == id })
    }

    private fun startTorrent(id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        val link = TorrentLink.parse(item.source)
        if (link == null) {
            change(id) { it.copy(state = DownloadState.FAILED, error = "Invalid torrent link") }
            return
        }
        DownloadService.start(context)
        scope.launch {
            try {
                val target = torrents.startDownload(link)
                change(id) {
                    it.copy(state = DownloadState.DOWNLOADING, filePath = target.path, totalBytes = target.size, error = null)
                }
                poll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                change(id) { it.copy(state = DownloadState.FAILED, error = e.message ?: "Couldn't start the download") }
            }
        }
    }

    private fun enqueueDirect(item: DownloadItem, fileNameHint: String?): DownloadItem = try {
        val fileName = fileNameFor(item, fileNameHint)
        val request = DownloadManager.Request(Uri.parse(item.source))
            .setTitle(listOfNotNull(item.name, item.episodeLabel).joinToString(" · "))
            .setDescription("GlassPlay")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, "GlassPlay/$fileName")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        item.headers.forEach { (k, v) -> request.addRequestHeader(k, v) }
        if (item.headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            request.addRequestHeader("User-Agent", AddonClient.PLAYER_USER_AGENT)
        }
        val id = system.enqueue(request)
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GlassPlay")
        item.copy(systemId = id, filePath = File(dir, fileName).absolutePath, state = DownloadState.DOWNLOADING)
    } catch (e: Exception) {
        item.copy(state = DownloadState.FAILED, error = e.message ?: "Couldn't start the download")
    }

    private fun fileNameFor(item: DownloadItem, hint: String?): String {
        val ext = (hint ?: Uri.parse(item.source).lastPathSegment.orEmpty())
            .substringAfterLast('.', "").lowercase().takeIf { TorrentEngine.isVideo("x.$it") } ?: "mp4"
        val title = listOfNotNull(item.name, item.episodeLabel).joinToString(" ")
            .replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().ifEmpty { "video" }
        return "$title ${item.id.take(6)}.$ext"
    }

    /** Updates progress once a second while anything is downloading. */
    private fun poll() {
        if (poller?.isActive == true) return
        poller = scope.launch {
            var ticks = 0
            while (true) {
                val active = _items.value.filter { it.isActive }
                if (active.isEmpty()) break
                active.forEach { refresh(it) }
                // Progress is saved every few seconds; state changes are saved straight away.
                if (++ticks % 5 == 0) persist(_items.value)
                delay(1_000)
            }
            persist(_items.value)
        }
    }

    private fun refresh(item: DownloadItem) {
        if (item.state != DownloadState.DOWNLOADING) return
        if (item.isTorrent) {
            val hash = TorrentLink.parse(item.source)?.infoHash ?: return
            val p = torrents.downloadProgress(hash) ?: return
            if (item.totalBytes > 0 && p.bytesDone >= item.totalBytes) {
                torrents.finishDownload(hash)
                change(item.id) { it.copy(doneBytes = it.totalBytes, state = DownloadState.DONE, bytesPerSecond = 0, peers = 0) }
            } else {
                _items.update { list ->
                    list.map { if (it.id == item.id) it.copy(doneBytes = p.bytesDone, bytesPerSecond = p.downloadRate.toLong(), peers = p.peers) else it }
                }
            }
            return
        }
        try {
            system.query(DownloadManager.Query().setFilterById(item.systemId)).use { c ->
                if (!c.moveToFirst()) {
                    change(item.id) { it.copy(state = DownloadState.FAILED, error = "The download was cancelled") }
                    return
                }
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> change(item.id) {
                        it.copy(state = DownloadState.DONE, doneBytes = done, totalBytes = maxOf(total, done), bytesPerSecond = 0)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        change(item.id) { it.copy(state = DownloadState.FAILED, error = "The download failed (code $reason)") }
                    }
                    else -> _items.update { list ->
                        list.map {
                            if (it.id == item.id) it.copy(doneBytes = done, totalBytes = maxOf(total, 0), bytesPerSecond = (done - it.doneBytes).coerceAtLeast(0))
                            else it
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Try again on the next tick.
        }
    }

    private fun change(id: String, transform: (DownloadItem) -> DownloadItem) {
        save(_items.value.map { if (it.id == id) transform(it) else it })
    }

    private fun save(list: List<DownloadItem>) {
        _items.value = list
        persist(list)
    }

    private fun persist(list: List<DownloadItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<DownloadItem> = try {
        JSONArray(prefs.getString(KEY, "[]")).mapObjects { DownloadItem.fromJson(it) }
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        private const val KEY = "downloads"
    }
}
