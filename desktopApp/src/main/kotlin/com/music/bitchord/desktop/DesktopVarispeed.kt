package com.music.bitchord.desktop

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Playback speed as a turntable does it: the music is played faster or slower as a whole, so the
 * pitch goes with the tempo, and nothing is cut up and joined back together the way keeping the
 * pitch has to (see [DesktopAudioSpeed]) — which is what made sped-up songs flutter. Each output
 * sample is read between the input ones through a windowed-sinc filter, narrowed when speeding up
 * so what moves above the output's range is filtered out instead of folding back as grit.
 */
internal class DesktopVarispeed(private val channels: Int) {

    /** 1.0 is untouched, and is short-circuited entirely. */
    @Volatile
    var speed: Float = 1f

    private var input = FloatArray(0)
    private var inputFrames = 0
    /** Where the next output frame is read, in input frames from the start of [input]. */
    private var position = TAPS_EACH_SIDE.toDouble()
    private var output = FloatArray(0)
    private var engaged = false

    /** Filter weights for each of [PHASES] positions between two input frames, for [tableSpeed]. */
    private var table = FloatArray(0)
    private var tableSpeed = Float.NaN

    /** How much of the array [process] returned belongs to this call. */
    var outputCount: Int = 0
        private set

    /** Plays [count] interleaved samples at [speed]. */
    fun process(samples: FloatArray, count: Int): FloatArray {
        val rate = speed
        if (abs(rate - 1f) < 0.001f && !engaged) {
            outputCount = count
            return samples
        }
        if (!engaged) {
            // Silence before the first sample, so the filter has something either side of it.
            engaged = true
            reset()
            grow(TAPS_EACH_SIDE)
            inputFrames = TAPS_EACH_SIDE
        }
        if (rate != tableSpeed) buildTable(rate)

        val frames = count / channels
        grow(inputFrames + frames)
        System.arraycopy(samples, 0, input, inputFrames * channels, frames * channels)
        inputFrames += frames

        val most = ((inputFrames - position) / rate).toInt() + 2
        if (output.size < most * channels) output = FloatArray(most * channels)
        var produced = 0
        val taps = TAPS_EACH_SIDE * 2
        while (position + TAPS_EACH_SIDE < inputFrames) {
            val whole = floor(position).toInt()
            val phase = ((position - whole) * PHASES).toInt().coerceIn(0, PHASES - 1)
            val weights = phase * taps
            val first = whole - TAPS_EACH_SIDE + 1
            if (channels == 2) {
                // Both sides in one pass over the taps, each weight read once for the two: stereo
                // is nearly every track, and a pass per channel read the table and walked the input
                // twice. Each side still sums its taps in the same order, to the same result.
                var left = 0f
                var right = 0f
                var at = first * 2
                for (tap in 0 until taps) {
                    val weight = table[weights + tap]
                    left += input[at] * weight
                    right += input[at + 1] * weight
                    at += 2
                }
                output[produced * 2] = left
                output[produced * 2 + 1] = right
            } else {
                for (channel in 0 until channels) {
                    var sum = 0f
                    var at = first * channels + channel
                    for (tap in 0 until taps) {
                        sum += input[at] * table[weights + tap]
                        at += channels
                    }
                    output[produced * channels + channel] = sum
                }
            }
            produced++
            position += rate
        }

        // Keep only what the next reads still reach back to.
        val keepFrom = (floor(position).toInt() - TAPS_EACH_SIDE + 1).coerceIn(0, inputFrames)
        if (keepFrom > 0) {
            System.arraycopy(input, keepFrom * channels, input, 0, (inputFrames - keepFrom) * channels)
            inputFrames -= keepFrom
            position -= keepFrom
        }
        outputCount = produced * channels
        return output
    }

    /** Drops what is held: a seek, a new track, a flush. */
    fun reset() {
        inputFrames = 0
        position = TAPS_EACH_SIDE.toDouble()
        if (abs(speed - 1f) < 0.001f) engaged = false
    }

    private fun grow(frames: Int) {
        if (input.size < frames * channels) input = input.copyOf(maxOf(frames * channels, input.size * 2))
    }

    /** A Lanczos-windowed sinc, cut off at the output's own limit when the input is read faster. */
    private fun buildTable(rate: Float) {
        val cutoff = if (rate > 1f) 1.0 / rate else 1.0
        val taps = TAPS_EACH_SIDE * 2
        val weights = FloatArray(PHASES * taps)
        for (phase in 0 until PHASES) {
            val fraction = phase.toDouble() / PHASES
            var total = 0.0
            for (tap in 0 until taps) {
                // Distance from the read position to input frame (whole - TAPS_EACH_SIDE + 1 + tap).
                val x = tap - TAPS_EACH_SIDE + 1 - fraction
                val sinc = if (abs(x) < 1e-9) 1.0 else sin(PI * x * cutoff) / (PI * x * cutoff)
                val window = 0.5 + 0.5 * cos(PI * x / TAPS_EACH_SIDE)
                val weight = cutoff * sinc * window
                weights[phase * taps + tap] = weight.toFloat()
                total += weight
            }
            // Each set sums to one, so a steady level stays exactly where it was.
            for (tap in 0 until taps) weights[phase * taps + tap] = (weights[phase * taps + tap] / total).toFloat()
        }
        table = weights
        tableSpeed = rate
    }

    private companion object {
        const val TAPS_EACH_SIDE = 16
        const val PHASES = 256
    }
}
