package com.music.bitchord.desktop

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign

/**
 * Mouse-wheel scrolling that glides. Out of the box a notch of the wheel moves a list a fixed step
 * at once, so a page — and the home card's morph, which follows the page — went in jerks. Here each
 * notch adds to a distance still to travel, and every frame of the display covers a share of what
 * is left: the page eases to a stop the way a browser's or Apple's does, and a fast spin of the
 * wheel simply runs on further. Ends of the list stop it short rather than storing up the rest.
 *
 * [snap], where given, can take a turn of the wheel over entirely: it returns how far to travel
 * instead, and that distance is then covered in one eased stroke that the wheel cannot interrupt —
 * the home card's morph done whole rather than dragged notch by notch.
 */
@Stable
internal class DesktopSmoothWheel(
    private val state: ScrollableState,
    private val scope: CoroutineScope,
) {
    var snap: ((deltaPx: Float) -> Float?)? = null

    /** Where the middle button went down, while autoscrolling — for the marker drawn there. */
    var anchor by androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
        internal set

    /** -1 up, 1 down, 0 still: which way the pointer has the page going. */
    var direction by androidx.compose.runtime.mutableIntStateOf(0)
        internal set

    private var pending = 0f
    private var gliding: Job? = null
    private var snapping: Job? = null
    private var autoscroll: Job? = null

    /** Adds [deltaPx] to travel; positive goes on down the list. */
    fun add(deltaPx: Float) {
        if (snapping?.isActive == true) return
        snap?.invoke(deltaPx)?.let { distance ->
            gliding?.cancel()
            pending = 0f
            snapping = scope.launch { state.animateScrollBy(distance, tween(SNAP_MS, easing = SNAP_EASING)) }
            return
        }
        // A turn the other way drops what was still to go the first way, as a real wheel would.
        if (pending != 0f && (pending > 0f) != (deltaPx > 0f)) pending = 0f
        pending += deltaPx
        if (gliding?.isActive != true) {
            gliding = scope.launch {
                try {
                    glide()
                } finally {
                    pending = 0f
                }
            }
        }
    }

    private suspend fun glide() {
        var last = withFrameNanos { it }
        while (abs(pending) > 0.5f) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                val step = pending * (1f - exp(-dt / GLIDE_SECONDS))
                val used = state.dispatchRawDelta(step)
                pending -= step
                // The end of the list: nothing more to travel.
                if (abs(used) < abs(step) * 0.5f) pending = 0f
            }
        }
    }

    /**
     * The middle button's scrolling, as in a browser: from where it was pressed, the further the
     * pointer moves away the faster the page runs that way; [offset] reports how far it is now.
     */
    fun startAutoscroll(offset: () -> Float) {
        autoscroll?.cancel()
        gliding?.cancel()
        autoscroll = scope.launch {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    last = now
                    val away = offset()
                    val beyond = (abs(away) - AUTOSCROLL_DEAD_ZONE).coerceAtLeast(0f)
                    if (beyond > 0f) state.dispatchRawDelta(sign(away) * beyond.pow(1.25f) * AUTOSCROLL_GAIN * dt)
                }
            }
        }
    }

    fun stopAutoscroll() {
        autoscroll?.cancel()
        autoscroll = null
        anchor = null
        direction = 0
    }

    val autoscrolling: Boolean get() = autoscroll?.isActive == true

    private companion object {
        /** How quickly the remaining distance closes: about 95% of it in three times this. */
        const val GLIDE_SECONDS = 0.11f
        const val SNAP_MS = 560
        val SNAP_EASING = CubicBezierEasing(0.33f, 0f, 0.15f, 1f)
        const val AUTOSCROLL_DEAD_ZONE = 12f
        const val AUTOSCROLL_GAIN = 6f
    }
}

@Composable
internal fun rememberSmoothWheel(state: ScrollableState): DesktopSmoothWheel {
    val scope = rememberCoroutineScope()
    return remember(state) { DesktopSmoothWheel(state, scope) }
}

/**
 * Takes the vertical wheel over this away from whatever would scroll in steps and gives it to
 * [wheel]; and the middle button, for browser-style autoscroll. Sideways wheels, and shift for
 * sideways, are left alone for the shelves.
 */
internal fun Modifier.smoothWheel(wheel: DesktopSmoothWheel?): Modifier = if (wheel == null) this else composed {
    // The cursor of a browser's autoscroll: an arrow the way the page is going, a four-way one
    // while it rests on the spot it started from.
    val cursor = when {
        wheel.anchor == null -> null
        wheel.direction < 0 -> PointerIcon(java.awt.Cursor(java.awt.Cursor.N_RESIZE_CURSOR))
        wheel.direction > 0 -> PointerIcon(java.awt.Cursor(java.awt.Cursor.S_RESIZE_CURSOR))
        else -> PointerIcon(java.awt.Cursor(java.awt.Cursor.MOVE_CURSOR))
    }
    this
        .then(if (cursor != null) Modifier.pointerHoverIcon(cursor, overrideDescendants = true) else Modifier)
        .drawWithContent {
            drawContent()
            val at = wheel.anchor ?: return@drawWithContent
            drawAutoscrollMarker(at)
        }
        .pointerInput(wheel) {
            val notch = WHEEL_NOTCH.toPx()
            awaitPointerEventScope {
                var anchorY = 0f
                var pointerY = 0f
                var pressedAt = 0L
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    when (event.type) {
                        PointerEventType.Scroll -> {
                            if (event.keyboardModifiers.isShiftPressed || change.isConsumed) continue
                            val delta = change.scrollDelta
                            if (delta.y == 0f || abs(delta.x) > abs(delta.y)) continue
                            if (wheel.autoscrolling) wheel.stopAutoscroll()
                            wheel.add(delta.y * notch)
                            event.changes.forEach { it.consume() }
                        }
                        PointerEventType.Press -> {
                            if (wheel.autoscrolling) {
                                // Any click ends a clicked-on autoscroll.
                                wheel.stopAutoscroll()
                                event.changes.forEach { it.consume() }
                            } else if (event.buttons.isTertiaryPressed) {
                                anchorY = change.position.y
                                pointerY = anchorY
                                pressedAt = System.nanoTime()
                                wheel.anchor = change.position
                                wheel.direction = 0
                                wheel.startAutoscroll { pointerY - anchorY }
                                event.changes.forEach { it.consume() }
                            }
                        }
                        PointerEventType.Move -> if (wheel.autoscrolling) {
                            pointerY = change.position.y
                            val away = pointerY - anchorY
                            wheel.direction = when {
                                away < -AUTOSCROLL_REST -> -1
                                away > AUTOSCROLL_REST -> 1
                                else -> 0
                            }
                        }
                        PointerEventType.Release -> {
                            // Held and let go: stop. Clicked: keeps going until the next click.
                            if (wheel.autoscrolling && System.nanoTime() - pressedAt > HOLD_NANOS && !event.buttons.isTertiaryPressed) {
                                wheel.stopAutoscroll()
                            }
                        }
                    }
                }
            }
        }
}

/** A browser's autoscroll mark: a small round badge with an arrow up and an arrow down. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAutoscrollMarker(at: androidx.compose.ui.geometry.Offset) {
    val r = 15.dp.toPx()
    drawCircle(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), radius = r, center = at)
    drawCircle(
        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
        radius = r,
        center = at,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
    )
    drawCircle(androidx.compose.ui.graphics.Color.White, radius = 2.dp.toPx(), center = at)
    val w = 5.dp.toPx()
    val h = 4.dp.toPx()
    val gap = 5.dp.toPx()
    fun arrow(up: Boolean) {
        val tipY = if (up) at.y - gap - h else at.y + gap + h
        val baseY = if (up) at.y - gap else at.y + gap
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(at.x, tipY)
            lineTo(at.x - w, baseY)
            lineTo(at.x + w, baseY)
            close()
        }
        drawPath(path, androidx.compose.ui.graphics.Color.White)
    }
    arrow(up = true)
    arrow(up = false)
}

/** One notch of the wheel. */
private val WHEEL_NOTCH = 110.dp

/** Longer than this between press and release is holding the middle button, not clicking it. */
private const val HOLD_NANOS = 300_000_000L

/** Within this many pixels of where it started the pointer leaves the page still. */
private const val AUTOSCROLL_REST = 12f
