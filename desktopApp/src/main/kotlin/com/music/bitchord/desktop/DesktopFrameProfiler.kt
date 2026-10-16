package com.music.bitchord.desktop

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Frame pacing, measured inside the app: with `BITCHORD_PROFILE=1` in the environment the app
 * walks itself through scrolling Home and opening the player and logs, for each stretch, how
 * evenly the frames came — the average and the worst gaps between them, and how many missed the
 * display's refresh. Off otherwise, and then it costs nothing.
 */
internal object DesktopFrameProfiler {

    val enabled: Boolean = System.getenv("BITCHORD_PROFILE") == "1"

    /** Layers switched off for a measuring run, from BITCHORD_PROFILE_OFF — empty otherwise. */
    val off: Set<String> = if (!enabled) emptySet() else
        System.getenv("BITCHORD_PROFILE_OFF").orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).toSet()

    fun on(layer: String): Boolean = layer !in off

    /** Whether the drawing thread is sampled natively too — see [DesktopNativeSampler]. */
    private val NATIVE = enabled && System.getenv("BITCHORD_PROFILE_NATIVE") == "1"

    private val presentTimes = java.util.concurrent.ConcurrentLinkedQueue<Long>()

    /**
     * Times Skiko's other half of each frame — drawing the recorded frame on the GPU and
     * presenting it, which it does on a pool of its own — by swapping that pool for one that
     * clocks every task. Measuring runs only.
     */
    fun instrumentPresent() {
        if (!enabled) return
        runCatching {
            val holder = Class.forName("org.jetbrains.skiko.redrawer.DispatcherToBlockOnKt")
            val field = holder.getDeclaredField("dispatcherToBlockOn")
            val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            val unsafe = unsafeField.get(null) as sun.misc.Unsafe
            @Suppress("DEPRECATION") val base = unsafe.staticFieldBase(field)
            @Suppress("DEPRECATION") val offset = unsafe.staticFieldOffset(field)
            val started = ThreadLocal<Long>()
            val pool = object : java.util.concurrent.ThreadPoolExecutor(
                0, Int.MAX_VALUE, 60L, java.util.concurrent.TimeUnit.SECONDS,
                java.util.concurrent.SynchronousQueue(),
                { r -> Thread(r, "skiko-dispatcher-to-block-on").apply { isDaemon = true } },
            ) {
                override fun beforeExecute(t: Thread?, r: Runnable?) {
                    DesktopSchedulingBoost.boostDrawingThread()
                    if (NATIVE) DesktopNativeSampler.noteRenderThread()
                    started.set(System.nanoTime())
                }
                override fun afterExecute(r: Runnable?, t: Throwable?) {
                    started.get()?.let {
                        val now = System.nanoTime()
                        val d = now - it
                        presentTimes += d
                        lastPresent = d
                        if (NATIVE) DesktopNativeSampler.noteTask(it, now)
                    }
                }
            }
            unsafe.putObject(base, offset, pool.asCoroutineDispatcher())
            DesktopTrackLog.log("profile present timing on")
        }.onFailure { DesktopTrackLog.log("profile present timing unavailable: ${it.message}") }
    }

    /** Logs [what] with the time on the same clock as the stalls. */
    fun mark(what: String) {
        DesktopTrackLog.log("profile mark %.3fs %s".format(java.lang.management.ManagementFactory.getRuntimeMXBean().uptime / 1000.0, what))
    }

    /** When the current frame's callbacks started, and the draw passes measured since. */
    @Volatile private var frameStart = 0L
    @Volatile private var lastCpu = 0L
    @Volatile private var lastPresent = 0L
    private val drawTimes = java.util.concurrent.ConcurrentLinkedQueue<Long>()
    private val cpuTimes = java.util.concurrent.ConcurrentLinkedQueue<Long>()

    /** Times the whole window's draw pass, and the frame's work up to its end. */
    fun Modifier.profiledDraw(): Modifier = if (!enabled) this else drawWithContent {
        val start = System.nanoTime()
        drawContent()
        val end = System.nanoTime()
        drawTimes += end - start
        if (frameStart != 0L && end - frameStart < 1_000_000_000L) { cpuTimes += end - frameStart; lastCpu = end - frameStart }
    }

    /** How many frames the window draws on its own over [millis], with nothing forcing them. */
    suspend fun countIdleFrames(label: String, millis: Long) {
        drawTimes.clear()
        // What changes meanwhile: whatever keeps the window redrawing changes on every frame.
        val changes = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val handle = androidx.compose.runtime.snapshots.Snapshot.registerApplyObserver { changed, _ ->
            changed.forEach { state ->
                val name = state.javaClass.name.substringAfterLast('.') + "@" + System.identityHashCode(state)
                changes.merge(name, 1, Int::plus)
            }
        }
        kotlinx.coroutines.delay(millis)
        handle.dispose()
        DesktopTrackLog.log("profile %-14s idle frames=%d in %dms".format(label, drawTimes.size, millis))
        changes.entries.sortedByDescending { it.value }.take(8).forEach { (name, count) ->
            DesktopTrackLog.log("profile   changed x$count  $name")
        }
    }

    /** Runs [action] while timing every frame drawn meanwhile, and logs what came out as [label]. */
    suspend fun measure(label: String, refreshHz: Int, action: suspend () -> Unit) {
        val gaps = ArrayList<Long>(4096)
        drawTimes.clear()
        cpuTimes.clear()
        presentTimes.clear()
        val stalls = ArrayList<String>()
        val budgetNanos = 1_000_000_000L / refreshHz.coerceAtLeast(30)
        val uptime = java.lang.management.ManagementFactory.getRuntimeMXBean()
        coroutineScope {
            val timer = launch {
                var last = withFrameNanos { it }
                while (true) {
                    withFrameNanos { now ->
                        frameStart = System.nanoTime()
                        val gap = now - last
                        gaps += gap
                        if (gap > budgetNanos * 3 / 2 && stalls.size < 40) {
                            stalls += "%.3fs:%.1fms(cpu %.1f, gpu %.1f)".format(uptime.uptime / 1000.0, gap / 1_000_000.0, lastCpu / 1e6, lastPresent / 1e6)
                        }
                        last = now
                    }
                }
            }
            if (NATIVE) DesktopNativeSampler.around(label) { action() } else action()
            timer.cancel()
        }
        if (gaps.isEmpty()) return
        val sorted = gaps.sorted()
        fun pct(p: Double) = sorted[((sorted.size - 1) * p).toInt()] / 1_000_000.0
        val budget = 1_000_000_000.0 / refreshHz.coerceAtLeast(30)
        val missed = gaps.count { it > budget * 1.5 }
        val seconds = gaps.sum() / 1_000_000_000.0
        DesktopTrackLog.log(
            "profile %-14s frames=%d fps=%.0f avg=%.2fms p50=%.2f p95=%.2f p99=%.2f max=%.2f missed=%d (budget %.2fms)".format(
                label, gaps.size, gaps.size / seconds, gaps.average() / 1_000_000.0,
                pct(0.50), pct(0.95), pct(0.99), sorted.last() / 1_000_000.0, missed, budget / 1_000_000.0,
            ),
        )
        fun summary(values: Collection<Long>): String {
            if (values.isEmpty()) return "-"
            val v = values.sorted()
            return "avg=%.2f p95=%.2f max=%.2f".format(v.average() / 1e6, v[((v.size - 1) * 0.95).toInt()] / 1e6, v.last() / 1e6)
        }
        DesktopTrackLog.log("profile %-14s draw %s | frame-cpu %s | gpu+present %s".format(label, summary(drawTimes.toList()), summary(cpuTimes.toList()), summary(presentTimes.toList())))
        if (stalls.isNotEmpty()) DesktopTrackLog.log("profile $label stalls " + stalls.joinToString(" "))
    }
}
