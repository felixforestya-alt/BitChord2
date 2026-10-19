@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.music.bitchord.desktop

import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.layout.PrefetchRequest
import androidx.compose.foundation.lazy.layout.PrefetchRequestScope
import androidx.compose.foundation.lazy.layout.PrefetchScheduler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Lists that build their next items before they scroll into view.
 *
 * A lazy list composes an item the frame it first shows, and on Android the list's prefetcher has
 * the next one built in the idle time between frames. On the desktop Compose ships that prefetcher
 * doing nothing: every new shelf of Home was composed inside a frame of the scroll — the card
 * morphing into the bar dropped a frame or two of 15–20 ms each time a shelf came in, and again on
 * the way back, the shelves scrolled past having been thrown away. This is the prefetcher the
 * desktop lacks: it builds what the list asks for in the time a frame leaves over, or, with nothing
 * moving, as soon as it is asked. And the list keeps a window of items around what shows — the
 * next stretch already built, the last stretch not yet thrown away.
 */
internal object DesktopPrefetch {

    /** How far past what shows, ahead and behind, items are kept built — in viewport heights. */
    private val AHEAD = System.getenv("BITCHORD_PREFETCH_AHEAD")?.toFloatOrNull() ?: 1f
    private val BEHIND = System.getenv("BITCHORD_PREFETCH_BEHIND")?.toFloatOrNull() ?: 0.5f

    private val frameNanos: Long by lazy {
        val hz = runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode.refreshRate
        }.getOrDefault(60).takeIf { it in 30..500 } ?: 60
        1_000_000_000L / hz
    }

    /** Frames handed to be drawn so far, for the profiler. */
    @Volatile var framesDrawn = 0L
        private set

    /** When the last frame went to be drawn: the UI thread is done with it from then on. */
    @Volatile private var lastFrame = 0L

    private val queue = ArrayDeque<PrefetchRequest>()
    private var posted = false
    private var stalled = 0

    /** For the profiler: requests made, refused for want of time while moving or still, time spent. */
    var scheduled = 0
        private set
    var refusedMoving = 0
        private set
    var refusedStill = 0
        private set
    var busyNanos = 0L
        private set

    /** Requests carried out, for the profiler. */
    var built = 0
        private set
    private val retry = javax.swing.Timer(20) { run() }.apply { isRepeats = false }

    private object Scheduler : PrefetchScheduler {
        override fun schedulePrefetch(prefetchRequest: PrefetchRequest) {
            queue.addLast(prefetchRequest)
            scheduled++
            if (scheduled <= 60 && System.getenv("BITCHORD_PROFILE") == "1") DesktopTrackLog.log("prefetch: asked #$scheduled")
            post()
        }
    }

    private class Deadline(var at: Long) : PrefetchRequestScope {
        override fun availableTimeNanos(): Long = (at - System.nanoTime()).coerceAtLeast(0L)
    }

    /**
     * A deadline that lets the next step start whenever there is a step's worth of time left.
     * Compose weighs each step of building an item — the item, each card in its row, measuring
     * it — against what it has learnt a step of that kind costs, and its figure for a shelf was
     * one learnt from a shelf built whole in a frame: never under the time offered, so the shelf
     * was put off frame after frame until it scrolled in and was built whole again. Steps are
     * small — a card is about a millisecond — so each is let start with [STEP_NANOS] to spare.
     */
    private class Steps(private val at: Long) : PrefetchRequestScope {
        override fun availableTimeNanos(): Long =
            if (at - System.nanoTime() > STEP_NANOS) PLENTY_NANOS else 0L
    }

    private fun post() {
        if (posted) return
        posted = true
        java.awt.EventQueue.invokeLater { run() }
    }

    /**
     * Builds ahead until [deadline], on the UI thread, from inside a frame that is waiting for
     * its turn at the monitor's refresh (see [DesktopFramePacing]). That wait is the time a frame
     * leaves over, and it was spent asleep: on Home something always moves — the card's
     * visualiser, the lyrics — so the page never fell still enough for the idle slices, and a
     * shelf, judged too big for the half-frame it was otherwise offered, was put off until it
     * scrolled in and was built in a frame of its own. Returns whether anything was left to do.
     */
    fun workUntil(deadline: Long): Boolean {
        if (queue.isEmpty()) return false
        val scope = Steps(deadline)
        while (queue.isNotEmpty() && scope.availableTimeNanos() > 0) {
            val request = queue.first()
            val before = System.nanoTime()
            val more = with(request) { scope.execute() }
            val spent = System.nanoTime() - before
            busyNanos += spent
            if (more) {
                if (spent <= PROGRESS_NANOS) {
                    refusedMoving++
                    break
                }
            } else {
                queue.removeFirst()
                built++
            }
        }
        return queue.isNotEmpty()
    }

    /**
     * Works through what the lists asked for. While frames are coming, only in what the frame
     * leaves over: from the moment one went to be drawn, half the frame's time — the next is
     * started at the display's next refresh, and building work here never pushes it back. With
     * nothing moving, in slices; and an item too big for a slice — the first of its kind, or a
     * heavy one — is built whole the second time it is tried, while still nothing moves.
     */
    private fun run() {
        posted = false
        if (queue.isEmpty()) return
        val now = System.nanoTime()
        val moving = now - lastFrame < frameNanos * 3
        val deadline = Deadline(
            when {
                moving -> lastFrame + frameNanos / 2
                stalled > 0 -> now + IDLE_WHOLE_NANOS
                else -> now + IDLE_SLICE_NANOS
            },
        )
        var progressed = false
        while (queue.isNotEmpty() && deadline.availableTimeNanos() > 0) {
            val request = queue.first()
            val before = System.nanoTime()
            val more = with(request) { deadline.execute() }
            val spent = System.nanoTime() - before
            busyNanos += spent
            if (spent > PROGRESS_NANOS) progressed = true
            if (more) {
                if (spent <= PROGRESS_NANOS) {
                    if (moving) refusedMoving++ else refusedStill++
                    break
                }
            } else {
                queue.removeFirst()
                progressed = true
                built++
            }
        }
        if (queue.isEmpty()) {
            stalled = 0
            return
        }
        if (!moving) stalled = if (progressed) 0 else stalled + 1
        // Moving, the next frame going out brings this back (see [Signal]); still, a moment later.
        retry.restart()
    }

    private const val STEP_NANOS = 1_200_000L
    private const val PLENTY_NANOS = 50_000_000L
    private const val IDLE_SLICE_NANOS = 8_000_000L
    private const val IDLE_WHOLE_NANOS = 200_000_000L
    private const val PROGRESS_NANOS = 200_000L

    /**
     * Skiko's dispatcher for drawing each frame, wrapped: every frame handed to it is the moment
     * the UI thread finished building one, the start of the time it leaves over.
     */
    private class Signal(@Volatile var delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext): Boolean = delegate.isDispatchNeeded(context)

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            lastFrame = System.nanoTime()
            framesDrawn++
            if (queue.isNotEmpty() && !posted) post()
            delegate.dispatch(context, block)
        }
    }

    private var signal: Signal? = null

    /** Puts [Signal] in front of Skiko's drawing dispatcher; once, before the window opens. */
    fun install() {
        runCatching {
            val field = Class.forName("org.jetbrains.skiko.redrawer.DispatcherToBlockOnKt").getDeclaredField("dispatcherToBlockOn")
            field.isAccessible = true
            val current = field.get(null) as CoroutineDispatcher
            val wrapped = Signal(current)
            writeStatic(field, wrapped)
            signal = wrapped
        }.onFailure { DesktopTrackLog.log("prefetch: no frame signal: ${it.message}") }
    }

    /**
     * Has frames drawn by [dispatcher] instead — the profiler's timed pool — keeping the signal.
     * Returns false when there is no signal, and the caller swaps the field itself.
     */
    fun drawOn(dispatcher: CoroutineDispatcher): Boolean {
        val s = signal ?: return false
        s.delegate = dispatcher
        return true
    }

    internal fun writeStatic(field: java.lang.reflect.Field, value: Any) {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        @Suppress("DEPRECATION") val base = unsafe.staticFieldBase(field)
        @Suppress("DEPRECATION") val offset = unsafe.staticFieldOffset(field)
        unsafe.putObject(base, offset, value)
    }

    /** The prefetching and the window of kept items, for a list. */
    fun strategy(): LazyListPrefetchStrategy = if (System.getenv("BITCHORD_NO_PREFETCH") == "1") LazyListPrefetchStrategy() else object : LazyListPrefetchStrategy by cacheWindow() {
        override val prefetchScheduler: PrefetchScheduler get() = Scheduler
    }

    /**
     * Compose's own strategy for a window of kept items — internal to Compose's Kotlin, though not to
     * the JVM; the plain one-item-ahead prefetching should it ever move.
     */
    private fun cacheWindow(): LazyListPrefetchStrategy = runCatching {
        Class.forName("androidx.compose.foundation.lazy.LazyListCacheWindowStrategy")
            .getConstructor(LazyLayoutCacheWindow::class.java)
            .newInstance(LazyLayoutCacheWindow(aheadFraction = AHEAD, behindFraction = BEHIND)) as LazyListPrefetchStrategy
    }.getOrElse { LazyListPrefetchStrategy() }

    /** As `rememberLazyListState()`, with [strategy]. */
    @Composable
    fun rememberListState(): LazyListState = rememberSaveable(saver = Saver) { LazyListState(0, 0, strategy()) }

    private val Saver = androidx.compose.runtime.saveable.listSaver<LazyListState, Int>(
        save = { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
        restore = { LazyListState(it[0], it[1], strategy()) },
    )
}
