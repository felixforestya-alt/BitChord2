package com.music.bitchord.desktop

import com.music.bitchord.data.Http
import com.music.bitchord.data.innertube.StreamResolver
import java.util.concurrent.TimeUnit
import okhttp3.Request

/** Reads a remote file in bounded windows rather than as one open-ended request. */
internal class DesktopRangeStream(
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
    private val windowBytes: Int = DEFAULT_WINDOW,
) {

    /** Total length in bytes, or -1 until the first response has stated one. */
    var length: Long = -1L
        private set

    /**
     * The stream stopped because a window could not be had, not because the file ended. Reads stay
     * ended from then on: FFmpeg asks again after a failed read, and each ask used to run the whole
     * round of retries, so one dead connection held the player silent for over two minutes.
     */
    @Volatile
    var brokeOff = false
        private set

    private var position = 0L
    private var window = ByteArray(0)

    /** Where [window] starts in the file; -1 when nothing is held. */
    private var windowStart = -1L

    /** Where the next read will come from. */
    fun position(): Long = position

    fun seek(offset: Long) {
        position = offset.coerceAtLeast(0)
    }

    /**
     * Fills up to [count] bytes at the current position, returning how many — or -1 at the end of
     * the file.
     */
    fun read(into: ByteArray, count: Int): Int {
        if (count <= 0) return 0
        if (length >= 0 && position >= length) return -1
        if (!holds(position) && (brokeOff || !fetchWindowFor(position))) return -1
        val offset = (position - windowStart).toInt()
        val available = window.size - offset
        if (available <= 0) return -1
        val taken = minOf(count, available)
        System.arraycopy(window, offset, into, 0, taken)
        position += taken
        return taken
    }

    private fun holds(at: Long): Boolean =
        windowStart >= 0 && at >= windowStart && at < windowStart + window.size

    /** Fetches the window containing [at]. */
    private fun fetchWindowFor(at: Long): Boolean {
        // Straight on from a window that came up short, so what did arrive is not asked for again.
        val start = if (windowStart >= 0 && at == windowStart + window.size) at else at - (at % windowBytes)
        val end = start + windowBytes - 1
        val request = Request.Builder().url(url)
            .header("Range", "bytes=$start-$end")
            .header("Accept", "*/*")
            // Anything else would arrive compressed and the byte offsets the caller is counting in
            // would stop meaning anything.
            .header("Accept-Encoding", "identity")
        headers.forEach { (name, value) -> runCatching { request.header(name, value) } }

        // The app's own client, so the media goes the way the rest of YouTube does — including
        // round a network that throttles it, when that is switched on (see DpiBypass).
        // A dropped connection or a server hiccup used to end the stream on the spot, which FFmpeg
        // reports as "Invalid data" and the track was skipped. Asked again while there is time; a
        // refusal is an answer and is not asked twice.
        val startedAt = System.nanoTime()
        var attempt = 0
        var answer: Answer
        while (true) {
            if (Thread.currentThread().isInterrupted) return false
            val outcome = runCatching { fetch(request.get().build()) }
            val got = outcome.getOrNull()
            val problem = when {
                got == null -> "not fetched: ${outcome.exceptionOrNull()?.message}"
                got.status >= 500 -> "answered HTTP ${got.status}"
                got.status !in 200..299 -> null
                got.body.isEmpty() -> "answered HTTP ${got.status} with nothing in it"
                got.isNotMedia -> "answered HTTP ${got.status}, ${got.type}: ${got.preview()}"
                else -> null
            }
            if (problem == null && got != null) {
                answer = got
                break
            }
            attempt++
            val spentMs = (System.nanoTime() - startedAt) / 1_000_000
            if (attempt > RETRIES || spentMs > RETRY_BUDGET_MS) {
                DesktopTrackLog.log("stream: window at $at $problem")
                brokeOff = true
                return false
            }
            runCatching { Thread.sleep(300L * attempt) }.onFailure { return false }
        }
        val status = answer.status
        val body = answer.body
        val stated = answer.stated
        if (status !in 200..299) {
            DesktopTrackLog.log("stream: window at $at refused with HTTP $status")
            brokeOff = true
            // A URL cleared before playback has been refused mid-track: tell the resolver, as the
            // phone's ChunkedDataSource does, so it forgets it and benches the client that minted it.
            StreamResolver.onPlaybackRefused(url, status)
            return false
        }
        val spentMs = (System.nanoTime() - startedAt) / 1_000_000
        if (spentMs >= SLOW_WINDOW_MS) DesktopTrackLog.log("stream: window at $at took ${spentMs}ms for ${body.size} bytes")

        // A server that honoured the range says where the bytes came from and how many there are in
        // total.
        if (stated != null) {
            windowStart = stated.substringAfter("bytes ").substringBefore('-').trim().toLongOrNull() ?: start
            length = stated.substringAfterLast('/').trim().toLongOrNull() ?: length
        } else {
            windowStart = 0
            length = body.size.toLong()
        }
        window = body
        if (length < 0) length = windowStart + body.size
        return body.isNotEmpty()
    }

    /** One request. On a connection too slow to finish in time, what did arrive still counts. */
    private fun fetch(request: Request): Answer = client.newCall(request).execute().use { response ->
        val stated = response.header("content-range")
        val type = response.header("content-type")
        if (!response.isSuccessful) return@use Answer(response.code, ByteArray(0), stated, type)
        val received = java.io.ByteArrayOutputStream(windowBytes)
        val cut = runCatching { response.body.byteStream().copyTo(received) }.exceptionOrNull()
        // Without a range stated there is no saying where a part of the file belongs.
        if (cut != null && (received.size() == 0 || stated == null)) throw cut
        Answer(response.code, received.toByteArray(), stated, type)
    }

    private class Answer(val status: Int, val body: ByteArray, val stated: String?, val type: String?) {
        /** A page or a message where the audio should be: FFmpeg can only call that invalid data. */
        val isNotMedia: Boolean
            get() = type != null && (type.startsWith("text/") || type.contains("json") || type.contains("html"))

        fun preview(): String = String(body, 0, minOf(body.size, 120), Charsets.UTF_8).replace(Regex("\\s+"), " ")
    }

    private companion object {
        /** Comfortably inside what Google will serve. */
        const val DEFAULT_WINDOW = 512 * 1024

        /** Further tries for a window that failed on the way, not by refusal. */
        const val RETRIES = 2

        /** Past this, the decode-ahead is close to dry: the rest of the round is not worth waiting for. */
        const val RETRY_BUDGET_MS = 20_000L

        /** A window this slow is the reason for any gap that follows, so it is logged. */
        const val SLOW_WINDOW_MS = 5_000L

        val client = Http.client.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}
