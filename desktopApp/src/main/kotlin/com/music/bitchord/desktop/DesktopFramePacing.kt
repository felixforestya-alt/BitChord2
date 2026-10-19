package com.music.bitchord.desktop

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.awt.Container
import java.awt.Window
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate

/**
 * Frames started on the monitor's vertical blank, with a precise timer to stand in when the blanks
 * come irregularly — rather than held to Direct3D's vsync.
 *
 * Skia presents each frame with a sync interval of one and waits for its buffer back. Alone on
 * the GPU that holds the screen's rate; with a game running — in front, behind, or minimised —
 * the buffer came back a refresh late every time, so on a 160 Hz screen the player ran at 78
 * frames a second while drawing one took four milliseconds of the six it had. Measured with
 * Deadlock open: 78 fps with Skia's vsync; 233–258 drawn with none.
 *
 * So Skia presents without waiting, and each frame waits at its start:
 *  - for the monitor's next vertical blank, asked of the driver. A frame started there is on
 *    screen at the blank after, every time: the motion is as even as the display. A timer alone
 *    keeps the count but not the phase — frames drifting across the blanks land two in one
 *    refresh and none in the next, which looks like a lower rate than it is.
 *  - but no longer than a refresh after the last frame, on a high-resolution waitable timer. With
 *    G-SYNC the monitor refreshes when a game presents, and in a match its blanks came 127 a
 *    second with gaps of 20 ms: frames waiting on them alone ran at 83.
 * And never sooner than three quarters of a refresh after the last, so two never share one.
 *
 * A frame is still only drawn when something on screen changes; nothing here asks for frames, so
 * an idle window stays idle, and the thread following the blanks sleeps.
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
        if (Win.timer == null || Win.blankEvent == null) return
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
        DesktopTrackLog.log("frames: started on the vertical blank, a precise timer standing in")
    }

    private fun findLayer(container: Container): SkiaLayer? {
        for (child in container.components) {
            if (child is SkiaLayer) return child
            if (child is Container) findLayer(child)?.let { return it }
        }
        return null
    }

    private class Paced(private val inner: SkikoRenderDelegate, private val window: Window) : SkikoRenderDelegate {

        private var last = 0L
        private var period = 6_250_000L
        private var periodCheckedAt = Long.MIN_VALUE / 2

        override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
            val start = System.nanoTime()
            if (start - periodCheckedAt > PERIOD_CHECK_NANOS) {
                period = periodNanos(window)
                periodCheckedAt = start
                Blanks.follow(window)
            }
            Blanks.wanted()
            last = waitForTurn(start)
            inner.onRender(canvas, width, height, last)
        }

        /** Until a blank comes after the last frame's start, or a refresh has passed; returns the time. */
        private fun waitForTurn(start: Long): Long {
            val earliest = last + period * 3 / 4
            val deadline = last + period + period / 16
            var timerSetFor = 0L
            var now = start
            while (true) {
                val blank = Blanks.lastAt
                if (blank > last && now >= earliest) return now
                if (now >= deadline) return now
                // A blank before the earliest a frame may start: wait out the rest on the timer.
                val until = if (blank > last) earliest else deadline
                if (until != timerSetFor) {
                    Win.setTimer(until - now)
                    timerSetFor = until
                }
                Win.waitForBlankOrTimer()
                now = System.nanoTime()
            }
        }
    }

    /**
     * The blanks, followed on a thread of their own: the driver's call has no timeout, and a
     * monitor switched off could hold it indefinitely; the frame, meanwhile, never waits past its
     * timer. Each blank sets an event the frame can wait on beside its timer. The thread follows
     * while frames are coming, and a quarter of a second after the last it sleeps.
     */
    private object Blanks {
        @Volatile var lastAt = 0L
            private set
        private val lock = Object()
        @Volatile private var askedAt = 0L
        @Volatile private var output: Output? = null
        private var monitor: Pointer? = null

        init {
            Thread(::run, "BitChord-VBlank").apply { isDaemon = true; start() }
        }

        fun wanted() {
            val now = System.nanoTime()
            val idle = now - askedAt > FOLLOW_NANOS
            askedAt = now
            if (idle) synchronized(lock) { lock.notifyAll() }
        }

        /** Keeps the output the window is on: the blank followed is that monitor's. On the UI thread. */
        fun follow(window: Window) {
            val hwnd = runCatching { Native.getWindowPointer(window) }.getOrNull() ?: return
            val now = runCatching { Win.monitorFromWindow.invokePointer(arrayOf(hwnd, MONITOR_DEFAULTTONEAREST)) }.getOrNull() ?: return
            if (now == monitor && output != null) return
            monitor = now
            val opened = runCatching { Output.open(now) }.getOrNull() ?: return
            output?.let { old -> output = opened; old.close() } ?: run { output = opened }
        }

        private fun run() {
            while (true) {
                synchronized(lock) {
                    while (System.nanoTime() - askedAt > FOLLOW_NANOS) lock.wait()
                }
                val out = output
                if (out == null || !out.waitForBlank()) {
                    // No blanks to be had: the frames run on their timer alone.
                    Thread.sleep(50)
                    continue
                }
                lastAt = System.nanoTime()
                Win.signalBlank()
            }
        }

        private const val FOLLOW_NANOS = 250_000_000L
    }

    /**
     * The monitor's refresh period. The desktop's own figure where it gives one (159.98 Hz, not
     * the 160 the display mode rounds to), else the display mode's.
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

    /** One monitor's output on its adapter, as the driver's vertical-blank call wants it. */
    private class Output(private val adapter: Int, private val source: Int) {

        private val wait = Memory(12).apply { clear(); setInt(0, adapter); setInt(8, source) }

        fun waitForBlank(): Boolean = Win.waitForVerticalBlank.invokeInt(arrayOf(wait)) == 0

        fun close() {
            val close = Memory(4).apply { setInt(0, adapter) }
            runCatching { Win.closeAdapter.invokeInt(arrayOf(close)) }
        }

        companion object {
            fun open(monitor: Pointer): Output? {
                // MONITORINFOEXW: the device name follows the 40 bytes of MONITORINFO.
                val info = Memory(104).apply { clear(); setInt(0, 104) }
                if (Win.getMonitorInfo.invokeInt(arrayOf(monitor, info)) == 0) return null
                val name = com.sun.jna.WString(info.getWideString(40))
                val dc = Win.createDC.invokePointer(arrayOf(name, name, null, null)) ?: return null
                try {
                    // D3DKMT_OPENADAPTERFROMHDC: hDc, hAdapter, AdapterLuid, VidPnSourceId.
                    val open = Memory(24).apply { clear(); setPointer(0, dc) }
                    if (Win.openAdapterFromHdc.invokeInt(arrayOf(open)) != 0) return null
                    return Output(open.getInt(8), open.getInt(20))
                } finally {
                    Win.deleteDC.invokeInt(arrayOf(dc))
                }
            }
        }
    }

    private object Win {
        private val user32 = NativeLibrary.getInstance("user32")
        private val gdi32 = NativeLibrary.getInstance("gdi32")
        private val kernel32 = NativeLibrary.getInstance("kernel32")
        val monitorFromWindow = user32.getFunction("MonitorFromWindow")
        val getMonitorInfo = user32.getFunction("GetMonitorInfoW")
        val createDC = gdi32.getFunction("CreateDCW")
        val deleteDC = gdi32.getFunction("DeleteDC")
        val openAdapterFromHdc = gdi32.getFunction("D3DKMTOpenAdapterFromHdc")
        val waitForVerticalBlank = gdi32.getFunction("D3DKMTWaitForVerticalBlankEvent")
        val closeAdapter = gdi32.getFunction("D3DKMTCloseAdapter")
        private val setWaitableTimer = kernel32.getFunction("SetWaitableTimer")
        private val setEvent = kernel32.getFunction("SetEvent")
        private val waitForMultiple = kernel32.getFunction("WaitForMultipleObjects")

        /** High resolution: waits to well under a millisecond without spinning. Used by the UI thread alone. */
        val timer: Pointer? = runCatching {
            kernel32.getFunction("CreateWaitableTimerExW")
                .invokePointer(arrayOf<Any?>(null, null, CREATE_WAITABLE_TIMER_HIGH_RESOLUTION, TIMER_ALL_ACCESS))
        }.getOrNull()

        /** Auto-reset, set at each blank. */
        val blankEvent: Pointer? = runCatching {
            kernel32.getFunction("CreateEventW").invokePointer(arrayOf<Any?>(null, 0, 0, null))
        }.getOrNull()

        private val due = Memory(8)
        private val handles = Memory(Native.POINTER_SIZE * 2L).apply {
            setPointer(0, blankEvent)
            setPointer(Native.POINTER_SIZE.toLong(), timer)
        }

        fun setTimer(nanos: Long) {
            // Relative times are negative, in units of 100 ns.
            due.setLong(0, -(nanos.coerceIn(1_000, 20_000_000) / 100))
            setWaitableTimer.invokeInt(arrayOf(timer, due, 0, null, null, 0))
        }

        fun signalBlank() {
            setEvent.invokeInt(arrayOf(blankEvent))
        }

        fun waitForBlankOrTimer() {
            waitForMultiple.invokeInt(arrayOf(2, handles, 0, WAIT_LIMIT_MS))
        }
    }

    private const val MONITOR_DEFAULTTONEAREST = 2
    private const val CREATE_WAITABLE_TIMER_HIGH_RESOLUTION = 0x00000002
    private const val TIMER_ALL_ACCESS = 0x1F0003
    private const val WAIT_LIMIT_MS = 50
    private const val TIMING_INFO_SIZE = 292
    private const val PERIOD_CHECK_NANOS = 2_000_000_000L
    private const val CHECK_MS = 2_000
}
