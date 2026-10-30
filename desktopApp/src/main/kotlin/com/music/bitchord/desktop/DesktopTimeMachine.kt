package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.Song
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * What the listener was playing a while ago, offered back to them on Home.
 *
 * Two memories to draw on, because neither is enough alone. This computer's monthly listening is
 * exact but only goes back as far as the app has been installed; the account's YouTube Music
 * history reaches further, in the periods YouTube itself groups it by. Those periods' titles are
 * shown as YouTube wrote them, in the account's language, rather than parsed into dates.
 */
internal object DesktopTimeMachine {

    class Memory(val title: String, val songs: List<Song>)

    /** Picks the memory furthest back that has enough in it to be worth playing. */
    internal fun pick(
        local: (YearMonth) -> List<Song>,
        shelves: List<Pair<String, List<Song>>>,
        today: YearMonth = YearMonth.now(),
        monthTitle: (YearMonth) -> String = ::titleOf,
    ): Memory? {
        fun month(ago: Long): Memory? {
            val at = today.minusMonths(ago)
            val songs = local(at).takeIf { it.size >= MIN_SONGS } ?: return null
            return Memory(monthTitle(at), songs.take(MAX_SONGS))
        }
        return month(12) ?: fromHistory(shelves) ?: month(6) ?: month(3) ?: month(1)
    }

    /** The oldest period of the account's history, without what has been played again lately. */
    private fun fromHistory(shelves: List<Pair<String, List<Song>>>): Memory? {
        // Today and yesterday, or this week: not a memory yet.
        if (shelves.size <= RECENT_SHELVES) return null
        val recent = shelves.take(RECENT_SHELVES).flatMap { it.second }.mapTo(HashSet()) { it.videoId }
        val (title, songs) = shelves.drop(RECENT_SHELVES).lastOrNull { it.second.size >= MIN_SONGS } ?: return null
        val forgotten = songs.filter { it.videoId !in recent }
        return Memory(title, (forgotten.takeIf { it.size >= MIN_SONGS } ?: songs).take(MAX_SONGS))
    }

    /** Reads both memories. Slow, and for a thread that may wait. */
    suspend fun load(): Memory? {
        val shelves = DesktopSearchClient.historyShelves().getOrElse {
            DesktopTrackLog.log("time machine: history not read: ${it.message}")
            emptyList()
        }
        DesktopTrackLog.log("time machine: history periods " + shelves.joinToString { "${it.first} (${it.second.size})" })
        val memory = pick(local = DesktopListeningStats::songsOf, shelves = shelves)
        DesktopTrackLog.log("time machine: " + (memory?.let { "${it.title}, ${it.songs.size} tracks" } ?: "nothing old enough yet"))
        return memory
    }

    private fun titleOf(month: YearMonth): String =
        DateTimeFormatter.ofPattern("LLLL yyyy", replayLocale()).format(month)
            .replaceFirstChar { it.uppercase(replayLocale()) }

    private const val MIN_SONGS = 5
    private const val MAX_SONGS = 30
    private const val RECENT_SHELVES = 2
}

/** The card on Home: when it is from, the covers, and a click that plays them. */
@Composable
internal fun DesktopTimeMachineCard(
    memory: DesktopTimeMachine.Memory,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, DesktopCardEdge, RoundedCornerShape(26.dp))
            .clickable(onClick = onPlay)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                DesktopStrings["d_time_machine", "Time machine"],
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                DesktopStrings["d_time_machine_subtitle", "What you were listening to: %s"].format(memory.title),
                color = DesktopSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            memory.songs.take(COVERS).forEach { song ->
                DesktopArtwork(song.thumbnailUrl, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), px = COVER_PX)
            }
        }
    }
}

private const val COVERS = 4
private const val COVER_PX = 128
