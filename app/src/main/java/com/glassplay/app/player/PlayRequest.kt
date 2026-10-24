package com.glassplay.app.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.glassplay.app.data.DownloadItem
import com.glassplay.app.data.DownloadState
import com.glassplay.app.data.MetaDetail
import com.glassplay.app.data.StreamItem
import com.glassplay.app.data.SubtitleItem
import com.glassplay.app.data.TorrentLink
import com.glassplay.app.data.Video
import com.glassplay.app.data.WatchEntry

data class PlayRequest(
    val url: String,
    val headers: Map<String, String>,
    val streamTitle: String?,
    val metaId: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val videoId: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    /** Subtitles that came with the stream itself. */
    val subtitles: List<SubtitleItem> = emptyList(),
) {
    val torrent: TorrentLink? get() = TorrentLink.parse(url)

    val subtitle: String?
        get() {
            val label = if (season != null && episode != null) "S$season E$episode" else null
            return listOfNotNull(label, episodeTitle?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null }
        }

    fun toIntent(context: Context): Intent = Intent(context, PlayerActivity::class.java).apply {
        putExtra("url", url)
        putExtra("headers", Bundle().apply { headers.forEach { (k, v) -> putString(k, v) } })
        putExtra("streamTitle", streamTitle)
        putExtra("metaId", metaId)
        putExtra("type", type)
        putExtra("name", name)
        putExtra("poster", poster)
        putExtra("background", background)
        putExtra("videoId", videoId)
        putExtra("season", season ?: -1)
        putExtra("episode", episode ?: -1)
        putExtra("episodeTitle", episodeTitle)
        putExtra("subUrls", subtitles.map { it.url }.toTypedArray())
        putExtra("subLangs", subtitles.map { it.lang }.toTypedArray())
        putExtra("subSources", subtitles.map { it.source }.toTypedArray())
    }

    companion object {
        fun fromIntent(intent: Intent): PlayRequest? {
            val url = intent.getStringExtra("url") ?: return null
            val hb = intent.getBundleExtra("headers")
            val headers = hb?.keySet()?.associateWith { hb.getString(it).orEmpty() } ?: emptyMap()
            val metaId = intent.getStringExtra("metaId") ?: return null
            val subUrls = intent.getStringArrayExtra("subUrls").orEmpty()
            val subLangs = intent.getStringArrayExtra("subLangs").orEmpty()
            val subSources = intent.getStringArrayExtra("subSources").orEmpty()
            val subtitles = subUrls.mapIndexed { i, u ->
                SubtitleItem(u, u, subLangs.getOrNull(i) ?: "und", subSources.getOrNull(i).orEmpty())
            }
            return PlayRequest(
                url = url,
                headers = headers,
                streamTitle = intent.getStringExtra("streamTitle"),
                metaId = metaId,
                type = intent.getStringExtra("type") ?: "movie",
                name = intent.getStringExtra("name") ?: "",
                poster = intent.getStringExtra("poster"),
                background = intent.getStringExtra("background"),
                videoId = intent.getStringExtra("videoId") ?: metaId,
                season = intent.getIntExtra("season", -1).takeIf { it >= 0 },
                episode = intent.getIntExtra("episode", -1).takeIf { it >= 0 },
                episodeTitle = intent.getStringExtra("episodeTitle"),
                subtitles = subtitles,
            )
        }

        fun fromEntry(e: WatchEntry): PlayRequest? {
            val url = e.streamUrl ?: return null
            return PlayRequest(
                url, e.streamHeaders, e.streamTitle, e.metaId, e.type, e.name, e.poster, e.background,
                e.videoId, e.season, e.episode, e.episodeTitle,
            )
        }
    }
}

/** Opens a stream: direct links and torrents in the built-in player, YouTube and external links outside the app. */
fun playStream(context: Context, meta: MetaDetail, video: Video?, stream: StreamItem) {
    val url = stream.url ?: TorrentLink.fromStream(stream)?.toUri()
    when {
        url != null -> context.startActivity(
            PlayRequest(
                url = url,
                headers = stream.headers,
                streamTitle = stream.name,
                metaId = meta.id,
                type = meta.type,
                name = meta.name,
                poster = meta.poster,
                background = meta.background ?: video?.thumbnail,
                videoId = video?.id ?: meta.id,
                season = video?.season,
                episode = video?.episode,
                episodeTitle = video?.title,
                subtitles = stream.subtitles,
            ).toIntent(context)
        )
        stream.ytId != null -> openLink(context, "https://www.youtube.com/watch?v=${stream.ytId}")
        stream.externalUrl != null -> openLink(context, stream.externalUrl)
    }
}

/**
 * Plays a download: the saved file once it's finished, or a torrent straight from the
 * download in progress (pieces it already has play right away).
 */
fun playDownload(context: Context, item: DownloadItem): Boolean {
    val file = item.file
    val url = when {
        item.state == DownloadState.DONE && file != null && file.exists() -> Uri.fromFile(file).toString()
        item.isTorrent -> item.source
        else -> return false
    }
    context.startActivity(
        PlayRequest(
            url = url,
            headers = emptyMap(),
            streamTitle = item.streamTitle,
            metaId = item.metaId,
            type = item.type,
            name = item.name,
            poster = item.poster,
            background = item.background,
            videoId = item.videoId,
            season = item.season,
            episode = item.episode,
            episodeTitle = item.episodeTitle,
        ).toIntent(context)
    )
    return true
}

fun resumeEntry(context: Context, entry: WatchEntry): Boolean {
    if (entry.completed) return false
    val req = PlayRequest.fromEntry(entry) ?: return false
    context.startActivity(req.toIntent(context))
    return true
}

fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this link", Toast.LENGTH_SHORT).show()
    }
}

fun openInExternalPlayer(context: Context, url: String, title: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), "video/*")
        putExtra("title", title)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No video player app found", Toast.LENGTH_SHORT).show()
    }
}
