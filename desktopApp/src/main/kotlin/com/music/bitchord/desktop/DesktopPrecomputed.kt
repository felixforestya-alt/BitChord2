package com.music.bitchord.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.music.bitchord.data.model.artworkAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * Pictures worked out once rather than on every frame.
 *
 * A render-effect blur on a full-window layer is re-run by the GPU each time the frame is drawn,
 * at the window's full size — on a 4K screen, tens of millions of pixels blurred per frame for an
 * image that never changes. Blurred once into a small bitmap and then simply stretched, the
 * result looks the same (a blur has no detail left to lose) and costs a texture lookup.
 */
internal object DesktopPrecomputed {

    private val blurred = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

    /** [url]'s artwork, shrunk to [edge] px and blurred by [sigma] px; held for the session. */
    suspend fun blurredArtwork(url: String, edge: Int, sigma: Float): ImageBitmap? {
        val key = "$url|$edge|$sigma"
        blurred[key]?.let { return it }
        val source = DesktopArtworkCache.load(url.artworkAt(240) ?: url) ?: return null
        val made = withContext(Dispatchers.Default) {
            runCatching {
                val image = Image.makeFromBitmap(source.asSkiaBitmap())
                val aspect = image.width.toFloat() / image.height.coerceAtLeast(1)
                val width = if (aspect >= 1f) edge else (edge * aspect).toInt().coerceAtLeast(1)
                val height = if (aspect >= 1f) (edge / aspect).toInt().coerceAtLeast(1) else edge
                val surface = Surface.makeRasterN32Premul(width, height)
                Paint().use { paint ->
                    paint.imageFilter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP, null, null)
                    surface.canvas.drawImageRect(image, Rect.makeWH(width.toFloat(), height.toFloat()), paint)
                }
                surface.makeImageSnapshot().toComposeImageBitmap().frozen().also { surface.close() }
            }.getOrNull()
        } ?: return null
        if (blurred.size > 64) blurred.clear()
        blurred[key] = made
        return made
    }
}

/** [DesktopPrecomputed.blurredArtwork], as state; null until ready. */
@Composable
internal fun rememberBlurredArtwork(url: String?, edge: Int = 96, sigma: Float = 6f): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, url, edge, sigma) {
        value = url?.let { DesktopPrecomputed.blurredArtwork(it, edge, sigma) }
    }
    return image
}
