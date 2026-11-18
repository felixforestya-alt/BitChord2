package com.music.bitchord.bc2

import android.util.Log
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.RadioTuning
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.JamEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Which way the jam is tuned — YouTube Music's own radio tuner, as Yandex's My Wave has "character". */
internal enum class JamTuning(
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

/** Which tracks the jam starts out from. */
internal enum class JamSeeds { LIKED_AND_HISTORY, LIKED, HISTORY, PLAYLIST }

/**
 * My Jam: one button that plays the listener's own endless station, as Yandex Music's My Wave —
 * BitChord²'s, carried over from the desktop.
 *
 * YouTube Music has the parts — a station around any track, and a tuner on it ("Popular", "Deep
 * cuts", "Party") — but no single station of the listener's own. So the jam takes tracks they like
 * or played lately as seeds, plays a tuned station from one, and when it runs low moves on to the
 * next seed: the queue never ends and never sits on one artist for long. With "only new" it leaves
 * out what is already liked or was played lately.
 *
 * Every jam track carries [radioName] in the queue; that is how AutoPlay knows to come here for
 * more rather than to the plain station of the last track — see `loadAutoplayTracks`.
 */
internal object MyJam {

    /** The name the jam's tracks carry in the queue. */
    val radioName: String get() = bc2("Мой джем", "My Jam")

    /** Whether a queue entry's radio name is the jam's — in either language, as set when it began. */
    fun isJam(name: String?): Boolean = name == "Мой джем" || name == "My Jam"

    private val _tuning = MutableStateFlow(
        runCatching { JamTuning.valueOf(Bc2Prefs.string(KEY_TUNING, JamTuning.ALL.name)) }.getOrDefault(JamTuning.ALL),
    )
    private val _seeds = MutableStateFlow(
        runCatching { JamSeeds.valueOf(Bc2Prefs.string(KEY_SEEDS, JamSeeds.LIKED_AND_HISTORY.name)) }.getOrDefault(JamSeeds.LIKED_AND_HISTORY),
    )
    private val _onlyNew = MutableStateFlow(Bc2Prefs.boolean(KEY_ONLY_NEW, false))
    private val _playlist = MutableStateFlow(
        Bc2Prefs.string(KEY_PLAYLIST).split('\n').takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0] to it[1] },
    )
    private val _shown = MutableStateFlow(Bc2Prefs.boolean(KEY_SHOWN, true))

    val tuning: StateFlow<JamTuning> = _tuning
    val seeds: StateFlow<JamSeeds> = _seeds
    val onlyNew: StateFlow<Boolean> = _onlyNew

    /** The playlist the jam grows from when [seeds] says so: its browse id and title. */
    val playlist: StateFlow<Pair<String, String>?> = _playlist

    /** Whether the jam's card is on Home at all. */
    val shown: StateFlow<Boolean> = _shown

    fun setTuning(value: JamTuning) {
        _tuning.value = value
        Bc2Prefs.putString(KEY_TUNING, value.name)
    }

    fun setSeeds(value: JamSeeds) {
        _seeds.value = value
        Bc2Prefs.putString(KEY_SEEDS, value.name)
    }

    fun setOnlyNew(value: Boolean) {
        _onlyNew.value = value
        Bc2Prefs.putBoolean(KEY_ONLY_NEW, value)
    }

    fun setPlaylist(id: String, title: String) {
        _playlist.value = id to title
        Bc2Prefs.putString(KEY_PLAYLIST, "$id\n$title")
    }

    fun setShown(value: Boolean) {
        _shown.value = value
        Bc2Prefs.putBoolean(KEY_SHOWN, value)
    }

    /** What the jam grows from and picks — shared with the desktop, see [JamEngine]. */
    private val engine = JamEngine(::stationFor)

    /**
     * Starts a jam from [liked] and [history] (most recent first) and returns its first tracks, or
     * an empty list when there is nothing to grow it from.
     */
    suspend fun start(liked: List<Song>, history: List<Song>, fromPlaylist: List<Song> = emptyList(), limit: Int = FIRST_BATCH): List<Song> {
        val pool = when (_seeds.value) {
            JamSeeds.PLAYLIST -> fromPlaylist.shuffled().take(SEED_POOL * 2)
            JamSeeds.LIKED -> likedSeeds(liked, SEED_POOL)
            JamSeeds.HISTORY -> history.take(SEED_POOL)
            // Interleaved, so neither side crowds the other out.
            JamSeeds.LIKED_AND_HISTORY -> interleave(history.take(SEED_POOL / 2), likedSeeds(liked, SEED_POOL / 2))
        }.distinctBy { it.videoId }.ifEmpty { (liked + history).distinctBy { it.videoId }.take(SEED_POOL) }
        Log.d(TAG, "jam from ${pool.size} seeds (${_seeds.value}, ${liked.size} liked, ${history.size} played, only new ${_onlyNew.value})")
        if (pool.isEmpty()) return emptyList()
        engine.start(pool, (liked + history + fromPlaylist).mapTo(HashSet(), Song::videoId), _onlyNew.value)
        // Kept, so a jam still playing after Android has restarted the app carries on as itself
        // rather than as the plain station of whatever track was on.
        Bc2Prefs.putString(KEY_STATE, engine.save())
        return more(emptyList(), limit)
    }

    /**
     * Liked tracks to seed from: half the newest, half from all the rest. Only the newest were
     * taken, and with the app holding only the first page of likes that was the last hundred or
     * so — a jam "from likes" went round what was liked lately, not what the listener likes.
     */
    private fun likedSeeds(liked: List<Song>, count: Int): List<Song> {
        val newest = liked.take(RECENT_LIKES).shuffled().take(count / 2)
        val rest = (liked - newest.toSet()).shuffled().take(count - newest.size)
        return interleave(newest, rest)
    }

    /**
     * The whole liked list, read page by page once a jam from likes has started on the first, and
     * dealt in among its seeds. Remembered for the rest of the process.
     */
    suspend fun widenWithAllLikes() {
        if (_seeds.value != JamSeeds.LIKED && _seeds.value != JamSeeds.LIKED_AND_HISTORY) return
        val all = allLiked ?: loadAllLiked().also { allLiked = it }
        if (all.size <= RECENT_LIKES) return
        val count = if (_seeds.value == JamSeeds.LIKED) SEED_POOL else SEED_POOL / 2
        engine.addSeeds(all.drop(RECENT_LIKES).shuffled().take(count), all.mapTo(HashSet(), Song::videoId))
        Bc2Prefs.putString(KEY_STATE, engine.save())
        Log.d(TAG, "jam widened with likes from ${all.size} in all")
    }

    @Volatile private var allLiked: List<Song>? = null

    private suspend fun loadAllLiked(): List<Song> {
        val first = YtMusicRepository.browseSongs(YtMusicRepository.LIKED_MUSIC).getOrNull() ?: return emptyList()
        val out = LinkedHashMap<String, Song>()
        first.songs.forEach { out.putIfAbsent(it.videoId, it) }
        var token = first.continuation
        var pages = 1
        while (token != null && pages++ < MAX_LIKED_PAGES) {
            val page = YtMusicRepository.moreSongs(token).getOrNull() ?: break
            page.songs.forEach { out.putIfAbsent(it.videoId, it) }
            token = page.continuation
        }
        return out.values.toList()
    }

    /**
     * The next tracks after [existing]; empty when there is no jam, in this process or saved from
     * an earlier one — AutoPlay then carries on with the plain station.
     */
    suspend fun more(existing: List<Song>, limit: Int): List<Song> {
        if (!engine.active && !engine.restore(Bc2Prefs.string(KEY_STATE))) return emptyList()
        val out = engine.more(existing, limit)
        // Where it has got to, for a later process to carry on from.
        Bc2Prefs.putString(KEY_STATE, engine.save())
        Log.d(TAG, "${out.size} more for the jam: ${out.joinToString { it.artist }}")
        return out.map { it.copy(queueTier = QueueTier.AUTOPLAY, radioName = radioName) }
    }

    private suspend fun stationFor(seed: Song): List<Song> {
        val wanted = _tuning.value
        val (plain, tuner) = YtMusicRepository.radioWithTuner(seed.videoId).getOrNull() ?: return emptyList()
        if (wanted == JamTuning.ALL) return plain
        val tuning = tuner.firstOrNull { it.matches(wanted) } ?: return plain
        return YtMusicRepository.radioTuned(seed.videoId, tuning).getOrNull()?.ifEmpty { null } ?: plain
    }

    private fun RadioTuning.matches(wanted: JamTuning): Boolean =
        playlistId.startsWith(wanted.idPrefix) || label.lowercase() in wanted.labels

    private fun <T> interleave(a: List<T>, b: List<T>): List<T> {
        val out = ArrayList<T>(a.size + b.size)
        for (i in 0 until maxOf(a.size, b.size)) {
            a.getOrNull(i)?.let(out::add)
            b.getOrNull(i)?.let(out::add)
        }
        return out
    }

    private const val TAG = "MyJam"
    private const val FIRST_BATCH = 15
    private const val SEED_POOL = 40
    private const val KEY_TUNING = "jam_tuning"
    private const val KEY_SEEDS = "jam_seeds"
    private const val KEY_ONLY_NEW = "jam_only_new"
    private const val KEY_SHOWN = "jam_shown"
    private const val KEY_PLAYLIST = "jam_playlist"
    private const val KEY_STATE = "jam_state"
    /** Likes counted as the listener's latest. */
    private const val RECENT_LIKES = 60
    private const val MAX_LIKED_PAGES = 30
}
