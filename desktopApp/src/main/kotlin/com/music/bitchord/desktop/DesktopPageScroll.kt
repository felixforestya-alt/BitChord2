package com.music.bitchord.desktop

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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

/** A thin scrollbar for [state] along the page's right edge, whatever kind of list it is. */
@Composable
internal fun DesktopPageScrollbar(state: ScrollableState?, modifier: Modifier) {
    val adapter = when (state) {
        is LazyListState -> rememberScrollbarAdapter(state)
        is LazyGridState -> rememberScrollbarAdapter(state)
        is ScrollState -> rememberScrollbarAdapter(state)
        else -> return
    }
    VerticalScrollbar(adapter, modifier, style = PAGE_SCROLLBAR)
}

private val PAGE_SCROLLBAR = ScrollbarStyle(
    minimalHeight = 48.dp,
    thickness = 7.dp,
    shape = RoundedCornerShape(4.dp),
    hoverDurationMillis = 250,
    unhoverColor = Color.White.copy(alpha = 0.16f),
    hoverColor = Color.White.copy(alpha = 0.38f),
)

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
