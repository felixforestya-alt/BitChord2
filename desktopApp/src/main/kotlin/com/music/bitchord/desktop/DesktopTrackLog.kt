package com.music.bitchord.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The few lines a resolve has to say about itself. */
internal object DesktopTrackLog {

    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** `$XDG_STATE_HOME/bitchord/desktop.log`, or the usual default under $HOME. */
    private val file: Path? by lazy {
        runCatching {
            val state = System.getenv("XDG_STATE_HOME")?.takeIf { it.isNotBlank() }
                ?: "${System.getProperty("user.home")}/.local/state"
            val directory = Path.of(state, "bitchord")
            Files.createDirectories(directory)
            directory.resolve("desktop.log")
        }.getOrNull()
    }

    /** The folder the log is in, for showing it to someone. */
    val folder: Path? get() = file?.parent

    private val waiting = java.util.concurrent.LinkedBlockingQueue<String>()

    /**
     * Writes on a thread of its own. A line used to open and close the file on whichever thread
     * logged it, which is 300 microseconds on the audio thread or the UI thread; and the file grew
     * for as long as the app was used.
     */
    private val writer = Thread({
        val batch = ArrayList<String>()
        while (true) {
            batch += waiting.take()
            waiting.drainTo(batch)
            write(batch)
            batch.clear()
        }
    }, "bitchord-log").apply {
        isDaemon = true
        priority = Thread.MIN_PRIORITY
        // Only an orderly exit gets here, so a log that stops without this line was killed or crashed.
        Runtime.getRuntime().addShutdownHook(Thread({
            log("exiting in an orderly way")
            drain()
        }, "bitchord-log-exit"))
        start()
    }

    fun log(line: String) {
        waiting.offer("${LocalTime.now().format(clock)}  $line")
    }

    private fun drain() {
        val rest = ArrayList<String>()
        waiting.drainTo(rest)
        if (rest.isNotEmpty()) write(rest)
    }

    private fun write(lines: List<String>) {
        lines.forEach { println("BitChord: $it") }
        val target = file ?: return
        runCatching {
            // One older file kept beside it, so a report still has the run before this one.
            if (Files.exists(target) && Files.size(target) > MAX_BYTES) {
                Files.move(target, target.resolveSibling("desktop.1.log"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            Files.write(target, lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }
    }

    private const val MAX_BYTES = 4L * 1024 * 1024
}
