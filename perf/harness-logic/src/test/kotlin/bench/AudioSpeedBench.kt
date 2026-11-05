package bench

import baseline.BaselineDesktopAudioSpeed
import com.music.bitchord.desktop.DesktopAudioSpeed
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class AudioSpeedBench {
    /** Something like music: a few partials, a beat, a little noise — stereo, decoder-sized blocks. */
    private fun music(rate: Int, seconds: Int): FloatArray {
        val frames = rate * seconds
        val out = FloatArray(frames * 2)
        val random = Random(7)
        for (i in 0 until frames) {
            val t = i.toDouble() / rate
            val beat = if ((t * 2.0) % 1.0 < 0.08) 0.5 else 0.0
            val l = 0.3 * sin(2 * PI * 220 * t) + 0.2 * sin(2 * PI * 331 * t) + 0.1 * sin(2 * PI * 1250 * t) + beat * sin(2 * PI * 60 * t) + 0.02 * (random.nextDouble() - 0.5)
            val r = 0.3 * sin(2 * PI * 220 * t + 0.4) + 0.2 * sin(2 * PI * 443 * t) + 0.1 * sin(2 * PI * 1800 * t) + beat * sin(2 * PI * 60 * t) + 0.02 * (random.nextDouble() - 0.5)
            out[i * 2] = l.toFloat(); out[i * 2 + 1] = r.toFloat()
        }
        return out
    }

    private fun run(speed: Float, input: FloatArray, block: Int, make: () -> Any): FloatArray {
        val stretch = make()
        val collected = ArrayList<Float>()
        var at = 0
        val buf = FloatArray(block * 2)
        while (at < input.size) {
            val n = minOf(block * 2, input.size - at)
            System.arraycopy(input, at, buf, 0, n)
            when (stretch) {
                is DesktopAudioSpeed -> { stretch.speed = speed; val o = stretch.process(buf, n); for (i in 0 until stretch.outputCount) collected += o[i] }
                is BaselineDesktopAudioSpeed -> { stretch.speed = speed; val o = stretch.process(buf, n); for (i in 0 until stretch.outputCount) collected += o[i] }
            }
            at += n
        }
        return collected.toFloatArray()
    }

    @Test
    fun keepPitchSpeedIsIdenticalAndFaster() {
        for (rate in intArrayOf(44_100, 48_000)) {
            val input = music(rate, 2)
            for (speed in floatArrayOf(1.25f, 0.8f, 1.5f)) {
                val before = run(speed, input, 1024) { BaselineDesktopAudioSpeed(2, rate) }
                val after = run(speed, input, 1024) { DesktopAudioSpeed(2, rate) }
                assertTrue(before.contentEquals(after), "output differs at $rate Hz, ${speed}x (${before.size} vs ${after.size})")
            }
            // Timing: one second of 48k/44.1k stereo audio, 1024-frame blocks, at 1.25x.
            val second = music(rate, 1)
            val block = FloatArray(1024 * 2)
            fun feeder(make: () -> Any): () -> Unit {
                val stretch = make()
                return {
                    var at = 0
                    while (at < second.size) {
                        val n = minOf(block.size, second.size - at)
                        System.arraycopy(second, at, block, 0, n)
                        when (stretch) {
                            is DesktopAudioSpeed -> { stretch.speed = 1.25f; stretch.process(block, n) }
                            is BaselineDesktopAudioSpeed -> { stretch.speed = 1.25f; stretch.process(block, n) }
                        }
                        at += n
                    }
                }
            }
            Bench.compare(
                "DesktopAudioSpeed.process, 1 s of stereo at $rate Hz, 1.25x, 1024-frame blocks",
                ops = 3,
                a = Bench.Side("main", feeder { BaselineDesktopAudioSpeed(2, rate) }),
                b = Bench.Side("branch", feeder { DesktopAudioSpeed(2, rate) }),
            )
        }
    }
}
