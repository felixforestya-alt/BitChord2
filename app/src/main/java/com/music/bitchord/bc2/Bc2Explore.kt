package com.music.bitchord.bc2

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.cardEntrance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Explore as BitChord²'s desktop has it, for a phone: a big shimmering heading, then each of
 * YouTube's groups as a grid of cards — the category's cover full-bleed under a wash of its own
 * colour, its name bottom-left — that ripple in as the page arrives. Categories carry no artwork
 * of their own; each cover is found from the shelves the category opens, once, and kept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Bc2ExploreScreen(
    state: UiState<List<MoodGenreSection>>,
    gridState: LazyGridState,
    onCategoryClick: (MoodGenre) -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    contentPadding: PaddingValues,
) {
    val covers = rememberExploreCovers(state)
    PullToRefresh(refreshing = refreshing, onRefresh = onRefresh, state = pullState) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(156.dp),
            state = gridState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "explore-title") { ExploreHeading() }
            when (state) {
                is UiState.Loading -> itemsIndexed(List(10) { it }, key = { _, i -> "skeleton$i" }) { index, _ ->
                    ExplorePlaceholderCard(index)
                }
                is UiState.Error -> item(span = { GridItemSpan(maxLineSpan) }, key = "explore-error") {
                    Column(Modifier.padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onRetry) { Text(bc2("Повторить", "Retry")) }
                    }
                }
                is UiState.Success -> {
                    var running = 0
                    state.data.forEach { section ->
                        val items = section.items.distinctBy(::exploreKey)
                        if (items.isEmpty()) return@forEach
                        if (section.title.isNotBlank()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "section:${section.title}") {
                                Text(
                                    section.title,
                                    color = Color.White,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp),
                                )
                            }
                        }
                        val start = running
                        itemsIndexed(items, key = { _, item -> "${section.title}|${exploreKey(item)}" }) { index, item ->
                            ExploreCard(item, cover = item.thumbnailUrl ?: covers[exploreKey(item)], index = start + index) {
                                onCategoryClick(item)
                            }
                        }
                        running += items.size
                    }
                }
            }
        }
    }
}

/**
 * The categories' covers, by [exploreKey]: those found before, from disk, at once; the missing
 * ones looked up a few at a time and applied in batches, not one recomposition per cover.
 */
@Composable
private fun rememberExploreCovers(state: UiState<List<MoodGenreSection>>): Map<String, String> {
    val covers = remember {
        mutableStateMapOf<String, String>().apply {
            Bc2Prefs.string(KEY_COVERS).lineSequence().forEach { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) put(line.substring(0, tab), line.substring(tab + 1))
            }
        }
    }
    val sections = (state as? UiState.Success)?.data
    LaunchedEffect(sections) {
        val missing = sections.orEmpty().flatMap { it.items }
            .filter { it.thumbnailUrl == null && exploreKey(it) !in covers }
            .distinctBy(::exploreKey)
        if (missing.isEmpty()) return@LaunchedEffect
        val found = java.util.concurrent.ConcurrentHashMap<String, String>()
        val limiter = Semaphore(4)
        fun apply() {
            if (found.isEmpty()) return
            covers.putAll(found)
            found.clear()
        }
        kotlinx.coroutines.coroutineScope {
            val ticker = launch {
                while (true) {
                    delay(400)
                    apply()
                }
            }
            missing.map { item ->
                launch {
                    limiter.withPermit { YtMusicRepository.moodGenreArtwork(item.browseId, item.params).getOrNull() }
                        ?.let { found[exploreKey(item)] = it }
                }
            }.forEach { it.join() }
            ticker.cancel()
        }
        apply()
        Bc2Prefs.putString(KEY_COVERS, covers.entries.joinToString("\n") { "${it.key}\t${it.value}" })
    }
    return covers
}

private fun exploreKey(item: MoodGenre) = "${item.browseId}|${item.params.orEmpty()}"

private const val KEY_COVERS = "explore_covers"

@Composable
private fun ExploreHeading() {
    val shimmer = rememberInfiniteTransition(label = "exploreHeading")
    val sweep by shimmer.animateFloat(0f, 1f, infiniteRepeatable(tween(7_000, easing = LinearEasing), RepeatMode.Restart), label = "exploreSweep")
    Column(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
        Text(
            bc2("Обзор", "Explore"),
            style = TextStyle(
                brush = Brush.linearGradient(
                    listOf(Color.White, Color(0xFFB9A8FF), Color(0xFF7FE3D1), Color.White),
                    start = Offset(sweep * 1600f - 800f, 0f),
                    end = Offset(sweep * 1600f, 120f),
                    tileMode = TileMode.Mirror,
                ),
                fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 44.sp,
                letterSpacing = (-1.2).sp,
            ),
        )
        Text(bc2("Настроения, моменты и жанры", "Moods, moments and genres"), color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
    }
}

@Composable
private fun ExploreCard(item: MoodGenre, cover: String?, index: Int, onClick: () -> Unit) {
    val tone = remember(item.stripeColor, item.title) { exploreTone(item) }
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .cardEntrance(index)
            .fillMaxWidth()
            .aspectRatio(1.45f)
            .graphicsLayer {
                shadowElevation = 6f * density
                this.shape = shape
                clip = true
                ambientShadowColor = tone
                spotShadowColor = tone
            }
            .background(lerp(tone, Color.Black, 0.55f))
            .clickable(onClick = onClick),
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover.artworkAt(EXPLORE_ART_PX) ?: cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.04f; scaleY = 1.04f },
            )
        } else {
            ExploreGlow(tone, index, Modifier.fillMaxSize())
        }
        // The category's own colour washed up from the bottom-left, so the name always reads.
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    0f to lerp(tone, Color.Black, 0.25f).copy(alpha = 0.92f),
                    0.55f to tone.copy(alpha = 0.35f),
                    1f to Color.Transparent,
                    start = Offset(0f, Float.POSITIVE_INFINITY),
                    end = Offset(Float.POSITIVE_INFINITY, 0f),
                ),
            ),
        )
        Box(Modifier.align(Alignment.TopStart).padding(12.dp).width(22.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(tone))
        Text(
            item.title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 10f)),
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 13.dp, vertical = 11.dp),
        )
        Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.08f), shape))
    }
}

/** A card waiting for its cover: its own colour, softly moving, rather than a grey shimmer. */
@Composable
private fun ExploreGlow(tone: Color, index: Int, modifier: Modifier) {
    val drift = rememberInfiniteTransition(label = "exploreGlow")
    val t by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(3_200 + index % 5 * 300, easing = LinearEasing), RepeatMode.Reverse), label = "glowT")
    Box(
        modifier.background(
            Brush.radialGradient(
                listOf(lerp(tone, Color.White, 0.25f), tone, lerp(tone, Color.Black, 0.6f)),
                center = Offset(150f + t * 250f, 50f + t * 100f),
                radius = 420f,
            ),
        ),
    )
}

@Composable
private fun ExplorePlaceholderCard(index: Int) {
    val tone = PLACEHOLDER_TONES[index % PLACEHOLDER_TONES.size]
    Box(Modifier.cardEntrance(index).fillMaxWidth().aspectRatio(1.45f).clip(RoundedCornerShape(18.dp))) {
        ExploreGlow(tone.copy(alpha = 0.55f), index, Modifier.fillMaxSize())
    }
}

private fun exploreTone(item: MoodGenre): Color {
    val base = item.stripeColor?.let { Color(it.toInt()) } ?: PLACEHOLDER_TONES[(item.title.hashCode() and Int.MAX_VALUE) % PLACEHOLDER_TONES.size]
    // YouTube's colours are pastel; a little more depth keeps white type legible on them.
    return lerp(base, Color.Black, 0.12f)
}

private val PLACEHOLDER_TONES = listOf(
    Color(0xFF7C6CFF), Color(0xFFE0607E), Color(0xFF4FB3A9), Color(0xFFE39A4B),
    Color(0xFF5C8DEB), Color(0xFFB45FD1), Color(0xFF6CC070), Color(0xFFD4566B),
)

private const val EXPLORE_ART_PX = 480
