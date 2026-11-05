package bench

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import kotlin.test.Test

class LyricSweepBench {
    @Test
    fun revealedCharsPerFrame() {
        // A long word-synced line: 14 words, sung over 5.6 s.
        val words = List(14) { w -> LyricWord(10_000L + w * 400L, 10_000L + w * 400L + 360L, listOf("we", "were", "dancing", "under", "the", "golden", "light", "of", "a", "summer", "that", "never", "ended", "tonight")[w]) }
        val line = LyricLine(10_000L, words.joinToString(" ") { it.text }, words)
        var t = 10_000L
        var sink = 0f
        val mx = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        repeat(200_000) { sink += line.revealedChars(10_000L + (it % 5_600)) }
        val a0 = mx.currentThreadAllocatedBytes
        val t0 = System.nanoTime()
        val n = 2_000_000
        for (i in 0 until n) { sink += line.revealedChars(t); t += 3; if (t > 15_600L) t = 10_000L }
        val ns = (System.nanoTime() - t0).toDouble() / n
        val bytes = (mx.currentThreadAllocatedBytes - a0).toDouble() / n
        println("BENCH LyricLine.revealedChars, 14-word line: ${"%.0f".format(ns)} ns and ${"%.1f".format(bytes)} B per call (sink $sink)")
    }
}
