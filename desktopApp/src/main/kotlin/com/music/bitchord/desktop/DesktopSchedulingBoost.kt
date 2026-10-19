package com.music.bitchord.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference

/**
 * Asks Windows to schedule the app the way it does a game or a media player, so frames come out
 * evenly even with the rest of the machine busy: the process a step above ordinary programs, and
 * the thread that draws each frame registered with the Multimedia Class Scheduler (MMCSS) under
 * its "Games" task — which keeps that thread from being pushed off the CPU part-way through a
 * frame by background work. Each step is best effort; anything missing is skipped quietly.
 */
internal object DesktopSchedulingBoost {

    private interface Kernel32 : Library {
        fun GetCurrentProcess(): Pointer
        fun SetPriorityClass(process: Pointer, priorityClass: Int): Boolean
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

    private const val ABOVE_NORMAL_PRIORITY_CLASS = 0x00008000
    private const val NORMAL_PRIORITY_CLASS = 0x00000020
    private const val GPU_PRIORITY_NORMAL = 2
    private const val GPU_PRIORITY_ABOVE_NORMAL = 3
    private const val GPU_PRIORITY_HIGH = 4

    @Volatile
    private var inFront = true

    /**
     * Ahead of other programs only while the window is the one being used. Behind something else —
     * a game, above all — the app goes back to an ordinary process's place on the CPU and the
     * GPU: raised then, it took its frames out of the game's, and the two fought over the GPU
     * with both of them stuttering. The sound is unaffected: its thread keeps its own priority.
     */
    @Volatile
    private var seen = true

    /**
     * [front]: the window is the one in use. [visible]: it can be seen at all. The processor's
     * priority is raised only in front; the GPU's whenever the window can be seen — its frames are
     * small and due every few milliseconds, and behind a game's large ones in the GPU's queue they
     * came late, which is what made the disc stutter beside a game. Out of sight, nothing is raised.
     */
    fun setInFront(front: Boolean, visible: Boolean = front) {
        if (!DesktopPlatform.isWindows || (front == inFront && visible == seen)) return
        inFront = front
        seen = visible
        runCatching {
            val kernel = Native.load("kernel32", Kernel32::class.java)
            val gdi = Native.load("gdi32", Gdi32::class.java)
            val process = kernel.GetCurrentProcess()
            kernel.SetPriorityClass(process, if (front) ABOVE_NORMAL_PRIORITY_CLASS else NORMAL_PRIORITY_CLASS)
            val gpu = if (visible) GPU_PRIORITY_HIGH else GPU_PRIORITY_NORMAL
            if (gdi.D3DKMTSetProcessSchedulingPriorityClass(process, gpu) != 0 && visible) {
                gdi.D3DKMTSetProcessSchedulingPriorityClass(process, GPU_PRIORITY_ABOVE_NORMAL)
            }
            DesktopTrackLog.log("scheduling: ${if (front) "in front" else "behind"}, ${if (visible) "seen" else "out of sight"}")
        }.onFailure { DesktopTrackLog.log("scheduling: priority change failed: ${it.message}") }
    }

    /** Raises the whole process; call once at start. */
    fun boostProcess() {
        if (!DesktopPlatform.isWindows) return
        runCatching {
            val kernel = Native.load("kernel32", Kernel32::class.java)
            kernel.SetPriorityClass(kernel.GetCurrentProcess(), ABOVE_NORMAL_PRIORITY_CLASS)
        }.onFailure { DesktopTrackLog.log("scheduling: process priority unavailable: ${it.message}") }
        noPowerThrottling()
    }

    /**
     * Ahead of ordinary programs on the GPU as well: a game or a video running alongside fills
     * the GPU's queue, and this app's frames — small, and due every few milliseconds — waited
     * behind its large ones. HIGH where Windows allows it, ABOVE_NORMAL otherwise.
     */
    fun boostGpu() {
        if (!DesktopPlatform.isWindows) return
        runCatching {
            val kernel = Native.load("kernel32", Kernel32::class.java)
            val gdi = Native.load("gdi32", Gdi32::class.java)
            val process = kernel.GetCurrentProcess()
            val high = gdi.D3DKMTSetProcessSchedulingPriorityClass(process, GPU_PRIORITY_HIGH)
            val result = if (high == 0) "high" else {
                val above = gdi.D3DKMTSetProcessSchedulingPriorityClass(process, GPU_PRIORITY_ABOVE_NORMAL)
                if (above == 0) "above normal" else "unchanged (0x%08x)".format(above)
            }
            DesktopTrackLog.log("scheduling: GPU priority $result")
        }.onFailure { DesktopTrackLog.log("scheduling: GPU priority unavailable: ${it.message}") }
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
        thread.priority = Thread.MAX_PRIORITY
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
