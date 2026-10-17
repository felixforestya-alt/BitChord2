package com.music.bitchord.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * The player's everyday switches, in the middle of the top bar: where the sound goes, which
 * player opens, what moves behind it, video or picture, where the bar sits — one click each — and
 * the rest of the look in a panel under the last button, so none of it needs a trip to Settings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DesktopQuickSettings(
    onOpenAudioOutput: () -> Unit,
    onOpenAllSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerStyle by DesktopStyleSettings.playerStyle.collectAsState()
    val visualizer by DesktopStyleSettings.visualizerStyle.collectAsState()
    val visualMode by DesktopStyleSettings.visualMode.collectAsState()
    val placement by DesktopStyleSettings.nowPlayingPlacement.collectAsState()
    var panelOpen by remember { mutableStateOf(false) }
    val visualizerLabels = DesktopVisualizerStyle.entries.associateWith { it.label() }

    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        QuickButton(Icons.Rounded.Headphones, DesktopStrings["audio_output", "Audio output"], lit = false, onClick = onOpenAudioOutput)
        QuickButton(
            if (playerStyle == DesktopPlayerStyle.DISC) Icons.Rounded.Album else Icons.Rounded.ViewAgenda,
            DesktopStrings["d_player_style", "Player"] + ": " + if (playerStyle == DesktopPlayerStyle.DISC) {
                DesktopStrings["d_player_style_disc", "Disc"]
            } else {
                DesktopStrings["d_player_style_classic", "Classic"]
            },
            lit = playerStyle == DesktopPlayerStyle.DISC,
        ) {
            DesktopStyleSettings.setPlayerStyle(
                if (playerStyle == DesktopPlayerStyle.DISC) DesktopPlayerStyle.CLASSIC else DesktopPlayerStyle.DISC,
            )
        }
        QuickButton(
            Icons.Rounded.GraphicEq,
            DesktopStrings["d_visualizer", "Visualiser"] + ": " + visualizerLabels.getValue(visualizer),
            lit = visualizer != DesktopVisualizerStyle.NONE,
        ) { DesktopStyleSettings.setVisualizerStyle(visualizer.next()) }
        QuickButton(
            if (visualMode == DesktopVisualMode.VIDEO) Icons.Rounded.Movie else Icons.Rounded.Image,
            DesktopStrings["d_play_music_videos", "Play music videos"] + ": " + if (visualMode == DesktopVisualMode.VIDEO) {
                DesktopStrings["on", "On"]
            } else {
                DesktopStrings["d_off", "Off"]
            },
            lit = visualMode == DesktopVisualMode.VIDEO,
        ) {
            DesktopStyleSettings.setVisualMode(
                if (visualMode == DesktopVisualMode.VIDEO) DesktopVisualMode.ARTWORK else DesktopVisualMode.VIDEO,
            )
        }
        QuickButton(
            if (placement == DesktopNowPlayingPlacement.TOP) Icons.Rounded.VerticalAlignTop else Icons.Rounded.VerticalAlignBottom,
            DesktopStrings["d_now_playing_placement", "Now playing in the main window"] + ": " + if (placement == DesktopNowPlayingPlacement.TOP) {
                DesktopStrings["d_placement_top", "Top"]
            } else {
                DesktopStrings["d_placement_bottom", "Bottom"]
            },
            lit = false,
        ) {
            DesktopStyleSettings.setNowPlayingPlacement(
                if (placement == DesktopNowPlayingPlacement.TOP) DesktopNowPlayingPlacement.BOTTOM else DesktopNowPlayingPlacement.TOP,
            )
        }
        Box {
            QuickButton(Icons.Rounded.Tune, DesktopStrings["d_quick_settings", "Player settings"], lit = panelOpen) { panelOpen = !panelOpen }
            DropdownMenu(
                expanded = panelOpen,
                onDismissRequest = { panelOpen = false },
                offset = DpOffset((-150).dp, 8.dp),
                containerColor = Color(0xFF1E1E22),
                shape = RoundedCornerShape(18.dp),
            ) {
                QuickPanel(
                    visualizerLabels = visualizerLabels,
                    onOpenAllSettings = {
                        panelOpen = false
                        onOpenAllSettings()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickPanel(visualizerLabels: Map<DesktopVisualizerStyle, String>, onOpenAllSettings: () -> Unit) {
    val playerStyle by DesktopStyleSettings.playerStyle.collectAsState()
    val visualizer by DesktopStyleSettings.visualizerStyle.collectAsState()
    val visualMode by DesktopStyleSettings.visualMode.collectAsState()
    val placement by DesktopStyleSettings.nowPlayingPlacement.collectAsState()
    val showDisc by DesktopStyleSettings.showDisc.collectAsState()
    val backdropText by DesktopStyleSettings.backdropText.collectAsState()
    val liquidGlass by DesktopStyleSettings.liquidGlass.collectAsState()
    val animatedHome by DesktopStyleSettings.animatedHome.collectAsState()
    val lyricsScale by DesktopStyleSettings.lyricsScale.collectAsState()

    Column(Modifier.width(340.dp).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(DesktopStrings["d_quick_settings", "Player settings"], color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        PanelLabel(DesktopStrings["d_player_style", "Player"])
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelChip(DesktopStrings["d_player_style_disc", "Disc"], playerStyle == DesktopPlayerStyle.DISC) {
                DesktopStyleSettings.setPlayerStyle(DesktopPlayerStyle.DISC)
            }
            PanelChip(DesktopStrings["d_player_style_classic", "Classic"], playerStyle == DesktopPlayerStyle.CLASSIC) {
                DesktopStyleSettings.setPlayerStyle(DesktopPlayerStyle.CLASSIC)
            }
        }
        Spacer(Modifier.height(12.dp))

        PanelLabel(DesktopStrings["d_visualizer", "Visualiser"])
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DesktopVisualizerStyle.entries.forEach { style ->
                PanelChip(visualizerLabels.getValue(style), visualizer == style) { DesktopStyleSettings.setVisualizerStyle(style) }
            }
        }
        Spacer(Modifier.height(12.dp))

        PanelLabel(DesktopStrings["d_now_playing_placement", "Now playing in the main window"])
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelChip(DesktopStrings["d_placement_top", "Top"], placement == DesktopNowPlayingPlacement.TOP) {
                DesktopStyleSettings.setNowPlayingPlacement(DesktopNowPlayingPlacement.TOP)
            }
            PanelChip(DesktopStrings["d_placement_bottom", "Bottom"], placement == DesktopNowPlayingPlacement.BOTTOM) {
                DesktopStyleSettings.setNowPlayingPlacement(DesktopNowPlayingPlacement.BOTTOM)
            }
        }
        Spacer(Modifier.height(8.dp))

        PanelSwitch(DesktopStrings["d_play_music_videos", "Play music videos"], visualMode == DesktopVisualMode.VIDEO) {
            DesktopStyleSettings.setVisualMode(if (it) DesktopVisualMode.VIDEO else DesktopVisualMode.ARTWORK)
        }
        if (playerStyle == DesktopPlayerStyle.DISC) {
            PanelSwitch(DesktopStrings["d_show_disc", "Spinning disc"], showDisc, DesktopStyleSettings::setShowDisc)
            PanelSwitch(DesktopStrings["d_backdrop_text", "Giant backdrop lettering"], backdropText, DesktopStyleSettings::setBackdropText)
        }
        PanelSwitch(DesktopStrings["d_liquid_glass", "Liquid glass"], liquidGlass, DesktopStyleSettings::setLiquidGlass)
        val barTinted by DesktopStyleSettings.barTinted.collectAsState()
        PanelSwitch(DesktopStrings["d_bar_tinted", "Player bar in the cover's colour"], barTinted, DesktopStyleSettings::setBarTinted)
        PanelSwitch(DesktopStrings["d_animated_home", "Living main window"], animatedHome, DesktopStyleSettings::setAnimatedHome)

        // The size of the words in the player's lyrics.
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(DesktopStrings["d_lyrics_scale", "Lyrics size"], color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            PanelChip("A−", false) {
                DesktopStyleSettings.setLyricsScale((lyricsScale - LYRICS_SCALE_STEP).coerceAtLeast(LYRICS_SCALE_MIN))
            }
            Text(
                "${(lyricsScale * 100).roundToInt()}%",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                modifier = Modifier.width(52.dp).padding(horizontal = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            PanelChip("A+", false) {
                DesktopStyleSettings.setLyricsScale((lyricsScale + LYRICS_SCALE_STEP).coerceAtMost(LYRICS_SCALE_MAX))
            }
        }

        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onOpenAllSettings)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(DesktopStrings["d_all_settings", "All settings"], color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(
        text,
        color = DesktopSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun PanelChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(
        if (selected) Color.White else Color.White.copy(alpha = 0.08f),
        tween(180),
        label = "quickChip",
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(fill)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = if (selected) Color.Black else Color.White, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun PanelSwitch(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            // The hover wash reaches a little past the words on either side, as a capsule round
            // them, while the words stay in line with the rest of the panel.
            .layout { measurable, constraints ->
                val extra = 10.dp.roundToPx()
                val wide = constraints.copy(
                    minWidth = constraints.minWidth + extra * 2,
                    maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra * 2 else constraints.maxWidth,
                )
                val placeable = measurable.measure(wide)
                layout(placeable.width - extra * 2, placeable.height) { placeable.place(-extra, 0) }
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(start = 10.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.graphicsLayer { scaleX = 0.8f; scaleY = 0.8f },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** One of the bar's switches: lit while its option is on, its name on hover. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickButton(icon: ImageVector, label: String, lit: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.1f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "quickScale")
    val wash by animateFloatAsState(if (hovered) 0.12f else if (lit) 0.08f else 0f, tween(140), label = "quickWash")
    TooltipArea(
        tooltip = {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xF0222226))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) { Text(label, color = Color.White, fontSize = 12.sp) }
        },
        delayMillis = 450,
        tooltipPlacement = TooltipPlacement.ComponentRect(Alignment.BottomCenter, Alignment.BottomCenter, DpOffset(0.dp, 6.dp)),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = wash))
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (lit) Color.White else DesktopSecondary, modifier = Modifier.size(19.dp))
        }
    }
}
