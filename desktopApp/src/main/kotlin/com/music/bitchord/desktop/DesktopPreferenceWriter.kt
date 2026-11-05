package com.music.bitchord.desktop

import java.util.prefs.Preferences

/**
 * Saves that come many times a second — a volume, speed or equaliser drag — or with the click that
 * changes the track, written off the thread that asked for them.
 *
 * Each of those saves went to the store and flushed it there and then, and nearly every one was
 * asked for on the UI thread. The flush is the expensive half: on Windows it is RegFlushKey, which
 * waits for the registry hive to reach the disk, and on Linux the whole preferences file written
 * out again. A volume drag flushed on every step of the drag; a track change saved the queue, its
 * place and the history, three flushes before the next track could start.
 *
 * Here the latest value of each key waits, is written on a thread of its own as soon as it can be
 * — the same chunks in the same place, by [DesktopPreferenceChunks] — and the store is flushed once
 * the saves have paused for a moment, and at least every couple of seconds while they keep coming.
 * A key still waiting reads as its waiting value, so nothing reads older than what was saved. An
 * orderly exit writes and flushes whatever is left.
 *
 * Only for the keys whose every save comes through here: one saved here and also directly could
 * have the older of the two land last.
 */
internal object DesktopPreferenceWriter {

    private class Pending(val node: Preferences, val key: String, val id: String, produce: () -> String) {
        /** Worked out once, by whichever comes first: the writer, or someone reading it back. */
        val text: String by lazy(produce)
    }

    /** Guards [waiting] and the times; held only for a lookup or an insert, never for a write. */
    private val lock = Object()
    private val waiting = LinkedHashMap<String, Pending>()
    private var lastSaveAt = 0L

    /** Held for every write to the store, so the exit's writes and the thread's never interleave. */
    private val writing = Any()
    private val written = java.util.concurrent.ConcurrentHashMap.newKeySet<Preferences>()

    init {
        Thread(::run, "bitchord-prefs").apply {
            isDaemon = true
            Runtime.getRuntime().addShutdownHook(Thread(::drain, "bitchord-prefs-exit"))
            start()
        }
    }

    /**
     * Saves what [value] gives under [key], soon and off the calling thread; a later save of the
     * same key replaces this one if it has not been written yet. [value] is worked out on the
     * writing thread, so it must only read what will not change under it.
     */
    fun save(node: Preferences, key: String, value: () -> String) {
        val id = node.absolutePath() + '\u0000' + key
        synchronized(lock) {
            waiting[id] = Pending(node, key, id, value)
            lastSaveAt = System.nanoTime()
            lock.notifyAll()
        }
    }

    /** The value saved under [key] and not yet written, or null when nothing is waiting. */
    fun waiting(node: Preferences, key: String): String? {
        val pending = synchronized(lock) {
            if (waiting.isEmpty()) return null
            waiting[node.absolutePath() + '\u0000' + key]
        }
        return pending?.text
    }

    /** Writes and flushes everything still waiting, on the calling thread. */
    fun drain() {
        synchronized(writing) {
            val rest = synchronized(lock) { waiting.values.toList().also { waiting.clear() } }
            for (pending in rest) write(pending)
            for (node in written) runCatching { node.flush() }
        }
    }

    private fun run() {
        var dirtySince = 0L
        while (true) {
            var next: Pending? = null
            synchronized(lock) {
                while (true) {
                    next = waiting.values.firstOrNull()
                    if (next != null) break
                    if (dirtySince == 0L) {
                        lock.wait()
                        continue
                    }
                    val now = System.nanoTime()
                    val flushAt = minOf(lastSaveAt + QUIET_NANOS, dirtySince + LATEST_NANOS)
                    if (now >= flushAt) break
                    lock.wait(((flushAt - now) / 1_000_000L).coerceAtLeast(1L))
                }
            }
            val pending = next
            if (pending == null) {
                synchronized(writing) { for (node in written) runCatching { node.flush() } }
                dirtySince = 0L
                continue
            }
            synchronized(writing) {
                // Replaced while it waited for this, or already written by the exit: the newer
                // value is the one to write, and it is still waiting.
                val current = synchronized(lock) { waiting[pending.id] === pending }
                if (current) write(pending)
            }
            if (dirtySince == 0L) dirtySince = System.nanoTime()
            // Only now does a read go back to the store: until the chunks were all in, the value
            // there was half old and half new.
            synchronized(lock) { if (waiting[pending.id] === pending) waiting.remove(pending.id) }
        }
    }

    private fun write(pending: Pending) {
        runCatching { DesktopPreferenceChunks.write(pending.node, pending.key, pending.text, flush = false) }
        written += pending.node
    }

    /** Flushed once the saves have paused this long... */
    private const val QUIET_NANOS = 400_000_000L

    /** ...or at the latest this long after the first write that was not. */
    private const val LATEST_NANOS = 2_000_000_000L
}
