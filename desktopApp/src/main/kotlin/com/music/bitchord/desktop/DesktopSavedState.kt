package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import java.util.concurrent.atomic.AtomicReference

/**
 * What the window opens with from last time — the queue, the history, likes, downloads,
 * playlists — read and decoded on a thread of its own while skiko and the network come up,
 * instead of in the window's first composition on the UI thread, where it cost 28–39 ms of cold
 * code before the first frame.
 *
 * Taken once: the first composition gets what was read here if it is ready, and reads for itself
 * if it is not, so a slow disk only means the old way. Nothing is saved before that first
 * composition, so what was read here is still what is stored. Any later composition — another
 * account, say — reads the store itself.
 */
internal object DesktopSavedState {

    class Snapshot(
        val queue: List<Song>,
        val queueIndex: Int,
        val history: List<Song>,
        val likedIds: Set<String>,
        val dislikedIds: Set<String>,
        val downloads: List<Song>,
        val playlists: List<DesktopPlaylist>,
    )

    private val ready = AtomicReference<Snapshot?>(null)
    @Volatile private var taken = false

    fun start() {
        Thread({
            val read = runCatching { read(DesktopPersistence()) }.getOrNull() ?: return@Thread
            ready.set(read)
            // Too late for the window: not kept for nothing.
            if (taken) ready.set(null)
        }, "bitchord-saved-state").apply { isDaemon = true }.start()
    }

    /** What was read ahead, once; read from [persistence] there and then if it is not ready. */
    fun take(persistence: DesktopPersistence): Snapshot {
        val ahead = if (taken) null else ready.getAndSet(null)
        taken = true
        return ahead ?: read(persistence)
    }

    private fun read(persistence: DesktopPersistence) = Snapshot(
        queue = persistence.queue(),
        queueIndex = persistence.string("queue_index", "0").toIntOrNull() ?: 0,
        history = persistence.history(),
        likedIds = persistence.likedIds(),
        dislikedIds = persistence.dislikedIds(),
        downloads = persistence.downloads(),
        playlists = persistence.playlists(),
    )
}
