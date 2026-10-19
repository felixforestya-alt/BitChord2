package com.music.bitchord.desktop

import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.awt.Container
import java.awt.Window
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate

/**
 * Frames limited to the monitor's rate by a precise timer, rather than held to Direct3D's vsync.
 *
 * Skia presents each frame with a sync interval of one and waits for its buffer back. Alone on
 * the GPU that holds the screen's rate; with a game running — in front, behind, or minimised —
 * the buffer came back a refresh late every time, so on a 160 Hz screen the player ran at 78
 * frames a second while drawing one took four milliseconds of the six it had. Measured with
 * Deadlock open: 78 fps with Skia's vsync; 233–258 drawn with none, so the drawing was never
 * short.
 *
 * Waiting for the monitor's vertical blank instead did no better in a match: with G-SYNC the
 * monitor refreshes when the game presents, so its blanks came irregularly — 127 a second, gaps
 * of 20 ms — and frames waiting for them ran at 83. Nor did waiting on the desktop's
 * compositions (DwmFlush), 54: that wait includes the window's own last frame reaching the
 * screen, which is exactly what comes late.
 *
 * So Skia presents without waiting, and a frame that comes sooner than one refresh after the
 * last waits out the rest on a high-resolution waitable timer: no spinning, a fraction of a
 * millisecond late at most. At a fixed refresh the desktop shows the newest frame at each one;
 * under G-SYNC the monitor shows each as it lands. A frame is still only drawn when something
 * on screen changes; nothing here asks for frames, so an idle window stays idle.
 */
internal object DesktopFramePacing {

    @Volatile
    private var active = false

    /** Before the first window: tells Skia not to wait for vsync itself, when this can take over. */
    fun prepare() {
        if (!DesktopPlatform.isWindows) return
        // Set on the command line — say for a benchmark of frames unpaced — that wins.
        if (System.getProperty("skiko.vsync.enabled") != null) return
        if (System.getProperty("skiko.renderApi") != DesktopGraphicsApi.DIRECT3D.skikoName) return
        // Only with a timer precise enough; otherwise Skia keeps its own vsync.
        if (Timer.handle == null) return
        System.setProperty("skiko.vsync.enabled", "false")
        active = true
    }

    /** Puts the pacing in front of the window's drawing; again if the layer or its drawing is replaced. */
    fun attach(window: Window) {
        if (!active) return
        val check = javax.swing.Timer(CHECK_MS) { wrap(window) }
        check.isRepeats = true
        check.start()
        wrap(window)
    }

    private fun wrap(window: Window) {
        val layer = findLayer(window) ?: return
        val inner = layer.renderDelegate ?: return
        if (inner is Paced) return
        layer.renderDelegate = Paced(inner, window)
        DesktopTrackLog.log("frames: limited to the monitor's rate by a precise timer")
    }

    private fun findLayer(container: Container): SkiaLayer? {
        for (child in container.components) {
            if (child is SkiaLayer) return child
            if (child is Container) findLayer(child)?.let { return it }
        }
        return null
    }

    private class Paced(private val inner: SkikoRenderDelegate, private val window: Window) : SkikoRenderDelegate {

        /** When the next frame may start. */
        private var next = 0L
        private var period = 0L
        private var periodCheckedAt = 0L

        override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
            var now = System.nanoTime()
            if (now - periodCheckedAt > PERIOD_CHECK_NANOS) {
                period = periodNanos(window)
                periodCheckedAt = now
            }
            if (now < next) {
                Timer.waitNanos(next - now)
                now = System.nanoTime()
            }
            // On time, the next is one refresh after this one was due, so the rate holds exactly;
            // late by more than a refresh — a pause, or a long frame — the count starts again.
            next = if (next != 0L && now - next < period) next + period else now + period
            inner.onRender(canvas, width, height, now)
        }
    }

    /**
     * The monitor's refresh period. The desktop's own figure where it gives one (159.98 Hz, not
     * the 160 the display mode rounds to: frames a hair faster than the screen would now and then
     * land two to a refresh), else the display mode's.
     */
    private fun periodNanos(window: Window): Long {
        val hz = runCatching { window.graphicsConfiguration.device.displayMode.refreshRate }.getOrDefault(60)
        val mode = 1_000_000_000L / (if (hz in 24..1000) hz else 60)
        val dwm = runCatching { compositionPeriodNanos() }.getOrNull() ?: return mode
        // The desktop composes at the fastest monitor's rate; trust it only when it agrees with
        // the monitor the window is on.
        return if (kotlin.math.abs(dwm - mode) < mode / 50) dwm else mode
    }

    /** DWM_TIMING_INFO.rateRefresh, from DwmGetCompositionTimingInfo. */
    private fun compositionPeriodNanos(): Long? {
        val info = Memory(TIMING_INFO_SIZE.toLong()).apply { clear(); setInt(0, TIMING_INFO_SIZE) }
        val result = NativeLibrary.getInstance("dwmapi").getFunction("DwmGetCompositionTimingInfo")
            .invokeInt(arrayOf<Any?>(null, info))
        if (result != 0) return null
        val numerator = info.getInt(4).toLong() and 0xFFFFFFFFL
        val denominator = info.getInt(8).toLong() and 0xFFFFFFFFL
        if (numerator == 0L || denominator == 0L) return null
        return denominator * 1_000_000_000L / numerator
    }

    /** A high-resolution waitable timer: waits to well under a millisecond without spinning. One user, the UI thread. */
    private object Timer {
        private val kernel32 = NativeLibrary.getInstance("kernel32")
        private val setTimer = kernel32.getFunction("SetWaitableTimer")
        private val waitFor = kernel32.getFunction("WaitForSingleObject")
        private val due = Memory(8)

        val handle: Pointer? = runCatching {
            kernel32.getFunction("CreateWaitableTimerExW")
                .invokePointer(arrayOf<Any?>(null, null, CREATE_WAITABLE_TIMER_HIGH_RESOLUTION, TIMER_ALL_ACCESS))
        }.getOrNull()

        fun waitNanos(nanos: Long) {
            val timer = handle ?: return
            // Relative times are negative, in units of 100 ns. Never more than two refreshes.
            due.setLong(0, -(nanos.coerceIn(0, 20_000_000) / 100))
            if (setTimer.invokeInt(arrayOf(timer, due, 0, null, null, 0)) == 0) return
            waitFor.invokeInt(arrayOf(timer, WAIT_LIMIT_MS))
        }
    }

    private const val CREATE_WAITABLE_TIMER_HIGH_RESOLUTION = 0x00000002
    private const val TIMER_ALL_ACCESS = 0x1F0003
    private const val WAIT_LIMIT_MS = 50
    private const val TIMING_INFO_SIZE = 292
    private const val PERIOD_CHECK_NANOS = 2_000_000_000L
    private const val CHECK_MS = 2_000
}
