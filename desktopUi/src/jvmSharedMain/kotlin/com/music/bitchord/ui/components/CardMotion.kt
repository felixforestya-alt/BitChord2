package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Whether cards move: lift under the pointer, show a play button, and slide in as a shelf
 * appears. A window with a mouse wants it; a phone, where nothing hovers and the feed is touched
 * rather than pointed at, keeps the still cards it has always had — so it is off unless a host
 * provides it.
 */
val LocalCardMotion = staticCompositionLocalOf { false }

/**
 * Pauses or resumes what is playing, for the button on the card of the thing playing now. Null
 * where a host has no player to hand; the button then only shows what is going on.
 */
val LocalCardPlayPause = staticCompositionLocalOf<(() -> Unit)?> { null }

/** The hover state a card's parts share: the lift on the whole card, the play button on its art. */
class CardHover internal constructor(val interaction: MutableInteractionSource)

@Composable
fun rememberCardHover(): CardHover = remember { CardHover(MutableInteractionSource()) }

/** Lifts and grows the card a little while the pointer is over it. */
@Composable
fun Modifier.cardHoverLift(hover: CardHover): Modifier {
    if (!LocalCardMotion.current) return this
    val hovered by hover.interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(
        if (hovered) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "cardLift",
    )
    return this
        .hoverable(hover.interaction)
        .graphicsLayer {
            scaleX = 1f + lift * 0.045f
            scaleY = 1f + lift * 0.045f
            translationY = -lift * 5.dp.toPx()
        }
}

/**
 * A faint light over a card's artwork while it is hovered. Placed inside the artwork's clip, so
 * it takes the artwork's rounded shape: the card's own click draws no highlight of its own,
 * which was a square over cover and captions alike.
 */
@Composable
fun BoxScope.CardHoverWash(hover: CardHover) {
    if (!LocalCardMotion.current) return
    val hovered by hover.interaction.collectIsHoveredAsState()
    val wash by animateFloatAsState(if (hovered) 1f else 0f, tween(140), label = "cardWash")
    if (wash <= 0.01f) return
    Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.10f * wash)))
}

/**
 * The round button that rises into the corner of a card's artwork while it is hovered. On the
 * card of what is playing now it stays, and is a pause button: pressing it pauses, and again
 * resumes, rather than starting the track over.
 */
@Composable
fun BoxScope.CardHoverPlay(
    hover: CardHover,
    alignment: Alignment = Alignment.BottomEnd,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    if (!LocalCardMotion.current) return
    val hovered by hover.interaction.collectIsHoveredAsState()
    val playPause = LocalCardPlayPause.current.takeIf { isCurrent }
    val shown by animateFloatAsState(
        if (hovered || (isCurrent && isPlaying)) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardPlay",
    )
    if (shown <= 0.01f) return
    Box(
        Modifier
            .align(alignment)
            .padding(10.dp)
            .size(42.dp)
            .graphicsLayer {
                alpha = shown.coerceIn(0f, 1f)
                scaleX = 0.6f + shown * 0.4f
                scaleY = 0.6f + shown * 0.4f
                translationY = (1f - shown) * 10.dp.toPx()
                shadowElevation = 12.dp.toPx()
                shape = CircleShape
                clip = true
            }
            .background(Color.White.copy(alpha = 0.92f))
            .border(0.5.dp, Color.White, CircleShape)
            .then(
                if (playPause != null) {
                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = playPause)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * Fades and slides a card up into place the first time it is shown, each a beat after the one
 * before it, so a shelf arrives as a ripple rather than all at once.
 */
@Composable
fun Modifier.cardEntrance(index: Int): Modifier {
    if (!LocalCardMotion.current) return this
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(MAX_STAGGERED) * STAGGER_MS)
        appear.animateTo(1f, tween(ENTRANCE_MS, easing = EASE_OUT_EXPO))
    }
    return graphicsLayer {
        alpha = appear.value
        translationY = (1f - appear.value) * 26.dp.toPx()
        val s = 0.94f + appear.value * 0.06f
        scaleX = s
        scaleY = s
    }
}

private const val STAGGER_MS = 45L
private const val MAX_STAGGERED = 10
private const val ENTRANCE_MS = 620
private val EASE_OUT_EXPO = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
