package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.JamEngine

/**
 * How energetic the jam's tracks sound, heard while they play — so a new batch can be put in an
 * order with no sudden leaps from a quiet ballad to a loud anthem.
 *
 * Nothing is analysed for it. Twice a second, while a jam track plays, it reads the level the
 * visualiser's analyser has already worked out from what is being played ([DesktopAudioAnalyzer]),
 * and keeps how loud the track runs and how much it moves. A track heard for long enough is
 * remembered, and so is its artist: a track the jam has never played is placed by its artist's
 * other tracks, when it has played any.
 */
internal object DesktopJamEnergy {

    private val persistence by lazy { DesktopPersistence() }

    /** Per track and per artist: energy 0..1, and how many tracks the artist's figure is from. */
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
            playing = song?.takeIf { it.radioName == DesktopMyWave.radioName }
            samples = 0
            levelSum = 0.0
            moveSum = 0.0
            lastLevel = -1f
            heard
        }
        done?.let { (track, value) -> record(track, value) }
        if (playing != null) ensureListening()
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
        val level = (levelSum / samples).toFloat()
        val move = (moveSum / samples).toFloat()
        return (level * 0.7f + (move * 4f).coerceAtMost(1f) * 0.3f).coerceIn(0f, 1f)
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
                tracks.forEach { (id, e) -> append('t').append(' ').append(id).append(' ').append(e).append('\n') }
                artists.forEach { (name, v) -> append('a').append(' ').append(v.first).append(' ').append(v.second).append(' ').append(name).append('\n') }
            }
        }
        persistence.saveStringSoon(KEY, text)
        DesktopTrackLog.log("jam: '${song.title}' heard at energy %.2f".format(value))
    }

    private fun load() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            persistence.string(KEY).split('\n').forEach { line ->
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

    /**
     * One sleeping thread, waking twice a second only while a jam track plays: a read of two
     * numbers the analyser already holds, and an addition.
     */
    private fun ensureListening() {
        if (listening) return
        listening = true
        Thread({
            while (true) {
                runCatching { Thread.sleep(SAMPLE_MS) }.onFailure { return@Thread }
                if (playing == null) continue
                // Only while sound is coming through: paused, nothing is heard.
                if (!DesktopAudioAnalyzer.fedWithin(SAMPLE_MS * 1_000_000L * 2)) continue
                val level = DesktopAudioAnalyzer.levelAt(System.nanoTime())
                synchronized(lock) {
                    if (playing == null) return@synchronized
                    levelSum += level
                    if (lastLevel >= 0f) moveSum += kotlin.math.abs(level - lastLevel)
                    lastLevel = level
                    samples++
                }
            }
        }, "bitchord-jam-energy").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    private const val KEY = "wave_energy"
    private const val SAMPLE_MS = 500L
    /** Twenty seconds of a track before its energy is trusted. */
    private const val MIN_SAMPLES = 40
    private const val MAX_TRACKS = 1_500
    private const val MAX_ARTISTS = 1_000
    /** How many tracks an artist's figure averages, at most: newer ones still move it. */
    private const val ARTIST_MEMORY = 8
}
