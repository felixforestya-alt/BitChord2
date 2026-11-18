package com.music.bitchord.playback

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A bounded queue snapshot for restoring playback after process death.
 *
 * Queue contents are serialized only when Media3 reports that the playlist
 * changed. The frequently changing index and position are stored separately as
 * primitives, so progress sampling never walks or serializes the song list.
 */
object LastPlayed {

    class Snapshot(val songs: List<Song>, val index: Int, val positionMs: Long)

    private lateinit var prefs: SharedPreferences

    /**
     * The index and position, kept apart from the queue. Android rewrites a whole preferences
     * file on every change to it, and beside the queue's JSON — up to 76 tracks — a position saved
     * every five seconds rewrote all of that every five seconds. Stamped with the queue they
     * belong to, so a position saved for an older queue is never read against a newer one.
     */
    private lateinit var progress: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }

    /** The stamp of the queue stored now. */
    @Volatile private var stamp = 0L

    /**
     * The last position saved. A queue saved without one carries it over, as it did when both sat
     * in one file: the playing track is usually still the playing track after a queue edit.
     */
    @Volatile private var lastPositionMs = 0L

    fun init(context: Context) {
        attach(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            context.getSharedPreferences(PROGRESS_PREFS_NAME, Context.MODE_PRIVATE),
        )
    }

    /** Test-only dependency injection; production uses [init]. */
    internal fun initForTesting(testPrefs: SharedPreferences, testProgress: SharedPreferences) =
        attach(testPrefs, testProgress)

    private fun attach(queuePrefs: SharedPreferences, progressPrefs: SharedPreferences) {
        prefs = queuePrefs
        progress = progressPrefs
        stamp = prefs.getLong(KEY_STAMP, 0L)
        // A queue stored before the stamps: given one now, or every position saved against it
        // would be passed over until the queue next changed.
        if (stamp == 0L && prefs.contains(KEY_QUEUE)) prefs.edit().putLong(KEY_STAMP, newStamp()).apply()
        lastPositionMs = when {
            progress.contains(KEY_POSITION) -> progress.getLong(KEY_POSITION, 0L)
            else -> prefs.getLong(KEY_POSITION, 0L)
        }
    }

    private fun newStamp(): Long = (System.nanoTime() xor stamp).let { if (it == 0L) 1L else it }.also { stamp = it }

    internal fun window(size: Int, index: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val safeIndex = index.coerceIn(0, size - 1)
        val start = (safeIndex - MAX_QUEUE_HISTORY).coerceAtLeast(0)
        return start until (safeIndex + 1 + KEEP_AHEAD).coerceAtMost(size)
    }

    /** Persist at most 25 previous entries, the current one, and 50 upcoming entries. */
    fun saveQueue(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) {
            clear()
            return
        }
        val safeIndex = index.coerceIn(songs.indices)
        val bounds = window(songs.size, safeIndex)
        val stored = StoredQueue(
            tracks = songs.subList(bounds.first, bounds.last + 1).map { StoredTrack.from(it) },
        )
        val encoded = runCatching {
            json.encodeToString(StoredQueue.serializer(), stored)
        }.getOrNull() ?: return
        val stamped = newStamp()
        prefs.edit()
            .putString(KEY_QUEUE, encoded)
            .putInt(KEY_INDEX, safeIndex - bounds.first)
            // Beside the queue too, rewritten anyway: what a restart falls back on if it finds
            // this queue on disk but not the progress written after it.
            .putLong(KEY_POSITION, lastPositionMs)
            .putLong(KEY_STAMP, stamped)
            .apply()
        writeProgress(stamped, safeIndex - bounds.first, lastPositionMs).apply()
    }

    /**
     * Atomically replaces the restart snapshot and waits until it reaches disk.
     * Used at an explicit queue boundary, where restoring the queue from before
     * that boundary would be worse than the small cost of a synchronous write.
     */
    fun saveQueueImmediately(songs: List<Song>, index: Int, positionMs: Long) {
        if (songs.isEmpty()) {
            clearImmediately()
            return
        }
        val safeIndex = index.coerceIn(songs.indices)
        val bounds = window(songs.size, safeIndex)
        val stored = StoredQueue(
            tracks = songs.subList(bounds.first, bounds.last + 1).map { StoredTrack.from(it) },
        )
        val encoded = runCatching {
            json.encodeToString(StoredQueue.serializer(), stored)
        }.getOrNull() ?: return
        val stamped = newStamp()
        prefs.edit()
            .putString(KEY_QUEUE, encoded)
            .putInt(KEY_INDEX, safeIndex - bounds.first)
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .putLong(KEY_STAMP, stamped)
            .commit()
        writeProgress(stamped, safeIndex - bounds.first, positionMs).commit()
    }

    /** Update only cheap scalar state; this performs no queue conversion or JSON work. */
    fun savePlaybackState(index: Int, positionMs: Long) {
        if (!prefs.contains(KEY_QUEUE)) return
        writeProgress(stamp, index, positionMs).apply()
    }

    private fun writeProgress(stamped: Long, index: Int, positionMs: Long): SharedPreferences.Editor {
        lastPositionMs = positionMs.coerceAtLeast(0L)
        return progress.edit()
            .putLong(KEY_STAMP, stamped)
            .putInt(KEY_INDEX, index.coerceAtLeast(0))
            .putLong(KEY_POSITION, lastPositionMs)
    }

    fun load(): Snapshot? {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return null
        val stored = runCatching { json.decodeFromString<StoredQueue>(raw) }.getOrNull()
            ?: return null
        if (stored.tracks.isEmpty()) return null
        val songs = stored.tracks.map(StoredTrack::toSong)
        // The progress kept apart, when it is this queue's: the newest there is.
        val kept = progress.takeIf { it.getLong(KEY_STAMP, 0L).let { s -> s != 0L && s == prefs.getLong(KEY_STAMP, -1L) } }
        if (kept != null && kept.contains(KEY_INDEX)) {
            return Snapshot(
                songs = songs,
                index = kept.getInt(KEY_INDEX, 0).coerceIn(songs.indices),
                positionMs = kept.getLong(KEY_POSITION, 0L).coerceAtLeast(0L),
            )
        }
        val index = if (prefs.contains(KEY_INDEX)) {
            prefs.getInt(KEY_INDEX, 0)
        } else {
            stored.legacyIndex ?: 0
        }
        val positionMs = if (prefs.contains(KEY_POSITION)) {
            prefs.getLong(KEY_POSITION, 0L)
        } else {
            stored.legacyPositionMs ?: 0L
        }
        return Snapshot(
            songs = songs,
            index = index.coerceIn(songs.indices),
            positionMs = positionMs.coerceAtLeast(0L),
        )
    }

    fun clear() {
        lastPositionMs = 0L
        prefs.edit().clear().apply()
        progress.edit().clear().apply()
    }

    /** Remove a stale queue before another one is installed. */
    fun clearImmediately() {
        lastPositionMs = 0L
        prefs.edit().clear().commit()
        progress.edit().clear().commit()
    }

    @Serializable
    private data class StoredQueue(
        val tracks: List<StoredTrack>,
        /** Compatibility with queue snapshots written before scalar state was separated. */
        val index: Int? = null,
        /** Compatibility with queue snapshots written before scalar state was separated. */
        val positionMs: Long? = null,
    ) {
        val legacyIndex: Int? get() = index
        val legacyPositionMs: Long? get() = positionMs
    }

    @Serializable
    private data class StoredTrack(
        val id: String,
        val title: String,
        val artist: String,
        val artwork: String? = null,
        val auto: Boolean = false,
        val tier: String? = null,
        val entryId: String? = null,
        val local: String? = null,
        val path: String? = null,
        val duration: String? = null,
        val album: String? = null,
        val explicit: Boolean? = null,
        val video: Boolean = false,
        val radio: String? = null,
        val source: String? = null,
        val sourceType: String? = null,
        val sourceId: String? = null,
    ) {
        fun toSong(): Song {
            val resolvedTier = when (tier) {
                "USER_QUEUE" -> com.music.bitchord.data.model.QueueTier.USER_QUEUE
                "CONTEXT" -> com.music.bitchord.data.model.QueueTier.CONTEXT
                "AUTOPLAY" -> com.music.bitchord.data.model.QueueTier.AUTOPLAY
                else -> if (auto) com.music.bitchord.data.model.QueueTier.AUTOPLAY else com.music.bitchord.data.model.QueueTier.CONTEXT
            }
            return Song(
                videoId = id,
                title = title,
                artist = artist,
                thumbnailUrl = artwork,
                durationText = duration,
                albumName = album,
                isExplicit = explicit,
                isVideo = video,
                queueTier = resolvedTier,
                queueEntryId = entryId,
                radioName = radio,
                playbackSource = source,
                playbackSourceType = sourceType?.let {
                    runCatching { com.music.bitchord.data.model.PlaybackSourceType.valueOf(it) }.getOrNull()
                },
                playbackSourceId = sourceId,
                localUri = local,
                localPath = path,
            )
        }

        companion object {
            fun from(song: Song) = StoredTrack(
                id = song.videoId,
                title = song.title,
                artist = song.artist,
                artwork = song.thumbnailUrl,
                auto = song.fromAutoplay,
                tier = song.queueTier.name,
                entryId = song.queueEntryId,
                local = song.localUri,
                path = song.localPath,
                duration = song.durationText,
                album = song.albumName,
                explicit = song.isExplicit,
                video = song.isVideo,
                radio = song.radioName,
                source = song.playbackSource,
                sourceType = song.playbackSourceType?.name,
                sourceId = song.playbackSourceId,
            )
        }
    }

    private const val KEEP_AHEAD = 50
    private const val PREFS_NAME = "bitchord_last_played"
    private const val KEY_QUEUE = "queue"
    private const val KEY_INDEX = "index"
    private const val KEY_POSITION = "position"
    private const val PROGRESS_PREFS_NAME = "bitchord_last_played_progress"
    private const val KEY_STAMP = "stamp"
}
