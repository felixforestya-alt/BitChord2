package bench

import baseline.BaselineDesktopAudioAnalyzer
import com.music.bitchord.desktop.DesktopAudioAnalyzer
import java.lang.reflect.Modifier
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyzerBench {
    private class Probe(val target: Any) {
        val cls = target.javaClass
        val analyse = cls.getDeclaredMethod("analyse", Int::class.javaPrimitiveType).apply { isAccessible = true }
        init {
            val f = cls.getDeclaredField("lastAnalysis").apply { isAccessible = true }
            if (Modifier.isStatic(f.modifiers)) f.setLong(null, Long.MAX_VALUE / 2) else f.setLong(target, Long.MAX_VALUE / 2)
        }
        @Suppress("UNCHECKED_CAST")
        fun run(rate: Int): Pair<FloatArray, Float> = analyse.invoke(target, rate) as Pair<FloatArray, Float>
    }

    private fun feed(target: Any, samples: FloatArray, rate: Int) = when (target) {
        is DesktopAudioAnalyzer -> target.feed(samples, samples.size, 2, rate, 0L)
        is BaselineDesktopAudioAnalyzer -> target.feed(samples, samples.size, 2, rate, 0L)
        else -> error("?")
    }

    @Test
    fun analysisIsIdenticalAndCheaper() {
        val a = Probe(BaselineDesktopAudioAnalyzer)
        val b = Probe(DesktopAudioAnalyzer)
        val random = Random(3)
        for (rate in intArrayOf(44_100, 48_000)) {
            for (step in 0 until 200) {
                val block = FloatArray(800 * 2) { i ->
                    val t = (step * 800 + i / 2).toDouble() / rate
                    (0.4 * sin(2 * PI * (80 + step) * t) + 0.2 * sin(2 * PI * 2500 * t) + 0.1 * (random.nextDouble() - 0.5)).toFloat()
                }
                feed(a.target, block, rate)
                feed(b.target, block, rate)
                if (step < 3) continue
                val (bandsA, levelA) = a.run(rate)
                val (bandsB, levelB) = b.run(rate)
                assertTrue(bandsA.contentEquals(bandsB), "bands differ at $rate Hz, step $step")
                assertEquals(levelA.toRawBits(), levelB.toRawBits(), "level differs at $rate Hz, step $step")
            }
        }
        Bench.compare(
            "DesktopAudioAnalyzer.analyse (2048-point FFT + 48 bands), 48 kHz",
            ops = 2_000,
            a = Bench.Side("main") { a.run(48_000) },
            b = Bench.Side("branch") { b.run(48_000) },
        )
        val block = FloatArray(1024 * 2) { (sin(it * 0.01) * 0.3).toFloat() }
        Bench.compare(
            "DesktopAudioAnalyzer.feed, 1024 stereo frames (no analysis due)",
            ops = 20_000,
            a = Bench.Side("main") { feed(a.target, block, 48_000) },
            b = Bench.Side("branch") { feed(b.target, block, 48_000) },
        )
        Bench.compare(
            "feed, order swapped",
            ops = 20_000,
            a = Bench.Side("branch") { feed(b.target, block, 48_000) },
            b = Bench.Side("main") { feed(a.target, block, 48_000) },
        )
        Bench.compare(
            "feed, A/A main",
            ops = 20_000,
            a = Bench.Side("main") { feed(a.target, block, 48_000) },
            b = Bench.Side("main") { feed(a.target, block, 48_000) },
        )
    }
}
