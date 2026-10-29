package com.glassplay.app.data

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
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
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
        val req = Request.Builder().url(release.apkUrl).header("User-Agent", "GlassPlay").build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body
            if (!resp.isSuccessful || body == null) throw IOException("Download failed (${resp.code})")
            val total = body.contentLength().takeIf { it > 0 } ?: release.size
            var done = 0L
            var lastShown = 0f
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        val progress = if (total > 0) done.toFloat() / total else 0f
                        if (progress - lastShown >= 0.01f) {
                            lastShown = progress
                            _state.value = UpdateState.Downloading(release, progress)
                        }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("Incomplete download")
        }
        if (!partial.renameTo(file)) throw IOException("Couldn't save the update")
        file
    }

    companion object {
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
