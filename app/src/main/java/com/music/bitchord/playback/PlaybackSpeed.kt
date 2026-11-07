package com.music.bitchord.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.music.bitchord.data.settings.AppSettings

/**
 * Plays at [speed] the way the listener asked for it: by default like a record turned faster or
 * slower, the pitch going with the tempo, as the desktop does — or, with "Keep the pitch" on,
 * stretched in time with the pitch held, which is what Media3's own setPlaybackSpeed does and
 * what made a faster song sound chopped.
 *
 * The pitch follows the listener's own speed alone. Automix nudges [speed] a little on top of it
 * to match two tempos, and those nudges keep the pitch where it was, so two tracks mixed in key
 * stay in key.
 */
internal fun Player.setBitChordSpeed(speed: Float) {
    val pitch = if (AppSettings.speedKeepsPitch.value) 1f else AppSettings.playbackSpeed.value
    playbackParameters = PlaybackParameters(speed, pitch.coerceIn(MIN_PITCH, MAX_PITCH))
}

private const val MIN_PITCH = 0.25f
private const val MAX_PITCH = 4f
