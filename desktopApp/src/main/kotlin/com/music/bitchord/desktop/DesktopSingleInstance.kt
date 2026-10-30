package com.music.bitchord.desktop

import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path

/**
 * One BitChord per profile. Closed to the tray the app is still running, and the shortcut used to
 * start a second one beside it — two players, two tray icons, two sets of settings writing over
 * each other. Now the second launch finds the first, asks it to show its window, and leaves.
 *
 * The first holds a lock on a file in its cache directory for as long as it runs (the system lets
 * go of it however the process ends, so a crash leaves nothing stale), and listens on a loopback
 * port it writes beside the lock.
 */
internal object DesktopSingleInstance {

    private val directory: Path by lazy { DesktopMediaCache.directory.parent }
    private val lockFile: Path get() = directory.resolve("instance.lock")
    private val portFile: Path get() = directory.resolve("instance.port")

    // Held for the life of the process: released, the next launch would take it.
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    /**
     * True when this process is the one to run. False when another already is — it has been asked
     * to come forward, and this one should exit.
     */
    fun claim(): Boolean {
        val acquired = runCatching {
            Files.createDirectories(directory)
            RandomAccessFile(lockFile.toFile(), "rw").channel.tryLock()
        }.getOrNull()
        if (acquired == null) {
            // The lock could not even be tried (a read-only profile, say): run rather than refuse.
            if (!Files.exists(lockFile)) return true
            if (signalRunning()) return false
            // Held, but nobody answering: a first instance still on its way up, or one that hangs.
            // Better a second window than no window at all.
            return true
        }
        lock = acquired
        listen()
        return true
    }

    private fun signalRunning(): Boolean = runCatching {
        val port = Files.readString(portFile).trim().toInt()
        // So the window the first instance raises is allowed to take the foreground from us.
        if (DesktopPlatform.isWindows) {
            runCatching {
                com.sun.jna.NativeLibrary.getInstance("user32").getFunction("AllowSetForegroundWindow")
                    .invokeInt(arrayOf(ASFW_ANY))
            }
        }
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = CONNECT_TIMEOUT_MS
            socket.getOutputStream().write("$SHOW\n".toByteArray())
            socket.getOutputStream().flush()
            socket.getInputStream().read() >= 0
        }
    }.getOrDefault(false)

    private fun listen() {
        val socket = runCatching { ServerSocket(0, 4, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        server = socket
        runCatching { Files.writeString(portFile, socket.localPort.toString()) }
        Thread({
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: continue
                runCatching {
                    client.use {
                        it.soTimeout = CONNECT_TIMEOUT_MS
                        val line = it.getInputStream().bufferedReader().readLine()
                        if (line == SHOW) {
                            DesktopTrackLog.log("another launch asked for the window; showing it")
                            DesktopWindowVisibility.show()
                        }
                        it.getOutputStream().write(1)
                    }
                }
            }
        }, "bitchord-single-instance").apply { isDaemon = true }.start()
    }

    private const val SHOW = "show"
    private const val CONNECT_TIMEOUT_MS = 2_000
    private const val ASFW_ANY = -1
}
