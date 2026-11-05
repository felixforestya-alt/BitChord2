package bench

import baseline.BaselineDesktopTrackMatcher
import com.music.bitchord.data.model.Song
import com.music.bitchord.desktop.DesktopTrackMatcher
import kotlin.test.Test
import kotlin.test.assertEquals

class MatcherBench {
    private val target = Song("kb_6Vz-k7Wc", "Tera Mera Rishta -  New Version (From \"Awarapan 2\") [Official Audio]", "Mithoon, Saaj Bhatt, Sayeed Quadri & Mustafa Zahid", null, "6:04")

    /** What a catalogue answers a search with: the right take, other takes, other songs. */
    private val candidates = List(24) { i ->
        when (i % 6) {
            0 -> Song("c$i", "Tera Mera Rishta - New Version", "Mithoon", null, "6:05")
            1 -> Song("c$i", "Tera Mera Rishta (Live)", "Mustafa Zahid", null, "5:58")
            2 -> Song("c$i", "Tera Mera Rishta - Reprise Version", "Mithoon, Sayeed Quadri", null, "4:10")
            3 -> Song("c$i", "Awarapan Title Track $i", "Pritam, KK", null, "5:21")
            4 -> Song("c$i", "Tera Mera Rishta (Unplugged) | Remix", "DJ Someone feat. Mithoon", null, "6:30")
            else -> Song("c$i", "Rishta $i - From \"Something\"", "Various Artists", null, "3:3$i".take(4))
        }
    }

    @Test
    fun rankingIsTheSameAndCheaper() {
        assertEquals(BaselineDesktopTrackMatcher.ranked(candidates, target), DesktopTrackMatcher.ranked(candidates, target))
        Bench.compare(
            "DesktopTrackMatcher.ranked, 24 candidates",
            ops = 2_000,
            a = Bench.Side("main") { BaselineDesktopTrackMatcher.ranked(candidates, target) },
            b = Bench.Side("branch") { DesktopTrackMatcher.ranked(candidates, target) },
        )
    }
}
