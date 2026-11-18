package com.music.bitchord.playback

import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JamEngineTest {

    private fun song(id: String, artist: String) = Song(videoId = id, title = "t$id", artist = artist, thumbnailUrl = null)

    /** Each seed's station: 25 tracks of its own, by 25 artists of its own. */
    private fun stations(): suspend (Song) -> List<Song> = { seed ->
        List(25) { i -> song("${seed.videoId}-$i", "${seed.videoId}-artist$i") }
    }

    private suspend fun JamEngine.songs(existing: List<Song>, limit: Int) = more(existing, limit).map { it.song }

    @Test
    fun aBatchSpansManySeedsNotOne() = runBlocking {
        val seeds = List(20) { song("s$it", "seedartist$it") }
        val engine = JamEngine(stations(), Random(1))
        engine.start(seeds, familiar = emptySet(), familiarShare = 1.0 / 3)
        val batch = engine.songs(emptyList(), 10)
        assertEquals(10, batch.size)
        val seedsUsed = batch.map { it.videoId.substringBefore('-') }.toSet()
        assertTrue(seedsUsed.size >= 5, "a batch of ten came from ${seedsUsed.size} seeds")
    }

    @Test
    fun aHundredTracksKeepMovingAcrossSeeds() = runBlocking {
        val seeds = List(30) { song("s$it", "seedartist$it") }
        val engine = JamEngine(stations(), Random(2))
        engine.start(seeds, familiar = emptySet(), familiarShare = 1.0 / 3)
        val queue = ArrayList<Song>()
        repeat(10) { queue += engine.songs(queue, 10) }
        assertEquals(100, queue.size)
        queue.windowed(10, 10).forEach { stretch ->
            assertTrue(stretch.map { it.videoId.substringBefore('-') }.toSet().size >= 4)
        }
        assertEquals(100, queue.map { it.videoId }.toSet().size, "a track came twice")
    }

    @Test
    fun seedsAreDealtRoundArtists() = runBlocking {
        val seeds = List(10) { song("a$it", "Same Singer") } + List(5) { song("b$it", "other$it") }
        val asked = ArrayList<Song>()
        val engine = JamEngine({ seed -> synchronized(asked) { asked += seed }; List(25) { i -> song("${seed.videoId}-$i", "${seed.videoId}-x$i") } }, Random(3))
        engine.start(seeds, emptySet(), familiarShare = 1.0 / 3)
        engine.more(emptyList(), 10)
        // The first seeds dealt are one of each artist before any second of the one singer.
        val firstDealt = engine.save().split('\n').drop(2).filter { it.isNotBlank() }.take(6).map { it.split('\t')[2] }.toSet()
        assertEquals(6, firstDealt.size)
    }

    @Test
    fun anArtistComesOnceABatch() = runBlocking {
        val seeds = List(10) { song("s$it", "seed$it") }
        val engine = JamEngine({ seed -> List(25) { i -> song("${seed.videoId}-$i", "Same Band") } }, Random(4))
        engine.start(seeds, emptySet(), familiarShare = 1.0 / 3)
        assertEquals(1, engine.songs(emptyList(), 10).size)
    }

    @Test
    fun theFamiliarShareHolds() = runBlocking {
        val seeds = List(10) { song("s$it", "seed$it") }
        val familiar = seeds.flatMap { seed -> List(25) { "${seed.videoId}-$it" } }.filter { it.substringAfter('-').toInt() % 2 == 0 }.toSet()
        suspend fun count(share: Double) = JamEngine(stations(), Random(5)).apply { start(seeds, familiar, share) }.songs(emptyList(), 12).count { it.videoId in familiar }
        assertTrue(count(1.0 / 3) <= 4)
        assertEquals(0, count(0.0))
        assertTrue(count(2.0 / 3) in 5..8, "favourites gave ${count(2.0 / 3)} familiar of 12")
    }

    @Test
    fun oneSoundDoesNotFillABatch() = runBlocking {
        // Ten seeds whose stations share their artists — one sound — and four of another each.
        val sameSound = List(10) { song("a$it", "a-seed$it") }
        val others = List(4) { song("b$it", "b-seed$it") }
        val engine = JamEngine({ seed ->
            if (seed.videoId.startsWith("a")) {
                List(25) { i -> song("${seed.videoId}-$i", "a-band${(i + seed.videoId.drop(1).toInt()) % 30}") }
            } else {
                List(25) { i -> song("${seed.videoId}-$i", "${seed.videoId}-artist$i") }
            }
        }, Random(9))
        engine.start(sameSound + others, emptySet(), familiarShare = 1.0 / 3)
        val queue = ArrayList<Song>()
        repeat(3) { queue += engine.songs(queue, 10) }
        val fromB = queue.count { it.videoId.startsWith("b") }
        assertTrue(fromB >= 12, "the other sounds gave $fromB of 30")
        assertTrue(engine.sounds >= 2)
    }

    @Test
    fun aSavedJamCarriesOnInAnotherProcess() = runBlocking {
        val seeds = List(8) { song("s$it", "seed$it") }
        val engine = JamEngine(stations(), Random(6))
        engine.start(seeds, setOf("x"), familiarShare = 0.0)
        val later = JamEngine(stations(), Random(7))
        assertTrue(later.restore(engine.save()))
        assertTrue(later.active)
        assertEquals(10, later.songs(emptyList(), 10).size)
        assertFalse(JamEngine(stations()).restore(""))
        // The first form, from before familiar shares.
        assertTrue(JamEngine(stations()).restore("jam1\t1\t0\n\ns1\tt\ta\t\n"))
    }

    @Test
    fun aStationThatComesBackEmptyIsPassedOver() = runBlocking {
        val seeds = List(12) { song("s$it", "seed$it") }
        val engine = JamEngine({ seed -> if (seed.videoId.removePrefix("s").toInt() % 2 == 0) emptyList() else List(25) { i -> song("${seed.videoId}-$i", "${seed.videoId}-a$i") } }, Random(8))
        engine.start(seeds, emptySet(), familiarShare = 1.0 / 3)
        assertEquals(10, engine.songs(emptyList(), 10).size)
    }

    private class MemoryStore : JamStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key].orEmpty()
        override fun put(key: String, value: String) { map[key] = value }
    }

    private fun core(store: JamStore, clock: () -> Long = { 0L }) = JamCore(
        store = store,
        stationOf = stations(),
        radioName = { "My Jam" },
        reason = { seed -> "My Jam · like ${seed.title}" },
        now = clock,
    )

    @Test
    fun eachTrackSaysWhichOfTheListenersItCameFrom() = runBlocking {
        val seeds = List(10) { song("s$it", "seed$it") }
        val batch = core(MemoryStore()).start(seeds, emptySet(), JamDiscovery.BALANCED, 10)
        assertEquals(10, batch.size)
        batch.forEach { track ->
            assertEquals("My Jam", track.radioName)
            assertEquals("My Jam · like t${track.videoId.substringBefore('-')}", track.playbackSource)
        }
    }

    @Test
    fun whatTheJamPlayedLatelyIsNotPlayedAgain() = runBlocking {
        val store = MemoryStore()
        var clock = 1_000_000_000L
        val seeds = List(3) { song("s$it", "seed$it") }
        val first = core(store) { clock }.start(seeds, emptySet(), JamDiscovery.BALANCED, 10).map { it.videoId }.toSet()
        // A new jam the next day, from the same three tracks, in a new process.
        clock += 24L * 60 * 60 * 1000
        val second = core(store) { clock }.start(seeds, emptySet(), JamDiscovery.BALANCED, 10).map { it.videoId }.toSet()
        assertTrue((first intersect second).isEmpty(), "played again: ${first intersect second}")
        // Five days on, they may come back.
        clock += 5L * 24 * 60 * 60 * 1000
        val later = core(store) { clock }.start(seeds, emptySet(), JamDiscovery.BALANCED, 10).map { it.videoId }.toSet()
        assertTrue((first intersect later).isNotEmpty())
    }

    @Test
    fun aDislikedArtistLeavesTheJamAndASkippedTrackDoesNotComeBack() = runBlocking {
        val store = MemoryStore()
        val seeds = List(10) { song("s$it", "seed$it") }
        val jam = core(store)
        val batch = jam.start(seeds, emptySet(), JamDiscovery.BALANCED, 10)
        val disliked = batch[0]
        jam.onRated(disliked, LikeStatus.DISLIKE)
        assertTrue(jam.unwanted(song("other", disliked.artist)))
        val skipped = batch[1]
        jam.onSkipped(skipped)
        assertTrue(jam.unwanted(skipped))
        // Kept for the next run.
        assertTrue(core(store).unwanted(skipped))
    }

    @Test
    fun aHeartGrowsTheNextBatchFromThatTrack() = runBlocking {
        val seeds = List(20) { song("s$it", "seed$it") }
        val jam = core(MemoryStore())
        val batch = jam.start(seeds, emptySet(), JamDiscovery.BALANCED, 10)
        val loved = song("loved", "someone new")
        jam.onRated(loved, LikeStatus.LIKE)
        val next = jam.more(batch, 10)
        assertTrue(next.any { it.videoId.startsWith("loved-") }, "nothing from the liked track: ${next.map { it.videoId }}")
    }

    @Test
    fun aBatchIsOrderedWithoutSuddenJumpsInEnergy() = runBlocking {
        val energy = mapOf("a" to 0.1f, "b" to 0.9f, "c" to 0.2f, "d" to 0.8f, "e" to 0.5f)
        val jam = JamCore(
            store = MemoryStore(),
            stationOf = stations(),
            radioName = { "My Jam" },
            reason = { "" },
            energyOf = { energy[it.videoId] },
        )
        val seed = song("seed", "x")
        val picks = listOf("a", "b", "unknown", "c", "d", "e").map { JamPick(song(it, it), seed) }
        val ordered = jam.smooth(picks, from = 0.12f).map { it.song.videoId }
        // Up from where the track playing now is, without leaps; the unheard one keeps its place.
        assertEquals(listOf("a", "c", "unknown", "e", "d", "b"), ordered)
        // Nothing known: left as picked.
        assertEquals(picks, JamCore(MemoryStore(), stations(), { "" }, { "" }).smooth(picks, 0.5f))
    }

    @Test
    fun aBatchCostsLittle() = runBlocking {
        // Every station already fetched, as for a jam that has been playing a while: what is left
        // is the engine's own work, which must be next to nothing beside a network request.
        val seeds = List(40) { song("s$it", "seed$it") }
        val jam = JamCore(MemoryStore(), stations(), { "My Jam" }, { "x" }, energyOf = { it.videoId.hashCode() % 100 / 100f })
        val queue = ArrayList<Song>()
        queue += jam.start(seeds, seeds.mapTo(HashSet()) { it.videoId }, JamDiscovery.BALANCED, 10)
        repeat(20) { queue += jam.more(queue, 10) }
        val threads = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val bytes0 = threads.currentThreadAllocatedBytes
        val t0 = System.nanoTime()
        repeat(20) { queue += jam.more(queue.takeLast(200), 10) }
        val perBatchMs = (System.nanoTime() - t0) / 20 / 1e6
        val perBatchKb = (threads.currentThreadAllocatedBytes - bytes0) / 20 / 1024
        println("BENCH jam batch of 10: %.2f ms, %d KB".format(perBatchMs, perBatchKb))
        assertTrue(perBatchMs < 20, "a batch took $perBatchMs ms")
    }

    @Test
    fun theEarLearnsATracksEnergyAndItsArtists() {
        val store = MemoryStore()
        var loud = 0.8f
        val ear = JamEnergy(store, level = { loud }, isJam = { it.radioName == "My Jam" })
        val track = song("x", "Loud Band").copy(radioName = "My Jam")
        ear.nowPlaying(track)
        repeat(JamEnergy.MIN_SAMPLES) { ear.sample(); loud = if (loud == 0.8f) 0.7f else 0.8f }
        ear.nowPlaying(null)
        val heard = ear.of(track)!!
        assertTrue(heard in 0.5f..0.9f, "heard $heard")
        // A track of theirs never played is placed by the artist; another artist's is not known.
        assertEquals(heard, JamEnergy(store, { null }, { true }).of(song("y", "Loud Band")))
        assertEquals(null, ear.of(song("z", "Someone Else")))
        // Not a jam track, or not heard long enough: nothing kept.
        val short = song("s", "Quiet").copy(radioName = "My Jam")
        ear.nowPlaying(short)
        repeat(5) { ear.sample() }
        ear.nowPlaying(null)
        assertEquals(null, ear.of(short))
    }

    @Test
    fun theLevelMeterCostsNextToNothing() {
        val block = FloatArray(2048) { kotlin.math.sin(it * 0.05).toFloat() * 0.5f }
        repeat(20_000) { LevelMeter.feed(block, block.size) }
        LevelMeter.take()
        val threads = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val bytes0 = threads.currentThreadAllocatedBytes
        val t0 = System.nanoTime()
        val blocks = 100_000
        repeat(blocks) { LevelMeter.feed(block, block.size) }
        val perBlockUs = (System.nanoTime() - t0) / blocks / 1e3
        val allocated = threads.currentThreadAllocatedBytes - bytes0
        val level = LevelMeter.take()!!
        // A 1024-frame stereo block is 21 ms of sound at 48 kHz.
        println("BENCH level meter: %.2f us per 1024-frame stereo block (%.4f%% of one core), %d bytes allocated, level %.2f".format(perBlockUs, perBlockUs / 21_333.0 * 100, allocated, level))
        assertTrue(level in 0.5f..1f)
        assertTrue(perBlockUs < 50)
    }
}
