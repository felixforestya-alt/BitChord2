package com.music.bitchord.desktop

import kotlinx.coroutines.delay

/**
 * Room on the graphics card for what the window draws with.
 *
 * Skia keeps the textures it renders through — the layers behind glass, blurs, faded groups —
 * for reuse, but only up to a budget, a few hundred megabytes by default. On a 4K window one such
 * layer is over thirty megabytes, and while the player slides over the page both are drawn: the
 * budget ran out, Skia let go of textures the next frame needed again and made them anew, and
 * those frames took tens of milliseconds. A larger budget keeps them.
 */
internal object DesktopGpuCache {

    private const val BUDGET_BYTES = 1_536L * 1024 * 1024

    /** Raises the budget of [window]'s drawing context, once that context exists. */
    suspend fun raise(window: java.awt.Window) {
        repeat(40) {
            val found = runCatching { contextOf(window) }.getOrNull()
            if (found != null) {
                val (context, lock) = found
                // Under the lock the frames are drawn under: the context is not to be touched
                // from two threads at once.
                val before = synchronized(lock) {
                    context.resourceCacheLimit.also { if (it < BUDGET_BYTES) context.resourceCacheLimit = BUDGET_BYTES }
                }
                DesktopTrackLog.log("gpu cache: budget %d MB -> %d MB".format(before shr 20, context.resourceCacheLimit shr 20))
                return
            }
            delay(250)
        }
        DesktopTrackLog.log("gpu cache: drawing context not found, budget left as it is")
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
