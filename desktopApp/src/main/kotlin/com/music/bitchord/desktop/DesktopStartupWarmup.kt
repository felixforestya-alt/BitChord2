package com.music.bitchord.desktop

import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The window's first moments, spent out of sight.
 *
 * Direct3D compiles each shader the first time something needs it, on the drawing thread, and a
 * frame that meets several new ones takes tens of milliseconds: the first time the player opened,
 * the first time the home card turned into the bar. Priming them faintly in the open still left a
 * hitch on the page while it ran. So the window starts *cloaked* — DWM's own way of keeping a
 * window off the screen while it goes on drawing, as Windows does for windows on another virtual
 * desktop — and in that time the app opens and closes the player and scrolls Home for real, at
 * full strength, through exactly the shaders those will use. Then the cloak comes off, and the
 * window is shown already warm. It appears a moment later than it would have; nothing in it
 * looks any different, and nothing after stutters.
 */
internal object DesktopStartupWarmup {

    /** Whether this launch warms up behind a cloak: Windows, and not turned off for a measuring run. */
    val enabled: Boolean = DesktopPlatform.isWindows && System.getenv("BITCHORD_NO_WARMUP") != "1"

    private val _cloaked = MutableStateFlow(false)

    /** While the window is cloaked: the app's warm-up runs while this holds. */
    val cloaked: StateFlow<Boolean> = _cloaked

    private val _ready = MutableStateFlow(!enabled)

    /** Whether the window may be shown: at once without a warm-up, once cloaked with one. */
    val ready: StateFlow<Boolean> = _ready

    @Volatile private var window: java.awt.Window? = null
    @Volatile private var startedAt = 0L

    /**
     * Gives [window] its native handle without showing it, cloaks it, and lets it be shown. Called
     * on the event thread while the window is still hidden.
     */
    fun prepare(window: java.awt.Window) {
        if (!enabled || this.window != null) return
        this.window = window
        val cloakedOk = runCatching {
            if (!window.isDisplayable) window.addNotify()
            setCloak(window, true)
        }.getOrDefault(false)
        if (cloakedOk) {
            startedAt = System.nanoTime()
            _cloaked.value = true
            DesktopTrackLog.log("startup: window cloaked for the warm-up")
            // However the warm-up goes, the window is not kept from the listener for long.
            Thread({
                Thread.sleep(MAX_CLOAK_MS)
                if (_cloaked.value) {
                    DesktopTrackLog.log("startup: warm-up ran long, showing the window anyway")
                    finish()
                }
            }, "bitchord-warmup-guard").apply { isDaemon = true }.start()
        } else {
            DesktopTrackLog.log("startup: could not cloak the window; showing it straight away")
        }
        _ready.value = true
    }

    /** Ends the warm-up: the cloak comes off and the window is seen. */
    fun finish() {
        if (!_cloaked.value) return
        _cloaked.value = false
        val target = window ?: return
        java.awt.EventQueue.invokeLater {
            runCatching { setCloak(target, false) }
            DesktopTrackLog.log("startup: window shown after %d ms warming up".format((System.nanoTime() - startedAt) / 1_000_000))
        }
    }

    private fun setCloak(window: java.awt.Window, on: Boolean): Boolean {
        val hwnd = Native.getWindowPointer(window) ?: return false
        val result = NativeLibrary.getInstance("dwmapi").getFunction("DwmSetWindowAttribute")
            .invokeInt(arrayOf(hwnd, DWMWA_CLOAK, IntByReference(if (on) 1 else 0), 4))
        return result == 0
    }

    /**
     * [ms] as an animation should take it: half as long while the warm-up plays behind the
     * cloak. A shader is made the first time its kind of draw appears, however fast the animation
     * runs through it, and the window should not be kept from the listener any longer than that.
     */
    fun paced(ms: Int): Int = if (_cloaked.value) (ms / WARMUP_SPEED).coerceAtLeast(1) else ms

    private const val WARMUP_SPEED = 2
    private const val DWMWA_CLOAK = 13
    private const val MAX_CLOAK_MS = 6_000L
}
