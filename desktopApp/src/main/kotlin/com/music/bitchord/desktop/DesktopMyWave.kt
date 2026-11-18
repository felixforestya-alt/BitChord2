package com.music.bitchord.desktop

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.RadioTuning
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.JamCore
import com.music.bitchord.playback.JamDiscovery
import com.music.bitchord.playback.JamEngine
import com.music.bitchord.playback.JamStore
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
 * My Wave: one button that plays the listener's own endless station, as Yandex Music has it —
 * the same as the phone's: [JamCore] picks and learns, this keeps its settings and tells it what
 * the listener does.
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
    private val _discovery = MutableStateFlow(
        runCatching { JamDiscovery.valueOf(persistence.string(KEY_DISCOVERY, "")) }.getOrNull()
            // From before the three settings: "only new" was the one choice there was.
            ?: if (persistence.boolean(KEY_ONLY_NEW, false)) JamDiscovery.NEW else JamDiscovery.BALANCED,
    )
    private val _playlist = MutableStateFlow(
        persistence.string(KEY_PLAYLIST).split('\n').takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0] to it[1] },
    )
    private val _shown = MutableStateFlow(persistence.boolean(KEY_SHOWN, true))

    /** The playlist the jam grows from when [seeds] says so: its id and title. */
    val playlist: StateFlow<Pair<String, String>?> = _playlist

    val tuning: StateFlow<DesktopWaveTuning> = _tuning
    val seeds: StateFlow<DesktopWaveSeeds> = _seeds

    /** How much of the jam is what the listener knows, against what is new. */
    val discovery: StateFlow<JamDiscovery> = _discovery

    /** Whether the wave's card is on Home at all. */
    val shown: StateFlow<Boolean> = _shown

    fun setPlaylist(id: String, title: String) {
        _playlist.value = id to title
        persistence.saveString(KEY_PLAYLIST, "$id\n$title")
    }

    fun setTuning(value: DesktopWaveTuning) {
        _tuning.value = value
        persistence.saveString(KEY_TUNING, value.name)
    }

    fun setSeeds(value: DesktopWaveSeeds) {
        _seeds.value = value
        persistence.saveString(KEY_SEEDS, value.name)
    }

    fun setDiscovery(value: JamDiscovery) {
        _discovery.value = value
        persistence.saveString(KEY_DISCOVERY, value.name)
        core.setDiscovery(value)
    }

    fun setShown(value: Boolean) {
        _shown.value = value
        persistence.saveBoolean(KEY_SHOWN, value)
    }

    private val core by lazy {
        JamCore(
            // Off the calling thread — the jam saves after every batch — and every save of these
            // keys through it, as [DesktopPreferenceWriter] needs.
            store = object : JamStore {
                override fun get(key: String) = persistence.string("wave_$key")
                override fun put(key: String, value: String) = persistence.saveStringSoon("wave_$key", value)
            },
            stationOf = ::stationFor,
            radioName = { radioName },
            reason = { seed -> DesktopStrings["wave_like_seed", "My Jam · like “%s”"].format(seed.title) },
            log = DesktopTrackLog::log,
        )
    }

    /** The names a track is credited to, as the jam keys its taste. */
    fun artistKeys(song: Song): List<String> = JamEngine.artistKeys(song)

    /** Whether the jam is to leave [song] out: an artist disliked, or skipped past before. */
    fun unwanted(song: Song): Boolean = core.unwanted(song)

    /** A heart or a thumbs-down given to [song] while the jam plays. */
    fun onRated(song: Song, status: com.music.bitchord.data.model.LikeStatus) = core.onRated(song, status)

    /** [song] skipped in its first seconds. */
    fun onSkipped(song: Song) = core.onSkipped(song)

    /** [song] heard to its end. */
    fun onFinished(song: Song) = core.onFinished(song)

    val blockedArtists: StateFlow<Set<String>> get() = core.blockedArtists
    val skippedTracks: StateFlow<Set<String>> get() = core.skippedTracks

    fun unblock(artist: String) = core.unblock(artist)

    fun unblockAll() = core.unblockAll()

    fun clearSkipped() = core.clearSkipped()

    /**
     * Starts a wave from [liked] and [history] (most recent first) and returns its first tracks,
     * or an empty list when there is nothing to grow it from.
     */
    suspend fun start(liked: List<Song>, history: List<Song>, fromPlaylist: List<Song> = emptyList(), limit: Int = FIRST_BATCH): List<Song> {
        val pool = when (_seeds.value) {
            DesktopWaveSeeds.PLAYLIST -> fromPlaylist.shuffled().take(SEED_POOL * 2)
            DesktopWaveSeeds.LIKED -> likedSeeds(liked, SEED_POOL)
            DesktopWaveSeeds.HISTORY -> history.take(SEED_POOL)
            // Interleaved, so neither side crowds the other out.
            DesktopWaveSeeds.LIKED_AND_HISTORY -> interleave(history.take(SEED_POOL / 2), likedSeeds(liked, SEED_POOL / 2))
        }.distinctBy { it.videoId }.ifEmpty { (liked + history).distinctBy { it.videoId }.take(SEED_POOL) }
        DesktopTrackLog.log("jam: ${_seeds.value}, ${liked.size} liked, ${history.size} played")
        return core.start(pool, (liked + history + fromPlaylist).mapTo(HashSet(), Song::videoId), _discovery.value, limit)
    }

    /**
     * Liked tracks to seed from: half the newest, half from all the rest — the newest alone made a
     * jam "from likes" go round what was liked lately, not what the listener likes.
     */
    private fun likedSeeds(liked: List<Song>, count: Int): List<Song> {
        val newest = liked.take(RECENT_LIKES).shuffled().take(count / 2)
        val rest = (liked - newest.toSet()).shuffled().take(count - newest.size)
        return interleave(newest, rest)
    }

    /**
     * A jam grown from one track: it plays first, its station's tracks are what the jam grows
     * from, and it is come back to every few batches so the jam keeps its feel.
     */
    suspend fun startFrom(song: Song, liked: List<Song>, history: List<Song>, limit: Int = FIRST_BATCH): List<Song> =
        core.startFrom(song, stationFor(song), liked, (liked + history).mapTo(HashSet(), Song::videoId), _discovery.value, limit)

    /** The next tracks after [existing] — see [JamCore.more]. */
    suspend fun more(existing: List<Song>, limit: Int): List<Song> = core.more(existing, limit)

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
    private const val SEED_POOL = JamCore.SEED_POOL
    /** Likes counted as the listener's latest. */
    private const val RECENT_LIKES = 60
    private const val KEY_TUNING = "wave_tuning"
    private const val KEY_SEEDS = "wave_seeds"
    private const val KEY_ONLY_NEW = "wave_only_new"
    private const val KEY_DISCOVERY = "wave_discovery"
    private const val KEY_SHOWN = "wave_shown"
    private const val KEY_PLAYLIST = "wave_playlist"
}
