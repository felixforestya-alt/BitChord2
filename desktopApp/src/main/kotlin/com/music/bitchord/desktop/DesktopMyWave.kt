package com.music.bitchord.desktop

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.RadioTuning
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Which way the wave is tuned — YouTube Music's own radio tuner, as Yandex's My Wave has "character". */
internal enum class DesktopWaveTuning(
    /** How the tuner's playlist id for this tuning starts, which does not depend on the language. */
    val idPrefix: String,
    /** The chip's label in the languages the tuner is asked in, when the id says nothing. */
    val labels: Set<String>,
) {
    ALL("RDAMVM", setOf("все", "all")),
    POPULAR("RDATpY", setOf("популярное", "popular")),
    RECOMMENDED("RDATiX", setOf("рекомендованное", "recommended", "familiar")),
    DEEP_CUTS("RDATpX", setOf("редкие треки", "deep cuts")),
    PARTY("RDATme", setOf("вечеринка", "party", "pump up")),
}

/** Which tracks the wave starts out from. */
internal enum class DesktopWaveSeeds { LIKED_AND_HISTORY, LIKED, HISTORY, PLAYLIST }

/**
 * My Wave: one button that plays the listener's own endless station, as Yandex Music has it.
 *
 * YouTube Music has the parts — a station around any track, and a tuner on it ("Popular", "Deep
 * cuts", "Party") — but no single station of the listener's own. So the wave takes tracks they
 * like or played lately as seeds, plays a tuned station from one, and when it runs low moves on to
 * the next seed: the queue never ends and never sits on one artist for long. With "only new" it
 * leaves out what is already liked or was played lately, which is the "unfamiliar" setting.
 */
internal object DesktopMyWave {

    private val persistence by lazy { DesktopPersistence() }

    /** The name every wave track carries in the queue, which is how AutoPlay knows to ask here. */
    val radioName: String get() = DesktopStrings["my_jam", "My Jam"]

    private val _tuning = MutableStateFlow(
        runCatching { DesktopWaveTuning.valueOf(persistence.string(KEY_TUNING, DesktopWaveTuning.ALL.name)) }
            .getOrDefault(DesktopWaveTuning.ALL),
    )
    private val _seeds = MutableStateFlow(
        runCatching { DesktopWaveSeeds.valueOf(persistence.string(KEY_SEEDS, DesktopWaveSeeds.LIKED_AND_HISTORY.name)) }
            .getOrDefault(DesktopWaveSeeds.LIKED_AND_HISTORY),
    )
    private val _onlyNew = MutableStateFlow(persistence.boolean(KEY_ONLY_NEW, false))
    private val _playlist = MutableStateFlow(
        persistence.string(KEY_PLAYLIST).split('\n').takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0] to it[1] },
    )

    /** The playlist the jam grows from when [seeds] says so: its id and title. */
    val playlist: StateFlow<Pair<String, String>?> = _playlist

    fun setPlaylist(id: String, title: String) {
        _playlist.value = id to title
        persistence.saveString(KEY_PLAYLIST, "$id\n$title")
    }
    private val _shown = MutableStateFlow(persistence.boolean(KEY_SHOWN, true))

    val tuning: StateFlow<DesktopWaveTuning> = _tuning
    val seeds: StateFlow<DesktopWaveSeeds> = _seeds
    val onlyNew: StateFlow<Boolean> = _onlyNew

    /** Whether the wave's card is on Home at all. */
    val shown: StateFlow<Boolean> = _shown

    fun setTuning(value: DesktopWaveTuning) {
        _tuning.value = value
        persistence.saveString(KEY_TUNING, value.name)
    }

    fun setSeeds(value: DesktopWaveSeeds) {
        _seeds.value = value
        persistence.saveString(KEY_SEEDS, value.name)
    }

    fun setOnlyNew(value: Boolean) {
        _onlyNew.value = value
        persistence.saveBoolean(KEY_ONLY_NEW, value)
    }

    fun setShown(value: Boolean) {
        _shown.value = value
        persistence.saveBoolean(KEY_SHOWN, value)
    }

    // The wave in play: what it grows from, and what it should not bring back.
    @Volatile private var seedPool: List<Song> = emptyList()
    @Volatile private var nextSeed = 0
    @Volatile private var known: Set<String> = emptySet()

    // ---- What the jam learns while it plays ----------------------------------------------------
    // A heart, a thumbs-down, a skip in the first seconds, a track heard to the end: each moves
    // how much of an artist the jam brings next — at once, in the queue already lined up, not
    // only the next time a jam starts.

    /** How the listener has taken each artist this session: up for likes and full listens, down for skips. */
    private val artistTaste = java.util.concurrent.ConcurrentHashMap<String, Float>()

    /** Artists a track was disliked for: the jam brings no more of them, this time or later. */
    private val _blocked = MutableStateFlow(
        persistence.string(KEY_BLOCKED).split('\n').map(String::trim).filter(String::isNotEmpty).toSet(),
    )
    val blockedArtists: StateFlow<Set<String>> = _blocked

    /** Liked tracks waiting to seed the next stations, newest first. */
    private val promoted = java.util.concurrent.ConcurrentLinkedDeque<Song>()

    /** The names a track is credited to, as the jam keys its taste. */
    fun artistKeys(song: Song): List<String> =
        song.artists.map { it.name }.ifEmpty { song.artist.split(ARTIST_SPLIT) }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Whether the jam is to leave [song] out: an artist disliked, or skipped past again and again. */
    fun unwanted(song: Song): Boolean {
        val keys = artistKeys(song)
        val blocked = _blocked.value
        return keys.any { it in blocked || (artistTaste[it] ?: 0f) <= SKIPPED_OUT }
    }

    /** A heart or a thumbs-down given to [song] while the jam plays. */
    fun onRated(song: Song, status: com.music.bitchord.data.model.LikeStatus) {
        val keys = artistKeys(song)
        when (status) {
            com.music.bitchord.data.model.LikeStatus.LIKE -> {
                keys.forEach { nudge(it, LIKE_WEIGHT) }
                // Grows the next stations — once per artist, so a run of hearts for one singer
                // does not turn the jam into their discography.
                promoted.removeIf { queued -> artistKeys(queued).any(keys::contains) }
                promoted.addFirst(song)
                while (promoted.size > MAX_PROMOTED) promoted.pollLast()
            }
            com.music.bitchord.data.model.LikeStatus.DISLIKE -> {
                // The first-credited artist is the one being judged; a guest on the track is not.
                keys.firstOrNull()?.let { block(it) }
                promoted.removeIf { queued -> artistKeys(queued).any(keys::contains) }
            }
            else -> keys.forEach { nudge(it, -LIKE_WEIGHT) }
        }
        DesktopTrackLog.log("jam: ${status.name.lowercase()} '${song.title}' by ${keys.joinToString()}")
    }

    /** [song] skipped in its first seconds. */
    fun onSkipped(song: Song) = artistKeys(song).forEach { nudge(it, -SKIP_WEIGHT) }

    /** [song] heard to its end. */
    fun onFinished(song: Song) = artistKeys(song).forEach { nudge(it, LISTEN_WEIGHT) }

    /** Lets an artist back into the jam. */
    fun unblock(artist: String) = saveBlocked(_blocked.value - artist.trim().lowercase())

    fun unblockAll() = saveBlocked(emptySet())

    private fun block(artist: String) {
        artistTaste[artist] = SKIPPED_OUT
        saveBlocked((_blocked.value + artist).toList().takeLast(MAX_BLOCKED).toSet())
    }

    private fun saveBlocked(artists: Set<String>) {
        _blocked.value = artists
        persistence.saveString(KEY_BLOCKED, artists.joinToString("\n"))
    }

    private fun nudge(artist: String, by: Float) {
        artistTaste.merge(artist, by) { a, b -> (a + b).coerceIn(SKIPPED_OUT, TASTE_CEILING) }
    }

    /**
     * How many of one artist a batch may bring: one, or two for an artist the listener keeps
     * liking — enough to be heard, never enough to take the jam over.
     */
    private fun perArtistCap(artist: String): Int = if ((artistTaste[artist] ?: 0f) >= FAVOURITE) 2 else 1

    /**
     * Starts a wave from [liked] and [history] (most recent first) and returns its first tracks,
     * or an empty list when there is nothing to grow it from.
     */
    suspend fun start(liked: List<Song>, history: List<Song>, fromPlaylist: List<Song> = emptyList(), limit: Int = FIRST_BATCH): List<Song> {
        val pool = when (_seeds.value) {
            DesktopWaveSeeds.PLAYLIST -> fromPlaylist.take(SEED_POOL * 2)
            DesktopWaveSeeds.LIKED -> liked.take(SEED_POOL)
            DesktopWaveSeeds.HISTORY -> history.take(SEED_POOL)
            // Interleaved, so neither side crowds the other out.
            DesktopWaveSeeds.LIKED_AND_HISTORY -> interleave(history.take(SEED_POOL), liked.take(SEED_POOL))
        }.distinctBy { it.videoId }.ifEmpty { (liked + history).distinctBy { it.videoId }.take(SEED_POOL) }
        if (pool.isEmpty()) return emptyList()
        // A different start each time, as the wave is meant to feel — and none of what was
        // disliked to grow it from.
        seedPool = pool.filterNot(::unwanted).ifEmpty { pool }.shuffled()
        nextSeed = 0
        promoted.clear()
        known = if (_onlyNew.value) (liked + history + fromPlaylist).mapTo(HashSet(), Song::videoId) else emptySet()
        return more(emptyList(), limit)
    }

    /** The next tracks after [existing]: from the next seed's tuned station. */
    suspend fun more(existing: List<Song>, limit: Int): List<Song> {
        val pool = seedPool
        if (pool.isEmpty()) return emptyList()
        val have = existing.mapTo(HashSet(), Song::videoId)
        val out = ArrayList<Song>(limit)
        // How many of each artist this batch has, counting the last few already queued: no artist
        // twice in a row, and none more than [perArtistCap] a batch.
        val count = HashMap<String, Int>()
        existing.takeLast(RECENT_WINDOW).forEach { song -> artistKeys(song).forEach { count.merge(it, 1, Int::plus) } }
        fun fits(song: Song): Boolean = artistKeys(song).all { (count[it] ?: 0) < perArtistCap(it) }
        // A track just liked seeds the next station first; then the pool, in its turn.
        val liked = promoted.pollFirst()
        val seeds = listOfNotNull(liked) + List(minOf(pool.size, SEED_TRIES)) { pool[(nextSeed + it) % pool.size] }
        // A seed's station can come back all known or all queued already: move on, a few times.
        for (seed in seeds) {
            if (out.size >= limit) break
            if (seed !== liked) nextSeed++
            if (unwanted(seed)) continue
            val station = stationFor(seed)
            DesktopTrackLog.log("wave: ${station.size} tracks around '${seed.title}' (${_tuning.value})")
            station.asSequence()
                .filter { it.videoId != seed.videoId || existing.isEmpty() }
                .filter { it.videoId !in have && it.videoId !in known }
                .filterNot(::unwanted)
                // A few from each seed, so the wave keeps moving across what the listener likes.
                .take(PER_SEED)
                .forEach {
                    if (out.size < limit && fits(it) && have.add(it.videoId)) {
                        out += it
                        artistKeys(it).forEach { artist -> count.merge(artist, 1, Int::plus) }
                    }
                }
        }
        return out.map { it.copy(queueTier = QueueTier.AUTOPLAY, radioName = radioName) }
    }

    private suspend fun stationFor(seed: Song): List<Song> {
        val wanted = _tuning.value
        val (plain, tuner) = YtMusicRepository.radioWithTuner(seed.videoId).getOrNull() ?: return emptyList()
        if (wanted == DesktopWaveTuning.ALL) return plain
        val tuning = tuner.firstOrNull { it.matches(wanted) } ?: run {
            DesktopTrackLog.log("wave: no '$wanted' tuning around '${seed.title}', playing it untuned")
            return plain
        }
        return YtMusicRepository.radioTuned(seed.videoId, tuning).getOrNull()?.ifEmpty { null } ?: plain
    }

    private fun RadioTuning.matches(wanted: DesktopWaveTuning): Boolean =
        playlistId.startsWith(wanted.idPrefix) || label.lowercase() in wanted.labels

    private fun <T> interleave(a: List<T>, b: List<T>): List<T> {
        val out = ArrayList<T>(a.size + b.size)
        for (i in 0 until maxOf(a.size, b.size)) {
            a.getOrNull(i)?.let(out::add)
            b.getOrNull(i)?.let(out::add)
        }
        return out
    }

    private const val FIRST_BATCH = 15
    private val ARTIST_SPLIT = Regex("""\s*(?:,|&|\bfeat\.|\bft\.)\s*""", RegexOption.IGNORE_CASE)
    private const val LIKE_WEIGHT = 1f
    private const val SKIP_WEIGHT = 0.75f
    private const val LISTEN_WEIGHT = 0.25f
    private const val FAVOURITE = 1.5f
    private const val TASTE_CEILING = 3f
    /** An artist this far down — disliked, or skipped about three times — is left out. */
    private const val SKIPPED_OUT = -2.2f
    private const val MAX_PROMOTED = 3
    private const val MAX_BLOCKED = 300
    /** How far back in the queue "in a row" looks. */
    private const val RECENT_WINDOW = 4
    private const val KEY_BLOCKED = "wave_blocked_artists"
    private const val SEED_POOL = 40
    private const val PER_SEED = 6
    private const val SEED_TRIES = 6
    private const val KEY_TUNING = "wave_tuning"
    private const val KEY_SEEDS = "wave_seeds"
    private const val KEY_ONLY_NEW = "wave_only_new"
    private const val KEY_SHOWN = "wave_shown"
    private const val KEY_PLAYLIST = "wave_playlist"
}
