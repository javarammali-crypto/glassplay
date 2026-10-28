package com.glassplay.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/*
 * Watch tracking rules, kept free of storage so they're easy to follow and test.
 *
 * - Time watched is real playing time, recorded per day. Skipping ahead adds nothing.
 * - A movie is finished within 15 minutes of its end; an episode within its last 10%
 *   (at least 2 minutes). Finishing tops the time up to the full length, so watching
 *   2h45 of a 3h movie counts as 3 hours.
 * - Rewatching adds the time again but still counts as one title.
 * - Not finished and nothing watched for 15 days: dropped.
 * - A show still airing whose released episodes are all watched is "up to date".
 */

/** A movie or show the viewer has watched or marked. */
data class TitleRecord(
    val metaId: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    /** Movie length, or a typical episode length for shows; 0 when unknown. */
    val runtimeMs: Long,
    /** Episodes released so far (shows), not counting specials; 0 when unknown. */
    val releasedEpisodes: Int,
    /** The show has ended, so watching every episode finishes it. */
    val ended: Boolean,
    /** "watching", "finished" or "dropped" when set by hand. */
    val manualStatus: String?,
    val manualStatusAt: Long,
    val lastActivity: Long,
)

/** One movie or episode. */
data class VideoRecord(
    val videoId: String,
    val metaId: String,
    val type: String,
    val season: Int?,
    val episode: Int?,
    val durationMs: Long,
    val completions: Int,
    val lastCompletedAt: Long,
    /** Time added by "mark as watched", taken back off if it's unmarked. */
    val manualSeconds: Long,
    /** The day [manualSeconds] was added to. */
    val manualDay: String?,
)

/** Watching on one day, for one movie or episode. */
data class DayActivity(
    val day: String,
    val metaId: String,
    val videoId: String,
    val type: String,
    val seconds: Long,
    val completions: Int,
)

enum class WatchStatus(val label: String) {
    WATCHING("Watching"),
    UP_TO_DATE("Up to date"),
    FINISHED("Finished"),
    DROPPED("Dropped"),
}

enum class StatsPeriod(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    WEEK("This week"),
    MONTH("This month"),
    YEAR("This year"),
    ALL("All time"),
}

data class TypeStats(val titles: Int, val seconds: Long, val episodes: Int)

data class PeriodStats(val movies: TypeStats, val shows: TypeStats)

object Tracking {
    const val DROP_AFTER_MS = 15L * 24 * 60 * 60 * 1000
    private const val MOVIE_CREDITS_MS = 15L * 60 * 1000
    private const val EPISODE_MIN_CREDITS_MS = 2L * 60 * 1000

    private val dayFormat = DateTimeFormatter.ISO_LOCAL_DATE

    fun dayOf(timeMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDate().format(dayFormat)

    /** Where in the video it counts as finished. */
    fun completionPointMs(type: String, durationMs: Long): Long {
        if (durationMs <= 0) return Long.MAX_VALUE
        val allowance = if (type == TYPE_MOVIE) {
            MOVIE_CREDITS_MS
        } else {
            maxOf(durationMs / 10, EPISODE_MIN_CREDITS_MS)
        }
        // Very short videos: never let the allowance swallow more than a fifth of it.
        return durationMs - minOf(allowance, durationMs / 5)
    }

    fun isComplete(type: String, positionMs: Long, durationMs: Long): Boolean =
        positionMs >= completionPointMs(type, durationMs)

    fun status(title: TitleRecord, videos: List<VideoRecord>, now: Long): WatchStatus {
        val sinceActivity = now - maxOf(title.lastActivity, title.manualStatusAt)
        when (title.manualStatus) {
            "dropped" -> return WatchStatus.DROPPED
            "finished" -> return WatchStatus.FINISHED
        }
        if (title.type == TYPE_MOVIE) {
            if (videos.any { it.completions > 0 }) return WatchStatus.FINISHED
            return if (sinceActivity > DROP_AFTER_MS) WatchStatus.DROPPED else WatchStatus.WATCHING
        }
        val watched = videos.count { it.completions > 0 && (it.season ?: 1) > 0 }
        if (title.releasedEpisodes > 0 && watched >= title.releasedEpisodes) {
            return if (title.ended) WatchStatus.FINISHED else WatchStatus.UP_TO_DATE
        }
        return if (sinceActivity > DROP_AFTER_MS) WatchStatus.DROPPED else WatchStatus.WATCHING
    }

    /** First and last day (inclusive) of a period; null means no limit. */
    fun range(period: StatsPeriod, today: LocalDate): Pair<LocalDate?, LocalDate?> = when (period) {
        StatsPeriod.TODAY -> today to today
        StatsPeriod.YESTERDAY -> today.minusDays(1) to today.minusDays(1)
        StatsPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to today
        StatsPeriod.MONTH -> today.withDayOfMonth(1) to today
        StatsPeriod.YEAR -> today.withDayOfYear(1) to today
        StatsPeriod.ALL -> null to null
    }

    fun stats(activity: List<DayActivity>, period: StatsPeriod, today: LocalDate): PeriodStats {
        val (from, to) = range(period, today)
        val inRange = activity.filter { a ->
            val day = runCatching { LocalDate.parse(a.day, dayFormat) }.getOrNull() ?: return@filter false
            (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to))
        }
        fun of(type: String, episodes: Boolean): TypeStats {
            val rows = inRange.filter { if (type == TYPE_MOVIE) it.type == TYPE_MOVIE else it.type != TYPE_MOVIE }
                .filter { it.seconds > 0 || it.completions > 0 }
            return TypeStats(
                titles = rows.map { it.metaId }.distinct().size,
                seconds = rows.sumOf { it.seconds },
                episodes = if (episodes) rows.sumOf { it.completions } else 0,
            )
        }
        return PeriodStats(movies = of(TYPE_MOVIE, false), shows = of(TYPE_SERIES, true))
    }

    /** Minutes from runtimes like "152 min", "2h 32min" or "45m"; 0 if there's no number. */
    fun parseRuntimeMs(runtime: String?): Long {
        if (runtime.isNullOrBlank()) return 0
        val hours = Regex("(\\d+)\\s*h").find(runtime)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val minutes = Regex("(\\d+)\\s*m").find(runtime)?.groupValues?.get(1)?.toLongOrNull()
            ?: if (hours == 0L) Regex("(\\d+)").find(runtime)?.groupValues?.get(1)?.toLongOrNull() ?: 0 else 0
        return (hours * 60 + minutes) * 60_000
    }

    /** Episodes out so far, not counting specials. */
    fun releasedEpisodes(meta: MetaDetail, now: Long): Int {
        val today = dayOf(now)
        return meta.videos.count { v -> (v.season ?: 1) > 0 && (v.released == null || v.released.take(10) <= today) }
    }

    /**
     * Whether a show has ended. Cinemeta says so in "status"; without it, a show whose newest
     * episode is over a year old is treated as ended.
     */
    fun hasEnded(meta: MetaDetail, now: Long): Boolean {
        meta.status?.let { return it.equals("Ended", ignoreCase = true) || it.equals("Canceled", ignoreCase = true) }
        val latest = meta.videos.mapNotNull { it.released?.take(10) }.maxOrNull() ?: return false
        return latest < dayOf(now - 365L * 24 * 60 * 60 * 1000)
    }

    fun formatHours(seconds: Long): String {
        val minutes = seconds / 60
        return when {
            minutes < 60 -> "${minutes}m"
            minutes % 60 == 0L -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }
}
