package bench

import com.music.bitchord.desktop.DesktopPreferenceChunks
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.prefs.Preferences
import kotlin.test.Test

/** What BitChordDesktopApp's first composition reads and decodes (DesktopPersistence's decodeSong). */
class StartupReadBench {
    private fun enc(v: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(v.toByteArray(StandardCharsets.UTF_8))
    private fun dec(v: String) = runCatching { String(Base64.getUrlDecoder().decode(v), StandardCharsets.UTF_8) }.getOrDefault("")
    private fun encodeSong(i: Int) = listOf("v$i", "A song title number $i (Remastered)", "Some Artist $i", "https://lh3.googleusercontent.com/abcdefghijklmnop$i=w544-h544", "3:41", "UCx", "MPRE", "An album", "", "", "false", "false", "", "false", "", "", "", "").joinToString("|", transform = ::enc)
    private fun decodeSong(value: String): List<String>? {
        val fields = value.split("|").map(::dec)
        return if (fields.size < 10 || fields[0].isBlank()) null else fields
    }

    @Test
    fun firstCompositionReads() {
        val node = Preferences.userRoot().node("com.music.bitchord.desktop.bench.startup")
        node.clear()
        DesktopPreferenceChunks.write(node, "queue", (0 until 200).joinToString("\n") { encodeSong(it) })
        DesktopPreferenceChunks.write(node, "history", (0 until 100).joinToString("\n") { encodeSong(it) })
        DesktopPreferenceChunks.write(node, "playlists", (0 until 10).joinToString("\n") { p -> enc("p$p") + "#" + enc("title $p") + "#" + (0 until 100).joinToString(";") { encodeSong(p * 100 + it) } })
        DesktopPreferenceChunks.write(node, "liked_ids", (0 until 2000).joinToString("\n") { "v$it" })
        node.flush()
        fun once(): Int {
            var n = 0
            n += DesktopPreferenceChunks.read(node, "queue").orEmpty().split('\n').filter(String::isNotBlank).mapNotNull(::decodeSong).size
            n += DesktopPreferenceChunks.read(node, "history").orEmpty().split('\n').filter(String::isNotBlank).mapNotNull(::decodeSong).size
            n += DesktopPreferenceChunks.read(node, "playlists").orEmpty().split('\n').filter(String::isNotBlank).sumOf { line ->
                line.split("#")[2].split(";").filter(String::isNotBlank).mapNotNull(::decodeSong).size
            }
            n += DesktopPreferenceChunks.read(node, "liked_ids").orEmpty().split('\n').filter(String::isNotBlank).toSet().size
            return n
        }
        val t0 = System.nanoTime(); val first = once(); val firstMs = (System.nanoTime() - t0) / 1e6
        repeat(20) { once() }
        val times = (0 until 15).map { val s = System.nanoTime(); once(); (System.nanoTime() - s) / 1e6 }.sorted()
        println("BENCH startup reads (200 queue, 100 history, 10x100 playlists, 2000 likes; $first rows): first in this JVM ${"%.1f".format(firstMs)} ms, warmed median ${"%.1f".format(times[7])} ms")
        node.removeNode()
    }
}
