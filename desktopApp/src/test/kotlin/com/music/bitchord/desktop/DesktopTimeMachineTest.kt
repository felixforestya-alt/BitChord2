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
        assertNull(pick(shelves = listOf("Today" to songs("t", 9), "Yesterday" to songs("y", 9))))
    }

    @Test
    fun `a year ago on this computer comes first`() {
        val memory = pick(
            local = mapOf(today.minusMonths(12) to songs("old", 6)),
            shelves = listOf("Today" to songs("t", 9), "Yesterday" to songs("y", 9), "March" to songs("m", 9)),
        )
        assertEquals("2025-10", memory?.title)
    }

    @Test
    fun `otherwise the oldest period of the account's history, as YouTube titled it`() {
        val memory = pick(
            local = mapOf(today.minusMonths(1) to songs("recent", 9)),
            shelves = listOf(
                "Today" to songs("t", 9),
                "Yesterday" to songs("y", 9),
                "Last week" to songs("w", 9),
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
            shelves = listOf(
                "Today" to again,
                "Yesterday" to songs("y", 1),
                "Old" to again + songs("z", 6),
            ),
        )
        assertEquals(6, memory?.songs?.size)
    }

    @Test
    fun `a nearer month stands in when there is nothing further back`() {
        assertEquals("2026-09", pick(local = mapOf(today.minusMonths(1) to songs("r", 5)))?.title)
    }
}

class DesktopDeepLinksTest {

    @Test
    fun `an invite link gives its code`() {
        assertEquals("ABC123", DesktopDeepLinks.parse("bitchord://party/abc123")?.code)
        assertEquals("ABC123", DesktopDeepLinks.parse("https://bitchord-party.bitchord-party.workers.dev/invite/ABC123")?.code)
    }

    @Test
    fun `a server is kept only if the app knows it`() {
        val known = "bitchord://party/ABC123?server=https%3A%2F%2Fbitchord-party.bitchord-party.workers.dev"
        assertEquals("https://bitchord-party.bitchord-party.workers.dev", DesktopDeepLinks.parse(known)?.server)
        val other = DesktopDeepLinks.parse("bitchord://party/ABC123?server=https%3A%2F%2Fevil.example")
        assertEquals("ABC123", other?.code)
        assertNull(other?.server)
    }

    @Test
    fun `anything else is not an invite`() {
        listOf(
            null,
            "",
            "bitchord://party/short",
            "bitchord://party/ABC123/more",
            "bitchord://party/ABC-23",
            "bitchord://other/ABC123",
            "https://evil.example/invite/ABC123",
            "file:///C:/x",
        ).forEach { assertNull(DesktopDeepLinks.parse(it), it.toString()) }
    }
}
