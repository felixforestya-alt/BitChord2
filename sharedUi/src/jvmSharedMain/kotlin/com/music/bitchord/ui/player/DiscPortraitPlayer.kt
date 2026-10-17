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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.ui.zIndex
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
    /** Where the track is in the queue: a later one rolls in from the right, an earlier from the left. */
    trackIndex: Int,
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
    val glass = PlayerPlatform.host.rememberPlayerGlass()
    // Which way the disc moved last: forward through the queue it leaves to the left and the next
    // one comes in from the right, as a swipe forward does; back, the other way.
    val lastIndex = remember { androidx.compose.runtime.mutableIntStateOf(trackIndex) }
    val forward = remember { androidx.compose.runtime.mutableStateOf(true) }
    if (trackIndex != lastIndex.intValue) {
        forward.value = trackIndex > lastIndex.intValue
        lastIndex.intValue = trackIndex
    }

    val showHeading by PlayerSettings.discHeading.collectAsStateWithLifecycle()

    // Clipped to the sheet: pulled down, the disc goes with it rather than hanging over the page.
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        val width = maxWidth
        val height = maxHeight
        // On its side the phone takes the desktop's shape: the disc down the left, and everything
        // else in a column beside it.
        val wide = width > height
        val statusTop = with(density) { stableStatusBarInsets().getTop(this).toDp() }
        // Where the front camera is: the hole in the display the disc's own hole is set over, so
        // the camera reads as the middle of the CD. The cutout's band across the top (or down the
        // side, on its side) is centred on the hole; with none, the status bar stands in for it.
        val cutout = WindowInsets.displayCutout
        val cutoutTop = with(density) { cutout.getTop(this).toDp() }
        val cutoutLeft = with(density) { cutout.getLeft(this, androidx.compose.ui.unit.LayoutDirection.Ltr).toDp() }
        // Upright: the disc's lower edge where it has always been, two thirds of a screen-wide disc
        // below the top, and its middle on the camera — so it is as big as that makes it.
        val discBottom = statusTop + min(width * 0.94f, height * 0.5f) * 0.68f
        val cameraY = if (cutoutTop > 0.dp) cutoutTop / 2 else statusTop / 2
        val cameraX = if (cutoutLeft > 0.dp) cutoutLeft / 2 else width * 0.04f
        val discSize = if (wide) height * 1.36f else (discBottom - cameraY) * 2
        val discCentreX = if (wide) cameraX else width / 2
        val discCentreY = if (wide) height / 2 else cameraY
        // Risen with a panel up: a band of disc across the top. On its side the disc stays put and
        // the panel takes the column beside it.
        val discRise = if (wide) 0.dp else discCentreY + discSize * 0.5f - (statusTop + PANEL_BAND)
        // Where everything that is not the disc begins, on its side.
        val stageStart = if (wide) discCentreX + discSize * 0.5f + 20.dp else 0.dp

        // ---- The room, and everything the player's sheets frost ---------------------------
        Box(
            Modifier
                .fillMaxSize()
                .then(if ("haze" in off) Modifier else hazeSource)
                .let { if (glass != null) glass.source(it) else it },
        ) {
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
                                center = Offset(size.width * (if (wide) 0.62f else 0.5f), size.height * (if (wide) 0.4f else 0.30f)),
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
                        Offset(discCentreX.toPx(), (discCentreY - discRise * panelT).toPx()) to discSize.toPx() / 2f
                    }
                },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - panelT * 0.45f },
            )
            if ("spheres" !in off) DiscWireSpheres(clock = { clock.floatValue }, tint = Color.White, modifier = Modifier.fillMaxSize())
            if ("scan" !in off) DiscScanlines(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen })

            // The disc, always the song's cover. A new cover rolls in from the side. Upright it
            // fades out above the camera, so the sliver over it passes softly under the status bar
            // and, as the sheet is pulled down, under the shade's edge.
            val fadeTo = with(density) { cameraY.toPx() * 0.7f }
            // A shade behind the status bar, so its clock and icons read over the print.
            if (!wide) Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusTop + 16.dp)
                    .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.5f), 1f to Color.Transparent))
                    .zIndex(1f),
            )
            if ("disc" !in off) Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (!wide && fadeTo > 0f) {
                            drawRect(
                                Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = fadeTo),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    },
            ) {
                AnimatedContent(
                    targetState = cover,
                    transitionSpec = {
                        val dir = if (forward.value) 1 else -1
                        (slideInHorizontally(tween(900, easing = EASE_OUT_EXPO)) { it * dir } + fadeIn(tween(500))) togetherWith
                            (slideOutHorizontally(tween(650, easing = FastOutSlowInEasing)) { -it * dir } + fadeOut(tween(450)))
                    },
                    // Bigger than the screen, so laid out unbounded and placed by its middle,
                    // rather than squeezed to fit.
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset(x = discCentreX - discSize / 2, y = discCentreY - discSize / 2)
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
        // A little larger than the app's own scale upright, where the player is the one screen
        // made to be looked at from arm's length; on its side a little smaller, to fit the height.
        val uiScale = if (wide) WIDE_UI_SCALE else UI_SCALE
        androidx.compose.runtime.CompositionLocalProvider(
            LocalDensity provides androidx.compose.ui.unit.Density(density.density * uiScale, density.fontScale),
        ) {
            val centre: @Composable (Modifier) -> Unit = { centreModifier ->
                Box(centreModifier.padding(top = if (wide) 0.dp else PANEL_BAND * panelT)) {
                    AnimatedContent(
                        targetState = pane,
                        transitionSpec = { fadeIn(tween(360, delayMillis = 80)) togetherWith fadeOut(tween(200)) },
                        label = "discCentre",
                    ) { showing ->
                        when (showing) {
                            PlayerPane.Main -> Column(
                                Modifier
                                    .fillMaxSize()
                                    // On its side, no wider than reads as one block, so the like and
                                    // menu buttons stay beside the track rather than across the room.
                                    .then(if (wide) Modifier.wrapContentWidth(Alignment.Start).widthIn(max = 470.dp) else Modifier)
                                    .padding(horizontal = if (wide) 8.dp else PLAYER_GUTTER),
                                verticalArrangement = if (wide) Arrangement.Center else Arrangement.Bottom,
                            ) {
                                if (caption != null) {
                                    // Where the session came from, on one line and in one type:
                                    // "from" and the place, named in the phone's language.
                                    val source = song.playbackSource?.takeIf { it.isNotBlank() }
                                    Text(
                                        (if (source != null) "${fromLabel()} ${localisedSource(source)}" else caption).uppercase(),
                                        color = Color.White.copy(alpha = 0.62f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .padding(bottom = 10.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = onOpenCaption,
                                            ),
                                    )
                                }
                                // The desktop's heading, as big as the room allows — unless
                                // Settings has it off.
                                if (showHeading) {
                                    Text(
                                        nowPlayingHeading(),
                                        color = Color.White,
                                        fontFamily = androidx.compose.material3.MaterialTheme.typography.displayLarge.fontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (wide) 40.sp else 46.sp,
                                        letterSpacing = (-1.4).sp,
                                        maxLines = 1,
                                        style = androidx.compose.ui.text.TextStyle(
                                            shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 6f), 28f),
                                            lineHeight = if (wide) 42.sp else 48.sp,
                                            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                                androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                                androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                                            ),
                                        ),
                                        modifier = Modifier.padding(bottom = if (wide) 10.dp else 8.dp),
                                    )
                                }
                                // The track, its artist and the line being sung, larger than the
                                // app sets them; the like and menu buttons down by the artist,
                                // clear of the title above and the line below.
                                val here = LocalDensity.current
                                // Upright the buttons hang just below the title, by the artist;
                                // on its side, where the words are one short block, they stand
                                // level with the track beside it.
                                val drop = if (wide) 0.dp else CREDITS_BUTTONS_DROP
                                androidx.compose.runtime.CompositionLocalProvider(
                                    LocalDensity provides androidx.compose.ui.unit.Density(
                                        here.density * (if (wide) WIDE_WORDS_SCALE else WORDS_SCALE),
                                        here.fontScale * (if (wide) WIDE_WORDS_SCALE else WORDS_FONT_SCALE),
                                    ),
                                    LocalCreditsButtonsPlacement provides CreditsButtonsPlacement(
                                        if (wide) Alignment.CenterVertically else Alignment.Bottom,
                                        drop,
                                    ),
                                ) {
                                    Column {
                                        // Above the line below in touch as in drawing: the buttons
                                        // hang down beside the artist, over the gap, taking no room.
                                        Box(Modifier.zIndex(1f)) { credits() }
                                        if (lyricStrip != null) {
                                            // Just clear of the buttons hanging below the artist.
                                            Spacer(Modifier.height(if (wide) 8.dp else drop - 4.dp))
                                            lyricStrip()
                                        }
                                    }
                                }
                                Spacer(Modifier.height(if (wide) 0.dp else 14.dp))
                            }
                            PlayerPane.Lyrics -> Box(Modifier.fillMaxSize().padding(horizontal = if (wide) 4.dp else PANE_GUTTER)) {
                                // The line being sung in the middle of the stretch, as on the desktop,
                                // and the pane's toggles big enough to hit, clear of the deck.
                                androidx.compose.runtime.CompositionLocalProvider(
                                    LocalLyricsFocusFraction provides 0.42f,
                                    LocalLyricsPaneControls provides LyricsPaneControls(scale = 1.3f, bottomGap = 14.dp),
                                ) { lyricsPane() }
                            }
                            PlayerPane.Queue -> Box(Modifier.fillMaxSize().padding(horizontal = if (wide) 4.dp else PANE_GUTTER)) { queuePane() }
                        }
                    }
                }
            }
            // The deck: every control the player has, on one pane of liquid glass where the phone
            // can draw it, smoked glass otherwise.
            val deck: @Composable (Modifier) -> Unit = { deckModifier ->
                if ("deck" !in off) Column(
                    deckModifier
                        .let { d ->
                            if (glass != null) {
                                glass.pane(d, DECK_SHAPE)
                            } else {
                                d.clip(DECK_SHAPE)
                                    .background(Color.Black.copy(alpha = 0.34f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), DECK_SHAPE)
                            }
                        }
                        .padding(horizontal = PLAYER_GUTTER - 12.dp, vertical = if (wide) 8.dp else 14.dp),
                ) {
                    scrubber()
                    Spacer(Modifier.height(6.dp))
                    if (wide) {
                        // On its side the deck is low and wide: the transport and the actions on
                        // one line, and no volume — the phone's own keys have it.
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            // The transport a size down on its side, to keep the deck low.
                            val deckDensity = LocalDensity.current
                            androidx.compose.runtime.CompositionLocalProvider(
                                LocalDensity provides androidx.compose.ui.unit.Density(deckDensity.density * 0.8f, deckDensity.fontScale),
                            ) {
                                Box(Modifier.weight(1f)) { transport() }
                            }
                            Spacer(Modifier.width(24.dp))
                            Column(Modifier.weight(1f)) { actions() }
                        }
                    } else {
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
            if (wide) {
                // On its side: the words, the lyrics or the queue in the space beside the disc,
                // and the deck low and wide along the foot of it.
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets()))
                        .padding(start = stageStart / uiScale, top = 6.dp, bottom = 8.dp, end = 12.dp),
                ) {
                    centre(Modifier.weight(1f).fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    deck(Modifier.fillMaxWidth())
                }
            } else {
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
                    centre(Modifier.fillMaxWidth().weight(1f))
                    deck(Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp).fillMaxWidth())
                }
            }
        }
    }
}

/** "From", in the phone's language. */
private fun fromLabel(): String =
    if (java.util.Locale.getDefault().language == "ru") "ИЗ" else "FROM"

/** A source as the feed names it, in the phone's language where it is one of the feed's own. */
private fun localisedSource(source: String): String =
    if (source.equals("Recents", ignoreCase = true) && java.util.Locale.getDefault().language == "ru") "Недавние" else source

/** The desktop's heading over the track, in the phone's language. */
private fun nowPlayingHeading(): String =
    if (java.util.Locale.getDefault().language == "ru") "сейчас играет." else "now playing."

/** How much larger than the app's own scale the player's words and deck are drawn, upright. */
private const val UI_SCALE = 1.08f

/** And on its side, smaller, to fit the height. */
private const val WIDE_UI_SCALE = 0.9f

/** And the track's own words and their buttons, larger again. */
private const val WORDS_SCALE = 1.12f
private const val WORDS_FONT_SCALE = 1.14f

/** How far the like and menu buttons hang below the artist, to the right of the words. */
private val CREDITS_BUTTONS_DROP = 16.dp

/** On its side the track's words are larger still: there is width to spare for them. */
private const val WIDE_WORDS_SCALE = 1.3f

/** One revolution every this many seconds — a slow, legible spin rather than a blur. */
private const val DISC_PERIOD_S = 3.2f

/** How much of the disc stays in view, across the top, with the lyrics or the queue up. */
private val PANEL_BAND = 84.dp

private val DECK_SHAPE = RoundedCornerShape(30.dp)

/** Margin each side of the lyrics and the queue: the deck's own, so the three line up. */
private val PANE_GUTTER = 22.dp

private val EASE_OUT_EXPO = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Layers of the disc player switched off for measuring, from the `debug.bc2.off` system property
 * (`adb shell setprop debug.bc2.off vis,marquee`): empty — everything on — anywhere else.
 */
private fun discLayersOff(): Set<String> = runCatching {
    Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
        .invoke(null, "debug.bc2.off") as String
}.getOrDefault("").split(',').map(String::trim).filter(String::isNotEmpty).toSet()
