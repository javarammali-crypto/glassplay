package com.glassplay.app.data

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import java.util.concurrent.atomic.AtomicLong
import java.io.RandomAccessFile
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.glassplay.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A GlassPlay release on GitHub. */
data class AppRelease(val number: Int, val name: String, val notes: String, val apkUrl: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float, val bytesPerSecond: Long = 0) : UpdateState
    data class ReadyToInstall(val release: AppRelease, val file: File) : UpdateState
    data class Failed(val message: String, val release: AppRelease?) : UpdateState
}

/**
 * Checks the app's GitHub releases for a newer version, downloads its APK and hands it to
 * Android's installer. Releases are numbered v1, v2, …; every GitHub build knows its own number.
 */
class AppUpdater(private val context: Context, private val scope: CoroutineScope) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val dir = File(context.cacheDir, "updates")
    private var job: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    val currentNumber: Int get() = BuildConfig.RELEASE_NUMBER
    val currentName: String get() = BuildConfig.VERSION_NAME

    /** Looks for an update; [manual] also reports "up to date" and errors instead of staying quiet. */
    fun check(manual: Boolean) {
        if (job?.isActive == true) return
        // Builds made outside GitHub don't know their release number.
        if (currentNumber <= 0 && !manual) return
        job = scope.launch {
            if (manual) _state.value = UpdateState.Checking
            _state.value = try {
                val latest = latestRelease()
                when {
                    latest == null -> if (manual) UpdateState.Failed("No releases found.", null) else UpdateState.Idle
                    latest.number > currentNumber -> UpdateState.Available(latest)
                    manual -> UpdateState.UpToDate
                    else -> UpdateState.Idle
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) UpdateState.Failed("Couldn't check for updates. Check your connection.", null) else UpdateState.Idle
            }
        }
    }

    fun download(release: AppRelease) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            _state.value = try {
                UpdateState.ReadyToInstall(release, fetchApk(release))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed("The download didn't finish. Try again.", release)
            }
        }
    }

    /**
     * Opens Android's installer. The first time, Android needs permission for GlassPlay to
     * install apps; this opens that setting instead, and the viewer taps Install again after.
     */
    fun install(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            _state.value = UpdateState.Failed("This phone has no app installer available.", null)
        }
    }

    fun dismiss() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    private suspend fun latestRelease(): AppRelease? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "GlassPlay")
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 404) return@use null
            if (!resp.isSuccessful) throw IOException("GitHub returned ${resp.code}")
            parseRelease(JSONObject(resp.body?.string() ?: throw IOException("Empty response")))
        }
    }

    private suspend fun fetchApk(release: AppRelease): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val file = File(dir, "GlassPlay-v${release.number}.apk")
        if (file.exists() && release.size > 0 && file.length() == release.size) return@withContext file
        // Older downloads are no longer needed.
        dir.listFiles()?.forEach { it.delete() }
        val partial = File(dir, "${file.name}.part")

        // Ask for the first byte: this follows GitHub's redirect to the real file and says
        // whether it can be fetched in pieces.
        val probe = Request.Builder().url(release.apkUrl).header("User-Agent", "GlassPlay").header("Range", "bytes=0-0").build()
        val (url, total, ranged) = http.newCall(probe).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed (${resp.code})")
            val size = resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: release.size
            Triple(resp.request.url.toString(), size, resp.code == 206 && size > 0)
        }

        val progress = Progress(release, total)
        if (ranged && total >= PARALLEL_MIN_BYTES) {
            // Several connections at once: many networks limit how fast a single download can go.
            RandomAccessFile(partial, "rw").use { it.setLength(total) }
            val part = (total + CONNECTIONS - 1) / CONNECTIONS
            coroutineScope {
                (0 until CONNECTIONS).map { i ->
                    val from = i * part
                    val to = minOf(total, from + part) - 1
                    async { if (from <= to) fetchRange(url, from, to, partial, progress) }
                }.awaitAll()
            }
        } else {
            fetchRange(url, 0, -1, partial, progress)
        }
        if (total > 0 && partial.length() != total) throw IOException("Incomplete download")
        if (!partial.renameTo(file)) throw IOException("Couldn't save the update")
        file
    }

    /** Downloads bytes [from]..[to] (to the end when [to] is -1) into [target] at the same offset. */
    private fun fetchRange(url: String, from: Long, to: Long, target: File, progress: Progress) {
        val req = Request.Builder().url(url).header("User-Agent", "GlassPlay").apply {
            if (to >= 0) header("Range", "bytes=$from-$to")
        }.build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body
            if (!resp.isSuccessful || body == null) throw IOException("Download failed (${resp.code})")
            RandomAccessFile(target, "rw").use { out ->
                out.seek(from)
                val buffer = ByteArray(128 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        progress.add(n.toLong())
                    }
                }
            }
        }
    }

    /** Adds up progress from every connection and reports it about twice a second. */
    private inner class Progress(private val release: AppRelease, private val total: Long) {
        private val done = AtomicLong()
        private val started = System.currentTimeMillis()
        @Volatile private var lastShown = 0L

        fun add(bytes: Long) {
            val now = System.currentTimeMillis()
            val sum = done.addAndGet(bytes)
            if (now - lastShown < 400) return
            lastShown = now
            val elapsed = (now - started).coerceAtLeast(1)
            _state.value = UpdateState.Downloading(
                release,
                if (total > 0) (sum.toFloat() / total).coerceAtMost(1f) else 0f,
                sum * 1000 / elapsed,
            )
        }
    }

    companion object {
        private const val CONNECTIONS = 4
        private const val PARALLEL_MIN_BYTES = 4L * 1024 * 1024

        /** Reads "v7" from the tag, or from a name like "GlassPlay v7" if the tag has no number. */
        fun releaseNumber(tag: String?, name: String?): Int? =
            tag?.let { Regex("^v(\\d+)$", RegexOption.IGNORE_CASE).find(it.trim())?.groupValues?.get(1)?.toIntOrNull() }
                ?: name?.let { Regex("\\bv(\\d+)\\b", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toIntOrNull() }

        fun parseRelease(o: JSONObject): AppRelease? {
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
            val number = releaseNumber(o.str("tag_name"), o.str("name")) ?: return null
            val apk = o.optJSONArray("assets").mapObjects { a ->
                val url = a.str("browser_download_url")
                if (url != null && a.str("name")?.endsWith(".apk", ignoreCase = true) == true) url to a.optLong("size") else null
            }.firstOrNull() ?: return null
            return AppRelease(
                number = number,
                name = o.str("name") ?: "GlassPlay v$number",
                notes = cleanNotes(o.optString("body")),
                apkUrl = apk.first,
                size = apk.second,
            )
        }

        /** Release notes are the merge commit message; drop the parts that aren't for viewers. */
        private fun cleanNotes(body: String): String = body.lines()
            .filterNot { line ->
                val l = line.trim()
                l.startsWith("Merge pull request", true) || l.startsWith("Co-Authored-By", true) || l.startsWith("Claude-Session", true)
            }
            .joinToString("\n")
            .trim()
    }
}
