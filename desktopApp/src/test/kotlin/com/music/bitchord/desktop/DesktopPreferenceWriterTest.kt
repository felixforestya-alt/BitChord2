package com.music.bitchord.desktop

import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The saves a drag or a track change makes, written off the UI thread: what is read back has to be
 * what was saved last, before the write and after it.
 */
class DesktopPreferenceWriterTest {

    private val node: Preferences = Preferences.userRoot().node("com.music.bitchord.desktop.test.writer.${System.nanoTime()}")

    @AfterTest
    fun tearDown() {
        DesktopPreferenceWriter.drain()
        runCatching { node.removeNode() }
    }

    /** Until the writer's own thread has put [expected] in the store, or a few seconds pass. */
    private fun storedEventually(key: String, expected: String): Boolean {
        val until = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < until) {
            if (DesktopPreferenceWriter.waiting(node, key) == null && DesktopPreferenceChunks.read(node, key) == expected) {
                return true
            }
            Thread.sleep(5)
        }
        return false
    }

    @Test
    fun `a value saved for later reads back at once and reaches the store by itself`() {
        DesktopPreferenceWriter.save(node, "volume") { "0.35" }

        assertEquals("0.35", DesktopPreferenceWriter.waiting(node, "volume") ?: DesktopPreferenceChunks.read(node, "volume"))
        assertTrue(storedEventually("volume", "0.35"), "the writer never wrote it")
    }

    @Test
    fun `the last of a run of saves is the one that stays`() {
        for (step in 0..200) DesktopPreferenceWriter.save(node, "volume") { "0.$step" }

        assertEquals("0.200", DesktopPreferenceWriter.waiting(node, "volume") ?: DesktopPreferenceChunks.read(node, "volume"))
        assertTrue(storedEventually("volume", "0.200"), "an older save landed last")
    }

    @Test
    fun `a value longer than the platform limit is written whole`() {
        val huge = buildString { repeat(Preferences.MAX_VALUE_LENGTH * 3) { append('a' + (it % 26)) } }

        DesktopPreferenceWriter.save(node, "queue") { huge }

        assertTrue(storedEventually("queue", huge), "the chunks did not add back up")
    }

    @Test
    fun `an orderly exit writes what is still waiting`() {
        DesktopPreferenceWriter.save(node, "history") { "one\ntwo" }

        DesktopPreferenceWriter.drain()

        assertEquals(null, DesktopPreferenceWriter.waiting(node, "history"))
        assertEquals("one\ntwo", DesktopPreferenceChunks.read(node, "history"))
    }

    @Test
    fun `a value is worked out once, off the thread that saved it`() {
        val saver = Thread.currentThread()
        var workedOutOn: Thread? = null
        var times = 0
        DesktopPreferenceWriter.save(node, "queue") {
            times++
            workedOutOn = Thread.currentThread()
            "encoded"
        }

        // Watched in the store alone: asking the writer what is waiting would work it out here.
        val until = System.nanoTime() + 5_000_000_000L
        while (DesktopPreferenceChunks.read(node, "queue") != "encoded" && System.nanoTime() < until) Thread.sleep(5)
        assertEquals("encoded", DesktopPreferenceChunks.read(node, "queue"))
        assertEquals(1, times)
        assertTrue(workedOutOn != null && workedOutOn !== saver, "it was encoded on the thread that saved it")
    }
}
