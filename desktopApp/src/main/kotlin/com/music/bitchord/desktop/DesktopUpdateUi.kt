package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Everything the app says about updates outside Settings: the offer at launch, the note that one
 * is waiting to go in, and the word on how the last one went. Hosted once, at the app's root.
 */
@Composable
internal fun DesktopUpdatePrompts() {
    val state by DesktopUpdateChecker.state.collectAsState()
    var offering by remember { mutableStateOf(false) }
    var readyNoteDismissed by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<Result<String>?>(null) }

    LaunchedEffect(Unit) {
        report = DesktopUpdateChecker.takeLastResult()
        // Not in the first seconds, which belong to the window coming up.
        delay(LAUNCH_CHECK_DELAY_MS)
        val found = DesktopUpdateChecker.check() ?: return@LaunchedEffect
        if (DesktopUpdateSettings.autoUpdate.value && DesktopUpdateChecker.canSelfUpdate) {
            DesktopUpdateChecker.download(found)
            DesktopUpdateChecker.installWhenAppExits()
        } else if (found.version != DesktopUpdateSettings.skippedVersion.value) {
            offering = true
        }
    }

    if (offering) {
        DesktopUpdateDialog(onDismiss = { offering = false })
    }

    // Fetched on its own: a quiet note, not a dialog in the way.
    val ready = state as? DesktopUpdateChecker.UpdateState.Ready
    if (ready != null && !offering && !readyNoteDismissed) {
        UpdateNote(
            text = DesktopStrings["d_update_ready_note", "BitChord %s is downloaded and goes in when you close the app"].format(ready.info.version),
            action = DesktopStrings["d_update_restart_now", "Restart now"],
            onAction = { DesktopUpdateChecker.restartNow() },
            onDismiss = { readyNoteDismissed = true },
        )
    }

    report?.let { result ->
        result.onSuccess { version ->
            LaunchedEffect(result) {
                delay(REPORT_SHOWN_MS)
                report = null
            }
            UpdateNote(
                text = DesktopStrings["d_update_done", "Updated to BitChord %s"].format(version.ifBlank { DesktopUpdateChecker.currentVersion }),
                action = DesktopStrings["d_update_whats_new", "What's new"],
                onAction = {
                    DesktopExternalLinks.open(DesktopUpdateChecker.RELEASES_URL)
                    report = null
                },
                onDismiss = { report = null },
            )
        }.onFailure { failure ->
            AlertDialog(
                onDismissRequest = { report = null },
                title = { Text(DesktopStrings["d_update_failed_title", "The update did not go in"]) },
                text = {
                    Text(
                        DesktopStrings["d_update_failed_body", "The previous version was kept. You can download the new one from the release page."] +
                            (failure.message?.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: ""),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        DesktopExternalLinks.open(DesktopUpdateChecker.RELEASES_URL)
                        report = null
                    }) { Text(DesktopStrings["d_update_release_page", "Release page"]) }
                },
                dismissButton = {
                    TextButton(onClick = { report = null }) { Text(DesktopStrings["d_close", "Close"]) }
                },
            )
        }
    }
}

/** The offer: what is new, and the download and restart done right here, in view. */
@Composable
internal fun DesktopUpdateDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state by DesktopUpdateChecker.state.collectAsState()
    val info = when (val s = state) {
        is DesktopUpdateChecker.UpdateState.Available -> s.info
        is DesktopUpdateChecker.UpdateState.Downloading -> s.info
        is DesktopUpdateChecker.UpdateState.Preparing -> s.info
        is DesktopUpdateChecker.UpdateState.Ready -> s.info
        is DesktopUpdateChecker.UpdateState.Error -> s.info
        else -> null
    }
    if (info == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val canSelfUpdate = DesktopUpdateChecker.canSelfUpdate
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(DesktopStrings["d_update_available_version", "BitChord %s is out"].format(info.version)) },
        text = {
            Column(Modifier.widthIn(min = 360.dp, max = 520.dp)) {
                Text(
                    DesktopStrings["d_update_you_have", "You have %s."].format(DesktopUpdateChecker.currentVersion),
                    color = DesktopSecondary,
                )
                info.notes?.let { notes ->
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    ) { Text(notes, style = MaterialTheme.typography.bodyMedium) }
                }
                UpdateProgress(state)
                if (!canSelfUpdate) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        DesktopStrings["d_update_manual", "This copy cannot update itself — download the new version from the release page"],
                        color = DesktopSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            when (state) {
                is DesktopUpdateChecker.UpdateState.Ready -> Row {
                    TextButton(onClick = onDismiss) { Text(DesktopStrings["d_update_on_exit", "When I close it"]) }
                    UpdateButton(DesktopStrings["d_update_restart_now", "Restart now"]) { DesktopUpdateChecker.restartNow() }
                }
                is DesktopUpdateChecker.UpdateState.Downloading,
                is DesktopUpdateChecker.UpdateState.Preparing ->
                    // It goes on in the background; the note says when it is ready.
                    TextButton(onClick = onDismiss) { Text(DesktopStrings["d_update_in_background", "Continue in background"]) }
                else ->
                    if (canSelfUpdate && info.downloadUrl != null) {
                        UpdateButton(
                            if (state is DesktopUpdateChecker.UpdateState.Error) {
                                DesktopStrings["d_retry", "Retry"]
                            } else {
                                DesktopStrings["d_update_install", "Update"]
                            },
                        ) { scope.launch { DesktopUpdateChecker.download(info) } }
                    } else {
                        UpdateButton(DesktopStrings["d_update_release_page", "Release page"]) {
                            DesktopExternalLinks.open(info.releaseUrl)
                            onDismiss()
                        }
                    }
            }
        },
        dismissButton = {
            if (state is DesktopUpdateChecker.UpdateState.Available || state is DesktopUpdateChecker.UpdateState.Error) {
                Row {
                    TextButton(onClick = {
                        DesktopUpdateSettings.skip(info.version)
                        onDismiss()
                    }) { Text(DesktopStrings["d_update_skip", "Skip this version"]) }
                    TextButton(onClick = onDismiss) { Text(DesktopStrings["d_later", "Later"]) }
                }
            }
        },
    )
}

/** The bar and the words under the offer while it downloads, unpacks, or failed. */
@Composable
internal fun UpdateProgress(state: DesktopUpdateChecker.UpdateState) {
    when (state) {
        is DesktopUpdateChecker.UpdateState.Downloading -> {
            Spacer(Modifier.height(14.dp))
            if (state.progress >= 0f) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.15f),
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.15f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                DesktopStrings["d_update_downloading", "Downloading… %s"].format(sizeLine(state.downloadedBytes, state.totalBytes)),
                color = DesktopSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is DesktopUpdateChecker.UpdateState.Preparing -> {
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.15f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                DesktopStrings["d_update_preparing", "Checking and unpacking…"],
                color = DesktopSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is DesktopUpdateChecker.UpdateState.Ready -> {
            Spacer(Modifier.height(14.dp))
            Text(
                DesktopStrings["d_update_ready", "Downloaded and checked. It goes in when the app restarts."],
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is DesktopUpdateChecker.UpdateState.Error -> {
            Spacer(Modifier.height(14.dp))
            Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        else -> Unit
    }
}

/** A filled button that reads on the dark sheet: white, with black words. */
@Composable
internal fun UpdateButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) { Text(label, fontWeight = FontWeight.SemiBold) }
}

internal fun sizeLine(done: Long, total: Long): String {
    fun mb(bytes: Long) = "%.0f".format(bytes / 1_048_576.0)
    return if (total > 0) "${mb(done)} / ${mb(total)} MB" else "${mb(done)} MB"
}

/** A small card in the window's corner, for word that needs no answer. */
@Composable
private fun UpdateNote(text: String, action: String, onAction: () -> Unit, onDismiss: () -> Unit) {
    Popup(alignment = Alignment.BottomEnd, offset = IntOffset(-24, -24)) {
        Row(
            Modifier
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF01C1C1E))
                .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAction) { Text(action, color = Color.White, fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = onDismiss) { Text("✕", color = DesktopSecondary) }
        }
    }
}

private const val LAUNCH_CHECK_DELAY_MS = 8_000L
private const val REPORT_SHOWN_MS = 10_000L
