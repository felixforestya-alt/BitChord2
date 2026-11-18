package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.JamEnergy
import com.music.bitchord.playback.JamStore

/**
 * My Jam's ear on the desktop — see [JamEnergy]. The level is the one the visualiser's analyser
 * already works out from what is being played ([DesktopAudioAnalyzer]): two reads a second.
 */
internal object DesktopJamEnergy {

    private val persistence by lazy { DesktopPersistence() }

    private val energy by lazy {
        JamEnergy(
            store = object : JamStore {
                override fun get(key: String) = persistence.string("wave_$key")
                override fun put(key: String, value: String) = persistence.saveStringSoon("wave_$key", value)
            },
            // Only while sound is coming through: paused, nothing is heard.
            level = { if (DesktopAudioAnalyzer.fedWithin(1_000_000_000L)) DesktopAudioAnalyzer.levelAt(System.nanoTime()) else null },
            isJam = { it.radioName == DesktopMyWave.radioName },
            log = DesktopTrackLog::log,
        )
    }

    /** The track the player is on now. */
    fun nowPlaying(song: Song?) = energy.nowPlaying(song)

    /** What is known of [song]'s energy, or null. */
    fun of(song: Song): Float? = energy.of(song)
}
