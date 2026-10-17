package com.music.bitchord.desktop

import com.sun.jna.NativeLibrary
import com.sun.jna.WString

/**
 * Skia's compiled shaders, kept on disk between launches — see `native/shadercache`.
 *
 * Direct3D's HLSL compiler ran on the drawing thread every time Skia met a kind of draw it had not
 * compiled yet in this run, tens of milliseconds each: the hitches of a first opening, a first
 * scroll to the bottom of Home, a first track of another kind. With the cache, a shader is
 * compiled once on this computer and read back every time after, so only the very first launch
 * ever pays — and the startup warm-up behind the cloak takes most of that too.
 */
internal object DesktopShaderCache {

    @Volatile private var library: NativeLibrary? = null

    /** Puts the cache in front of skiko's compiler. Before the first window, or it is too late. */
    fun install() {
        if (!DesktopPlatform.isWindows || System.getenv("BITCHORD_NO_SHADER_CACHE") == "1") return
        runCatching {
            // Skiko's library first: the hook goes into its import table.
            org.jetbrains.skiko.Library.load()
            val native = NativeLibrary.getInstance(DesktopAnalysisRuntime.unpackNative("bitchord_shadercache").toString())
            val directory = DesktopMediaCache.directory.resolve("shaders-$SKIKO").toAbsolutePath()
            java.nio.file.Files.createDirectories(directory)
            val installed = native.getFunction("bitchord_shadercache_install")
                .invokeInt(arrayOf(WString("skiko-windows-x64.dll"), WString(directory.toString())))
            library = native
            DesktopTrackLog.log(if (installed == 1) "shader cache: on, in $directory" else "shader cache: skiko's import not found, off")
        }.onFailure { DesktopTrackLog.log("shader cache: unavailable: ${it.message}") }
    }

    /** How many shaders came from the disk and how many had to be compiled, this run. */
    fun counts(): Pair<Long, Long>? = library?.let {
        it.getFunction("bitchord_shadercache_hits").invokeLong(emptyArray()) to
            it.getFunction("bitchord_shadercache_misses").invokeLong(emptyArray())
    }

    /** Kept apart per Skia build: another Skia writes other shaders. */
    private const val SKIKO = "0.9.37.4"
}
