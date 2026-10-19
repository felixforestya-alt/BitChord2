package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AutomixPerformanceMode
import com.music.bitchord.data.settings.TrackAnalysisState
import com.music.bitchord.playback.smart.AnalysisStore
import com.music.bitchord.playback.smart.BeatTracker
import com.music.bitchord.playback.smart.MelSpectrogram
import com.music.bitchord.playback.smart.TrackAnalysis
import com.music.bitchord.playback.smart.TrackFeatures
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

/** A file on this machine — a download or a local track — rather than anything streamed. */
internal val DesktopStream.isLocalFile: Boolean
    get() = !url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)

/**
 * Produces the evidence Automix mixes on: tempo, beat grid, key, structure and where a track can be
 * entered and left.
 */
internal class DesktopTrackAnalyzer(
    /**
     * How much CPU the listener has allowed background analysis, read fresh on every pass so a
     * change takes effect on the next track rather than the next launch.
     */
    private val performance: () -> AutomixPerformanceMode = { AutomixPerformanceMode.BALANCED },
    /**
     * True while analysis has no use — in a party, where Automix is off. Checked on request, before
     * a queued pass starts, and between and during its decodes, as Android's `TrackAnalyzer` does:
     * a request made a moment before the party started would otherwise run a whole-track decode
     * and a model pass for a transition that cannot happen. A stopped pass records nothing.
     */
    private val stopped: () -> Boolean = { false },
    private val onAnalysed: (String) -> Unit = {},
) {
    /** One analysis at a time, on a thread of this class's own making. */
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(null, runnable, "BitChord-Analysis", ANALYSIS_STACK_BYTES).apply {
            isDaemon = true
        }
    }

    /** Analyses kept between runs, so a track measured yesterday is not measured again today. */
    private val store = AnalysisStore { DesktopAnalysisRuntime.analysisHome() }

    private val results = ConcurrentHashMap<String, TrackAnalysis>()
    private val running = ConcurrentHashMap.newKeySet<String>()

    /** Tracks the analyser could not measure. */
    private val failed = ConcurrentHashMap.newKeySet<String>()

    /** When each of [failed] last failed, so it is retried on a cooldown rather than every tick. */
    private val failedAt = ConcurrentHashMap<String, Long>()

    /** Said once, not once a tick. */
    private val warnedNoAnalyser = java.util.concurrent.atomic.AtomicBoolean(false)

    private val beats by lazy {
        BeatTracker(
            modelPath = { DesktopAnalysisRuntime.modelPath(BeatTracker.MODEL_ASSET) },
            // One thread, paced: see [Pacer]. Spread over several cores the model came in bursts
            // that took whole cores from whatever else was running — a game, above all.
            inferenceThreads = { 1 },
            betweenChunks = { pacer.pace() },
        )
    }

    /**
     * Keeps analysis to a quarter of one core on average: work in short stretches, then a rest
     * three times as long. A track is still measured in well under a minute — long before the
     * transition at its end that needs it — but never as a burst that shows up beside a game.
     */
    private val pacer = Pacer(DUTY)

    private class Pacer(private val duty: Double) {
        private var since = System.nanoTime()

        fun reset() {
            since = System.nanoTime()
        }

        fun pace() {
            val busy = System.nanoTime() - since
            if (busy < STRETCH_NANOS) return
            val rest = (busy * (1 - duty) / duty / 1_000_000).toLong().coerceAtMost(MAX_REST_MS)
            runCatching { Thread.sleep(rest) }
            since = System.nanoTime()
        }
    }

    /** What is known now, never a computation — the planner asks this every tick. */
    fun analysisFor(trackId: String): TrackAnalysis =
        results[trackId] ?: restored(trackId) ?: TrackAnalysis(trackId = trackId)

    /** A stored analysis, promoted into memory on first ask. */
    private fun restored(trackId: String): TrackAnalysis? =
        store.load(trackId)?.also { results[trackId] = it }

    fun isAnalysed(trackId: String): Boolean =
        results.containsKey(trackId) || restored(trackId) != null

    /** How far this track has got, for the player's Automix line. */
    fun stateFor(trackId: String): TrackAnalysisState = when {
        trackId.isBlank() -> TrackAnalysisState.WAITING
        !DesktopAnalysisRuntime.available -> TrackAnalysisState.FAILED
        // Usable first and a pass in flight second, the order Android settled on.
        results[trackId]?.isUsable == true ->
            if (trackId in running) TrackAnalysisState.REFINING else TrackAnalysisState.ANALYSED
        trackId in running -> TrackAnalysisState.ANALYSING
        // A recorded-but-unusable result is the analyser saying it tried and got nothing, and that
        // it will not try again.
        trackId in failed || results.containsKey(trackId) -> TrackAnalysisState.FAILED
        else -> TrackAnalysisState.WAITING
    }

    /**
     * Asks for [song] to be analysed, if it has not been already.
     *
     * [stream] is only read when it is a file on this machine. Anything streamed is measured on
     * the track's YouTube Opus instead — never on the addon, JioSaavn or lossless copy playback
     * may be using — exactly as Android's analyser reads its own pinned Opus rendition (see
     * `AutomixAnalysisSource`). That keeps an addon from being asked for a track nobody is
     * playing yet, and one recording's measurements from being stored under another's id.
     */
    fun request(song: Song, stream: DesktopStream?, durationSeconds: Double) {
        val trackId = song.videoId
        if (trackId.isBlank()) return
        if (durationSeconds <= 0) return
        if (stopped()) return
        if (!DesktopAnalysisRuntime.available) {
            if (warnedNoAnalyser.compareAndSet(false, true)) {
                DesktopTrackLog.log("automix: this build has no analyser, so nothing will be measured")
            }
            return
        }
        if (results.containsKey(trackId) || restored(trackId) != null) return
        // Asked every tick; a track that just failed is not resolved and decoded again each time.
        failedAt[trackId]?.let { if (System.currentTimeMillis() - it < RETRY_AFTER_MS) return }
        if (!running.add(trackId)) return
        failed.remove(trackId)
        val local = stream?.takeIf { it.isLocalFile }
        DesktopTrackLog.log(
            "automix: analysing '${song.title}' (${"%.0f".format(durationSeconds)}s)" +
                if (local != null) " from the file" else " from YouTube Opus",
        )

        worker.execute {
            run {
                if (stopped()) {
                    running.remove(trackId)
                    return@execute
                }
                // Android's rule: the lowest rung yields to playback rather than competing for a
                // core, and the thread count stays the speed knob for the other two.
                // Background work in the system's own sense too: lowest CPU, disk and memory
                // priority, so it waits for anything in the foreground rather than competing.
                Thread.currentThread().priority = Thread.MIN_PRIORITY
                enterBackgroundMode()
                pacer.reset()
                val analysis = runCatching {
                    val source = local ?: youTubeOpus(song) ?: return@runCatching null
                    analyse(trackId, source, durationSeconds)
                }
                    .onFailure { DesktopTrackLog.log("analysis of '${song.title}' failed: ${it.message}") }
                    .getOrNull()
                if (analysis == null && stopped()) {
                    // Called off, not failed: it is free to be measured once the party ends.
                    running.remove(trackId)
                    return@execute
                }
                if (analysis == null) {
                    failed += trackId
                    failedAt[trackId] = System.currentTimeMillis()
                }
                if (analysis != null) {
                    failedAt.remove(trackId)
                    store.save(trackId, analysis)
                    results[trackId] = analysis
                    DesktopTrackLog.log(
                        "analysed '${song.title}': ${"%.1f".format(analysis.bpm)} bpm" +
                            ", ${analysis.downbeats.size} downbeats" +
                            ", key ${analysis.key.ifBlank { "unknown" }}",
                    )
                    onAnalysed(trackId)
                }
                running.remove(trackId)
            }
        }
    }

    /**
     * [song]'s YouTube Opus stream, bypassing the source race playback runs — or null when there
     * is no YouTube copy of it to measure.
     */
    private fun youTubeOpus(song: Song): DesktopStream? = runBlocking {
        val identity = DesktopSourceRegistry.youTubeIdentity(song)
        if (identity == null) {
            DesktopTrackLog.log("automix: no YouTube copy of '${song.title}' to measure")
            return@runBlocking null
        }
        if (identity.videoId != song.videoId) {
            DesktopTrackLog.log("automix: measuring '${song.title}' on YouTube's ${identity.videoId}")
        }
        DesktopStreamClient.resolve(identity).getOrThrow()
    }

    private fun analyse(
        trackId: String,
        stream: DesktopStream,
        durationSeconds: Double,
    ): TrackAnalysis? {
        val features = decode(stream, TrackFeatures.sampleRate)
            ?.let { pcm -> TrackFeatures.analyze(pcm, durationSeconds).also { pacer.pace() } }
            ?: return null

        if (stopped()) return null
        // The model runs at its own rate, so it gets its own decode rather than a resample of the
        // analyser's.
        val grid = decode(stream, MelSpectrogram.sampleRate)?.let { beats.track(it) }

        return TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            trackId = trackId,
            duration = durationSeconds,
            // The grid wins where it has an opinion: a trained model reads meter, which
            // autocorrelation cannot.
            bpm = grid?.bpm ?: features.bpm,
            beatInterval = grid?.beatInterval ?: features.beatInterval,
            beatConfidence = grid?.beatConfidence ?: features.beatConfidence,
            downbeats = grid?.downbeats ?: features.downbeats,
            firstBeat = grid?.firstBeat ?: features.firstBeat,
            phraseBoundaries = features.phraseBoundaries,
            key = features.key,
            keyConfidence = features.keyConfidence,
            audibleStartTime = features.audibleStartTime,
            pickupTime = features.pickupTime,
            introEndTime = features.introEndTime,
            contentEndTime = features.contentEndTime,
            outroStartTime = features.outroStartTime,
            mixInTime = features.mixInTime,
            mixOutTime = features.mixOutTime,
            mixInCandidates = features.mixInCandidates,
            mixOutCandidates = features.mixOutCandidates,
            energyCurve = features.energyCurve,
            lowEnergyCurve = features.lowEnergyCurve,
            vocalActivityMask = features.vocalActivityMask,
            vocalProbability = features.vocalProbability,
        )
    }

    /** The whole track as mono float at [rate]. */
    private fun decode(stream: DesktopStream, rate: Double): FloatArray? {
        val decoder = DesktopAudioDecoder()
        val opened = decoder.open(
            url = stream.url,
            headers = stream.headers,
            requested = DesktopPcmFormat(rate.toInt(), channels = 1, bytesPerSample = 4, isFloat = true),
            windowed = stream.windowedReads,
            transport = stream.transport,
        )
        if (opened.isFailure) {
            decoder.close()
            return null
        }
        return try {
            val out = ArrayList<FloatArray>()
            var total = 0
            while (true) {
                if (stopped()) return null
                val block = decoder.readSamples() ?: break
                pacer.pace()
                val count = decoder.sampleCount
                if (count <= 0) continue
                out += block.copyOf(count)
                total += count
                if (total > MAX_SAMPLES) break
            }
            if (total == 0) return null
            val samples = FloatArray(total)
            var at = 0
            for (block in out) {
                block.copyInto(samples, at)
                at += block.size
            }
            samples
        } finally {
            decoder.close()
        }
    }

    fun release() {
        worker.shutdownNow()
        results.clear()
        running.clear()
        failed.clear()
        failedAt.clear()
    }

    internal companion object {

        /** How long a track that failed to be measured waits before it is tried again. */
        const val RETRY_AFTER_MS = 60_000L

        /**
         * A ceiling of about twenty minutes at the analyser's rate, so a mis-tagged eight-hour
         * upload cannot eat the heap.
         */
        const val MAX_SAMPLES = 20 * 60 * 11_025

        /** Stack for the analysis thread. */
        const val ANALYSIS_STACK_BYTES = 64L * 1024 * 1024
    }
}

/** The queue facts the shared planner needs, from a [Song]. */
internal fun Song.transitionInfo(durationMs: Long) = com.music.bitchord.playback.smart.TransitionTrackInfo(
    id = videoId,
    durationMs = durationMs,
    title = title,
    artist = artist,
    album = albumName.orEmpty(),
    albumId = albumId.orEmpty(),
)

/** "3:27" as seconds, or zero when there is nothing to read. */
internal fun String?.durationSeconds(): Double {
    val parts = this?.split(':')?.mapNotNull { it.trim().toIntOrNull() } ?: return 0.0
    return when (parts.size) {
        2 -> (parts[0] * 60 + parts[1]).toDouble()
        3 -> (parts[0] * 3_600 + parts[1] * 60 + parts[2]).toDouble()
        else -> 0.0
    }
}

/** How much of one core analysis may use, on average. */
private const val DUTY = 0.25

/** How long a stretch of work runs before its rest. */
private const val STRETCH_NANOS = 20_000_000L

/** The longest single rest, so a long stretch does not stall the analysis for seconds. */
private const val MAX_REST_MS = 400L

/** Windows' background mode for the calling thread: low CPU, I/O and memory priority. */
private fun enterBackgroundMode() {
    if (!DesktopPlatform.isWindows) return
    runCatching {
        val kernel = com.sun.jna.NativeLibrary.getInstance("kernel32")
        val thread = kernel.getFunction("GetCurrentThread").invokePointer(arrayOf())
        kernel.getFunction("SetThreadPriority").invokeInt(arrayOf(thread, THREAD_MODE_BACKGROUND_BEGIN))
    }
}

private const val THREAD_MODE_BACKGROUND_BEGIN = 0x00010000
