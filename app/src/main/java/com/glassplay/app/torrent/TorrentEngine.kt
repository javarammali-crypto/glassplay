package com.glassplay.app.torrent

import android.content.Context
import android.os.Environment
import com.glassplay.app.data.TorrentLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.libtorrent4j.FileStorage
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class TorrentStatus(
    val hasMetadata: Boolean,
    val peers: Int,
    val seeds: Int,
    /** Bytes per second. */
    val downloadRate: Int,
    /** Bytes of the wanted files downloaded so far. */
    val downloaded: Long,
)

/** The file a torrent download will produce. */
data class TorrentDownloadTarget(val fileIndex: Int, val path: String, val size: Long, val fileName: String)

data class TorrentDownloadProgress(val bytesDone: Long, val downloadRate: Int, val peers: Int)

/**
 * Streams torrents the way Stremio's streaming server does: the torrent is downloaded
 * on demand, with the pieces the player is reading fetched first, and served to the
 * player over a local HTTP server. The same session also runs downloads kept on the device.
 *
 * libtorrent throws when a torrent is removed while another thread still uses it, so every
 * call that can race with removal is guarded: an uncaught exception on any thread closes the app.
 */
class TorrentEngine(context: Context) {
    private val streamDir = File(context.cacheDir, "torrents")
    val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "GlassPlay")
    private val streams = ConcurrentHashMap<String, TorrentStream>()
    /** Torrents kept for the Downloads tab: info hash → file index. */
    private val downloads = ConcurrentHashMap<String, Int>()
    private val server by lazy { TorrentHttpServer { hash -> streams[hash] } }
    private var session: SessionManager? = null

    private val _status = MutableStateFlow<Map<String, TorrentStatus>>(emptyMap())
    val status: StateFlow<Map<String, TorrentStatus>> = _status

    @Synchronized
    private fun session(): SessionManager {
        session?.let {
            if (it.isPaused) it.resume()
            return it
        }
        // Streaming data left over from a previous run is useless now. Downloads are kept.
        streamDir.deleteRecursively()
        streamDir.mkdirs()
        val settings = SettingsPack()
            .connectionsLimit(200)
            .activeDownloads(6)
            .activeLimit(12)
        val s = SessionManager(false)
        s.start(SessionParams(settings))
        session = s
        return s
    }

    private fun find(infoHash: String): TorrentHandle? = try {
        session?.find(Sha1Hash.parseHex(infoHash))?.takeIf { it.isValid }
    } catch (e: RuntimeException) {
        null
    }

    /** Adds the torrent if needed and waits until its file list is known. */
    private suspend fun addAndAwaitMetadata(link: TorrentLink, dir: File): TorrentHandle {
        val s = session()
        val hash = Sha1Hash.parseHex(link.infoHash)
        if (s.find(hash)?.isValid != true) {
            dir.mkdirs()
            s.download(
                link.toEngineUri(),
                dir,
                TorrentFlags.DEFAULT_DONT_DOWNLOAD.or_(TorrentFlags.SEQUENTIAL_DOWNLOAD),
            )
        }
        val giveUpAt = System.currentTimeMillis() + METADATA_TIMEOUT_MS
        while (true) {
            val h = s.find(hash)
            if (h != null && h.isValid) {
                publish(link.infoHash, h)
                if (h.status().hasMetadata()) {
                    h.unsetFlags(TorrentFlags.AUTO_MANAGED)
                    h.resume()
                    return h
                }
            }
            if (System.currentTimeMillis() > giveUpAt) {
                throw IOException("Couldn't find anyone sharing this torrent. Try another stream.")
            }
            delay(300)
        }
    }

    /** Adds the torrent, waits for its file list and returns a local URL the player can open. */
    suspend fun open(link: TorrentLink): String = withContext(Dispatchers.IO) {
        val handle = try {
            addAndAwaitMetadata(link, streamDir)
        } catch (e: IOException) {
            close(link.infoHash)
            throw e
        }
        val info = handle.torrentFile() ?: throw IOException("Couldn't read this torrent")
        val files = info.files()
        val index = pickFile(link, files)
            ?: throw IOException("This torrent doesn't contain a video")
        val stream = TorrentStream(
            handle = handle,
            fileIndex = index,
            relativePath = files.filePath(index),
            fileName = files.fileName(index),
            fileOffset = files.fileOffset(index),
            length = files.fileSize(index),
            pieceLength = info.pieceLength(),
            numPieces = info.numPieces(),
        )
        streams.put(link.infoHash, stream)?.takeIf { it !== stream }?.closed = true
        applyPriorities(link.infoHash, handle)
        handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
        server.urlFor(link.infoHash, stream.fileName)
    }

    /** Starts (or resumes) downloading the torrent's video into the downloads folder. */
    suspend fun startDownload(link: TorrentLink): TorrentDownloadTarget = withContext(Dispatchers.IO) {
        val handle = addAndAwaitMetadata(link, downloadDir)
        // A torrent that's also streaming lives in the cache; move it somewhere permanent.
        if (File(handle.savePath()).absolutePath != downloadDir.absolutePath) {
            downloadDir.mkdirs()
            handle.moveStorage(downloadDir.absolutePath)
        }
        val files = (handle.torrentFile() ?: throw IOException("Couldn't read this torrent")).files()
        val index = pickFile(link, files) ?: throw IOException("This torrent doesn't contain a video")
        downloads[link.infoHash] = index
        applyPriorities(link.infoHash, handle)
        TorrentDownloadTarget(index, File(downloadDir, files.filePath(index)).absolutePath, files.fileSize(index), files.fileName(index))
    }

    fun downloadProgress(infoHash: String): TorrentDownloadProgress? {
        val index = downloads[infoHash] ?: return null
        val h = find(infoHash) ?: return null
        return try {
            val st = h.status()
            val done = h.fileProgress().getOrNull(index) ?: 0L
            TorrentDownloadProgress(done, st.downloadPayloadRate(), st.numPeers())
        } catch (e: RuntimeException) {
            null
        }
    }

    /** The download finished: stop sharing it but keep the file. */
    fun finishDownload(infoHash: String) {
        downloads.remove(infoHash)
        releaseIfUnused(infoHash, deleteFiles = false)
    }

    /** The download was cancelled or deleted: stop it and delete what it downloaded. */
    fun cancelDownload(infoHash: String) {
        downloads.remove(infoHash)
        releaseIfUnused(infoHash, deleteFiles = true)
    }

    /** Refreshes download stats for the torrent; call periodically while it's open. */
    fun refresh(infoHash: String) {
        find(infoHash)?.let { publish(infoHash, it) }
    }

    private fun publish(infoHash: String, h: TorrentHandle) {
        try {
            val st = h.status()
            val value = TorrentStatus(st.hasMetadata(), st.numPeers(), st.numSeeds(), st.downloadPayloadRate(), st.totalWantedDone())
            _status.update { it + (infoHash to value) }
        } catch (e: RuntimeException) {
            // The torrent was removed meanwhile.
        }
    }

    /** Stops streaming the torrent. Unless it's also being downloaded, its data is deleted. */
    fun close(infoHash: String) {
        streams.remove(infoHash)?.closed = true
        _status.update { it - infoHash }
        releaseIfUnused(infoHash, deleteFiles = true)
    }

    @Synchronized
    private fun releaseIfUnused(infoHash: String, deleteFiles: Boolean) {
        val s = session ?: return
        try {
            val h = s.find(Sha1Hash.parseHex(infoHash))
            if (h != null && h.isValid) {
                if (streams.containsKey(infoHash) || downloads.containsKey(infoHash)) {
                    applyPriorities(infoHash, h)
                } else if (deleteFiles) {
                    s.remove(h, SessionHandle.DELETE_FILES)
                } else {
                    s.remove(h)
                }
            }
            if (streams.isEmpty() && downloads.isEmpty()) s.pause()
        } catch (e: RuntimeException) {
            // Already gone.
        }
    }

    /** Only the files someone is watching or downloading get fetched. */
    private fun applyPriorities(infoHash: String, h: TorrentHandle) {
        try {
            val count = h.torrentFile()?.numFiles() ?: return
            val wanted = setOfNotNull(streams[infoHash]?.fileIndex, downloads[infoHash])
            h.prioritizeFiles(Array(count) { if (it in wanted) Priority.DEFAULT else Priority.IGNORE })
        } catch (e: RuntimeException) {
            // The torrent was removed meanwhile.
        }
    }

    private fun pickFile(link: TorrentLink, files: FileStorage): Int? {
        val candidates = (0 until files.numFiles()).filter { !files.padFileAt(it) }
        // The add-on's file name is the most reliable hint, then its file index.
        link.fileName?.let { name -> candidates.firstOrNull { files.fileName(it).equals(name, ignoreCase = true) }?.let { return it } }
        link.fileIdx?.takeIf { it in candidates && isVideo(files.fileName(it)) }?.let { return it }
        return candidates.filter { isVideo(files.fileName(it)) }.maxByOrNull { files.fileSize(it) }
            ?: link.fileIdx?.takeIf { it in candidates }
            ?: candidates.maxByOrNull { files.fileSize(it) }
    }

    companion object {
        private const val METADATA_TIMEOUT_MS = 90_000L
        private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "wmv", "flv", "mpg", "mpeg", "3gp", "ogv")

        fun isVideo(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
    }
}

/** One file of a torrent being streamed. Reads block until the pieces they need are downloaded. */
class TorrentStream(
    private val handle: TorrentHandle,
    val fileIndex: Int,
    private val relativePath: String,
    val fileName: String,
    private val fileOffset: Long,
    val length: Long,
    private val pieceLength: Int,
    private val numPieces: Int,
) {
    @Volatile var closed = false
    @Volatile private var lastWindowStart = -1
    private val windowPieces = (READ_AHEAD_BYTES / pieceLength).toInt().coerceIn(4, 40)

    /** Where the file is on disk right now (a download can move it out of the cache). */
    val file: File get() = guarded { File(handle.savePath(), relativePath) }

    private fun pieceAt(position: Long): Int = ((fileOffset + position) / pieceLength).toInt()

    /**
     * Waits until the data at [position] is downloaded and returns how many bytes from there
     * can be read right away (at most [max]).
     */
    fun awaitReadable(position: Long, max: Int): Int = guarded {
        val piece = pieceAt(position)
        if (!handle.havePiece(piece)) {
            prioritize(piece)
            val giveUpAt = System.currentTimeMillis() + READ_TIMEOUT_MS
            while (!handle.havePiece(piece)) {
                if (closed || !handle.isValid) throw IOException("Torrent closed")
                if (System.currentTimeMillis() > giveUpAt) throw IOException("Torrent download stalled")
                Thread.sleep(80)
            }
        }
        // Keep the pieces just ahead of the reader at the front of the queue.
        if (piece != lastWindowStart) prioritize(piece)
        val pieceEnd = (piece + 1).toLong() * pieceLength - fileOffset
        minOf(max.toLong(), pieceEnd - position, length - position).toInt()
    }

    private fun prioritize(fromPiece: Int) {
        if (fromPiece < lastWindowStart || fromPiece > lastWindowStart + windowPieces) {
            // The player jumped somewhere else, so earlier urgent pieces don't matter any more.
            handle.clearPieceDeadlines()
        }
        lastWindowStart = fromPiece
        val lastPiece = pieceAt(length - 1)
        for (i in 0..windowPieces) {
            val p = fromPiece + i
            if (p > lastPiece || p >= numPieces) break
            if (!handle.havePiece(p)) handle.setPieceDeadline(p, 50 + i * 120)
        }
    }

    /** Turns libtorrent's errors for a removed torrent into an IOException the server handles. */
    private inline fun <T> guarded(block: () -> T): T {
        if (closed) throw IOException("Torrent closed")
        return try {
            block()
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            throw IOException("Torrent unavailable", e)
        }
    }

    companion object {
        private const val READ_AHEAD_BYTES = 24L * 1024 * 1024
        private const val READ_TIMEOUT_MS = 3 * 60_000L
    }
}
