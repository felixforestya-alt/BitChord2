package bench

import baseline.BaselineDesktopVarispeed
import com.music.bitchord.desktop.DesktopVarispeed
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class VarispeedBench {
    private fun music(rate: Int, seconds: Int): FloatArray {
        val frames = rate * seconds
        val out = FloatArray(frames * 2)
        val random = Random(11)
        for (i in 0 until frames) {
            val t = i.toDouble() / rate
            out[i * 2] = (0.3 * sin(2 * PI * 220 * t) + 0.1 * sin(2 * PI * 3100 * t) + 0.02 * (random.nextDouble() - 0.5)).toFloat()
            out[i * 2 + 1] = (0.3 * sin(2 * PI * 330 * t) + 0.1 * sin(2 * PI * 5100 * t) + 0.02 * (random.nextDouble() - 0.5)).toFloat()
        }
        return out
    }

    private fun collect(speed: Float, input: FloatArray, a: Boolean): FloatArray {
        val out = ArrayList<Float>()
        val b = FloatArray(2048)
        val n = DesktopVarispeed(2).apply { this.speed = speed }
        val o = BaselineDesktopVarispeed(2).apply { this.speed = speed }
        var at = 0
        while (at < input.size) {
            val c = minOf(b.size, input.size - at)
            System.arraycopy(input, at, b, 0, c)
            if (a) { val r = o.process(b, c); for (i in 0 until o.outputCount) out += r[i] }
            else { val r = n.process(b, c); for (i in 0 until n.outputCount) out += r[i] }
            at += c
        }
        return out.toFloatArray()
    }

    @Test
    fun varispeedIsIdenticalAndFaster() {
        val input = music(48_000, 2)
        for (speed in floatArrayOf(1.25f, 0.8f, 1.5f, 0.5f)) {
            assertTrue(collect(speed, input, true).contentEquals(collect(speed, input, false)), "differs at $speed")
        }
        val second = music(48_000, 1)
        val block = FloatArray(2048)
        fun feeder(baseline: Boolean, speed: Float): () -> Unit {
            val n = DesktopVarispeed(2).apply { this.speed = speed }
            val o = BaselineDesktopVarispeed(2).apply { this.speed = speed }
            return {
                var at = 0
                while (at < second.size) {
                    val c = minOf(block.size, second.size - at)
                    System.arraycopy(second, at, block, 0, c)
                    if (baseline) o.process(block, c) else n.process(block, c)
                    at += c
                }
            }
        }
        for (speed in floatArrayOf(1.25f, 0.8f)) {
            Bench.compare(
                "DesktopVarispeed.process, 1 s of 48 kHz stereo at ${speed}x, 1024-frame blocks",
                ops = 5,
                a = Bench.Side("main", feeder(true, speed)),
                b = Bench.Side("branch", feeder(false, speed)),
            )
        }
    }
}
