package com.music.bitchord.ui.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.lang.management.ManagementFactory
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The aurora drawn with its colours and paths kept between frames costs less than with them made
 * anew on every frame, and draws the same pixels. The old drawing is kept here as it was.
 */
class AuroraCostTest {

    /** Skipped where Skia's native library is not on the test classpath (see the build file). */
    @BeforeTest
    fun skiaLoads() {
        org.junit.Assume.assumeTrue(runCatching { org.jetbrains.skia.Path().close() }.isSuccess)
    }

    private fun DrawScope.auroraBefore(t: Float, energy: Energy, accent: Color) {
        val lights = listOf(
            accent.discHueShifted(0f, 0.95f) to energy.bass,
            accent.discHueShifted(38f, 0.9f) to energy.mid,
            accent.discHueShifted(-46f, 0.85f) to energy.high,
            accent.discHueShifted(180f, 0.75f) to energy.mid * 0.7f,
            Color.White to energy.high * 0.6f,
        )
        lights.forEachIndexed { i, (colour, level) ->
            val cx = size.width * (0.58f + 0.30f * sin(t * (0.07f + i * 0.013f) + i * 1.7f))
            val cy = size.height * (0.46f + 0.30f * cos(t * (0.06f + i * 0.011f) + i * 2.3f))
            val radius = size.minDimension * (0.34f + 0.20f * level + 0.04f * sin(t * 0.8f + i))
            val strength = if (colour == Color.White) 0.10f + level * 0.25f else 0.28f + level * 0.45f
            drawCircle(
                Brush.radialGradient(
                    0f to colour.copy(alpha = strength.coerceAtMost(0.85f)),
                    0.55f to colour.copy(alpha = strength * 0.35f),
                    1f to Color.Transparent,
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
                blendMode = BlendMode.Screen,
            )
        }
        for (k in 0 until 3) {
            val path = Path()
            val base = size.height * (0.28f + k * 0.14f)
            var x = 0f
            path.moveTo(0f, base)
            while (x <= size.width) {
                val y = base + sin(x / size.width * 5f + t * (0.25f + k * 0.08f) + k) * size.height * (0.05f + energy.mid * 0.06f)
                path.lineTo(x, y)
                x += 16f
            }
            drawPath(
                path,
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, accent.discHueShifted(k * 30f - 30f, 1f).copy(alpha = 0.22f + energy.high * 0.3f), Color.Transparent),
                ),
                style = Stroke(width = size.height * (0.03f + energy.bass * 0.03f), cap = StrokeCap.Round),
                blendMode = BlendMode.Screen,
            )
        }
    }

    // The size the phone draws it at: half of a 1080 × 2340 screen.
    private val width = 540
    private val height = 1170
    private val accent = Color(0xFF7A3CE0)

    private fun frames(count: Int, start: Int, draw: DrawScope.(Float, Energy) -> Unit): ImageBitmap {
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        val scope = CanvasDrawScope()
        for (f in start until start + count) {
            val t = f / 60f
            val energy = Energy(0.5f + 0.4f * sin(t * 3f), 0.4f + 0.3f * cos(t * 2f), 0.3f + 0.2f * sin(t * 5f))
            scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
                drawRect(Color.Black)
                draw(t, energy)
            }
        }
        return bitmap
    }

    @Test
    fun theSamePixels() {
        val parts = AuroraParts()
        for (f in listOf(0, 37, 600, 4_321)) {
            val before = frames(1, f) { t, e -> auroraBefore(t, e, accent) }.toPixelMap()
            val after = frames(1, f) { t, e -> aurora(t, e, accent, parts) }.toPixelMap()
            for (y in 0 until height step 3) for (x in 0 until width step 3) {
                assertTrue(before[x, y] == after[x, y], "pixel $x,$y of frame $f")
            }
        }
    }

    @Test
    fun cost() {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val parts = AuroraParts()
        fun measure(draw: DrawScope.(Float, Energy) -> Unit): Pair<Double, Double> {
            val n = 600
            val bytes0 = threads.getThreadAllocatedBytes(Thread.currentThread().id)
            val t0 = System.nanoTime()
            frames(n, 0, draw)
            val micros = (System.nanoTime() - t0) / n / 1e3
            val bytes = (threads.getThreadAllocatedBytes(Thread.currentThread().id) - bytes0).toDouble() / n
            return micros to bytes
        }
        val before: DrawScope.(Float, Energy) -> Unit = { t, e -> auroraBefore(t, e, accent) }
        val after: DrawScope.(Float, Energy) -> Unit = { t, e -> aurora(t, e, accent, parts) }
        repeat(3) { measure(before); measure(after) }
        // Each round both, in turn, so a slower moment of the machine lands on both.
        val rounds = List(5) { listOf(measure(before), measure(after)) }
        val b = rounds.map { it[0] }
        val a = rounds.map { it[1] }
        println(
            "BENCH aurora frame at 540x1170: before %.0f-%.0f us, %.0f B; after %.0f-%.0f us, %.0f B (5 rounds of 600 frames, JVM)".format(
                b.minOf { it.first }, b.maxOf { it.first }, b.map { it.second }.average(),
                a.minOf { it.first }, a.maxOf { it.first }, a.map { it.second }.average(),
            ),
        )
    }
}
