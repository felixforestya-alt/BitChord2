package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopTimeMachineTest {

    private fun songs(prefix: String, count: Int) = List(count) {
        Song(videoId = "$prefix$it", title = "$prefix$it", artist = "a", thumbnailUrl = "")
    }

    private val today = YearMonth.of(2026, 10)

    /** The four periods the account's history opens with. */
    private fun recent() = listOf("Today", "Yesterday", "This week", "Last week").map { it to songs(it, 9) }

    private fun pick(
        local: Map<YearMonth, List<Song>> = emptyMap(),
        shelves: List<Pair<String, List<Song>>> = emptyList(),
    ) = DesktopTimeMachine.pick(
        local = { local[it].orEmpty() },
        shelves = shelves,
        today = today,
        monthTitle = { it.toString() },
    )

    @Test
    fun `nothing old enough means no card`() {
        assertNull(pick())
        assertNull(pick(local = mapOf(today to songs("n", 50))))
        assertNull(pick(shelves = recent()))
    }

    @Test
    fun `a year ago on this computer comes first`() {
        val memory = pick(
            local = mapOf(today.minusMonths(12) to songs("old", 6)),
            shelves = recent() + ("March" to songs("m", 9)),
        )
        assertEquals("2025-10", memory?.title)
    }

    @Test
    fun `otherwise the oldest period of the account's history, as YouTube titled it`() {
        val memory = pick(
            local = mapOf(today.minusMonths(1) to songs("recent", 9)),
            shelves = recent() + listOf(
                "Март 2025" to songs("m", 9),
                "Too few" to songs("f", 2),
            ),
        )
        assertEquals("Март 2025", memory?.title)
    }

    @Test
    fun `what was played again lately is left out of a memory`() {
        val again = songs("m", 2)
        val memory = pick(
            shelves = listOf("Today" to again) + recent().drop(1) + ("Old" to again + songs("z", 6)),
        )
        assertEquals(6, memory?.songs?.size)
    }

    @Test
    fun `a nearer month stands in when there is nothing further back`() {
        assertEquals("2026-09", pick(local = mapOf(today.minusMonths(1) to songs("r", 5)))?.title)
    }
}
