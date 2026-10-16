package com.music.bitchord.desktop

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.UiState
import com.music.bitchord.ui.components.cardEntrance
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.nio.file.Files

/**
 * Explore as it was last seen, on disk: the categories and the cover each one was given.
 *
 * Every category's cover is a request of its own — forty-odd of them — so a cold Explore was a
 * page of placeholders filling in over many seconds, every launch. From here it opens at once with
 * what it had, and only what is stale or missing is asked for again in the background.
 */
internal object DesktopExploreCache {

    class Snapshot(val sections: List<MoodGenreSection>, val savedAtMs: Long)

    private val file get() = DesktopMediaCache.directory.resolve("explore_v1.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(language: String): Snapshot? = runCatching {
        val root = json.parseToJsonElement(Files.readString(file)).jsonObject
        if (root["lang"]?.jsonPrimitive?.contentOrNull != language) return null
        val sections = root["sections"]!!.jsonArray.map { section ->
            val s = section.jsonObject
            MoodGenreSection(
                title = s["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                items = s["items"]!!.jsonArray.map { item ->
                    val i = item.jsonObject
                    MoodGenre(
                        title = i["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        browseId = i["browseId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        params = i["params"]?.jsonPrimitive?.contentOrNull,
                        thumbnailUrl = i["thumb"]?.jsonPrimitive?.contentOrNull,
                        stripeColor = i["stripe"]?.jsonPrimitive?.longOrNull,
                    )
                },
            )
        }
        Snapshot(sections, root["savedAt"]?.jsonPrimitive?.longOrNull ?: 0L)
    }.getOrNull()

    fun save(language: String, sections: List<MoodGenreSection>) {
        runCatching {
            val root = buildJsonObject {
                put("lang", language)
                put("savedAt", System.currentTimeMillis())
                put(
                    "sections",
                    buildJsonArray {
                        sections.forEach { section ->
                            add(
                                buildJsonObject {
                                    put("title", section.title)
                                    put(
                                        "items",
                                        buildJsonArray {
                                            section.items.forEach { item ->
                                                add(
                                                    buildJsonObject {
                                                        put("title", item.title)
                                                        put("browseId", item.browseId)
                                                        item.params?.let { put("params", it) }
                                                        item.thumbnailUrl?.let { put("thumb", it) }
                                                        item.stripeColor?.let { put("stripe", it) }
                                                    },
                                                )
                                            }
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
            }
            Files.createDirectories(file.parent)
            val partial = file.resolveSibling("explore_v1.json.partial")
            Files.writeString(partial, root.toString())
            Files.move(partial, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { DesktopTrackLog.log("explore cache: could not save: ${it.message}") }
    }

    /** [fresh] with each category's cover carried over from [known] where [fresh] has none. */
    fun carryCovers(fresh: List<MoodGenreSection>, known: List<MoodGenreSection>?): List<MoodGenreSection> {
        if (known == null) return fresh
        val covers = known.flatMap { it.items }.mapNotNull { item -> item.thumbnailUrl?.let { key(item) to it } }.toMap()
        return fresh.map { section ->
            section.copy(items = section.items.map { item -> if (item.thumbnailUrl == null) item.copy(thumbnailUrl = covers[key(item)]) else item })
        }
    }

    fun key(item: MoodGenre) = "${item.browseId}|${item.params.orEmpty()}"

    /** How long a saved Explore is shown without asking YouTube again. */
    const val FRESH_MS = 12L * 60 * 60 * 1000
}

/**
 * Explore, for a window: a big heading, then each of YouTube's groups as a grid of wide cards —
 * the category's cover full-bleed under a wash of its own colour, its name bottom-left — that lift
 * and zoom under the pointer and ripple in as the page arrives.
 */
@Composable
internal fun DesktopExplorePage(
    state: UiState<List<MoodGenreSection>>,
    gridState: LazyGridState,
    onCategoryClick: (MoodGenre) -> Unit,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(250.dp),
        state = gridState,
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = contentPadding.calculateTopPadding() + 18.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "explore-title") { ExploreHeading() }
        when (state) {
            is UiState.Loading -> itemsIndexed(List(12) { it }, key = { _, i -> "skeleton$i" }) { index, _ ->
                ExplorePlaceholderCard(index)
            }
            is UiState.Error -> item(span = { GridItemSpan(maxLineSpan) }, key = "explore-error") {
                Column(Modifier.padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, color = DesktopSecondary)
                    TextButton(onClick = onRetry) { Text(DesktopStrings["retry", "Retry"], color = DesktopAccent) }
                }
            }
            is UiState.Success -> {
                var running = 0
                state.data.forEach { section ->
                    val items = section.items.distinctBy { DesktopExploreCache.key(it) }
                    if (items.isEmpty()) return@forEach
                    if (section.title.isNotBlank()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "section:${section.title}") {
                            Text(
                                section.title,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                            )
                        }
                    }
                    val start = running
                    itemsIndexed(items, key = { _, item -> DesktopExploreCache.key(item) }) { index, item ->
                        ExploreCard(item, index = start + index, onClick = { onCategoryClick(item) })
                    }
                    running += items.size
                }
            }
        }
    }
}

@Composable
private fun ExploreHeading() {
    val shimmer = rememberInfiniteTransition(label = "exploreHeading")
    val sweep by shimmer.animateFloat(0f, 1f, infiniteRepeatable(tween(7_000, easing = LinearEasing), RepeatMode.Restart), label = "exploreSweep")
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(
            DesktopStrings["explore", "Explore"],
            style = TextStyle(
                brush = Brush.linearGradient(
                    listOf(Color.White, Color(0xFFB9A8FF), Color(0xFF7FE3D1), Color.White),
                    start = Offset(sweep * 1600f - 800f, 0f),
                    end = Offset(sweep * 1600f, 120f),
                    tileMode = TileMode.Mirror,
                ),
                fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 46.sp,
                letterSpacing = (-1.2).sp,
            ),
        )
        Text(DesktopStrings["d_explore_subtitle", "Moods, moments and genres"], color = DesktopSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun ExploreCard(item: MoodGenre, index: Int, onClick: () -> Unit) {
    val tone = remember(item.stripeColor, item.title) { exploreTone(item) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(
        if (hovered) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "exploreLift",
    )
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .cardEntrance(index)
            .fillMaxWidth()
            .aspectRatio(1.62f)
            .graphicsLayer {
                scaleX = 1f + lift * 0.035f
                scaleY = 1f + lift * 0.035f
                translationY = -lift * 4.dp.toPx()
                shadowElevation = (6f + lift * 22f) * density
                this.shape = shape
                clip = true
                ambientShadowColor = tone
                spotShadowColor = tone
            }
            .background(lerp(tone, Color.Black, 0.55f))
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
    ) {
        if (item.thumbnailUrl != null) {
            AsyncImage(
                model = item.thumbnailUrl.artworkAt(EXPLORE_ART_PX) ?: item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    // A slow push-in under the pointer.
                    val zoom = 1.04f + lift * 0.08f
                    scaleX = zoom
                    scaleY = zoom
                },
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
        Box(Modifier.align(Alignment.TopStart).padding(14.dp).width(26.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(tone))
        Text(
            item.title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 10f)),
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 14.dp),
        )
        Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.08f + lift * 0.18f), shape))
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
                center = Offset(200f + t * 300f, 60f + t * 120f),
                radius = 520f,
            ),
        ),
    )
}

@Composable
private fun ExplorePlaceholderCard(index: Int) {
    val tone = PLACEHOLDER_TONES[index % PLACEHOLDER_TONES.size]
    Box(Modifier.cardEntrance(index).fillMaxWidth().aspectRatio(1.62f).clip(RoundedCornerShape(20.dp))) {
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

private const val EXPLORE_ART_PX = 544
