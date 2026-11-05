package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The listener's playback speed, one value for the player's own slider, Settings and the system's
 * media controls alike, kept between runs.
 */
internal object DesktopSpeedControl {
    private val persistence by lazy { DesktopPersistence() }

    private val _speed = MutableStateFlow(
        (persistence.string(KEY, "1.0").toFloatOrNull() ?: 1f).coerceIn(MIN, MAX),
    )
    val speed: StateFlow<Float> = _speed.asStateFlow()

    fun set(value: Float) {
        val speed = value.coerceIn(MIN, MAX)
        if (speed == _speed.value) return
        _speed.value = speed
        persistence.saveStringSoon(KEY, speed.toString())
    }

    /**
     * Where [speed] sits along a slider, 0 to 1: even steps of doubling, so 1x is the middle and
     * 0.5x and 2x the ends.
     */
    fun toSlider(speed: Float): Float =
        ((ln(speed.coerceIn(SLIDER_MIN, SLIDER_MAX) / SLIDER_MIN) / ln(SLIDER_MAX / SLIDER_MIN))).toFloat()

    /** The speed at [position] along a slider, to the nearest 0.05, held at 1x near the middle. */
    fun fromSlider(position: Float): Float {
        val raw = SLIDER_MIN * (SLIDER_MAX / SLIDER_MIN).pow(position.coerceIn(0f, 1f))
        if (abs(raw - 1f) < 0.035f) return 1f
        return (raw * 20f).roundToInt() / 20f
    }

    fun label(speed: Float): String = "%.2f×".format(speed).replace(Regex("0×$"), "×").replace(Regex("\\.0×$"), "×")

    private const val KEY = "playback_speed"
    private const val MIN = 0.25f
    private const val MAX = 3.0f
    private const val SLIDER_MIN = 0.5f
    private const val SLIDER_MAX = 2.0f
}
