package com.music.bitchord.desktop

/**
 * The classes behind the player, Home and the lyrics, loaded on a background thread at launch.
 *
 * The JVM loads a class the first time code needs it, on whatever thread that code runs — for a
 * composable, the UI thread, inside a frame. The first time a track's lyrics arrived the card's
 * lyric line, its translate and romanise buttons and the player's words each brought a handful of
 * classes in mid-frame, a few milliseconds apiece. Loaded here first (defined only, not
 * initialised, so nothing of theirs runs early), they are already there when wanted. Only these
 * parts of the app, not all of it: each class loaded is memory held, and the rest of the app is
 * either loaded at launch anyway or rarely used.
 */
internal object DesktopClassPreload {

    /** Where in the app's jars the classes worth having early live. */
    private val PREFIXES = listOf(
        "com/music/bitchord/ui/player/",
        "com/music/bitchord/ui/components/",
        "com/music/bitchord/ui/screens/HomeScreen",
        "com/music/bitchord/desktop/DesktopHome",
        "com/music/bitchord/desktop/DesktopDiscPlayer",
        "com/music/bitchord/desktop/DesktopPlayerSheet",
        "com/music/bitchord/desktop/DesktopNowPlayingBar",
        "com/music/bitchord/desktop/DesktopVisualizers",
        "com/music/bitchord/desktop/DesktopLiquidGlass",
        "com/music/bitchord/desktop/DesktopHomeCustomize",
        "com/music/bitchord/desktop/DesktopMusicVideo",
        "com/music/bitchord/data/lyrics/",
    )

    fun start() {
        Thread({
            val started = System.nanoTime()
            var loaded = 0
            val loader = DesktopClassPreload::class.java.classLoader
            System.getProperty("java.class.path").orEmpty().split(java.io.File.pathSeparatorChar)
                .filter { path ->
                    val name = java.io.File(path).name
                    name.startsWith("desktopApp") || name.startsWith("desktopUi") || name.startsWith("shared")
                }
                .forEach { path ->
                    runCatching {
                        java.util.jar.JarFile(path).use { jar ->
                            val entries = jar.entries()
                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement().name
                                if (!entry.endsWith(".class") || PREFIXES.none(entry::startsWith)) continue
                                val className = entry.removeSuffix(".class").replace('/', '.')
                                if (runCatching { Class.forName(className, false, loader) }.isSuccess) loaded++
                            }
                        }
                    }
                }
            DesktopTrackLog.log("class preload: %d classes in %d ms".format(loaded, (System.nanoTime() - started) / 1_000_000))
        }, "bitchord-class-preload").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }
}
