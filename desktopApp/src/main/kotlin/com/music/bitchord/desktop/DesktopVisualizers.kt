package com.music.bitchord.desktop

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** How loud the lows, the mids and the highs are, 0..1 each. */
private class Energy(val bass: Float, val mid: Float, val high: Float) {
    val overall: Float get() = (bass * 0.5f + mid * 0.35f + high * 0.15f)
}

private fun energyOf(bands: FloatArray): Energy {
    fun avg(from: Int, to: Int): Float {
        var sum = 0f
        for (i in from until to) sum += bands[i]
        return sum / (to - from)
    }
    val n = bands.size
    return Energy(avg(0, n / 8), avg(n / 8, n * 5 / 8), avg(n * 5 / 8, n))
}

/**
 * What moves behind the track, in whichever [style] the listener picked. Everything here follows
 * the real output through [DesktopAudioAnalyzer], read on the frame [clock] so it redraws every
 * frame without recomposing anything.
 *
 * @param disc where the disc is, centre and radius, for the styles that radiate from it.
 * @param focus the stretch beside the disc where the words are, for the bars.
 */
@Composable
internal fun DesktopVisualizer(
    style: DesktopVisualizerStyle,
    clock: () -> Float,
    accent: Color,
    disc: () -> Pair<Offset, Float>,
    focus: () -> Rect,
    modifier: Modifier,
) {
    if (style == DesktopVisualizerStyle.NONE) return
    if (style == DesktopVisualizerStyle.AURORA) {
        // Soft lights and broad ribbons, drawn at half the window's size into a layer of their
        // own and laid over the page once, stretched back up. Drawn straight at full size, each of
        // the eight passes blended over the whole window: on a 4K screen the aurora alone was
        // about half the GPU's work for a frame, and beside a game the frames came late. Screen
        // is as good over the layer as over each light (screening is associative), and what is
        // all soft gradients looks the same stretched.
        androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
            Canvas(
                Modifier
                    .size(maxWidth / AURORA_SCALE, maxHeight / AURORA_SCALE)
                    .graphicsLayer {
                        scaleX = AURORA_SCALE
                        scaleY = AURORA_SCALE
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        blendMode = BlendMode.Screen
                    },
            ) {
                val bands = DesktopAudioAnalyzer.bandsAt(System.nanoTime())
                aurora(clock(), energyOf(bands), accent)
            }
        }
        return
    }
    val particles = remember { ParticleField(220) }
    Canvas(modifier) {
        val t = clock()
        val bands = DesktopAudioAnalyzer.bandsAt(System.nanoTime())
        val energy = energyOf(bands)
        when (style) {
            DesktopVisualizerStyle.AURORA -> aurora(t, energy, accent)
            DesktopVisualizerStyle.RING -> ring(t, bands, energy, accent, disc())
            DesktopVisualizerStyle.WAVES -> waves(t, energy, accent)
            DesktopVisualizerStyle.PARTICLES -> particles.step(this, t, energy, accent, disc())
            DesktopVisualizerStyle.SPECTRUM -> spectrum(bands, accent)
            DesktopVisualizerStyle.BARS -> bars(bands, accent, focus())
            DesktopVisualizerStyle.NONE -> Unit
        }
    }
}

/** The colour [degrees] round the hue wheel from [base], at its own saturation and brightness. */
internal fun Color.hueShifted(degrees: Float, value: Float? = null): Color {
    val hsv = FloatArray(3)
    rgbToHsvInto(red, green, blue, hsv)
    val hue = ((hsv[0] + degrees) % 360f + 360f) % 360f
    return Color.hsv(hue, hsv[1].coerceIn(0f, 1f), (value ?: hsv[2]).coerceIn(0f, 1f))
}

internal fun rgbToHsvInto(r: Float, g: Float, b: Float, out: FloatArray) {
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    val hue = when {
        delta == 0f -> 0f
        maxC == r -> 60f * (((g - b) / delta) % 6f)
        maxC == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    out[0] = if (hue < 0f) hue + 360f else hue
    out[1] = if (maxC == 0f) 0f else delta / maxC
    out[2] = maxC
}

// ---- Aurora: soft lights drifting in the artwork's colours, swelling with the music ----------

/** Points each aurora ribbon's curve passes through. */
private const val RIBBON_POINTS = 24

/** How much smaller than the window the aurora is drawn; see [DesktopVisualizer]. */
private const val AURORA_SCALE = 2f

private fun DrawScope.aurora(t: Float, energy: Energy, accent: Color) {
    val lights = listOf(
        accent.hueShifted(0f, 0.95f) to energy.bass,
        accent.hueShifted(38f, 0.9f) to energy.mid,
        accent.hueShifted(-46f, 0.85f) to energy.high,
        accent.hueShifted(180f, 0.75f) to energy.mid * 0.7f,
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
    // Ribbons of light: the curtains an aurora is named for. Each a band of triangles with its
    // colours at the corners, which the GPU fills as it is: stroked as a wide curved path, as they
    // were, Skia rebuilt each ribbon's outline on every frame, and the three cost more than the
    // whole of the rest of the player. A band also has soft edges, as light does.
    for (k in 0 until 3) {
        val base = size.height * (0.28f + k * 0.14f)
        val amplitude = size.height * (0.05f + energy.mid * 0.06f)
        val half = size.height * (0.03f + energy.bass * 0.03f) / 2f
        val colour = accent.hueShifted(k * 30f - 30f, 1f)
        val peak = (0.22f + energy.high * 0.3f).coerceAtMost(1f)
        ribbon(base, amplitude, half, t * (0.25f + k * 0.08f) + k, colour, peak)
    }
}

/**
 * One aurora ribbon: a sine across the width, [half] thick either side of it, fading out toward
 * both ends and along its edges. Four rows of points across it — clear, full, full, clear — so it
 * has a body and a soft edge; [RIBBON_POINTS] + 1 columns along it.
 */
private fun DrawScope.ribbon(base: Float, amplitude: Float, half: Float, phase: Float, colour: Color, peak: Float) {
    val columns = RIBBON_POINTS + 1
    val positions = ArrayList<Offset>(columns * 4)
    val colours = ArrayList<Color>(columns * 4)
    val rows = floatArrayOf(-1f, -0.4f, 0.4f, 1f)
    for (c in 0 until columns) {
        val x = size.width * c / RIBBON_POINTS
        val y = base + sin(x / size.width * 5f + phase) * amplitude
        // Clear at the ends, full in the middle, as the gradient it was stroked with.
        val along = 1f - abs(c * 2f / RIBBON_POINTS - 1f)
        for (r in rows.indices) {
            positions += Offset(x, y + rows[r] * half)
            val edge = if (r == 0 || r == rows.size - 1) 0f else 1f
            colours += colour.copy(alpha = peak * along * edge)
        }
    }
    val indices = ArrayList<Int>(RIBBON_POINTS * 3 * 6)
    for (c in 0 until RIBBON_POINTS) {
        for (r in 0 until rows.size - 1) {
            val a = c * rows.size + r
            val b = a + rows.size
            indices += a; indices += b; indices += a + 1
            indices += a + 1; indices += b; indices += b + 1
        }
    }
    drawContext.canvas.drawVertices(
        androidx.compose.ui.graphics.Vertices(
            androidx.compose.ui.graphics.VertexMode.Triangles,
            positions,
            positions,
            colours,
            indices,
        ),
        BlendMode.Dst,
        ribbonPaint,
    )
}

/** Vertex colours alone, screened over what is beneath. */
private val ribbonPaint = androidx.compose.ui.graphics.Paint().apply { blendMode = BlendMode.Screen }

// ---- Ring: a spectrum radiating from the disc's edge -------------------------------------

private fun DrawScope.ring(t: Float, bands: FloatArray, energy: Energy, accent: Color, disc: Pair<Offset, Float>) {
    val (centre, radius) = disc
    if (radius <= 0f) return
    val spokes = 160
    val inner = radius * (1.035f + energy.bass * 0.03f)
    val spin = t * 4f
    val highlight = lerp(accent.hueShifted(0f, 1f), Color.White, 0.35f)
    for (i in 0 until spokes) {
        // Mirrored round the circle so the lows sit at the right-hand side, facing the room.
        val around = i.toFloat() / spokes
        val mirrored = 1f - abs(around * 2f - 1f)
        val band = (mirrored * (bands.size - 1)).roundToInt().coerceIn(0, bands.size - 1)
        val value = bands[band]
        val angle = ((around * 360f + spin) * PI / 180f).toFloat()
        val length = radius * (0.02f + value * 0.34f)
        val dir = Offset(cos(angle), sin(angle))
        val start = centre + dir * inner
        val end = centre + dir * (inner + length)
        drawLine(highlight.copy(alpha = 0.10f + value * 0.25f), start, end, strokeWidth = 9f, cap = StrokeCap.Round)
        drawLine(lerp(highlight, Color.White, value).copy(alpha = 0.55f + value * 0.45f), start, end, strokeWidth = 2.6f, cap = StrokeCap.Round)
    }
    // A breathing halo just outside the rim.
    drawCircle(
        Brush.radialGradient(
            0.0f to Color.Transparent,
            0.90f to Color.Transparent,
            0.96f to highlight.copy(alpha = 0.25f + energy.bass * 0.45f),
            1f to Color.Transparent,
            center = centre,
            radius = inner * 1.06f,
        ),
        radius = inner * 1.06f,
        center = centre,
        blendMode = BlendMode.Screen,
    )
}

// ---- Waves: layered ribbons, each riding a part of the spectrum ---------------------------

private fun DrawScope.waves(t: Float, energy: Energy, accent: Color) {
    val layers = listOf(
        Triple(energy.bass, accent.hueShifted(0f, 1f), 0.9f),
        Triple(energy.mid, accent.hueShifted(40f, 1f), 1.6f),
        Triple(energy.high, accent.hueShifted(-40f, 1f), 2.7f),
        Triple(energy.overall, Color.White, 2.1f),
    )
    layers.forEachIndexed { k, (level, colour, frequency) ->
        val base = size.height * (0.66f + k * 0.035f)
        val amplitude = size.height * (0.025f + level * 0.16f)
        val line = Path()
        val fill = Path()
        var x = 0f
        fill.moveTo(0f, size.height)
        while (x <= size.width + 12f) {
            val u = x / size.width
            val envelope = sin(PI.toFloat() * u.coerceIn(0f, 1f))
            val y = base + envelope * amplitude * (
                sin(u * frequency * 6f + t * (0.9f + k * 0.35f) + k) * 0.7f +
                    sin(u * frequency * 13f - t * (1.3f + k * 0.2f)) * 0.3f
                )
            if (x == 0f) line.moveTo(x, y) else line.lineTo(x, y)
            fill.lineTo(x, y)
            x += 10f
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(
            fill,
            Brush.verticalGradient(
                0f to colour.copy(alpha = 0.16f + level * 0.12f),
                1f to Color.Transparent,
                startY = base - amplitude,
                endY = size.height,
            ),
            blendMode = BlendMode.Screen,
        )
        drawPath(line, colour.copy(alpha = 0.18f), style = Stroke(width = 10f, cap = StrokeCap.Round), blendMode = BlendMode.Screen)
        drawPath(line, colour.copy(alpha = 0.75f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
    }
}

// ---- Particles: sparks thrown off the disc, faster on the beat ----------------------------

private class ParticleField(count: Int) {
    private val x = FloatArray(count)
    private val y = FloatArray(count)
    private val vx = FloatArray(count)
    private val vy = FloatArray(count)
    private val life = FloatArray(count) { -1f }
    private val ttl = FloatArray(count) { 1f }
    private val dot = FloatArray(count)
    private val tint = FloatArray(count)
    private val random = Random(7)
    private var last = Float.NaN

    fun step(scope: DrawScope, t: Float, energy: Energy, accent: Color, disc: Pair<Offset, Float>) = with(scope) {
        val dt = if (last.isNaN()) 0f else (t - last).coerceIn(0f, 0.1f)
        last = t
        val (centre, radius) = disc
        val boost = 0.6f + energy.bass * 3.2f
        val warm = accent.hueShifted(0f, 1f)
        for (i in x.indices) {
            if (life[i] < 0f || life[i] > ttl[i] || x[i] < -40f || x[i] > this.size.width + 40f || y[i] < -40f || y[i] > this.size.height + 40f) {
                // Born on the disc's rim, thrown out into the room.
                val angle = ((random.nextFloat() * 2f - 1f) * 1.25f)
                val r = radius * (0.95f + random.nextFloat() * 0.1f)
                x[i] = centre.x + cos(angle) * r
                y[i] = centre.y + sin(angle) * r
                val speed = 40f + random.nextFloat() * 120f
                vx[i] = cos(angle) * speed
                vy[i] = sin(angle) * speed - 10f
                ttl[i] = 3f + random.nextFloat() * 5f
                // The first generation is born already part-way through, so the field opens
                // full rather than as one burst.
                life[i] = if (primed) 0f else random.nextFloat() * ttl[i]
                if (!primed) {
                    x[i] += vx[i] * life[i]
                    y[i] += vy[i] * life[i]
                }
                dot[i] = 1f + random.nextFloat() * 2.6f
                tint[i] = random.nextFloat()
            }
            life[i] += dt
            x[i] += vx[i] * dt * boost
            y[i] += vy[i] * dt * boost
            // A slow swirl, so the field drifts rather than flies in straight lines.
            vy[i] += sin(t * 0.7f + i) * 6f * dt
            val fade = (1f - life[i] / ttl[i]).coerceIn(0f, 1f) * min(1f, life[i] * 3f)
            val twinkle = 0.55f + 0.45f * sin(t * 6f + i * 1.3f)
            val colour = lerp(Color.White, warm, tint[i] * 0.8f)
            val s = dot[i] * (1f + energy.high * 1.4f)
            drawCircle(colour.copy(alpha = fade * 0.18f), radius = s * 4f, center = Offset(x[i], y[i]), blendMode = BlendMode.Screen)
            drawCircle(colour.copy(alpha = (fade * twinkle).coerceIn(0f, 1f)), radius = s, center = Offset(x[i], y[i]))
        }
        primed = true
    }

    private var primed = false
}

// ---- Spectrum: the whole range as a glowing landscape along the floor ---------------------

private fun DrawScope.spectrum(bands: FloatArray, accent: Color) {
    val n = bands.size
    val floor = size.height
    val top = size.height * 0.42f
    val points = List(n) { i ->
        val u = i.toFloat() / (n - 1)
        Offset(u * size.width, floor - (size.height * 0.03f) - bands[i] * (floor - top))
    }
    val ridge = Path()
    ridge.moveTo(points.first().x, points.first().y)
    for (i in 1 until n) {
        val mid = (points[i - 1] + points[i]) / 2f
        ridge.quadraticTo(points[i - 1].x, points[i - 1].y, mid.x, mid.y)
    }
    ridge.lineTo(points.last().x, points.last().y)
    val fill = Path().apply {
        addPath(ridge)
        lineTo(size.width, floor)
        lineTo(0f, floor)
        close()
    }
    drawPath(
        fill,
        Brush.verticalGradient(
            0f to accent.hueShifted(0f, 1f).copy(alpha = 0.55f),
            0.6f to accent.hueShifted(-30f, 0.6f).copy(alpha = 0.22f),
            1f to Color.Transparent,
            startY = top,
            endY = floor,
        ),
        blendMode = BlendMode.Screen,
    )
    drawPath(ridge, Color.White.copy(alpha = 0.22f), style = Stroke(width = 12f, cap = StrokeCap.Round), blendMode = BlendMode.Screen)
    drawPath(ridge, Color.White.copy(alpha = 0.9f), style = Stroke(width = 2.4f, cap = StrokeCap.Round))
}

// ---- Bars: the broadcast-ident level meter behind the words -------------------------------

private fun DrawScope.bars(bands: FloatArray, accent: Color, area: Rect) {
    if (area.width <= 0f) return
    val count = 72
    val gap = area.width / count
    val barWidth = (gap * 0.34f).coerceAtLeast(1.5f)
    val mid = area.center.y
    val glow = lerp(accent.hueShifted(0f, 1f), Color.White, 0.5f)
    for (i in 0 until count) {
        val fromCentre = abs(i - (count - 1) / 2f) / ((count - 1) / 2f)
        val band = (fromCentre * (bands.size - 1)).roundToInt().coerceIn(0, bands.size - 1)
        val half = area.height * (0.03f + bands[band] * 0.47f * (1f - fromCentre * 0.35f))
        val x = area.left + i * gap + gap / 2f
        drawLine(glow.copy(alpha = 0.18f), Offset(x, mid - half), Offset(x, mid + half), strokeWidth = barWidth * 3f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.55f), Offset(x, mid - half), Offset(x, mid + half), strokeWidth = barWidth, cap = StrokeCap.Round)
    }
}
