package bench

import baseline.BaselineDesktopAudioAnalyzer
import com.music.bitchord.desktop.DesktopAudioAnalyzer
import java.lang.reflect.Modifier
import kotlin.test.Test

/** DesktopAudioAnalyzer.feed alone, with no analysis due: the per-sample ring fill. */
class AnalyzerFeedBench {
    private fun hold(target: Any) {
        val f = target.javaClass.getDeclaredField("lastAnalysis").apply { isAccessible = true }
        if (Modifier.isStatic(f.modifiers)) f.setLong(null, Long.MAX_VALUE / 2) else f.setLong(target, Long.MAX_VALUE / 2)
    }

    @Test
    fun feedOnly() {
        hold(BaselineDesktopAudioAnalyzer)
        hold(DesktopAudioAnalyzer)
        val block = FloatArray(1024 * 2) { (kotlin.math.sin(it * 0.01) * 0.3).toFloat() }
        repeat(3) { run ->
            Bench.compare(
                "feed only, run ${run + 1}",
                ops = 50_000,
                rounds = 61,
                a = Bench.Side("main") { BaselineDesktopAudioAnalyzer.feed(block, block.size, 2, 48_000, 0L) },
                b = Bench.Side("branch") { DesktopAudioAnalyzer.feed(block, block.size, 2, 48_000, 0L) },
            )
        }
    }
}
