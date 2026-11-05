package com.glassplay.app.data

import kotlin.math.log10

/** What can be read from a stream's name and description. */
data class StreamFacts(
    /** Vertical resolution: 2160, 1080, 720, 480, or 0 when not stated. */
    val quality: Int,
    val sizeBytes: Long,
    /** Seeders for torrents; -1 when not stated. */
    val seeders: Int,
    val dolbyVision: Boolean,
    val hdr: Boolean,
    /** Served straight from a server (a direct link, or a debrid-cached torrent). */
    val instant: Boolean,
    /** Only in a language other than English (no MULTI/DUAL/ENG marker). */
    val foreignOnly: Boolean,
    val hindi: Boolean,
    /** Filmed in a cinema (CAM, TS, TELESYNC): poor picture and sound. */
    val cam: Boolean = false,
)

/** What the phone and its connection can handle. */
data class PlaybackContext(
    val bandwidthMbps: Double,
    val runtimeSeconds: Long,
    val supportsHdr: Boolean,
    val supportsDolbyVision: Boolean,
    val prefersHindi: Boolean,
)

data class RankedStream(val stream: StreamItem, val facts: StreamFacts, val score: Double) {
    /** A short description such as "1080p · 2.1 GB · 1.6k seeders". */
    val summary: String
        get() = listOfNotNull(
            facts.quality.takeIf { it > 0 }?.let { if (it >= 2160) "4K" else "${it}p" },
            facts.sizeBytes.takeIf { it > 0 }?.let { StreamRanking.formatSize(it) },
            if (facts.instant) "instant" else facts.seeders.takeIf { it >= 0 }?.let { "${StreamRanking.compact(it)} seeders" },
        ).joinToString(" · ")
}

/**
 * Picks the stream most likely to play without buffering: one the connection can keep up
 * with, from a healthy source, in a format the phone shows properly, at the best quality
 * that still fits.
 */
object StreamRanking {
    fun facts(s: StreamItem): StreamFacts {
        val text = listOfNotNull(s.name, s.title, s.filename).joinToString(" ")
        val lower = text.lowercase()
        val quality = when {
            Regex("2160p|\\b4k\\b|\\buhd\\b").containsMatchIn(lower) -> 2160
            Regex("1440p").containsMatchIn(lower) -> 1440
            Regex("1080p|\\bfhd\\b").containsMatchIn(lower) -> 1080
            Regex("720p").containsMatchIn(lower) -> 720
            Regex("480p|576p|\\bsd\\b|\\bdvdrip\\b").containsMatchIn(lower) -> 480
            else -> 0
        }
        val size = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(gb|gib|mb|mib)\\b").findAll(lower).mapNotNull { m ->
            val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            val unit = if (m.groupValues[2].startsWith("g")) 1L shl 30 else 1L shl 20
            (n * unit).toLong()
        }.maxOrNull() ?: 0L
        val seeders = Regex("👤\\s*([0-9]+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("([0-9]+)\\s*(?:seeders|seeds|seed)\\b").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("\\bs:\\s*([0-9]+)").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            ?: -1
        val dv = Regex("\\bdv\\b|dolby ?vision|\\bdovi\\b").containsMatchIn(lower)
        val hdr = dv || Regex("hdr").containsMatchIn(lower)
        val instant = s.url != null || Regex("\\[(rd|ad|pm|tb|dl|oc|ed|pp|tor|torbox|realdebrid|alldebrid)\\+]|⚡|\\binstant\\b|\\bcached\\b").containsMatchIn(lower)
        val tokens = Regex("[a-z]+").findAll(lower).map { it.value }.toSet()
        val foreign = tokens.any { it in FOREIGN } || text.any { it in 'Ѐ'..'ӿ' } || FOREIGN_FLAGS.any { text.contains(it) }
        val english = tokens.any { it in ENGLISH_OK } || text.contains("🇬🇧") || text.contains("🇺🇸")
        val hindi = "hindi" in tokens || "hin" in tokens || text.contains("🇮🇳")
        val cam = tokens.any { it in CAM }
        return StreamFacts(quality, size, seeders, dv, hdr, instant, foreign && !english && !hindi, hindi, cam)
    }

    /** Streams GlassPlay can play itself, best first. */
    fun rank(streams: List<StreamItem>, ctx: PlaybackContext): List<RankedStream> =
        streams.filter { it.url != null || it.isTorrent }
            .map { s -> facts(s).let { f -> RankedStream(s, f, score(f, s.isTorrent, ctx)) } }
            .sortedByDescending { it.score }

    fun score(f: StreamFacts, torrent: Boolean, ctx: PlaybackContext): Double {
        val bandwidth = ctx.bandwidthMbps.coerceAtLeast(1.0)
        var score = when (f.quality) {
            2160 -> if (bandwidth >= 60) 46.0 else 34.0
            1440 -> 36.0
            1080 -> 40.0
            720 -> 28.0
            480 -> 14.0
            else -> 22.0
        }

        // Can the connection (and for torrents, the swarm) keep up with this file?
        val needMbps = when {
            f.sizeBytes > 0 && ctx.runtimeSeconds > 0 -> f.sizeBytes * 8.0 / ctx.runtimeSeconds / 1_000_000
            else -> when (f.quality) { 2160 -> 25.0; 1440 -> 14.0; 1080 -> 8.0; 720 -> 4.0; else -> 3.0 }
        }
        val supply = if (torrent && !f.instant) {
            // Each seeder realistically gives a fraction of a megabit; a big swarm saturates the link.
            val swarm = if (f.seeders >= 0) f.seeders * 0.5 else 4.0
            minOf(bandwidth, swarm)
        } else {
            bandwidth
        }
        val headroom = supply / needMbps
        score += when {
            headroom >= 1.6 -> 12.0
            headroom >= 1.15 -> 4.0
            headroom >= 0.9 -> -12.0
            else -> -40.0
        }

        if (f.instant) score += 22.0
        if (torrent && !f.instant) {
            score += when {
                f.seeders < 0 -> 0.0
                f.seeders < 3 -> -45.0
                f.seeders < 10 -> -15.0
                else -> log10(f.seeders.toDouble()) * 6
            }
            // Huge torrents take long to fill the buffer and to seek around in.
            if (f.sizeBytes > 20L * (1L shl 30)) score -= 10.0
        }

        // Dolby Vision without support shows wrong colours; HDR on a plain screen looks flat.
        if (f.dolbyVision && !ctx.supportsDolbyVision) score -= 18.0
        else if (f.hdr && !ctx.supportsHdr) score -= 5.0

        if (f.foreignOnly) score -= 30.0
        if (f.cam) score -= 35.0
        if (f.hindi && ctx.prefersHindi) score += 8.0
        return score
    }

    private val FOREIGN = setOf(
        "ita", "italian", "french", "truefrench", "vff", "vf", "rus", "russian", "spa", "spanish", "esp", "castellano",
        "latino", "ger", "german", "deu", "por", "portuguese", "dublado", "pol", "polish", "tur", "turkish", "kor", "jpn",
    )
    private val CAM = setOf("cam", "camrip", "hdcam", "ts", "hdts", "telesync", "tc", "telecine")
    private val ENGLISH_OK = setOf("multi", "dual", "eng", "english")
    private val FOREIGN_FLAGS = listOf("🇮🇹", "🇫🇷", "🇷🇺", "🇪🇸", "🇩🇪", "🇧🇷", "🇵🇹", "🇵🇱", "🇹🇷", "🇲🇽")

    fun formatSize(bytes: Long): String =
        if (bytes >= 1L shl 30) "%.1f GB".format(bytes / (1L shl 30).toDouble()) else "${bytes / (1L shl 20)} MB"

    fun compact(n: Int): String = if (n >= 1000) "%.1fk".format(n / 1000.0) else n.toString()
}

/**
 * The ranked streams for what's playing, so the player can switch to the next one if a
 * stream keeps buffering.
 */
object StreamChoices {
    data class Entry(val meta: MetaDetail, val video: Video?, val ranked: List<RankedStream>, val tried: MutableSet<String>)

    @Volatile var current: Entry? = null
        private set

    fun remember(meta: MetaDetail, video: Video?, ranked: List<RankedStream>, playing: StreamItem) {
        current = Entry(meta, video, ranked, mutableSetOf(keyOf(playing)))
    }

    /** The best stream not tried yet for [videoId], or null. */
    fun next(videoId: String): RankedStream? {
        val entry = current ?: return null
        if ((entry.video?.id ?: entry.meta.id) != videoId) return null
        return entry.ranked.firstOrNull { keyOf(it.stream) !in entry.tried && it.score > -20 }
    }

    fun markTried(stream: StreamItem) {
        current?.tried?.add(keyOf(stream))
    }

    fun keyOf(s: StreamItem): String = s.infoHash?.let { "bt:$it:${s.fileIdx ?: -1}" } ?: s.url ?: s.name.orEmpty()
}
