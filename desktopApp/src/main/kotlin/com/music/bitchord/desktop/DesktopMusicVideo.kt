package com.music.bitchord.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.playback.PlaybackPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where a music video is: still being fetched, on screen, or not to be had. */
internal enum class DesktopVideoState { IDLE, LOADING, PLAYING, UNAVAILABLE }

/** The frames of one music video, for as many surfaces as want to draw them. */
@Stable
internal class DesktopVideoFrames {
    /**
     * The frame on show, as Skia's own image: drawn as it is, never copied into a Compose bitmap.
     * That copy was a second full frame a time held until the garbage collector got round to it —
     * which, since a frame weighs almost nothing on the Java heap, was seldom: gigabytes piled up.
     */
    var frame by mutableStateOf<Image?>(null)
        internal set
    var state by mutableStateOf(DesktopVideoState.IDLE)
        internal set
}

/**
 * A music video's picture, muted, kept in step with the audio the engine is playing; null
 * [videoId] for none.
 *
 * The audio is the clock. Each decoded frame is held until the playhead reaches its timestamp and
 * dropped if the playhead is already past it; a seek in the audio — or a drift of more than a
 * second and a half — repositions the decoder. Nothing here makes a sound.
 */
@Composable
internal fun rememberDesktopMusicVideo(
    videoId: String?,
    position: PlaybackPosition,
    isPlaying: Boolean,
): DesktopVideoFrames {
    val frames = remember(videoId) { DesktopVideoFrames() }
    val playing by rememberUpdatedState(isPlaying)

    /** Where the audio is right now, carried forward from the last reading on the frame clock. */
    fun audioNowMs(): Long {
        val base = position.positionMs
        if (!playing || !position.advancing) return base
        return base + (System.nanoTime() - position.sampledAtNanos) / 1_000_000L
    }

    LaunchedEffect(videoId) {
        if (videoId == null) {
            frames.state = DesktopVideoState.IDLE
            return@LaunchedEffect
        }
        frames.state = DesktopVideoState.LOADING
        val stream = runCatching { StreamResolver.resolveVideoStream(videoId, maxHeight = videoMaxHeight()) }
            .onFailure { DesktopTrackLog.log("video: no picture for $videoId: ${it.message}") }
            .getOrNull()
        if (stream == null) {
            frames.state = DesktopVideoState.UNAVAILABLE
            return@LaunchedEffect
        }
        val decoder = DesktopVideoDecoder(maxEdge = VIDEO_OUTPUT_EDGE)
        val opened = withContext(Dispatchers.IO) { decoder.open(stream.url, stream.headers) }
        if (opened.isFailure) {
            DesktopTrackLog.log("video: ${opened.exceptionOrNull()?.message}")
            withContext(Dispatchers.IO) { decoder.close() }
            frames.state = DesktopVideoState.UNAVAILABLE
            return@LaunchedEffect
        }
        DesktopTrackLog.log("video: playing ${stream.width}x${stream.height}@${stream.fps} ${stream.codec}")
        val pixels = ByteArray(decoder.width * decoder.height * 4)
        val info = ImageInfo.makeN32(decoder.width, decoder.height, ColorAlphaType.OPAQUE)
        // Skia images live in native memory the collector cannot see, so they are closed by hand,
        // a few frames after they were replaced — by then nothing can still be drawing them.
        val retired = ArrayDeque<Image>()
        var lastPts = Long.MIN_VALUE
        var seekTo: Long? = audioNowMs()
        try {
            while (isActive) {
                val now = audioNowMs()
                // A jump in the audio the decoder has to follow.
                if (seekTo == null && lastPts != Long.MIN_VALUE && abs(now - lastPts) > RESYNC_MS) seekTo = now
                seekTo?.let { target ->
                    withContext(Dispatchers.IO) {
                        decoder.seek(target)
                        // Decode, without converting, up to the frame the audio is on.
                        while (decoder.nextFrame(pixels, convert = false) && decoder.framePtsMs < target - FRAME_SLACK_MS) Unit
                        decoder.convertCurrent(pixels)
                    }
                    seekTo = null
                    lastPts = decoder.framePtsMs
                    frames.frame = publish(withContext(Dispatchers.Default) { rasterOf(pixels, info, decoder.width) }, retired)
                    frames.state = DesktopVideoState.PLAYING
                }
                if (!playing) {
                    // Paused: the frame stays up; only a seek moves it.
                    delay(PAUSED_POLL_MS)
                    val held = audioNowMs()
                    if (abs(held - lastPts) > PAUSED_RESYNC_MS) seekTo = held
                    continue
                }
                val decoded = withContext(Dispatchers.IO) { decoder.nextFrame(pixels, convert = false) }
                if (!decoded) {
                    // The end of the picture; the audio may run on a little longer.
                    delay(PAUSED_POLL_MS)
                    if (audioNowMs() < lastPts - RESYNC_MS) seekTo = audioNowMs()
                    continue
                }
                val pts = decoder.framePtsMs
                lastPts = pts
                // Already late: dropped before the (costly) colour conversion.
                if (pts < audioNowMs() - FRAME_SLACK_MS) continue
                withContext(Dispatchers.IO) { decoder.convertCurrent(pixels) }
                // Held until the audio reaches it.
                var wait = pts - audioNowMs()
                while (wait > 1 && isActive && playing) {
                    delay(wait.coerceAtMost(MAX_WAIT_STEP_MS))
                    wait = pts - audioNowMs()
                    if (wait > RESYNC_MS) break
                }
                frames.frame = publish(withContext(Dispatchers.Default) { rasterOf(pixels, info, decoder.width) }, retired)
            }
        } finally {
            withContext(NonCancellable) {
                // The last frames go too: off the screen first, then — a couple of frames later,
                // when nothing can be drawing them — freed.
                frames.frame = null
                androidx.compose.runtime.withFrameNanos { }
                androidx.compose.runtime.withFrameNanos { }
                while (retired.isNotEmpty()) retired.removeFirst().close()
                withContext(Dispatchers.IO) { decoder.close() }
            }
        }
    }
    return frames
}

/** The video, filling (or, with [crop] false, fitting) its bounds, fading in on its first frame. */
@Composable
internal fun DesktopVideoSurface(frames: DesktopVideoFrames, modifier: Modifier = Modifier, crop: Boolean = true) {
    val shown by animateFloatAsState(if (frames.frame != null) 1f else 0f, tween(700), label = "videoFade")
    Canvas(modifier.graphicsLayer { alpha = shown }) {
        val image = frames.frame ?: return@Canvas
        drawSkiaFrame(image, crop)
    }
}

/** Draws [image] across this scope, cropped to fill or fitted inside. */
/** A video frame — a Skia image, drawn straight onto the canvas — like [drawVideoFrame]. */
internal fun DrawScope.drawSkiaFrame(image: Image, crop: Boolean = true) {
    val clipAspect = image.width.toFloat() / image.height
    val viewAspect = size.width / size.height
    val (drawWidth, drawHeight) = if (crop == clipAspect > viewAspect) {
        size.height * clipAspect to size.height
    } else {
        size.width to size.width / clipAspect
    }
    val left = (size.width - drawWidth) / 2f
    val top = (size.height - drawHeight) / 2f
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawImageRect(
            image,
            org.jetbrains.skia.Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            org.jetbrains.skia.Rect.makeXYWH(left, top, drawWidth, drawHeight),
            org.jetbrains.skia.SamplingMode.LINEAR,
            null,
            true,
        )
    }
}

internal fun DrawScope.drawVideoFrame(image: ImageBitmap, crop: Boolean = true) {
    val clipAspect = image.width.toFloat() / image.height
    val viewAspect = size.width / size.height
    val (drawWidth, drawHeight) = if (crop == clipAspect > viewAspect) {
        size.height * clipAspect to size.height
    } else {
        size.width to size.width / clipAspect
    }
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(((size.width - drawWidth) / 2f).roundToInt(), ((size.height - drawHeight) / 2f).roundToInt()),
        dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

/**
 * The decoded frame copied into an image of its own, off the UI thread: at 4K that is 33 MB a
 * frame, which done on the UI thread took a slice of every frame of the window while a video played.
 */
private fun rasterOf(pixels: ByteArray, info: ImageInfo, width: Int): Image = Image.makeRaster(info, pixels, width * 4)

private fun publish(image: Image, retired: ArrayDeque<Image>): Image {
    retired.addLast(image)
    // A few frames of grace: a frame the renderer recorded may still be in flight.
    while (retired.size > 3) retired.removeFirst().close()
    return image
}

/**
 * As tall as the screen the window is on, in its real pixels — 2160 on a 4K monitor — so the
 * picture filling the room is not upscaled into mush; at least 720, at most 2160.
 */
/**
 * What each frame is scaled to before it is drawn: 1440p. A 4K stream still gives the picture its
 * detail, but a 4K frame is 33 MB, copied and uploaded to the GPU twenty-five times a second —
 * which drove the app's memory to several gigabytes. Behind the room's scrim and the disc, 1440p
 * cannot be told from it, at well under half the weight.
 */
private const val VIDEO_OUTPUT_EDGE = 2560

private fun videoMaxHeight(): Int = runCatching {
    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode.height
}.getOrDefault(1080).coerceIn(720, 1440)

/** A frame this far behind the audio is not worth showing. */
private const val FRAME_SLACK_MS = 45L

/** Drift beyond this is a seek, not jitter. */
private const val RESYNC_MS = 1_500L
private const val PAUSED_RESYNC_MS = 400L
private const val PAUSED_POLL_MS = 60L
private const val MAX_WAIT_STEP_MS = 40L
