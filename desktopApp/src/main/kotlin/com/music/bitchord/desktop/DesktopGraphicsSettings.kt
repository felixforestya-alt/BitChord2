package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Which of the system's graphics APIs the window is drawn through. */
internal enum class DesktopGraphicsApi(val skikoName: String) { OPENGL("OPENGL"), DIRECT3D("DIRECT3D") }

/**
 * The graphics API, chosen before the window exists. Direct3D by default: measured side by side
 * with the player open on a 4K, 160 Hz screen, both took about the same share of the processor,
 * and Direct3D kept its frames on time (4-5 late in ten seconds) where OpenGL let 40-100 slip.
 * OpenGL stays as the way round a machine whose Direct3D driver misbehaves. Read at launch; a
 * change applies from the next one.
 */
internal object DesktopGraphicsSettings {

    private val persistence by lazy { DesktopPersistence() }

    private val _api = MutableStateFlow(
        runCatching { DesktopGraphicsApi.valueOf(persistence.string(KEY_API, DesktopGraphicsApi.DIRECT3D.name)) }
            .getOrDefault(DesktopGraphicsApi.DIRECT3D),
    )

    val api: StateFlow<DesktopGraphicsApi> = _api

    fun setApi(value: DesktopGraphicsApi) {
        persistence.saveString(KEY_API, value.name)
        _api.value = value
    }

    /** Tells Skia which API to draw through; before the first window, or it is too late. */
    fun apply() {
        if (!DesktopPlatform.isWindows) return
        // Set explicitly on the command line, say for a test: that wins.
        if (System.getProperty("skiko.renderApi") != null) return
        System.setProperty("skiko.renderApi", _api.value.skikoName)
    }

    private const val KEY_API = "graphics_api"
}
