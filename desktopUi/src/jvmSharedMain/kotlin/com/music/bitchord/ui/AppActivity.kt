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
 * Whether anyone can see the window: not when it is minimised, put away to the tray, or covered
 * whole by another window — a game in front of it, say. Then it is a music player playing in the background — a game in front of
 * it, say — and every animation that runs for the eye stops where it is, so the window asks for
 * no frames at all. Sound is not touched by this.
 *
 * Set by the desktop app from its window's state; a phone leaves it false. Merely not having
 * focus does not count: a window on a second screen is still being watched.
 */
object AppActivity {
    var quiet by mutableStateOf(false)
}

/**
 * A frame for something that moves on its own, at the display's full rate — 160 Hz on a 160 Hz
 * monitor — with the frame's own time, so the motion is as even as the display is.
 *
 * What makes it cheap is not a lower rate but that nothing asks for frames it does not need:
 * whatever calls this stops while the window is [AppActivity.quiet], and every caller writes in
 * the frame's callback, so a step is drawn in the frame it was made for.
 */
suspend fun awaitAmbientFrame(onFrame: (Long) -> Unit) {
    withFrameNanos { now ->
        // Once per frame, however many step in it.
        if (now != lastAmbientFrame) {
            lastAmbientFrame = now
            ambientBeats++
            ambientTick.intValue++
        }
        onFrame(now)
    }
}

private var lastAmbientFrame = Long.MIN_VALUE

/**
 * Bumped on every frame something steps on its own in, inside that frame. What redraws because
 * something under it moved — a glass pane over the page — reads this to redraw in that same
 * frame, rather than finding out after it and asking for one more.
 */
val ambientTick = androidx.compose.runtime.mutableIntStateOf(0)

/** Frames something stepped on its own in, so far — for measuring. */
@Volatile
var ambientBeats = 0L
    private set

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
        var last = withFrameNanos { it }
        while (true) {
            awaitAmbientFrame { now ->
                fraction.floatValue = (fraction.floatValue + (now - last) / 1_000_000f / period) % 1f
                last = now
            }
        }
    }
    return fraction
}
