package com.music.bitchord.desktop

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** What [DesktopDecodeAhead] reads from: a decoder, or a stand-in for one in tests. */
internal interface DesktopSampleSource {
    /** The next block as interleaved floats, or null at the end. The array may be reused. */
    fun readSamples(): FloatArray?

    /** How much of the array [readSamples] returned is actually this block. */
    val sampleCount: Int

    fun seek(micros: Long): Boolean

    fun close()

    /** Whether the end just reached was the connection failing rather than the file ending. */
    val brokeOff: Boolean get() = false
}

/**
 * Decodes a track a few seconds ahead of the speakers, on a thread of its own.
 *
 * The audio thread used to call the decoder directly, and the decoder reads straight from the
 * network: a YouTube stream stops for a whole 512 KiB range request every half-minute or so, and a
 * lossless stream waits on its socket whenever the connection hiccups. WASAPI holds only a fifth
 * of a second, so any read slower than that was heard as a gap of a few milliseconds. Here those
 * waits land on this thread while the audio thread plays what was decoded earlier.
 *
 * Only this thread touches [source] once constructed — seeks and the close are handed to it — so
 * the native decoder never sees two threads.
 */
internal class DesktopDecodeAhead(
    private val source: DesktopSampleSource,
    val outputFormat: DesktopPcmFormat,
    val durationUs: Long?,
    val measuredFormat: DesktopStreamFormat?,
    /** How far ahead to decode, in samples across all channels. */
    private val aheadSamples: Int = outputFormat.sampleRate * outputFormat.channels * AHEAD_SECONDS,
    name: String = "BitChord-Decode",
) {

    private val lock = ReentrantLock()
    /** Signalled when there is something for the audio thread to take. */
    private val ready = lock.newCondition()
    /** Signalled when the worker has room, a seek, or a close to act on. */
    private val wake = lock.newCondition()

    private val queue = ArrayDeque<FloatArray>()
    /** Blocks done with, for the worker to fill again: a block every 20 ms, not one thrown away. */
    private val spare = ArrayDeque<FloatArray>()
    /** The block last handed out, which the audio thread has finished with when it asks again. */
    private var handedOut: FloatArray? = null
    private var queuedSamples = 0
    private var ended = false
    private var closed = false
    private var pendingSeekUs: Long? = null
    /** Bumped by every seek, so a block decoded before it is never played after it. */
    private var generation = 0
    /** Whether a block has been handed out since the last seek; a wait before the first is a load. */
    private var started = false

    /** How much of the array [readSamples] returned is actually this block. */
    var sampleCount: Int = 0
        private set

    /** The end reached was a failed connection, not the end of the file. */
    @Volatile
    var brokeOff = false
        private set

    private val worker = Thread(::decodeLoop, name).apply {
        isDaemon = true
        // Below the UI thread, which draws every frame of the display and must never wait on
        // this; it buffers well ahead, so a frame's worth of delay costs it nothing. The audio
        // thread itself stays above both.
        priority = Thread.NORM_PRIORITY
        start()
    }

    /** The next decoded block, waiting for the decoder only if it has fallen behind. */
    fun readSamples(): FloatArray? = lock.withLock {
        if (queue.isEmpty() && !ended && !closed) {
            val waitedFrom = System.nanoTime()
            while (queue.isEmpty() && !ended && !closed) ready.await()
            val waitedMs = (System.nanoTime() - waitedFrom) / 1_000_000
            if (started && waitedMs >= STARVED_LOG_MS) {
                DesktopTrackLog.log("decode-ahead ran dry: the audio thread waited ${waitedMs}ms for the decoder")
                // The upper bound is the computer sleeping, which the clock counts.
                if (waitedMs in SOUND_GAP_ALERT_MS..SLEPT_MS) {
                    DesktopAlerts.raise(
                        kind = "gap",
                        title = DesktopStrings["d_alert_sound_gap", "Sound stopped for %s s: the music did not arrive in time"]
                            .format(waitedMs / 1_000),
                        detail = DesktopAlerts.lastStreamProblem,
                    )
                }
            }
        }
        handedOut?.let(::recycle)
        handedOut = null
        val block = queue.removeFirstOrNull()
        if (block == null) {
            sampleCount = 0
            return null
        }
        handedOut = block
        queuedSamples -= block.size
        started = true
        wake.signal()
        sampleCount = block.size
        block
    }

    /** Moves to [micros]; whatever was decoded ahead of the old position is thrown away. */
    fun seek(micros: Long): Boolean {
        lock.withLock {
            generation++
            queue.forEach(::recycle)
            queue.clear()
            queuedSamples = 0
            ended = false
            started = false
            pendingSeekUs = micros
            wake.signal()
        }
        return true
    }

    /** Stops decoding; the decoder itself is closed by the worker, once any read in flight returns. */
    fun close() {
        lock.withLock {
            closed = true
            queue.clear()
            queuedSamples = 0
            wake.signal()
            ready.signalAll()
        }
    }

    private fun recycle(block: FloatArray) {
        if (spare.size < MAX_SPARE) spare.addLast(block)
    }

    /** A spare block of exactly [size]; block sizes hardly ever change within a track. */
    private fun takeSpare(size: Int): FloatArray? {
        val block = spare.removeLastOrNull() ?: return null
        return block.takeIf { it.size == size }
    }

    private fun decodeLoop() {
        try {
            while (true) {
                var seekTo: Long? = null
                val decodingFor = lock.withLock {
                    while (!closed && pendingSeekUs == null && (ended || queuedSamples >= aheadSamples)) {
                        wake.await()
                    }
                    if (closed) return
                    seekTo = pendingSeekUs
                    pendingSeekUs = null
                    generation
                }
                val micros = seekTo
                if (micros != null) {
                    if (!source.seek(micros)) DesktopTrackLog.log("decode-ahead: seek to ${micros / 1_000}ms failed")
                    continue
                }

                val decoded = try {
                    source.readSamples()?.let { samples ->
                        val count = source.sampleCount
                        val block = lock.withLock { takeSpare(count) } ?: FloatArray(count)
                        System.arraycopy(samples, 0, block, 0, count)
                        block
                    }
                } catch (failure: Exception) {
                    DesktopTrackLog.log("decode-ahead: decoder failed, ending the track: ${failure.message}")
                    brokeOff = true
                    null
                }
                if (decoded == null && source.brokeOff) brokeOff = true
                lock.withLock {
                    // A seek came in while this block was being read; it belongs to the old position.
                    if (decodingFor != generation) return@withLock
                    // Close came in while this block was being read. Committing it would leave the
                    // queue non-empty after close, and the caller's very next readSamples() — which
                    // the contract says is null — would return this block instead. Seen on Windows
                    // CI, where scheduling lets the caller's close win this race consistently;
                    // Linux's timing hides it. The block dies with the closed reader.
                    if (closed) return@withLock
                    if (decoded == null) {
                        ended = true
                    } else if (decoded.isNotEmpty()) {
                        queue.addLast(decoded)
                        queuedSamples += decoded.size
                    }
                    ready.signalAll()
                }
            }
        } catch (_: InterruptedException) {
            // Closing the app; the decoder goes with the process.
        } finally {
            runCatching { source.close() }
        }
    }

    private companion object {
        /** Long enough to ride out a slow range request or a reconnect. */
        const val AHEAD_SECONDS = 8

        /** Waits shorter than this are ordinary scheduling, not a network stall worth logging. */
        const val STARVED_LOG_MS = 20L

        const val MAX_SPARE = 16

        /** A gap long enough that the listener is wondering what happened. */
        const val SOUND_GAP_ALERT_MS = 3_000L
        const val SLEPT_MS = 600_000L
    }
}
