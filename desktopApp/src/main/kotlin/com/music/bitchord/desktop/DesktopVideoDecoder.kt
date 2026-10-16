package com.music.bitchord.desktop

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_flush_buffers
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD
import org.bytedeco.ffmpeg.global.avformat.av_find_best_stream
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.av_seek_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
import org.bytedeco.ffmpeg.global.avutil.av_dict_set
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_image_fill_arrays
import org.bytedeco.ffmpeg.global.avutil.av_image_get_buffer_size
import org.bytedeco.ffmpeg.global.swscale.SWS_ACCURATE_RND
import org.bytedeco.ffmpeg.global.swscale.SWS_BICUBIC
import org.bytedeco.ffmpeg.global.swscale.SWS_FULL_CHR_H_INT
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.PointerPointer

/**
 * A music video's picture, decoded for the player to draw in step with the audio.
 *
 * The same FFmpeg the audio and the Canvas clips use, but for a full video rather than a sleeve
 * loop: frames come back with their presentation time so the caller can hold or drop them against
 * the audio clock, the decoder can be repositioned when the listener seeks, it decodes on every
 * core, and it scales with bicubic and full chroma rather than the clip decoder's bilinear.
 */
internal class DesktopVideoDecoder(private val maxEdge: Int = 1920) {

    private var format: AVFormatContext? = null
    private var codec: AVCodecContext? = null
    private var packet: AVPacket? = null
    private var frame: AVFrame? = null
    private var scaler: SwsContext? = null
    private var buffer: BytePointer? = null
    private var view: java.nio.ByteBuffer? = null
    private var planes: PointerPointer<BytePointer>? = null
    private var strides: IntPointer? = null
    private var scalerFormat = -1
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var streamIndex = -1
    private var timeBase = 0.0
    private var startPts = 0L

    var width: Int = 0
        private set

    var height: Int = 0
        private set

    /** Presentation time of the frame most recently returned, in milliseconds. */
    var framePtsMs: Long = 0L
        private set

    fun open(url: String, headers: Map<String, String> = emptyMap()): Result<Unit> = runCatching {
        DesktopAudioDecoder.ensureNetworkReady()
        val options = AVDictionary(null)
        if (headers.isNotEmpty()) {
            av_dict_set(options, "headers", headers.entries.joinToString("") { (k, v) -> "$k: $v\r\n" }, 0)
        }
        av_dict_set(options, "rw_timeout", "15000000", 0)
        // Seeking a progressive googlevideo URL is a fresh ranged request; keep the connection.
        av_dict_set(options, "reconnect", "1", 0)
        av_dict_set(options, "multiple_requests", "1", 0)

        val opened = AVFormatContext(null)
        check(avformat_open_input(opened, url, null, options) >= 0) { "could not open the video" }
        format = opened
        check(avformat_find_stream_info(opened, null as AVDictionary?) >= 0) { "no stream info" }
        streamIndex = av_find_best_stream(opened, AVMEDIA_TYPE_VIDEO, -1, -1, null as org.bytedeco.ffmpeg.avcodec.AVCodec?, 0)
        check(streamIndex >= 0) { "no video stream" }

        val stream = opened.streams(streamIndex)
        timeBase = stream.time_base().num().toDouble() / stream.time_base().den().coerceAtLeast(1)
        startPts = stream.start_time().takeIf { it != AV_NOPTS_VALUE } ?: 0L
        val parameters = stream.codecpar()
        val decoder = avcodec_find_decoder(parameters.codec_id()) ?: error("no decoder for this video")
        val context = avcodec_alloc_context3(decoder)
        check(avcodec_parameters_to_context(context, parameters) >= 0) { "bad codec parameters" }
        // A few threads, not every core: each frame-thread holds frames of its own in flight,
        // and 720p decodes in real time on two.
        context.thread_count(minOf(4, Runtime.getRuntime().availableProcessors()).coerceAtLeast(1))
        context.pkt_timebase(stream.time_base())
        check(avcodec_open2(context, decoder, null as AVDictionary?) >= 0) { "decoder refused to open" }
        codec = context

        sourceWidth = context.width()
        sourceHeight = context.height()
        check(sourceWidth > 0 && sourceHeight > 0) { "the video has no size" }
        val scale = minOf(1.0, maxEdge.toDouble() / maxOf(sourceWidth, sourceHeight))
        width = ((sourceWidth * scale).toInt() / 2) * 2
        height = ((sourceHeight * scale).toInt() / 2) * 2

        val size = av_image_get_buffer_size(AV_PIX_FMT_BGRA, width, height, 1)
        buffer = BytePointer(size.toLong())
        view = buffer?.asByteBuffer()
        planes = PointerPointer<BytePointer>(4)
        strides = IntPointer(4L)
        check(av_image_fill_arrays(planes, strides, buffer, AV_PIX_FMT_BGRA, width, height, 1) >= 0) {
            "could not lay out the frame buffer"
        }
        packet = av_packet_alloc()
        frame = av_frame_alloc()
    }.onFailure { close() }

    /** Repositions to the keyframe at or before [positionMs]. */
    fun seek(positionMs: Long): Boolean {
        val container = format ?: return false
        val context = codec ?: return false
        if (timeBase <= 0.0) return false
        val target = startPts + (positionMs / 1000.0 / timeBase).toLong()
        val ok = av_seek_frame(container, streamIndex, target, AVSEEK_FLAG_BACKWARD) >= 0
        avcodec_flush_buffers(context)
        return ok
    }

    /**
     * Decodes the next frame. With [convert] false the frame is only decoded — the cheap way to
     * skip forward to a seek target — and [into] is untouched.
     */
    fun nextFrame(into: ByteArray, convert: Boolean = true): Boolean {
        val container = format ?: return false
        val context = codec ?: return false
        val pkt = packet ?: return false
        val decoded = frame ?: return false
        while (true) {
            if (avcodec_receive_frame(context, decoded) == 0) {
                val pts = decoded.best_effort_timestamp().takeIf { it != AV_NOPTS_VALUE } ?: decoded.pts()
                if (pts != AV_NOPTS_VALUE) framePtsMs = ((pts - startPts) * timeBase * 1000.0).toLong()
                if (!convert) return true
                val converter = scalerFor(decoded.format()) ?: return false
                sws_scale(converter, decoded.data(), decoded.linesize(), 0, sourceHeight, planes, strides)
                view?.let { pixels ->
                    pixels.rewind()
                    pixels.get(into, 0, minOf(into.size, width * height * 4))
                }
                return true
            }
            if (av_read_frame(container, pkt) < 0) return false
            if (pkt.stream_index() == streamIndex) avcodec_send_packet(context, pkt)
            av_packet_unref(pkt)
        }
    }

    /** Converts the frame [nextFrame] last decoded with `convert = false`. */
    fun convertCurrent(into: ByteArray): Boolean {
        val decoded = frame ?: return false
        val converter = scalerFor(decoded.format()) ?: return false
        sws_scale(converter, decoded.data(), decoded.linesize(), 0, sourceHeight, planes, strides)
        view?.let { pixels ->
            pixels.rewind()
            pixels.get(into, 0, minOf(into.size, width * height * 4))
        }
        return true
    }

    private fun scalerFor(pixelFormat: Int): SwsContext? {
        scaler?.let { if (pixelFormat == scalerFormat) return it }
        if (pixelFormat < 0) return null
        scaler?.let { sws_freeContext(it) }
        val built = sws_getContext(
            sourceWidth, sourceHeight, pixelFormat,
            width, height, AV_PIX_FMT_BGRA,
            SWS_BICUBIC or SWS_ACCURATE_RND or SWS_FULL_CHR_H_INT, null, null, null as DoublePointer?,
        ) ?: return null
        scaler = built
        scalerFormat = pixelFormat
        return built
    }

    fun close() {
        frame?.let { av_frame_free(it) }
        packet?.let { av_packet_free(it) }
        scaler?.let { sws_freeContext(it) }
        codec?.let { avcodec_free_context(it) }
        format?.let { avformat_close_input(it) }
        buffer?.deallocate()
        strides?.deallocate()
        frame = null
        packet = null
        scaler = null
        scalerFormat = -1
        codec = null
        format = null
        buffer = null
        view = null
        planes = null
        strides = null
        streamIndex = -1
    }
}
