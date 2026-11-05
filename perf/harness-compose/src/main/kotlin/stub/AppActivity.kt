package stub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/** desktopUi's AppActivity and awaitAmbientFrame, as they are. */
object AppActivity {
    var quiet by mutableStateOf(false)
}

suspend fun awaitAmbientFrame(onFrame: (Long) -> Unit) {
    withFrameNanos { now -> onFrame(now) }
}
