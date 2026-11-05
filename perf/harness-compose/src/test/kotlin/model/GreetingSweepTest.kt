package model

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import stub.AppActivity
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A model of DesktopHomeHeader's greeting sweep (DesktopHome.kt), before and after. */
class GreetingSweepTest {
    private var header = 0
    val trace = ArrayList<String>()
    private var greetingScope = 0
    private val accent = Color(0xFF7C6CFF)

    private fun Color.hueShifted(degrees: Float, value: Float? = null): Color {
        val hsv = FloatArray(3)
        val maxC = max(red, max(green, blue)); val minC = min(red, min(green, blue)); val delta = maxC - minC
        val hue = when { delta == 0f -> 0f; maxC == red -> 60f * (((green - blue) / delta) % 6f); maxC == green -> 60f * (((blue - red) / delta) + 2f); else -> 60f * (((red - green) / delta) + 4f) }
        hsv[0] = if (hue < 0f) hue + 360f else hue; hsv[1] = if (maxC == 0f) 0f else delta / maxC; hsv[2] = maxC
        val h = ((hsv[0] + degrees) % 360f + 360f) % 360f
        return Color.hsv(h, hsv[1].coerceIn(0f, 1f), (value ?: hsv[2]).coerceIn(0f, 1f))
    }
    private fun lerpTo(colour: Color, t: Float) = androidx.compose.ui.graphics.lerp(colour, Color.White, t)
    private fun greeting() = "Good evening"
    private fun style(sweep: Float) = TextStyle(
        brush = Brush.linearGradient(
            listOf(Color.White, lerpTo(accent, 0.25f), accent.hueShifted(40f, 1f), Color.White),
            start = Offset(sweep * 1600f - 800f, 0f),
            end = Offset(sweep * 1600f, 120f),
            tileMode = TileMode.Mirror,
        ),
        fontWeight = FontWeight.Black,
        fontSize = 46.sp,
        letterSpacing = (-1.2).sp,
    )

    @Composable
    private fun Rest() {
        Text(
            LocalDate.of(2026, 11, 5).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag("en"))),
            color = Color.Gray, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp),
        )
        Box(Modifier.padding(top = 20.dp).fillMaxWidth().height(60.dp).background(Color(0xFF222233)))
    }

    /** The animation of each version, recording every value it steps to (in the frame, not in composition). */
    @Composable
    private fun SweepBefore(sweepAnim: Animatable<Float, *>, record: (Float) -> Unit) {
        LaunchedEffect(Unit) {
            while (true) {
                sweepAnim.snapTo(0f)
                sweepAnim.animateTo(1f, tween(4_000, easing = LinearEasing)) { record(value) }
                delay(20_000)
            }
        }
    }

    @Composable
    private fun SweepAfter(sweepAnim: Animatable<Float, *>, record: (Float) -> Unit) {
        val quiet = AppActivity.quiet
        LaunchedEffect(quiet) {
            if (quiet) return@LaunchedEffect
            while (true) {
                val left = 1f - sweepAnim.value
                if (left > 0f) sweepAnim.animateTo(1f, tween((4_000 * left).toInt().coerceAtLeast(1), easing = LinearEasing)) { record(value) }
                delay(20_000)
                sweepAnim.snapTo(0f)
            }
        }
    }

    /** DesktopHomeHeader before: the sweep read in the header's own composition. */
    @Composable
    private fun HeaderBefore(sweepValue: () -> Float) {
        SideEffect { header++ }
        val sweep = sweepValue()
        Column(Modifier.fillMaxWidth().background(Color.Black).padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SideEffect { greetingScope++ }
                Text(greeting(), modifier = Modifier.weight(1f), style = style(sweep))
                Text("Customize Home", color = Color.White)
            }
            Rest()
        }
    }

    @Composable
    private fun HomeGreeting(sweep: () -> Float, modifier: Modifier) {
        SideEffect { greetingScope++ }
        val at = sweep()
        Text(greeting(), modifier = modifier, style = style(at))
    }

    /** DesktopHomeHeader after: only HomeGreeting reads it. */
    @Composable
    private fun HeaderAfter(sweepValue: () -> Float) {
        SideEffect { header++ }
        Column(Modifier.fillMaxWidth().background(Color.Black).padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomeGreeting(sweep = sweepValue, modifier = Modifier.weight(1f))
                Text("Customize Home", color = Color.White)
            }
            Rest()
        }
    }

    @Test
    fun theSweepRecomposesOnlyTheGreetingAndLooksTheSame() {
        // The frames of one sweep at 60 fps, the sweep's value written each frame as the animation does.
        val frames = 241
        val times = LongArray(frames) { it * 16_666_667L }
        val keep = (0 until frames step 24).toSet()
        val runs = listOf("before", "after").map { variant ->
            header = 0; greetingScope = 0
            val sweep = androidx.compose.runtime.mutableFloatStateOf(0f)
            val run = runFrames(640, 200, times, keep, before = { i, _ -> sweep.floatValue = i / 240f }) {
                if (variant == "before") HeaderBefore { sweep.floatValue } else HeaderAfter { sweep.floatValue }
            }
            println("MODEL greeting sweep $variant, $frames frames: header compositions=$header, greeting compositions=$greetingScope, render() median ${median(run.renderNanos.copyOfRange(10, frames)) / 1000} µs")
            Triple(header, run, greetingScope)
        }
        sheet(File("build/frames/greeting-sweep.png"), runs[0].second.images, runs[1].second.images)
        for (i in runs[0].second.pngs.indices) assertTrue(runs[0].second.pngs[i].contentEquals(runs[1].second.pngs[i]), "frame ${keep.sorted()[i]} differs")
        assertTrue(runs[1].first < 5 && runs[0].first > 200)
    }

    @Test
    fun theSweepMovesExactlyAsBefore() {
        val sequences = listOf("before", "after").map { variant ->
            val values = ArrayList<Float>()
            runFrames(64, 16, LongArray(260) { it * 16_666_667L }) {
                val anim = remember { Animatable(0f) }
                if (variant == "before") SweepBefore(anim) { values += it } else SweepAfter(anim) { values += it }
            }
            values
        }
        println("MODEL greeting sweep animation: ${sequences[0].size} steps before, ${sequences[1].size} after, identical=${sequences[0] == sequences[1]}")
        assertEquals(sequences[0], sequences[1])
    }

    @Test
    fun outOfSightTheSweepAsksForNoFramesAndCarriesOnAfter() {
        AppActivity.quiet = false
        val results = listOf("before", "after").map { variant ->
            val values = ArrayList<Float>()
            val scene = androidx.compose.ui.ImageComposeScene(64, 16) {
                val anim = remember { Animatable(0f) }
                if (variant == "before") SweepBefore(anim) { values += it } else SweepAfter(anim) { values += it }
            }
            var t = 0L
            repeat(60) { scene.render(t); t += 16_666_667L }
            val atHide = values.last()
            AppActivity.quiet = true
            scene.render(t); t += 16_666_667L
            // Half a second out of sight, partway through the sweep: how many frames does it still want?
            var wanted = 0
            repeat(30) { if (scene.hasInvalidations()) wanted++; scene.render(t); t += 16_666_667L }
            val hiddenSteps = values.size
            AppActivity.quiet = false
            repeat(30) { scene.render(t); t += 16_666_667L }
            scene.close()
            val firstBack = values.getOrNull(hiddenSteps)
            println("MODEL greeting sweep out of sight, $variant: $wanted of 30 frames still asked for; value when hidden ${"%.4f".format(atHide)}, first value back ${firstBack?.let { "%.4f".format(it) }}")
            wanted to (atHide to firstBack)
        }
        assertTrue(results[0].first >= 29)
        assertEquals(0, results[1].first)
        // Carried on from where it was, at the same pace (one frame's step, 16.7 ms of 4 s).
        val (hidden, back) = results[1].second
        assertTrue(back != null && back > hidden && back - hidden < 0.01f)
    }
}
