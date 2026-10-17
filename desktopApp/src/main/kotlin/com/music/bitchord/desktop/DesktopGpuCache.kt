package com.music.bitchord.desktop

import kotlinx.coroutines.delay

/**
 * Room on the graphics card for what the window draws with — and handing it back while the window
 * is out of sight.
 *
 * Skia keeps the textures it renders through for reuse, up to a budget. On Windows that memory is
 * the process's too: every megabyte of the budget held shows up in Task Manager. While the window
 * is shown the budget is Skia's own default; once it is minimised or in the tray, nothing is being
 * drawn, so the budget drops to almost nothing and Skia frees what it held.
 */
internal object DesktopGpuCache {

    private val shownBudget: Long =
        (System.getenv("BITCHORD_GPU_BUDGET_MB")?.toLongOrNull() ?: DEFAULT_BUDGET_MB) * 1024 * 1024

    /**
     * Skia's own default, kept. Raised to 1.5 GB once, on a guess that the player's first opening
     * was losing textures to eviction; measured, it was shader compiles instead, and the larger
     * budget only made the process hold about 1.5 GB more memory for no smoother a frame.
     */
    private const val DEFAULT_BUDGET_MB = 256L

    /** What Skia may keep while nothing is drawn. */
    private const val HIDDEN_BUDGET_BYTES = 8L * 1024 * 1024

    @Volatile private var found: Pair<org.jetbrains.skia.DirectContext, Any>? = null

    /** Finds [window]'s drawing context once it exists and sets the shown budget on it. */
    suspend fun attach(window: java.awt.Window) {
        repeat(40) {
            val context = runCatching { contextOf(window) }.getOrNull()
            if (context != null) {
                found = context
                setBudget(shownBudget)
                return
            }
            delay(250)
        }
        DesktopTrackLog.log("gpu cache: drawing context not found, budget left as it is")
    }

    /** The window is out of sight: let Skia's textures go. */
    fun release() = setBudget(HIDDEN_BUDGET_BYTES)

    /** The window is back: the usual budget again. */
    fun restore() = setBudget(shownBudget)

    private fun setBudget(bytes: Long) {
        val (context, lock) = found ?: return
        // Under the lock the frames are drawn under: the context is not to be touched from two
        // threads at once. Lowering the limit purges down to it there and then.
        val before = runCatching {
            synchronized(lock) { context.resourceCacheLimit.also { if (it != bytes) context.resourceCacheLimit = bytes } }
        }.getOrNull() ?: return
        if (before != bytes) DesktopTrackLog.log("gpu cache: budget %d MB -> %d MB".format(before shr 20, bytes shr 20))
    }

    private fun contextOf(window: java.awt.Window): Pair<org.jetbrains.skia.DirectContext, Any>? {
        val layer = findLayer(window) ?: return null
        val redrawer = layer.javaClass.getMethod("getRedrawer\$skiko").invoke(layer) ?: return null
        val handlerField = generateSequence<Class<*>>(redrawer.javaClass) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { it.name == "contextHandler" } ?: return null
        handlerField.isAccessible = true
        val handler = handlerField.get(redrawer) ?: return null
        val getter = generateSequence<Class<*>>(handler.javaClass) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { it.name == "getContext" && it.parameterCount == 0 } ?: return null
        getter.isAccessible = true
        val context = getter.invoke(handler) as? org.jetbrains.skia.DirectContext ?: return null
        val lock = runCatching {
            redrawer.javaClass.getDeclaredField("drawLock").apply { isAccessible = true }.get(redrawer)
        }.getOrNull() ?: return null
        return context to lock
    }

    private fun findLayer(component: java.awt.Component): org.jetbrains.skiko.SkiaLayer? {
        if (component is org.jetbrains.skiko.SkiaLayer) return component
        if (component is java.awt.Container) {
            for (child in component.components) findLayer(child)?.let { return it }
        }
        return null
    }
}

/**
 * Hands memory back while the window is minimised or in the tray, the way a browser does with a
 * background tab: Skia's textures go (see [DesktopGpuCache]), the Java heap is collected and
 * shrunk, and Windows is told the pages the process holds may be paged out. Playback carries on
 * untouched; it is the drawing side that is put away, and it comes back on the first frame shown.
 */
internal object DesktopMemoryTrim {

    @Volatile private var hiddenSince = 0L
    @Volatile private var trimmed = false

    /** Watches [window] for being minimised or hidden. */
    fun watch(window: java.awt.Window) {
        window.addWindowStateListener { event ->
            val minimised = (event.newState and java.awt.Frame.ICONIFIED) != 0
            if (minimised) onHidden() else onShown()
        }
        window.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentHidden(e: java.awt.event.ComponentEvent?) = onHidden()
            override fun componentShown(e: java.awt.event.ComponentEvent?) = onShown()
        })
    }

    /** For a measuring run: as if the window had just been put away. */
    fun simulateHidden() = onHidden()

    private fun onHidden() {
        val at = System.nanoTime()
        hiddenSince = at
        // A moment's grace: a quick minimise-and-back should not pay for any of this.
        Thread({
            Thread.sleep(TRIM_AFTER_MS)
            if (hiddenSince != at) return@Thread
            trimmed = true
            DesktopGpuCache.release()
            System.gc()
            if (DesktopPlatform.isWindows) {
                runCatching {
                    val psapi = com.sun.jna.NativeLibrary.getInstance("psapi")
                    val kernel = com.sun.jna.NativeLibrary.getInstance("kernel32")
                    val process = kernel.getFunction("GetCurrentProcess").invokePointer(emptyArray())
                    psapi.getFunction("EmptyWorkingSet").invokeInt(arrayOf(process))
                }.onFailure { DesktopTrackLog.log("memory trim: working set not trimmed: ${it.message}") }
            }
            DesktopTrackLog.log("memory trim: window out of sight, memory handed back")
        }, "bitchord-memory-trim").apply { isDaemon = true }.start()
    }

    private fun onShown() {
        hiddenSince = 0L
        if (trimmed) {
            trimmed = false
            DesktopGpuCache.restore()
        }
    }

    private const val TRIM_AFTER_MS = 4_000L
}
