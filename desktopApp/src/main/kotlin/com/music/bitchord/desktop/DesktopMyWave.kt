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
internal enum class DesktopWaveSeeds { LIKED_AND_HISTORY, LIKED, HISTORY }

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

    /**
     * Starts a wave from [liked] and [history] (most recent first) and returns its first tracks,
     * or an empty list when there is nothing to grow it from.
     */
    suspend fun start(liked: List<Song>, history: List<Song>, limit: Int = FIRST_BATCH): List<Song> {
        val pool = when (_seeds.value) {
            DesktopWaveSeeds.LIKED -> liked.take(SEED_POOL)
            DesktopWaveSeeds.HISTORY -> history.take(SEED_POOL)
            // Interleaved, so neither side crowds the other out.
            DesktopWaveSeeds.LIKED_AND_HISTORY -> interleave(history.take(SEED_POOL), liked.take(SEED_POOL))
        }.distinctBy { it.videoId }.ifEmpty { (liked + history).distinctBy { it.videoId }.take(SEED_POOL) }
        if (pool.isEmpty()) return emptyList()
        // A different start each time, as the wave is meant to feel.
        seedPool = pool.shuffled()
        nextSeed = 0
        known = if (_onlyNew.value) (liked + history).mapTo(HashSet(), Song::videoId) else emptySet()
        return more(emptyList(), limit)
    }

    /** The next tracks after [existing]: from the next seed's tuned station. */
    suspend fun more(existing: List<Song>, limit: Int): List<Song> {
        val pool = seedPool
        if (pool.isEmpty()) return emptyList()
        val have = existing.mapTo(HashSet(), Song::videoId)
        val out = ArrayList<Song>(limit)
        // A seed's station can come back all known or all queued already: move on, a few times.
        repeat(minOf(pool.size, SEED_TRIES)) {
            if (out.size >= limit) return@repeat
            val seed = pool[nextSeed % pool.size]
            nextSeed++
            val station = stationFor(seed)
            DesktopTrackLog.log("wave: ${station.size} tracks around '${seed.title}' (${_tuning.value})")
            station.asSequence()
                .filter { it.videoId != seed.videoId || existing.isEmpty() }
                .filter { it.videoId !in have && it.videoId !in known }
                // A few from each seed, so the wave keeps moving across what the listener likes.
                .take(PER_SEED)
                .forEach {
                    if (out.size < limit && have.add(it.videoId)) out += it
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
    private const val SEED_POOL = 40
    private const val PER_SEED = 6
    private const val SEED_TRIES = 6
    private const val KEY_TUNING = "wave_tuning"
    private const val KEY_SEEDS = "wave_seeds"
    private const val KEY_ONLY_NEW = "wave_only_new"
    private const val KEY_SHOWN = "wave_shown"
}
