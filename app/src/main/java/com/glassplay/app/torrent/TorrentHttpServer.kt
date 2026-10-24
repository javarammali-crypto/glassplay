package com.glassplay.app.torrent

import android.net.Uri
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** Serves torrent files to the player over http://127.0.0.1, with Range support for seeking. */
class TorrentHttpServer(private val lookup: (String) -> TorrentStream?) {
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newCachedThreadPool()

    init {
        thread(name = "torrent-http", isDaemon = true) {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                workers.execute { client.use { handle(it) } }
            }
        }
    }

    fun urlFor(infoHash: String, fileName: String): String =
        "http://127.0.0.1:${socket.localPort}/$infoHash/${Uri.encode(fileName)}"

    private fun handle(client: Socket) {
        try {
            val input = client.getInputStream()
            val requestLine = readLine(input) ?: return
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: return
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0)?.uppercase() ?: return
            val path = parts.getOrNull(1) ?: return
            val out = BufferedOutputStream(client.getOutputStream(), 64 * 1024)

            val hash = path.trimStart('/').substringBefore('/')
            val stream = lookup(hash)
            if (stream == null || (method != "GET" && method != "HEAD")) {
                writeHead(out, if (stream == null) "404 Not Found" else "405 Method Not Allowed", listOf("Content-Length" to "0"))
                out.flush()
                return
            }

            val total = stream.length
            var start = 0L
            var end = total - 1
            val range = headers["range"]
            val partial = range != null && range.startsWith("bytes=")
            if (partial) {
                val spec = range!!.removePrefix("bytes=").substringBefore(',')
                val from = spec.substringBefore('-').trim()
                val to = spec.substringAfter('-', "").trim()
                if (from.isEmpty()) {
                    // "bytes=-N": the last N bytes.
                    start = (total - (to.toLongOrNull() ?: 0L)).coerceAtLeast(0L)
                } else {
                    start = from.toLongOrNull() ?: 0L
                    to.toLongOrNull()?.let { end = minOf(it, total - 1) }
                }
                if (start >= total || start > end) {
                    writeHead(out, "416 Range Not Satisfiable", listOf("Content-Range" to "bytes */$total", "Content-Length" to "0"))
                    out.flush()
                    return
                }
            }

            val responseHeaders = mutableListOf(
                "Content-Type" to mimeType(stream.fileName),
                "Accept-Ranges" to "bytes",
                "Content-Length" to (end - start + 1).toString(),
            )
            if (partial) responseHeaders.add("Content-Range" to "bytes $start-$end/$total")
            writeHead(out, if (partial) "206 Partial Content" else "200 OK", responseHeaders)
            if (method == "HEAD") {
                out.flush()
                return
            }

            val buffer = ByteArray(64 * 1024)
            var position = start
            var raf: RandomAccessFile? = null
            try {
                while (position <= end) {
                    val want = minOf(buffer.size.toLong(), end - position + 1).toInt()
                    val ready = stream.awaitReadable(position, want)
                    // The file only appears once its first piece has been written.
                    val file = raf ?: RandomAccessFile(stream.file, "r").also { raf = it }
                    file.seek(position)
                    val n = file.read(buffer, 0, ready)
                    if (n <= 0) {
                        if (stream.closed) break
                        Thread.sleep(50)
                        continue
                    }
                    out.write(buffer, 0, n)
                    position += n
                }
                out.flush()
            } finally {
                raf?.close()
            }
        } catch (e: IOException) {
            // The player closed the connection, usually because it seeked elsewhere.
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun writeHead(out: OutputStream, status: String, headers: List<Pair<String, String>>) {
        val sb = StringBuilder("HTTP/1.1 ").append(status).append("\r\n")
        headers.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.US_ASCII))
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
            if (sb.length > 8192) return null
        }
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts", "m2ts" -> "video/mp2t"
        "mpg", "mpeg" -> "video/mpeg"
        "flv" -> "video/x-flv"
        "wmv" -> "video/x-ms-wmv"
        "3gp" -> "video/3gpp"
        "ogv" -> "video/ogg"
        else -> "application/octet-stream"
    }
}
