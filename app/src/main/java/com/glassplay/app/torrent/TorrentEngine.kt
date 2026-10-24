package com.glassplay.app.torrent

import android.content.Context
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
)

/**
 * Streams torrents the way Stremio's streaming server does: the torrent is downloaded
 * on demand, with the pieces the player is reading fetched first, and served to the
 * player over a local HTTP server.
 */
class TorrentEngine(context: Context) {
    private val saveDir = File(context.cacheDir, "torrents")
    private val streams = ConcurrentHashMap<String, TorrentStream>()
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
        // Anything left over from a previous run is useless now.
        saveDir.deleteRecursively()
        saveDir.mkdirs()
        val settings = SettingsPack()
            .connectionsLimit(200)
            .activeDownloads(4)
            .activeLimit(8)
        val s = SessionManager(false)
        s.start(SessionParams(settings))
        session = s
        return s
    }

    /** Adds the torrent, waits for its file list and returns a local URL the player can open. */
    suspend fun open(link: TorrentLink): String = withContext(Dispatchers.IO) {
        val s = session()
        val hash = Sha1Hash.parseHex(link.infoHash)
        if (s.find(hash)?.isValid != true) {
            s.download(
                link.toEngineUri(),
                saveDir,
                TorrentFlags.DEFAULT_DONT_DOWNLOAD.or_(TorrentFlags.SEQUENTIAL_DOWNLOAD),
            )
        }

        val giveUpAt = System.currentTimeMillis() + METADATA_TIMEOUT_MS
        var handle: TorrentHandle
        while (true) {
            val h = s.find(hash)
            if (h != null && h.isValid) {
                handle = h
                publish(link.infoHash, h)
                if (h.status().hasMetadata()) break
            }
            if (System.currentTimeMillis() > giveUpAt) {
                close(link.infoHash)
                throw IOException("Couldn't find anyone sharing this torrent. Try another stream.")
            }
            delay(300)
        }

        handle.unsetFlags(TorrentFlags.AUTO_MANAGED)
        handle.resume()
        val info = handle.torrentFile() ?: throw IOException("Couldn't read this torrent")
        val files = info.files()
        val index = pickFile(link, files)
            ?: throw IOException("This torrent doesn't contain a video")
        handle.prioritizeFiles(Array(files.numFiles()) { if (it == index) Priority.DEFAULT else Priority.IGNORE })
        handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)

        val stream = TorrentStream(
            handle = handle,
            file = File(saveDir, files.filePath(index)),
            fileName = files.fileName(index),
            fileOffset = files.fileOffset(index),
            length = files.fileSize(index),
            pieceLength = info.pieceLength(),
            numPieces = info.numPieces(),
        )
        streams.put(link.infoHash, stream)?.takeIf { it !== stream }?.closed = true
        server.urlFor(link.infoHash, stream.fileName)
    }

    /** Refreshes download stats for the torrent; call periodically while it's open. */
    fun refresh(infoHash: String) {
        val h = session?.find(Sha1Hash.parseHex(infoHash)) ?: return
        if (h.isValid) publish(infoHash, h)
    }

    private fun publish(infoHash: String, h: TorrentHandle) {
        val st = h.status()
        val value = TorrentStatus(st.hasMetadata(), st.numPeers(), st.numSeeds(), st.downloadPayloadRate())
        _status.update { it + (infoHash to value) }
    }

    /** Stops the torrent and deletes what was downloaded. */
    fun close(infoHash: String) {
        streams.remove(infoHash)?.closed = true
        _status.update { it - infoHash }
        synchronized(this) {
            val s = session ?: return
            val h = s.find(Sha1Hash.parseHex(infoHash))
            if (h != null && h.isValid) s.remove(h, SessionHandle.DELETE_FILES)
            if (streams.isEmpty()) s.pause()
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
    val file: File,
    val fileName: String,
    private val fileOffset: Long,
    val length: Long,
    private val pieceLength: Int,
    private val numPieces: Int,
) {
    @Volatile var closed = false
    @Volatile private var lastWindowStart = -1
    private val windowPieces = (READ_AHEAD_BYTES / pieceLength).toInt().coerceIn(4, 40)

    private fun pieceAt(position: Long): Int = ((fileOffset + position) / pieceLength).toInt()

    /**
     * Waits until the data at [position] is downloaded and returns how many bytes from there
     * can be read right away (at most [max]).
     */
    fun awaitReadable(position: Long, max: Int): Int {
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
        return minOf(max.toLong(), pieceEnd - position, length - position).toInt()
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

    companion object {
        private const val READ_AHEAD_BYTES = 24L * 1024 * 1024
        private const val READ_TIMEOUT_MS = 3 * 60_000L
    }
}
