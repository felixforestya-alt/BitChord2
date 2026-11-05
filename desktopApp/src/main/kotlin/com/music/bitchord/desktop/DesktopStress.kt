package com.music.bitchord.desktop

import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Plays rough with the player and says what it left behind: BITCHORD_SELFTEST=1 with
 * BITCHORD_SELFTEST_STRESS=1, or =2 for the long run that also breaks the network. For limits and
 * leaks that only show after dozens of tracks, such as decoders nobody closes. Each line of the log
 * is one phase; after the rests, what is open should be back to what a player with two decks needs.
 */
internal object DesktopStress {

    class Controls(
        val start: () -> Unit,
        val skip: () -> Unit,
        val togglePlay: () -> Unit,
        val seekTo: (Long) -> Unit,
        val goTo: (Int) -> Unit,
        val pages: Int,
        val state: () -> DesktopPlaybackState,
    )

    private val worstUiMs = AtomicLong()
    private val worstBusy = AtomicLong()
    private val mostWindowed = AtomicLong()
    private val worstStuckMs = AtomicLong()
    private val songChanges = AtomicLong()

    /** Where a test proxy reads what to do to the connections: ok, drop, stall, flaky or slow. */
    private val netControl = System.getenv("BITCHORD_STRESS_NET")?.let(::File)

    suspend fun run(controls: Controls) = with(controls) {
        watch(this)
        report("idle")
        start()
        delay(20_000)
        report("playing")
        repeat(25) { skip(); delay(400) }
        report("skip every 0.4s")
        repeat(25) { skip(); delay(1_500) }
        report("skip every 1.5s")
        delay(15_000)
        report("rest")
        repeat(60) { seekTo(5_000L + it * 2_000L); delay(150) }
        report("seek every 0.15s")
        repeat(40) { togglePlay(); delay(150) }
        report("pause every 0.15s")
        visitPages(this)
        report("every page, three times")
        settle("rest")
        DesktopTrackLog.log("selftest done")
    }

    suspend fun runBrutal(controls: Controls) = with(controls) {
        watch(this)
        keepBehind()
        report("idle")
        start()
        delay(20_000)
        report("playing")

        repeat(200) { skip(); delay(100) }
        report("skip every 0.1s")
        delay(15_000)
        report("rest")

        repeat(600) {
            when (Random.nextInt(10)) {
                0, 1 -> skip()
                2, 3, 4 -> seekTo(Random.nextLong(0, 200_000))
                5, 6 -> togglePlay()
                else -> goTo(Random.nextInt(pages))
            }
            delay(100)
        }
        goTo(0)
        ensurePlaying(this)
        report("everything at once")
        delay(15_000)
        report("rest")

        repeat(15) { transition ->
            val before = state().song?.videoId
            seekNearEnd(this)
            delay(16_000)
            if (state().song?.videoId == before) DesktopTrackLog.log("stress transition ${transition + 1}: still on the same track")
        }
        report("15 automix transitions")

        network("drop", seconds = 30, this)
        network("stall", seconds = 30, this)
        network("flaky", seconds = 60, this)
        network("slow", seconds = 45, this)
        repeat(6) { skip(); delay(1_000) }
        netMode("drop")
        repeat(6) { skip(); delay(2_000) }
        netMode("ok")
        delay(30_000)
        report("skips with the network gone, then back")

        ensurePlaying(this)
        togglePlay()
        delay(120_000)
        report("paused two minutes")
        togglePlay()
        delay(15_000)
        report("resumed")

        visitPages(this)
        report("every page, three times")
        settle("rest")
        DesktopTrackLog.log("selftest done")
    }

    /** Putting the window away and bringing it back, as alt-tabbing out of a game does. */
    suspend fun runReturn(controls: Controls) = with(controls) {
        watch(this)
        start()
        delay(30_000)
        report("playing")
        val window = java.awt.Window.getWindows().filterIsInstance<java.awt.Frame>().first { it.isShowing }
        repeat(6) { round ->
            javax.swing.SwingUtilities.invokeLater { window.extendedState = java.awt.Frame.ICONIFIED }
            delay(20_000)
            val shown = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { measureReturn(window) }
            DesktopTrackLog.log("stress return ${round + 1}: $shown")
            delay(4_000)
        }
        report("after the returns")
        DesktopTrackLog.log("selftest done")
    }

    private fun measureReturn(window: java.awt.Frame): String {
        val before = DesktopPrefetch.framesDrawn
        val asked = System.nanoTime()
        worstUiMs.set(0)
        javax.swing.SwingUtilities.invokeLater { window.extendedState = java.awt.Frame.NORMAL }
        while (DesktopPrefetch.framesDrawn == before && System.nanoTime() - asked < 10_000_000_000L) Thread.sleep(1)
        val first = (System.nanoTime() - asked) / 1_000_000
        var last = System.nanoTime()
        var seen = DesktopPrefetch.framesDrawn
        var worst = 0L
        var late = 0
        val startedAt = last
        while (last - startedAt < 3_000_000_000L) {
            Thread.sleep(1)
            val now = System.nanoTime()
            val frames = DesktopPrefetch.framesDrawn
            if (frames != seen) {
                val gap = (now - last) / 1_000_000
                if (gap > worst) worst = gap
                if (gap > 25) late++
                seen = frames
                last = now
            } else if (now - startedAt >= 3_000_000_000L) {
                break
            }
        }
        return "first frame ${first}ms, then worst gap ${worst}ms, gaps over 25ms $late, frames ${seen - before}, worst UI ${worstUiMs.get()}ms"
    }

    /** Only the outages, and whether the player comes back from them by itself. */
    suspend fun runNetwork(controls: Controls) = with(controls) {
        watch(this)
        keepBehind()
        start()
        delay(25_000)
        report("playing")
        for (seconds in listOf(30, 120, 10)) {
            netMode("drop")
            delay(seconds * 1_000L)
            report("network drop for ${seconds}s")
            netMode("ok")
            val backAt = System.nanoTime()
            while (System.nanoTime() - backAt < 45_000_000_000L && !movingWithin(this, 3_000)) delay(500)
            DesktopTrackLog.log(
                "stress came back after ${(System.nanoTime() - backAt) / 1_000_000}ms: " +
                    "playing=${state().isPlaying} at ${state().positionMs}ms error=${state().error}",
            )
            delay(10_000)
            report("after a drop of ${seconds}s")
        }
        netMode("drop")
        delay(30_000)
        skip()
        delay(10_000)
        netMode("ok")
        delay(30_000)
        DesktopTrackLog.log("stress skipped while waiting: playing=${state().isPlaying} at ${state().positionMs}ms error=${state().error}")
        report("skip during a drop")
        DesktopTrackLog.log("selftest done")
    }

    private suspend fun movingWithin(controls: Controls, ms: Long): Boolean {
        val from = controls.state().positionMs
        delay(ms)
        return controls.state().isPlaying && controls.state().positionMs > from
    }

    private suspend fun network(mode: String, seconds: Int, controls: Controls) {
        ensurePlaying(controls)
        netMode(mode)
        delay(seconds * 1_000L)
        report("network $mode for ${seconds}s")
        netMode("ok")
        delay(30_000)
        report("network back after $mode")
    }

    private fun netMode(mode: String) {
        netControl?.writeText(mode)
        DesktopTrackLog.log("stress network: $mode")
    }

    private suspend fun ensurePlaying(controls: Controls) {
        if (!controls.state().isPlaying) controls.togglePlay()
        delay(2_000)
        if (controls.state().song == null || controls.state().error != null) {
            controls.skip()
            delay(5_000)
        }
    }

    private suspend fun seekNearEnd(controls: Controls) {
        repeat(10) {
            val length = controls.state().durationMs
            if (length > 30_000) {
                controls.seekTo(length - 14_000)
                return
            }
            delay(500)
        }
    }

    private suspend fun visitPages(controls: Controls) {
        repeat(3) { repeat(controls.pages) { page -> controls.goTo(page); delay(1_000) } }
        controls.goTo(0)
    }

    private suspend fun settle(phase: String) {
        delay(20_000)
        System.gc()
        delay(5_000)
        report(phase)
    }

    /** Out of the way of whatever the person at this computer is doing. */
    private fun keepBehind() {
        javax.swing.SwingUtilities.invokeLater { java.awt.Window.getWindows().forEach { it.toBack() } }
    }

    private fun report(phase: String) {
        val traces = Thread.getAllStackTraces()
        val threads = traces.keys.map { it.name }
        worstBusy.accumulateAndGet(busyWorkers(traces).toLong(), ::maxOf)
        traces.filterKeys { it.name.startsWith("BitChord-Decode") }.entries.drop(2).forEach { (thread, stack) ->
            val where = stack.firstOrNull { it.className.startsWith("com.music.bitchord") }
            DesktopTrackLog.log("stress extra decoder '${thread.name}' ${thread.state} at $where")
        }
        val heapMb = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used / MB
        DesktopTrackLog.log(
            "stress $phase: decoders=${threads.count { it.startsWith("BitChord-Decode") }} " +
                "windowed=${DesktopAudioDecoder.windowedOpen} windowedPeak=${mostWindowed.getAndSet(0)} threads=${threads.size} " +
                "workers=${threads.count { it.startsWith("DefaultDispatcher-worker") }} busyPeak=${worstBusy.getAndSet(0)} " +
                "okhttp=${threads.count { it.startsWith("OkHttp") }} " +
                "heap=${heapMb}MB " +
                "worstUi=${worstUiMs.getAndSet(0)}ms stuck=${worstStuckMs.getAndSet(0)}ms songs=${songChanges.getAndSet(0)}",
        )
    }

    // Parked workers are spare; the rest hold one of the IO dispatcher's 64 places.
    private fun busyWorkers(traces: Map<Thread, Array<StackTraceElement>>): Int =
        traces.count { (thread, stack) ->
            thread.name.startsWith("DefaultDispatcher-worker") && stack.none { it.methodName == "park" }
        }

    private fun watch(controls: Controls) {
        thread(isDaemon = true, name = "bitchord-stress-ui") {
            var lastSong: String? = null
            var lastPosition = -1L
            var movedAt = System.nanoTime()
            while (true) {
                val asked = System.nanoTime()
                val answered = java.util.concurrent.CountDownLatch(1)
                javax.swing.SwingUtilities.invokeLater { answered.countDown() }
                answered.await()
                worstUiMs.accumulateAndGet((System.nanoTime() - asked) / 1_000_000, ::maxOf)
                worstBusy.accumulateAndGet(busyWorkers(Thread.getAllStackTraces()).toLong(), ::maxOf)
                mostWindowed.accumulateAndGet(DesktopAudioDecoder.windowedOpen.toLong(), ::maxOf)

                val state = controls.state()
                if (state.song?.videoId != lastSong) {
                    lastSong = state.song?.videoId
                    songChanges.incrementAndGet()
                }
                // Says it is playing and the clock does not move: what a listener hears as a hang.
                if (!state.isPlaying || state.positionMs != lastPosition) movedAt = System.nanoTime()
                lastPosition = state.positionMs
                worstStuckMs.accumulateAndGet((System.nanoTime() - movedAt) / 1_000_000, ::maxOf)
                Thread.sleep(50)
            }
        }
    }

    private const val MB = 1024L * 1024L
}
