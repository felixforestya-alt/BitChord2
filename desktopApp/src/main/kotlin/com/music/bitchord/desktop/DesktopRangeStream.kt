package com.music.bitchord.desktop

import com.music.bitchord.data.Http
import com.music.bitchord.data.innertube.StreamResolver
import java.util.concurrent.TimeUnit
import okhttp3.Request

/** Reads a remote file in bounded windows rather than as one open-ended request. */
internal class DesktopRangeStream(
    private var url: String,
    private var headers: Map<String, String> = emptyMap(),
    private val windowBytes: Int = DEFAULT_WINDOW,
    /**
     * Another link to the same track, for when this one is refused or stops answering. Called on
     * the decoding thread, and may take its time: what is already decoded plays on meanwhile, so
     * the listener hears nothing happen.
     */
    private val renew: (() -> Link?)? = null,
) {

    internal class Link(val url: String, val headers: Map<String, String>)

    private var renewals = 0
    private var renewed = false

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

    /** Fetches the window containing [at], on a fresh link if the one in hand has gone bad. */
    private fun fetchWindowFor(at: Long): Boolean {
        // Straight on from a window that came up short, so what did arrive is not asked for again.
        val start = if (windowStart >= 0 && at == windowStart + window.size) at else at - (at % windowBytes)
        val end = start + windowBytes - 1

        val startedAt = System.nanoTime()
        var attempt = 0
        var refusals = 0
        while (true) {
            if (Thread.currentThread().isInterrupted) return false
            val outcome = runCatching { fetch(requestFor(start, end)) }
            val got = outcome.getOrNull()
            val problem = when {
                got == null -> "not fetched: ${outcome.exceptionOrNull()?.message}"
                got.status >= 500 -> "answered HTTP ${got.status}"
                got.status !in 200..299 -> "refused with HTTP ${got.status}"
                got.body.isEmpty() -> "answered HTTP ${got.status} with nothing in it"
                got.isNotMedia -> "answered HTTP ${got.status}, ${got.type}: ${got.preview()}"
                else -> null
            }
            if (problem == null && got != null) {
                if (!sameFile(got.stated)) {
                    // A different rendition: its bytes would land mid-frame in this one. The engine
                    // reopens by time instead, which costs a moment and not the track.
                    giveUp(at, "the new link is a different file (${got.stated} against $length bytes)")
                    return false
                }
                accept(got, start)
                val spentMs = (System.nanoTime() - startedAt) / 1_000_000
                if (renewed) {
                    renewed = false
                    DesktopAlerts.raise(
                        kind = "link",
                        title = DesktopStrings["d_alert_link_renewed", "YouTube link replaced on the fly; the track kept playing"],
                        detail = "byte $at, ${spentMs}ms",
                        popup = false,
                    )
                } else if (spentMs >= SLOW_WINDOW_MS) {
                    DesktopTrackLog.log("stream: window at $at took ${spentMs}ms for ${got.body.size} bytes")
                }
                return got.body.isNotEmpty()
            }

            // A refusal is an answer and is not asked twice. Anything else — a dropped connection,
            // a server hiccup — is asked again while there is time.
            val refused = got != null && got.status in 300..499
            if (!refused) {
                attempt++
                val spentMs = (System.nanoTime() - startedAt) / 1_000_000
                if (attempt <= RETRIES && spentMs <= RETRY_BUDGET_MS) {
                    runCatching { Thread.sleep(300L * attempt) }.onFailure { return false }
                    continue
                }
            }
            DesktopTrackLog.log("stream: window at $at $problem")
            if (refused) {
                refusals++
                // The first refusal costs only this link: another from the same client takes
                // under a second, inside what the decode-ahead holds, where benching the client
                // sends the resolver through ones that refuse too for up to 15 seconds. A second
                // means it is the client, and the resolver is told, as the phone's
                // ChunkedDataSource tells it.
                if (refusals > 1 || renew == null) StreamResolver.onPlaybackRefused(url, got?.status ?: 403)
            }
            if (refusals > MAX_RENEWALS || !renewLink()) {
                giveUp(at, problem ?: "failed")
                return false
            }
            attempt = 0
        }
    }

    private fun requestFor(start: Long, end: Long): Request {
        val request = Request.Builder().url(url)
            .header("Range", "bytes=$start-$end")
            .header("Accept", "*/*")
            // Anything else would arrive compressed and the byte offsets the caller is counting in
            // would stop meaning anything.
            .header("Accept-Encoding", "identity")
        headers.forEach { (name, value) -> runCatching { request.header(name, value) } }
        return request.get().build()
    }

    /** Whether a window that says [stated] belongs to the file being read. */
    private fun sameFile(stated: String?): Boolean {
        if (length < 0 || stated == null) return true
        val total = stated.substringAfterLast('/').trim().toLongOrNull() ?: return true
        return total == length
    }

    private fun accept(got: Answer, start: Long) {
        // A server that honoured the range says where the bytes came from and how many there are in
        // total.
        if (got.stated != null) {
            windowStart = got.stated.substringAfter("bytes ").substringBefore('-').trim().toLongOrNull() ?: start
            length = got.stated.substringAfterLast('/').trim().toLongOrNull() ?: length
        } else {
            windowStart = 0
            length = got.body.size.toLong()
        }
        window = got.body
        if (length < 0) length = windowStart + got.body.size
    }

    /** A fresh link in place of this one; false when there is none to be had. */
    private fun renewLink(): Boolean {
        val asked = renew ?: return false
        if (renewals >= MAX_RENEWALS_PER_STREAM) return false
        renewals++
        StreamResolver.forget(url)
        val fresh = runCatching { asked() }.getOrNull() ?: return false
        if (fresh.url == url) return false
        url = fresh.url
        headers = fresh.headers
        renewed = true
        return true
    }

    private fun giveUp(at: Long, why: String) {
        DesktopAlerts.lastStreamProblem = "byte $at: $why"
        brokeOff = true
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

        /** Links tried for one window before the engine is left to reopen the track. */
        const val MAX_RENEWALS = 2

        /** Links tried over one play of a track, so that a dead track cannot spin on them. */
        const val MAX_RENEWALS_PER_STREAM = 8

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
