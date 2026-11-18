package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

/** A track the jam picked, and the listener's track whose station it came from. */
data class JamPick(val song: Song, val seed: Song)

/**
 * My Jam's choices, the same on the phone and the desktop: which of the listener's tracks the jam
 * grows from, and which of the tracks around them it plays next.
 *
 * YouTube Music has a station around any one track and no station of the listener's own, so the
 * jam is many small stations stitched together. What made it feel like one station played for a
 * hundred tracks, and what this keeps from happening:
 *
 * - Six tracks were taken from each seed's station, its first six — the closest to that one seed.
 *   Now two, from further down it as the seed comes round again, so a batch of ten spans five seeds.
 * - Seeds were a plain shuffle, so a run of them could be one artist's. They are dealt round the
 *   artists instead: no artist seeds twice before every other has had a turn.
 * - Two seeds by different artists can still be one sound — their stations share most of their
 *   artists. Seeds are put into sounds as their stations come in, and no one sound may fill more
 *   than [SOUND_SHARE] of a batch while another is to hand.
 * - Nothing kept one artist from filling a batch. Now an artist comes once a batch, and not again
 *   within the last few tracks.
 * - A station around a liked track is mostly the listener's own likes, so a jam without "only new"
 *   played their likes back to them. Familiar tracks are now a set share of each batch.
 * - A jam whose seeds were lost — the process restarted behind a playing queue — fell back to the
 *   plain station of whatever track was playing, and stayed on it. [save] and [restore] keep the
 *   jam itself across that.
 *
 * One batch is asked for at a time.
 */
class JamEngine(
    /** The station around a seed: YouTube's radio, tuned however the listener has it tuned. */
    private val stationOf: suspend (Song) -> List<Song>,
    private val random: Random = Random.Default,
) {
    @Volatile private var seeds: List<Song> = emptyList()
    @Volatile private var cursor = 0
    @Volatile private var familiar: Set<String> = emptySet()
    @Volatile private var familiarShare = 1.0 / 3.0

    private val lock = Any()

    /** How far into each seed's station the jam has already taken. */
    private val depth = HashMap<String, Int>()

    /** Stations already fetched, so a seed come round again costs no request. */
    private val stations = object : LinkedHashMap<String, List<Song>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Song>>?) = size > STATION_CACHE
    }

    /** Which sound each seed is, by the artists its station shares with the others'. */
    private val soundOf = HashMap<String, Int>()
    private val soundArtists = ArrayList<MutableSet<String>>()

    /** Whether a jam has been started, or restored, in this process. */
    val active: Boolean get() = seeds.isNotEmpty()

    /** How many sounds the jam has told apart so far, for the log. */
    val sounds: Int get() = synchronized(lock) { soundArtists.size }

    /**
     * Starts a jam growing from [candidates] (the listener's tracks, in any order). [familiar] are
     * the ids the listener already knows — liked or played — of which each batch has at most
     * [familiarShare].
     */
    fun start(candidates: List<Song>, familiar: Set<String>, familiarShare: Double) {
        clearLearnt()
        seeds = dealRoundArtists(candidates.distinctBy { it.videoId })
        cursor = 0
        this.familiar = familiar
        this.familiarShare = familiarShare.coerceIn(0.0, 1.0)
    }

    /** How much of each batch may be familiar, from now on. */
    fun setFamiliarShare(share: Double) {
        familiarShare = share.coerceIn(0.0, 1.0)
    }

    private fun clearLearnt() = synchronized(lock) {
        depth.clear()
        stations.clear()
        soundOf.clear()
        soundArtists.clear()
    }

    /**
     * More of the listener's tracks to grow from, once they are to hand — the rest of a long liked
     * list, say, read after the jam had to start from its first page. Dealt in among the seeds
     * still to come, so they show up soon rather than after every seed already there; [familiar]
     * grows by them too.
     */
    fun addSeeds(candidates: List<Song>, familiar: Set<String> = emptySet()) {
        val current = seeds
        if (current.isEmpty()) return
        val present = current.mapTo(HashSet(), Song::videoId)
        val added = dealRoundArtists(candidates.filter { it.videoId !in present }.distinctBy { it.videoId })
        if (added.isEmpty()) return
        val at = cursor.coerceIn(0, current.size)
        val ahead = current.drop(at)
        val mixed = ArrayList<Song>(ahead.size + added.size)
        val a = ahead.iterator()
        val b = added.iterator()
        while (a.hasNext() || b.hasNext()) {
            if (a.hasNext()) mixed += a.next()
            if (b.hasNext()) mixed += b.next()
        }
        seeds = current.take(at) + mixed
        this.familiar = this.familiar + familiar
    }

    /**
     * The next tracks after [existing] (the queue, and whatever has played this session).
     *
     * @param first seeds whose stations come before the jam's own this time — a track just liked.
     * @param unwanted tracks to leave out whatever else holds — a disliked artist, a skipped track.
     * @param preference how much the listener has taken to a track's artists; higher comes first
     *   among a station's picks.
     * @param artistCap how many of one artist a batch may bring.
     */
    suspend fun more(
        existing: List<Song>,
        limit: Int,
        first: List<Song> = emptyList(),
        unwanted: (Song) -> Boolean = { false },
        preference: (Song) -> Float = { 0f },
        artistCap: (String) -> Int = { 1 },
    ): List<JamPick> {
        val pool = seeds
        if (pool.isEmpty() || limit <= 0) return emptyList()
        val have = existing.mapTo(HashSet(), Song::videoId)
        val inBatch = HashMap<String, Int>()
        val perSound = HashMap<Int, Int>()
        val recent = existing.takeLast(RECENT_ARTISTS).flatMapTo(HashSet(), ::artistKeys)
        var familiarLeft = ceil(limit * familiarShare - 1e-9).toInt()
        val soundCap = max(PER_SEED, ceil(limit * SOUND_SHARE).toInt())
        val out = ArrayList<JamPick>(limit)

        fun take(candidate: Song, seed: Song, relaxed: Boolean): Boolean {
            if (out.size >= limit || candidate.videoId in have || unwanted(candidate)) return false
            val isFamiliar = candidate.videoId in familiar
            if (isFamiliar && familiarLeft <= 0) return false
            val artists = artistKeys(candidate)
            if (artists.any { (inBatch[it] ?: 0) >= artistCap(it) }) return false
            val sound = synchronized(lock) { soundOf[seed.videoId] }
            if (!relaxed) {
                if (artists.any { it in recent }) return false
                if (sound != null && (perSound[sound] ?: 0) >= soundCap) return false
            }
            out += JamPick(candidate, seed)
            have += candidate.videoId
            artists.forEach { inBatch.merge(it, 1, Int::plus) }
            sound?.let { perSound.merge(it, 1, Int::plus) }
            if (isFamiliar) familiarLeft--
            return true
        }

        // Enough seeds for the batch at two a seed, and a few over for stations that come back
        // all known; every seed at most once a batch. Still short, the next seeds after them —
        // never the plain station of the last track, which is what kept a jam on one sound.
        val fetched = ArrayList<Pair<Song, List<Song>>>()
        var tried = 0
        var extra = first
        while (out.size < limit && tried < pool.size && tried < MAX_SEEDS_A_BATCH) {
            val count = minOf(pool.size - tried, limit / PER_SEED + SPARE_SEEDS)
            val wanted = (extra + List(count) { pool[(cursor + it) % pool.size] })
                .filterNot(unwanted)
                .distinctBy { it.videoId }
                .filter { seed -> fetched.none { it.first.videoId == seed.videoId } }
            cursor = (cursor + count) % pool.size
            tried += count
            extra = emptyList()
            fetched += coroutineScope {
                wanted.chunked(PARALLEL).flatMap { chunk -> chunk.map { seed -> async { seed to stationFor(seed) } }.awaitAll() }
            }
            // The first pass keeps an artist out for the last few tracks and a sound to its share;
            // a second, once every seed this batch could try has been tried and it is still short,
            // lets them back rather than leave the queue to run dry.
            val lastRound = tried >= pool.size || tried >= MAX_SEEDS_A_BATCH
            for (relaxed in if (lastRound) listOf(false, true) else listOf(false)) {
                for ((seed, station) in fetched) {
                    if (out.size >= limit) break
                    val from = synchronized(lock) { depth[seed.videoId] ?: 0 }
                    val window = station.asSequence()
                        .drop(from)
                        .filter { it.videoId != seed.videoId }
                        .take(PER_SEED * WINDOW)
                        .toList()
                        .sortedByDescending(preference)
                    val already = out.count { it.seed.videoId == seed.videoId }
                    var taken = already
                    for (candidate in window) {
                        if (taken >= PER_SEED) break
                        if (take(candidate, seed, relaxed)) taken++
                    }
                    // Further down the station next time this seed comes round; from the top
                    // again once it is used up.
                    if (taken > already) synchronized(lock) {
                        val next = from + PER_SEED * WINDOW
                        depth[seed.videoId] = if (next >= station.size) 0 else next
                    }
                }
                if (out.size >= limit) break
            }
        }
        return out
    }

    private suspend fun stationFor(seed: Song): List<Song> {
        synchronized(lock) { stations[seed.videoId] }?.let { return it }
        val station = runCatching { stationOf(seed) }.getOrDefault(emptyList())
        if (station.isNotEmpty()) synchronized(lock) {
            stations[seed.videoId] = station
            placeInSound(seed, station)
        }
        return station
    }

    /**
     * Puts [seed] with the sound whose stations share enough of its station's artists, or in a
     * sound of its own. Held under [lock].
     */
    private fun placeInSound(seed: Song, station: List<Song>) {
        if (seed.videoId in soundOf) return
        val artists = station.take(SOUND_SAMPLE).flatMapTo(HashSet(), ::artistKeys)
        if (artists.isEmpty()) return
        var best = -1
        var bestShare = 0.0
        soundArtists.forEachIndexed { i, sound ->
            val share = artists.count { it in sound }.toDouble() / artists.size
            if (share > bestShare) {
                bestShare = share
                best = i
            }
        }
        if (best >= 0 && bestShare >= SAME_SOUND) {
            soundOf[seed.videoId] = best
            if (soundArtists[best].size < MAX_SOUND_ARTISTS) soundArtists[best] += artists
        } else {
            soundOf[seed.videoId] = soundArtists.size
            soundArtists += artists.toMutableSet()
        }
    }

    /**
     * Seeds dealt round their artists: one of each artist in turn, the artists in a random order
     * and each artist's own tracks shuffled, so consecutive seeds are never one artist's while
     * another is waiting.
     */
    private fun dealRoundArtists(candidates: List<Song>): List<Song> {
        val byArtist = candidates.shuffled(random).groupBy { artistKeys(it).firstOrNull().orEmpty() }
        val queues = byArtist.values.shuffled(random).map { ArrayDeque(it) }
        val out = ArrayList<Song>(candidates.size)
        while (out.size < candidates.size) {
            for (queue in queues) queue.removeFirstOrNull()?.let(out::add)
        }
        return out
    }

    /** The jam as text, to be [restore]d by a later process: its seeds, where it is, what is known. */
    fun save(): String = buildString {
        append(FORMAT).append('\t').append(familiarShare).append('\t').append(cursor).append('\n')
        append(familiar.joinToString(",")).append('\n')
        for (seed in seeds) {
            append(listOf(seed.videoId, seed.title, seed.artist, seed.thumbnailUrl.orEmpty()).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') })
            append('\n')
        }
    }

    /** Takes up a jam [save]d before; false when [text] holds none. */
    fun restore(text: String): Boolean {
        val lines = text.split('\n')
        val head = lines.firstOrNull()?.split('\t') ?: return false
        if (head.size != 3) return false
        val share = when (head[0]) {
            FORMAT -> head[1].toDoubleOrNull() ?: return false
            // The first form kept "only new" as a flag.
            "jam1" -> if (head[1] == "1") 0.0 else 1.0 / 3.0
            else -> return false
        }
        val restored = lines.drop(2).mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 4 || f[0].isBlank()) null else Song(videoId = f[0], title = f[1], artist = f[2], thumbnailUrl = f[3].ifBlank { null })
        }
        if (restored.isEmpty()) return false
        clearLearnt()
        familiarShare = share.coerceIn(0.0, 1.0)
        cursor = (head[2].toIntOrNull() ?: 0).coerceIn(0, restored.size - 1)
        familiar = lines.getOrNull(1).orEmpty().split(',').filter(String::isNotBlank).toSet()
        seeds = restored
        return true
    }

    companion object {
        /** The names a track is credited to, as the jam tells artists apart. */
        fun artistKeys(song: Song): List<String> =
            song.artists.map { it.name }.ifEmpty { song.artist.split(ARTIST_SPLIT) }
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()

        private val ARTIST_SPLIT = Regex("""\s*(?:,|&|\bfeat\.|\bft\.)\s*""", RegexOption.IGNORE_CASE)
        private const val FORMAT = "jam2"
        /** Tracks taken from one seed's station a batch. */
        const val PER_SEED = 2
        /** How far down a station's next stretch the picks are made from, in [PER_SEED]s. */
        private const val WINDOW = 3
        private const val SPARE_SEEDS = 3
        private const val PARALLEL = 4
        /** How many of the last queued tracks an artist must not be among to come again. */
        private const val RECENT_ARTISTS = 6
        private const val STATION_CACHE = 48
        /** Seeds a batch may go through before it gives up on being full. */
        private const val MAX_SEEDS_A_BATCH = 24
        /** One sound's share of a batch, at most, while another is to hand. */
        private const val SOUND_SHARE = 0.4
        /** The share of a station's artists another sound must already have for the seed to be that sound. */
        private const val SAME_SOUND = 0.25
        /** How much of a station tells its sound. */
        private const val SOUND_SAMPLE = 25
        private const val MAX_SOUND_ARTISTS = 400
    }
}
