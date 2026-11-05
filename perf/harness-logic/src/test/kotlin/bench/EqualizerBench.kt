package bench

import baseline.BaselineDesktopEqualizer
import com.music.bitchord.desktop.DesktopEqualizer
import com.music.bitchord.playback.EqCurve
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.EqualizerPreset
import com.music.bitchord.playback.manualCurve
import com.music.bitchord.playback.toneCurve
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class EqualizerBench {
    private fun block(seed: Int, frames: Int): FloatArray {
        val r = Random(seed)
        return FloatArray(frames * 2) { i -> (0.4 * sin(2 * PI * 97 * (i / 2) / 48_000.0) + 0.2 * (r.nextDouble() - 0.5)).toFloat() }
    }

    @Test
    fun equaliserIsIdenticalAndCheaper() {
        val tone = toneCurve(x = EqLayout.TONE_STEPS / 2, y = -EqLayout.TONE_STEPS / 3, focused = false)
        val preset = manualCurve(EqualizerPreset.LOUDNESS.bandsDb.toList())
        // A whole session: on with the pad, settled, then a preset, then off and gliding down, then a seek.
        val script = buildList<Pair<Int, (Any) -> Unit>> {
            add(0 to { e -> set(e, true, tone, 0.1f) })
            add(300 to { e -> set(e, true, preset, 0f) })
            add(600 to { e -> set(e, true, preset, -0.4f) })
            add(800 to { e -> set(e, false, preset, 0f) })
            add(950 to { e -> reset(e) })
            add(1000 to { e -> set(e, true, tone, 0f) })
        }
        val a = BaselineDesktopEqualizer().apply { configure(2, 48_000) }
        val b = DesktopEqualizer().apply { configure(2, 48_000) }
        for (index in 0 until 1300) {
            script.filter { it.first == index }.forEach { (_, act) -> act(a); act(b) }
            val x = block(index, 1024)
            val y = x.copyOf()
            a.process(x, x.size)
            b.process(y, y.size)
            assertTrue(x.contentEquals(y), "block $index differs")
        }

        for ((name, curve) in listOf("tone pad (3 sections)" to tone, "LOUDNESS preset (7 sections)" to preset)) {
            val input = block(1, 1024)
            val work = FloatArray(input.size)
            val ea = BaselineDesktopEqualizer().apply { configure(2, 48_000); setTuning(true, curve, 0f); reset() }
            val eb = DesktopEqualizer().apply { configure(2, 48_000); setTuning(true, curve, 0f); reset() }
            Bench.compare(
                "DesktopEqualizer.process, 1024 stereo frames at 48 kHz, $name, at rest",
                ops = 4_000,
                a = Bench.Side("main") { input.copyInto(work); ea.setTuning(true, curve, 0f); ea.process(work, work.size) },
                b = Bench.Side("branch") { input.copyInto(work); eb.setTuning(true, curve, 0f); eb.process(work, work.size) },
            )
        }
    }

    private fun set(e: Any, on: Boolean, c: EqCurve, balance: Float) = when (e) {
        is DesktopEqualizer -> e.setTuning(on, c, balance)
        is BaselineDesktopEqualizer -> e.setTuning(on, c, balance)
        else -> error("?")
    }

    private fun reset(e: Any) = when (e) {
        is DesktopEqualizer -> e.reset()
        is BaselineDesktopEqualizer -> e.reset()
        else -> error("?")
    }
}
