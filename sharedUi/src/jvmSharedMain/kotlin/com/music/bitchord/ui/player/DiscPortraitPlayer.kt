package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.theme.stableStatusBarInsets
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.min

/**
 * BitChord²'s disc player, upright: the desktop player's broadcast-ident look turned for a phone.
 *
 * A CD with the song's cover printed on it turns at the top of the screen, a little over its lower
 * half showing, the way the desktop's hangs off the left edge. The room is lit in the cover's own
 * colour, the artist's name drifts by enormous and faint, and a visualiser that follows the real
 * output moves behind it all. The track, the line being sung and every control sit on one glass
 * deck along the bottom. Lyrics and the queue take the middle of the room: the disc rises into a
 * band across the top, still turning, and a tap on it brings the player back.
 *
 * Every part that is not the room — the credits, the lyric strip, the scrubber, the transport, the
 * volume, the action row, the lyrics and the queue — is the same composable the other shapes of the
 * player use, handed in, so the disc player never drifts from them in what it does.
 */
@Composable
internal fun DiscPortraitPlayer(
    pane: PlayerPane,
    song: Song,
    coverUrl: String?,
    isPlaying: Boolean,
    /** Applied to the room, so the player's sheets frost what is behind them. */
    hazeSource: Modifier,
    /** "Playing from …", or null where Settings hides it. */
    caption: String?,
    onOpenCaption: () -> Unit,
    credits: @Composable () -> Unit,
    lyricStrip: (@Composable () -> Unit)?,
    scrubber: @Composable () -> Unit,
    transport: @Composable () -> Unit,
    volume: (@Composable () -> Unit)?,
    actions: @Composable () -> Unit,
    lyricsPane: @Composable () -> Unit,
    queuePane: @Composable () -> Unit,
    onBackToMain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val cover = coverUrl ?: song.thumbnailUrl

    val palette = rememberDiscPalette(cover)
    val glow by animateColorAsState(palette.vivid, tween(1_400), label = "discGlow")
    val deep by animateColorAsState(palette.deep, tween(1_400), label = "discDeep")
    val ink by animateColorAsState(palette.ink, tween(1_400), label = "discInk")

    // The bars only run while this is on screen: the audio thread skips the analysis otherwise.
    DisposableEffect(Unit) {
        val stop = AudioSpectrum.watch()
        onDispose { stop() }
    }

    // One clock for everything that moves on its own: read only in draw lambdas, so a frame
    // redraws what moves and recomposes nothing. Ticking only while the sheet is up — a player
    // kept composed down in the mini bar asks for no frames at all.
    val clock = remember { mutableFloatStateOf(0f) }
    val discAngle = remember { mutableFloatStateOf(0f) }
    val playing by rememberUpdatedState(isPlaying)
    val dock = LocalPlayerDock.current
    LaunchedEffect(reduceAnimation, dock) {
        snapshotFlow { dock == null || dock.offset() == null || dock.openFraction() > 0.01f }
            .collectLatest { open ->
                if (!open) return@collectLatest
                var last = androidx.compose.runtime.withFrameNanos { it }
                var velocity = 0f
                while (true) {
                    androidx.compose.runtime.withFrameNanos { now ->
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
    }

    // 0 with the player's own words in the middle, 1 with the lyrics or the queue there.
    val panelT by animateFloatAsState(
        targetValue = if (pane == PlayerPane.Main) 0f else 1f,
        animationSpec = tween(520, easing = EASE_OUT_EXPO),
        label = "discPanel",
    )
    val density = LocalDensity.current
    val off = remember { discLayersOff() }

    // Clipped to the sheet: pulled down, the disc goes with it rather than hanging over the page.
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        val width = maxWidth
        val height = maxHeight
        val statusTop = with(density) { stableStatusBarInsets().getTop(this).toDp() }
        // Where the disc's lower edge sits: two thirds of a screen-wide disc below the top.
        val discBottom = statusTop + min(width * 0.94f, height * 0.5f) * 0.68f
        // A bigger disc in that same stretch of screen: wider than the screen and higher up, so
        // more of its print shows rather than more of the screen going to it.
        val discSize = min(width * 1.22f, height * 0.62f)
        val discCentreY = discBottom - discSize / 2
        // Risen with a panel up: a band of disc across the top, the hub just under the edge.
        val discRise = discCentreY + discSize * 0.5f - (statusTop + PANEL_BAND)

        // ---- The room, and everything the player's sheets frost ---------------------------
        Box(Modifier.fillMaxSize().then(if ("haze" in off) Modifier else hazeSource)) {
            // The light and the soft cover under it change with the track and nothing else: one
            // cached layer, so a frame composites one picture rather than redrawing both.
            if ("room" !in off) Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            0f to glow.copy(alpha = 0.95f),
                            0.55f to lerp(glow, deep, 0.55f),
                            1f to deep,
                            center = Offset(size.width * 0.5f, size.height * 0.30f),
                            radius = size.maxDimension * 0.85f,
                        ),
                    )
                },
            )
            DiscAmbientArtwork(cover, Modifier.fillMaxSize().graphicsLayer { alpha = 0.38f })
            }
            if ("marquee" !in off) DiscGhostMarquee(
                text = song.artist.ifBlank { song.title }.lowercase(),
                color = ink.copy(alpha = 0.30f),
                clock = { clock.floatValue },
                modifier = Modifier.fillMaxSize(),
            )
            if ("vis" !in off) DiscVisualizer(
                style = DiscVisualizerStyle.AURORA,
                clock = { clock.floatValue },
                accent = glow,
                disc = {
                    with(density) {
                        Offset(width.toPx() / 2f, (discCentreY - discRise * panelT).toPx()) to discSize.toPx() / 2f
                    }
                },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - panelT * 0.45f },
            )
            if ("spheres" !in off) DiscWireSpheres(clock = { clock.floatValue }, tint = Color.White, modifier = Modifier.fillMaxSize())
            if ("scan" !in off) DiscScanlines(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen })

            // The disc, always the song's cover. A new cover rolls in from the side. Faded out
            // toward the top, so it passes under the status bar — and, as the sheet is pulled
            // down, under the notification shade's edge — softly rather than cut.
            val fadeTo = with(density) { (statusTop + DISC_FADE).toPx() }
            if ("disc" !in off) Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = fadeTo),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
            AnimatedContent(
                targetState = cover,
                transitionSpec = {
                    (slideInHorizontally(tween(900, easing = EASE_OUT_EXPO)) { -it } + fadeIn(tween(500))) togetherWith
                        (slideOutHorizontally(tween(650, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(450)))
                },
                // Wider than the screen, so laid out unbounded and centred across it, rather than
                // squeezed to the screen's width.
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentSize(Alignment.TopCenter, unbounded = true)
                    .offset(y = discCentreY - discSize / 2)
                    .graphicsLayer {
                        translationY = -discRise.toPx() * panelT
                        val s = 1f - 0.12f * panelT
                        scaleX = s
                        scaleY = s
                    }
                    // With a panel up the disc is the way back to the player.
                    .clickable(
                        enabled = pane != PlayerPane.Main,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBackToMain,
                    ),
                label = "disc",
            ) { art ->
                SpinningDisc(
                    artUrl = art,
                    angle = { discAngle.floatValue },
                    level = { AudioSpectrum.levelAt(System.nanoTime()).also { clock.floatValue } },
                    glow = glow,
                    modifier = Modifier.requiredSize(discSize),
                )
            }
            }
        }

        // ---- The words, the lyrics or the queue, and the deck ------------------------------
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets())),
        ) {
            // The mark, top left, as on a broadcast ident.
            Text(
                "BC².",
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                letterSpacing = (-1).sp,
                modifier = Modifier.padding(start = 20.dp, top = 10.dp).graphicsLayer { alpha = 1f - panelT },
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = PANEL_BAND * panelT),
            ) {
                AnimatedContent(
                    targetState = pane,
                    transitionSpec = { fadeIn(tween(360, delayMillis = 80)) togetherWith fadeOut(tween(200)) },
                    label = "discCentre",
                ) { showing ->
                    when (showing) {
                        PlayerPane.Main -> Column(
                            Modifier.fillMaxSize().padding(horizontal = PLAYER_GUTTER),
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            if (caption != null) {
                                Text(
                                    caption,
                                    color = Color.White.copy(alpha = 0.62f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.2.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onOpenCaption,
                                        ),
                                )
                            }
                            credits()
                            if (lyricStrip != null) {
                                Spacer(Modifier.height(10.dp))
                                lyricStrip()
                            }
                            Spacer(Modifier.height(18.dp))
                        }
                        PlayerPane.Lyrics -> Box(Modifier.fillMaxSize().padding(horizontal = PANE_GUTTER)) { lyricsPane() }
                        PlayerPane.Queue -> Box(Modifier.fillMaxSize().padding(horizontal = PANE_GUTTER)) { queuePane() }
                    }
                }
            }
            // The deck: every control the player has, on one pane of smoked glass. Plain
            // translucency rather than a live blur — the room behind it moves on every frame,
            // and a blur under it would be redone on every frame with it.
            if ("deck" !in off) Column(
                Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp)
                    .fillMaxWidth()
                    .clip(DECK_SHAPE)
                    .background(Color.Black.copy(alpha = 0.34f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), DECK_SHAPE)
                    .padding(horizontal = PLAYER_GUTTER - 12.dp, vertical = 14.dp),
            ) {
                scrubber()
                Spacer(Modifier.height(6.dp))
                transport()
                if (volume != null && pane == PlayerPane.Main) {
                    Spacer(Modifier.height(8.dp))
                    volume()
                }
                Spacer(Modifier.height(10.dp))
                actions()
            }
        }
    }
}

/** One revolution every this many seconds — a slow, legible spin rather than a blur. */
private const val DISC_PERIOD_S = 3.2f

/** How much of the disc stays in view, across the top, with the lyrics or the queue up. */
private val PANEL_BAND = 84.dp

private val DECK_SHAPE = RoundedCornerShape(30.dp)

/** Margin each side of the lyrics and the queue: the deck's own, so the three line up. */
private val PANE_GUTTER = 22.dp

/** How far below the status bar the disc has faded in fully. */
private val DISC_FADE = 56.dp

private val EASE_OUT_EXPO = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Layers of the disc player switched off for measuring, from the `debug.bc2.off` system property
 * (`adb shell setprop debug.bc2.off vis,marquee`): empty — everything on — anywhere else.
 */
private fun discLayersOff(): Set<String> = runCatching {
    Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
        .invoke(null, "debug.bc2.off") as String
}.getOrDefault("").split(',').map(String::trim).filter(String::isNotEmpty).toSet()
