package model

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A model of BitChordDesktopApp's download reading (DesktopApp.kt): the root collected the whole
 * DesktopDownloadQueue.active map and kept its keys; the sidebar collected the map for its size.
 */
class DownloadsRootTest {
    data class Item(val id: String, val fraction: Float)

    private val active = MutableStateFlow<Map<String, Item>>(emptyMap())
    private var root = 0
    private var sidebar = 0
    private var page = 0

    @Composable
    private fun Page(downloadInProgress: Set<String>) {
        SideEffect { page++ }
        Text("downloading ${downloadInProgress.size}")
    }

    @Composable
    private fun SidebarBefore() {
        SideEffect { sidebar++ }
        val queued by active.collectAsState()
        Text(if (queued.isEmpty()) "Downloads" else "Downloads · ${queued.size}")
    }

    @Composable
    private fun SidebarAfter() {
        SideEffect { sidebar++ }
        val queued by remember { active.map { it.size }.distinctUntilChanged() }.collectAsState(active.value.size)
        Text(if (queued == 0) "Downloads" else "Downloads · $queued")
    }

    @Composable
    private fun RootBefore() {
        SideEffect { root++ }
        val downloadQueue by active.collectAsState()
        val downloadInProgress = downloadQueue.keys
        Column { SidebarBefore(); Page(downloadInProgress) }
    }

    @Composable
    private fun RootAfter() {
        SideEffect { root++ }
        val downloadInProgress by remember { active.map { it.keys }.distinctUntilChanged() }.collectAsState(active.value.keys)
        Column { SidebarAfter(); Page(downloadInProgress) }
    }

    /** Two tracks downloading over 300 frames, five 8 KB progress steps a frame, then done. */
    private fun session(content: @Composable () -> Unit): Triple<IntArray, ByteArray, ByteArray> {
        root = 0; sidebar = 0; page = 0
        active.value = emptyMap()
        val frames = 302
        val times = LongArray(frames) { it * 5_555_555L }
        val run = runFrames(240, 80, times, keep = setOf(150, 301), before = { index, _ ->
            when (index) {
                1 -> active.value = mapOf("a" to Item("a", 0f), "b" to Item("b", 0f))
                301 -> active.value = emptyMap()
                else -> if (index in 2..300) repeat(5) { step -> active.update { it + ("a" to Item("a", (index * 5 + step) / 1500f)) } }
            }
        }, content = content)
        return Triple(intArrayOf(root, sidebar, page), run.pngs[0], run.pngs[1])
    }

    @Test
    fun progressNoLongerRecomposesTheRoot() {
        val (before, beforeMid, beforeEnd) = session { RootBefore() }
        val (after, afterMid, afterEnd) = session { RootAfter() }
        println("MODEL downloads, 302 frames, 1495 progress steps: compositions root/sidebar/page before=${before.toList()} after=${after.toList()}")
        assertTrue(beforeMid.contentEquals(afterMid) && beforeEnd.contentEquals(afterEnd), "what is drawn differs")
        assertTrue(after[0] < before[0] / 10)
    }
}
