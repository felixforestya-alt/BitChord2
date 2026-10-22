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
            while (true) {
                runCatching { Thread.sleep(PING_MS) }.onFailure { return@Thread }
                SwingUtilities.invokeLater { answeredAt = System.nanoTime() }
                val silentMs = (System.nanoTime() - answeredAt) / 1_000_000
                if (silentMs >= STALL_MS && !reported) {
                    reported = true
                    DesktopTrackLog.log("hang: the UI thread has not answered for ${silentMs}ms")
                    dumpThreads()
                } else if (silentMs < STALL_MS && reported) {
                    reported = false
                    DesktopTrackLog.log("hang: the UI thread answers again")
                }
            }
        }, "bitchord-hang-watch").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun dumpThreads() {
        val interesting = listOf("AWT-EventQueue", "skiko", "BitChord", "main", "JavaFX")
        Thread.getAllStackTraces()
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
                    report.delete()
                }
            }
    }

    private const val PING_MS = 1_000L
    private const val STALL_MS = 5_000L
    private const val STACK_DEPTH = 25
    private const val CRASH_LINES = 90
}
