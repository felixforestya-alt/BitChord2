package com.music.bitchord.playback

import com.music.bitchord.data.model.Song

/**
 * How energetic the jam's tracks sound, heard while they play — so a new batch can be put in an
 * order with no sudden leaps from a quiet ballad to a loud anthem (see [JamCore.smooth]).
 *
 * Nothing is analysed for it. Twice a second, while a jam track plays, it asks [level] for how
 * loud the last half second was — a figure the platform has anyway, or a sum of squares at most
 * (see [LevelMeter]) — and keeps how loud the track runs and how much it moves. A track heard for
 * long enough is remembered, and so is its artist: a track the jam has never played is placed by
 * its artist's other tracks, when it has played any.
 */
class JamEnergy(
    private val store: JamStore,
    /** How loud the last moments were, 0..1, or null when nothing was playing. */
    private val level: () -> Float?,
    private val isJam: (Song) -> Boolean,
    private val log: (String) -> Unit = {},
) {
    /** Per track; per artist the mean and how many tracks it is from. */
    private val tracks = object : LinkedHashMap<String, Float>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?) = size > MAX_TRACKS
    }
    private val artists = object : LinkedHashMap<String, Pair<Float, Int>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Float, Int>>?) = size > MAX_ARTISTS
    }
    private val lock = Any()
    @Volatile private var loaded = false

    @Volatile private var playing: Song? = null
    private var samples = 0
    private var levelSum = 0.0
    private var moveSum = 0.0
    private var lastLevel = -1f

    /** The track the player is on now; a jam track's energy is listened for until the next. */
    fun nowPlaying(song: Song?) {
        val done = synchronized(lock) {
            val previous = playing
            val heard = if (previous != null && samples >= MIN_SAMPLES) previous to energy() else null
            playing = song?.takeIf(isJam)
            samples = 0
            levelSum = 0.0
            moveSum = 0.0
            lastLevel = -1f
            heard
        }
        done?.let { (track, value) -> record(track, value) }
        if (playing != null) {
            // What played before this track is not this track's.
            level()
            ensureListening()
        }
    }

    /** What is known of [song]'s energy: its own, or its artists' — null when nothing is. */
    fun of(song: Song): Float? {
        load()
        return synchronized(lock) {
            tracks[song.videoId] ?: JamEngine.artistKeys(song).mapNotNull { artists[it]?.first }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        }
    }

    /** Loudness carries most of it; how much the level moves — beats, hits — the rest. */
    private fun energy(): Float {
        val loud = (levelSum / samples).toFloat()
        val move = (moveSum / samples).toFloat()
        return (loud * 0.7f + (move * 4f).coerceAtMost(1f) * 0.3f).coerceIn(0f, 1f)
    }

    /** One half-second of listening; public for the platform's own timer, and for tests. */
    fun sample() {
        if (playing == null) return
        val heard = level() ?: return
        synchronized(lock) {
            if (playing == null) return
            levelSum += heard
            if (lastLevel >= 0f) moveSum += kotlin.math.abs(heard - lastLevel)
            lastLevel = heard
            samples++
        }
    }

    private fun record(song: Song, value: Float) {
        load()
        val text = synchronized(lock) {
            tracks[song.videoId] = value
            JamEngine.artistKeys(song).forEach { artist ->
                val (mean, count) = artists[artist] ?: (value to 0)
                val n = (count + 1).coerceAtMost(ARTIST_MEMORY)
                artists[artist] = (mean + (value - mean) / n) to n
            }
            buildString {
                tracks.forEach { (id, e) -> append("t ").append(id).append(' ').append(e).append('\n') }
                artists.forEach { (name, v) -> append("a ").append(v.first).append(' ').append(v.second).append(' ').append(name).append('\n') }
            }
        }
        store.put(KEY, text)
        log("jam: '${song.title}' heard at energy %.2f".format(value))
    }

    private fun load() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            store.get(KEY).split('\n').forEach { line ->
                val f = line.split(' ', limit = 4)
                when {
                    f.size == 3 && f[0] == "t" -> f[2].toFloatOrNull()?.let { tracks[f[1]] = it }
                    f.size == 4 && f[0] == "a" -> {
                        val e = f[1].toFloatOrNull()
                        val n = f[2].toIntOrNull()
                        if (e != null && n != null) artists[f[3]] = e to n
                    }
                }
            }
            loaded = true
        }
    }

    @Volatile private var listening = false

    /** One sleeping thread, waking twice a second only while a jam track plays. */
    private fun ensureListening() {
        if (listening) return
        listening = true
        Thread({
            while (true) {
                runCatching { Thread.sleep(SAMPLE_MS) }.onFailure { return@Thread }
                sample()
            }
        }, "bitchord-jam-energy").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    companion object {
        const val KEY = "energy"
        private const val SAMPLE_MS = 500L
        /** Twenty seconds of a track before its energy is trusted. */
        const val MIN_SAMPLES = 40
        private const val MAX_TRACKS = 1_500
        private const val MAX_ARTISTS = 1_000
        /** How many tracks an artist's figure averages, at most: newer ones still move it. */
        private const val ARTIST_MEMORY = 8
    }
}
