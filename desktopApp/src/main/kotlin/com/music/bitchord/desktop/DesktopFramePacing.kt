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
 * Frames started in step with the monitor's refresh — rather than held to Direct3D's vsync.
 *
 * Skia presents each frame with a sync interval of one and waits for its buffer back. Alone on
 * the GPU that holds the screen's rate; with a game running — in front, behind, or minimised —
 * the buffer came back a refresh late every time, so on a 160 Hz screen the player ran at 78
 * frames a second while drawing one took four milliseconds of the six it had. Measured with
 * Deadlock open: 78 fps with Skia's vsync; 233–258 drawn with none.
 *
 * So Skia presents without waiting, and each frame waits at its start, on a high-resolution
 * waitable timer, for the monitor's next refresh: when the last vertical blank seen fell, plus
 * as many refresh periods as reach past the frame before. A frame started there is on screen at
 * the refresh after, every time, so the motion is as even as the display; a timer at the right
 * rate but not in step keeps the count and not the phase — frames drifting across the refreshes
 * land two in one and none in the next, which looks like a lower rate than it is.
 *
 * The blanks are followed on a thread of their own, only for their phase: it sees one in two of
 * them (the driver's wait comes back too late for the next), which is phase enough, and frames
 * waiting on the blanks themselves came in step only half the time. Never sooner than three
 * quarters of a refresh after the last frame, so two never share one, and never later than a
 * refresh after it: under G-SYNC the monitor refreshes when a game presents, and its blanks in a
 * match came 127 a second with gaps of 20 ms.
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
        if (Win.timer == null) return
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
        DesktopTrackLog.log("frames: started in step with the monitor's refresh")
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
        /** The monitor's refresh period. */
        private var refresh = 6_250_000L
        /** A frame's: the refresh, or two of them beside a busy GPU — see [GpuNeighbours]. */
        private var period = 6_250_000L
        private var periodCheckedAt = Long.MIN_VALUE / 2

        override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
            val start = System.nanoTime()
            if (start - periodCheckedAt > PERIOD_CHECK_NANOS) {
                refresh = periodNanos(window)
                periodCheckedAt = start
                Blanks.follow(window)
            }
            GpuNeighbours.wanted()
            period = refresh * (FORCED_DIVISOR ?: if (GpuNeighbours.busy) 2L else 1L)
            Blanks.wanted(refresh)
            last = waitForTurn(start)
            inner.onRender(canvas, width, height, last)
        }

        /** Until the monitor's next refresh after the last frame; returns the time it is. */
        private fun waitForTurn(start: Long): Long {
            val blank = Blanks.phase
            val from = maxOf(start, last + period * 3 / 4)
            val next = if (blank != 0L && from - blank < PHASE_FRESH_NANOS) {
                // A frame's period after the last, put on the refresh nearest it: the blanks come
                // every refresh from one known to have been. Counted on from the last frame rather
                // than taken as the next slot of a frame's period from the blanks, so that drawing
                // every other refresh keeps to the same ones — the blanks' phase, estimated afresh,
                // could fall on the others, and a frame then came a refresh early or late: the
                // page stepped 2, 2, 3, 1, 2 refreshes' worth.
                val ideal = if (last != 0L && start - last < period * 2) last + period else from
                var slot = blank + Math.floorDiv(ideal - blank + refresh / 2, refresh) * refresh
                while (slot < from) slot += refresh
                slot
            } else {
                from
            }
            val due = minOf(next, maxOf(start, last + period + period / 16))
            // The wait is the frame's spare time: what the lists want built ahead is built in it,
            // up to a little before the refresh, and the rest of it slept.
            if (due - start > PREFETCH_MIN_NANOS) DesktopPrefetch.workUntil(due - PREFETCH_MARGIN_NANOS)
            val now = System.nanoTime()
            if (due - now > MIN_WAIT_NANOS) Win.sleepFor(due - now)
            val woke = System.nanoTime()
            // The frame is timed as of the refresh it was started on, not as of when the timer
            // got round to waking it, a fraction of a millisecond either way: everything that
            // moves steps by exactly one refresh's worth a frame, as the screen shows it.
            return if (due == next && woke - due in 0..period / 2) due else woke
        }
    }

    /**
     * Whether another program — a game, open or only minimised — is keeping the GPU busy.
     *
     * Then Windows shows a window's new frames on only about every other refresh, and at random
     * which: recorded off the screen beside Deadlock in its menu, frames drawn on every refresh
     * reached it one refresh apart and then two, the disc stepping 1, 2, 1, 1, 2 — a judder worse
     * to look at than a lower, even rate. Chromium is held to every other refresh there too, but
     * evenly, and scrolls smoothly. So beside a busy GPU the frames are drawn on every other
     * refresh, in step, and each lands: the disc steps 2, 2, 2 (97% of its steps, recorded),
     * as Chromium's does; with the GPU to itself again, every refresh.
     *
     * Read from Windows' own per-process GPU counters every two seconds, on a thread of its own,
     * only while frames are being drawn.
     */
    private object GpuNeighbours {
        @Volatile var busy = false
            private set
        @Volatile private var askedAt = 0L
        private val lock = Object()
        private val ownPid = ProcessHandle.current().pid()
        private var high = 0
        private var low = 0

        init {
            Thread(::run, "BitChord-GpuNeighbours").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
        }

        fun wanted() {
            val now = System.nanoTime()
            val idle = now - askedAt > IDLE_NANOS
            askedAt = now
            if (idle) synchronized(lock) { lock.notifyAll() }
        }

        private fun run() {
            val pdh = runCatching { Pdh.open() }.getOrNull() ?: return
            while (true) {
                synchronized(lock) {
                    while (System.nanoTime() - askedAt > IDLE_NANOS) {
                        high = 0
                        lock.wait()
                    }
                }
                Thread.sleep(SAMPLE_MS)
                val others = runCatching { pdh.busiestOther(ownPid) }.getOrNull() ?: continue
                if (others >= BUSY_PERCENT) {
                    high++
                    low = 0
                } else if (others < CALM_PERCENT) {
                    low++
                    high = 0
                }
                val next = if (busy) low < CALM_SAMPLES else high >= BUSY_SAMPLES
                if (next != busy) {
                    busy = next
                    DesktopTrackLog.log(
                        if (next) "frames: another program keeps the GPU busy (%.0f%%); every other refresh, evenly".format(others)
                        else "frames: the GPU to ourselves again; every refresh",
                    )
                }
            }
        }

        private const val SAMPLE_MS = 2_000L
        private const val IDLE_NANOS = 3_000_000_000L
        private const val BUSY_PERCENT = 10.0
        private const val CALM_PERCENT = 5.0
        private const val BUSY_SAMPLES = 1
        private const val CALM_SAMPLES = 2
    }

    /** Windows' performance counters for the GPU's 3D engines, per process. */
    private class Pdh private constructor(private val query: Pointer, private val counter: Pointer) {

        private val dwm: Set<Long> = ProcessHandle.allProcesses()
            .filter { it.info().command().orElse("").endsWith("dwm.exe", ignoreCase = true) }
            .map { it.pid() }.toList().toSet()

        /** The 3D engine use, in percent, of the busiest process other than [own] and the desktop. */
        fun busiestOther(own: Long): Double {
            if (collect.invokeInt(arrayOf(query)) != 0) return 0.0
            val size = com.sun.jna.ptr.IntByReference(0)
            val count = com.sun.jna.ptr.IntByReference(0)
            getArray.invokeInt(arrayOf(counter, PDH_FMT_DOUBLE, size, count, null))
            if (size.value <= 0) return 0.0
            val buffer = Memory(size.value.toLong())
            if (getArray.invokeInt(arrayOf(counter, PDH_FMT_DOUBLE, size, count, buffer)) != 0) return 0.0
            val perProcess = HashMap<Long, Double>()
            for (i in 0 until count.value) {
                // PDH_FMT_COUNTERVALUE_ITEM_W: the name, then status, padding and the double.
                val at = i * ITEM_SIZE
                val name = buffer.getPointer(at)?.getWideString(0) ?: continue
                if (buffer.getInt(at + Native.POINTER_SIZE) != 0) continue
                val value = buffer.getDouble(at + Native.POINTER_SIZE + 8L)
                val pid = PID.find(name)?.groupValues?.get(1)?.toLongOrNull() ?: continue
                if (pid == own || pid in dwm) continue
                perProcess.merge(pid, value, Double::plus)
            }
            return perProcess.values.maxOrNull() ?: 0.0
        }

        companion object {
            private val pdh = NativeLibrary.getInstance("pdh")
            private val openQuery = pdh.getFunction("PdhOpenQueryW")
            private val addCounter = pdh.getFunction("PdhAddEnglishCounterW")
            private val collect = pdh.getFunction("PdhCollectQueryData")
            private val getArray = pdh.getFunction("PdhGetFormattedCounterArrayW")
            private val PID = Regex("""pid_(\d+)_""")
            private const val PDH_FMT_DOUBLE = 0x00000200
            private val ITEM_SIZE = Native.POINTER_SIZE + 16L

            fun open(): Pdh? {
                val q = com.sun.jna.ptr.PointerByReference()
                if (openQuery.invokeInt(arrayOf(null, Pointer.NULL, q)) != 0) return null
                val c = com.sun.jna.ptr.PointerByReference()
                val path = com.sun.jna.WString("\\GPU Engine(*engtype_3D)\\Utilization Percentage")
                if (addCounter.invokeInt(arrayOf(q.value, path, Pointer.NULL, c)) != 0) return null
                // A rate needs two readings: the first one now.
                collect.invokeInt(arrayOf(q.value))
                return Pdh(q.value, c.value)
            }
        }
    }

    /**
     * The blanks, followed on a thread of their own: the driver's call has no timeout, and a
     * monitor switched off could hold it indefinitely, where the frame waits only on its timer.
     * The thread follows while frames are coming, and a quarter of a second after the last it
     * sleeps.
     */
    private object Blanks {
        /**
         * When a blank fell — to within the thread's waking, at best a fraction of a millisecond.
         * The thread wakes late by anything up to two: each blank is taken as when it was seen,
         * less as far into the refresh as the earliest of the last few was seen. Lateness only
         * ever adds, so the earliest is the nearest to true.
         */
        @Volatile var phase = 0L
            private set
        @Volatile private var period = 6_250_000L
        private var anchor = 0L
        private val offsets = LongArray(PHASE_SAMPLES)
        private var seen = 0
        private val lock = Object()
        @Volatile private var askedAt = 0L
        @Volatile private var output: Output? = null
        private var monitor: Pointer? = null

        init {
            Thread(::run, "BitChord-VBlank").apply { isDaemon = true; start() }
        }

        fun wanted(period: Long) {
            this.period = period
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
            val attempt = runCatching { Output.open(now) }
            val opened = attempt.getOrNull()
            if (opened == null) {
                DesktopTrackLog.log("frames: no vertical blank for this monitor (${attempt.exceptionOrNull()?.message ?: "the driver said no"}); the timer alone paces them")
                return
            }
            DesktopTrackLog.log("frames: following this monitor's vertical blank")
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
                note(System.nanoTime())
                count++
            }
        }

        private fun note(at: Long) {
            val p = period
            // The anchor half a refresh ahead of the first blank seen, so lateness never carries
            // an offset round past the end of the refresh; set again should it drift toward one.
            if (anchor == 0L) anchor = at - p / 2
            offsets[seen % PHASE_SAMPLES] = Math.floorMod(at - anchor, p)
            seen++
            var earliest = Long.MAX_VALUE
            for (i in 0 until minOf(seen, PHASE_SAMPLES)) earliest = minOf(earliest, offsets[i])
            val blank = at - Math.floorMod(at - anchor - earliest, p)
            phase = blank
            if (earliest < p / 4 || earliest > p * 3 / 4) {
                anchor = blank - p / 2
                seen = 0
            }
        }

        /** Blanks seen so far, for measuring. */
        @Volatile var count = 0L
            private set

        private const val FOLLOW_NANOS = 250_000_000L
        private const val PHASE_SAMPLES = 24
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
        private val waitForSingle = kernel32.getFunction("WaitForSingleObject")

        /** High resolution: waits to well under a millisecond without spinning. Used by the UI thread alone. */
        val timer: Pointer? = runCatching {
            kernel32.getFunction("CreateWaitableTimerExW")
                .invokePointer(arrayOf<Any?>(null, null, CREATE_WAITABLE_TIMER_HIGH_RESOLUTION, TIMER_ALL_ACCESS))
        }.getOrNull()

        private val due = Memory(8)

        fun sleepFor(nanos: Long) {
            val timer = timer ?: return
            // Relative times are negative, in units of 100 ns.
            due.setLong(0, -(nanos.coerceIn(1_000, 20_000_000) / 100))
            if (setWaitableTimer.invokeInt(arrayOf(timer, due, 0, null, null, 0)) == 0) return
            waitForSingle.invokeInt(arrayOf(timer, WAIT_LIMIT_MS))
        }
    }

    private const val MONITOR_DEFAULTTONEAREST = 2
    private const val CREATE_WAITABLE_TIMER_HIGH_RESOLUTION = 0x00000002
    private const val TIMER_ALL_ACCESS = 0x1F0003
    private const val WAIT_LIMIT_MS = 50
    private const val TIMING_INFO_SIZE = 292
    private const val PERIOD_CHECK_NANOS = 2_000_000_000L
    /** How long the last blank seen still gives the phase. */
    private const val PHASE_FRESH_NANOS = 200_000_000L
    /** Shorter than this, not worth a wait. */
    private const val MIN_WAIT_NANOS = 150_000L
    /** Spare time worth building in, and how long before the refresh building stops. */
    private const val PREFETCH_MIN_NANOS = 1_000_000L
    private const val PREFETCH_MARGIN_NANOS = 400_000L
    /** BITCHORD_PACE_DIVISOR, for measuring: a frame every so many refreshes, whatever else runs. */
    private val FORCED_DIVISOR = System.getenv("BITCHORD_PACE_DIVISOR")?.toLongOrNull()?.takeIf { it in 1..4 }
    private const val CHECK_MS = 2_000
}
