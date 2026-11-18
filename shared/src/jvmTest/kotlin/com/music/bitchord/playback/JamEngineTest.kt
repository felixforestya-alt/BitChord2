package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JamEngineTest {

    private fun song(id: String, artist: String) = Song(videoId = id, title = "t$id", artist = artist, thumbnailUrl = null)

    /** Each seed's station: 25 tracks of its own, by 25 artists of its own "genre". */
    private fun stations(): suspend (Song) -> List<Song> = { seed ->
        List(25) { i -> song("${seed.videoId}-$i", "${seed.videoId}-artist$i") }
    }

    @Test
    fun aBatchSpansManySeedsNotOne() = runBlocking {
        val seeds = List(20) { song("s$it", "seedartist$it") }
        val engine = JamEngine(stations(), Random(1))
        engine.start(seeds, familiar = emptySet(), onlyNew = false)
        val batch = engine.more(emptyList(), 10)
        assertEquals(10, batch.size)
        val seedsUsed = batch.map { it.videoId.substringBefore('-') }.toSet()
        assertTrue(seedsUsed.size >= 5, "a batch of ten came from ${seedsUsed.size} seeds")
    }

    @Test
    fun aHundredTracksKeepMovingAcrossSeeds() = runBlocking {
        val seeds = List(30) { song("s$it", "seedartist$it") }
        val engine = JamEngine(stations(), Random(2))
        engine.start(seeds, familiar = emptySet(), onlyNew = false)
        val queue = ArrayList<Song>()
        repeat(10) { queue += engine.more(queue, 10) }
        assertEquals(100, queue.size)
        // No stretch of ten tracks from fewer than four seeds' stations.
        queue.windowed(10, 10).forEach { stretch ->
            assertTrue(stretch.map { it.videoId.substringBefore('-') }.toSet().size >= 4)
        }
        assertEquals(100, queue.map { it.videoId }.toSet().size, "a track came twice")
    }

    @Test
    fun seedsAreDealtRoundArtists() = runBlocking {
        // Ten seeds by one artist and one each by five others: the first six seeds are six artists.
        val seeds = List(10) { song("a$it", "Same Singer") } + List(5) { song("b$it", "other$it") }
        val asked = ArrayList<Song>()
        val engine = JamEngine({ seed -> asked += seed; List(25) { i -> song("${seed.videoId}-$i", "${seed.videoId}-x$i") } }, Random(3))
        engine.start(seeds, emptySet(), onlyNew = false)
        engine.more(emptyList(), 10)
        val firstSix = asked.take(6).map { it.artist }.toSet()
        assertEquals(6, firstSix.size)
    }

    @Test
    fun anArtistComesOnceABatch() = runBlocking {
        val seeds = List(10) { song("s$it", "seed$it") }
        // Every station is one artist through and through.
        val engine = JamEngine({ seed -> List(25) { i -> song("${seed.videoId}-$i", "Same Band") } }, Random(4))
        engine.start(seeds, emptySet(), onlyNew = false)
        val batch = engine.more(emptyList(), 10)
        assertEquals(1, batch.size)
    }

    @Test
    fun familiarTracksAreAtMostAThirdUnlessOnlyNewLeavesThemOut() = runBlocking {
        val seeds = List(10) { song("s$it", "seed$it") }
        val familiar = seeds.flatMap { seed -> List(25) { "${seed.videoId}-$it" } }.filter { it.substringAfter('-').toInt() % 2 == 0 }.toSet()
        val mixed = JamEngine(stations(), Random(5)).apply { start(seeds, familiar, onlyNew = false) }.more(emptyList(), 12)
        assertTrue(mixed.count { it.videoId in familiar } <= 4, "too many liked tracks: ${mixed.count { it.videoId in familiar }}")
        val fresh = JamEngine(stations(), Random(5)).apply { start(seeds, familiar, onlyNew = true) }.more(emptyList(), 12)
        assertEquals(0, fresh.count { it.videoId in familiar })
        assertEquals(12, fresh.size)
    }

    @Test
    fun aSavedJamCarriesOnInAnotherProcess() = runBlocking {
        val seeds = List(8) { song("s$it", "seed$it") }
        val engine = JamEngine(stations(), Random(6))
        engine.start(seeds, setOf("x"), onlyNew = true)
        val later = JamEngine(stations(), Random(7))
        assertTrue(later.restore(engine.save()))
        assertTrue(later.active)
        assertEquals(10, later.more(emptyList(), 10).size)
        assertTrue(!JamEngine(stations()).restore(""))
    }

    @Test
    fun aStationThatComesBackEmptyIsPassedOver() = runBlocking {
        val seeds = List(12) { song("s$it", "seed$it") }
        val engine = JamEngine({ seed -> if (seed.videoId.removePrefix("s").toInt() % 2 == 0) emptyList() else List(25) { i -> song("${seed.videoId}-$i", "${seed.videoId}-a$i") } }, Random(8))
        engine.start(seeds, emptySet(), onlyNew = false)
        assertEquals(10, engine.more(emptyList(), 10).size)
    }
}
