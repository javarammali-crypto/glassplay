package com.glassplay.app.data

import android.content.SharedPreferences
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Keeps each account's watch history in Firestore, under users/{uid}:
 * titles/{metaId}, videos/{videoId} and activity/{day}_{videoId}. Firestore caches it on
 * the phone, so tracking works offline and syncs when the connection is back.
 * The rules for what counts are in [Tracking].
 */
class WatchTracker(
    private val scope: CoroutineScope,
    private val addons: AddonRepository,
    private val prefs: SharedPreferences,
) {
    private var db: FirebaseFirestore? = null
    private var uid: String? = null
    private val listeners = mutableListOf<ListenerRegistration>()

    private val _titles = MutableStateFlow<Map<String, TitleRecord>>(emptyMap())
    val titles: StateFlow<Map<String, TitleRecord>> = _titles
    private val _videos = MutableStateFlow<Map<String, VideoRecord>>(emptyMap())
    val videos: StateFlow<Map<String, VideoRecord>> = _videos
    private val _activity = MutableStateFlow<List<DayActivity>>(emptyList())
    val activity: StateFlow<List<DayActivity>> = _activity

    /** Follows the signed-in account; null stops tracking. */
    fun bind(firestore: FirebaseFirestore?, userId: String?, history: List<WatchEntry>) {
        if (userId == uid && firestore == db) return
        listeners.forEach { it.remove() }
        listeners.clear()
        _titles.value = emptyMap()
        _videos.value = emptyMap()
        _activity.value = emptyList()
        db = firestore
        uid = userId
        val base = base() ?: return
        listeners += base.collection("titles").addSnapshotListener { snap, _ ->
            snap ?: return@addSnapshotListener
            _titles.value = snap.documents.mapNotNull { titleFrom(it) }.associateBy { it.metaId }
        }
        listeners += base.collection("videos").addSnapshotListener { snap, _ ->
            snap ?: return@addSnapshotListener
            _videos.value = snap.documents.mapNotNull { videoFrom(it) }.associateBy { it.videoId }
        }
        listeners += base.collection("activity").addSnapshotListener { snap, _ ->
            snap ?: return@addSnapshotListener
            _activity.value = snap.documents.mapNotNull { activityFrom(it) }
        }
        importLocalHistory(userId!!, history)
    }

    fun statusOf(metaId: String, now: Long = System.currentTimeMillis()): WatchStatus? {
        val title = _titles.value[metaId] ?: return null
        return Tracking.status(title, _videos.value.values.filter { it.metaId == metaId }, now)
    }

    // ---------- Playback ----------

    /** Tracks one playing session; the player reports progress through it. */
    inner class Session(
        private val metaId: String,
        private val type: String,
        private val name: String,
        private val poster: String?,
        private val background: String?,
        private val videoId: String,
        private val season: Int?,
        private val episode: Int?,
    ) {
        private var pendingMs = 0L
        private var lastFlush = System.currentTimeMillis()
        private var completed = false
        private var described = false
        private var position = 0L
        private var duration = 0L

        /** Called about once a second while the video is actually playing. */
        fun played(elapsedMs: Long, positionMs: Long, durationMs: Long) {
            pendingMs += elapsedMs
            position = positionMs
            if (durationMs > 0) duration = durationMs
            if (!completed && Tracking.isComplete(type, positionMs, duration)) {
                completed = true
                // Count the credits as watched, so the title adds up to its full length.
                pendingMs += (duration - positionMs).coerceAtLeast(0)
                flush(completion = true)
            } else if (System.currentTimeMillis() - lastFlush > 30_000) {
                flush(completion = false)
            }
        }

        /** Saves what's been watched so far; call when playback pauses or stops. */
        fun flush(completion: Boolean = false) {
            val base = base() ?: return
            val seconds = pendingMs / 1000
            pendingMs -= seconds * 1000
            lastFlush = System.currentTimeMillis()
            if (seconds == 0L && !completion && described) return
            val now = System.currentTimeMillis()
            val day = Tracking.dayOf(now)
            val titleUpdate = mutableMapOf<String, Any?>(
                "metaId" to metaId, "type" to type, "name" to name,
                "poster" to poster, "background" to background, "lastActivity" to now,
            )
            // Watching again undoes "dropped" or a manual "watching".
            val manual = _titles.value[metaId]?.manualStatus
            if (manual == "dropped" || manual == "watching") titleUpdate["manualStatus"] = FieldValue.delete()
            base.collection("titles").document(key(metaId)).set(titleUpdate, SetOptions.merge())

            val videoUpdate = mutableMapOf<String, Any?>(
                "videoId" to videoId, "metaId" to metaId, "type" to type,
                "season" to season, "episode" to episode,
            )
            if (duration > 0) videoUpdate["durationMs"] = duration
            if (completion) {
                videoUpdate["completions"] = FieldValue.increment(1)
                videoUpdate["lastCompletedAt"] = now
            }
            base.collection("videos").document(key(videoId)).set(videoUpdate, SetOptions.merge())
            if (seconds > 0 || completion) addActivity(base, day, metaId, videoId, type, seconds, if (completion) 1 else 0)

            if (!described) {
                described = true
                describe(metaId, type)
            }
        }
    }

    fun session(req: com.glassplay.app.player.PlayRequest): Session? {
        if (base() == null) return null
        return Session(req.metaId, req.type, req.name, req.poster, req.background, req.videoId, req.season, req.episode)
    }

    // ---------- Marking by hand ----------

    /** Marks a movie or show as watching, finished or dropped. Finishing counts its runtime. */
    fun markTitle(meta: MetaDetail, status: String) {
        val base = base() ?: return
        val now = System.currentTimeMillis()
        val batch = Batch(base.firestore)
        batch.set(base.collection("titles").document(key(meta.id)), titleFields(meta, now) + mapOf("manualStatus" to status, "manualStatusAt" to now))
        if (status == "finished") {
            if (meta.isSeries) {
                episodesOf(meta, now).filter { (_videos.value[it.id]?.completions ?: 0) == 0 }
                    .forEach { addManualCompletion(batch, base, meta, it, now) }
            } else if ((_videos.value[meta.id]?.completions ?: 0) == 0) {
                addManualCompletion(batch, base, meta, null, now)
            }
        }
        batch.commit()
    }

    /** Removes a title from the history entirely, with the time it counted. */
    fun forget(metaId: String) {
        val base = base() ?: return
        val batch = Batch(base.firestore)
        batch.delete(base.collection("titles").document(key(metaId)))
        _videos.value.values.filter { it.metaId == metaId }.forEach { batch.delete(base.collection("videos").document(key(it.videoId))) }
        _activity.value.filter { it.metaId == metaId }.forEach {
            batch.delete(base.collection("activity").document("${it.day}_${key(it.videoId)}"))
        }
        batch.commit()
    }

    /** Marks one episode (or a movie) as watched or not watched. */
    fun markVideo(meta: MetaDetail, video: Video?, watched: Boolean) {
        val base = base() ?: return
        val now = System.currentTimeMillis()
        val videoId = video?.id ?: meta.id
        val batch = Batch(base.firestore)
        batch.set(base.collection("titles").document(key(meta.id)), titleFields(meta, now))
        if (watched) {
            if ((_videos.value[videoId]?.completions ?: 0) == 0) addManualCompletion(batch, base, meta, video, now)
        } else {
            val record = _videos.value[videoId]
            // Take back the time "mark as watched" added; real watching time stays.
            if (record != null && record.manualSeconds > 0 && record.manualDay != null) {
                batch.set(
                    base.collection("activity").document("${record.manualDay}_${key(videoId)}"),
                    mapOf("seconds" to FieldValue.increment(-record.manualSeconds), "completions" to FieldValue.increment(-1)),
                )
            }
            batch.set(
                base.collection("videos").document(key(videoId)),
                mapOf("completions" to 0, "manualSeconds" to 0, "manualDay" to FieldValue.delete()),
            )
            if (_titles.value[meta.id]?.manualStatus == "finished") {
                batch.set(base.collection("titles").document(key(meta.id)), mapOf("manualStatus" to FieldValue.delete()))
            }
        }
        batch.commit()
    }

    private fun addManualCompletion(batch: Batch, base: DocumentReference, meta: MetaDetail, video: Video?, now: Long) {
        val videoId = video?.id ?: meta.id
        val known = _videos.value[videoId]?.durationMs ?: 0
        val runtime = if (known > 0) known else Tracking.parseRuntimeMs(meta.runtime)
        val seconds = runtime / 1000
        val day = Tracking.dayOf(now)
        batch.set(
            base.collection("videos").document(key(videoId)),
            mapOf(
                "videoId" to videoId, "metaId" to meta.id, "type" to meta.type,
                "season" to video?.season, "episode" to video?.episode,
                "completions" to FieldValue.increment(1), "lastCompletedAt" to now,
                "manualSeconds" to seconds, "manualDay" to day,
            ),
        )
        batch.set(
            base.collection("activity").document("${day}_${key(videoId)}"),
            mapOf(
                "day" to day, "metaId" to meta.id, "videoId" to videoId, "type" to meta.type,
                "seconds" to FieldValue.increment(seconds), "completions" to FieldValue.increment(1),
            ),
        )
    }

    /** Keeps the show details that decide "finished" and "up to date" current. */
    fun describe(meta: MetaDetail) {
        val base = base() ?: return
        if (!_titles.value.containsKey(meta.id)) return
        base.collection("titles").document(key(meta.id)).set(titleFields(meta, null), SetOptions.merge())
    }

    private fun describe(metaId: String, type: String) {
        scope.launch {
            try {
                addons.loadMeta(type, metaId)?.let { describe(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Details are refreshed again next time.
            }
        }
    }

    private fun titleFields(meta: MetaDetail, lastActivity: Long?): Map<String, Any?> {
        val now = System.currentTimeMillis()
        val fields = mutableMapOf<String, Any?>(
            "metaId" to meta.id, "type" to meta.type, "name" to meta.name,
            "poster" to meta.poster, "background" to meta.background,
            "runtimeMs" to Tracking.parseRuntimeMs(meta.runtime),
        )
        if (meta.isSeries) {
            fields["releasedEpisodes"] = Tracking.releasedEpisodes(meta, now)
            fields["ended"] = Tracking.hasEnded(meta, now)
        }
        if (lastActivity != null) fields["lastActivity"] = lastActivity
        return fields
    }

    private fun episodesOf(meta: MetaDetail, now: Long): List<Video> {
        val today = Tracking.dayOf(now)
        return meta.videos.filter { v -> (v.season ?: 1) > 0 && (v.released == null || v.released.take(10) <= today) }
    }

    // ---------- Storage ----------

    private fun base(): DocumentReference? {
        val d = db ?: return null
        val u = uid ?: return null
        return d.collection("users").document(u)
    }

    private fun addActivity(base: DocumentReference, day: String, metaId: String, videoId: String, type: String, seconds: Long, completions: Int) {
        base.collection("activity").document("${day}_${key(videoId)}").set(
            mapOf(
                "day" to day, "metaId" to metaId, "videoId" to videoId, "type" to type,
                "seconds" to FieldValue.increment(seconds), "completions" to FieldValue.increment(completions.toLong()),
            ),
            SetOptions.merge(),
        )
    }

    /** Brings titles from the phone's Continue Watching into a new account's history, once. */
    private fun importLocalHistory(userId: String, history: List<WatchEntry>) {
        val flag = "tracking_imported_$userId"
        if (prefs.getBoolean(flag, false)) return
        prefs.edit().putBoolean(flag, true).apply()
        val base = base() ?: return
        val batch = Batch(base.firestore)
        history.forEach { e ->
            batch.set(
                base.collection("titles").document(key(e.metaId)),
                mapOf(
                    "metaId" to e.metaId, "type" to e.type, "name" to e.name,
                    "poster" to e.poster, "background" to e.background, "lastActivity" to e.updatedAt,
                ),
            )
            if (e.completed) {
                batch.set(
                    base.collection("videos").document(key(e.videoId)),
                    mapOf(
                        "videoId" to e.videoId, "metaId" to e.metaId, "type" to e.type,
                        "season" to e.season, "episode" to e.episode, "durationMs" to e.durationMs,
                        "completions" to FieldValue.increment(1), "lastCompletedAt" to e.updatedAt,
                    ),
                )
            }
        }
        batch.commit()
    }

    /** Firestore batches hold up to 500 writes; long shows need several. */
    private class Batch(private val firestore: FirebaseFirestore) {
        private var current = firestore.batch()
        private var count = 0

        fun set(ref: DocumentReference, data: Map<String, Any?>) {
            roll()
            current.set(ref, data, SetOptions.merge())
            count++
        }

        fun delete(ref: DocumentReference) {
            roll()
            current.delete(ref)
            count++
        }

        private fun roll() {
            if (count >= 450) {
                current.commit()
                current = firestore.batch()
                count = 0
            }
        }

        fun commit() {
            if (count > 0) current.commit()
        }
    }

    private companion object {
        /** Firestore document ids can't contain "/". */
        fun key(id: String) = id.replace('/', '_')

        fun titleFrom(d: DocumentSnapshot): TitleRecord? {
            val metaId = d.getString("metaId") ?: return null
            return TitleRecord(
                metaId = metaId,
                type = d.getString("type") ?: TYPE_MOVIE,
                name = d.getString("name").orEmpty(),
                poster = d.getString("poster"),
                background = d.getString("background"),
                runtimeMs = d.getLong("runtimeMs") ?: 0,
                releasedEpisodes = (d.getLong("releasedEpisodes") ?: 0).toInt(),
                ended = d.getBoolean("ended") ?: false,
                manualStatus = d.getString("manualStatus"),
                manualStatusAt = d.getLong("manualStatusAt") ?: 0,
                lastActivity = d.getLong("lastActivity") ?: 0,
            )
        }

        fun videoFrom(d: DocumentSnapshot): VideoRecord? {
            val videoId = d.getString("videoId") ?: return null
            return VideoRecord(
                videoId = videoId,
                metaId = d.getString("metaId") ?: return null,
                type = d.getString("type") ?: TYPE_MOVIE,
                season = d.getLong("season")?.toInt(),
                episode = d.getLong("episode")?.toInt(),
                durationMs = d.getLong("durationMs") ?: 0,
                completions = (d.getLong("completions") ?: 0).toInt().coerceAtLeast(0),
                lastCompletedAt = d.getLong("lastCompletedAt") ?: 0,
                manualSeconds = d.getLong("manualSeconds") ?: 0,
                manualDay = d.getString("manualDay"),
            )
        }

        fun activityFrom(d: DocumentSnapshot): DayActivity? = DayActivity(
            day = d.getString("day") ?: return null,
            metaId = d.getString("metaId") ?: return null,
            videoId = d.getString("videoId") ?: return null,
            type = d.getString("type") ?: TYPE_MOVIE,
            seconds = (d.getLong("seconds") ?: 0).coerceAtLeast(0),
            completions = (d.getLong("completions") ?: 0).toInt().coerceAtLeast(0),
        )
    }
}
