package com.music.bitchord.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.graphics.forPixelAccess
import com.music.bitchord.ui.graphics.toImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * The disc player's pieces, carried over from BitChord²'s desktop player: the CD with the cover
 * printed on it, the room lit in the cover's colour, the artist's name drifting enormous and faint,
 * and the visualisers that follow the real output through [AudioSpectrum]. Everything that moves
 * reads its clock in a draw lambda, so a frame redraws what moves and recomposes nothing.
 */

/** What moves behind the track. */
internal enum class DiscVisualizerStyle { AURORA, RING, WAVES, PARTICLES, SPECTRUM, NONE }

/** A cover's colour as the room is lit by it: a light, a shadow, and an ink. */
internal class DiscPalette(val vivid: Color, val deep: Color, val ink: Color) {
    companion object {
        val Default = DiscPalette(Color(0xFF6E7488), Color(0xFF0B0C10), Color(0xFF050608))
    }
}

private val PALETTES = java.util.concurrent.ConcurrentHashMap<String, DiscPalette>()

/** The cover at [px] across, loaded through the app's image loader; null until it is there. */
@Composable
internal fun rememberDiscArtwork(url: String?, px: Int, pixelAccess: Boolean = false): ImageBitmap? {
    val context = LocalPlatformContext.current
    val image by produceState<ImageBitmap?>(null, url, px) {
        if (url == null) return@produceState
        val request = ImageRequest.Builder(context)
            .data(url.artworkAt(px) ?: url)
            .size(px)
            .let { if (pixelAccess) it.forPixelAccess() else it }
            .build()
        value = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toImageBitmap()
    }
    return image
}

/**
 * The cover's strongest colour, worked out once a cover and remembered, so every surface showing
 * it has its colour from the first frame rather than fading from grey.
 */
@Composable
internal fun rememberDiscPalette(url: String?): DiscPalette {
    val context = LocalPlatformContext.current
    val palette by produceState(url?.let(PALETTES::get) ?: DiscPalette.Default, url) {
        if (url == null || PALETTES.containsKey(url)) return@produceState
        val request = ImageRequest.Builder(context).data(url.artworkAt(120) ?: url).size(120).forPixelAccess().build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toImageBitmap()
        val found = withContext(Dispatchers.Default) { vividPaletteOf(bitmap) } ?: DiscPalette.Default
        if (PALETTES.size > 256) PALETTES.clear()
        PALETTES[url] = found
        value = found
    }
    return palette
}

/**
 * A hue histogram weighted by saturation and brightness — so a small patch of strong colour beats
 * a large grey field — and the winning hue turned into a light, a shadow and an ink.
 */
private fun vividPaletteOf(bitmap: ImageBitmap?): DiscPalette? {
    if (bitmap == null || bitmap.width == 0 || bitmap.height == 0) return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.readPixels(pixels)
    val buckets = 36
    val weight = FloatArray(buckets)
    val satSum = FloatArray(buckets)
    val hsv = FloatArray(3)
    var greyWeight = 0f
    for (index in pixels.indices step 3) {
        val pixel = pixels[index]
        rgbToHsvInto((pixel shr 16 and 0xFF) / 255f, (pixel shr 8 and 0xFF) / 255f, (pixel and 0xFF) / 255f, hsv)
        val w = hsv[1] * hsv[1] * hsv[2]
        if (hsv[1] < 0.15f) {
            greyWeight += hsv[2] * 0.02f
            continue
        }
        val bucket = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
        weight[bucket] += w
        satSum[bucket] += hsv[1] * w
    }
    var best = 0
    for (i in 1 until buckets) if (weight[i] + weight[(i + 1) % buckets] * 0.5f > weight[best] + weight[(best + 1) % buckets] * 0.5f) best = i
    // A monochrome sleeve: lit in a cool neutral rather than an invented colour.
    if (weight[best] <= greyWeight * 0.5f || weight[best] < 0.5f) return DiscPalette.Default
    val hue = (best + 0.5f) * (360f / buckets)
    val saturation = (satSum[best] / weight[best]).coerceIn(0.55f, 0.95f)
    return DiscPalette(
        vivid = Color.hsv(hue, saturation, 0.78f),
        deep = Color.hsv(hue, (saturation * 0.9f).coerceAtMost(1f), 0.10f),
        ink = Color.hsv(hue, saturation, 0.06f),
    )
}

/**
 * A CD: the cover printed edge to edge, a clear hub with its stacking ring, a centre hole,
 * data-track grooves, a holographic sheen that turns with it, and a reflection that does not —
 * the light is in the room, not on the disc.
 */
@Composable
internal fun SpinningDisc(
    artUrl: String?,
    angle: () -> Float,
    level: () -> Float,
    glow: Color,
    modifier: Modifier,
) {
    val art = rememberDiscArtwork(artUrl, 1080)
    Box(modifier) {
        // The shadow it casts, and the glow of the colour it is lit by.
        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = 1.06f; scaleY = 1.06f }) {
            val beat = level()
            drawCircle(
                Brush.radialGradient(0.70f to Color.Black.copy(alpha = 0.55f), 1f to Color.Transparent),
                radius = size.minDimension / 2,
                center = center + Offset(size.width * 0.02f, size.height * 0.03f),
            )
            drawCircle(
                Brush.radialGradient(
                    0.80f to glow.copy(alpha = 0.0f),
                    0.93f to glow.copy(alpha = 0.18f + beat * 0.35f),
                    1f to Color.Transparent,
                ),
                radius = size.minDimension / 2,
            )
        }
        // The disc itself, turning. Its picture is recorded once into its own layer; the turn is
        // the layer's rotation, so a frame of spin is the GPU drawing one texture at an angle.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = angle()
                    // Offscreen, so the centre hole can be cut clean through to what is behind.
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        ) {
            val radius = size.minDimension / 2f
            val disc = Path().apply { addOval(Rect(center, radius)) }
            clipPath(disc) {
                val image = art
                if (image != null) drawSquareCrop(image) else drawRect(Brush.linearGradient(listOf(Color(0xFF2B2B35), Color(0xFF55556A))))
                // The data side's rainbow, turning with the disc.
                drawCircle(
                    Brush.sweepGradient(
                        listOf(
                            Color(0xFF7CF7FF), Color(0xFFFF7CE5), Color(0xFFFFF27C), Color(0xFF7CFF9B),
                            Color(0xFF7C9BFF), Color(0xFFFF7C7C), Color(0xFF7CF7FF),
                        ),
                    ),
                    radius = radius,
                    alpha = 0.16f,
                    blendMode = BlendMode.Screen,
                )
                // Grooves.
                val step = 1.1f * density
                var r = radius * 0.40f
                while (r < radius * 0.985f) {
                    drawCircle(Color.Black, radius = r, alpha = 0.035f, style = Stroke(0.3f * density))
                    r += step
                }
            }
            // The clear hub, to a real disc's proportions (120 mm across, a 15 mm hole, the clear
            // zone out to 46 mm): see-through polycarbonate over the print, the stacking ring
            // moulded into it, a smoky clamping area, then the hole.
            val hair = 0.45f * density
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.30f),
                    0.6f to Color(0xFFB8C4CC).copy(alpha = 0.22f),
                    1f to Color.White.copy(alpha = 0.10f),
                    radius = radius * 0.38f,
                    center = center,
                ),
                radius = radius * 0.38f,
            )
            drawCircle(Color.White.copy(alpha = 0.60f), radius = radius * 0.38f, style = Stroke(hair * 1.2f))
            drawCircle(Color.Black.copy(alpha = 0.10f), radius = radius * 0.30f, style = Stroke(radius * 0.035f))
            drawCircle(Color.White.copy(alpha = 0.35f), radius = radius * 0.285f, style = Stroke(hair))
            drawCircle(Color(0xFF20242A).copy(alpha = 0.45f), radius = radius * 0.21f)
            drawCircle(Color.White.copy(alpha = 0.22f), radius = radius * 0.21f, style = Stroke(hair))
            drawCircle(Color.Black, radius = radius * 0.125f, blendMode = BlendMode.Clear)
            drawCircle(Color.White.copy(alpha = 0.45f), radius = radius * 0.125f, style = Stroke(hair * 1.4f))
            // The rim.
            drawCircle(Color.White.copy(alpha = 0.65f), radius = radius - hair, style = Stroke(hair * 1.5f))
        }
        // The reflection, fixed in the room while the disc turns under it.
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val disc = Path().apply { addOval(Rect(center, radius)) }
            val hole = Path().apply { addOval(Rect(center, radius * 0.125f)) }
            clipPath(disc) {
                clipPath(hole, clipOp = ClipOp.Difference) {
                    drawCircle(
                        Brush.sweepGradient(
                            0.00f to Color.White.copy(alpha = 0.0f),
                            0.08f to Color.White.copy(alpha = 0.34f),
                            0.16f to Color.White.copy(alpha = 0.0f),
                            0.50f to Color.White.copy(alpha = 0.0f),
                            0.58f to Color.White.copy(alpha = 0.22f),
                            0.66f to Color.White.copy(alpha = 0.0f),
                            1.00f to Color.White.copy(alpha = 0.0f),
                        ),
                        radius = radius,
                        blendMode = BlendMode.Screen,
                    )
                    drawCircle(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.18f),
                            0.5f to Color.Transparent,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height),
                        ),
                        radius = radius,
                    )
                }
            }
        }
    }
}

/** [image] centre-cropped to a square and drawn across this scope, with high-quality filtering. */
private fun DrawScope.drawSquareCrop(image: ImageBitmap) {
    val side = min(image.width, image.height)
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - side) / 2, (image.height - side) / 2),
        srcSize = IntSize(side, side),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

/**
 * The cover, huge and soft, for the light to have texture: a few dozen pixels stretched across the
 * screen, which blurs it for free — no blur pass for the GPU to redo.
 */
@Composable
internal fun DiscAmbientArtwork(url: String?, modifier: Modifier) {
    val art = rememberDiscArtwork(url, 40)
    val shown by animateFloatAsState(if (art != null) 1f else 0f, tween(900), label = "discAmbient")
    Canvas(
        modifier.graphicsLayer {
            alpha *= shown
            scaleX = 1.25f
            scaleY = 1.25f
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
    ) {
        val image = art ?: return@Canvas
        // Cropped to the screen's shape, from the middle.
        val aspect = size.width / size.height
        val srcW: Int
        val srcH: Int
        if (image.width.toFloat() / image.height > aspect) {
            srcH = image.height
            srcW = (image.height * aspect).roundToInt().coerceAtLeast(1)
        } else {
            srcW = image.width
            srcH = (image.width / aspect).roundToInt().coerceAtLeast(1)
        }
        drawImage(
            image,
            srcOffset = IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2),
            srcSize = IntSize(srcW, srcH),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}

/** The artist's name, enormous and faint, drifting: one row one way, its mirror the other. */
@Composable
internal fun DiscGhostMarquee(text: String, color: Color, clock: () -> Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val family = MaterialTheme.typography.displayLarge.fontFamily
    BoxWithConstraints(modifier) {
        val fontPx = with(LocalDensity.current) { (maxHeight * 0.20f).toSp() }
        val layout = remember(text, fontPx, family) {
            measurer.measure(
                "$text  ",
                TextStyle(fontFamily = family, fontWeight = FontWeight.Black, fontSize = fontPx, letterSpacing = (-0.04).em),
            )
        }
        // Letters this tall drawn from outlines on every frame cost the GPU; drawn once into a
        // half-size picture instead, and the picture slid along. Faint and huge, the half
        // resolution does not show.
        val strip = remember(layout, color) { marqueeStrip(layout, color) }
        Canvas(Modifier.fillMaxSize()) {
            val runWidth = layout.size.width.toFloat().coerceAtLeast(1f)
            val t = clock()
            val speedScale = density / 2f
            fun row(y: Float, speed: Float) {
                var x = -((t * speed * speedScale) % runWidth + runWidth) % runWidth
                while (x < size.width) {
                    drawImage(
                        strip,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(strip.width, strip.height),
                        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                        dstSize = IntSize(layout.size.width, layout.size.height),
                        filterQuality = FilterQuality.Low,
                    )
                    x += runWidth
                }
            }
            val rowHeight = layout.size.height.toFloat()
            row(size.height * 0.36f - rowHeight * 0.5f, 26f)
            // Upside down, the other way — the mirror image under the floor.
            withTransform({ rotate(180f, Offset(size.width / 2f, size.height * 0.80f)) }) {
                row(size.height * 0.80f - rowHeight * 0.55f, 18f)
            }
        }
    }
}

/** Two wireframe globes turning slowly, out of the way at the edges. */
@Composable
internal fun DiscWireSpheres(clock: () -> Float, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        val t = clock()
        fun globe(c: Offset, r: Float, phase: Float) {
            val stroke = Stroke(width = 0.5f * density)
            for (i in 0 until 9) {
                val lat = (i - 4) / 4.5f
                val ry = r * kotlin.math.sqrt((1f - lat * lat).coerceAtLeast(0f))
                drawOval(tint.copy(alpha = 0.10f), topLeft = Offset(c.x - ry, c.y + lat * r - ry * 0.18f), size = Size(ry * 2f, ry * 0.36f), style = stroke)
            }
            for (i in 0 until 6) {
                val turn = (phase + t * 0.35f + i * (PI.toFloat() / 6f))
                val rx = r * abs(cos(turn))
                drawOval(tint.copy(alpha = 0.10f), topLeft = Offset(c.x - rx, c.y - r), size = Size(rx * 2f, r * 2f), style = stroke)
            }
        }
        globe(Offset(size.width * 0.88f, size.height * 0.58f), size.minDimension * 0.16f, 0f)
        globe(Offset(size.width * 0.12f, size.height * 0.50f), size.minDimension * 0.06f, 1.3f)
    }
}

/** A tape's worth of scanlines and a vignette — the faintest VHS cast. */
@Composable
internal fun DiscScanlines(modifier: Modifier) {
    // One small tile repeated down the screen, instead of hundreds of lines drawn one by one.
    val density = LocalDensity.current.density
    val lines = remember(density) { scanlineBrush((1.5f * density).roundToInt().coerceAtLeast(3)) }
    Canvas(modifier) {
        drawRect(lines)
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.55f),
                center = center,
                radius = size.maxDimension * 0.72f,
            ),
        )
    }
}

/** What moves behind the track, following the real output through [AudioSpectrum]. */
@Composable
internal fun DiscVisualizer(
    style: DiscVisualizerStyle,
    clock: () -> Float,
    accent: Color,
    /** Where the disc is, centre and radius, for the styles that radiate from it. */
    disc: () -> Pair<Offset, Float>,
    modifier: Modifier,
) {
    if (style == DiscVisualizerStyle.NONE) return
    val particles = remember { ParticleField(140) }
    // Drawn at half the screen's resolution and stretched: every style here is soft light, and
    // a quarter of the pixels is what lets a phone's GPU keep it up at the display's rate.
    BoxWithConstraints(modifier) {
    Canvas(
        Modifier
            .size(maxWidth / VIS_SCALE, maxHeight / VIS_SCALE)
            .graphicsLayer {
                scaleX = VIS_SCALE
                scaleY = VIS_SCALE
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        val t = clock()
        val bands = AudioSpectrum.bandsAt(System.nanoTime())
        val energy = energyOf(bands)
        when (style) {
            DiscVisualizerStyle.AURORA -> aurora(t, energy, accent)
            DiscVisualizerStyle.RING -> ring(t, bands, energy, accent, disc().let { (c, r) -> c / VIS_SCALE to r / VIS_SCALE })
            DiscVisualizerStyle.WAVES -> waves(t, energy, accent)
            DiscVisualizerStyle.PARTICLES -> particles.step(this, t, energy, accent, disc().let { (c, r) -> c / VIS_SCALE to r / VIS_SCALE })
            DiscVisualizerStyle.SPECTRUM -> spectrum(bands, accent)
            DiscVisualizerStyle.NONE -> Unit
        }
    }
    }
}

/** How much smaller than the screen the visualiser is drawn — see [DiscVisualizer]. */
private const val VIS_SCALE = 2f

/** The marquee's text, drawn once at half size into a bitmap. */
private fun marqueeStrip(layout: TextLayoutResult, color: Color): ImageBitmap {
    val width = (layout.size.width / 2).coerceAtLeast(1)
    val height = (layout.size.height / 2).coerceAtLeast(1)
    val bitmap = ImageBitmap(width, height)
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
        withTransform({ scale(0.5f, 0.5f, Offset.Zero) }) { drawText(layout, color = color) }
    }
    return bitmap
}

/** A transparent tile with one faint dark line in [height], repeated across whatever it fills. */
private fun scanlineBrush(height: Int): ShaderBrush {
    val tile = ImageBitmap(1, height)
    val canvas = androidx.compose.ui.graphics.Canvas(tile)
    canvas.drawRect(0f, 0f, 1f, (height / 3).coerceAtLeast(1).toFloat(), Paint().apply { this.color = Color.Black.copy(alpha = 0.06f) })
    return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

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

/** The colour [degrees] round the hue wheel from [base], at its own saturation and brightness. */
private fun Color.discHueShifted(degrees: Float, value: Float? = null): Color {
    val hsv = FloatArray(3)
    rgbToHsvInto(red, green, blue, hsv)
    val hue = ((hsv[0] + degrees) % 360f + 360f) % 360f
    return Color.hsv(hue, hsv[1].coerceIn(0f, 1f), (value ?: hsv[2]).coerceIn(0f, 1f))
}

private fun rgbToHsvInto(r: Float, g: Float, b: Float, out: FloatArray) {
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

private fun DrawScope.aurora(t: Float, energy: Energy, accent: Color) {
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
    // Ribbons of light: the curtains an aurora is named for.
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

// ---- Ring: a spectrum radiating from the disc's edge -------------------------------------

private fun DrawScope.ring(t: Float, bands: FloatArray, energy: Energy, accent: Color, disc: Pair<Offset, Float>) {
    val (centre, radius) = disc
    if (radius <= 0f) return
    val spokes = 160
    val inner = radius * (1.035f + energy.bass * 0.03f)
    val spin = t * 4f
    val highlight = lerp(accent.discHueShifted(0f, 1f), Color.White, 0.35f)
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
        Triple(energy.bass, accent.discHueShifted(0f, 1f), 0.9f),
        Triple(energy.mid, accent.discHueShifted(40f, 1f), 1.6f),
        Triple(energy.high, accent.discHueShifted(-40f, 1f), 2.7f),
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
        val warm = accent.discHueShifted(0f, 1f)
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
            0f to accent.discHueShifted(0f, 1f).copy(alpha = 0.55f),
            0.6f to accent.discHueShifted(-30f, 0.6f).copy(alpha = 0.22f),
            1f to Color.Transparent,
            startY = top,
            endY = floor,
        ),
        blendMode = BlendMode.Screen,
    )
    drawPath(ridge, Color.White.copy(alpha = 0.22f), style = Stroke(width = 12f, cap = StrokeCap.Round), blendMode = BlendMode.Screen)
    drawPath(ridge, Color.White.copy(alpha = 0.9f), style = Stroke(width = 2.4f, cap = StrokeCap.Round))
}
