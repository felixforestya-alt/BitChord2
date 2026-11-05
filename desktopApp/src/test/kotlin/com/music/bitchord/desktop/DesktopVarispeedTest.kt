package com.music.bitchord.desktop

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopVarispeedTest {

    private val rate = 48_000

    /** A stereo tone fed through in uneven blocks, as the decoder hands them over. */
    private fun play(speed: Float, hz: Double, seconds: Double): FloatArray {
        val turntable = DesktopVarispeed(channels = 2).apply { this.speed = speed }
        val frames = (rate * seconds).toInt()
        val out = ArrayList<Float>()
        var at = 0
        var block = 0
        while (at < frames) {
            val size = minOf(frames - at, 700 + (block++ % 5) * 311)
            val input = FloatArray(size * 2) { sin(2 * PI * hz * (at + it / 2) / rate).toFloat() * 0.5f }
            val result = turntable.process(input, input.size)
            for (i in 0 until turntable.outputCount step 2) out += result[i]
            at += size
        }
        return out.toFloatArray()
    }

    /** Upward zero crossings a second, past the filter's start-up. */
    private fun frequency(left: FloatArray): Double {
        val from = 2_000
        var crossings = 0
        for (i in from + 1 until left.size) if (left[i - 1] < 0f && left[i] >= 0f) crossings++
        return crossings.toDouble() * rate / (left.size - from)
    }

    @Test
    fun `faster plays higher and shorter, like a record`() {
        val left = play(1.25f, 440.0, 2.0)
        assertEquals(550.0, frequency(left), 3.0)
        assertEquals(2.0 / 1.25, left.size.toDouble() / rate, 0.02)
    }

    @Test
    fun `slower plays lower and longer`() {
        val left = play(0.8f, 500.0, 2.0)
        assertEquals(400.0, frequency(left), 3.0)
        assertEquals(2.0 / 0.8, left.size.toDouble() / rate, 0.02)
    }

    @Test
    fun `no clicks between blocks and the level stays`() {
        val left = play(1.1f, 300.0, 2.0)
        val body = left.copyOfRange(2_000, left.size - 2_000)
        // A 330 Hz sine at 0.5 moves at most about 0.022 a sample; a join would jump far more.
        var worst = 0f
        for (i in 1 until body.size) worst = maxOf(worst, abs(body[i] - body[i - 1]))
        assertTrue(worst < 0.03f, "largest step $worst")
        val rms = sqrt(body.sumOf { (it * it).toDouble() } / body.size)
        assertEquals(0.5 / sqrt(2.0), rms, 0.01)
    }

    @Test
    fun `at 1x the samples go through untouched`() {
        val turntable = DesktopVarispeed(channels = 2)
        val input = FloatArray(1_000) { it / 1_000f }
        val result = turntable.process(input, input.size)
        assertEquals(1_000, turntable.outputCount)
        assertTrue(result === input)
    }
}
