package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Which full-screen player the window opens. */
internal enum class DesktopPlayerStyle { DISC, CLASSIC }

/** What the disc player puts behind the music when a track has a music video. */
internal enum class DesktopVisualMode { VIDEO, ARTWORK }

/** Where the main window shows what is playing. */
internal enum class DesktopNowPlayingPlacement { BOTTOM, TOP }

/** What moves behind the track in the disc player, driven by the real output. */
internal enum class DesktopVisualizerStyle {
    NONE, AURORA, RING, WAVES, PARTICLES, SPECTRUM, BARS;

    /** The next one along, for the player's cycle button. */
    fun next(): DesktopVisualizerStyle = entries[(ordinal + 1) % entries.size]
}

/**
 * The look this build adds on top of the stock desktop app: the disc player, its video, the
 * bottom now-playing bar, the liquid glass. Collected far from the settings page that writes them,
 * so each is a flow, persisted the moment it changes.
 */
internal object DesktopStyleSettings {

    private val persistence by lazy { DesktopPersistence() }

    private inline fun <reified T : Enum<T>> choice(key: String, default: T): MutableStateFlow<T> =
        MutableStateFlow(
            runCatching { enumValueOf<T>(persistence.string(key, default.name)) }.getOrDefault(default),
        )

    private fun flag(key: String, default: Boolean) = MutableStateFlow(persistence.boolean(key, default))

    private val _playerStyle = choice(KEY_PLAYER_STYLE, DesktopPlayerStyle.DISC)
    private val _visualMode = choice(KEY_VISUAL_MODE, DesktopVisualMode.VIDEO)
    private val _placement = choice(KEY_NOW_PLAYING_PLACEMENT, DesktopNowPlayingPlacement.TOP)
    private val _lyricsScale = MutableStateFlow(persistence.float(KEY_LYRICS_SCALE, 1.3f))
    private val _showDisc = flag(KEY_SHOW_DISC, true)
    private val _backdropText = flag(KEY_BACKDROP_TEXT, true)
    private val _visualizerStyle = choice(
        KEY_VISUALIZER_STYLE,
        // The older on/off switch, carried over: off stays off.
        if (persistence.boolean(KEY_VISUALIZER, true)) DesktopVisualizerStyle.AURORA else DesktopVisualizerStyle.NONE,
    )
    private val _liquidGlass = flag(KEY_LIQUID_GLASS, true)
    private val _animatedHome = flag(KEY_ANIMATED_HOME, true)
    private val _heroVisualizer = choice(KEY_HERO_VISUALIZER, DesktopVisualizerStyle.BARS)
    private val _heroLyrics = flag(KEY_HERO_LYRICS, true)

    /** What moves in the home card, apart from the player's own. */
    val heroVisualizer: StateFlow<DesktopVisualizerStyle> = _heroVisualizer

    /** The line being sung, over the home card's visualiser. */
    val heroLyrics: StateFlow<Boolean> = _heroLyrics

    fun setHeroVisualizer(value: DesktopVisualizerStyle) = save(KEY_HERO_VISUALIZER, value, _heroVisualizer)
    fun setHeroLyrics(value: Boolean) = save(KEY_HERO_LYRICS, value, _heroLyrics)

    private val _barTinted = flag(KEY_BAR_TINTED, true)

    /** The floating player bars in the cover's colour; off, clear glass. */
    val barTinted: StateFlow<Boolean> = _barTinted

    fun setBarTinted(value: Boolean) = save(KEY_BAR_TINTED, value, _barTinted)

    val playerStyle: StateFlow<DesktopPlayerStyle> = _playerStyle
    val visualMode: StateFlow<DesktopVisualMode> = _visualMode
    val nowPlayingPlacement: StateFlow<DesktopNowPlayingPlacement> = _placement

    /** The spinning CD beside the track; off leaves the artwork card on its own. */
    val showDisc: StateFlow<Boolean> = _showDisc

    /** The oversized artist name drifting behind the player. */
    val backdropText: StateFlow<Boolean> = _backdropText

    /** How much bigger than the shared panel's own type the disc player draws its lyrics. */
    val lyricsScale: StateFlow<Float> = _lyricsScale

    fun setLyricsScale(value: Float) {
        persistence.saveFloat(KEY_LYRICS_SCALE, value)
        _lyricsScale.value = value
    }

    /** What moves behind the track. */
    val visualizerStyle: StateFlow<DesktopVisualizerStyle> = _visualizerStyle

    /** Refraction, rim light and dispersion on the floating bars instead of flat frost. */
    val liquidGlass: StateFlow<Boolean> = _liquidGlass

    /** The main window's moving colour, card motion and greeting. */
    val animatedHome: StateFlow<Boolean> = _animatedHome

    fun setPlayerStyle(value: DesktopPlayerStyle) = save(KEY_PLAYER_STYLE, value, _playerStyle)
    fun setVisualMode(value: DesktopVisualMode) = save(KEY_VISUAL_MODE, value, _visualMode)
    fun setNowPlayingPlacement(value: DesktopNowPlayingPlacement) = save(KEY_NOW_PLAYING_PLACEMENT, value, _placement)
    fun setShowDisc(value: Boolean) = save(KEY_SHOW_DISC, value, _showDisc)
    fun setBackdropText(value: Boolean) = save(KEY_BACKDROP_TEXT, value, _backdropText)
    fun setVisualizerStyle(value: DesktopVisualizerStyle) = save(KEY_VISUALIZER_STYLE, value, _visualizerStyle)
    fun setLiquidGlass(value: Boolean) = save(KEY_LIQUID_GLASS, value, _liquidGlass)
    fun setAnimatedHome(value: Boolean) = save(KEY_ANIMATED_HOME, value, _animatedHome)

    private fun <T : Enum<T>> save(key: String, value: T, into: MutableStateFlow<T>) {
        persistence.saveString(key, value.name)
        into.value = value
    }

    private fun save(key: String, value: Boolean, into: MutableStateFlow<Boolean>) {
        persistence.saveBoolean(key, value)
        into.value = value
    }

    private const val KEY_PLAYER_STYLE = "style_player"
    private const val KEY_VISUAL_MODE = "style_visual_mode"
    // A new key: the bar's places changed meaning (Top is now the home card that floats away).
    private const val KEY_NOW_PLAYING_PLACEMENT = "style_now_playing_placement_v2"
    private const val KEY_LYRICS_SCALE = "style_lyrics_scale"
    private const val KEY_SHOW_DISC = "style_show_disc"
    private const val KEY_BACKDROP_TEXT = "style_backdrop_text"
    private const val KEY_VISUALIZER = "style_visualizer"
    private const val KEY_VISUALIZER_STYLE = "style_visualizer_kind"
    private const val KEY_LIQUID_GLASS = "style_liquid_glass"
    private const val KEY_ANIMATED_HOME = "style_animated_home"
    private const val KEY_HERO_VISUALIZER = "style_hero_visualizer"
    private const val KEY_HERO_LYRICS = "style_hero_lyrics"
    private const val KEY_BAR_TINTED = "style_bar_tinted"
}
