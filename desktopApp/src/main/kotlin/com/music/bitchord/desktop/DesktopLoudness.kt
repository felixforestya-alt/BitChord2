package com.music.bitchord.desktop

import com.music.bitchord.data.innertube.StreamResolver
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether tracks are brought to the same loudness. */
internal object DesktopLoudnessSettings {

    private val persistence by lazy { DesktopPersistence() }

    private val _enabled = MutableStateFlow(persistence.boolean(KEY, true))

    /** Read on the audio thread as well; a StateFlow's value is safe there. */
    val enabled: StateFlow<Boolean> = _enabled

    fun setEnabled(value: Boolean) {
        persistence.saveBoolean(KEY, value)
        _enabled.value = value
    }

    private const val KEY = "loudness_normalization"
}

/**
 * Loudness normalization for one track, as the phone's `LoudnessProcessor` does it: YouTube's own
 * figure for how far the track sits from its reference loudness, turned into a gain — down by up
 * to 15 dB for a loud master, up by at most 3 dB for a quiet one — glided rather than stepped,
 * with a peak catcher so a boosted track never clips.
 *
 * One per track, not one for the output: in a crossfade each side keeps its own level, rather
 * than the outgoing track being re-levelled to the incoming one's figure as the blend begins.
 *
 * The figure is read lazily on every block, on the audio thread: it often arrives a moment after
 * the track starts, and is then glided in.
 */
internal class DesktopLoudness(private val videoId: String, sampleRate: Int) {

    private var gain = 1f
    private var reduction = 1f
    private var snapped = false
    private var reported = false
    private val glide = coefficient(GLIDE_SECONDS, sampleRate)
    private val release = coefficient(RELEASE_SECONDS, sampleRate)

    /** Levels [count] interleaved samples of [channels] channels in place. */
    fun process(samples: FloatArray, count: Int, channels: Int) {
        if (channels < 1 || count <= 0) return
        val target = targetGain()
        if (!snapped) {
            // The first block lands at the track's own level, not glided up to it from unity.
            gain = target
            snapped = true
        }
        // Nothing to do, and nothing that could clip: leave the samples untouched.
        if (target == 1f && abs(gain - 1f) < SETTLED && reduction >= PARKED) {
            gain = 1f
            reduction = 1f
            return
        }
        var g = gain
        var gr = reduction
        var index = 0
        val frames = count / channels
        for (frame in 0 until frames) {
            g += (target - g) * glide
            var peak = 0f
            for (c in 0 until channels) {
                val m = abs(samples[index + c])
                if (m > peak) peak = m
            }
            peak *= g
            val wanted = if (peak > LIMIT) LIMIT / peak else 1f
            gr = if (wanted < gr) wanted else gr + (wanted - gr) * release
            val applied = g * gr
            for (c in 0 until channels) samples[index + c] *= applied
            index += channels
        }
        gain = g
        reduction = gr
    }

    private fun targetGain(): Float {
        if (!DesktopLoudnessSettings.enabled.value) return 1f
        val loudnessDb = StreamResolver.loudnessDbFor(videoId) ?: return 1f
        val db = (-loudnessDb).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        if (!reported) {
            reported = true
            DesktopTrackLog.log("loudness: %s %+.1f dB (YouTube says %+.1f)".format(videoId, db, loudnessDb))
        }
        return 10.0.pow(db / 20.0).toFloat()
    }

    private fun coefficient(seconds: Double, rate: Int): Float =
        if (rate <= 0) 1f else (1.0 - exp(-1.0 / (seconds * rate))).toFloat()

    private companion object {
        const val MIN_GAIN_DB = -15.0
        const val MAX_GAIN_DB = 3.0
        const val GLIDE_SECONDS = 0.5
        const val RELEASE_SECONDS = 0.25
        /** Just under full scale. */
        const val LIMIT = 0.98f
        const val SETTLED = 0.001f
        const val PARKED = 0.997f
    }
}
