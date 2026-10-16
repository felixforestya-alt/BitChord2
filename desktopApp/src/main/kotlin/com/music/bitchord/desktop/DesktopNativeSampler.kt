package com.music.bitchord.desktop

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

/**
 * Where the drawing thread spends its time, by module — measuring runs only. Every millisecond
 * the thread is stopped for a moment and the address it is at read, along with the return
 * addresses on its stack; each is put down to the DLL it falls in. The frames that took long are
 * then told apart: Skia itself, the graphics driver, a shader compiler, or waiting.
 */
internal object DesktopNativeSampler {

    @Suppress("FunctionName")
    private interface Kernel32 : StdCallLibrary {
        fun GetCurrentThreadId(): Int
        fun GetCurrentProcess(): Pointer
        fun OpenThread(access: Int, inherit: Boolean, id: Int): Pointer?
        fun SuspendThread(thread: Pointer): Int
        fun ResumeThread(thread: Pointer): Int
        fun GetThreadContext(thread: Pointer, context: Pointer): Boolean
        fun K32EnumProcessModules(process: Pointer, modules: Pointer, size: Int, needed: IntByReference): Boolean
        fun K32GetModuleFileNameExW(process: Pointer, module: Pointer, name: CharArray, size: Int): Int
        fun K32GetModuleInformation(process: Pointer, module: Pointer, info: Pointer, size: Int): Boolean
    }

    private val kernel by lazy { Native.load("kernel32", Kernel32::class.java) }

    @Volatile var renderThreadId = 0
    @Volatile private var sampling = false

    private class Sample(val time: Long, val chain: String)
    private val samples = java.util.concurrent.ConcurrentLinkedQueue<Sample>()
    private class Task(val start: Long, val end: Long)
    private val slowTasks = java.util.concurrent.ConcurrentLinkedQueue<Task>()

    private class Module(val name: String, val base: Long, val end: Long)
    @Volatile private var modules: List<Module> = emptyList()

    /** Called on the drawing thread as each task starts. */
    fun noteRenderThread() {
        if (renderThreadId == 0) renderThreadId = runCatching { kernel.GetCurrentThreadId() }.getOrDefault(0)
    }

    fun noteTask(start: Long, end: Long) {
        if (sampling && end - start > 18_000_000L) slowTasks += Task(start, end)
    }

    private fun loadModules() {
        val process = kernel.GetCurrentProcess()
        val handles = Memory(8L * 1024)
        val needed = IntByReference()
        if (!kernel.K32EnumProcessModules(process, handles, 8 * 1024, needed)) return
        val count = needed.value / 8
        val info = Memory(24)
        val name = CharArray(512)
        modules = (0 until count).mapNotNull { i ->
            val module = handles.getPointer(i * 8L) ?: return@mapNotNull null
            if (!kernel.K32GetModuleInformation(process, module, info, 24)) return@mapNotNull null
            val length = kernel.K32GetModuleFileNameExW(process, module, name, name.size)
            val base = Pointer.nativeValue(info.getPointer(0))
            Module(String(name, 0, length).substringAfterLast('\\').lowercase(), base, base + (info.getInt(8).toLong() and 0xFFFFFFFFL))
        }.sortedBy { it.base }
    }

    private fun moduleOf(address: Long): String? {
        val list = modules
        var lo = 0
        var hi = list.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val m = list[mid]
            when {
                address < m.base -> hi = mid - 1
                address >= m.end -> lo = mid + 1
                else -> return m.name
            }
        }
        return null
    }

    /** Samples the drawing thread while [action] runs, then logs where the slow frames went. */
    suspend fun <T> around(label: String, action: suspend () -> T): T {
        val id = renderThreadId
        if (id == 0) return action()
        loadModules()
        samples.clear()
        slowTasks.clear()
        sampling = true
        val thread = Thread({ sampleLoop(id) }, "bitchord-native-sampler").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
        try {
            return action()
        } finally {
            sampling = false
            thread.join(200)
            report(label)
        }
    }

    private fun sampleLoop(id: Int) {
        // THREAD_SUSPEND_RESUME | THREAD_GET_CONTEXT | THREAD_QUERY_INFORMATION
        val handle = kernel.OpenThread(0x0002 or 0x0008 or 0x0040, false, id) ?: return
        val raw = Memory(1232 + 32)
        val context = raw.align(16)
        val stack = LongArray(STACK_WORDS)
        while (sampling) {
            context.setInt(0x30, 0x00100003) // CONTEXT_CONTROL | CONTEXT_INTEGER, AMD64
            var ok = false
            var rip = 0L
            var rsp = 0L
            if (kernel.SuspendThread(handle) != -1) {
                try {
                    if (kernel.GetThreadContext(handle, context)) {
                        rip = context.getLong(0xF8)
                        rsp = context.getLong(0x98)
                        // The words just above the stack pointer: the return addresses of the
                        // calls the thread is inside, among other things.
                        Pointer(rsp).read(0, stack, 0, STACK_WORDS)
                        ok = true
                    }
                } finally {
                    kernel.ResumeThread(handle)
                }
            }
            if (ok) {
                val chain = ArrayList<String>(6)
                moduleOf(rip)?.let { chain += it } ?: chain.add("?")
                for (word in stack) {
                    val m = moduleOf(word) ?: continue
                    if (chain.last() != m) chain += m
                    if (chain.size >= 5) break
                }
                samples += Sample(System.nanoTime(), chain.joinToString(" < "))
            }
            Thread.sleep(1)
        }
    }

    private fun report(label: String) {
        val all = samples.toList()
        val slow = slowTasks.toList()
        fun histogram(list: List<Sample>, title: String) {
            if (list.isEmpty()) return
            DesktopTrackLog.log("native $label $title: ${list.size} samples")
            list.groupingBy { it.chain.substringBefore(" < ") }.eachCount().entries.sortedByDescending { it.value }.take(8)
                .forEach { (m, n) -> DesktopTrackLog.log("native   top %4d %5.1f%%  %s".format(n, n * 100.0 / list.size, m)) }
            list.groupingBy { it.chain }.eachCount().entries.sortedByDescending { it.value }.take(12)
                .forEach { (m, n) -> DesktopTrackLog.log("native   chain %4d  %s".format(n, m)) }
        }
        histogram(all, "all")
        histogram(all.filter { s -> slow.any { s.time in it.start..it.end } }, "slow frames (${slow.size})")
    }

    private const val STACK_WORDS = 384
}
