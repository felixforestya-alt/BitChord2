package com.music.bitchord.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.artworkAt

/**
 * The playing track's pictures, on the GPU before anything shows them.
 *
 * A picture is copied to the graphics card the first frame it is drawn: for the disc's cover, a
 * 1200-pixel image with its mipmaps, a frame of a few tens of milliseconds — the first frame of
 * opening the player on a new track. Drawn here once, a pixel across and all but transparent, as
 * soon as each is loaded, the same bitmaps the player and the home card draw are already on the
 * card when they are wanted. Skia keeps a texture with the bitmap it came from, so this is the
 * same texture, not a copy.
 */
@Composable
internal fun DesktopTextureWarmer(coverUrl: String?) {
    if (coverUrl.isNullOrBlank()) return
    // Exactly the bitmaps the player and the home card ask for, from the same caches.
    val disc = rememberArtworkBitmap(coverUrl.artworkAt(1200) ?: coverUrl)
    val words = rememberArtworkBitmap(coverUrl.artworkAt(300) ?: coverUrl)
    val ambient = rememberBlurredArtwork(coverUrl, edge = 64, sigma = 5f)
    val hero = rememberBlurredArtwork(coverUrl, edge = 64, sigma = 4f)
    Canvas(Modifier.size(1.dp)) {
        for (image in listOfNotNull(disc, words, ambient, hero)) {
            touch(image)
        }
    }
}

/** Draws [image] into a single pixel, as nearly transparent as a draw can be, both ways it is sampled. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.touch(image: ImageBitmap) {
    for (quality in arrayOf(FilterQuality.High, FilterQuality.Low)) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(1, 1),
            alpha = 1f / 255f,
            filterQuality = quality,
        )
    }
}
