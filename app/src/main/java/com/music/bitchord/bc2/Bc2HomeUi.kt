package com.music.bitchord.bc2

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.player.rememberDiscPalette
import kotlin.math.PI
import kotlin.math.sin

/**
 * My Jam's card at the head of Home, lit in the colours of what is playing: before it plays, one
 * press starts it; while it plays, it shows what the jam is on, skips, and pauses. The jam's own
 * settings are on the card, and Home's beside them.
 */
@Composable
internal fun JamCard(
    starting: Boolean,
    playing: Boolean,
    paused: Boolean,
    current: Song?,
    coverUrl: String?,
    onPlay: () -> Unit,
    onPlayPause: () -> Unit,
    onSkip: () -> Unit,
    onTune: () -> Unit,
    onCustomizeHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberDiscPalette(coverUrl)
    val light by animateColorAsState(palette.vivid, tween(700), label = "jamLight")
    val deep by animateColorAsState(palette.deep, tween(700), label = "jamDeep")
    val moving = playing && !paused
    // Read only while drawing, so the wave moves by redrawing alone and nothing recomposes for it.
    val phase = rememberInfiniteTransition(label = "jam").animateFloat(
        0f, 1f, infiniteRepeatable(tween(if (moving) 2_600 else 9_000, easing = LinearEasing), RepeatMode.Restart), label = "jamPhase",
    )
    val tuning by MyJam.tuning.collectAsStateWithLifecycle()
    val onlyNew by MyJam.onlyNew.collectAsStateWithLifecycle()
    Box(
        modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.horizontalGradient(listOf(deep.copy(alpha = 0.95f), light.copy(alpha = 0.55f), deep.copy(alpha = 0.85f))))
            .drawBehind {
                // The wave: lines drifting across, in the light of the cover.
                for (k in 0 until 3) {
                    val path = Path()
                    val amp = size.height * ((if (moving) 0.13f else 0.07f) + k * 0.035f)
                    val mid = size.height * (0.52f + k * 0.09f)
                    val shift = (phase.value + k * 0.23f) * 2f * PI.toFloat()
                    var x = 0f
                    path.moveTo(0f, mid)
                    val step = 3.dp.toPx()
                    while (x <= size.width) {
                        path.lineTo(x, mid + amp * sin(x / size.width * (2.2f + k * 0.5f) * PI.toFloat() + shift))
                        x += step
                    }
                    drawPath(path, light.copy(alpha = 0.45f - k * 0.12f), style = Stroke(width = (3.5f - k).dp.toPx()))
                }
            }
            .clickable(enabled = !starting, onClick = if (playing) onPlayPause else onPlay),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.padding(start = 18.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(enabled = !starting, onClick = if (playing) onPlayPause else onPlay),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    starting -> CircularProgressIndicator(Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.5.dp)
                    moving -> LevelBars({ phase.value }, Color.Black)
                    else -> Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(MyJam.radioName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, maxLines = 1)
                    if (playing) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            (if (paused) bc2("пауза", "paused") else bc2("играет", "playing")).uppercase(),
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.9f)).padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    if (playing && current != null) {
                        "${current.artist} — ${current.title}"
                    } else {
                        listOfNotNull(
                            tuning.label().takeIf { tuning != JamTuning.ALL },
                            bc2("Только новое", "Only new").takeIf { onlyNew },
                        ).ifEmpty { listOf(bc2("Бесконечная музыка для тебя", "Endless music picked for you")) }.joinToString(" · ")
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (playing) CardIcon(Icons.Rounded.SkipNext, bc2("Дальше", "Next"), onSkip)
            CardIcon(Icons.Rounded.Tune, bc2("Настройки джема", "Jam settings"), onTune)
            CardIcon(Icons.Rounded.ViewAgenda, bc2("Настроить главную", "Customize Home"), onCustomizeHome)
        }
    }
}

@Composable
private fun CardIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 6.dp).size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** A small moving level, four bars, drawn so they follow [phase] by redrawing alone. */
@Composable
private fun LevelBars(phase: () -> Float, color: Color) {
    Canvas(Modifier.size(width = 4.dp * 4 + 3.dp * 3, height = 22.dp)) {
        val bar = 4.dp.toPx()
        val gap = 3.dp.toPx()
        val corner = CornerRadius(2.dp.toPx())
        val p = phase()
        for (i in 0 until 4) {
            val h = 0.35f + 0.65f * ((sin((p * 2f * PI.toFloat() * 3f) + i * 1.7f) + 1f) / 2f)
            val height = size.height * h
            drawRoundRect(color, topLeft = Offset(i * (bar + gap), size.height - height), size = Size(bar, height), cornerRadius = corner)
        }
    }
}

private fun JamTuning.label(): String = when (this) {
    JamTuning.ALL -> bc2("Всё", "Everything")
    JamTuning.POPULAR -> bc2("Популярное", "Popular")
    JamTuning.RECOMMENDED -> bc2("Рекомендованное", "Recommended")
    JamTuning.DEEP_CUTS -> bc2("Редкие треки", "Deep cuts")
    JamTuning.PARTY -> bc2("Вечеринка", "Party")
}

private fun JamSeeds.label(): String = when (this) {
    JamSeeds.LIKED_AND_HISTORY -> bc2("Лайки и история", "Likes and history")
    JamSeeds.LIKED -> bc2("Лайки", "Likes")
    JamSeeds.HISTORY -> bc2("Недавнее", "Recently played")
    JamSeeds.PLAYLIST -> bc2("Плейлист", "A playlist")
}

/** My Jam's settings: what it plays and what it grows from. Changes apply from the next tracks. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun JamSettingsSheet(
    /** The playlists the jam can grow from: browse id and title. */
    playlists: List<Pair<String, String>>,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tuning by MyJam.tuning.collectAsStateWithLifecycle()
    val seeds by MyJam.seeds.collectAsStateWithLifecycle()
    val playlist by MyJam.playlist.collectAsStateWithLifecycle()
    val onlyNew by MyJam.onlyNew.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            SheetTitle(bc2("Настройки джема", "Jam settings"))
            Section(bc2("Характер", "Character"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                JamTuning.entries.forEach { option -> Choice(option.label(), option == tuning) { MyJam.setTuning(option) } }
            }
            Section(bc2("На основе", "Based on"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                JamSeeds.entries.forEach { option -> Choice(option.label(), option == seeds) { MyJam.setSeeds(option) } }
            }
            if (seeds == JamSeeds.PLAYLIST) {
                Spacer(Modifier.height(10.dp))
                if (playlists.isEmpty()) {
                    Text(bc2("Плейлистов пока нет.", "No playlists yet."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                ) {
                    playlists.forEach { (id, title) ->
                        val picked = playlist?.first == id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { MyJam.setPlaylist(id, title) }
                                .background(if (picked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else Color.Transparent)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (picked) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            ToggleLine(bc2("Только новое", "Only new"), bc2("Без того, что уже лайкнуто или недавно играло", "Leave out what you have liked or played lately"), onlyNew, MyJam::setOnlyNew)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction(bc2("Закрыть", "Close"), filled = false, onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                TextAction(bc2("Включить с этими", "Play with these"), filled = true) {
                    onDismiss()
                    onRestart()
                }
            }
        }
    }
}

/**
 * Which of the feed's shelves Home shows and in what order — each row held and dragged by its
 * handle, as the player's queue is — and whether My Jam leads it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeCustomizeSheet(shelfTitles: List<String>, onDismiss: () -> Unit) {
    val hidden by HomeShelves.hidden.collectAsStateWithLifecycle()
    val order by HomeShelves.order.collectAsStateWithLifecycle()
    val jamShown by MyJam.shown.collectAsStateWithLifecycle()
    // What the feed has now, then whatever is hidden but did not come this time — in Home's order.
    val all = (shelfTitles + hidden).distinctBy { it.trim().lowercase() }
    val shown = remember(all, order) { HomeShelves.ordered(all) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            SheetTitle(bc2("Настроить главную", "Customize Home"))
            Text(
                bc2("Перетаскивай за ручку, чтобы поменять порядок. Выключи то, что не хочешь видеть.", "Drag by the handle to reorder. Switch off what you don't want to see."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            ToggleLine(MyJam.radioName, bc2("Карточка вверху главной", "The card at the top of Home"), jamShown, MyJam::setShown)
            Spacer(Modifier.height(4.dp))
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                ReorderableShelves(shown, hidden, onReorder = HomeShelves::setOrder)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (order.isNotEmpty()) {
                    TextAction(bc2("Исходный порядок", "Original order"), filled = false) { HomeShelves.resetOrder() }
                    Spacer(Modifier.width(8.dp))
                }
                if (hidden.isNotEmpty()) {
                    TextAction(bc2("Показать все", "Show all"), filled = false) { HomeShelves.showAll() }
                    Spacer(Modifier.width(8.dp))
                }
                TextAction(bc2("Готово", "Done"), filled = true, onClick = onDismiss)
            }
        }
    }
}

/** The shelves as rows that follow the finger while dragged by their handle. */
@Composable
private fun ReorderableShelves(titles: List<String>, hidden: Set<String>, onReorder: (List<String>) -> Unit) {
    var live by remember(titles) { mutableStateOf(titles) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var totalDrag by remember { mutableFloatStateOf(0f) }
    var startIndex by remember { mutableIntStateOf(0) }
    var startOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    val heights = remember { mutableStateMapOf<String, Float>() }
    val haptics = LocalHapticFeedback.current
    val hiddenKeys = remember(hidden) { hidden.mapTo(HashSet()) { it.trim().lowercase() } }

    fun topOf(list: List<String>, index: Int): Float {
        var top = 0f
        for (slot in 0 until index.coerceAtMost(list.size)) top += heights[list[slot]] ?: 0f
        return top
    }

    Column {
        live.forEach { title ->
            key(title) {
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
                        .background(if (dragging) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.DragHandle,
                        bc2("Перетащить", "Drag to reorder"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(26.dp)
                            .pointerInput(title) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                    totalDrag += amount
                                    val layout = startOrder.map { heights[it] ?: 0f }
                                    if (layout.all { it > 0f }) {
                                        val target = targetSlot(layout, startIndex, totalDrag)
                                        val at = live.indexOf(title)
                                        if (target != at) {
                                            live = live.toMutableList().apply { removeAt(at); add(target, title) }
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                            },
                    )
                    Box(Modifier.weight(1f)) {
                        ToggleLine(title, null, title.trim().lowercase() !in hiddenKeys) { show -> HomeShelves.setHidden(title, !show) }
                    }
                }
            }
        }
    }
}

/** Where a row that started at [from] and has moved [drag] pixels belongs among rows of [heights]. */
private fun targetSlot(heights: List<Float>, from: Int, drag: Float): Int {
    var slot = from
    var moved = drag
    while (moved > 0f && slot < heights.lastIndex) {
        val next = heights[slot + 1]
        if (moved < next / 2f) break
        moved -= next
        slot++
    }
    while (moved < 0f && slot > 0) {
        val prev = heights[slot - 1]
        if (-moved < prev / 2f) break
        moved += prev
        slot--
    }
    return slot
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Section(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val on = MaterialTheme.colorScheme.onSurface
    Text(
        label,
        color = if (selected) MaterialTheme.colorScheme.surface else on.copy(alpha = 0.9f),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) on else on.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun ToggleLine(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TextAction(label: String, filled: Boolean, onClick: () -> Unit) {
    val on = MaterialTheme.colorScheme.onSurface
    Text(
        label,
        color = if (filled) MaterialTheme.colorScheme.surface else on,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) on else on.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
