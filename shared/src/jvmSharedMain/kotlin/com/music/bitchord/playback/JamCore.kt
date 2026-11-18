package com.music.bitchord.playback

import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/** Where a jam keeps what it has learnt between runs: the platform's own settings store. */
interface JamStore {
    fun get(key: String): String
    fun put(key: String, value: String)
}

/**
 * How much of the jam the listener already knows — the "character" Yandex's My Wave has. Each
 * batch is at most [familiarShare] liked or played tracks.
 */
enum class JamDiscovery(val familiarShare: Double) {
    /** Mostly what the listener loves, a few new ones among it. */
    FAVOURITES(2.0 / 3.0),

    /** Some of theirs, mostly new. */
    BALANCED(1.0 / 3.0),

    /** Nothing they have liked or played. */
    NEW(0.0),
}

/**
 * My Jam on any platform: [JamEngine]'s picks, steered by what the listener does while it plays,
 * as My Wave learns.
 *
 * - A heart grows the next stretch from that track, and favours its artist.
 * - A thumbs-down takes the artist out of the jam, this time and later.
 * - A skip in the first seconds keeps that track out for good and favours its artist a little less.
 * - A track heard to the end becomes a seed, so the jam drifts toward what is really listened to.
 * - What the jam played in the last few days is not played again, so a new jam does not open on
 *   the same hits as yesterday's.
 *
 * Each track carries in its "playing from" which of the listener's tracks it came from.
 */
class JamCore(
    private val store: JamStore,
    stationOf: suspend (Song) -> List<Song>,
    /** The name every jam track carries in the queue, which is how AutoPlay knows to ask here. */
    private val radioName: () -> String,
    /** The "playing from" line for a track grown from [seed]. */
    private val reason: (seed: Song) -> String,
    private val log: (String) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val engine = JamEngine(stationOf)

    private val artistTaste = ConcurrentHashMap<String, Float>()
    private val promoted = ConcurrentLinkedDeque<Song>()
    private val heard = ConcurrentLinkedDeque<Song>()
    @Volatile private var anchor: Song? = null
    @Volatile private var batches = 0

    /** The jam track playing now, so a rating given by id alone can be told apart. */
    @Volatile var current: Song? = null

    private val _blocked = MutableStateFlow(readSet(KEY_BLOCKED))
    /** Artists the listener disliked a track of: the jam brings no more of them. */
    val blockedArtists: StateFlow<Set<String>> = _blocked

    private val _skipped = MutableStateFlow(readSet(KEY_SKIPPED))
    /** Tracks skipped in their first seconds: the jam does not bring them again. */
    val skippedTracks: StateFlow<Set<String>> = _skipped

    /** What the jam has queued lately, id to when: kept out for [MEMORY_MS]. */
    private val played: LinkedHashMap<String, Long> = LinkedHashMap<String, Long>().apply {
        val cutoff = now() - MEMORY_MS
        store.get(KEY_MEMORY).split('\n').forEach { line ->
            val id = line.substringBefore(' ')
            val at = line.substringAfter(' ', "").toLongOrNull() ?: return@forEach
            if (id.isNotBlank() && at >= cutoff) put(id, at)
        }
    }

    private fun readSet(key: String): Set<String> =
        store.get(key).split('\n').map(String::trim).filter(String::isNotEmpty).toSet()

    fun isJam(song: Song?): Boolean = song?.radioName != null && song.radioName == radioName()

    /** Whether the jam is to leave [song] out: an artist disliked, or the track skipped before. */
    fun unwanted(song: Song): Boolean {
        if (song.videoId in _skipped.value) return true
        val blocked = _blocked.value
        return JamEngine.artistKeys(song).any { it in blocked }
    }

    private fun playedLately(song: Song): Boolean = synchronized(played) { song.videoId in played }

    /**
     * Starts a jam from [seeds] — the listener's own tracks, already chosen — and returns its first
     * tracks, or an empty list when there is nothing to grow it from.
     */
    suspend fun start(seeds: List<Song>, familiar: Set<String>, discovery: JamDiscovery, limit: Int): List<Song> {
        if (seeds.isEmpty()) return emptyList()
        engine.start(seeds.filterNot(::unwanted).ifEmpty { seeds }, familiar, discovery.familiarShare)
        reset(null)
        log("jam: from ${seeds.size} seeds, ${discovery.name.lowercase()}")
        return more(emptyList(), limit)
    }

    /**
     * A jam grown from one track: it plays first, its station's tracks are what the jam grows
     * from, and it is come back to every few batches so the jam keeps its feel.
     */
    suspend fun startFrom(song: Song, around: List<Song>, liked: List<Song>, familiar: Set<String>, discovery: JamDiscovery, limit: Int): List<Song> {
        val pool = (around.filter { it.videoId != song.videoId }.take(SEED_POOL) + liked.shuffled().take(TRACK_JAM_TASTE)).distinctBy { it.videoId }
        if (pool.isEmpty()) return emptyList()
        engine.start(pool.filterNot(::unwanted).ifEmpty { pool }, familiar - song.videoId, discovery.familiarShare)
        reset(song)
        return more(listOf(song), limit)
    }

    private fun reset(anchor: Song?) {
        promoted.clear()
        heard.clear()
        this.anchor = anchor
        batches = 0
        saveState()
    }

    /** The rest of a long liked list, read after the jam started, to grow from as well. */
    fun addSeeds(seeds: List<Song>, familiar: Set<String>) {
        engine.addSeeds(seeds.filterNot(::unwanted), familiar)
        saveState()
    }

    /** The listener moved the familiar-to-new setting while a jam plays: from the next batch on. */
    fun setDiscovery(discovery: JamDiscovery) {
        engine.setFamiliarShare(discovery.familiarShare)
        saveState()
    }

    /**
     * The next tracks after [existing]; empty when there is no jam, in this process or saved from
     * an earlier one — AutoPlay then carries on with the plain station.
     */
    suspend fun more(existing: List<Song>, limit: Int): List<Song> {
        if (!engine.active && !engine.restore(store.get(KEY_STATE))) return emptyList()
        // Whose station comes first: a track just liked; then, every few batches, the track a
        // jam from one track was started from; then one heard through; then the seeds in turn.
        val liked = promoted.pollFirst()
        val anchored = anchor?.takeIf { batches % ANCHOR_EVERY == 0 && liked == null }
        val listened = heard.pollFirst()
        batches++
        val picks = engine.more(
            existing = existing,
            limit = limit,
            first = listOfNotNull(liked, anchored, listened),
            unwanted = { unwanted(it) || playedLately(it) },
            preference = { candidate -> JamEngine.artistKeys(candidate).maxOfOrNull { artistTaste[it] ?: 0f } ?: 0f },
            artistCap = { artist -> if ((artistTaste[artist] ?: 0f) >= FAVOURITE) 2 else 1 },
        )
        remember(picks.map { it.song.videoId })
        saveState()
        log("jam: ${picks.size} more in ${engine.sounds} sounds: ${picks.joinToString { "${it.song.artist} (from ${it.seed.title})" }}")
        return picks.map { (song, seed) ->
            song.copy(queueTier = QueueTier.AUTOPLAY, radioName = radioName(), playbackSource = reason(seed))
        }
    }

    private fun remember(ids: List<String>) {
        val at = now()
        val text = synchronized(played) {
            ids.forEach { played.remove(it); played[it] = at }
            val cutoff = at - MEMORY_MS
            val iterator = played.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value < cutoff || played.size > MAX_MEMORY) iterator.remove() else break
            }
            played.entries.joinToString("\n") { "${it.key} ${it.value}" }
        }
        store.put(KEY_MEMORY, text)
    }

    private fun saveState() = store.put(KEY_STATE, engine.save())

    // ---- What the jam learns while it plays ---------------------------------------------------

    /** A heart or a thumbs-down given to [song] while the jam plays. */
    fun onRated(song: Song, status: LikeStatus) {
        val keys = JamEngine.artistKeys(song)
        when (status) {
            LikeStatus.LIKE -> {
                keys.forEach { nudge(it, LIKE_WEIGHT) }
                // Grows the next stations — once per artist, so a run of hearts for one singer
                // does not turn the jam into their discography.
                promoted.removeIf { queued -> JamEngine.artistKeys(queued).any(keys::contains) }
                promoted.addFirst(song)
                while (promoted.size > MAX_PROMOTED) promoted.pollLast()
            }
            LikeStatus.DISLIKE -> {
                // The first-credited artist is the one being judged; a guest on the track is not.
                keys.firstOrNull()?.let { block(it) }
                promoted.removeIf { queued -> JamEngine.artistKeys(queued).any(keys::contains) }
            }
            else -> keys.forEach { nudge(it, -LIKE_WEIGHT) }
        }
        log("jam: ${status.name.lowercase()} '${song.title}' by ${keys.joinToString()}")
    }

    /** A rating given by id alone: counted when it is the jam track playing now. */
    fun onRated(videoId: String, status: LikeStatus) {
        current?.takeIf { it.videoId == videoId }?.let { onRated(it, status) }
    }

    /** [song] skipped in its first seconds. */
    fun onSkipped(song: Song) {
        // The track is the one turned down, not who made it: it never comes back, and the artist
        // still can — only a little less favoured. Leaving an artist out is the thumbs-down's job.
        saveSkipped((_skipped.value + song.videoId).toList().takeLast(MAX_SKIPPED).toSet())
        JamEngine.artistKeys(song).forEach { artistTaste.merge(it, -SKIP_WEIGHT) { a, b -> (a + b).coerceIn(SKIP_FLOOR, TASTE_CEILING) } }
        log("jam: skipped '${song.title}'")
    }

    /** [song] heard to its end. */
    fun onFinished(song: Song) {
        JamEngine.artistKeys(song).forEach { nudge(it, LISTEN_WEIGHT) }
        // Heard through: a seed for later on. One per artist, the newest kept.
        val lead = JamEngine.artistKeys(song).firstOrNull()
        heard.removeIf { queued -> queued.videoId == song.videoId || JamEngine.artistKeys(queued).firstOrNull() == lead }
        heard.addFirst(song)
        while (heard.size > MAX_HEARD) heard.pollLast()
    }

    fun unblock(artist: String) = saveBlocked(_blocked.value - artist.trim().lowercase())

    fun unblockAll() = saveBlocked(emptySet())

    fun clearSkipped() = saveSkipped(emptySet())

    private fun block(artist: String) {
        artistTaste[artist] = SKIPPED_OUT
        saveBlocked((_blocked.value + artist).toList().takeLast(MAX_BLOCKED).toSet())
    }

    private fun saveBlocked(artists: Set<String>) {
        _blocked.value = artists
        store.put(KEY_BLOCKED, artists.joinToString("\n"))
    }

    private fun saveSkipped(ids: Set<String>) {
        _skipped.value = ids
        store.put(KEY_SKIPPED, ids.joinToString("\n"))
    }

    private fun nudge(artist: String, by: Float) {
        artistTaste.merge(artist, by) { a, b -> (a + b).coerceIn(SKIPPED_OUT, TASTE_CEILING) }
    }

    companion object {
        const val KEY_STATE = "state"
        const val KEY_MEMORY = "memory"
        const val KEY_BLOCKED = "blocked_artists"
        const val KEY_SKIPPED = "skipped_tracks"
        const val SEED_POOL = 40
        /** Liked tracks mixed into a jam from one track, so it is still the listener's jam. */
        private const val TRACK_JAM_TASTE = 6
        /** How often, in batches, a jam started from a track goes back to that track's station. */
        private const val ANCHOR_EVERY = 3
        private const val LIKE_WEIGHT = 1f
        private const val SKIP_WEIGHT = 0.75f
        private const val LISTEN_WEIGHT = 0.25f
        private const val FAVOURITE = 1.5f
        private const val TASTE_CEILING = 3f
        /** An artist this far down — disliked — is left out. */
        private const val SKIPPED_OUT = -2.2f
        /** How far skips alone can bring an artist down: out of favour, never out of the jam. */
        private const val SKIP_FLOOR = -1f
        private const val MAX_PROMOTED = 3
        private const val MAX_HEARD = 8
        private const val MAX_BLOCKED = 300
        private const val MAX_SKIPPED = 2_000
        /** How long what the jam played is kept out of it. */
        private const val MEMORY_MS = 4L * 24 * 60 * 60 * 1000
        private const val MAX_MEMORY = 1_500
    }
}
