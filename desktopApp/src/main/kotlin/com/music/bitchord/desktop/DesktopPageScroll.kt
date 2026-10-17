package com.music.bitchord.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The list the open page scrolls by, whichever page it is.
 *
 * Home, Explore, Search and Library hold their lists up in the app, so the smooth wheel, the
 * middle-button autoscroll and a scrollbar could be given to them; an album, a playlist, an
 * artist, History and the rest kept theirs to themselves and had none of it. A page's list made
 * with [rememberPageListState] is announced here instead, and the page frame gives it the same.
 */
internal class DesktopPageScroll {
    var state by mutableStateOf<ScrollableState?>(null)
}

internal val LocalDesktopPageScroll = staticCompositionLocalOf<DesktopPageScroll?> { null }

/** A page's list, announced as the one the page scrolls by while it is shown. */
@Composable
internal fun rememberPageListState(): LazyListState {
    val state = rememberLazyListState()
    val page = LocalDesktopPageScroll.current
    DisposableEffect(state, page) {
        page?.state = state
        onDispose { if (page?.state === state) page.state = null }
    }
    return state
}

/** As [rememberPageListState], for a page laid out as a grid. */
@Composable
internal fun rememberPageGridState(): LazyGridState {
    val state = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val page = LocalDesktopPageScroll.current
    DisposableEffect(state, page) {
        page?.state = state
        onDispose { if (page?.state === state) page.state = null }
    }
    return state
}

/**
 * The page's scrollbar along its right edge, whatever kind of list [state] is: a thumb drawn to be
 * seen on any backdrop, dragged to scroll, the track clicked to jump there. Its own rather than
 * Compose's, whose thumb sat nearly invisible on light covers and would not be dragged back up
 * once the page had been scrolled down.
 */
@Composable
internal fun DesktopPageScrollbar(state: ScrollableState?, modifier: Modifier) {
    if (state == null) return
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val wide by animateFloatAsState(if (hovered || dragging) 1f else 0f, tween(180), label = "scrollbarWide")
    Box(
        modifier
            .width(16.dp)
            .hoverable(interaction)
            .pointerInput(state) {
                detectTapGestures { at ->
                    val m = measureOf(state, size.height.toFloat()) ?: return@detectTapGestures
                    // A click on the track: the thumb's middle goes there.
                    val target = ((at.y - m.thumb / 2f) / (size.height - m.thumb).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val delta = target * m.range - m.scrolled
                    scope.launch { state.animateScrollBy(delta, tween(320)) }
                }
            }
            .pointerInput(state) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, dy ->
                    change.consume()
                    val m = measureOf(state, size.height.toFloat()) ?: return@detectVerticalDragGestures
                    val track = (size.height - m.thumb).coerceAtLeast(1f)
                    state.dispatchRawDelta(dy * m.range / track)
                }
            }
            .drawBehind {
                val m = measureOf(state, size.height) ?: return@drawBehind
                val width = (5f + 4f * wide).dp.toPx()
                val left = size.width - width - 3.dp.toPx()
                val top = (size.height - m.thumb) * (m.scrolled / m.range).coerceIn(0f, 1f)
                val r = CornerRadius(width / 2f)
                // The track, faintly, while the pointer is near.
                if (wide > 0f) {
                    drawRoundRect(Color.White.copy(alpha = 0.07f * wide), Offset(left, 0f), Size(width, size.height), r)
                }
                // A dark edge round a light thumb: seen on a dark page and on a white cover alike.
                drawRoundRect(Color.Black.copy(alpha = 0.4f), Offset(left - 1.5f, top - 1.5f), Size(width + 3f, m.thumb + 3f), r)
                drawRoundRect(Color.White.copy(alpha = 0.45f + 0.35f * wide), Offset(left, top), Size(width, m.thumb), r)
            },
    )
}

/** Where the page stands: how far it is scrolled, how far it can go, and the thumb's length. */
private class ScrollMeasure(val scrolled: Float, val range: Float, val thumb: Float)

private fun measureOf(state: ScrollableState, track: Float): ScrollMeasure? {
    val scrolled: Float
    val content: Float
    val viewport: Float
    when (state) {
        is ScrollState -> {
            scrolled = state.value.toFloat()
            viewport = state.viewportSize.toFloat()
            content = state.maxValue + viewport
        }
        is LazyListState -> {
            val info = state.layoutInfo
            val items = info.visibleItemsInfo
            if (items.isEmpty()) return null
            // The rows not laid out are taken to be as tall as the ones that are.
            val average = items.sumOf { it.size }.toFloat() / items.size
            viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            scrolled = state.firstVisibleItemIndex * average + state.firstVisibleItemScrollOffset
            content = average * info.totalItemsCount + info.beforeContentPadding + info.afterContentPadding
        }
        is LazyGridState -> {
            val info = state.layoutInfo
            val items = info.visibleItemsInfo
            if (items.isEmpty()) return null
            val rows = items.map { it.offset.y }.distinct().size.coerceAtLeast(1)
            val perRow = ((items.size + rows - 1) / rows).coerceAtLeast(1)
            val rowHeight = items.sumOf { it.size.height }.toFloat() / items.size
            val totalRows = (info.totalItemsCount + perRow - 1) / perRow
            viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            scrolled = (state.firstVisibleItemIndex / perRow) * rowHeight + state.firstVisibleItemScrollOffset
            content = rowHeight * totalRows + info.beforeContentPadding + info.afterContentPadding
        }
        else -> return null
    }
    val range = content - viewport
    if (range <= 1f || viewport <= 0f || track <= 0f) return null
    val thumb = (track * viewport / content).coerceIn(minOf(48f, track), track)
    return ScrollMeasure(scrolled.coerceIn(0f, range), range, thumb)
}
