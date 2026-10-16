package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import java.awt.MouseInfo

// The window's own frame, drawn by the application rather than by the system.

/** The strip where the system's title bar would have been. */
@Composable
internal fun DesktopTitleBar() {
    // Collected rather than read: [DesktopTitleBarSetting.active] is a plain call, so a composable
    // that only asks it never learns the answer changed.
    val enabled by DesktopTitleBarSetting.enabled.collectAsState()
    if (!DesktopPlatform.drawsOwnWindowFrame || !enabled) return
    DesktopTitleBarDragArea(
        Modifier
            .fillMaxWidth()
            .height(if (DesktopPlatform.isMac) CAPTION_HEIGHT else WIN_CAPTION_HEIGHT)
            .desktopWindowGlass(),
    ) {
        Box(Modifier.fillMaxSize()) {
            DesktopWindowButtons(
                Modifier.align(if (DesktopPlatform.isMac) Alignment.CenterStart else Alignment.CenterEnd),
            )
        }
    }
}

/**
 * Where the caption buttons go: macOS leads with its traffic lights, Windows ends with
 * minimise / maximise / close, as every other window on that desktop does.
 */
internal val DesktopCaptionOnRight: Boolean get() = !DesktopPlatform.isMac

/** Whether there is a title bar at all. */
internal object DesktopTitleBarSetting {

    internal const val KEY = "window_title_bar"

    private val _enabled = MutableStateFlow(DesktopPersistence().boolean(KEY, false))

    /** What Settings shows and writes. */
    val enabled: StateFlow<Boolean> = _enabled

    fun set(value: Boolean) {
        DesktopPersistence().saveBoolean(KEY, value)
        _enabled.value = value
    }
}

/** What the caption buttons act on. */
internal class DesktopWindowActions(
    val minimize: () -> Unit,
    val toggleMaximize: () -> Unit,
    val close: () -> Unit,
)

internal val LocalDesktopWindowActions = staticCompositionLocalOf<DesktopWindowActions?> { null }

/** The window, for the drag area. */
internal val LocalDesktopWindowScope = staticCompositionLocalOf<WindowScope?> { null }

/** A region that moves the window, and maximizes it on a double click. */
@Composable
internal fun DesktopTitleBarDragArea(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val scope = LocalDesktopWindowScope.current
    val actions = LocalDesktopWindowActions.current
    if (!DesktopPlatform.drawsOwnWindowFrame || scope == null) {
        Box(modifier) { content() }
        return
    }
    // Behind the content rather than around it. Wrapped around it, the drag area saw every press
    // in the toolbar too — dragging the volume slider moved the window with it. As a sibling
    // underneath, anything with its own pointer input (buttons, sliders) is hit first and the
    // press never reaches here; only empty toolbar falls through to move the window.
    Box(modifier) {
        Box(Modifier.matchParentSize().captionPress(scope, actions))
        content()
    }
}

/** The caption buttons. In the title bar when there is one, otherwise on the toolbar. */
@Composable
internal fun DesktopWindowButtons(modifier: Modifier = Modifier) {
    if (!DesktopPlatform.drawsOwnWindowFrame) return
    val actions = LocalDesktopWindowActions.current ?: return
    if (!DesktopPlatform.isMac) {
        WindowsCaptionButtons(actions, modifier)
        return
    }
    val maximized by DesktopWindowMode.maximized.collectAsState()
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val isFocused = windowInfo.isWindowFocused
    val inactiveFill = if (!isFocused && DesktopPlatform.isMac) Color.White.copy(alpha = 0.22f) else null

    Row(
        modifier = modifier.height(CAPTION_HEIGHT).padding(start = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MacCaptionButton("Close", MAC_CLOSE, onClick = actions.close, inactiveFill = inactiveFill) { glyph ->
            val reach = GLYPH_HALF.toPx()
            val line = HAIRLINE.toPx()
            drawLine(
                color = glyph,
                start = Offset(center.x - reach, center.y - reach),
                end = Offset(center.x + reach, center.y + reach),
                strokeWidth = line,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = glyph,
                start = Offset(center.x + reach, center.y - reach),
                end = Offset(center.x - reach, center.y + reach),
                strokeWidth = line,
                cap = StrokeCap.Round,
            )
        }
        MacCaptionButton("Minimize", MAC_MINIMIZE, onClick = actions.minimize, inactiveFill = inactiveFill) { glyph ->
            drawLine(
                color = glyph,
                start = Offset(center.x - GLYPH_HALF.toPx(), center.y),
                end = Offset(center.x + GLYPH_HALF.toPx(), center.y),
                strokeWidth = HAIRLINE.toPx(),
                cap = StrokeCap.Round,
            )
        }
        MacCaptionButton(
            if (maximized) "Restore" else "Maximize",
            MAC_MAXIMIZE,
            onClick = actions.toggleMaximize,
            inactiveFill = inactiveFill,
        ) { glyph ->
            val reach = GLYPH_HALF.toPx()
            val line = HAIRLINE.toPx()
            // The opposing corner marks used by macOS' green zoom control. They remain legible at
            // 100% Windows scaling, unlike a tiny outlined square.
            drawLine(glyph, Offset(center.x - reach, center.y + reach), Offset(center.x + reach, center.y - reach), line, StrokeCap.Round)
            drawLine(glyph, Offset(center.x + reach, center.y - reach), Offset(center.x + 1.dp.toPx(), center.y - reach), line, StrokeCap.Round)
            drawLine(glyph, Offset(center.x + reach, center.y - reach), Offset(center.x + reach, center.y - 1.dp.toPx()), line, StrokeCap.Round)
            drawLine(glyph, Offset(center.x - reach, center.y + reach), Offset(center.x - 1.dp.toPx(), center.y + reach), line, StrokeCap.Round)
            drawLine(glyph, Offset(center.x - reach, center.y + reach), Offset(center.x - reach, center.y + 1.dp.toPx()), line, StrokeCap.Round)
        }
    }
}

@Composable
private fun MacCaptionButton(
    label: String,
    fill: Color,
    onClick: () -> Unit,
    inactiveFill: Color? = null,
    glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val buttonColor = if (inactiveFill != null && !hovered) inactiveFill else fill
    Box(
        Modifier
            .size(CAPTION_TOUCH_SIZE)
            .hoverable(interaction)
            // The arrow, not the hand: this is window furniture, and the pointer says so before the
            // click does.
            .pointerHoverIcon(PointerIcon.Default)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .drawBehind {
                drawCircle(buttonColor, radius = MAC_DOT_RADIUS.toPx())
                if (hovered) glyph(MAC_GLYPH)
            },
        contentAlignment = Alignment.Center,
    ) {}
}

/**
 * Windows 11's caption buttons: three flat 46-wide cells at the right-hand end, a thin glyph in
 * each, a faint wash on hover and the system red behind close.
 */
@Composable
private fun WindowsCaptionButtons(actions: DesktopWindowActions, modifier: Modifier = Modifier) {
    val maximized by DesktopWindowMode.maximized.collectAsState()
    val focused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    val glyph = if (focused) Color.White else Color.White.copy(alpha = 0.45f)
    Row(
        modifier = modifier.height(WIN_CAPTION_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowsCaptionButton("Minimize", glyph, onClick = actions.minimize) { color ->
            val half = WIN_GLYPH_HALF.toPx()
            drawLine(color, Offset(center.x - half, center.y), Offset(center.x + half, center.y), WIN_HAIRLINE.toPx())
        }
        WindowsCaptionButton(if (maximized) "Restore" else "Maximize", glyph, onClick = actions.toggleMaximize) { color ->
            val half = WIN_GLYPH_HALF.toPx()
            val line = WIN_HAIRLINE.toPx()
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = line)
            if (maximized) {
                // Restore: the front square, and the corner of the one behind it.
                val inset = 2.dp.toPx()
                drawRoundRect(
                    color,
                    topLeft = Offset(center.x - half, center.y - half + inset),
                    size = androidx.compose.ui.geometry.Size(half * 2 - inset, half * 2 - inset),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
                    style = stroke,
                )
                drawLine(color, Offset(center.x - half + inset, center.y - half), Offset(center.x + half, center.y - half), line)
                drawLine(color, Offset(center.x + half, center.y - half), Offset(center.x + half, center.y + half - inset), line)
            } else {
                drawRoundRect(
                    color,
                    topLeft = Offset(center.x - half, center.y - half),
                    size = androidx.compose.ui.geometry.Size(half * 2, half * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                    style = stroke,
                )
            }
        }
        WindowsCaptionButton("Close", glyph, onClick = actions.close, isClose = true) { color ->
            val half = WIN_GLYPH_HALF.toPx()
            val line = WIN_HAIRLINE.toPx()
            drawLine(color, Offset(center.x - half, center.y - half), Offset(center.x + half, center.y + half), line, StrokeCap.Round)
            drawLine(color, Offset(center.x + half, center.y - half), Offset(center.x - half, center.y + half), line, StrokeCap.Round)
        }
    }
}

@Composable
private fun WindowsCaptionButton(
    label: String,
    glyphColor: Color,
    onClick: () -> Unit,
    isClose: Boolean = false,
    glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val wash by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            isClose && pressed -> WIN_CLOSE_PRESSED
            isClose && hovered -> WIN_CLOSE_HOVER
            pressed -> Color.White.copy(alpha = 0.04f)
            hovered -> Color.White.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = androidx.compose.animation.core.tween(if (hovered) 90 else 160),
        label = "captionWash",
    )
    val glyphTint = if (isClose && (hovered || pressed)) Color.White else glyphColor
    Box(
        Modifier
            .width(WIN_CAPTION_WIDTH)
            .fillMaxHeight()
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Default)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = label }
            .background(wash)
            .drawBehind { glyph(glyphTint) },
    )
}

private val WIN_CAPTION_WIDTH = 46.dp
internal val WIN_CAPTION_HEIGHT = 32.dp
private val WIN_GLYPH_HALF = 5.dp
private val WIN_HAIRLINE = 1.dp
private val WIN_CLOSE_HOVER = Color(0xFFC42B1C)
private val WIN_CLOSE_PRESSED = Color(0xFFB2271A)

/**
 * The platform's own caption metrics, which is what makes these read as the window's controls
 * rather than the application's.
 */
private val CAPTION_TOUCH_SIZE = 22.dp
private val CAPTION_HEIGHT = 24.dp
private val MAC_DOT_RADIUS = 6.dp

/** Half the width of a glyph, and the weight every one of them is drawn at. */
private val GLYPH_HALF = 2.5.dp
private val HAIRLINE = 0.75.dp

private val MAC_CLOSE = Color(0xFFFF5F57)
private val MAC_MINIMIZE = Color(0xFFFFBD2E)
private val MAC_MAXIMIZE = Color(0xFF28C840)
private val MAC_GLYPH = Color(0xB3000000)

/**
 * A press on empty caption: moves the window, and a second one in quick succession maximizes it,
 * the way the system caption always has.
 *
 * On Windows the move is handed to the system ([DesktopWindowsFrame.startDrag]), which runs its own
 * move loop — DWM slides the window, Aero Snap works, and no edge is left exposed to paint white.
 * On macOS it is handed to AppKit via [DesktopMacFrame.startDrag] for smooth ProMotion dragging.
 * The double click is counted here rather than left to Windows: the area is client area as far as
 * Windows knows, so it never sends the caption's own double click. Without the native frame the
 * window is moved from the pointer, as WindowDraggableArea did.
 */
private fun Modifier.captionPress(scope: WindowScope, actions: DesktopWindowActions?): Modifier =
    pointerInput(scope, actions) {
        var lastPressAt = 0L
        var lastPressPosition = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown()
            val double = down.uptimeMillis - lastPressAt <= viewConfiguration.doubleTapTimeoutMillis &&
                (down.position - lastPressPosition).getDistance() <= viewConfiguration.touchSlop
            if (double) {
                lastPressAt = 0L
                down.consume()
                actions?.toggleMaximize()
                return@awaitEachGesture
            }
            lastPressAt = down.uptimeMillis
            lastPressPosition = down.position
            if (DesktopWindowsFrame.startDrag()) return@awaitEachGesture
            if (DesktopPlatform.isMac && DesktopMacFrame.startDrag()) return@awaitEachGesture
            val window = scope.window
            val windowAtStart = window.location
            val pointerAtStart = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
            drag(down.id) {
                val pointer = MouseInfo.getPointerInfo()?.location ?: return@drag
                window.setLocation(
                    windowAtStart.x + pointer.x - pointerAtStart.x,
                    windowAtStart.y + pointer.y - pointerAtStart.y,
                )
            }
        }
    }
