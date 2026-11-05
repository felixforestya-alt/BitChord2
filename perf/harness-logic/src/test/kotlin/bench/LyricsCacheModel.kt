package bench

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertTrue

/** A model of DesktopLyricsClient's cache: the same entries, keys, TTL and (after) the sweep. */
class LyricsCacheModel {
    private class Cached(val createdAt: Long, val result: Result<List<LyricLine>>)
    private val ttl = 12L * 60L * 60L * 1_000L

    /** A word-synced song: 60 lines of 8 words, the shape Apple's and Musixmatch's answers take. */
    private fun song(seed: Int): List<LyricLine> = List(60) { line ->
        val start = line * 4_000L
        val words = List(8) { w -> LyricWord(start + w * 400L, start + w * 400L + 380L, "word${seed % 97}$w") }
        LyricLine(start, words.joinToString(" ") { it.text }, words)
    }

    private fun usedHeap(): Long {
        repeat(3) { System.gc(); Thread.sleep(50) }
        val mx = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        return mx.used
    }

    private fun session(sweep: Boolean): Pair<Int, Long> {
        val cache = ConcurrentHashMap<String, Cached>()
        val before = usedHeap()
        // Three days in the tray at 18 tracks an hour, each looked up once.
        val tracks = 72 * 18
        for (i in 0 until tracks) {
            val now = i * (3_600_000L / 18)
            cache["v$i|200000|Title $i|Artist|"] = Cached(now, Result.success(song(i)))
            if (sweep) cache.entries.removeIf { now - it.value.createdAt >= ttl }
        }
        val held = usedHeap() - before
        val size = cache.size
        cache.clear()
        return size to held
    }

    @Test
    fun aThreeDaySessionHoldsOnlyTheLastTwelveHours() {
        val (sizeBefore, heapBefore) = session(sweep = false)
        val (sizeAfter, heapAfter) = session(sweep = true)
        println("MODEL lyrics cache after 72 h at 18 tracks/h: main ${sizeBefore} entries, ~${heapBefore / 1024 / 1024} MB held; branch ${sizeAfter} entries, ~${heapAfter / 1024 / 1024} MB held")
        assertTrue(sizeAfter <= 12 * 18 + 1 && sizeBefore == 72 * 18)
    }
}
