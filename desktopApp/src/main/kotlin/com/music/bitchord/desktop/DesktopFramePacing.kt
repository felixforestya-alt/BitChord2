package com.music.bitchord.desktop

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import java.awt.Container
import java.awt.Window
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate

/**
 * Frames timed to the monitor's own refresh rather than to Direct3D's vsync.
 *
 * Skia presents each frame with a sync interval of one and waits for its buffer back. Alone on
 * the GPU that holds the screen's rate; with a game running — in front, behind, or minimised —
 * the buffer came back a refresh late every time, so on a 160 Hz screen the player ran at 78
 * frames a second while drawing one took four milliseconds of the six it had. Measured with
 * Deadlock open: 78 fps with Skia's vsync; 233 drawn with none, so the drawing was never short.
 * Waiting on the desktop's compositions instead (DwmFlush) was worse, 54: that wait includes
 * the window's own last frame reaching the screen, which is exactly what comes late.
 *
 * So Skia presents without waiting, and the frame waits at its start for the monitor's next
 * vertical blank — the display's own clock, asked of the driver, and not held up by anyone's
 * queue. A frame is still only drawn when something on screen changes; nothing here asks for
 * frames, so an idle window stays idle and the waiting thread sleeps.
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
        // Only when the driver answers; otherwise Skia keeps its own vsync.
        val probe = runCatching { Output.open(null) }.getOrNull() ?: return
        probe.close()
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
        DesktopTrackLog.log("frames: paced to the monitor's vertical blank")
    }

    private fun findLayer(container: Container): SkiaLayer? {
        for (child in container.components) {
            if (child is SkiaLayer) return child
            if (child is Container) findLayer(child)?.let { return it }
        }
        return null
    }

    private class Paced(private val inner: SkikoRenderDelegate, private val window: Window) : SkikoRenderDelegate {
        override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
            VBlank.follow(window)
            VBlank.await()
            inner.onRender(canvas, width, height, System.nanoTime())
        }
    }

    /**
     * The wait itself, on a thread of its own: the driver's call has no timeout, and a monitor
     * switched off could hold it indefinitely. The frame waits for it at most two refreshes,
     * then goes on unpaced.
     *
     * While frames are coming the thread follows every blank, so a frame that comes a moment
     * after one — its predecessor ran a little long — takes that blank rather than waiting a
     * whole refresh for the next. A quarter of a second after the last frame it stops, and
     * sleeps until another asks.
     */
    private object VBlank {
        private val lock = Object()
        private var lastAsked = 0L
        private var blanks = 0L
        private var blankAt = 0L
        private var taken = 0L
        @Volatile private var output: Output? = null
        private var monitor: Pointer? = null
        @Volatile private var periodNanos = 7_000_000L

        init {
            Thread(::run, "BitChord-VBlank").apply { isDaemon = true; start() }
        }

        /** Keeps the output the window is on: the blank waited for is that monitor's. On the UI thread. */
        fun follow(window: Window) {
            val hwnd = runCatching { Native.getWindowPointer(window) }.getOrNull() ?: return
            val now = runCatching { Win.monitorFromWindow.invokePointer(arrayOf(hwnd, MONITOR_DEFAULTTONEAREST)) }.getOrNull() ?: return
            if (now == monitor && output != null) return
            monitor = now
            val opened = runCatching { Output.open(now) }.getOrNull() ?: return
            output?.let { old -> output = opened; old.close() } ?: run { output = opened }
            val hz = runCatching { window.graphicsConfiguration.device.displayMode.refreshRate }.getOrDefault(60)
            periodNanos = 1_000_000_000L / (if (hz in 24..1000) hz else 60)
        }

        fun await() {
            synchronized(lock) {
                val now = System.nanoTime()
                val idle = now - lastAsked > FOLLOW_NANOS
                lastAsked = now
                if (idle) lock.notifyAll()
                // A blank not yet used, and recent: this frame's.
                if (blanks > taken && now - blankAt < periodNanos / 2) {
                    taken = blanks
                    return
                }
                val seen = blanks
                val deadline = now + periodNanos * 2
                while (blanks == seen) {
                    val left = (deadline - System.nanoTime()) / 1_000_000
                    if (left <= 0) return
                    lock.wait(left.coerceAtLeast(1))
                }
                taken = blanks
            }
        }

        private fun run() {
            while (true) {
                synchronized(lock) {
                    while (System.nanoTime() - lastAsked > FOLLOW_NANOS) lock.wait()
                }
                val out = output
                if (out == null || !out.waitForBlank()) Thread.sleep(periodNanos / 1_000_000 + 1)
                synchronized(lock) {
                    blanks++
                    blankAt = System.nanoTime()
                    lock.notifyAll()
                }
            }
        }

        private const val FOLLOW_NANOS = 250_000_000L
    }

    /** One monitor's output on its adapter, as the driver's vertical-blank call wants it. */
    private class Output(private val adapter: Int, private val source: Int) {

        fun waitForBlank(): Boolean {
            val wait = Memory(12).apply { clear(); setInt(0, adapter); setInt(8, source) }
            return Win.waitForVerticalBlank.invokeInt(arrayOf(wait)) == 0
        }

        fun close() {
            val close = Memory(4).apply { setInt(0, adapter) }
            runCatching { Win.closeAdapter.invokeInt(arrayOf(close)) }
        }

        companion object {
            /** [monitor] null: the primary one. */
            fun open(monitor: Pointer?): Output? {
                val dc: Pointer
                val owned: Boolean
                if (monitor == null) {
                    dc = Win.getDC.invokePointer(arrayOf<Any?>(null)) ?: return null
                    owned = false
                } else {
                    // MONITORINFOEXW: the device name follows the 40 bytes of MONITORINFO.
                    val info = Memory(104).apply { clear(); setInt(0, 104) }
                    if (Win.getMonitorInfo.invokeInt(arrayOf(monitor, info)) == 0) return null
                    val name = WString(info.getWideString(40))
                    dc = Win.createDC.invokePointer(arrayOf(name, name, null, null)) ?: return null
                    owned = true
                }
                try {
                    // D3DKMT_OPENADAPTERFROMHDC: hDc, hAdapter, AdapterLuid, VidPnSourceId.
                    val open = Memory(24).apply { clear(); setPointer(0, dc) }
                    if (Win.openAdapterFromHdc.invokeInt(arrayOf(open)) != 0) return null
                    return Output(open.getInt(8), open.getInt(20))
                } finally {
                    if (owned) Win.deleteDC.invokeInt(arrayOf(dc)) else Win.releaseDC.invokeInt(arrayOf(null, dc))
                }
            }
        }
    }

    private object Win {
        private val user32 = NativeLibrary.getInstance("user32")
        private val gdi32 = NativeLibrary.getInstance("gdi32")
        val monitorFromWindow = user32.getFunction("MonitorFromWindow")
        val getMonitorInfo = user32.getFunction("GetMonitorInfoW")
        val getDC = user32.getFunction("GetDC")
        val releaseDC = user32.getFunction("ReleaseDC")
        val createDC = gdi32.getFunction("CreateDCW")
        val deleteDC = gdi32.getFunction("DeleteDC")
        val openAdapterFromHdc = gdi32.getFunction("D3DKMTOpenAdapterFromHdc")
        val waitForVerticalBlank = gdi32.getFunction("D3DKMTWaitForVerticalBlankEvent")
        val closeAdapter = gdi32.getFunction("D3DKMTCloseAdapter")
    }

    private const val MONITOR_DEFAULTTONEAREST = 2
    private const val CHECK_MS = 2_000
}
