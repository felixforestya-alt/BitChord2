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
    }

    private interface Gdi32 : Library {
        /** NTSTATUS; 0 is success. */
        fun D3DKMTSetProcessSchedulingPriorityClass(process: Pointer, priority: Int): Int
    }

    private interface Avrt : Library {
        fun AvSetMmThreadCharacteristicsW(task: WString, taskIndex: IntByReference): Pointer?
    }

    private const val ABOVE_NORMAL_PRIORITY_CLASS = 0x00008000
    private const val GPU_PRIORITY_ABOVE_NORMAL = 3
    private const val GPU_PRIORITY_HIGH = 4

    /** Raises the whole process; call once at start. */
    fun boostProcess() {
        if (!DesktopPlatform.isWindows) return
        runCatching {
            val kernel = Native.load("kernel32", Kernel32::class.java)
            kernel.SetPriorityClass(kernel.GetCurrentProcess(), ABOVE_NORMAL_PRIORITY_CLASS)
        }.onFailure { DesktopTrackLog.log("scheduling: process priority unavailable: ${it.message}") }
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

    /** Registers the calling thread — the one that draws — with MMCSS. */
    fun boostRenderThread() {
        if (!DesktopPlatform.isWindows) return
        runCatching {
            val avrt = Native.load("avrt", Avrt::class.java)
            avrt.AvSetMmThreadCharacteristicsW(WString("Games"), IntByReference(0))
        }.onFailure { DesktopTrackLog.log("scheduling: MMCSS unavailable: ${it.message}") }
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
