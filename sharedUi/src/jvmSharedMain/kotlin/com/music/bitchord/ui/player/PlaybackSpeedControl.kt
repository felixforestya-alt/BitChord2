package com.music.bitchord.ui.player

import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** The listener's playback speed, as the platform keeps it — see [PlayerHost.playbackSpeed]. */
interface PlaybackSpeedControl {
    val speed: StateFlow<Float>
    fun set(speed: Float)
}

/** The speed slider's scale, the same as the desktop's. */
internal object PlaybackSpeedScale {
    /**
     * Where [speed] sits along a slider, 0 to 1: even steps of doubling, so 1x is the middle and
     * 0.5x and 2x the ends.
     */
    fun toSlider(speed: Float): Float =
        (ln(speed.coerceIn(SLIDER_MIN, SLIDER_MAX) / SLIDER_MIN) / ln(SLIDER_MAX / SLIDER_MIN)).toFloat()

    /** The speed at [position] along a slider, to the nearest 0.05, held at 1x near the middle. */
    fun fromSlider(position: Float): Float {
        val raw = SLIDER_MIN * (SLIDER_MAX / SLIDER_MIN).pow(position.coerceIn(0f, 1f))
        if (abs(raw - 1f) < 0.035f) return 1f
        return (raw * 20f).roundToInt() / 20f
    }

    fun label(speed: Float): String {
        val hundredths = (speed * 100f).roundToInt()
        val text = when {
            hundredths % 100 == 0 -> "${hundredths / 100}"
            hundredths % 10 == 0 -> "${hundredths / 100}.${(hundredths % 100) / 10}"
            else -> "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
        }
        return "$text×"
    }

    private const val SLIDER_MIN = 0.5f
    private const val SLIDER_MAX = 2.0f
}
