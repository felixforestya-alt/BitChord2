package com.music.bitchord.desktop

import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.data.DebugLog
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.innertube.InnerTubeXResolver
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.innertube.potoken.PoTokenGenerator
import com.music.bitchord.data.lyrics.LyricsTranslation
import com.music.bitchord.data.spotify.SpotifyLibrary
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import bitchord.desktopapp.generated.resources.Res
import bitchord.desktopapp.generated.resources.logo
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.Toolkit
import kotlin.math.roundToInt
import okio.Path.Companion.toOkioPath
import org.jetbrains.compose.resources.painterResource

fun main() {
    // Already running — in the tray, say: bring that one forward rather than start a second.
    if (!DesktopSingleInstance.claim()) kotlin.system.exitProcess(0)
    // The graphics API Skia draws through — OpenGL unless set otherwise, see DesktopGraphicsSettings.
    DesktopGraphicsSettings.apply()
    DesktopFramePacing.prepare()
    // Before the window, so skiko's first shader already goes through the disk cache.
    DesktopShaderCache.install()
    // The player's and Home's classes, loaded off the UI thread while the window comes up.
    DesktopClassPreload.start()
    // Lists build their next items between frames — see [DesktopPrefetch].
    DesktopPrefetch.install()
    // BITCHORD_FPSLOG=1: frames drawn per second, and whether the window counts as in the
    // background, every few seconds — what a still window should keep at nothing.
    if (System.getenv("BITCHORD_FPSLOG") == "1") {
        // Who writes state, by the first frame of our code (or the library's) on the writer's stack.
        val writers = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val counter = java.util.concurrent.atomic.AtomicLong()
        androidx.compose.runtime.snapshots.Snapshot.registerGlobalWriteObserver { written ->
            if (counter.incrementAndGet() % 7 != 0L) return@registerGlobalWriteObserver
            val value = runCatching { (written as? androidx.compose.runtime.State<*>)?.value }.getOrNull()
            writers.merge("#" + System.identityHashCode(written) + " " + (value?.javaClass?.simpleName ?: "?") + "=" + value.toString().take(28), 1, Int::plus)
            val stack = Thread.currentThread().stackTrace
            val site = stack.drop(2).firstOrNull { it.className.startsWith("com.music.bitchord") }
                ?: stack.drop(2).firstOrNull { !it.className.startsWith("androidx.compose.runtime") && !it.className.startsWith("java.") && !it.className.startsWith("kotlin") }
            val key = site?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" } ?: "?"
            writers.merge(key, 1, Int::plus)
        }
        Thread({
            var before = DesktopPrefetch.framesDrawn
            while (true) {
                Thread.sleep(5_000)
                val now = DesktopPrefetch.framesDrawn
                DesktopTrackLog.log("fps %.1f quiet=%s beats=%d".format((now - before) / 5.0, com.music.bitchord.ui.AppActivity.quiet, com.music.bitchord.ui.ambientBeats))
                DesktopTrackLog.log("fps writers: " + writers.entries.filterNot { it.key.startsWith("#") }.sortedByDescending { it.value }.take(6).joinToString { "${it.key} x${it.value}" })
                DesktopTrackLog.log("fps states: " + writers.entries.filter { it.key.startsWith("#") }.sortedByDescending { it.value }.take(10).joinToString { "${it.key} x${it.value}" })
                writers.clear()
                before = now
            }
        }, "bitchord-fps-log").apply { isDaemon = true }.start()
    }
    // Before any WebView exists, so the sign-in window can start over with an empty jar.
    DesktopWebCookies.install()
    DesktopWindowVisibility.install()
    // The player is the phone's, from the shared UI module; this is what it reads underneath.
    PlayerPlatform.install(DesktopPlayerHost)
    com.music.bitchord.ui.AppUi.install(DesktopAppUiHost)
    // The data layer both apps share logs through here, and keeps translated
    // lyrics beside the rest of the desktop's cache.
    DebugLog.sink = DebugLog.Sink { level, tag, message, error ->
        DesktopTrackLog.log("$tag/$level: $message" + (error?.let { " (${it.message})" } ?: ""))
    }
    LyricsTranslation.cacheDir = DesktopMediaCache.directory.toFile()
    // The Spotify library both apps share, on the tokens the web player mints in JavaFX.
    SpotifyLibrary.auth = SpotifyLibrary.Auth {
        DesktopSpotifyToken.accessToken()?.let { SpotifyLibrary.Tokens(it, DesktopSpotifyToken.clientToken()) }
    }
    // Desktop follows its own language picker (zh-Hant / zh-Hans preserved).
    com.music.bitchord.data.innertube.Innertube.appLanguage = { DesktopStrings.resolvedTag() }
    // YouTube playback is the phone's StreamResolver over InnerTubeX, with BotGuard PoTokens
    // minted in JavaFX's WebView where the phone uses Android's.
    TrackLog.echo = { level, tag, message, error ->
        DesktopTrackLog.log("$tag/$level: $message" + (error?.let { " (${it.message})" } ?: ""))
    }
    StreamResolver.maxKbps = {
        DesktopStreamClient.ceiling.get() ?: DesktopSourceRegistry.ceiling(null).maxKbps
    }
    InnerTubeXResolver.init(
        filesDir = DesktopMediaCache.directory.toFile(),
        store = DesktopInnerTubeXStore,
        poTokenProvider = PoTokenGenerator(
            createMinter = { DesktopPoTokenWebView.getNewPoTokenGenerator() },
            available = { DesktopPoTokenWebView.available },
        ).asTokenProvider(),
    )
    // Round a network that throttles YouTube, where the setting says to — see DesktopNetworkSettings.
    com.music.bitchord.data.net.DpiBypass.enabled = DesktopNetworkSettings.bypass.value
    // The public "apps open right now" count; see Presence.
    DesktopPresence.install()
    installImageLoader()
    desktopMain()
}

/**
 * Coil, which every shared card and shelf loads its artwork through, with both of its caches
 * sized on purpose. Left to its defaults it kept decoded covers in memory against a share of the
 * whole heap and nothing on disk, so the app grew as you browsed and fetched every cover again on
 * every launch. Now: a bounded set of decoded covers in memory, and the files themselves on disk,
 * so a page seen before comes back at once without the memory to show for it.
 */
private fun installImageLoader() {
    coil3.SingletonImageLoader.setSafe { context ->
        coil3.ImageLoader.Builder(context)
            // Covers through the app's own client, so they come round a throttling network too.
            .components {
                add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = { com.music.bitchord.data.Http.client }))
            }
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizeBytes(48L * 1024 * 1024)
                    .build()
            }
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(DesktopMediaCache.directory.resolve("images").toFile().toOkioPath())
                    .maxSizeBytes(300L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}

private fun desktopMain() = application {
    // No priorities raised over other programs: the app is made light enough not to need them.
    LaunchedEffect(Unit) { DesktopSchedulingBoost.boostRenderThread() }
    // Closing puts the window away rather than ending the process, while there is a tray icon to
    // bring it back from — see [DesktopWindowVisibility].
    val visible by DesktopWindowVisibility.visible.collectAsState()
    // Shown only once it has been cloaked for its warm-up — see [DesktopStartupWarmup].
    val warmupReady by DesktopStartupWarmup.ready.collectAsState()
    val state = rememberWindowState(width = 1_220.dp, height = 780.dp)
    LaunchedEffect(visible) {
        if (visible && state.isMinimized) {
            state.isMinimized = false
        }
    }
    // Hoisted so the player can fill the screen and the caption buttons can maximize — see
    // [DesktopWindowMode]. Fullscreen on Windows is a native borderless window that covers the
    // taskbar, so it needs no placement change here; only ordinary maximize does.
    val placement by DesktopWindowMode.placement.collectAsState()
    LaunchedEffect(placement) { state.placement = placement }
    // ...and back, for the times the window is moved between placements by something that is not
    // us; see [DesktopWindowMode.adopt].
    LaunchedEffect(state) {
        snapshotFlow { state.placement }.collect(DesktopWindowMode::adopt)
    }
    val maximized by DesktopWindowMode.maximized.collectAsState()
    LaunchedEffect(maximized) {
        if (DesktopPlatform.isMac) {
            DesktopMacFrame.updateCornerRadius(maximized)
        }
    }
    Window(
        onCloseRequest = { if (DesktopWindowVisibility.onCloseRequest()) exitApplication() },
        visible = visible && warmupReady,
        title = "BitChord",
        icon = painterResource(Res.drawable.logo),
        state = state,
        // No system title bar on Windows, where the application draws its own instead — see
        // [DesktopWindowChrome].
        undecorated = DesktopPlatform.drawsOwnWindowFrame,
        // For Mica and Acrylic behind the sidebar and top bar: only a transparent window lets
        // DWM's material show through the chrome. Fixed at creation, so it is on wherever the
        // material is possible and the chosen one can be switched live — see [DesktopWindowBackdrop].
        transparent = DesktopWindowBackdrop.available,
        resizable = true,
    ) {
        val composeWindow = window
        val openingSize = remember { state.size }
        // Before anything else, while the window is still hidden: its handle, cloaked.
        LaunchedEffect(composeWindow) { DesktopStartupWarmup.prepare(composeWindow) }
        LaunchedEffect(composeWindow) {
            DesktopWindowVisibility.attachWindow(composeWindow)
            // Whether anyone is looking — see DesktopActivityWatch.
            DesktopActivityWatch.attach(composeWindow)
            DesktopFramePacing.attach(composeWindow)
            this.launch { DesktopGpuCache.attach(composeWindow) }
            DesktopMemoryTrim.watch(composeWindow)
            // AWT's default is white, and it is what shows for the frame or two a moved or resized
            // window takes to repaint: a white band along its edges.
            // Not over a transparent window, whose clear background is what the material shows
            // through; DWM moves that window, so there is no band to hide.
            if (!DesktopWindowBackdrop.available) {
                composeWindow.background = java.awt.Color.BLACK
                composeWindow.contentPane.background = java.awt.Color.BLACK
            } else {
                composeWindow.background = java.awt.Color(0, 0, 0, 0)
                composeWindow.contentPane.background = java.awt.Color(0, 0, 0, 0)
                (composeWindow.contentPane as? javax.swing.JComponent)?.isOpaque = false
                composeWindow.rootPane.isOpaque = false
            }
            // AWT measures this in device pixels while Compose's window state is in dp. Keeping
            // the scale in the conversion makes the usable minimum consistent on every display.
            val transform = composeWindow.graphicsConfiguration.defaultTransform
            composeWindow.minimumSize = Dimension(
                (MIN_WINDOW_WIDTH_DP * transform.scaleX).roundToInt(),
                (MIN_WINDOW_HEIGHT_DP * transform.scaleY).roundToInt(),
            )
            // Setting AWT's minimum during the first composition can resize the native peer to
            // that minimum without updating Compose's state. Compose would then keep laying out a
            // 1220dp surface inside a 900dp window, cropping the right edge and putting its resize
            // boundary off-screen until a maximize/restore cycle synchronised the two. Re-assert
            // the requested opening size on the native peer so both layers start in agreement.
            composeWindow.size = Dimension(
                (openingSize.width.value * transform.scaleX).roundToInt(),
                (openingSize.height.value * transform.scaleY).roundToInt(),
            )
            // What draws the window — the GPU, or the CPU when Skia could not get a device — and
            // at what rate the screen asks for frames: the first thing to know about smoothness.
            DesktopTrackLog.log(
                "render: ${composeWindow.renderApi}, " +
                    "${composeWindow.graphicsConfiguration.device.displayMode.refreshRate} Hz, " +
                    "${composeWindow.graphicsConfiguration.device.displayMode.width}x" +
                    "${composeWindow.graphicsConfiguration.device.displayMode.height}",
            )
            if (DesktopPlatform.isWindows && DesktopWindowsFrame.install("BitChord")) {
                DesktopWindowBackdrop.apply()
            } else if (DesktopPlatform.isMac && DesktopMacFrame.install(composeWindow)) {
                DesktopWindowBackdrop.apply()
            }
        }
        // The way out an update's restart takes, so the app ends as it always does.
        LaunchedEffect(Unit) { DesktopUpdateChecker.exitApp = ::exitApplication }
        val actions = remember {
            DesktopWindowActions(
                minimize = {
                    if (!DesktopWindowsFrame.minimize()) state.isMinimized = true
                },
                toggleMaximize = {
                    if (!DesktopWindowsFrame.toggleMaximize()) {
                        // An undecorated AWT window otherwise maximizes to the monitor bounds on
                        // Windows when the native frame bridge is unavailable. Give AWT the
                        // monitor's usable work area before Compose switches the placement.
                        if (DesktopPlatform.isWindows && !DesktopWindowMode.maximized.value) {
                            val screen = composeWindow.graphicsConfiguration.bounds
                            val insets = Toolkit.getDefaultToolkit().getScreenInsets(
                                composeWindow.graphicsConfiguration,
                            )
                            composeWindow.maximizedBounds = Rectangle(
                                screen.x + insets.left,
                                screen.y + insets.top,
                                screen.width - insets.left - insets.right,
                                screen.height - insets.top - insets.bottom,
                            )
                        }
                        DesktopWindowMode.toggleMaximized()
                    }
                },
                // The same door the system's close button went through, so the tray keeps the
                // process alive exactly as it did before.
                close = { if (DesktopWindowVisibility.onCloseRequest()) exitApplication() },
            )
        }
        CompositionLocalProvider(
            LocalDesktopWindowActions provides actions,
            LocalDesktopWindowScope provides this,
        ) {
            BitChordDesktopApp()
        }
    }
}

private const val MIN_WINDOW_WIDTH_DP = 900.0
private const val MIN_WINDOW_HEIGHT_DP = 600.0
