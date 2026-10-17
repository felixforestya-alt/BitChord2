package com.music.bitchord.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.layout.onSizeChanged
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.playback.PlaybackPosition
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import com.music.bitchord.ui.icons.BitChordIcons
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Where the home feed's "now playing" card is, in window pixels — so the one player that floats
 * over the page can sit exactly on it while it is on screen, and peel away from it as it scrolls
 * off. [present] is false whenever the card is not composed: another page, or scrolled far away.
 */
@Stable
internal class DesktopHeroAnchor {
    var present by mutableStateOf(false)
    var left by mutableFloatStateOf(0f)
    var top by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(0f)
    var height by mutableFloatStateOf(0f)

    /** How much of the card the page still shows, 0 to 1 — clipped by the top of the feed. */
    var visible by mutableFloatStateOf(0f)
}

/**
 * The head of the home feed: a greeting for the time of day in a slowly shimmering gradient, the
 * date, and — with the player placed at the top and something playing — the room the "now
 * playing" card occupies. The card itself is drawn by [DesktopMorphingPlayer], over the page.
 */
@Composable
internal fun DesktopHomeHeader(
    song: Song?,
    heroAnchor: DesktopHeroAnchor?,
    /** The card drawn in the feed itself, scrolling with it — when the player is not the one floating over it. */
    inlineCard: (@Composable (Modifier) -> Unit)? = null,
) {
    val palette = rememberVividPalette(song?.thumbnailUrl)
    val accent by animateColorAsState(if (song != null) palette.vivid else Color(0xFF7C6CFF), tween(1_200), label = "homeAccent")
    // The light runs across the greeting once as it appears, then every twenty seconds — not
    // without end: a sweep that never stops redraws the whole window on every frame of the
    // display for as long as Home is open, with nothing else on it moving.
    val sweepAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            sweepAnim.snapTo(0f)
            sweepAnim.animateTo(1f, tween(4_000, easing = LinearEasing))
            delay(20_000)
        }
    }
    val sweep = sweepAnim.value
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(900, easing = EASE_OUT_EXPO)) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 18.dp)
            .graphicsLayer {
                alpha = entrance.value
                translationY = (1f - entrance.value) * 30.dp.toPx()
            },
    ) {
        Text(
            greeting(),
            style = TextStyle(
                brush = Brush.linearGradient(
                    listOf(Color.White, lerpTo(accent, 0.25f), accent.hueShifted(40f, 1f), Color.White),
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
        Text(
            LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(appLocale()))
                .replaceFirstChar { it.titlecase(appLocale()) },
            color = DesktopSecondary,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (song != null && heroAnchor != null) {
            // Room for the card, and where it is: the floating player reads this to sit on it.
            Box(
                Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .height(HERO_HEIGHT)
                    .onGloballyPositioned { layout ->
                        val at = layout.positionInWindow()
                        heroAnchor.left = at.x
                        heroAnchor.top = at.y
                        heroAnchor.width = layout.size.width.toFloat()
                        heroAnchor.height = layout.size.height.toFloat()
                        val shown = layout.boundsInWindow().height
                        heroAnchor.visible = if (layout.size.height > 0) (shown / layout.size.height).coerceIn(0f, 1f) else 0f
                        heroAnchor.present = true
                    },
            ) {
                // In the feed: as it leaves under the top edge it sinks back and dims, handing
                // over to the bar that rises from the bottom of the window.
                inlineCard?.invoke(
                    Modifier.fillMaxSize().graphicsLayer {
                        val v = heroAnchor.visible
                        val s = 0.9f + 0.1f * v
                        scaleX = s
                        scaleY = s
                        alpha = (v * 1.4f).coerceIn(0f, 1f)
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    },
                )
            }
            DisposableEffect(heroAnchor) {
                onDispose {
                    heroAnchor.present = false
                    heroAnchor.visible = 0f
                }
            }
        }
    }
}

/**
 * The player at the top of the window: on the home feed, the big "now playing" card where the feed
 * puts it; scroll the card up past the top and it peels off and shrinks into the floating glass
 * bar, which then stays pinned while the page scrolls under it. Every other page gets the bar.
 *
 * One element, not two crossfading: its position, size and corner are interpolated between the
 * card's place and the bar's, the card's colour gives way to clear glass, and the card's contents
 * hand over to the bar's halfway through.
 */
@Composable
internal fun DesktopMorphingPlayer(
    anchor: DesktopHeroAnchor,
    song: Song,
    isPlaying: Boolean,
    onOpenPlayer: () -> Unit,
    /** The card's own transport, seek bar and volume. */
    transport: @Composable () -> Unit,
    extras: DesktopHeroExtras,
    /** The feed's wheel, for a wheel turned over the card. */
    wheel: DesktopSmoothWheel,
    bar: @Composable (Modifier) -> Unit,
) {
    val palette = rememberVividPalette(song.thumbnailUrl)
    val accent by animateColorAsState(palette.vivid, tween(1_200), label = "morphAccent")
    val density = LocalDensity.current
    // Arrives the way a drop forms: swelling into shape, with no bounce.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 220f)) }
    // Where the morph stands, worked out while laying out and read while drawing: scrolling moves
    // the pane, resizes it and redraws it, and recomposes nothing at all.
    val morph = remember { MorphState() }
    // The drop: stretched along the way it is moving, by how fast, and rounding back once it
    // stops — critically damped, so it settles without a wobble.
    val flow = remember { Animatable(0f) }
    LaunchedEffect(morph) {
        var last = morph.e
        androidx.compose.runtime.snapshotFlow { morph.e }.collectLatest { e ->
            val moved = e - last
            last = e
            if (moved == 0f) return@collectLatest
            flow.snapTo((flow.value + moved * 7f).coerceIn(-1f, 1f))
            flow.animateTo(0f, spring(dampingRatio = 1f, stiffness = 140f))
        }
    }

    // The card and the bar each keep their own size the whole way: only the pane around them
    // changes, so neither is measured again on any frame of the morph.
    val cardWidth = with(density) { anchor.width.toDp() }
    val cardHeight = with(density) { anchor.height.toDp() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { morph.origin = it.positionInWindow() },
    ) {
        val barWidth = (maxWidth - 32.dp).coerceAtMost(MORPH_BAR_WIDTH_MAX)
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val g = morph.compute(anchor, constraints.maxWidth.toFloat(), this)
                    if (g == null) {
                        // Below the page and then some: not laid out, not drawn.
                        return@layout layout(0, 0) {}
                    }
                    val placeable = measurable.measure(
                        androidx.compose.ui.unit.Constraints.fixed(g.width.roundToInt().coerceAtLeast(1), g.height.roundToInt().coerceAtLeast(1)),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(g.left.roundToInt(), g.top.roundToInt())
                    }
                }
                .graphicsLayer {
                    val a = appear.value
                    alpha = a.coerceIn(0f, 1f)
                    translationY = (1f - a) * -16.dp.toPx()
                    val grow = 0.85f + 0.15f * a
                    val f = kotlin.math.abs(flow.value)
                    scaleX = grow * (1f - f * 0.04f)
                    scaleY = grow * (1f + f * 0.08f)
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (flow.value >= 0f) 0f else 1f)
                    shadowElevation = (8f + (1f - morph.e) * 10f) * density.density
                    shape = RoundedCornerShape(morph.cornerPx(density.density).toDp())
                    clip = true
                }
                .desktopLiquidGlass(
                    26.dp,
                    tint = Color.Black.copy(alpha = 0.46f),
                    blurRadius = 8.dp,
                    // Halfway it is most a lens: it bends and magnifies what is under it hardest while
                    // it changes shape, the way a drop of water does, and calms as it settles.
                    cornerOf = { with(density) { morph.cornerPx(density.density).toDp() } },
                    lensOf = { sin(morph.e * Math.PI.toFloat()) },
                ) {
                    desktopBarGlass(RoundedCornerShape(26.dp))
                }
                .smoothWheel(wheel),
        ) {
            // The card's colour, fading to clear glass as it becomes the bar.
            HeroBackground(
                song,
                accent,
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = 1f - morph.e
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                },
            )
            // As the card's colour goes, the bar's comes — the cover's own, or none for clear glass.
            val tinted by DesktopStyleSettings.barTinted.collectAsState()
            if (tinted) {
                Box(
                    Modifier.fillMaxSize().background(desktopBarTint(accent)).graphicsLayer {
                        alpha = morph.e
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                    },
                )
            }
            // The card's contents, at the card's own size, fading out over the first half.
            HeroContent(
                song = song,
                isPlaying = isPlaying,
                onOpenPlayer = onOpenPlayer,
                transport = transport,
                extras = extras,
                accent = accent,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // Out of the way of the pointer once it has faded: invisible, it still took the
                    // clicks meant for the bar over it.
                    .offset { if (morph.e < 0.5f) IntOffset.Zero else IntOffset(OUT_OF_REACH, 0) }
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(cardWidth, cardHeight)
                    .graphicsLayer { alpha = (1f - morph.e * 2f).coerceIn(0f, 1f) },
            )
            // The bar's, at the bar's own size, fading in over the second.
            bar(
                Modifier
                    .align(Alignment.Center)
                    // Likewise the bar while it is still the card: its seek bar, unseen, lay across
                    // the card's lyrics, and a click on the words moved the song.
                    .offset { if (morph.e >= 0.5f) IntOffset.Zero else IntOffset(OUT_OF_REACH, 0) }
                    .wrapContentSize(Alignment.Center, unbounded = true)
                    .requiredSize(barWidth, MORPH_BAR_HEIGHT)
                    .graphicsLayer { alpha = ((morph.e - 0.5f) * 2f).coerceIn(0f, 1f) },
            )
        }
    }
}

/** The morph's geometry, kept out of composition. */
@Stable
private class MorphState {
    var origin by mutableStateOf(Offset.Zero)
    var e by mutableFloatStateOf(0f)
    private var height = 0f

    class Geometry(val left: Float, val top: Float, val width: Float, val height: Float)

    fun compute(anchor: DesktopHeroAnchor, maxWidth: Float, d: androidx.compose.ui.unit.Density): Geometry? = with(d) {
        val pinTop = 12.dp.toPx()
        val barHeight = MORPH_BAR_HEIGHT.toPx()
        val barWidth = min(maxWidth - 32.dp.toPx(), MORPH_BAR_WIDTH_MAX.toPx())
        val barLeft = (maxWidth - barWidth) / 2f
        val onCard = anchor.present
        val cardTop = anchor.top - origin.y
        val cardLeft = anchor.left - origin.x
        // How far the card has been pushed past the pin, over three quarters of its height.
        val progress = if (!onCard) 1f else ((pinTop - cardTop) / (anchor.height * 0.75f)).coerceIn(0f, 1f)
        val eased = FastOutSlowInEasing.transform(progress)
        e = eased
        val top = if (!onCard) pinTop else max(cardTop, pinTop)
        if (top > 4_000f) return null
        val width = if (!onCard) barWidth else lerpF(anchor.width, barWidth, eased)
        height = if (!onCard) barHeight else lerpF(anchor.height, barHeight, eased)
        Geometry(
            left = if (!onCard) barLeft else lerpF(cardLeft, barLeft, eased),
            top = top,
            width = width,
            height = height,
        )
    }

    /** The corner, rounding all the way into a pill as it becomes the drop. */
    fun cornerPx(density: Float): Float = lerpF(26f * density, height / 2f, e)
}

private val MORPH_BAR_HEIGHT = 76.dp

/** Far enough off to the side that nothing placed there can be pointed at. */
private const val OUT_OF_REACH = 100_000
private val MORPH_BAR_WIDTH_MAX = 1080.dp

/**
 * The home feed's "now playing" card on its own, for when it scrolls with the feed: the colour of
 * the cover, the track, and the controls a bar would have.
 */
@Composable
internal fun DesktopHeroCard(
    song: Song,
    isPlaying: Boolean,
    onOpenPlayer: () -> Unit,
    transport: @Composable () -> Unit,
    extras: DesktopHeroExtras,
    modifier: Modifier = Modifier,
) {
    val palette = rememberVividPalette(song.thumbnailUrl)
    val accent by animateColorAsState(palette.vivid, tween(1_200), label = "heroAccent")
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .graphicsLayer {
                shadowElevation = 18.dp.toPx()
                this.shape = shape
                clip = true
            },
    ) {
        HeroBackground(song, accent, Modifier.fillMaxSize())
        HeroContent(song, isPlaying, onOpenPlayer, transport, extras, accent, Modifier.fillMaxSize())
    }
}

@Composable
private fun HeroBackground(song: Song, accent: Color, modifier: Modifier) {
    // Blurred once and stretched — a live blur here was redone on every frame of the morph.
    val art = rememberBlurredArtwork(song.thumbnailUrl, edge = 64, sigma = 4f)
    Box(modifier.background(Brush.linearGradient(listOf(accent.hueShifted(0f, 0.45f), accent.hueShifted(-30f, 0.16f), Color(0xFF0C0C10))))) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = 0.55f
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            },
        ) {
            art?.let { drawVideoFrame(it, crop = true) }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.50f)))))
    }
}

/** What the home card needs beyond the track: its words, the like, the way to the queue. */
@Stable
internal class DesktopHeroExtras(
    val lyricLines: List<LyricLine>?,
    val position: PlaybackPosition,
    val liked: Boolean,
    val queueOpen: Boolean,
    val onToggleLike: () -> Unit,
    val onOpenQueue: () -> Unit,
)

@Composable
private fun HeroContent(
    song: Song,
    isPlaying: Boolean,
    onOpenPlayer: () -> Unit,
    transport: @Composable () -> Unit,
    extras: DesktopHeroExtras,
    accent: Color,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered) 1f else 0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "heroLift")
    val visualizer by DesktopStyleSettings.heroVisualizer.collectAsState()
    val showLyrics by DesktopStyleSettings.heroLyrics.collectAsState()
    val visualizerNames = DesktopVisualizerStyle.entries.associateWith { it.label() }
    Box(
        modifier
            .warmsPlayer()
            .hoverable(interaction)
            .clickable(onClick = onOpenPlayer)
            .pointerHoverIcon(PointerIcon.Hand),
    ) {
        Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(158.dp)
                    .graphicsLayer {
                        shadowElevation = 16.dp.toPx()
                        this.shape = RoundedCornerShape(14.dp)
                        clip = true
                        rotationZ = -lift * 2f
                        scaleX = 1f + lift * 0.03f
                        scaleY = 1f + lift * 0.03f
                    },
            ) {
                DesktopArtwork(song.thumbnailUrl.artworkAt(300) ?: song.thumbnailUrl, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxWidth().padding(end = 56.dp)) {
                    Column {
                        Text(
                            DesktopStrings["d_home_now_playing", "NOW PLAYING"],
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )
                        AnimatedContent(
                            targetState = song,
                            contentKey = { it.videoId },
                            transitionSpec = {
                                (slideInVertically(tween(500, easing = EASE_OUT_EXPO)) { it / 2 } + fadeIn(tween(400))) togetherWith
                                    (slideOutVertically(tween(300)) { -it / 2 } + fadeOut(tween(200)))
                            },
                            label = "heroTrack",
                        ) { current ->
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(current.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Text("  ·  " + current.artist, color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 3.dp))
                            }
                        }
                    }
                }
                // The middle of the card, the whole width of it: the visualiser, and over it the
                // line being sung, centred, with its translation and romanization at either side.
                HeroStage(
                    song = song,
                    style = visualizer,
                    lyricLines = extras.lyricLines.takeIf { showLyrics },
                    position = extras.position,
                    isPlaying = isPlaying,
                    accent = accent,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { transport() }
                    HeroSmallButton(
                        if (extras.liked) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                        DesktopStrings["d_like", "Like"],
                        tint = if (extras.liked) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.8f),
                        onClick = extras.onToggleLike,
                    )
                    HeroSmallButton(
                        BitChordIcons.LyricsQuote,
                        DesktopStrings["lyrics", "Lyrics"],
                        tint = if (showLyrics) Color.White else Color.White.copy(alpha = 0.5f),
                        onClick = { DesktopStyleSettings.setHeroLyrics(!showLyrics) },
                    )
                    HeroSmallButton(
                        Icons.Rounded.AutoAwesome,
                        DesktopStrings["d_visualizer", "Visualiser"] + ": " + visualizerNames.getValue(visualizer),
                        tint = if (visualizer != DesktopVisualizerStyle.NONE) Color.White else Color.White.copy(alpha = 0.5f),
                        onClick = { DesktopStyleSettings.setHeroVisualizer(visualizer.next()) },
                    )
                    HeroSmallButton(
                        BitChordIcons.Queue,
                        DesktopStrings["queue", "Queue"],
                        tint = if (extras.queueOpen) Color.White else Color.White.copy(alpha = 0.8f),
                        onClick = extras.onOpenQueue,
                    )
                }
            }
        }
        // Up into the player, in the corner.
        Box(Modifier.align(Alignment.TopEnd).padding(14.dp)) {
            HeroRoundButton(Icons.Rounded.KeyboardArrowUp, filled = false, onClick = onOpenPlayer)
        }
    }
}

/**
 * The card's middle: its own visualiser, and over it the line being sung. While there is a line
 * the visualiser sinks into a blur behind it, and each new line arrives zooming in out of focus.
 */
@Composable
private fun HeroStage(
    song: Song,
    style: DesktopVisualizerStyle,
    lyricLines: List<LyricLine>?,
    position: PlaybackPosition,
    isPlaying: Boolean,
    accent: Color,
    modifier: Modifier,
) {
    // Redraws the visualiser on every frame of the display while something plays.
    val tick = rememberFrameClock(isPlaying)
    var area by remember { mutableStateOf(IntSize.Zero) }
    val timed = remember(lyricLines) { lyricLines?.takeIf { list -> list.any { it.timeMs > 0 } }.orEmpty() }
    val indexState = if (timed.isEmpty()) null else rememberLyricIndex(timed, position, isPlaying)
    val intro = if (timed.isEmpty()) "" else rememberIntroPhrase(timed)
    // The lyrics panel's own translation and romanization, line for line with these lyrics.
    val versions = com.music.bitchord.ui.player.rememberLyricsVersions(song.videoId, timed.ifEmpty { null })
    val blur by animateFloatAsState(if (indexState != null) 1f else 0f, tween(600), label = "heroStageBlur")
    // Faded out at every edge rather than cut: a blur stops dead at the bounds of what it blurs,
    // and that was the hard band down the sides.
    Box(modifier.onSizeChanged { area = it }, contentAlignment = Alignment.Center) {
        DesktopVisualizer(
            style = style,
            clock = { tick.floatValue },
            accent = accent,
            // The card is wide and low: the ring and the particles go round an ellipse that fills
            // it, not a small circle in its middle.
            disc = { Offset(area.width / 2f, area.height / 2f) to area.height * 0.30f },
            focus = { Rect(0f, 0f, area.width.toFloat(), area.height.toFloat()) },
            squash = (area.width * 0.40f / (area.height * 0.30f).coerceAtLeast(1f)).coerceAtLeast(1f),
            modifier = Modifier.fillMaxSize().softEdges().graphicsLayer {
                val b = blur * 14f
                renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
                alpha = 1f - blur * 0.45f
            },
        )
        if (indexState != null) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(start = 8.dp)) {
                com.music.bitchord.ui.player.LyricsRomanizationButton(versions, enabled = true)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = indexState.value,
                transitionSpec = {
                    (fadeIn(tween(380)) + scaleIn(tween(520, easing = EASE_OUT_EXPO), initialScale = 1.3f)) togetherWith
                        (fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 0.85f))
                },
                label = "heroLyric",
            ) { shown ->
                // Out of focus as it arrives, sharpening as it settles — with room around it for
                // the blur to spread into instead of being cut at the words' own edges.
                val focus = remember { Animatable(0f) }
                LaunchedEffect(Unit) { focus.animateTo(1f, tween(480, easing = EASE_OUT_EXPO)) }
                Box(
                    Modifier
                        .graphicsLayer {
                            val b = (1f - focus.value) * 12f
                            renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DesktopSungLine(
                            timed = timed,
                            index = shown,
                            intro = intro,
                            position = position,
                            isPlaying = isPlaying,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 32.sp,
                                lineHeight = 38.sp,
                                fontWeight = FontWeight.Bold,
                                shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 14f),
                            ),
                            textAlign = TextAlign.Center,
                        )
                        // The translation or romanization of this line, small beneath it.
                        val under = versions.subLines?.getOrNull(shown)?.text?.takeIf { it.isNotBlank() }
                        if (under != null) {
                            Text(
                                under,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 19.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
            }
            Box(Modifier.padding(end = 8.dp)) {
                com.music.bitchord.ui.player.LyricsTranslationButton(versions, enabled = true)
            }
        }
    }
}

/** Fades whatever is drawn here to nothing towards all four edges. */
private fun Modifier.softEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, 0.10f to Color.Black, 0.90f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.18f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun HeroSmallButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val wash by animateFloatAsState(if (hovered) 0.14f else 0f, tween(140), label = "heroSmallWash")
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = wash))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun HeroRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.12f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "heroButton")
    Box(
        Modifier
            .size(if (filled) 54.dp else 44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.16f))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = { (scaleIn(tween(200)) + fadeIn(tween(150))) togetherWith (scaleOut(tween(150)) + fadeOut(tween(110))) },
            label = "heroIcon",
        ) { glyph ->
            Icon(glyph, null, tint = if (filled) Color.Black else Color.White, modifier = Modifier.size(if (filled) 30.dp else 26.dp))
        }
    }
}

/**
 * Colour that moves behind the main window: soft lights in the playing cover's hues drifting
 * slowly under a dark veil, so the page stays readable and the window feels alive. Redrawn at
 * about twenty frames a second — it moves slowly, and it is behind everything.
 */
@Composable
internal fun DesktopLivingBackdrop(coverUrl: String?, modifier: Modifier = Modifier) {
    if (!DesktopFrameProfiler.on("living")) return
    val palette = rememberVividPalette(coverUrl)
    val accent by animateColorAsState(if (coverUrl != null) palette.vivid else Color(0xFF5B4BFF), tween(2_000), label = "livingAccent")
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            delay(50)
            time.floatValue = (System.nanoTime() - start) / 1_000_000_000f
        }
    }
    Canvas(modifier) {
        val t = time.floatValue
        val level = DesktopAudioAnalyzer.levelAt(System.nanoTime())
        val lights = listOf(
            accent.hueShifted(0f, 0.85f),
            accent.hueShifted(45f, 0.7f),
            accent.hueShifted(-50f, 0.65f),
            accent.hueShifted(170f, 0.45f),
        )
        lights.forEachIndexed { i, colour ->
            val cx = size.width * (0.5f + 0.42f * sin(t * (0.045f + i * 0.011f) + i * 1.9f))
            val cy = size.height * (0.4f + 0.40f * cos(t * (0.037f + i * 0.009f) + i * 2.6f))
            val radius = size.maxDimension * (0.42f + 0.06f * sin(t * 0.3f + i) + level * 0.05f)
            drawCircle(
                Brush.radialGradient(
                    0f to colour.copy(alpha = 0.34f),
                    0.6f to colour.copy(alpha = 0.10f),
                    1f to Color.Transparent,
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
                blendMode = BlendMode.Screen,
            )
        }
        // The veil that keeps text on the page readable.
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.35f),
                1f to Color.Black.copy(alpha = 0.62f),
            ),
        )
    }
}

@Composable
private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when (hour) {
        in 5..11 -> DesktopStrings["d_greeting_morning", "Good morning"]
        in 12..17 -> DesktopStrings["d_greeting_afternoon", "Good afternoon"]
        in 18..22 -> DesktopStrings["d_greeting_evening", "Good evening"]
        else -> DesktopStrings["d_greeting_night", "Good night"]
    }
}

private fun appLocale(): Locale = Locale.forLanguageTag(DesktopStrings.resolvedTag())

private fun lerpTo(colour: Color, towardsWhite: Float): Color = androidx.compose.ui.graphics.lerp(colour, Color.White, towardsWhite)

private fun lerpF(from: Float, to: Float, t: Float): Float = from + (to - from) * t

/**
 * Brings [content] up from the bottom edge like a drop of water rising: drawn out tall as it moves,
 * rounding into shape as it arrives, settling without a bounce. Going, it sinks back into the edge.
 * Not composed at all while fully away.
 */
@Composable
internal fun DesktopBubbleIn(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val start = if (visible) 1f else 0f
    val rise = remember { Animatable(start) }
    val form = remember { Animatable(start) }
    LaunchedEffect(visible) {
        if (visible) {
            coroutineScope {
                // Up on a soft, unhurried spring; it takes its full round shape a beat behind,
                // drawn out tall while it rises and settling round as it arrives — no bounce.
                launch { rise.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 170f)) }
                launch { form.animateTo(1f, spring(dampingRatio = 1f, stiffness = 120f)) }
            }
        } else {
            coroutineScope {
                launch { form.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
                launch { rise.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
            }
        }
    }
    val shown by remember { androidx.compose.runtime.derivedStateOf { rise.value > 0.001f } }
    if (!shown && !visible) return
    Box(
        modifier.graphicsLayer {
            val r = rise.value
            val f = form.value
            translationY = (1f - r) * (size.height + 48.dp.toPx())
            scaleX = 0.55f + 0.45f * f
            scaleY = (0.55f + 0.45f * f) * (1f + (r - f).coerceAtLeast(0f) * 0.18f)
            alpha = (r * 1.6f).coerceIn(0f, 1f)
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        },
    ) { content() }
}

/**
 * The bottom bar, out by as much as [reveal] says (1 when null) and in step with it: it rises from
 * the window's edge as the home card scrolls away and sinks as it comes back, rounding into shape
 * as it arrives. What it shows follows [reveal] through a short, critically damped glide, so a
 * jump — another page, a track starting — eases rather than snaps, and a scroll stays exact.
 */
@Composable
internal fun DesktopScrollLinkedBar(
    visible: Boolean,
    reveal: (() -> Float)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shown = remember { mutableFloatStateOf(0f) }
    // How fast it is rising or sinking, for the drop's stretch.
    val stretch = remember { mutableFloatStateOf(0f) }
    val target by androidx.compose.runtime.rememberUpdatedState(visible)
    val revealNow by androidx.compose.runtime.rememberUpdatedState(reveal)
    LaunchedEffect(Unit) {
        fun goal() = if (target) (revealNow?.invoke() ?: 1f) else 0f
        var last = 0L
        while (true) {
            if (kotlin.math.abs(goal() - shown.floatValue) <= 0.001f) {
                shown.floatValue = goal()
                stretch.floatValue = 0f
                last = 0L
                // At rest until the goal moves: no frames asked for while nothing changes.
                androidx.compose.runtime.snapshotFlow { goal() }.first { kotlin.math.abs(it - shown.floatValue) > 0.001f }
                continue
            }
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 120f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                val k = 1f - kotlin.math.exp(-dt / BAR_GLIDE_SECONDS)
                val step = (goal() - shown.floatValue) * k
                shown.floatValue += step
                val speed = if (dt > 0f) step / dt else 0f
                stretch.floatValue += ((speed * 0.025f).coerceIn(-0.14f, 0.14f) - stretch.floatValue) * 0.35f
            }
        }
    }
    val gone by remember { androidx.compose.runtime.derivedStateOf { shown.floatValue < 0.001f } }
    if (gone && !visible) return
    Box(
        modifier
            // Out of the pointer's way while it is away.
            .offset { if (shown.floatValue < 0.02f) IntOffset(0, 100_000) else IntOffset.Zero }
            .graphicsLayer {
                val r = shown.floatValue
                translationY = (1f - r) * (size.height + 40.dp.toPx())
                val s = 0.9f + 0.1f * r
                val f = kotlin.math.abs(stretch.floatValue)
                // Drawn out tall while it moves, round once it rests: a drop rising.
                scaleX = s * (1f - f * 0.5f)
                scaleY = s * (1f + f)
                alpha = (r * 1.6f).coerceIn(0f, 1f)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            },
    ) { content() }
}

private const val BAR_GLIDE_SECONDS = 0.06f

/** The card's height on the home feed. */
internal val HERO_HEIGHT = 252.dp

private val EASE_OUT_EXPO = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
