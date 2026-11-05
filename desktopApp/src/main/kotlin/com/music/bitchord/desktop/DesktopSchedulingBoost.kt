package com.music.bitchord.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference

/**
 * What the app asks of Windows' schedulers. It is meant to be light enough not to need more than
 * this: the GPU's queue while it can be seen, the class for audio on the sound's thread, and the
 * threads that matter kept out of power throttling. Each is best effort; what is missing is skipped.
 */
internal object DesktopSchedulingBoost {

    private interface Kernel32 : Library {
        fun GetCurrentProcess(): Pointer
        fun GetCurrentThread(): Pointer
        fun SetProcessInformation(process: Pointer, infoClass: Int, info: Pointer, size: Int): Boolean
        fun SetThreadInformation(thread: Pointer, infoClass: Int, info: Pointer, size: Int): Boolean
    }

    private val kernel: Kernel32? by lazy {
        if (!DesktopPlatform.isWindows) null else runCatching { Native.load("kernel32", Kernel32::class.java) }.getOrNull()
    }

    /** PROCESS_/THREAD_POWER_THROTTLING_STATE: version 1, these mechanisms controlled, all off. */
    private fun throttlingOff(controlMask: Int): Pointer = com.sun.jna.Memory(12).apply {
        setInt(0, 1)
        setInt(4, controlMask)
        setInt(8, 0)
    }

    private const val PROCESS_POWER_THROTTLING = 4
    private const val THREAD_POWER_THROTTLING = 3
    private const val THROTTLE_EXECUTION_SPEED = 0x1
    private const val THROTTLE_IGNORE_TIMER_RESOLUTION = 0x4

    /**
     * Out of Windows' power throttling (EcoQoS). A window that has sat still for a while looks
     * like background work to Windows, which then runs its threads on the efficiency cores at low
     * clocks: the first frames of an animation after a pause — the player opening, the home card
     * morphing — came out late while the threads were moved back. Asked out of it, the app's
     * threads stay where frames are made on time; when nothing moves they sleep all the same.
     */
    private fun noPowerThrottling() {
        val kernel = kernel ?: return
        runCatching {
            val ok = kernel.SetProcessInformation(
                kernel.GetCurrentProcess(), PROCESS_POWER_THROTTLING,
                throttlingOff(THROTTLE_EXECUTION_SPEED or THROTTLE_IGNORE_TIMER_RESOLUTION), 12,
            )
            DesktopTrackLog.log("scheduling: power throttling ${if (ok) "off" else "unchanged"}")
        }.onFailure { DesktopTrackLog.log("scheduling: power throttling unavailable: ${it.message}") }
    }

    /**
     * The whole process out of power throttling, once at start. Without it, a player on a second
     * screen beside a game in front was background work to Windows: its threads moved to slow
     * cores and its timers coarsened to the system tick, and it stuttered like a minimised window
     * while in full view. No priority is raised: it is only kept from being slowed down.
     */
    fun keepOutOfThrottling() {
        if (DesktopPlatform.isWindows) noPowerThrottling()
    }

    /** The same, for the calling thread alone. */
    private fun noThreadPowerThrottling() {
        val kernel = kernel ?: return
        runCatching {
            kernel.SetThreadInformation(kernel.GetCurrentThread(), THREAD_POWER_THROTTLING, throttlingOff(THROTTLE_EXECUTION_SPEED), 12)
        }
    }

    private interface Gdi32 : Library {
        /** NTSTATUS; 0 is success. */
        fun D3DKMTSetProcessSchedulingPriorityClass(process: Pointer, priority: Int): Int
    }

    private interface Avrt : Library {
        fun AvSetMmThreadCharacteristicsW(task: WString, taskIndex: IntByReference): Pointer?
    }

    private const val GPU_PRIORITY_NORMAL = 2
    private const val GPU_PRIORITY_ABOVE_NORMAL = 3
    private const val GPU_PRIORITY_HIGH = 4

    @Volatile
    private var gpuRaised = false

    /**
     * Ahead of a game on the GPU while the window can be seen, as on a second screen beside it: its
     * frames are small and due every few milliseconds, and behind a game's large ones in the queue
     * they came late, which is the disc stuttering. Out of sight it draws nothing and asks for
     * nothing. The processor's priority stays an ordinary program's throughout.
     */
    fun setSeen(seen: Boolean) {
        if (!DesktopPlatform.isWindows || seen == gpuRaised) return
        gpuRaised = seen
        if (System.getenv("BITCHORD_GPU_ARM") == "normal") return
        runCatching {
            val kernel = Native.load("kernel32", Kernel32::class.java)
            val gdi = Native.load("gdi32", Gdi32::class.java)
            val process = kernel.GetCurrentProcess()
            val outcome = if (!seen) {
                gdi.D3DKMTSetProcessSchedulingPriorityClass(process, GPU_PRIORITY_NORMAL)
                "normal"
            } else if (gdi.D3DKMTSetProcessSchedulingPriorityClass(process, GPU_PRIORITY_HIGH) == 0) {
                "high"
            } else if (gdi.D3DKMTSetProcessSchedulingPriorityClass(process, GPU_PRIORITY_ABOVE_NORMAL) == 0) {
                "above normal"
            } else {
                "unchanged"
            }
            DesktopTrackLog.log("scheduling: GPU priority $outcome")
        }.onFailure { DesktopTrackLog.log("scheduling: GPU priority unavailable: ${it.message}") }
    }

    /**
     * The sound's own thread, in the class Windows keeps for audio, so a game holding every core
     * does not make it late with the device's next block. Out of power throttling as well: on a
     * chip with efficiency cores it would otherwise be moved to them behind a game.
     */
    fun boostAudioThread() {
        if (!DesktopPlatform.isWindows) return
        noThreadPowerThrottling()
        runCatching {
            val avrt = com.sun.jna.NativeLibrary.getInstance("avrt")
            val task = com.sun.jna.ptr.IntByReference(0)
            val handle = avrt.getFunction("AvSetMmThreadCharacteristicsW")
                .invokePointer(arrayOf(WString("Audio"), task))
            DesktopTrackLog.log("scheduling: audio thread ${if (handle != null) "registered for audio" else "not registered"}")
        }.onFailure { DesktopTrackLog.log("scheduling: audio thread not registered: ${it.message}") }
    }

    /**
     * Keeps the calling thread — one that draws — out of power throttling. Not registered with
     * MMCSS as a game's thread any more: that put the app's drawing ahead of a real game's at a
     * near-real-time priority, whichever window was in front.
     */
    fun boostRenderThread() {
        if (!DesktopPlatform.isWindows) return
        noThreadPowerThrottling()
    }

    private val boosted = ThreadLocal.withInitial { false }

    /**
     * Raises whichever thread is drawing, the first time it draws. Skiko draws and presents each
     * frame not on the UI thread but on a pooled one ("skiko-dispatcher-to-block-on") at ordinary
     * priority — and the pool lets a thread go after a minute without work, so after a pause the
     * next frame was drawn by a fresh, unraised thread. Checked on every frame, so a replacement
     * thread is raised by its very first.
     */
    fun boostDrawingThread() {
        if (boosted.get()) return
        boosted.set(true)
        val thread = Thread.currentThread()
        boostRenderThread()
        DesktopTrackLog.log("scheduling: drawing thread '${thread.name}' raised")
    }

    /** Skiko's pool for presenting frames ("skiko-dispatcher-to-block-on"); null if it moved. */
    fun skikoPresentDispatcher(): kotlinx.coroutines.CoroutineDispatcher? = runCatching {
        Class.forName("org.jetbrains.skiko.redrawer.DispatcherToBlockOnKt")
            .getMethod("getDispatcherToBlockOn")
            .invoke(null) as kotlinx.coroutines.CoroutineDispatcher
    }.getOrNull()
}
