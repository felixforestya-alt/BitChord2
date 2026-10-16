package com.music.bitchord.desktop

import kotlin.math.abs
import kotlin.math.exp

/**
 * The last thing the samples pass through before the device: volume, then a peak limiter.
 *
 * Volume used to be applied and the result hard-clipped at ±1 on the way out. With the equaliser
 * boosting, the spatial widener summing, or an Automix blend overlapping two loud masters, a
 * sample over full scale is ordinary — and a hard clip turns every one of them into a square
 * corner, which is the harsh crackle at the loud bits. This rides the gain down smoothly instead,
 * and only ever when a peak would actually cross the ceiling: quieter than that, it is bit-for-bit
 * the volume-scaled input.
 */
internal class DesktopOutputStage {

    private var output = FloatArray(0)

    /** The limiter's current gain, 1 when nothing is being held down. */
    private var held = 1f
    private var releaseCoefficient = 0f
    private var configuredRate = 0

    /**
     * [samples] scaled by [volume] and limited, in a buffer this owns (valid until the next call).
     */
    fun process(samples: FloatArray, count: Int, channels: Int, sampleRate: Int, volume: Float): FloatArray {
        if (output.size < count) output = FloatArray(count)
        if (sampleRate != configuredRate && sampleRate > 0) {
            configuredRate = sampleRate
            releaseCoefficient = exp(-1.0 / (RELEASE_SECONDS * sampleRate)).toFloat()
        }
        val step = channels.coerceAtLeast(1)
        var frame = 0
        while (frame + step <= count) {
            var peak = 0f
            for (channel in 0 until step) {
                val value = abs(samples[frame + channel] * volume)
                if (value > peak) peak = value
            }
            val wanted = if (peak > CEILING) CEILING / peak else 1f
            // Instant on the way down, so nothing gets past; smooth on the way back up, so the
            // recovery is not heard as a pump.
            held = if (wanted < held) wanted else wanted - (wanted - held) * releaseCoefficient
            val gain = volume * held
            for (channel in 0 until step) {
                output[frame + channel] = (samples[frame + channel] * gain).coerceIn(-1f, 1f)
            }
            frame += step
        }
        // A trailing partial frame, which a well-formed block never has.
        while (frame < count) {
            output[frame] = (samples[frame] * volume).coerceIn(-CEILING, CEILING)
            frame++
        }
        return output
    }

    fun reset() {
        held = 1f
    }

    private companion object {
        /** -0.3 dBFS: headroom for the inter-sample peaks a DAC reconstructs between samples. */
        const val CEILING = 0.966f
        const val RELEASE_SECONDS = 0.120
    }
}

/**
 * Triangular (TPDF) dither for a 16-bit output: replaces the distortion of plain truncation with a
 * benign noise floor at about -96 dBFS. Only ever used when a device refuses float and 24-bit both.
 */
internal class DesktopDither {
    private var seed = 0x2545F491L

    private fun next(): Float {
        // xorshift: plenty for dither, and allocation-free on the output thread.
        seed = seed xor (seed shl 13)
        seed = seed xor (seed ushr 7)
        seed = seed xor (seed shl 17)
        return ((seed ushr 40).toInt() and 0xFFFFFF) / 16_777_216f
    }

    /** One LSB-scaled TPDF sample, for a target of 16 bits. */
    fun sample(): Float = (next() - next()) / 32_768f
}
