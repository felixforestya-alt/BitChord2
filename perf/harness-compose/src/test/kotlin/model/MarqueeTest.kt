package model

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import stub.AppActivity
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A model of MarqueeText's crawl (desktopUi PlayerControls.kt), before and after. The start delay
 * and the rest between laps are 0 here: they are real-time delays, and the scene's frames are not.
 */
class MarqueeTest {
    private val dpPerSec = 26f
    private val rest = 0L
    private val text = "A title far too long to fit in the space the player gives it (Deluxe Edition)"
    private val travel = 900

    @Composable
    private fun CrawlBefore(offsetX: Animatable<Float, AnimationVector1D>, travelPx: Int, startDelayMillis: Long) {
        val density = LocalDensity.current
        LaunchedEffect(text, travelPx, startDelayMillis) {
            offsetX.snapTo(0f)
            if (travelPx <= 0) return@LaunchedEffect
            val pxPerMs = with(density) { dpPerSec.dp.toPx() } / 1000f
            val scrollMs = (travelPx / pxPerMs).roundToInt().coerceAtLeast(400)
            delay(startDelayMillis)
            while (true) {
                offsetX.animateTo(-travelPx.toFloat(), tween(scrollMs, easing = LinearEasing))
                offsetX.snapTo(0f)
                delay(rest)
            }
        }
    }

    private class MarqueeProgress {
        var setOff = false
        var resting = false
    }

    // Copied from PlayerControls.kt's MarqueeCrawl, with MARQUEE_REST_MS as [rest].
    @Composable
    private fun CrawlAfter(offsetX: Animatable<Float, AnimationVector1D>, travelPx: Int, startDelayMillis: Long) {
        val density = LocalDensity.current
        val progress = remember(text, travelPx, startDelayMillis) { MarqueeProgress() }
        val quiet = AppActivity.quiet
        LaunchedEffect(progress, quiet) {
            if (!progress.setOff) offsetX.snapTo(0f)
            if (travelPx <= 0 || quiet) return@LaunchedEffect
            val pxPerMs = with(density) { dpPerSec.dp.toPx() } / 1000f
            val scrollMs = (travelPx / pxPerMs).roundToInt().coerceAtLeast(400)
            if (!progress.setOff) {
                delay(startDelayMillis)
                progress.setOff = true
            } else if (progress.resting) {
                delay(rest)
            }
            while (true) {
                progress.resting = false
                val left = 1f + offsetX.value / travelPx
                offsetX.animateTo(-travelPx.toFloat(), tween((scrollMs * left).roundToInt().coerceAtLeast(1), easing = LinearEasing))
                offsetX.snapTo(0f)
                progress.resting = true
                delay(rest)
            }
        }
    }

    @Composable
    private fun Line(variant: String, offsetX: Animatable<Float, AnimationVector1D>) {
        Box(Modifier.width(320.dp).clipToBounds().background(Color.Black)) {
            if (variant == "before") CrawlBefore(offsetX, travel, 0L) else CrawlAfter(offsetX, travel, 0L)
            Row(Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true).offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
                Text(text, color = Color.White, fontSize = 20.sp, softWrap = false, maxLines = 1)
                Spacer(Modifier.width(48.dp))
                Text(text, color = Color.White, fontSize = 20.sp, softWrap = false, maxLines = 1)
            }
        }
    }

    @Test
    fun inSightTheCrawlIsFrameForFrameTheSame() {
        AppActivity.quiet = false
        val frames = 2400 // 40 s at 60 fps: more than a lap of 900 px at 26 dp/s
        val times = LongArray(frames) { it * 16_666_667L }
        val keep = (0 until frames step 200).toSet()
        val results = listOf("before", "after").map { variant ->
            val offsets = FloatArray(frames)
            lateinit var anim: Animatable<Float, AnimationVector1D>
            val run = runFrames(320, 30, times, keep, before = { i, _ -> if (i > 0) offsets[i - 1] = anim.value }) {
                anim = remember { Animatable(0f) }
                Line(variant, anim)
            }
            offsets to run
        }
        sheet(File("build/frames/marquee.png"), results[0].second.images, results[1].second.images)
        val same = results[0].first.contentEquals(results[1].first)
        println("MODEL marquee in sight: ${frames} frames, offsets identical=$same, min offset ${results[0].first.min()} / ${results[1].first.min()}")
        assertTrue(same)
        for (i in results[0].second.pngs.indices) assertTrue(results[0].second.pngs[i].contentEquals(results[1].second.pngs[i]), "frame ${keep.sorted()[i]} differs")
    }

    @Test
    fun outOfSightItAsksForNoFramesAndCarriesOn() {
        val outcome = listOf("before", "after").map { variant ->
            AppActivity.quiet = false
            lateinit var anim: Animatable<Float, AnimationVector1D>
            val scene = androidx.compose.ui.ImageComposeScene(320, 30) {
                anim = remember { Animatable(0f) }
                Line(variant, anim)
            }
            var t = 0L
            repeat(300) { scene.render(t); t += 16_666_667L }
            val atHide = anim.value
            AppActivity.quiet = true
            scene.render(t); t += 16_666_667L
            var wanted = 0
            repeat(60) { if (scene.hasInvalidations()) wanted++; scene.render(t); t += 16_666_667L }
            AppActivity.quiet = false
            scene.render(t); t += 16_666_667L
            scene.render(t); t += 16_666_667L
            val back = anim.value
            scene.close()
            println("MODEL marquee out of sight, $variant: $wanted of 60 frames still asked for; offset when hidden ${"%.1f".format(atHide)}, two frames back ${"%.1f".format(back)}")
            Triple(wanted, atHide, back)
        }
        assertTrue(outcome[0].first >= 59)
        assertEquals(0, outcome[1].first)
        assertTrue(outcome[1].third < outcome[1].second && outcome[1].second - outcome[1].third < 3f, "did not carry on from where it was")
    }
}
