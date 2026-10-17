package com.music.bitchord.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import kotlinx.coroutines.launch

/**
 * The phone's player sheet, for a window: the shared player rises over the page
 * the way Android's full-screen bottom sheet brings it up, and goes back down
 * the same ways — a downward drag anywhere the player does not claim for
 * itself, a click on the handle it draws at the top, or Escape (which the
 * window routes through [com.music.bitchord.ui.player.PlayerBack] first, so the
 * lyrics or the queue close before the sheet does).
 *
 * Drawn inside the window rather than as Material's modal sheet: that one is a
 * focusable popup on the desktop, and would take the media keys and the
 * window's shortcuts away for as long as the player was up.
 */
@Composable
internal fun DesktopPlayerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    /**
     * Composed ahead, out of sight below the window's edge, before anyone asked for it — while the
     * pointer is over the way into it — so the first frames of opening are not also the frames
     * that build the whole player. See [DesktopPlayerWarmup].
     */
    prepare: Boolean = false,
    /**
     * What the player shows — the track. Each time it changes, the prepared player is primed again
     * once the new cover is in, so that cover's first trip to the GPU is not the first frame of
     * opening (where it took a sixth of a second).
     */
    primeKey: Any? = null,
    /** A collapse button in the corner, for a player that does not draw its own. */
    showCollapseButton: Boolean = false,
    content: @Composable BoxScope.(windowWidth: Dp, windowHeight: Dp) -> Unit,
) {
    // Mounted while it animates out, so the slide down is seen.
    var mounted by remember { mutableStateOf(visible) }
    if (visible || prepare) mounted = true
    val keepPrepared by androidx.compose.runtime.rememberUpdatedState(prepare)
    if (!mounted) return

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val offset = remember { Animatable(heightPx) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(visible) {
            if (visible) {
                // Two frames below the edge first: the player composes, lays out and compiles its
                // shaders out of sight, so the slide itself starts on a frame that is ready
                // instead of spending its first stretch on one long one.
                withFrameNanos { }
                withFrameNanos { }
                offset.animateTo(0f, tween(DesktopStartupWarmup.paced(SHEET_OPEN_MS), easing = FastOutSlowInEasing))
            } else {
                DesktopPlayerCover.covering = false
                offset.animateTo(heightPx, tween(DesktopStartupWarmup.paced(SHEET_CLOSE_MS), easing = FastOutSlowInEasing))
                // Kept, out of sight, while it is still being prepared for; gone once it is not.
                snapshotFlow { keepPrepared }.first { !it }
                mounted = false
            }
        }
        // Once it fills the window, the window under it need not be drawn at all.
        LaunchedEffect(visible) {
            snapshotFlow { visible && offset.value <= 0.5f }.collect { DesktopPlayerCover.covering = it; if (DesktopFrameProfiler.enabled) DesktopFrameProfiler.mark("covering $it") }
        }
        DisposableEffect(Unit) { onDispose { DesktopPlayerCover.covering = false } }
        // Built ahead of being opened: the whole opening — the slide up and the player's own
        // entrance — played through once in place at the faintest alpha there is, unseen, so
        // the GPU makes every texture and prepares every shader the opening needs. (A pass at
        // full strength through a one-pixel line, tried as well, showed as flicker across the
        // window, and the buffer below made it unnecessary.) The first real opening is then as smooth as every later one, instead of paying
        // for all that during its slide. A still frame alone was not enough: the opening draws
        // the player faded, turning and moving, and at sizes a still frame never shows.
        var priming by remember { mutableStateOf(false) }
        var faint by remember { mutableStateOf(false) }
        var primeFrame by remember { mutableStateOf(0) }
        val primeOffset = remember { Animatable(0f) }
        val primeEntrance = remember { Animatable(1f) }
        val shownNow by androidx.compose.runtime.rememberUpdatedState(visible)
        LaunchedEffect(primeKey, DesktopPlayerWarmup.primeAgain) {
            if (shownNow) return@LaunchedEffect
            // Opened for real behind the startup cloak already: every shader is made, and a new
            // track's cover is one small texture. Priming in view would only cost frames.
            if (warmedForReal && DesktopPlayerWarmup.primeAgain == 0) return@LaunchedEffect
            // Time for the new cover to arrive and be worked up before it is drawn.
            if (DesktopPlayerWarmup.primeAgain == 0) kotlinx.coroutines.delay(PRIME_DELAY_MS)
            if (shownNow) return@LaunchedEffect
            withFrameNanos { }
            DesktopTrackLog.log("player: priming for $primeKey")
            DesktopPlayerWarmup.heat(PRIME_HEAT_MS)
            // Slow the first time, when every shader is still to be made; quick after.
            val primeSlower = if (primedOnce) 1 else PRIME_SLOWER
            try {
                run {
                    primeOffset.snapTo(heightPx)
                    primeEntrance.snapTo(0f)
                    faint = true
                    priming = true
                    val play: suspend () -> Unit = {
                        kotlinx.coroutines.coroutineScope {
                            // Played slower than the real thing: every new kind of draw costs the GPU
                            // a shader compile on the frame it first appears, and at full speed
                            // several landed on one frame — a hitch of a hundred milliseconds or more
                            // on the page behind. Drawn out, they come a few to a frame.
                            launch { primeOffset.animateTo(0f, tween(SHEET_OPEN_MS * primeSlower, easing = FastOutSlowInEasing)) }
                            // Counts the frames, for the two pixels below.
                            val sweep = launch { while (true) withFrameNanos { primeFrame++ } }
                            primeEntrance.animateTo(1f, tween(PLAYER_ENTRANCE_MS * primeSlower, easing = PLAYER_ENTRANCE_EASING))
                            // And held there a few frames: the frame the entrance ends on is the
                            // first with everything opaque and in place, and ending the priming
                            // there, that was never drawn.
                            repeat(PRIME_HOLD_FRAMES) { withFrameNanos { } }
                            sweep.cancel()
                        }
                    }
                    if (DesktopFrameProfiler.enabled) DesktopFrameProfiler.measure("priming", 160, play) else play()
                // Shaders are made once a launch: later primings (a new track's cover) need not crawl.
                primedOnce = true
                }
            } finally {
                priming = false
                faint = false
                primeFrame = 0
            }
        }
        // Never while it is really up: an opening started mid-priming is drawn as itself.
        val primingNow = priming && !visible
        // Lets the player inside start its entrance when it is shown, not when it was prepared.
        androidx.compose.runtime.CompositionLocalProvider(
            LocalPlayerSheetShown provides (visible || primingNow),
            LocalPlayerSheetOpen provides visible,
            LocalPlayerPrimeEntrance provides if (primingNow) primeEntrance.asState() else null,
        ) {

        val dragState = rememberDraggableState { delta ->
            scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, heightPx)) }
        }
        Box(
            Modifier
                .fillMaxSize()
                // Clipped to itself: parts of the player drawn larger than it (the blurred cover
                // behind everything is scaled past its edges) showed above the window's bottom
                // while it was put away or on its way down.
                .graphicsLayer {
                    translationY = when {
                        !primingNow -> offset.value
                        else -> primeOffset.value
                    }
                    clip = true
                    alpha = if (primingNow && faint) 1f / 255f else 1f
                    // Always drawn through a buffer of its own, open or primed alike. Direct3D
                    // compiles each shader the first time it is needed, on the drawing thread,
                    // and a shader for drawing into a buffer is another one than for drawing
                    // straight onto the window: primed faint (so through a buffer) and opened
                    // straight, the first opening compiled the whole player's shaders over
                    // again, mid-slide — that was nearly all of its long frames. Through a
                    // buffer both ways, the opening finds them made; the one more full-window
                    // copy a frame is nothing to the GPU.
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    shape = androidx.compose.ui.graphics.RectangleShape
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        if (offset.value > heightPx * DISMISS_FRACTION || velocity > DISMISS_VELOCITY) {
                            onDismiss()
                        } else {
                            offset.animateTo(0f, tween(SHEET_SETTLE_MS, easing = FastOutSlowInEasing))
                        }
                    },
                ),
        ) {
            content(windowWidth, windowHeight)
            // The spinners the player shows while the video or the track loads, drawn once in the
            // faint pass too: their arcs are a shape of their own for the GPU, and one first seen
            // mid-opening was prepared on the spot.
            if (primingNow && faint) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.align(Alignment.Center).size(30.dp), strokeWidth = 1.5.dp)
                androidx.compose.material3.CircularProgressIndicator(Modifier.align(Alignment.BottomCenter).size(40.dp), strokeWidth = 3.dp)
            }
            // The window is still a window with the player up: a strip along the top to take hold
            // of it by, and its own minimise / maximise / close at the end of it.
            if (DesktopPlatform.drawsOwnWindowFrame) {
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(WIN_CAPTION_HEIGHT)) {
                    DesktopTitleBarDragArea(Modifier.fillMaxSize())
                    DesktopWindowButtons(
                        Modifier.align(if (DesktopCaptionOnRight) Alignment.TopEnd else Alignment.TopStart),
                    )
                }
            }
            // The handle the landscape player draws at its top edge, made to do
            // on a click what it promises for a drag.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(HANDLE_HIT_WIDTH_FRACTION)
                    .height(HANDLE_HIT_HEIGHT)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            if (showCollapseButton) {
                // Put away with a click, not only a drag.
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 18.dp, top = 18.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = if (hovered) 0.55f else 0.35f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .hoverable(interaction)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        DesktopStrings["d_collapse_player", "Collapse"],
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        // While priming: two pixels drawn the way the sheet itself is put on the window — through
        // a buffer of its own, at full strength, unclipped, now on whole pixels and now between
        // them as it is mid-slide. All but transparent, so nothing shows; but the GPU compiles
        // the shaders for putting a buffer on the window that way, which the first opening
        // otherwise compiled on its own first frames. The sheet's own priming cannot: faint,
        // its buffer goes on another way.
        if (primingNow) {
            Box(
                Modifier
                    .size(2.dp)
                    .graphicsLayer {
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        val between = primeFrame % 2 == 1
                        translationX = if (between) 0.37f else 0f
                        translationY = if (between) 0.61f else 0f
                    }
                    .drawBehind { drawRect(Color.Black.copy(alpha = 1f / 255f)) },
            )
        }
        }
    }
}

private const val SHEET_OPEN_MS = 360
private const val SHEET_CLOSE_MS = 280
private const val SHEET_SETTLE_MS = 220
private const val DISMISS_FRACTION = 0.25f
private const val DISMISS_VELOCITY = 1_600f
private const val HANDLE_HIT_WIDTH_FRACTION = 0.2f
private val HANDLE_HIT_HEIGHT = 24.dp

/**
 * Whether the player covers the whole window, so that what is under it — the page, the sidebar,
 * their moving backdrops and glass — can skip drawing rather than be painted every frame unseen.
 */
internal object DesktopPlayerCover {
    var covering by androidx.compose.runtime.mutableStateOf(false)
}

/** Whether the player sheet around this is showing, as against composed ahead out of sight. */
internal val LocalPlayerSheetShown = androidx.compose.runtime.compositionLocalOf { true }

/** Whether it is really open — not only being prepared — for what must not start until then. */
internal val LocalPlayerSheetOpen = androidx.compose.runtime.compositionLocalOf { true }

/**
 * The player's entrance as the priming plays it, or null when it is not being primed: the player
 * follows it instead of running an entrance of its own. See [DesktopPlayerSheet].
 */
internal val LocalPlayerPrimeEntrance =
    androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.State<Float>?> { null }

/** How long the player's own entrance takes, and its curve — the priming plays the same. */
internal const val PLAYER_ENTRANCE_MS = 1_100
internal val PLAYER_ENTRANCE_EASING = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

private const val PRIME_HOLD_FRAMES = 6
private const val PRIME_SLOWER = 4

/** Whether the player has been primed once this launch — see [PRIME_SLOWER]. */
@Volatile private var primedOnce = false

/** Whether the player was opened for real behind the startup cloak — see [DesktopStartupWarmup]. */
@Volatile private var warmedForReal = false
private const val PRIME_HEAT_MS = 300L
private const val PRIME_DELAY_MS = 1_500L

/**
 * The pointer is over a way into the player: have it built before the click — see
 * [DesktopPlayerSheet]'s `prepare`. Lapses a few seconds after the last sign of interest, so a
 * player nobody opened is not kept composed.
 */
internal object DesktopPlayerWarmup {
    /** The player has been opened for real already (see [DesktopStartupWarmup]): priming need not crawl. */
    fun markPrimed() {
        primedOnce = true
        warmedForReal = true
    }

    /** Bumped to have the prepared player primed once more, at once. */
    var primeAgain by androidx.compose.runtime.mutableIntStateOf(0)
    var wanted by mutableStateOf(false)
        private set
    private var lastAsk = 0L

    fun request() {
        lastAsk = System.nanoTime()
        if (!wanted) wanted = true
        rampUntil = lastAsk + RAMP_NANOS
    }

    @Volatile private var rampUntil = 0L

    /**
     * The warming-up drawn for [millis] without asking for the player: so the priming draws it
     * once too, and its blur is not first compiled by the GPU on the first real opening.
     */
    fun heat(millis: Long) {
        rampUntil = maxOf(rampUntil, System.nanoTime() + millis * 1_000_000L)
    }

    /** Whether the GPU is being warmed up right now. */
    val ramping: Boolean get() = System.nanoTime() < rampUntil

    /**
     * Frames drawn while the pointer is on a way into the player, for a second after it last moved
     * there. A window that has been still lets the GPU drop to its idle clocks, and an animation
     * that starts from those plays its first frames slowly while the driver winds back up; this
     * gets the winding up done before the click instead of during the slide. Nothing is drawn
     * otherwise — a still window stays still.
     */
    suspend fun keepGpuAwake() {
        while (true) {
            if (System.nanoTime() < rampUntil) {
                androidx.compose.runtime.withFrameNanos { }
                ramp.intValue++
            } else {
                kotlinx.coroutines.delay(50)
            }
        }
    }

    /** Read by the window's root draw, so each ramp tick draws a frame. */
    val ramp = androidx.compose.runtime.mutableIntStateOf(0)

    private const val RAMP_NANOS = 1_000_000_000L

    /** Runs for the app's life; lets [wanted] go once nothing has asked for a while. */
    suspend fun expire() {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            if (wanted && System.nanoTime() - lastAsk > WARM_HOLD_NANOS) wanted = false
        }
    }

    private const val WARM_HOLD_NANOS = 8_000_000_000L
}

/** Asks for [DesktopPlayerWarmup] whenever the pointer moves over this. */
internal fun Modifier.warmsPlayer(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            DesktopPlayerWarmup.request()
        }
    }
}
