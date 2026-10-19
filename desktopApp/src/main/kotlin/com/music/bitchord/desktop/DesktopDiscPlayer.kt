package com.music.bitchord.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The song/video pair the version switch moves between. */
internal class DesktopVersionSwitch(
    /** The other version of what is playing, once one has been found. */
    val alternate: Song?,
    /** Still looking for it. */
    val searching: Boolean,
    val onSwitch: () -> Unit,
)

/** What the middle of the disc player shows. */
internal enum class DiscCentre { NOW_PLAYING, LYRICS, QUEUE }

/**
 * The disc player: a full-window, late-90s "now playing." screen.
 *
 * A CD with the song's cover printed on it spins half off the left edge, the room is lit in the
 * cover's own colour, the artist's name drifts by enormous and faint behind everything, and a
 * visualiser that follows the real output moves behind the track. Everything is driven from one
 * big liquid-glass control deck along the bottom — the transport, the seek bar, the volume with
 * its mute, and every switch the player has — and the middle of the room is where the lyrics or
 * the queue come up when asked for.
 */
@Composable
internal fun DesktopDiscPlayer(
    song: Song,
    /** The song's own cover — for a music video, its song version's — which the disc always shows. */
    coverUrl: String?,
    isPlaying: Boolean,
    isLoading: Boolean,
    position: PlaybackPosition,
    durationMs: Long,
    hasPrevious: Boolean,
    hasNext: Boolean,
    shuffle: Boolean,
    /** 0 off, 1 all, 2 one. */
    repeat: Int,
    liked: Boolean,
    volume: Float,
    versionSwitch: DesktopVersionSwitch?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onCollapse: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: (() -> Unit)?,
    lyricsPanel: @Composable (Modifier) -> Unit,
    queuePanel: @Composable (Modifier) -> Unit,
    /** The track's lyrics, for the line being sung above the title; null or empty for none. */
    lyricLines: List<LyricLine>? = null,
    onOpenAudioOutput: () -> Unit = {},
    onListenTogether: () -> Unit = {},
    /** Back to the album, playlist or queue the session was started from. */
    onOpenPlaybackSource: () -> Unit = {},
) {
    // Laid out for a 1440 × 900 window and scaled to whatever the window is, so a big or a
    // high-resolution screen gets a bigger player rather than the same small one, off to a side.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val base = LocalDensity.current
        val scale = min(maxWidth.value / DESIGN_WIDTH, maxHeight.value / DESIGN_HEIGHT).coerceIn(0.78f, 1.9f)
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
            DiscPlayerLayout(
                song, coverUrl, isPlaying, isLoading, position, durationMs, hasPrevious, hasNext, shuffle,
                repeat, liked, volume, versionSwitch, onPlayPause, onNext, onPrevious, onSeek,
                onToggleShuffle, onCycleRepeat, onToggleLike, onVolumeChange, onCollapse, onOpenMenu,
                onOpenArtist, onOpenAlbum, lyricsPanel, queuePanel, lyricLines, onOpenAudioOutput, onListenTogether, onOpenPlaybackSource,
            )
        }
    }
}

@Composable
private fun DiscPlayerLayout(
    song: Song,
    coverUrl: String?,
    isPlaying: Boolean,
    isLoading: Boolean,
    position: PlaybackPosition,
    durationMs: Long,
    hasPrevious: Boolean,
    hasNext: Boolean,
    shuffle: Boolean,
    repeat: Int,
    liked: Boolean,
    volume: Float,
    versionSwitch: DesktopVersionSwitch?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onCollapse: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: (() -> Unit)?,
    lyricsPanel: @Composable (Modifier) -> Unit,
    queuePanel: @Composable (Modifier) -> Unit,
    lyricLines: List<LyricLine>?,
    onOpenAudioOutput: () -> Unit,
    onListenTogether: () -> Unit,
    onOpenPlaybackSource: () -> Unit,
) {
    val showDisc by DesktopStyleSettings.showDisc.collectAsState()
    val lyricsScale by DesktopStyleSettings.lyricsScale.collectAsState()
    val backdropText by DesktopStyleSettings.backdropText.collectAsState()
    val visualizerStyle by DesktopStyleSettings.visualizerStyle.collectAsState()
    val visualMode by DesktopStyleSettings.visualMode.collectAsState()
    val reduceAnimation by DesktopAppearanceSettings.reduceAnimation.collectAsState()

    // Only while the player is up: kept built behind the scenes it would otherwise decode a video
    // nobody sees.
    val wantsVideo = song.isVideo && visualMode == DesktopVisualMode.VIDEO && LocalPlayerSheetOpen.current
    val video = rememberDesktopMusicVideo(if (wantsVideo) song.videoId else null, position, isPlaying)
    val cover = coverUrl ?: song.thumbnailUrl

    val palette = rememberVividPalette(cover)
    val glow by animateColorAsState(palette.vivid, tween(1_400), label = "glow")
    val deep by animateColorAsState(palette.deep, tween(1_400), label = "deep")
    val ink by animateColorAsState(palette.ink, tween(1_400), label = "ink")

    // One clock for everything that moves on its own: read only in draw lambdas, so a frame
    // redraws what moves and recomposes nothing.
    val clock = remember { mutableFloatStateOf(0f) }
    val discAngle = remember { mutableFloatStateOf(0f) }
    val playing by rememberUpdatedState(isPlaying)
    // Still while composed ahead out of sight: nothing there to move, and no frames to ask for.
    val sheetShown = LocalPlayerSheetShown.current
    // And while the window is in the background (see AppActivity): the disc stops where it is.
    val quiet = com.music.bitchord.ui.AppActivity.quiet
    LaunchedEffect(reduceAnimation, sheetShown, quiet) {
        if (!sheetShown || quiet) return@LaunchedEffect
        var last = withFrameNanos { it }
        var velocity = 0f
        while (true) {
            com.music.bitchord.ui.awaitAmbientFrame { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                if (!reduceAnimation) clock.floatValue += dt
                // The disc spins up and coasts down like a real drive, never snaps.
                val target = if (playing) 360f / DISC_PERIOD_S else 0f
                velocity += (target - velocity) * min(1f, dt * if (playing) 1.6f else 0.9f)
                discAngle.floatValue = (discAngle.floatValue + velocity * dt) % 360f
            }
        }
    }

    // The entrance: the disc rolls in, the words follow, the deck rises.
    val entrance = remember { Animatable(0f) }
    // From when the sheet is shown — it may have been composed ahead, out of sight.
    val shown = LocalPlayerSheetShown.current
    val open = LocalPlayerSheetOpen.current
    val primeEntrance = LocalPlayerPrimeEntrance.current
    LaunchedEffect(shown, open, primeEntrance) {
        when {
            !shown -> entrance.snapTo(0f)
            // Being prepared out of sight: through the entrance as the priming plays it, then at rest.
            !open -> if (primeEntrance != null) {
                androidx.compose.runtime.snapshotFlow { primeEntrance.value }.collect { entrance.snapTo(it) }
            } else {
                entrance.snapTo(1f)
            }
            else -> {
                entrance.snapTo(0f)
                entrance.animateTo(1f, tween(DesktopStartupWarmup.paced(PLAYER_ENTRANCE_MS), easing = PLAYER_ENTRANCE_EASING))
                if (DesktopFrameProfiler.enabled) DesktopFrameProfiler.mark("entrance done")
            }
        }
    }

    var centre by remember { mutableStateOf(DiscCentre.NOW_PLAYING) }
    val backdrop = rememberDesktopGlassBackdrop()
    val density = LocalDensity.current

    CompositionLocalProvider(LocalDesktopGlassBackdrop provides backdrop) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val width = maxWidth
            val height = maxHeight
            val discSize = min(height.value * 0.94f, width.value * 0.6f).dp
            val discCentreX = width * 0.06f
            val stageStart = if (showDisc) width * 0.40f else width * 0.44f
            val stageWidth = width - stageStart - 40.dp
            val deckWidth = stageWidth.coerceAtMost(720.dp)
            // The deck's real height, once laid out: the stage above it ends where it begins.
            var deckMeasured by remember { mutableStateOf(236.dp) }
            val deckHeight = deckMeasured
            // Lined up under the words rather than centred in the space beside them.
            val deckX = stageStart
            val stageTop = 56.dp
            val stageBottom = deckHeight + 26.dp + 22.dp

            // ---- Everything the glass refracts --------------------------------------------
            Box(Modifier.fillMaxSize().glassSource(backdrop)) {
                // The room, lit in the cover's colour.
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                0f to glow.copy(alpha = 0.95f),
                                0.55f to lerp(glow, deep, 0.55f),
                                1f to deep,
                                center = Offset(size.width * 0.62f, size.height * 0.35f),
                                radius = size.maxDimension * 0.85f,
                            ),
                        )
                    },
                )
                // The cover itself, enormous and out of focus, for texture in the light.
                // Behind a playing video it would never be seen; not drawn at all then.
                if (DesktopFrameProfiler.on("ambient") && !(wantsVideo && video.frame != null)) {
                    AmbientArtwork(cover, Modifier.fillMaxSize().graphicsLayer { alpha = 0.38f; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha })
                }
                // The music video, full-bleed, when there is one and it is wanted.
                if (wantsVideo) {
                    DesktopVideoSurface(video, Modifier.fillMaxSize().graphicsLayer { alpha = 0.92f; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha })
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.10f),
                                0.45f to Color.Black.copy(alpha = 0.30f),
                                1f to Color.Black.copy(alpha = 0.55f),
                            ),
                        ),
                    )
                }
                if (backdropText && DesktopFrameProfiler.on("marquee")) {
                    GhostMarquee(
                        text = song.artist.ifBlank { song.title }.lowercase(),
                        color = if (wantsVideo) Color.White.copy(alpha = 0.07f) else ink.copy(alpha = 0.30f),
                        clock = { clock.floatValue },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // What moves behind the track.
                if (DesktopFrameProfiler.on("vis")) DesktopVisualizer(
                    style = visualizerStyle,
                    clock = { clock.floatValue },
                    accent = glow,
                    disc = {
                        with(density) {
                            Offset(discCentreX.toPx(), height.toPx() / 2f) to
                                (if (showDisc) discSize.toPx() / 2f else 0f)
                        }
                    },
                    focus = {
                        with(density) {
                            val top = stageTop.toPx()
                            val bottom = height.toPx() - stageBottom.toPx()
                            val mid = (top + bottom) / 2f
                            Rect(stageStart.toPx(), mid - 120.dp.toPx(), (stageStart + stageWidth).toPx(), mid + 120.dp.toPx())
                        }
                    },
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = if (centre == DiscCentre.NOW_PLAYING) 1f else 0.55f
                    },
                )
                if (DesktopFrameProfiler.on("spheres")) WireSpheres(clock = { clock.floatValue }, tint = Color.White, modifier = Modifier.fillMaxSize())
                if (DesktopFrameProfiler.on("scan")) Scanlines(Modifier.fillMaxSize())

                // The disc — or, with it off, the cover as a card. Always the song's cover.
                val discProgress = { entrance.value }
                AnimatedContent(
                    targetState = cover,
                    transitionSpec = {
                        (slideInHorizontally(tween(900, easing = EASE_OUT_EXPO)) { -it } + fadeIn(tween(500))) togetherWith
                            (slideOutHorizontally(tween(650, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(450)))
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        // The disc's centre sits just inside the left edge, so a little over half of it
                        // shows — the way it slides out of the frame in a broadcast ident.
                        .offset(x = if (showDisc) discCentreX - discSize / 2 else width * 0.07f)
                        .graphicsLayer {
                            val p = discProgress()
                            if (DesktopFrameProfiler.on("move-disc")) {
                                translationX = -(1f - p) * discSize.toPx() * 0.9f
                                rotationZ = -(1f - p) * 140f
                            }
                            if (DesktopFrameProfiler.on("entrance-alpha")) alpha = p
                        },
                    label = "disc",
                ) { art ->
                    if (showDisc) {
                        SpinningDisc(
                            artUrl = art,
                            angle = { discAngle.floatValue },
                            level = { DesktopAudioAnalyzer.levelAt(System.nanoTime()).also { clock.floatValue } },
                            glow = glow,
                            modifier = Modifier.size(discSize),
                        )
                    } else {
                        ArtworkCard(
                            artUrl = art,
                            clock = { clock.floatValue },
                            modifier = Modifier.size((height * 0.56f).coerceAtMost(width * 0.33f)),
                        )
                    }
                }

                // The mark, top left, as on a broadcast ident (the window's own buttons have the
                // top right).
                Text(
                    "BC².",
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    letterSpacing = (-1).sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = 26.dp),
                )
            }

            // ---- The middle of the room: the words, the lyrics or the queue ---------------------
            // Outside the glass's source, so the queue can stand on glass of its own: drawn inside
            // it, the pane would refract a layer that contains itself.
            Box(
                Modifier
                    .offset(x = stageStart)
                    .width(stageWidth)
                    .fillMaxHeight()
                    .padding(top = stageTop, bottom = stageBottom)
                    .graphicsLayer {
                        val p = ((entrance.value - 0.15f) / 0.85f).coerceIn(0f, 1f)
                        if (DesktopFrameProfiler.on("entrance-alpha")) alpha = p
                        if (DesktopFrameProfiler.on("move-stage")) translationY = (1f - p) * 60.dp.toPx()
                    },
            ) {
                AnimatedContent(
                    targetState = centre,
                    transitionSpec = {
                        (fadeIn(tween(420, delayMillis = 90)) + slideInVertically(tween(520, easing = EASE_OUT_EXPO)) { it / 8 }) togetherWith
                            (fadeOut(tween(220)) + slideOutVertically(tween(300)) { -it / 10 })
                    },
                    label = "centre",
                ) { showing ->
                    when (showing) {
                        DiscCentre.NOW_PLAYING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                            NowPlayingWords(
                                song = song,
                                coverUrl = cover,
                                compact = height < 700.dp,
                                lyricLines = lyricLines,
                                position = position,
                                isPlaying = isPlaying,
                                onOpenArtist = onOpenArtist,
                                onOpenAlbum = onOpenAlbum,
                                onOpenSource = onOpenPlaybackSource,
                            )
                        }
                        // As wide as the deck under it, so the lyrics' own buttons line up with it.
                        // The words as wide as the deck under them; the size control back at the
                        // far end of the stage, on a strip of its own above the first line, where
                        // it never sits on the words.
                        DiscCentre.LYRICS -> Box(Modifier.fillMaxSize()) {
                            val here = LocalDensity.current
                            Box(Modifier.width(deckWidth).fillMaxHeight().padding(top = 52.dp)) {
                                CompositionLocalProvider(
                                    LocalDensity provides Density(here.density, here.fontScale * lyricsScale),
                                    com.music.bitchord.ui.player.LocalLyricsFocusFraction provides 0.40f,
                                ) {
                                    lyricsPanel(Modifier.fillMaxSize())
                                }
                            }
                            LyricsScaleControl(
                                scale = lyricsScale,
                                onScale = DesktopStyleSettings::setLyricsScale,
                                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp),
                            )
                        }
                        // The deck's width and left edge, so the pane stands squarely on it.
                        DiscCentre.QUEUE -> Box(
                            Modifier
                                .width(deckWidth)
                                .fillMaxHeight()
                                // The deck's glass, corner and border, and its margins inside,
                                // so the two panes read as one set.
                                .desktopLiquidGlass(32.dp, tint = Color.Black.copy(alpha = 0.10f), blurRadius = 4.dp) {
                                    clip(RoundedCornerShape(32.dp)).background(Color.Black.copy(alpha = 0.5f))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(32.dp))
                                }
                                .clip(RoundedCornerShape(32.dp))
                                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                        ) {
                            CompositionLocalProvider(com.music.bitchord.ui.player.LocalQueueRowInset provides 10.dp) {
                                queuePanel(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }

            // ---- The deck: every control the player has, on glass -----------------------------
            ControlDeck(
                isPlaying = isPlaying,
                isLoading = isLoading,
                position = position,
                durationMs = durationMs,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                shuffle = shuffle,
                repeat = repeat,
                liked = liked,
                volume = volume,
                accent = glow,
                clock = { clock.floatValue },
                isVideo = song.isVideo,
                visualMode = visualMode,
                videoState = video.state.takeIf { wantsVideo },
                versionSwitch = versionSwitch,
                visualizerStyle = visualizerStyle,
                centre = centre,
                onCentre = { centre = if (centre == it) DiscCentre.NOW_PLAYING else it },
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onToggleLike = onToggleLike,
                onVolumeChange = onVolumeChange,
                onCollapse = onCollapse,
                onOpenMenu = onOpenMenu,
                onOpenAudioOutput = onOpenAudioOutput,
                onListenTogether = onListenTogether,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = deckX)
                    .padding(bottom = 26.dp)
                    .width(deckWidth)
                    .onSizeChanged { size ->
                        val measured = with(density) { size.height.toDp() }
                        if (measured > 0.dp && measured != deckMeasured) deckMeasured = measured
                    }
                    .graphicsLayer {
                        val p = ((entrance.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
                        if (DesktopFrameProfiler.on("move-deck")) translationY = (1f - p) * 160.dp.toPx()
                        if (DesktopFrameProfiler.on("entrance-alpha")) alpha = p
                    },
            )
        }
    }
}

// ---- The words -------------------------------------------------------------------------------

@Composable
private fun NowPlayingWords(
    song: Song,
    coverUrl: String?,
    compact: Boolean,
    lyricLines: List<LyricLine>?,
    position: PlaybackPosition,
    isPlaying: Boolean,
    onOpenArtist: () -> Unit,
    onOpenAlbum: (() -> Unit)?,
    onOpenSource: () -> Unit,
) {
    val display = MaterialTheme.typography.displayLarge.fontFamily
    Column(Modifier.padding(vertical = 18.dp)) {
        // Where the session came from, as the classic player captions it — a click goes back there.
        song.playbackSource?.takeIf { it.isNotBlank() }?.let { source ->
            Column(
                Modifier
                    .padding(bottom = 10.dp)
                    // The same capsule the artist and album light up in.
                    .pillLink(onOpenSource),
            ) {
                Text(
                    DesktopStrings["d_playing_from", "PLAYING FROM"],
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = if (compact) 10.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    // The feed names its own shelves in English; shown in the app's language.
                    if (source.equals("Recents", ignoreCase = true)) DesktopStrings["d_recents", "Recents"] else source,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = if (compact) 14.sp else 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            DesktopStrings["d_now_playing_title", "now playing."],
            color = Color.White,
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 46.sp else 66.sp,
            letterSpacing = (-1.5).sp,
            // Line height trimmed to the letters, so the caption above sits close to them
            // instead of a font's worth of empty ascent away.
            style = TextStyle(
                shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 6f), 28f),
                lineHeight = if (compact) 48.sp else 68.sp,
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                ),
            ),
        )
        // The line being sung, always on show, as the phone's player keeps it.
        CurrentLyricLine(song.videoId, lyricLines, position, isPlaying, compact)
        Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
        AnimatedContent(
            targetState = song to coverUrl,
            transitionSpec = {
                (slideInVertically(tween(700, delayMillis = 120, easing = EASE_OUT_EXPO)) { it / 2 } + fadeIn(tween(600, delayMillis = 120))) togetherWith
                    (slideOutVertically(tween(380)) { -it / 3 } + fadeOut(tween(300)))
            },
            contentKey = { it.first.videoId },
            label = "words",
        ) { (current, art) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(if (compact) 64.dp else 88.dp)
                        .graphicsLayer {
                            shadowElevation = 18.dp.toPx()
                            shape = RoundedCornerShape(5.dp)
                            clip = true
                        }
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(5.dp)),
                ) {
                    HighQualityArtwork(art.artworkAt(300) ?: art, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        current.title,
                        color = Color.White,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        fontSize = if (compact) 22.sp else 28.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 3f), 14f)),
                    )
                    Text(
                        "- ${current.artist}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontStyle = FontStyle.Italic,
                        fontSize = if (compact) 16.sp else 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.pillLink(onOpenArtist),
                    )
                    current.albumName?.takeIf { it.isNotBlank() }?.let { album ->
                        Text(
                            "- $album",
                            color = Color.White.copy(alpha = 0.72f),
                            fontStyle = FontStyle.Italic,
                            fontSize = if (compact) 13.sp else 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (onOpenAlbum != null) Modifier.pillLink(onOpenAlbum) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The lyric line the playhead is in, swapped with a rise and a fade as the song moves on. Nothing
 * at all for a track without timed lyrics.
 */
@Composable
private fun CurrentLyricLine(
    trackId: String,
    lines: List<LyricLine>?,
    position: PlaybackPosition,
    isPlaying: Boolean,
    compact: Boolean,
) {
    val timed = remember(lines) { lines?.takeIf { list -> list.any { it.timeMs > 0 } }.orEmpty() }
    if (timed.isEmpty()) return
    val index by rememberLyricIndex(timed, position, isPlaying)
    val intro = rememberIntroPhrase(timed)
    // The translation or romanization turned on anywhere — the lyrics panel, the home card — shown
    // small under the line here too.
    val versions = com.music.bitchord.ui.player.rememberLyricsVersions(trackId, timed)
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            // Swapped outright while the window is in the background: nobody to see it.
            if (com.music.bitchord.ui.AppActivity.quiet) {
                androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
            } else {
                (slideInVertically(tween(420, easing = EASE_OUT_EXPO)) { it / 2 } + fadeIn(tween(320))) togetherWith
                    (slideOutVertically(tween(260)) { -it / 2 } + fadeOut(tween(200)))
            }
        },
        label = "lyricLine",
        modifier = Modifier.padding(top = 10.dp),
    ) { shown -> Column {
        DesktopSungLine(
            timed = timed,
            index = shown,
            intro = intro,
            position = position,
            isPlaying = isPlaying,
            style = TextStyle(
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 20.sp else 26.sp,
                lineHeight = if (compact) 25.sp else 32.sp,
                shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 3f), 16f),
            ),
        )
        versions.subLines?.getOrNull(shown)?.text?.takeIf { it.isNotBlank() }?.let { under ->
            Text(
                under,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = if (compact) 15.sp else 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 2f), 12f)),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    } }
}

/**
 * Line [index] of [timed] as the classic player's strip shows it: before the first sung line one
 * of the "song is starting" phrases, through a break a note and "Instrumental", and a sung line
 * with the karaoke sweep across its words where the source times them.
 */
@Composable
internal fun DesktopSungLine(
    timed: List<LyricLine>,
    index: Int,
    intro: String,
    position: PlaybackPosition,
    isPlaying: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
) {
    val firstSung = remember(timed) { timed.indexOfFirst { !it.isGap } }
    val line = timed.getOrNull(index)
    val aligned = if (textAlign != null) style.copy(textAlign = textAlign) else style
    when {
        index < firstSung || firstSung < 0 -> Text(
            "♪  $intro",
            style = aligned,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        line == null || line.isGap -> Text(
            "♪  " + DesktopStrings["instrumental", "Instrumental"],
            style = aligned,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            modifier = modifier,
        )
        else -> com.music.bitchord.ui.player.KaraokeLyricLine(
            line = line,
            position = position,
            isPlaying = isPlaying,
            trackKey = timed,
            style = aligned,
            modifier = modifier,
            maxLines = 2,
        )
    }
}

/** Which of [timed] has most recently started — -1 before the first — kept current every [LYRIC_POLL_MS]. */
@Composable
internal fun rememberLyricIndex(
    timed: List<LyricLine>,
    position: PlaybackPosition,
    isPlaying: Boolean,
): androidx.compose.runtime.State<Int> {
    val offsetMs by DesktopPlayerSettings.lyricsOffsetMs.collectAsState()
    val playing by rememberUpdatedState(isPlaying)
    val offset by rememberUpdatedState(offsetMs)
    val index = remember(timed) { mutableStateOf(-1) }
    LaunchedEffect(timed) {
        while (true) {
            val extra = if (playing && position.advancing) (System.nanoTime() - position.sampledAtNanos) / 1_000_000L else 0L
            val now = position.positionMs + extra + offset
            // The last line that has started: a binary search, as a song has a hundred of them.
            var lo = 0
            var hi = timed.lastIndex
            var found = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (timed[mid].timeMs <= now) {
                    found = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            if (index.value != found) index.value = found
            delay(LYRIC_POLL_MS)
        }
    }
    return index
}

/** One of the "song is starting" lines, the same one for as long as these lyrics are up. */
@Composable
internal fun rememberIntroPhrase(timed: List<LyricLine>): String = remember(timed) {
    val phrases = DesktopStrings.array("lyrics_intro_lines")
    if (phrases.isEmpty()) "♪" else phrases[(timed.hashCode() and Int.MAX_VALUE) % phrases.size]
}

/** A− 120% A+ — how big the lyrics are drawn, remembered. */
@Composable
private fun LyricsScaleControl(scale: Float, onScale: (Float) -> Unit, modifier: Modifier) {
    Row(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color.Black.copy(alpha = 0.38f))
            .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(17.dp))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "A−",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onScale((scale - LYRICS_SCALE_STEP).coerceAtLeast(LYRICS_SCALE_MIN)) }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
            "${(scale * 100).roundToInt()}%",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            modifier = Modifier
                .clickable { onScale(1f) }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 4.dp),
        )
        Text(
            "A+",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onScale((scale + LYRICS_SCALE_STEP).coerceAtMost(LYRICS_SCALE_MAX)) }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

// ---- The deck --------------------------------------------------------------------------------

/**
 * The player's one control surface, after Apple Music's: a seek bar with the times under its
 * ends, the transport large and bare, the volume between a speaker that mutes and one that turns
 * up — and under it a row with everything else.
 */
@Composable
private fun ControlDeck(
    isPlaying: Boolean,
    isLoading: Boolean,
    position: PlaybackPosition,
    durationMs: Long,
    hasPrevious: Boolean,
    hasNext: Boolean,
    shuffle: Boolean,
    repeat: Int,
    liked: Boolean,
    volume: Float,
    accent: Color,
    clock: () -> Float,
    isVideo: Boolean,
    visualMode: DesktopVisualMode,
    videoState: DesktopVideoState?,
    versionSwitch: DesktopVersionSwitch?,
    visualizerStyle: DesktopVisualizerStyle,
    centre: DiscCentre,
    onCentre: (DiscCentre) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onCollapse: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenAudioOutput: () -> Unit,
    onListenTogether: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(32.dp)
    // The level the mute button brings back.
    var lastAudible by remember { mutableFloatStateOf(volume.takeIf { it > MUTE_FLOOR } ?: 0.6f) }
    LaunchedEffect(volume) { if (volume > MUTE_FLOOR) lastAudible = volume }
    val muted = volume <= MUTE_FLOOR
    // The name of the visualiser just picked, shown for a moment over the deck.
    var announced by remember { mutableStateOf<String?>(null) }
    val visualizerNames = DesktopVisualizerStyle.entries.associateWith { it.label() }
    LaunchedEffect(announced) {
        if (announced != null) {
            delay(1_400)
            announced = null
        }
    }

    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .desktopLiquidGlass(32.dp, tint = Color.Black.copy(alpha = 0.10f), blurRadius = 4.dp) {
                    clip(shape).background(Color.Black.copy(alpha = 0.5f)).border(0.5.dp, Color.White.copy(alpha = 0.16f), shape)
                }
                .padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 14.dp),
        ) {
            DeckSeekBar(position, durationMs, isPlaying, clock, onSeek)
            Spacer(Modifier.height(8.dp))
            // The transport, large and bare.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(Icons.Rounded.FastRewind, DesktopStrings["widget_previous", "Previous"], size = 44.dp, onClick = onPrevious)
                TransportButton(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    size = 60.dp,
                    loading = isLoading,
                    onClick = onPlayPause,
                )
                TransportButton(Icons.Rounded.FastForward, DesktopStrings["widget_next", "Next"], size = 44.dp, onClick = onNext)
            }
            Spacer(Modifier.height(6.dp))
            // The volume, between a speaker that mutes and one that turns it up.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DeckIcon(
                    when {
                        muted -> Icons.AutoMirrored.Rounded.VolumeOff
                        else -> Icons.AutoMirrored.Rounded.VolumeDown
                    },
                    if (muted) DesktopStrings["d_unmute", "Unmute"] else DesktopStrings["d_mute", "Mute"],
                    size = 20.dp,
                    tint = if (muted) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.7f),
                    onClick = { onVolumeChange(if (muted) lastAudible else 0f) },
                )
                DesktopThinSlider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    idleHeight = 6.dp,
                    activeHeight = 11.dp,
                    activeColor = Color.White.copy(alpha = 0.95f),
                    inactiveColor = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                DeckIcon(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    DesktopStrings["d_volume", "Volume"],
                    size = 20.dp,
                    tint = Color.White.copy(alpha = 0.7f),
                    onClick = { onVolumeChange((volume + 0.1f).coerceAtMost(1f)) },
                )
            }
            Spacer(Modifier.height(8.dp))
            // Everything else: five a side, every icon the same size and the same distance apart,
            // the outer ones lined up with the ends of the bars above — and the song / video
            // switch dead centre between them.
            Box(Modifier.fillMaxWidth().offset(x = 0.dp), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.align(Alignment.CenterStart).offset(x = (-8).dp),
                    horizontalArrangement = Arrangement.spacedBy(DECK_ICON_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeckIcon(Icons.Rounded.KeyboardArrowDown, DesktopStrings["d_collapse_player", "Collapse"], onClick = onCollapse)
                    DeckIcon(
                        if (liked) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                        DesktopStrings["d_like", "Like"],
                        tint = if (liked) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.8f),
                        onClick = onToggleLike,
                    )
                    DeckIcon(BitChordIcons.Shuffle, "Shuffle", tint = toggleTint(shuffle, accent), onClick = onToggleShuffle)
                    Box(contentAlignment = Alignment.Center) {
                        DeckIcon(BitChordIcons.Repeat, "Repeat", tint = toggleTint(repeat != 0, accent), onClick = onCycleRepeat)
                        if (repeat == 2) {
                            Text("1", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(x = 8.dp, y = (-7).dp))
                        }
                    }
                    DeckIcon(Icons.Rounded.Headphones, DesktopStrings["audio_output", "Audio output"], onClick = onOpenAudioOutput)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Only once there is another version to switch to. A spinner while it was
                    // looked for ran through the opening — a shape the GPU had not drawn before,
                    // prepared on the spot, mid-slide.
                    if (versionSwitch?.alternate != null) {
                        VersionSwitch(isVideo = isVideo, switch = versionSwitch)
                    }
                    if (isVideo) {
                        Box(contentAlignment = Alignment.Center) {
                            DeckIcon(
                                if (visualMode == DesktopVisualMode.VIDEO) Icons.Rounded.Image else Icons.Rounded.Videocam,
                                if (visualMode == DesktopVisualMode.VIDEO) {
                                    DesktopStrings["d_show_artwork", "Show the artwork"]
                                } else {
                                    DesktopStrings["d_show_video", "Show the video"]
                                },
                                tint = if (videoState == DesktopVideoState.UNAVAILABLE) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.8f),
                                onClick = {
                                    DesktopStyleSettings.setVisualMode(
                                        if (visualMode == DesktopVisualMode.VIDEO) DesktopVisualMode.ARTWORK else DesktopVisualMode.VIDEO,
                                    )
                                },
                            )
                            if (videoState == DesktopVideoState.LOADING) {
                                CircularProgressIndicator(Modifier.size(30.dp), color = Color.White.copy(alpha = 0.6f), strokeWidth = 1.5.dp)
                            }
                        }
                    }
                }
                Row(
                    Modifier.align(Alignment.CenterEnd).offset(x = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(DECK_ICON_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeckIcon(
                        Icons.Rounded.AutoAwesome,
                        DesktopStrings["d_visualizer_style", "Visualiser"],
                        tint = toggleTint(visualizerStyle != DesktopVisualizerStyle.NONE, accent),
                        onClick = {
                            val next = visualizerStyle.next()
                            DesktopStyleSettings.setVisualizerStyle(next)
                            announced = visualizerNames[next]
                        },
                    )
                    DeckIcon(
                        BitChordIcons.LyricsQuote,
                        DesktopStrings["lyrics", "Lyrics"],
                        tint = toggleTint(centre == DiscCentre.LYRICS, accent),
                        onClick = { onCentre(DiscCentre.LYRICS) },
                    )
                    DeckIcon(
                        BitChordIcons.Queue,
                        DesktopStrings["queue", "Queue"],
                        tint = toggleTint(centre == DiscCentre.QUEUE, accent),
                        onClick = { onCentre(DiscCentre.QUEUE) },
                    )
                    DeckIcon(Icons.Rounded.Groups, DesktopStrings["listen_together", "Listen together"], onClick = onListenTogether)
                    DeckIcon(Icons.Rounded.MoreHoriz, DesktopStrings["d_more", "More"], onClick = onOpenMenu)
                }
            }
        }
        AnimatedVisibility(
            visible = announced != null,
            enter = fadeIn(tween(160)) + slideInVertically(tween(260)) { it },
            exit = fadeOut(tween(260)),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-44).dp),
        ) {
            Text(
                announced.orEmpty(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/** A seek bar after Apple Music's: thin and long, the times beneath its two ends. */
@Composable
private fun DeckSeekBar(
    position: PlaybackPosition,
    durationMs: Long,
    isPlaying: Boolean,
    clock: () -> Float,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val playing by rememberUpdatedState(isPlaying)
    val duration by rememberUpdatedState(durationMs)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val thickness by animateDpAsState(if (hovered || dragging != null) 11.dp else 7.dp, spring(Spring.DampingRatioMediumBouncy), label = "deckScrub")

    fun liveFraction(): Float {
        val d = duration.takeIf { it > 0 } ?: return 0f
        dragging?.let { return it }
        val extra = if (playing && position.advancing) (System.nanoTime() - position.sampledAtNanos) / 1_000_000L else 0L
        return ((position.positionMs + extra).toFloat() / d).coerceIn(0f, 1f)
    }

    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        if (duration > 0) onSeek((f * duration).toLong())
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> dragging = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragging?.let { if (duration > 0) onSeek((it * duration).toLong()) }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                    ) { change, _ ->
                        change.consume()
                        dragging = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                },
        ) {
            clock()
            val f = liveFraction()
            val h = thickness.toPx()
            val y = size.height / 2f
            drawLine(Color.White.copy(alpha = 0.24f), Offset(h / 2, y), Offset(size.width - h / 2, y), h, StrokeCap.Round)
            if (f > 0f) {
                drawLine(Color.White.copy(alpha = 0.96f), Offset(h / 2, y), Offset(h / 2 + (size.width - h) * f, y), h, StrokeCap.Round)
            }
        }
        val shownMs = dragging?.let { (it * durationMs).toLong() } ?: position.positionMs
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(formatClock(shownMs), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "-" + formatClock((durationMs - shownMs).coerceAtLeast(0)),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** A transport glyph, bare and big, that swells under the pointer and dips when pressed. */
@Composable
private fun TransportButton(
    icon: ImageVector,
    label: String,
    size: Dp,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.86f
            hovered -> 1.10f
            else -> 1f
        },
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "transport",
    )
    val wash by animateFloatAsState(if (hovered) 0.10f else 0f, tween(160), label = "transportWash")
    Box(
        Modifier
            .size(size + 26.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = wash))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(size * 0.6f), color = Color.White, strokeWidth = 3.dp)
        } else {
            AnimatedContent(
                targetState = icon,
                transitionSpec = { (scaleIn(tween(220)) + fadeIn(tween(160))) togetherWith (scaleOut(tween(160)) + fadeOut(tween(120))) },
                label = "transportIcon",
            ) { glyph ->
                Icon(glyph, label, tint = Color.White, modifier = Modifier.size(size))
            }
        }
    }
}

@Composable
private fun DeckIcon(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White.copy(alpha = 0.8f),
    size: Dp = 20.dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateFloatAsState(if (hovered) 0.13f else 0f, tween(140), label = "deckWash")
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "deckPress")
    Box(
        Modifier
            .size(size + 16.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = wash))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(size))
    }
}

private fun toggleTint(on: Boolean, accent: Color): Color =
    if (on) lerp(accent, Color.White, 0.35f) else Color.White.copy(alpha = 0.8f)

/** Song | Video, YouTube Music's own toggle between the two versions of a track. */
@Composable
private fun VersionSwitch(isVideo: Boolean, switch: DesktopVersionSwitch) {
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Segment(Icons.Rounded.MusicNote, DesktopStrings["d_version_song", "Song"], selected = !isVideo, enabled = isVideo && switch.alternate != null, onClick = switch.onSwitch)
        Segment(Icons.Rounded.Videocam, DesktopStrings["d_version_video", "Video"], selected = isVideo, enabled = !isVideo && switch.alternate != null, onClick = switch.onSwitch)
    }
}

@Composable
private fun Segment(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(if (selected) Color.White else Color.Transparent, tween(260), label = "segment")
    val ink = if (selected) Color.Black else Color.White.copy(alpha = if (enabled) 0.9f else 0.4f)
    Row(
        Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(13.dp))
            .background(fill)
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .pointerHoverIcon(if (enabled && !selected) PointerIcon.Hand else PointerIcon.Default)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The visualiser's name, as the cycle button announces it and Settings lists it. */
@Composable
internal fun DesktopVisualizerStyle.label(): String = when (this) {
    DesktopVisualizerStyle.NONE -> DesktopStrings["d_vis_none", "Off"]
    DesktopVisualizerStyle.AURORA -> DesktopStrings["d_vis_aurora", "Aurora"]
    DesktopVisualizerStyle.RING -> DesktopStrings["d_vis_ring", "Ring"]
    DesktopVisualizerStyle.WAVES -> DesktopStrings["d_vis_waves", "Waves"]
    DesktopVisualizerStyle.PARTICLES -> DesktopStrings["d_vis_particles", "Particles"]
    DesktopVisualizerStyle.SPECTRUM -> DesktopStrings["d_vis_spectrum", "Spectrum"]
    DesktopVisualizerStyle.BARS -> DesktopStrings["d_vis_bars", "Bars"]
}

// ---- The disc --------------------------------------------------------------------------------

/**
 * A CD: the cover printed edge to edge, a clear hub with its stacking ring, a centre hole,
 * data-track grooves, a holographic sheen that turns with it, and a reflection that does not —
 * the light is in the room, not on the disc.
 */
@Composable
private fun SpinningDisc(
    artUrl: String?,
    angle: () -> Float,
    level: () -> Float,
    glow: Color,
    modifier: Modifier,
) {
    val art = rememberArtworkBitmap(artUrl.artworkAt(1200) ?: artUrl)
    Box(modifier) {
        // The shadow it casts, and the glow of the colour it is lit by.
        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = 1.06f; scaleY = 1.06f }) {
            val beat = level()
            drawCircle(
                Brush.radialGradient(
                    0.70f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Transparent,
                ),
                radius = size.minDimension / 2,
                center = center + Offset(size.width * 0.02f, size.height * 0.03f),
            )
            drawCircle(
                Brush.radialGradient(
                    0.80f to glow.copy(alpha = 0.0f),
                    0.93f to glow.copy(alpha = 0.18f + beat * 0.35f),
                    1f to Color.Transparent,
                ),
                radius = size.minDimension / 2,
            )
        }
        // The disc itself, turning.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = angle()
                    // Offscreen, so the centre hole can be cut clean through to what is behind.
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        ) {
            val radius = size.minDimension / 2f
            val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius)) }
            clipPath(disc) {
                val image = art
                if (image != null) {
                    drawSquareCrop(image)
                } else {
                    drawRect(Brush.linearGradient(listOf(Color(0xFF2B2B35), Color(0xFF55556A))))
                }
                // The data side's rainbow, turning with the disc.
                drawCircle(
                    Brush.sweepGradient(
                        listOf(
                            Color(0xFF7CF7FF), Color(0xFFFF7CE5), Color(0xFFFFF27C), Color(0xFF7CFF9B),
                            Color(0xFF7C9BFF), Color(0xFFFF7C7C), Color(0xFF7CF7FF),
                        ),
                    ),
                    radius = radius,
                    alpha = 0.16f,
                    blendMode = BlendMode.Screen,
                )
                // Grooves.
                var r = radius * 0.40f
                while (r < radius * 0.985f) {
                    drawCircle(Color.Black, radius = r, alpha = 0.035f, style = Stroke(0.8f))
                    r += 3.2f
                }
            }
            // The clear hub, to a real disc's proportions (120 mm across, a 15 mm hole, the clear
            // zone out to 46 mm): see-through polycarbonate over the print, the stacking ring
            // moulded into it, a smoky clamping area, then the hole.
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.30f),
                    0.6f to Color(0xFFB8C4CC).copy(alpha = 0.22f),
                    1f to Color.White.copy(alpha = 0.10f),
                    radius = radius * 0.38f,
                    center = center,
                ),
                radius = radius * 0.38f,
            )
            drawCircle(Color.White.copy(alpha = 0.60f), radius = radius * 0.38f, style = Stroke(1.2f))
            drawCircle(Color.Black.copy(alpha = 0.10f), radius = radius * 0.30f, style = Stroke(radius * 0.035f))
            drawCircle(Color.White.copy(alpha = 0.35f), radius = radius * 0.285f, style = Stroke(1f))
            drawCircle(Color(0xFF20242A).copy(alpha = 0.45f), radius = radius * 0.21f)
            drawCircle(Color.White.copy(alpha = 0.22f), radius = radius * 0.21f, style = Stroke(1f))
            drawCircle(Color.Black, radius = radius * 0.125f, blendMode = BlendMode.Clear)
            drawCircle(Color.White.copy(alpha = 0.45f), radius = radius * 0.125f, style = Stroke(1.4f))
            // The rim.
            drawCircle(Color.White.copy(alpha = 0.65f), radius = radius - 0.75f, style = Stroke(1.5f))
        }
        // The reflection, fixed in the room while the disc turns under it.
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius)) }
            val hole = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius * 0.125f)) }
            clipPath(disc) {
                clipPath(hole, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                    drawCircle(
                        Brush.sweepGradient(
                            0.00f to Color.White.copy(alpha = 0.0f),
                            0.08f to Color.White.copy(alpha = 0.34f),
                            0.16f to Color.White.copy(alpha = 0.0f),
                            0.50f to Color.White.copy(alpha = 0.0f),
                            0.58f to Color.White.copy(alpha = 0.22f),
                            0.66f to Color.White.copy(alpha = 0.0f),
                            1.00f to Color.White.copy(alpha = 0.0f),
                        ),
                        radius = radius,
                        blendMode = BlendMode.Screen,
                    )
                    drawCircle(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.18f),
                            0.5f to Color.Transparent,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height),
                        ),
                        radius = radius,
                    )
                }
            }
        }
    }
}

/** The cover as a card, for when the disc is switched off: tilted toward the light, floating. */
@Composable
private fun ArtworkCard(artUrl: String?, clock: () -> Float, modifier: Modifier) {
    val art = rememberArtworkBitmap(artUrl.artworkAt(1200) ?: artUrl)
    Box(
        modifier.graphicsLayer {
            val t = clock()
            translationY = sin(t * 0.9f) * 6.dp.toPx()
            rotationY = 9f + sin(t * 0.5f) * 2.5f
            rotationX = cos(t * 0.6f) * 1.6f
            cameraDistance = 18f * density
            shadowElevation = 40.dp.toPx()
            shape = RoundedCornerShape(14.dp)
            clip = true
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val image = art
            if (image != null) drawSquareCrop(image) else drawRect(Color(0xFF2B2B35))
            drawRect(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.22f),
                    0.45f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
        }
    }
}

/** [image] centre-cropped to a square and drawn across this scope, with high-quality filtering. */
private fun DrawScope.drawSquareCrop(image: ImageBitmap) {
    val side = min(image.width, image.height)
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - side) / 2, (image.height - side) / 2),
        srcSize = IntSize(side, side),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

// ---- The room --------------------------------------------------------------------------------

/** The artist's name, enormous and faint, drifting: one row one way, its mirror the other. */
@Composable
private fun GhostMarquee(text: String, color: Color, clock: () -> Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val family = MaterialTheme.typography.displayLarge.fontFamily
    BoxWithConstraints(modifier) {
        val fontPx = with(LocalDensity.current) { (maxHeight * 0.36f).toSp() }
        val layout = remember(text, fontPx, family) {
            measurer.measure(
                "$text  ",
                TextStyle(fontFamily = family, fontWeight = FontWeight.Black, fontSize = fontPx, letterSpacing = (-0.04).em),
            )
        }
        // Letters this tall are drawn as outlines, not from the glyph cache — expensive, every
        // frame. Drawn once into a half-size picture instead, and the picture slid along; faint
        // and huge, the half resolution does not show.
        val strip = remember(layout, color) { marqueeStrip(layout, color) }
        Canvas(Modifier.fillMaxSize()) {
            val runWidth = layout.size.width.toFloat().coerceAtLeast(1f)
            val t = clock()
            fun row(y: Float, speed: Float) {
                var x = -((t * speed) % runWidth + runWidth) % runWidth
                while (x < size.width) {
                    drawImage(
                        strip,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(strip.width, strip.height),
                        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                        dstSize = IntSize(layout.size.width, layout.size.height),
                        filterQuality = FilterQuality.Low,
                    )
                    x += runWidth
                }
            }
            val rowHeight = layout.size.height.toFloat()
            row(size.height * 0.04f - rowHeight * 0.18f, 26f)
            // Upside down, the other way — the mirror image under the floor.
            withTransform({ rotate(180f, Offset(size.width / 2f, size.height * 0.72f)) }) {
                row(size.height * 0.72f - rowHeight * 0.55f, 18f)
            }
        }
    }
}

/** Two wireframe globes turning slowly, out of the way at the edges. */
@Composable
private fun WireSpheres(clock: () -> Float, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        val t = clock()
        fun globe(c: Offset, r: Float, phase: Float) {
            val stroke = Stroke(width = 1f)
            for (i in 0 until 9) {
                val lat = (i - 4) / 4.5f
                val ry = r * kotlin.math.sqrt((1f - lat * lat).coerceAtLeast(0f))
                drawOval(
                    tint.copy(alpha = 0.10f),
                    topLeft = Offset(c.x - ry, c.y + lat * r - ry * 0.18f),
                    size = Size(ry * 2f, ry * 0.36f),
                    style = stroke,
                )
            }
            for (i in 0 until 6) {
                val turn = (phase + t * 0.35f + i * (PI.toFloat() / 6f))
                val rx = r * kotlin.math.abs(cos(turn))
                drawOval(
                    tint.copy(alpha = 0.10f),
                    topLeft = Offset(c.x - rx, c.y - r),
                    size = Size(rx * 2f, r * 2f),
                    style = stroke,
                )
            }
        }
        globe(Offset(size.width * 0.93f, size.height * 0.16f), size.minDimension * 0.15f, 0f)
        globe(Offset(size.width * 0.36f, size.height * 0.10f), size.minDimension * 0.05f, 1.3f)
    }
}

/** A tape's worth of scanlines and a vignette — the faintest VHS cast. */
@Composable
private fun Scanlines(modifier: Modifier) {
    // One three-pixel tile repeated down the window, instead of seven hundred lines drawn one by
    // one every frame.
    val lines = remember { scanlineBrush() }
    Canvas(modifier) {
        drawRect(lines)
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.55f),
                center = center,
                radius = size.maxDimension * 0.72f,
            ),
        )
    }
}

/** The cover, huge and blurred, for the light to have texture. */
@Composable
private fun AmbientArtwork(url: String?, modifier: Modifier) {
    // Blurred once, small, and stretched: the same picture as a 90 px blur over the whole window
    // without the GPU redoing that blur on every frame — see [DesktopPrecomputed].
    val art = rememberBlurredArtwork(url, edge = 64, sigma = 5f)
    val shown by animateFloatAsState(if (art != null) 1f else 0f, tween(900), label = "ambient")
    Canvas(
        modifier.graphicsLayer {
            alpha *= shown
            scaleX = 1.25f
            scaleY = 1.25f
            // One image: its alpha applied as it is drawn, with no window-sized buffer between.
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
        },
    ) {
        val image = art ?: return@Canvas
        drawVideoFrame(image, crop = true)
    }
}

// ---- Shared with the bottom bar --------------------------------------------------------------

/** A seek bar that runs on the frame clock between playhead reports, and seeks on release. */
@Composable
internal fun DesktopSeekBar(
    position: PlaybackPosition,
    durationMs: Long,
    isPlaying: Boolean,
    clock: () -> Float,
    accent: Color,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val playing by rememberUpdatedState(isPlaying)
    val duration by rememberUpdatedState(durationMs)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val thickness by animateDpAsState(if (hovered || dragging != null) 7.dp else 4.dp, spring(Spring.DampingRatioMediumBouncy), label = "scrub")

    fun liveFraction(): Float {
        val d = duration.takeIf { it > 0 } ?: return 0f
        dragging?.let { return it }
        val extra = if (playing && position.advancing) (System.nanoTime() - position.sampledAtNanos) / 1_000_000L else 0L
        return ((position.positionMs + extra).toFloat() / d).coerceIn(0f, 1f)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        val shownMs = dragging?.let { (it * durationMs).toLong() } ?: position.positionMs
        Text(formatClock(shownMs), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, modifier = Modifier.width(40.dp))
        Canvas(
            Modifier
                .weight(1f)
                .height(22.dp)
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        if (duration > 0) onSeek((f * duration).toLong())
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> dragging = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragging?.let { if (duration > 0) onSeek((it * duration).toLong()) }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                    ) { change, _ ->
                        change.consume()
                        dragging = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                },
        ) {
            clock()
            val f = liveFraction()
            val h = thickness.toPx()
            val y = size.height / 2f
            drawLine(Color.White.copy(alpha = 0.22f), Offset(h / 2, y), Offset(size.width - h / 2, y), h, StrokeCap.Round)
            if (f > 0f) {
                drawLine(
                    Brush.horizontalGradient(listOf(Color.White, lerp(Color.White, accent, 0.35f))),
                    Offset(h / 2, y),
                    Offset(h / 2 + (size.width - h) * f, y),
                    h,
                    StrokeCap.Round,
                )
            }
            if (hovered || dragging != null) {
                drawCircle(Color.White, radius = h * 1.15f, center = Offset(h / 2 + (size.width - h) * f, y))
            }
        }
        Text(
            "-" + formatClock((durationMs - shownMs).coerceAtLeast(0)),
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
            modifier = Modifier.width(46.dp).padding(start = 6.dp),
        )
    }
}

// ---- Artwork and colour ----------------------------------------------------------------------

/** Artwork as a bitmap, for drawing with high-quality filtering. */
@Composable
internal fun rememberArtworkBitmap(url: String?): ImageBitmap? {
    // Already held: drawn on the first frame instead of popping in on a later one.
    val bitmap by produceState(DesktopArtworkCache.peek(url), url) {
        if (value == null) value = withContext(Dispatchers.IO) { DesktopArtworkCache.load(url) }
    }
    return bitmap
}

@Composable
private fun HighQualityArtwork(url: String?, modifier: Modifier) {
    val art = rememberArtworkBitmap(url)
    if (art == null) {
        Box(modifier.background(Color(0xFF2B2B35)))
    } else {
        Image(
            BitmapPainter(art, filterQuality = FilterQuality.High),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

/** The cover's most vivid colour, and the shades the room is lit with. */
internal data class DesktopVividPalette(
    /** The light: the cover's strongest saturated hue, brightened. */
    val vivid: Color,
    /** The corners of the room. */
    val deep: Color,
    /** The ghost lettering: darker than the light, same hue. */
    val ink: Color,
) {
    companion object {
        val Default = DesktopVividPalette(Color(0xFF2E8B57), Color(0xFF06140C), Color(0xFF02150A))
    }
}

@Composable
internal fun rememberVividPalette(url: String?): DesktopVividPalette {
    // Worked out once a cover and remembered, so every surface showing it has its colour from
    // the first frame rather than fading from grey while it is worked out again.
    val palette by produceState(url?.let(VIVID_PALETTES::get) ?: DesktopVividPalette.Default, url) {
        if (url != null && VIVID_PALETTES.containsKey(url)) return@produceState
        val found = withContext(Dispatchers.Default) {
            vividPaletteOf(DesktopArtworkCache.load(url.artworkAt(120) ?: url))
        } ?: DesktopVividPalette.Default
        if (url != null) {
            if (VIVID_PALETTES.size > 256) VIVID_PALETTES.clear()
            VIVID_PALETTES[url] = found
        }
        value = found
    }
    return palette
}

/**
 * A hue histogram weighted by saturation and brightness — so a small patch of strong colour beats
 * a large grey field — and the winning hue turned into a light, a shadow and an ink.
 */
private fun vividPaletteOf(bitmap: ImageBitmap?): DesktopVividPalette? {
    if (bitmap == null || bitmap.width == 0 || bitmap.height == 0) return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.readPixels(pixels)
    val buckets = 36
    val weight = FloatArray(buckets)
    val satSum = FloatArray(buckets)
    val hsv = FloatArray(3)
    var greyWeight = 0f
    for (index in pixels.indices step 3) {
        val pixel = pixels[index]
        val r = (pixel shr 16 and 0xFF) / 255f
        val g = (pixel shr 8 and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f
        rgbToHsvInto(r, g, b, hsv)
        val w = hsv[1] * hsv[1] * hsv[2]
        if (hsv[1] < 0.15f) {
            greyWeight += hsv[2] * 0.02f
            continue
        }
        val bucket = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
        weight[bucket] += w
        satSum[bucket] += hsv[1] * w
    }
    var best = 0
    for (i in 1 until buckets) if (weight[i] + weight[(i + 1) % buckets] * 0.5f > weight[best] + weight[(best + 1) % buckets] * 0.5f) best = i
    if (weight[best] <= greyWeight * 0.5f || weight[best] < 0.5f) {
        // A monochrome sleeve: lit in a cool neutral rather than an invented colour.
        return DesktopVividPalette(Color(0xFF6E7488), Color(0xFF0B0C10), Color(0xFF050608))
    }
    val hue = (best + 0.5f) * (360f / buckets)
    val saturation = (satSum[best] / weight[best]).coerceIn(0.55f, 0.95f)
    return DesktopVividPalette(
        vivid = Color.hsv(hue, saturation, 0.78f),
        deep = Color.hsv(hue, (saturation * 0.9f).coerceAtMost(1f), 0.10f),
        ink = Color.hsv(hue, saturation, 0.06f),
    )
}

internal fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** Volume at or below this counts as muted. */
private const val MUTE_FLOOR = 0.005f

/** The window the player is laid out for, in dp; anything bigger or smaller scales it. */
private const val DESIGN_WIDTH = 1440f
private const val DESIGN_HEIGHT = 900f

private const val LYRIC_POLL_MS = 80L
/** Between the deck's small buttons. */
private val DECK_ICON_GAP = 6.dp

internal const val LYRICS_SCALE_STEP = 0.15f
internal const val LYRICS_SCALE_MIN = 0.8f
internal const val LYRICS_SCALE_MAX = 2.5f

/** One revolution every this many seconds — a slow, legible spin rather than 500 rpm blur. */
private const val DISC_PERIOD_S = 3.2f

private val EASE_OUT_EXPO = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

private val VIVID_PALETTES = java.util.concurrent.ConcurrentHashMap<String, DesktopVividPalette>()

/** The marquee's text, drawn once at half size into a bitmap. */
private fun marqueeStrip(layout: androidx.compose.ui.text.TextLayoutResult, color: Color): ImageBitmap {
    val width = (layout.size.width / 2).coerceAtLeast(1)
    val height = (layout.size.height / 2).coerceAtLeast(1)
    val bitmap = ImageBitmap(width, height)
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    val scope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
    scope.draw(Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
        withTransform({ scale(0.5f, 0.5f, Offset.Zero) }) { drawText(layout, color = color) }
    }
    return bitmap.frozen()
}

/** A transparent tile with one faint dark line in three, repeated across whatever it fills. */
private fun scanlineBrush(): androidx.compose.ui.graphics.ShaderBrush {
    val tile = ImageBitmap(1, 3)
    val canvas = androidx.compose.ui.graphics.Canvas(tile)
    canvas.drawRect(0f, 0f, 1f, 1f, androidx.compose.ui.graphics.Paint().apply { this.color = Color.Black.copy(alpha = 0.06f) })
    tile.frozen()
    return androidx.compose.ui.graphics.ShaderBrush(
        androidx.compose.ui.graphics.ImageShader(tile, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated),
    )
}

/**
 * A line of text that opens something: under the pointer it sits on a fully rounded wash, the
 * player's own shape, rather than a box. Drawn out past the words on both sides, so the words
 * themselves stay exactly where the lines around them are.
 */
private fun Modifier.pillLink(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val wash by animateFloatAsState(if (hovered) 0.14f else 0f, tween(160), label = "pillLink")
    this
        .hoverable(interaction)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .pointerHoverIcon(PointerIcon.Hand)
        .drawBehind {
            if (wash <= 0f) return@drawBehind
            val padX = 10.dp.toPx()
            val padY = 2.dp.toPx()
            drawRoundRect(
                Color.White.copy(alpha = wash),
                topLeft = Offset(-padX, -padY),
                size = Size(size.width + padX * 2, size.height + padY * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius((size.height + padY * 2) / 2f),
            )
        }
}
