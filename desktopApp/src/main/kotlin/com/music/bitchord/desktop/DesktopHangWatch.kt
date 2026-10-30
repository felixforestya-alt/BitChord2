package com.music.bitchord.desktop

import java.io.File
import javax.swing.SwingUtilities

/**
 * What a frozen window or a vanished process was doing, written where a listener can send it.
 *
 * A window that stopped answering after an hour behind a game, and a process that closed itself
 * mid-transition, both left the log with nothing: the first only had to be killed from Task
 * Manager, the second took its crash report to whatever folder it was started from. Here the UI
 * thread is pinged every second and, once it has not answered for [STALL_MS], the threads that
 * draw and play are written to the log; and a JVM crash report from the last run (the launcher
 * points `-XX:ErrorFile` next to the app) is copied into the log at the next start.
 */
internal object DesktopHangWatch {

    @Volatile private var answeredAt = System.nanoTime()

    fun start() {
        adoptCrashReports()
        Thread({
            var reported = false
            var stalledSince = 0L
            var cause = ""
            var tickedAt = System.nanoTime()
            while (true) {
                runCatching { Thread.sleep(PING_MS) }.onFailure { return@Thread }
                // This thread itself did not run on time: the computer slept, which the clock
                // counts. Ten hours of sleep is not a ten-hour hang.
                val now = System.nanoTime()
                if ((now - tickedAt) / 1_000_000 > PING_MS * MISSED_TICKS) answeredAt = now
                tickedAt = now
                SwingUtilities.invokeLater { answeredAt = System.nanoTime() }
                val silentMs = (System.nanoTime() - answeredAt) / 1_000_000
                if (silentMs >= STALL_MS && !reported) {
                    reported = true
                    stalledSince = answeredAt
                    DesktopTrackLog.log("hang: the UI thread has not answered for ${silentMs}ms")
                    val traces = Thread.getAllStackTraces()
                    dumpThreads(traces)
                    cause = causeOf(traces)
                    // The window cannot say so itself. Not before there is a window, and not when
                    // nobody is looking at it.
                    if (pastStartup() && !com.music.bitchord.ui.AppActivity.quiet) {
                        DesktopAwtTray.notify(
                            DesktopStrings["d_alert_hang_now", "BitChord is not responding"],
                            cause,
                        )
                    }
                } else if (silentMs < STALL_MS && reported) {
                    reported = false
                    DesktopTrackLog.log("hang: the UI thread answers again")
                    if (pastStartup()) {
                        DesktopAlerts.raise(
                            kind = "hang",
                            title = DesktopStrings["d_alert_hang_over", "The window froze for %s s"]
                                .format((now - stalledSince) / 1_000_000_000),
                            detail = cause,
                        )
                    }
                }
            }
        }, "bitchord-hang-watch").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /** The first seconds load the app's classes on this thread, behind a window not yet shown. */
    private fun pastStartup(): Boolean =
        java.lang.management.ManagementFactory.getRuntimeMXBean().uptime > STARTUP_MS

    /** What the UI thread is stuck in: in plain words where that is known, and always by name. */
    private fun causeOf(traces: Map<Thread, Array<StackTraceElement>>): String {
        val stack = traces.entries.firstOrNull { it.key.name.startsWith("AWT-EventQueue") }?.value
            ?: return DesktopStrings["d_alert_cause_unknown", "cause unknown"]
        val frames = stack.map { "${it.className}.${it.methodName}" }
        val plain = when {
            frames.any { "DesktopWindowsAudio" in it } -> DesktopStrings["d_alert_cause_audio", "waiting for Windows' audio system"]
            frames.any { "GraphicsDevice" in it || "DesktopFramePacing" in it } -> DesktopStrings["d_alert_cause_monitor", "waiting for the monitor"]
            frames.any { "Redrawer" in it || "drawAndSwap" in it } -> DesktopStrings["d_alert_cause_gpu", "waiting for the graphics card"]
            frames.any { "okhttp3" in it || "java.net." in it } -> DesktopStrings["d_alert_cause_network", "waiting for the network"]
            else -> null
        }
        val top = stack.firstOrNull { it.className.startsWith("com.music.bitchord") } ?: stack.firstOrNull()
        val where = top?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }.orEmpty()
        return listOfNotNull(plain, where.ifBlank { null }).joinToString(" · ")
    }

    private fun dumpThreads(traces: Map<Thread, Array<StackTraceElement>>) {
        val interesting = listOf("AWT-EventQueue", "skiko", "BitChord", "main", "JavaFX")
        traces
            .filterKeys { thread -> interesting.any { thread.name.startsWith(it) } }
            .forEach { (thread, stack) ->
                DesktopTrackLog.log("hang: thread '${thread.name}' ${thread.state}")
                stack.take(STACK_DEPTH).forEach { DesktopTrackLog.log("hang:     at $it") }
            }
    }

    /** Crash reports the JVM left beside the app, moved into the log. */
    private fun adoptCrashReports() {
        val dir = System.getProperty("jpackage.app-path")?.let { File(it).parentFile?.resolve("app") } ?: return
        dir.listFiles { file -> file.name.startsWith("hs_err_") && file.name.endsWith(".log") }
            ?.forEach { report ->
                runCatching {
                    val lines = report.readLines()
                    DesktopTrackLog.log("crash: the last run died in native code — ${report.name}:")
                    // The head names the frame that failed; the rest is the whole process's memory.
                    lines.take(CRASH_LINES).forEach { DesktopTrackLog.log("crash:   $it") }
                    val frame = lines.indexOfFirst { "Problematic frame" in it }
                    DesktopAlerts.raise(
                        kind = "crash",
                        title = DesktopStrings["d_alert_crash", "BitChord crashed last time. The report is in the log"],
                        detail = lines.getOrNull(frame + 1)?.removePrefix("#")?.trim(),
                    )
                    report.delete()
                }
            }
    }

    private const val PING_MS = 1_000L
    private const val STALL_MS = 5_000L
    private const val MISSED_TICKS = 3
    private const val STARTUP_MS = 30_000L
    private const val STACK_DEPTH = 25
    private const val CRASH_LINES = 90
}
