package com.music.bitchord.desktop

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.HomeShelf
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which of the feed's shelves the listener has taken off Home — "Trending", "Covers", whatever
 * YouTube puts there that they do not care for. Kept by the shelf's title, which is all a shelf
 * has that stays the same from one load of the feed to the next.
 */
internal object DesktopHomeShelves {

    private val persistence by lazy { DesktopPersistence() }

    private val _hidden = MutableStateFlow(
        persistence.string(KEY_HIDDEN).split('\n').map(String::trim).filter(String::isNotEmpty).toSet(),
    )

    /** The hidden titles, as they were shown. */
    val hidden: StateFlow<Set<String>> = _hidden

    fun isHidden(title: String): Boolean = keyOf(title) in _hidden.value.mapTo(HashSet(), ::keyOf)

    fun setHidden(title: String, hide: Boolean) {
        val rest = _hidden.value.filterNot { keyOf(it) == keyOf(title) }.toSet()
        save(if (hide) rest + title.trim() else rest)
    }

    fun showAll() = save(emptySet())

    private val _order = MutableStateFlow(
        persistence.string(KEY_ORDER).split('\n').map(String::trim).filter(String::isNotEmpty),
    )

    /** The listener's order of shelves, by title; shelves not in it follow in the feed's own order. */
    val order: StateFlow<List<String>> = _order

    /** [titles] in the listener's order. */
    fun ordered(titles: List<String>): List<String> {
        val rank = _order.value.withIndex().associate { (i, t) -> keyOf(t) to i }
        return titles.withIndex().sortedBy { (i, t) -> rank[keyOf(t)] ?: (ORDERED_LAST + i) }.map { it.value }
    }

    /** Moves [title] one place up ([by] -1) or down (+1) among [titles], as they are now shown. */
    fun move(titles: List<String>, title: String, by: Int) {
        val now = ordered(titles).toMutableList()
        val at = now.indexOfFirst { keyOf(it) == keyOf(title) }
        val to = at + by
        if (at < 0 || to !in now.indices) return
        now.add(to, now.removeAt(at))
        _order.value = now
        persistence.saveString(KEY_ORDER, now.joinToString("\n"))
    }

    /** Home's shelves in exactly [titles]' order. */
    fun setOrder(titles: List<String>) {
        _order.value = titles
        persistence.saveString(KEY_ORDER, titles.joinToString("\n"))
    }

    fun resetOrder() {
        _order.value = emptyList()
        persistence.saveString(KEY_ORDER, "")
    }

    /** [shelves] in the listener's order, without the hidden ones. */
    fun visible(shelves: List<HomeShelf>): List<HomeShelf> {
        val keys = _hidden.value.mapTo(HashSet(), ::keyOf)
        val rank = _order.value.withIndex().associate { (i, t) -> keyOf(t) to i }
        return shelves.withIndex()
            .filterNot { keyOf(it.value.title) in keys }
            .sortedBy { (i, shelf) -> rank[keyOf(shelf.title)] ?: (ORDERED_LAST + i) }
            .map { it.value }
    }

    private fun save(value: Set<String>) {
        _hidden.value = value
        persistence.saveString(KEY_HIDDEN, value.joinToString("\n"))
    }

    private fun keyOf(title: String) = title.trim().lowercase()

    private const val KEY_HIDDEN = "home_hidden_shelves"
    private const val KEY_ORDER = "home_shelf_order"
    private const val ORDERED_LAST = 10_000
}

/**
 * My Jam's card at the head of Home, lit in the colours of what is playing so it belongs with the
 * rest of the page. Before it plays, one press starts it; while it plays it says so — what track,
 * a moving level — and the press pauses it, with a skip beside. Next to it, the jam's settings and
 * the way into choosing what Home shows.
 */
@Composable
internal fun DesktopWaveCard(
    starting: Boolean,
    playing: Boolean,
    paused: Boolean,
    current: com.music.bitchord.data.model.Song?,
    coverUrl: String?,
    onPlay: () -> Unit,
    onPlayPause: () -> Unit,
    onSkip: () -> Unit,
    onTune: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tuning by DesktopMyWave.tuning.collectAsState()
    val onlyNew by DesktopMyWave.onlyNew.collectAsState()
    val palette = rememberVividPalette(coverUrl)
    val light by androidx.compose.animation.animateColorAsState(palette.vivid, tween(700), label = "jamLight")
    val deep by androidx.compose.animation.animateColorAsState(palette.deep, tween(700), label = "jamDeep")
    val moving = playing && !paused
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        0f, 1f, infiniteRepeatable(tween(if (moving) 2_600 else 9_000, easing = LinearEasing), RepeatMode.Restart), label = "wavePhase",
    )
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(112.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.horizontalGradient(listOf(deep.copy(alpha = 0.95f), light.copy(alpha = 0.55f), deep.copy(alpha = 0.85f))))
                .drawBehind {
                    // The wave: lines drifting across, in the light of the cover.
                    for (k in 0 until 3) {
                        val path = Path()
                        val amp = size.height * ((if (moving) 0.13f else 0.07f) + k * 0.035f)
                        val mid = size.height * (0.52f + k * 0.09f)
                        val shift = (phase + k * 0.23f) * 2f * PI.toFloat()
                        var x = 0f
                        path.moveTo(0f, mid)
                        while (x <= size.width) {
                            path.lineTo(x, mid + amp * sin(x / size.width * (2.2f + k * 0.5f) * PI.toFloat() + shift))
                            x += 6f
                        }
                        drawPath(path, light.copy(alpha = 0.45f - k * 0.12f), style = Stroke(width = (3.5f - k).dp.toPx()))
                    }
                }
                .clickable(enabled = !starting, onClick = if (playing) onPlayPause else onPlay)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    when {
                        starting -> CircularProgressIndicator(Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.5.dp)
                        moving -> LevelBars(phase, Color.Black)
                        else -> Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            DesktopMyWave.radioName,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                        )
                        if (playing) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                (if (paused) DesktopStrings["jam_paused", "paused"] else DesktopStrings["jam_playing", "playing"]).uppercase(),
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Text(
                        if (playing && current != null) {
                            "${current.artist} — ${current.title}"
                        } else {
                            listOfNotNull(
                                tuning.label().takeIf { tuning != DesktopWaveTuning.ALL },
                                DesktopStrings["wave_only_new", "Only new"].takeIf { onlyNew },
                            ).ifEmpty { listOf(DesktopStrings["wave_subtitle", "Endless music picked for you"]) }.joinToString(" · ")
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (playing) {
                    CardIcon(Icons.Rounded.SkipNext, DesktopStrings["next", "Next"], onSkip)
                    Spacer(Modifier.width(10.dp))
                }
                // The jam's own settings, on the jam itself.
                CardIcon(Icons.Rounded.Tune, DesktopStrings["wave_settings", "Jam settings"], onTune)
            }
        }
    }
}

@Composable
private fun CardIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** A small moving level, four bars, in place of the play button while the jam plays. */
@Composable
private fun LevelBars(phase: Float, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(22.dp)) {
        for (i in 0 until 4) {
            val h = 0.35f + 0.65f * ((sin((phase * 2f * PI.toFloat() * 3f) + i * 1.7f) + 1f) / 2f)
            Box(Modifier.width(4.dp).height(22.dp * h).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@Composable
private fun DesktopWaveTuning.label(): String = when (this) {
    DesktopWaveTuning.ALL -> DesktopStrings["wave_tuning_all", "Everything"]
    DesktopWaveTuning.POPULAR -> DesktopStrings["wave_tuning_popular", "Popular"]
    DesktopWaveTuning.RECOMMENDED -> DesktopStrings["wave_tuning_recommended", "Recommended"]
    DesktopWaveTuning.DEEP_CUTS -> DesktopStrings["wave_tuning_deep", "Deep cuts"]
    DesktopWaveTuning.PARTY -> DesktopStrings["wave_tuning_party", "Party"]
}

@Composable
private fun DesktopWaveSeeds.label(): String = when (this) {
    DesktopWaveSeeds.LIKED_AND_HISTORY -> DesktopStrings["wave_seeds_both", "Likes and history"]
    DesktopWaveSeeds.LIKED -> DesktopStrings["wave_seeds_liked", "Likes"]
    DesktopWaveSeeds.HISTORY -> DesktopStrings["wave_seeds_history", "Recently played"]
    DesktopWaveSeeds.PLAYLIST -> DesktopStrings["wave_seeds_playlist", "A playlist"]
}

/** My Wave's settings: what it plays and what it grows from. Changes apply from the next tracks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopWaveSettingsDialog(
    /** The playlists the jam can grow from: id and title. */
    playlists: List<Pair<String, String>>,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tuning by DesktopMyWave.tuning.collectAsState()
    val seeds by DesktopMyWave.seeds.collectAsState()
    val playlist by DesktopMyWave.playlist.collectAsState()
    val onlyNew by DesktopMyWave.onlyNew.collectAsState()
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 460, popup = true) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
            DialogTitle(DesktopStrings["wave_settings", "Wave settings"])
            Section(DesktopStrings["wave_character", "Character"])
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopWaveTuning.entries.forEach { option ->
                    Choice(option.label(), option == tuning) { DesktopMyWave.setTuning(option) }
                }
            }
            Section(DesktopStrings["wave_from", "Based on"])
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopWaveSeeds.entries.forEach { option ->
                    Choice(option.label(), option == seeds) { DesktopMyWave.setSeeds(option) }
                }
            }
            if (seeds == DesktopWaveSeeds.PLAYLIST) {
                Spacer(Modifier.height(10.dp))
                if (playlists.isEmpty()) {
                    Text(DesktopStrings["wave_no_playlists", "No playlists yet."], color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        playlists.forEach { (id, title) ->
                            Choice(title, playlist?.first == id) { DesktopMyWave.setPlaylist(id, title) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            ToggleLine(
                DesktopStrings["wave_only_new", "Only new"],
                DesktopStrings["wave_only_new_hint", "Leave out what you have liked or played lately"],
                onlyNew,
                DesktopMyWave::setOnlyNew,
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction(DesktopStrings["close", "Close"], onDismiss)
                Spacer(Modifier.width(8.dp))
                TextAction(DesktopStrings["wave_play_tuned", "Play with these"], filled = true) {
                    onDismiss()
                    onRestart()
                }
            }
        }
    }
}

/**
 * Which of the feed's shelves Home shows and in what order — each row dragged by its handle, as
 * the player's queue is — and whether My Jam leads it.
 */
@Composable
internal fun DesktopHomeCustomizeDialog(shelfTitles: List<String>, onDismiss: () -> Unit) {
    val hidden by DesktopHomeShelves.hidden.collectAsState()
    val order by DesktopHomeShelves.order.collectAsState()
    val waveShown by DesktopMyWave.shown.collectAsState()
    // What the feed has now, then whatever is hidden but did not come this time — in the order
    // Home shows them.
    val all = (shelfTitles + hidden).distinctBy { it.trim().lowercase() }
    val shown = androidx.compose.runtime.remember(all, order) { DesktopHomeShelves.ordered(all) }
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 500, popup = true) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
            DialogTitle(DesktopStrings["home_customize", "Customize Home"])
            Text(
                DesktopStrings["home_customize_hint2", "Drag by the handle to reorder. Switch off what you don't want to see."],
                color = DesktopSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            ToggleLine(DesktopMyWave.radioName, DesktopStrings["wave_card_hint", "The card at the top of Home"], waveShown, DesktopMyWave::setShown)
            Spacer(Modifier.height(4.dp))
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                ReorderableShelves(shown, onReorder = DesktopHomeShelves::setOrder)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (order.isNotEmpty()) {
                    TextAction(DesktopStrings["home_order_reset", "Original order"]) { DesktopHomeShelves.resetOrder() }
                    Spacer(Modifier.width(8.dp))
                }
                if (hidden.isNotEmpty()) {
                    TextAction(DesktopStrings["show_all", "Show all"]) { DesktopHomeShelves.showAll() }
                    Spacer(Modifier.width(8.dp))
                }
                TextAction(DesktopStrings["done", "Done"], filled = true, onClick = onDismiss)
            }
        }
    }
}

/** The shelves as rows that follow the pointer while dragged by their handle. */
@Composable
private fun ReorderableShelves(titles: List<String>, onReorder: (List<String>) -> Unit) {
    var live by androidx.compose.runtime.remember(titles) { androidx.compose.runtime.mutableStateOf(titles) }
    var dragged by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var totalDrag by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    var startIndex by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    var startOrder by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<String>>(emptyList()) }
    val heights = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<String, Float>() }

    fun topOf(list: List<String>, index: Int): Float {
        var top = 0f
        for (slot in 0 until index.coerceAtMost(list.size)) top += heights[list[slot]] ?: 0f
        return top
    }

    Column {
        live.forEach { title ->
            androidx.compose.runtime.key(title) {
                val dragging = title == dragged
                Row(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { heights[title] = it.height.toFloat() }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) totalDrag - (topOf(live, live.indexOf(title)) - topOf(startOrder, startIndex)) else 0f
                            if (dragging) { scaleX = 1.02f; scaleY = 1.02f }
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (dragging) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.DragHandle,
                        DesktopStrings["drag_to_reorder", "Drag to reorder"],
                        tint = DesktopSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon(java.awt.Cursor(java.awt.Cursor.N_RESIZE_CURSOR)))
                            .pointerInput(title) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragged = title
                                        totalDrag = 0f
                                        startOrder = live
                                        startIndex = live.indexOf(title)
                                    },
                                    onDragEnd = {
                                        dragged = null
                                        onReorder(live)
                                    },
                                    onDragCancel = {
                                        dragged = null
                                        live = titles
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    totalDrag += amount.y
                                    val layout = startOrder.map { heights[it] ?: 0f }
                                    if (layout.all { it > 0f }) {
                                        val target = reorderTargetSlot(layout, startIndex, totalDrag)
                                        val at = live.indexOf(title)
                                        if (target != at) live = live.toMutableList().apply { removeAt(at); add(target, title) }
                                    }
                                }
                            },
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        ToggleLine(title, null, !DesktopHomeShelves.isHidden(title)) { show -> DesktopHomeShelves.setHidden(title, !show) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = if (enabled) 0.85f else 0.2f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Section(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, color = DesktopSecondary, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Color.White.copy(alpha = 0.9f),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ToggleLine(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = desktopSwitchColors())
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) = TextAction(label, filled = false, onClick = onClick)

@Composable
private fun TextAction(label: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (filled) Color.Black else Color.White,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}
