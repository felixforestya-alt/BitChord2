package model

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File

/** Renders [content] at the given frame times; returns PNG bytes of each frame and the time each render() took. */
class FrameRun(val pngs: List<ByteArray>, val renderNanos: LongArray, val images: List<Image>)

fun runFrames(width: Int, height: Int, times: LongArray, keep: Set<Int> = emptySet(), before: (Int, ImageComposeScene) -> Unit = { _, _ -> }, content: @Composable () -> Unit): FrameRun {
    val scene = ImageComposeScene(width, height, content = content)
    val pngs = ArrayList<ByteArray>()
    val images = ArrayList<Image>()
    val took = LongArray(times.size)
    try {
        for ((index, at) in times.withIndex()) {
            before(index, scene)
            val t0 = System.nanoTime()
            val image = scene.render(at)
            took[index] = System.nanoTime() - t0
            if (index in keep) {
                pngs += image.encodeToData(EncodedImageFormat.PNG)!!.bytes
                images += image
            }
        }
    } finally {
        scene.close()
    }
    return FrameRun(pngs, took, images)
}

/** A contact sheet: two rows, before above, after below. */
fun sheet(file: File, top: List<Image>, bottom: List<Image>) {
    val w = top.first().width
    val h = top.first().height
    val surface = Surface.makeRasterN32Premul(w * top.size, h * 2)
    for ((i, img) in top.withIndex()) surface.canvas.drawImageRect(img, Rect.makeXYWH((i * w).toFloat(), 0f, w.toFloat(), h.toFloat()))
    for ((i, img) in bottom.withIndex()) surface.canvas.drawImageRect(img, Rect.makeXYWH((i * w).toFloat(), h.toFloat(), w.toFloat(), h.toFloat()))
    file.parentFile.mkdirs()
    file.writeBytes(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes)
}

fun median(values: LongArray): Long = values.sorted()[values.size / 2]
