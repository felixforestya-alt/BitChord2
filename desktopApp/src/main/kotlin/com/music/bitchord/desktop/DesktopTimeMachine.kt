package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * What the listener was playing a while ago, offered back to them among the Library's Replay cards.
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

    /** Today, yesterday, this week and last week: not a memory yet. */
    private const val RECENT_SHELVES = 4
}
