package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How new versions are taken: offered at launch, or fetched quietly and put in on the way out. */
internal object DesktopUpdateSettings {

    private val persistence by lazy { DesktopPersistence() }

    private val _autoUpdate = MutableStateFlow(persistence.boolean(KEY_AUTO_UPDATE, false))
    private val _skippedVersion = MutableStateFlow(persistence.string(KEY_SKIPPED_VERSION, ""))

    /**
     * On: a new version is downloaded in the background and installed when the app is closed.
     * Off: it is offered at launch, and nothing is fetched without a click.
     */
    val autoUpdate: StateFlow<Boolean> = _autoUpdate

    /** The version the offer was told to stop showing; a newer one is offered again. */
    val skippedVersion: StateFlow<String> = _skippedVersion

    fun setAutoUpdate(value: Boolean) {
        persistence.saveBoolean(KEY_AUTO_UPDATE, value)
        _autoUpdate.value = value
    }

    fun skip(version: String) {
        persistence.saveString(KEY_SKIPPED_VERSION, version)
        _skippedVersion.value = version
    }

    private const val KEY_AUTO_UPDATE = "update_auto"
    private const val KEY_SKIPPED_VERSION = "update_skipped_version"
}
