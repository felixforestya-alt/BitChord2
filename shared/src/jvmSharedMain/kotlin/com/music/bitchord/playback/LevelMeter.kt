package com.music.bitchord.playback

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * How loud what is being played is, for whoever asks now and then — My Jam's ear, see
 * [JamEnergy]. The audio thread only adds: a sum of squares and a count for each block, the
 * cheapest measure of loudness there is, with nothing kept and nothing allocated. The reader takes
 * the level of everything since it last asked.
 */
object LevelMeter {
    private val lock = Any()
    private var sumSquares = 0.0
    private var samples = 0L

    /** Called on the audio thread for each block, after every stage that shapes it. */
    fun feed(block: FloatArray, count: Int) {
        if (count <= 0) return
        var sum = 0.0
        for (i in 0 until count) {
            val s = block[i]
            sum += s * s
        }
        synchronized(lock) {
            sumSquares += sum
            samples += count
        }
    }

    /**
     * The level since the last call, 0..1 on the same scale as the visualiser's (a 42 dB range
     * up from -48 dBFS), or null when nothing has played since.
     */
    fun take(): Float? {
        val (sum, count) = synchronized(lock) {
            val taken = sumSquares to samples
            sumSquares = 0.0
            samples = 0L
            taken
        }
        if (count == 0L) return null
        val rms = sqrt(sum / count)
        return ((20.0 * log10(rms + 1e-9) + 48.0) / 42.0).coerceIn(0.0, 1.0).toFloat()
    }
}
