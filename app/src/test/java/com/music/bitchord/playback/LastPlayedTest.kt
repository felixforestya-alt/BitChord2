package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The queue kept in one file and where it is in it in another: what comes back after a restart. */
class LastPlayedTest {

    private lateinit var queueFile: FakeSharedPreferences
    private lateinit var progressFile: FakeSharedPreferences

    @Before
    fun setUp() {
        queueFile = FakeSharedPreferences()
        progressFile = FakeSharedPreferences()
        LastPlayed.initForTesting(queueFile, progressFile)
    }

    /** As the service would after a restart: the same files, read afresh. */
    private fun restart() = LastPlayed.initForTesting(queueFile, progressFile)

    @Test
    fun `the newest position comes back`() {
        LastPlayed.saveQueue(songs(10), 3)
        LastPlayed.savePlaybackState(3, 41_000L)
        LastPlayed.savePlaybackState(4, 2_000L)
        restart()
        val back = requireNotNull(LastPlayed.load())
        assertEquals(4, back.index)
        assertEquals(2_000L, back.positionMs)
    }

    @Test
    fun `a queue edit keeps the position, as one file did`() {
        LastPlayed.saveQueue(songs(10), 3)
        LastPlayed.savePlaybackState(3, 41_000L)
        // A track added behind the playing one: the queue is saved again, without a position.
        LastPlayed.saveQueue(songs(11), 3)
        restart()
        val back = requireNotNull(LastPlayed.load())
        assertEquals(3, back.index)
        assertEquals(41_000L, back.positionMs)
    }

    @Test
    fun `a position saved for an older queue is not read against a newer one`() {
        LastPlayed.saveQueue(songs(10), 3)
        LastPlayed.savePlaybackState(3, 41_000L)
        // The newer queue reached its file and its progress did not, as when the process dies
        // between the two writes.
        val olderProgress = progressFile.getAll()
        LastPlayed.saveQueueImmediately(songs(5), 1, 7_000L)
        progressFile.edit().clear().apply()
        val edit = progressFile.edit()
        olderProgress.forEach { (key, value) ->
            when (value) {
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
            }
        }
        edit.apply()
        restart()
        val back = requireNotNull(LastPlayed.load())
        assertEquals(1, back.index)
        assertEquals(7_000L, back.positionMs)
    }

    @Test
    fun `a queue stored before the progress file keeps the positions saved after the update`() {
        // As an older version left it: index and position beside the queue, no stamp.
        LastPlayed.saveQueueImmediately(songs(10), 2, 9_000L)
        queueFile.edit().remove("stamp").apply()
        progressFile.edit().clear().apply()
        restart()
        assertEquals(9_000L, requireNotNull(LastPlayed.load()).positionMs)
        LastPlayed.savePlaybackState(2, 60_000L)
        restart()
        assertEquals(60_000L, requireNotNull(LastPlayed.load()).positionMs)
    }

    @Test
    fun `clearing forgets the position too`() {
        LastPlayed.saveQueue(songs(10), 3)
        LastPlayed.savePlaybackState(3, 41_000L)
        LastPlayed.clear()
        assertNull(LastPlayed.load())
        LastPlayed.saveQueue(songs(10), 0)
        assertEquals(0L, requireNotNull(LastPlayed.load()).positionMs)
    }

    @Test
    fun `a progress save rewrites only the small file`() {
        LastPlayed.saveQueue(songs(76), 25)
        val queueWrites = queueFile.writes
        val progressBefore = progressFile.rewrittenBytes
        // A minute of playback, saved every five seconds.
        repeat(12) { LastPlayed.savePlaybackState(25, it * 5_000L) }
        assertEquals(queueWrites, queueFile.writes)
        val perSave = (progressFile.rewrittenBytes - progressBefore) / 12
        println(
            "BENCH last played, 76 tracks: a progress save rewrote ${queueFile.sizeBytes()} B before " +
                "(the queue's file), $perSave B now (the progress file)",
        )
        assertTrue(perSave < 100)
    }

    private fun songs(count: Int) = List(count) {
        Song(
            videoId = "video-$it",
            title = "A track of a usual length $it",
            artist = "Some Artist, Another Artist",
            thumbnailUrl = "https://lh3.googleusercontent.com/some-long-thumbnail-path-$it=w544-h544-l90-rj",
            durationText = "3:21",
        )
    }
}
