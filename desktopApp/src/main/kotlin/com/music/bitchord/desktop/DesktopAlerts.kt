package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Something went wrong that the listener would otherwise be left guessing about: the window froze,
 * the sound stopped, a track would not play. Said on screen with the reason, kept in Settings for
 * the session, and written to the log as an `ALERT` line so that a sent log shows them at a glance.
 */
internal object DesktopAlerts {

    class Alert(val at: String, val kind: String, val title: String, val detail: String?)

    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val _recent = MutableStateFlow<List<Alert>>(emptyList())
    val recent: StateFlow<List<Alert>> = _recent

    private val _notice = MutableStateFlow<Alert?>(null)
    val notice: StateFlow<Alert?> = _notice

    private val noticedAt = ConcurrentHashMap<String, Long>()

    /** What the last stream to fail said about it, for the alert that follows from it. */
    @Volatile
    var lastStreamProblem: String? = null
        // Stale, it would be given as the reason for a gap it has nothing to do with.
        get() = field?.takeIf { System.nanoTime() - lastStreamProblemAt < STREAM_PROBLEM_FRESH_NANOS }
        set(value) {
            lastStreamProblemAt = System.nanoTime()
            field = value
        }

    @Volatile
    private var lastStreamProblemAt = 0L

    /** Records an alert; with [popup], says it on screen too, once in a while for each [kind]. */
    fun raise(kind: String, title: String, detail: String? = null, popup: Boolean = true) {
        DesktopTrackLog.log("ALERT $kind: $title" + detail?.let { " ($it)" }.orEmpty())
        val alert = Alert(LocalTime.now().format(clock), kind, title, detail)
        _recent.update { (listOf(alert) + it).take(KEPT) }
        if (!popup) return
        val now = System.nanoTime()
        val last = noticedAt[kind]
        if (last != null && now - last < NOTICE_EVERY_NANOS) return
        noticedAt[kind] = now
        _notice.value = alert
    }

    fun dismiss() {
        _notice.value = null
    }

    /** The log's folder, in the file manager. */
    fun openLogFolder() {
        val folder = DesktopTrackLog.folder?.toFile() ?: return
        Thread({
            runCatching { java.awt.Desktop.getDesktop().open(folder) }
                .onFailure { DesktopTrackLog.log("could not open the log folder: ${it.message}") }
        }, "bitchord-open-log-folder").apply { isDaemon = true }.start()
    }

    private const val KEPT = 50
    private const val STREAM_PROBLEM_FRESH_NANOS = 30_000_000_000L
    private const val NOTICE_EVERY_NANOS = 30_000_000_000L
}

/** The alert on screen; a click puts it away, and it goes by itself after a few seconds. */
@Composable
internal fun DesktopAlertNotice(modifier: Modifier = Modifier) {
    val alert by DesktopAlerts.notice.collectAsState()
    LaunchedEffect(alert) {
        if (alert != null) {
            delay(NOTICE_MS)
            DesktopAlerts.dismiss()
        }
    }
    val shown = alert ?: return
    Column(
        modifier
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable { DesktopAlerts.dismiss() }
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Text(shown.title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        shown.detail?.let { Text(it, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
    }
}

/** Everything raised since the app started, newest first, for Settings. */
@Composable
internal fun DesktopAlertsHistory(title: String, subtitle: String) {
    val alerts by DesktopAlerts.recent.collectAsState()
    Column(Modifier.fillMaxWidth().padding(vertical = 13.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodyMedium)
        if (alerts.isEmpty()) {
            Text(
                DesktopStrings["d_alerts_empty", "Nothing so far"],
                color = DesktopSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        alerts.forEach { alert ->
            Column(Modifier.padding(top = 8.dp)) {
                Text("${alert.at}  ${alert.title}", style = MaterialTheme.typography.bodyMedium)
                alert.detail?.let { Text(it, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall) }
            }
        }
        TextButton(onClick = DesktopAlerts::openLogFolder, modifier = Modifier.padding(top = 4.dp)) {
            Text(DesktopStrings["d_alerts_open_log", "Open the log folder"])
        }
    }
}

private const val NOTICE_MS = 7_000L
