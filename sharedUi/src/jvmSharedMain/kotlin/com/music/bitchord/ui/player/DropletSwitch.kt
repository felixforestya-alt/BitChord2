package com.music.bitchord.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A row of choices with one pill behind the chosen one that moves like a drop of water: its
 * leading edge runs ahead, the trailing one lets go a moment later and catches up, so on the way
 * the pill draws out long and a little thinner, and it settles round with a small wobble.
 *
 * [segment] draws each choice; [selected] says which one the pill sits behind.
 */
@Composable
internal fun DropletSwitch(
    count: Int,
    selected: Int,
    modifier: Modifier = Modifier,
    pill: Color = Color.White,
    segment: @Composable RowScope.(index: Int, chosen: Boolean, onPlacedAt: Modifier) -> Unit,
) {
    val lefts = remember(count) { mutableStateListOf<Float>().apply { repeat(count) { add(Float.NaN) } } }
    val rights = remember(count) { mutableStateListOf<Float>().apply { repeat(count) { add(Float.NaN) } } }
    val left = remember { Animatable(Float.NaN) }
    val right = remember { Animatable(Float.NaN) }

    val targetLeft = lefts.getOrNull(selected) ?: Float.NaN
    val targetRight = rights.getOrNull(selected) ?: Float.NaN
    LaunchedEffect(targetLeft, targetRight) {
        if (targetLeft.isNaN() || targetRight.isNaN()) return@LaunchedEffect
        if (left.value.isNaN() || right.value.isNaN()) {
            // Where it starts: no travel to show.
            left.snapTo(targetLeft)
            right.snapTo(targetRight)
            return@LaunchedEffect
        }
        val forward = targetLeft > left.value
        coroutineScope {
            // The edge in front leads, quick and a little loose; the one behind follows softer.
            launch { (if (forward) right else left).animateTo(if (forward) targetRight else targetLeft, DROP_LEADING) }
            launch { (if (forward) left else right).animateTo(if (forward) targetLeft else targetRight, DROP_TRAILING) }
        }
    }

    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            // Its wobble at either end stays inside the track, rounded, instead of cut flat by it.
            val l = left.value.coerceAtLeast(0f)
            val r = right.value.coerceAtMost(size.width)
            if (l.isNaN() || r.isNaN() || r <= l) return@Canvas
            val rest = (targetRight - targetLeft).takeIf { it > 0f } ?: (r - l)
            // Drawn out by the travel, it gets thinner, as a drop does.
            val stretch = ((r - l) / rest - 1f).coerceIn(0f, 1.5f)
            val height = size.height * (1f - 0.22f * stretch / 1.5f)
            val top = (size.height - height) / 2f
            drawRoundRect(
                color = pill,
                topLeft = Offset(l, top),
                size = Size(r - l, height),
                cornerRadius = CornerRadius(height / 2f, height / 2f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (index in 0 until count) {
                val placed = Modifier.onPlaced { coordinates ->
                    val x = coordinates.positionInParent().x
                    lefts[index] = x
                    rights[index] = x + coordinates.size.width
                }
                segment(index, index == selected, placed)
            }
        }
    }
}

private val DROP_LEADING = spring<Float>(dampingRatio = 0.62f, stiffness = 900f)
private val DROP_TRAILING = spring<Float>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
