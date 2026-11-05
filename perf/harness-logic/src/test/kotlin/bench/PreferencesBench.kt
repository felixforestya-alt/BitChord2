package bench

import baseline.BaselineDesktopPreferenceChunks
import com.music.bitchord.desktop.DesktopPreferenceChunks
import com.music.bitchord.desktop.DesktopPreferenceWriter
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals

class PreferencesBench {
    private data class FakeSong(val id: String, val title: String, val artist: String, val thumb: String)

    private fun song(i: Int) = FakeSong(
        id = "v${i.toString().padStart(10, '0')}",
        title = "A song title number $i (Remastered)",
        artist = "Some Artist $i, Another",
        thumb = "https://lh3.googleusercontent.com/abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGH$i=w544-h544-l90-rj",
    )

    // The same shape DesktopPersistence.encodeSong writes: eighteen base64 fields, '|'-joined.
    private fun encode(s: FakeSong): String = listOf(
        s.id, s.title, s.artist, s.thumb, "3:41", "UC$i0", "MPRE", "An album", "", "",
        "false", "false", "", "false", "", "", "", "",
    ).joinToString("|") { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(StandardCharsets.UTF_8)) }
    private val i0 = "x"

    private fun realisticNode(name: String): Preferences {
        val node = Preferences.userRoot().node(name)
        node.clear()
        DesktopPreferenceChunks.write(node, "queue", (0 until 200).joinToString("\n") { encode(song(it)) })
        DesktopPreferenceChunks.write(node, "history", (0 until 100).joinToString("\n") { encode(song(it + 500)) })
        DesktopPreferenceChunks.write(node, "playlists", (0 until 10).joinToString("\n") { p -> "p$p#title$p#" + (0 until 100).joinToString(";") { encode(song(p * 100 + it)) } })
        DesktopPreferenceChunks.write(node, "liked_ids", (0 until 2000).joinToString("\n") { "v${it.toString().padStart(10, '0')}" })
        for (k in 0 until 80) node.put("setting_$k", "value $k")
        node.flush()
        return node
    }

    @Test
    fun savesOffTheUiThread() {
        val node = realisticNode("com.music.bitchord.desktop.bench.prefs")
        var v = 0
        val queue = (0 until 200).map(::song)
        val history = (0 until 50).map { song(it + 900) }
        Bench.compare(
            "one volume save during a drag (calling thread), store with a 200-track queue, 1000 playlist tracks",
            ops = 20,
            warmup = 3,
            rounds = 15,
            a = Bench.Side("main") { BaselineDesktopPreferenceChunks.write(node, "volume", "0.${v++ % 100}") },
            b = Bench.Side("branch") { DesktopPreferenceWriter.save(node, "volume") { "0.${v++ % 100}" } },
        )
        DesktopPreferenceWriter.drain()
        Bench.compare(
            "one track change: queue (200) + its index + history (50) saved (calling thread)",
            ops = 10,
            warmup = 3,
            rounds = 15,
            a = Bench.Side("main") {
                BaselineDesktopPreferenceChunks.write(node, "queue", queue.joinToString("\n") { encode(it) })
                BaselineDesktopPreferenceChunks.write(node, "queue_index", "${v++}")
                BaselineDesktopPreferenceChunks.write(node, "history", history.joinToString("\n") { encode(it) })
            },
            b = Bench.Side("branch") {
                val q = queue.toList(); val h = history.toList()
                DesktopPreferenceWriter.save(node, "queue") { q.joinToString("\n") { encode(it) } }
                DesktopPreferenceWriter.save(node, "queue_index") { "${v++}" }
                DesktopPreferenceWriter.save(node, "history") { h.joinToString("\n") { encode(it) } }
            },
        )
        DesktopPreferenceWriter.drain()
        val liked = (0 until 2000).map { "v${it.toString().padStart(10, '0')}" }.toSet()
        val disliked = (0 until 50).map { "d$it" }.toSet()
        Bench.compare(
            "one heart clicked: 2000 liked ids + 50 disliked saved (calling thread)",
            ops = 10,
            warmup = 3,
            rounds = 15,
            a = Bench.Side("main") {
                BaselineDesktopPreferenceChunks.write(node, "liked_ids", (liked + "x${v++}").toList().joinToString("\n"))
                BaselineDesktopPreferenceChunks.write(node, "disliked_ids", disliked.toList().joinToString("\n"))
            },
            b = Bench.Side("branch") {
                val l = (liked + "x${v++}").toList(); val d = disliked.toList()
                DesktopPreferenceWriter.save(node, "liked_ids") { l.joinToString("\n") }
                DesktopPreferenceWriter.save(node, "disliked_ids") { d.joinToString("\n") }
            },
        )
        DesktopPreferenceWriter.drain()
        val playlists = (0 until 10).map { p -> (0 until 100).map { song(p * 100 + it) } }
        fun line(p: Int, songs: List<FakeSong>) = "p$p#title$p#" + songs.joinToString(";") { encode(it) }
        Bench.compare(
            "one track added to a playlist: 10 playlists of 100 saved (calling thread)",
            ops = 10,
            warmup = 3,
            rounds = 15,
            a = Bench.Side("main") { BaselineDesktopPreferenceChunks.write(node, "playlists", playlists.mapIndexed { p, s -> line(p, s) }.joinToString("\n")) },
            b = Bench.Side("branch") { val saved = playlists.toList(); DesktopPreferenceWriter.save(node, "playlists") { saved.mapIndexed { p, s -> line(p, s) }.joinToString("\n") } },
        )
        DesktopPreferenceWriter.drain()
        // What the writer left is what was saved last.
        DesktopPreferenceWriter.save(node, "volume") { "0.42" }
        DesktopPreferenceWriter.drain()
        assertEquals("0.42", DesktopPreferenceChunks.read(node, "volume"))
        node.removeNode()
    }
}
