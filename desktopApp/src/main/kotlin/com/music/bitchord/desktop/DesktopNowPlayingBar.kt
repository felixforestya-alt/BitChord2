package com.music.bitchord.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.delay

/**
 * What is playing, along the bottom of the main window: a floating liquid-glass bar with the
 * transport, a seek bar that can be clicked or dragged, the volume, and the way into the player.
 * Where it goes (or whether the old top-bar display is used instead) is a setting.
 */
@Composable
internal fun DesktopNowPlayingBar(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    position: PlaybackPosition,
    durationMs: Long,
    hasPrevious: Boolean,
    shuffle: Boolean,
    /** 0 off, 1 all, 2 one. */
    repeat: Int,
    volume: Float,
    lyricsOpen: Boolean,
    queueOpen: Boolean,
    compact: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    /** False when something else draws the bar's glass and shape around it — the home card's morph. */
    framed: Boolean = true,
) {
    val palette = rememberVividPalette(song.thumbnailUrl)
    val accent by animateColorAsState(palette.vivid, tween(1_200), label = "barAccent")
    // The seek bar moves with every frame the display shows while something is playing, and
    // rests otherwise.
    val clock = rememberFrameClock(isPlaying)
    // A pill, like the drop the top player becomes.
    val barShape = RoundedCornerShape(BAR_CORNER)
    val tinted by DesktopStyleSettings.barTinted.collectAsState()
    // The level the mute button brings back.
    val lastAudible = remember { mutableFloatStateOf(volume.takeIf { it > 0.005f } ?: 0.6f) }
    LaunchedEffect(volume) { if (volume > 0.005f) lastAudible.floatValue = volume }
    Row(
        modifier
            // The pointer on the bar: the player behind it gets built ahead of a click.
            .warmsPlayer()
            .then(
                if (framed) {
                    Modifier
                        .widthIn(max = if (compact) 560.dp else 1080.dp)
                        .fillMaxWidth()
                        .height(if (compact) 64.dp else 76.dp)
                        .desktopLiquidGlass(BAR_CORNER, tint = Color.Black.copy(alpha = 0.46f), blurRadius = 8.dp) {
                            desktopBarGlass(barShape)
                        }
                        .clip(barShape)
                        // The cover's colour through the glass, the same the top player wears.
                        .then(if (tinted) Modifier.background(desktopBarTint(accent)) else Modifier)
                } else {
                    Modifier.fillMaxWidth().height(if (compact) 64.dp else 76.dp)
                },
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The track: a click opens the player.
        Row(
            Modifier
                .then(if (compact) Modifier.weight(1f) else Modifier.width(260.dp))
                // Concentric with the pill around it: its corner is the bar's, less the gap
                // between them, so the highlight under the pointer follows the bar's own shape.
                .clip(RoundedCornerShape(BAR_CORNER - 7.dp))
                .clickable(onClick = onExpand)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(if (compact) 44.dp else 54.dp)
                    .graphicsLayer {
                        shadowElevation = 10.dp.toPx()
                        shape = RoundedCornerShape(14.dp)
                        clip = true
                    },
            ) {
                DesktopArtwork(song.thumbnailUrl.artworkAt(160) ?: song.thumbnailUrl, Modifier.matchParentSize())
            }
            Spacer(Modifier.width(12.dp))
            AnimatedContent(
                targetState = song,
                contentKey = { it.videoId },
                transitionSpec = {
                    (slideInVertically(tween(420)) { it } + fadeIn(tween(320))) togetherWith
                        (slideOutVertically(tween(300)) { -it } + fadeOut(tween(220)))
                },
                label = "barTrack",
            ) { current ->
                Column {
                    Text(current.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(current.artist, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (compact) {
            BarButton(Icons.Rounded.SkipPrevious, "Previous", onClick = onPrevious)
            BarPlayButton(isPlaying, isLoading, onPlayPause)
            BarButton(Icons.Rounded.SkipNext, "Next", onClick = onNext)
            return@Row
        }

        // The transport over the seek bar.
        Column(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BarButton(BitChordIcons.Shuffle, "Shuffle", size = 17.dp, tint = if (shuffle) accentInk(accent) else BAR_IDLE, onClick = onToggleShuffle)
                BarButton(Icons.Rounded.SkipPrevious, "Previous", enabled = true, onClick = onPrevious)
                BarPlayButton(isPlaying, isLoading, onPlayPause)
                BarButton(Icons.Rounded.SkipNext, "Next", onClick = onNext)
                Box(contentAlignment = Alignment.Center) {
                    BarButton(BitChordIcons.Repeat, "Repeat", size = 17.dp, tint = if (repeat != 0) accentInk(accent) else BAR_IDLE, onClick = onCycleRepeat)
                    if (repeat == 2) Text("1", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(x = 8.dp, y = (-7).dp))
                }
            }
            DesktopSeekBar(
                position = position,
                durationMs = durationMs,
                isPlaying = isPlaying,
                clock = { clock.floatValue },
                accent = accent,
                onSeek = onSeek,
            )
        }

        // The rest: words, queue, loudness, and the way up into the player.
        Row(Modifier.width(260.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            BarButton(BitChordIcons.LyricsQuote, DesktopStrings["lyrics", "Lyrics"], size = 18.dp, tint = if (lyricsOpen) Color.White else BAR_IDLE, onClick = onOpenLyrics)
            BarButton(BitChordIcons.Queue, DesktopStrings["queue", "Queue"], size = 18.dp, tint = if (queueOpen) Color.White else BAR_IDLE, onClick = onOpenQueue)
            // The speaker mutes, and brings back what it muted.
            BarButton(
                if (volume <= 0.005f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                if (volume <= 0.005f) DesktopStrings["d_unmute", "Unmute"] else DesktopStrings["d_mute", "Mute"],
                size = 17.dp,
                tint = if (volume <= 0.005f) Color(0xFFFF6B6B) else BAR_IDLE,
                onClick = { onVolumeChange(if (volume <= 0.005f) lastAudible.floatValue else 0f) },
            )
            DesktopThinSlider(
                value = volume,
                onValueChange = onVolumeChange,
                idleHeight = 4.dp,
                activeHeight = 8.dp,
                modifier = Modifier.padding(start = 8.dp).width(88.dp),
            )
            BarButton(Icons.Rounded.KeyboardArrowUp, DesktopStrings["d_open_player", "Open the player"], size = 22.dp, onClick = onExpand)
        }
    }
}

/**
 * The home card's controls along its bottom: the transport, a seek bar to click or drag, and the
 * volume with its mute — what the bar has, laid out for the card.
 */
@Composable
internal fun DesktopHeroTransport(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    position: PlaybackPosition,
    durationMs: Long,
    shuffle: Boolean,
    /** 0 off, 1 all, 2 one. */
    repeat: Int,
    volume: Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    val palette = rememberVividPalette(song.thumbnailUrl)
    val accent by animateColorAsState(palette.vivid, tween(1_200), label = "heroTransportAccent")
    val clock = rememberFrameClock(isPlaying)
    val lastAudible = remember { mutableFloatStateOf(volume.takeIf { it > 0.005f } ?: 0.6f) }
    LaunchedEffect(volume) { if (volume > 0.005f) lastAudible.floatValue = volume }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BarButton(BitChordIcons.Shuffle, "Shuffle", size = 17.dp, tint = if (shuffle) Color.White else BAR_IDLE.copy(alpha = 0.6f), onClick = onToggleShuffle)
        BarButton(Icons.Rounded.SkipPrevious, "Previous", onClick = onPrevious)
        BarPlayButton(isPlaying, isLoading, onPlayPause)
        BarButton(Icons.Rounded.SkipNext, "Next", onClick = onNext)
        Box(contentAlignment = Alignment.Center) {
            BarButton(BitChordIcons.Repeat, "Repeat", size = 17.dp, tint = if (repeat != 0) Color.White else BAR_IDLE.copy(alpha = 0.6f), onClick = onCycleRepeat)
            if (repeat == 2) Text("1", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(x = 8.dp, y = (-7).dp))
        }
        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            DesktopSeekBar(
                position = position,
                durationMs = durationMs,
                isPlaying = isPlaying,
                clock = { clock.floatValue },
                accent = accent,
                onSeek = onSeek,
            )
        }
        BarButton(
            if (volume <= 0.005f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
            if (volume <= 0.005f) DesktopStrings["d_unmute", "Unmute"] else DesktopStrings["d_mute", "Mute"],
            size = 17.dp,
            tint = if (volume <= 0.005f) Color(0xFFFF6B6B) else BAR_IDLE,
            onClick = { onVolumeChange(if (volume <= 0.005f) lastAudible.floatValue else 0f) },
        )
        DesktopThinSlider(
            value = volume,
            onValueChange = onVolumeChange,
            idleHeight = 4.dp,
            activeHeight = 8.dp,
            modifier = Modifier.padding(start = 6.dp, end = 4.dp).width(96.dp),
        )
    }
}

@Composable
private fun BarPlayButton(isPlaying: Boolean, isLoading: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.1f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "barPlay")
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .size(38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Color.White)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
        } else {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = { (scaleIn(tween(200)) + fadeIn(tween(150))) togetherWith (scaleOut(tween(150)) + fadeOut(tween(110))) },
                label = "barPlayIcon",
            ) { playing ->
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun BarButton(
    icon: ImageVector,
    label: String,
    size: Dp = 22.dp,
    tint: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val wash by animateFloatAsState(if (hovered) 0.12f else 0f, tween(140), label = "barWash")
    Box(
        Modifier
            .size(size + 14.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = wash))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(size))
    }
}

private fun accentInk(accent: Color): Color = androidx.compose.ui.graphics.lerp(accent, Color.White, 0.3f)

/** An idle control on the glass: white enough to read over a bright page behind it. */
private val BAR_IDLE = Color.White.copy(alpha = 0.78f)

/**
 * Seconds, advanced on every frame the display draws while [running] — at the monitor's own rate,
 * 60, 144 or whatever it is — and still otherwise. What reads it in a draw redraws with it.
 */
@Composable
internal fun rememberFrameClock(running: Boolean): androidx.compose.runtime.MutableFloatState {
    val clock = remember { mutableFloatStateOf(0f) }
    // Still while nobody is looking at the window — see AppActivity.
    val quiet = com.music.bitchord.ui.AppActivity.quiet
    LaunchedEffect(running, quiet) {
        if (!running || quiet) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            com.music.bitchord.ui.awaitAmbientFrame { now ->
                clock.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    return clock
}

/** The bar's corner: half its height, a pill. */
private val BAR_CORNER = 38.dp

/**
 * The floating bars' colour when they take the cover's: the home card's own gradient, laid thinner
 * so the glass still reads through it.
 */
internal fun desktopBarTint(accent: Color): androidx.compose.ui.graphics.Brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
    listOf(
        accent.hueShifted(0f, 0.42f).copy(alpha = 0.72f),
        accent.hueShifted(-30f, 0.16f).copy(alpha = 0.55f),
        Color(0xFF0C0C10).copy(alpha = 0.45f),
    ),
)
