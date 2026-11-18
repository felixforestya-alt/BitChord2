package com.music.bitchord.ui.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The visualiser's analysis gives exactly what it gave before its band bounds, twiddles and
 * square roots were worked out once instead of on every analysis: the old analysis is kept here,
 * fed the same blocks, and compared value for value.
 */
class AudioSpectrumExactnessTest {

    /** The analysis as it was, with its own state. */
    private class Before {
        val n = 2048
        val bandCount = AudioSpectrum.BAND_COUNT
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        val window = DoubleArray(n) { 0.5 - 0.5 * cos(2.0 * PI * it / (n - 1)) }
        val smoothed = FloatArray(bandCount)

        fun analyse(ring: FloatArray, ringAt: Int, sampleRate: Int): Pair<FloatArray, Float> {
            var energy = 0.0
            for (i in 0 until n) {
                val sample = ring[(ringAt + i) % n].toDouble()
                energy += sample * sample
                re[i] = sample * window[i]
                im[i] = 0.0
            }
            fft(re, im)
            val bands = FloatArray(bandCount)
            val binHz = sampleRate.toDouble() / n
            val ratio = ln(16_000.0 / 35.0)
            for (band in 0 until bandCount) {
                val lowHz = 35.0 * exp(ratio * band / bandCount)
                val highHz = 35.0 * exp(ratio * (band + 1) / bandCount)
                val low = (lowHz / binHz).toInt().coerceIn(1, n / 2 - 1)
                val high = (highHz / binHz).toInt().coerceIn(low + 1, n / 2)
                var peak = 0.0
                for (bin in low until high) peak = max(peak, sqrt(re[bin] * re[bin] + im[bin] * im[bin]))
                val db = 20.0 * log10(peak / (n / 4.0) + 1e-9)
                val tilted = db + 3.0 * (band.toDouble() / bandCount) * 4.0
                val target = ((tilted - -72.0) / (-12.0 - -72.0)).coerceIn(0.0, 1.0).toFloat()
                smoothed[band] = if (target > smoothed[band]) {
                    smoothed[band] + (target - smoothed[band]) * 0.65f
                } else {
                    smoothed[band] * 0.82f + target * 0.18f
                }
                bands[band] = smoothed[band]
            }
            val rms = sqrt(energy / n)
            val level = ((20.0 * log10(rms + 1e-9) + 48.0) / 42.0).coerceIn(0.0, 1.0).toFloat()
            return bands to level
        }

        fun fft(real: DoubleArray, imag: DoubleArray) {
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

    @Test
    fun theAnalysisIsTheSameToTheLastBit() {
        val random = Random(42)
        val before = Before()
        AudioSpectrum.reset()
        val stop = AudioSpectrum.watch()
        try {
            for (rate in listOf(44_100, 48_000)) {
                repeat(40) { round ->
                    // A whole ring's worth each time, so both sides analyse the same 2048 frames,
                    // with a tone and noise that move the bands about.
                    val frames = 2048
                    val block = FloatArray(frames * 2)
                    val mono = FloatArray(frames)
                    for (f in 0 until frames) {
                        val tone = (sin(2.0 * PI * (110.0 * (round + 1)) * f / rate) * 0.4).toFloat()
                        val l = tone + (random.nextFloat() - 0.5f) * 0.2f
                        val r = tone + (random.nextFloat() - 0.5f) * 0.2f
                        block[f * 2] = l
                        block[f * 2 + 1] = r
                        var m = 0f
                        m += l
                        m += r
                        mono[f] = m / 2
                    }
                    // Past the analysis interval, so this block is analysed.
                    Thread.sleep(20)
                    AudioSpectrum.feed(block, block.size, 2, rate, 0L)
                    val (bands, level) = before.analyse(mono, 0, rate)
                    val now = System.nanoTime()
                    assertEquals(level, AudioSpectrum.levelAt(now), "level, rate $rate, block $round")
                    val after = AudioSpectrum.bandsAt(now)
                    for (b in bands.indices) assertEquals(bands[b], after[b], "band $b, rate $rate, block $round")
                }
            }
        } finally {
            stop()
        }
    }

    @Test
    fun analysisCost() {
        val before = Before()
        val ring = FloatArray(2048) { (sin(it * 0.07) * 0.5).toFloat() }
        // The same 2048 frames in the analyser's ring, fed as one stereo block.
        val block = FloatArray(4096) { ring[it / 2] }
        val stop = AudioSpectrum.watch()
        try {
            AudioSpectrum.feed(block, block.size, 2, 48_000, 0L)
            fun time(rounds: Int, analyse: () -> Unit): Double {
                val t0 = System.nanoTime()
                repeat(rounds) { analyse() }
                return (System.nanoTime() - t0) / rounds / 1e3
            }
            val old = { before.analyse(ring, 0, 48_000); Unit }
            val new = { AudioSpectrum.analyse(48_000); Unit }
            repeat(5) { time(400, old); time(400, new) }
            // Each round both, in turn, so a slower moment of the machine lands on both.
            val rounds = List(9) { time(600, old) to time(600, new) }
            val b = rounds.map { it.first }.sorted()
            val a = rounds.map { it.second }.sorted()
            println(
                "BENCH visualiser analysis at 48 kHz: before median %.1f us (best %.1f), after median %.1f us (best %.1f), 9 rounds of 600".format(
                    b[4], b[0], a[4], a[0],
                ),
            )
        } finally {
            stop()
        }
    }
}
