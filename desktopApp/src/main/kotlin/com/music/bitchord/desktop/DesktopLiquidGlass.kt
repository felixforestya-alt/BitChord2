package com.music.bitchord.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * Liquid glass for the desktop, after the Android build's (Kyant's backdrop): what is behind a
 * floating pane stays almost clear — a touch of blur, a touch of magnification — and is *bent* at
 * the rim, where a thick lens would bend it, the three colour channels by slightly different
 * amounts; then the rim catches the light.
 *
 * Frosted panes blur hard and tint dark. This does neither: the point is that the page is still
 * there under the glass, only refracted.
 *
 * The Android version needs `RuntimeShader` (API 33); here the same lens is an SkSL runtime image
 * filter, which every Skia backend Compose Desktop runs on can execute. A [glassSource] records
 * the page into a [GraphicsLayer]; each pane re-draws it, offset to its own position, into a layer
 * whose render effect is blur → lens, and clips the result to its shape.
 */
@Stable
internal class DesktopGlassBackdrop(val layer: GraphicsLayer) {
    /** Where the recorded content starts, in window coordinates. */
    var origin by mutableStateOf(Offset.Zero)

    /** Bumped every time the source draws, so a still pane over moving content redraws too. */
    internal var revision by mutableIntStateOf(0)

    /** Whether anything has been recorded yet. */
    internal var recorded = false

    /**
     * True while the source is recording. A pane drawn *inside* its own source would otherwise
     * draw the layer being recorded into that same layer, without end — the stack overflow that
     * took the app down when the queue opened on glass inside the player.
     */
    internal var recording = false
}

/** The backdrop the panes in this part of the tree bend; null where there is none. */
internal val LocalDesktopGlassBackdrop = staticCompositionLocalOf<DesktopGlassBackdrop?> { null }

@Composable
internal fun rememberDesktopGlassBackdrop(): DesktopGlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { DesktopGlassBackdrop(layer) }
}

/** Records what this draws so the glass above it can refract it. */
internal fun Modifier.glassSource(backdrop: DesktopGlassBackdrop): Modifier =
    this
        .onGloballyPositioned { backdrop.origin = it.positionInWindow() }
        .drawWithContent {
            backdrop.recording = true
            try {
                backdrop.layer.record { this@drawWithContent.drawContent() }
            } finally {
                backdrop.recording = false
            }
            backdrop.recorded = true
            drawLayer(backdrop.layer)
            // A write in the draw phase: whatever reads it in its own draw redraws next frame.
            // Read without being observed: `revision++` reads before it writes, and a read here
            // made this very draw depend on the value it then changed — so it marked itself stale
            // and redrew on every frame of the display, forever, the whole window with it.
            androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { backdrop.revision++ }
        }

/**
 * A liquid glass pane in the shape of a rounded rectangle of [cornerRadius] (anything past half
 * the height makes a pill). Falls back to [fallback] — the app's frosted pane — when the backdrop
 * or the shader is unavailable, or the listener has turned the effect off.
 *
 * @param tint laid over the refracted page; keep it faint, it is what turns glass into frost.
 * @param blurRadius how soft the page reads through the glass. A few dp: enough that text behind
 *   does not fight text on top, not so much that it stops being the page.
 * @param edge the width of the bending rim, as a share of the pane's smaller side.
 * @param bend how far the rim bends the page inward, as a share of the pane's smaller side.
 * @param zoom how much the whole pane magnifies what is under it.
 */
@Composable
internal fun Modifier.desktopLiquidGlass(
    cornerRadius: Dp,
    tint: Color = Color.Black.copy(alpha = 0.06f),
    blurRadius: Dp = 3.dp,
    edge: Float = 0.42f,
    bend: Float = 0.32f,
    zoom: Float = 1.04f,
    dispersion: Float = 0.30f,
    rimLight: Float = 0.75f,
    /**
     * Read while drawing, for a pane whose shape moves every frame: the corner, and how much
     * harder than [bend] / [zoom] / [dispersion] it lenses (0 none, 1 most). Read here rather
     * than passed as values, a morphing pane redraws instead of recomposing on each frame.
     */
    cornerOf: (() -> Dp)? = null,
    lensOf: (() -> Float)? = null,
    fallback: @Composable Modifier.() -> Modifier,
): Modifier {
    val enabled by DesktopStyleSettings.liquidGlass.collectAsState()
    val reduceBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    val backdrop = LocalDesktopGlassBackdrop.current
    if (!enabled || reduceBlur || backdrop == null || !DesktopLensShader.available || !DesktopFrameProfiler.on("glass")) return fallback()

    val pane = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    // Rebuilt only when the size or the knobs change, not every frame.
    val effects = remember { GlassEffectCache() }

    return this
        .onGloballyPositioned { position = it.positionInWindow() }
        .drawWithContent {
            // Read so the pane redraws when the page under it does.
            @Suppress("UNUSED_VARIABLE")
            val revision = backdrop.revision
            val radius = (cornerOf?.invoke() ?: cornerRadius).toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
            val lens = lensOf?.invoke() ?: 0f
            val outline = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
            }
            val minSide = minOf(size.width, size.height)
            // The bending rim is never wider than the corner is round. Inside that, every point is
            // on the corner's arc or along a straight side, where the surface normal is smooth;
            // past it lie the rectangle's inner diagonals, where the normal flips from one side
            // to the next — a band reaching that far drew a sharp X from corner to corner.
            val band = (minSide * edge).coerceAtMost(radius.coerceAtLeast(1f))
            if (backdrop.recorded && !backdrop.recording && size.width >= 1f && size.height >= 1f) {
                pane.renderEffect = effects.get(
                    size = size,
                    radius = radius,
                    blur = blurRadius.toPx(),
                    band = band,
                    bend = (minSide * (bend + lens * 0.38f)).coerceAtMost(band * 0.85f),
                    zoom = zoom + lens * 0.07f,
                    dispersion = dispersion + lens * 0.35f,
                )
                val shift = position - backdrop.origin
                pane.record(IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                    // An opaque ground first: where the page is see-through (a gap between cards,
                    // a window material), the sharp page beneath must not show through the blur.
                    drawRect(GLASS_GROUND)
                    translate(-shift.x, -shift.y) { drawLayer(backdrop.layer) }
                }
                clipPath(outline) { drawLayer(pane) }
            }
            drawPath(outline, tint.copy(alpha = (tint.alpha - lens * 0.16f).coerceAtLeast(0f)))
            // The inner glow along the top: the light caught inside the thickness of the glass.
            clipPath(outline) {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = rimLight * 0.22f),
                        0.18f to Color.White.copy(alpha = rimLight * 0.05f),
                        0.55f to Color.Transparent,
                        0.85f to Color.Black.copy(alpha = 0.04f),
                        1f to Color.Black.copy(alpha = 0.12f),
                    ),
                )
            }
            // The rim: a bright specular line where the light hits it — top-left, and a lesser
            // one bottom-right where it leaves — fading out along the sides.
            drawPath(
                outline,
                brush = Brush.linearGradient(
                    0f to Color.White.copy(alpha = rimLight),
                    0.30f to Color.White.copy(alpha = rimLight * 0.20f),
                    0.55f to Color.White.copy(alpha = rimLight * 0.04f),
                    0.80f to Color.White.copy(alpha = rimLight * 0.22f),
                    1f to Color.White.copy(alpha = rimLight * 0.55f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawContent()
        }
}

private val GLASS_GROUND = Color(0xFF0C0C10)

/** One pane's render effect, rebuilt only when what it depends on changes. */
private class GlassEffectCache {
    private var key: List<Float> = emptyList()
    private var effect: RenderEffect? = null

    fun get(size: Size, radius: Float, blur: Float, band: Float, bend: Float, zoom: Float, dispersion: Float): RenderEffect? {
        val wanted = listOf(size.width, size.height, radius, blur, band, bend, zoom, dispersion)
        if (wanted == key && effect != null) return effect
        key = wanted
        effect = DesktopLensShader.effect(size, radius, blur, band, bend, zoom, dispersion)
        return effect
    }
}

/** The lens, compiled once for the whole app. */
internal object DesktopLensShader {

    private val runtime: RuntimeEffect? by lazy {
        runCatching { RuntimeEffect.makeForShader(SKSL) }
            .onFailure { DesktopTrackLog.log("liquid glass: shader did not compile: ${it.message}") }
            .getOrNull()
    }

    val available: Boolean get() = runtime != null

    fun effect(
        size: Size,
        radius: Float,
        blur: Float,
        band: Float,
        bend: Float,
        zoom: Float,
        dispersion: Float,
    ): RenderEffect? {
        val compiled = runtime ?: return null
        return runCatching {
            val sigma = (blur / 2f).coerceAtLeast(0.01f)
            val blurred = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP, null, null)
            // A whisper of extra colour, as light through real glass picks up.
            val vivid = ImageFilter.makeColorFilter(ColorFilter.makeMatrix(saturation(1.12f)), blurred, null)
            val builder = RuntimeShaderBuilder(compiled).apply {
                uniform("size", size.width, size.height)
                uniform("radius", radius)
                uniform("band", band.coerceAtLeast(1f))
                uniform("bend", bend)
                uniform("zoom", zoom.coerceAtLeast(1f))
                uniform("dispersion", dispersion)
            }
            ImageFilter.makeRuntimeShader(builder, "content", vivid).asComposeRenderEffect()
        }.onFailure { DesktopTrackLog.log("liquid glass: effect failed: ${it.message}") }.getOrNull()
    }

    private fun saturation(amount: Float): ColorMatrix {
        val r = 0.2126f * (1 - amount)
        val g = 0.7152f * (1 - amount)
        val b = 0.0722f * (1 - amount)
        return ColorMatrix(
            r + amount, g, b, 0f, 0f,
            r, g + amount, b, 0f, 0f,
            r, g, b + amount, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /**
     * Signed distance to the rounded rectangle. The whole pane magnifies by [zoom] about its
     * centre; inside a band of [band] from the rim the sample point is pushed further inwards
     * along the surface normal, by up to [bend], on a circular profile — strongest right at the
     * edge, the curved lip of a slab of glass. Red bends a little further than green and blue a
     * little less: the dispersion, which only shows where the bending does.
     */
    private const val SKSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float band;
uniform float bend;
uniform float zoom;
uniform float dispersion;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r, r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

float2 normalAt(float2 p, float2 b, float r) {
    float e = 0.75;
    float dx = sdRoundRect(p + float2(e, 0.0), b, r) - sdRoundRect(p - float2(e, 0.0), b, r);
    float dy = sdRoundRect(p + float2(0.0, e), b, r) - sdRoundRect(p - float2(0.0, e), b, r);
    float2 g = float2(dx, dy);
    float len = length(g);
    return len > 0.0001 ? g / len : float2(0.0, 0.0);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float d = sdRoundRect(p, halfSize, radius);
    if (d > 0.0) {
        return content.eval(coord);
    }
    float rim = 1.0 - clamp(-d / band, 0.0, 1.0);
    float profile = 1.0 - sqrt(max(0.0, 1.0 - rim * rim));
    float2 n = normalAt(p, halfSize, radius);
    float2 lensed = halfSize + p / zoom;
    float2 offset = -n * profile * bend;
    float2 at = lensed + offset;
    half4 base = content.eval(at);
    if (dispersion <= 0.0) {
        return base;
    }
    float spread = dispersion * profile;
    half r = content.eval(lensed + offset * (1.0 + spread)).r;
    half b = content.eval(lensed + offset * (1.0 - spread)).b;
    return half4(r, base.g, b, base.a);
}
"""
}
