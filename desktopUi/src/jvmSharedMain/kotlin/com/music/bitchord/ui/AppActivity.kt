package com.music.bitchord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * Whether anyone is looking at the window: not when it is behind something else, minimised or
 * put away to the tray. Then it is a music player playing in the background — a game in front of
 * it, say — and every animation that runs for the eye stops where it is, so the window asks for
 * no frames at all. Sound is not touched by this.
 *
 * Set by the desktop app from its window's focus and state; a phone leaves it false.
 */
object AppActivity {
    var quiet by mutableStateOf(false)
}

/**
 * What drifts on its own — a gradient's slow orbit, a disc turning, a lit word travelling — is
 * drawn at this rate, whatever the display runs at. On a 144–240 Hz monitor every one of those
 * otherwise held the whole window at the display's full rate, three or four times the work, for
 * motion the eye cannot tell from this. Scrolling, dragging and transitions are not held to it.
 */
const val AMBIENT_FPS = 60

private const val AMBIENT_FRAME_NANOS = 1_000_000_000L / AMBIENT_FPS

/**
 * A step for something that drifts, at the next beat of the shared ambient clock: [onFrame] gets
 * the time on the [System.nanoTime] clock, writes what moves, and the frame that draws it is
 * waited for — so together they keep the window at [AMBIENT_FPS].
 */
suspend fun awaitAmbientFrame(onFrame: (Long) -> Unit) {
    // One beat for every drifting thing, run by whichever of them got there first: it waits for
    // the slot, makes every waiting one's step itself, all together, and then asks for the one
    // frame that draws them. Each stepping and asking on its own, the first one's frame was
    // already being drawn when the next wrote — two or three frames to a beat.
    // Everything here runs on the UI thread, so the queue needs no lock.
    // Waits while another beats; asked to beat instead, checks again that nobody has started.
    while (ambientDriving) {
        val stepped = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
            val waiter = AmbientWaiter(onFrame, continuation)
            ambientWaiters += waiter
            continuation.invokeOnCancellation { ambientWaiters.remove(waiter) }
        }
        // Stepped by the beat that woke it — unless the one beating went away first, and this
        // one beats instead.
        if (stepped) return
    }
    ambientDriving = true
    try {
        val wait = nextAmbientAt - System.nanoTime()
        if (wait > 0) kotlinx.coroutines.delay((wait + 999_999L) / 1_000_000L)
        val at = System.nanoTime()
        nextAmbientAt = at + AMBIENT_FRAME_NANOS - AMBIENT_SLACK_NANOS
        ambientBeats++
        ambientTick.intValue++
        val batch = ambientWaiters.toList()
        ambientWaiters.clear()
        // Written before the frame, then waited for: the writes are what ask for the frame, and
        // they are drawn in it.
        onFrame(at)
        batch.forEach { it.onFrame(at) }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        withFrameNanos { }
        batch.forEach { if (it.continuation.isActive) it.continuation.resumeWith(Result.success(true)) }
    } finally {
        ambientDriving = false
        // Whoever came in meanwhile, or was left waiting by a beat cut short, beats next.
        if (ambientWaiters.isNotEmpty()) {
            val next = ambientWaiters.removeAt(0)
            if (next.continuation.isActive) next.continuation.resumeWith(Result.success(false))
        }
    }
}

private class AmbientWaiter(
    val onFrame: (Long) -> Unit,
    val continuation: kotlinx.coroutines.CancellableContinuation<Boolean>,
)

private val ambientWaiters = ArrayList<AmbientWaiter>()
private var ambientDriving = false

@Volatile
private var nextAmbientAt = 0L

/**
 * Bumped at every beat of the ambient clock, before its frame. What redraws because something
 * under it moved — a glass pane over the page — reads this to redraw in that same frame, rather
 * than finding out after it and asking for one more.
 */
val ambientTick = androidx.compose.runtime.mutableIntStateOf(0)

/** Beats of the ambient clock so far, for measuring. */
@Volatile
var ambientBeats = 0L
    private set

/** Asked a little early, so the frame after the pause lands on the display's next refresh. */
private const val AMBIENT_SLACK_NANOS = 2_000_000L

/**
 * A fraction going 0 → 1 once every [periodMillis], round and round, for what loops on its own —
 * standing still, where it got to, while [running] is false or the window is [AppActivity.quiet].
 * In place of an infinite transition, which keeps asking for frames whatever happens.
 */
@Composable
fun rememberLoopingFraction(periodMillis: Int, running: Boolean = true): State<Float> {
    val fraction = remember { mutableFloatStateOf(0f) }
    val period by rememberUpdatedState(periodMillis.coerceAtLeast(1))
    val quiet = AppActivity.quiet
    LaunchedEffect(running, quiet) {
        if (!running || quiet) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            awaitAmbientFrame { now ->
                fraction.floatValue = (fraction.floatValue + (now - last) / 1_000_000f / period) % 1f
                last = now
            }
        }
    }
    return fraction
}
