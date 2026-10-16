package com.music.bitchord.desktop

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What the speakers are about to play, as bars: a small FFT over the output stream, folded into
 * [BAND_COUNT] log-spaced bands, for the player's visualiser.
 *
 * Fed from the output thread with the block it is about to hand the device, and stamped with how
 * long that block will sit in the device's queue first — so the bars move with what is *heard*
 * rather than a fifth of a second ahead of it.
 */
internal object DesktopAudioAnalyzer {

    const val BAND_COUNT = 48

    private const val FFT_SIZE = 2048
    private const val MIN_HZ = 35.0
    private const val MAX_HZ = 16_000.0

    /** At most this often, which is all a 60/120 Hz display can show anyway. */
    private const val ANALYSIS_INTERVAL_NS = 1_000_000_000L / 60

    private const val FLOOR_DB = -72.0
    private const val CEILING_DB = -12.0

    private val ring = FloatArray(FFT_SIZE)
    private var ringAt = 0
    private var filled = 0

    private val re = DoubleArray(FFT_SIZE)
    private val im = DoubleArray(FFT_SIZE)
    private val window = DoubleArray(FFT_SIZE) { 0.5 - 0.5 * cos(2.0 * PI * it / (FFT_SIZE - 1)) }
    private val smoothed = FloatArray(BAND_COUNT)
    private var lastAnalysis = 0L

    /** Frames ready to be shown, in the order they become due. */
    private val pending = ArrayDeque<Frame>()
    private val lock = Any()

    @Volatile
    private var latest: Frame = Frame(0L, FloatArray(BAND_COUNT), 0f)

    private class Frame(val dueNanos: Long, val bands: FloatArray, val level: Float)

    /** Called by the output for every block, before it blocks on the device. */
    fun feed(samples: FloatArray, count: Int, channels: Int, sampleRate: Int, latencyNanos: Long) {
        if (channels <= 0 || sampleRate <= 0 || count <= 0) return
        val frames = count / channels
        for (frame in 0 until frames) {
            var mono = 0f
            val base = frame * channels
            for (channel in 0 until channels) mono += samples[base + channel]
            ring[ringAt] = mono / channels
            ringAt = (ringAt + 1) % FFT_SIZE
        }
        filled = (filled + frames).coerceAtMost(FFT_SIZE)
        val now = System.nanoTime()
        if (filled < FFT_SIZE || now - lastAnalysis < ANALYSIS_INTERVAL_NS) return
        lastAnalysis = now
        val (bands, level) = analyse(sampleRate)
        synchronized(lock) {
            pending.addLast(Frame(now + latencyNanos, bands, level))
            while (pending.size > 64) pending.removeFirst()
        }
    }

    /** Drops what was queued for audio that will now never be heard — a seek, a skip, a stop. */
    fun reset() {
        synchronized(lock) { pending.clear() }
        filled = 0
        smoothed.fill(0f)
        latest = Frame(0L, FloatArray(BAND_COUNT), 0f)
    }

    /** The bands that are audible at [nowNanos] (System.nanoTime), 0..1 each. */
    fun bandsAt(nowNanos: Long): FloatArray = frameAt(nowNanos).bands

    /** Overall loudness at [nowNanos], 0..1. */
    fun levelAt(nowNanos: Long): Float = frameAt(nowNanos).level

    private fun frameAt(nowNanos: Long): Frame {
        synchronized(lock) {
            while (pending.isNotEmpty() && pending.first().dueNanos <= nowNanos) {
                latest = pending.removeFirst()
            }
        }
        // Silence decays rather than freezing on the last frame when playback stops.
        if (nowNanos - latest.dueNanos > 250_000_000L && latest.level > 0f) {
            val faded = Frame(nowNanos, FloatArray(BAND_COUNT) { latest.bands[it] * 0.86f }, latest.level * 0.86f)
            latest = faded
        }
        return latest
    }

    private fun analyse(sampleRate: Int): Pair<FloatArray, Float> {
        var energy = 0.0
        for (i in 0 until FFT_SIZE) {
            val sample = ring[(ringAt + i) % FFT_SIZE].toDouble()
            energy += sample * sample
            re[i] = sample * window[i]
            im[i] = 0.0
        }
        fft(re, im)
        val bands = FloatArray(BAND_COUNT)
        val binHz = sampleRate.toDouble() / FFT_SIZE
        val ratio = ln(MAX_HZ / MIN_HZ)
        for (band in 0 until BAND_COUNT) {
            val lowHz = MIN_HZ * exp(ratio * band / BAND_COUNT)
            val highHz = MIN_HZ * exp(ratio * (band + 1) / BAND_COUNT)
            val low = (lowHz / binHz).toInt().coerceIn(1, FFT_SIZE / 2 - 1)
            val high = (highHz / binHz).toInt().coerceIn(low + 1, FFT_SIZE / 2)
            var peak = 0.0
            for (bin in low until high) peak = max(peak, sqrt(re[bin] * re[bin] + im[bin] * im[bin]))
            // Normalised to the window's gain, then into a perceptual 0..1.
            val db = 20.0 * log10(peak / (FFT_SIZE / 4.0) + 1e-9)
            // A gentle tilt so the treble — always quieter in music — still moves.
            val tilted = db + 3.0 * (band.toDouble() / BAND_COUNT) * 4.0
            val target = ((tilted - FLOOR_DB) / (CEILING_DB - FLOOR_DB)).coerceIn(0.0, 1.0).toFloat()
            // Fast up, slow down: how a meter's ballistics feel right.
            smoothed[band] = if (target > smoothed[band]) {
                smoothed[band] + (target - smoothed[band]) * 0.65f
            } else {
                smoothed[band] * 0.82f + target * 0.18f
            }
            bands[band] = smoothed[band]
        }
        val rms = sqrt(energy / FFT_SIZE)
        val level = ((20.0 * log10(rms + 1e-9) + 48.0) / 42.0).coerceIn(0.0, 1.0).toFloat()
        return bands to level
    }

    /** In-place iterative radix-2 FFT. */
    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = real[i]; real[i] = real[j]; real[j] = t
                t = imag[i]; imag[i] = imag[j]; imag[j] = t
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wRe = cos(angle)
            val wIm = sin(angle)
            var start = 0
            while (start < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until length / 2) {
                    val a = start + k
                    val b = a + length / 2
                    val tRe = real[b] * curRe - imag[b] * curIm
                    val tIm = real[b] * curIm + imag[b] * curRe
                    real[b] = real[a] - tRe
                    imag[b] = imag[a] - tIm
                    real[a] += tRe
                    imag[a] += tIm
                    val next = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = next
                }
                start += length
            }
            length = length shl 1
        }
    }
}
