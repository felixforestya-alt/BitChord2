package com.music.bitchord.desktop

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The script that swaps a new build in, run for real against folders made to look like builds. */
class DesktopUpdateInstallScriptTest {

    private val windows = System.getProperty("os.name").contains("Windows", ignoreCase = true)

    private fun build(dir: File, tag: String, extra: String) {
        File(dir, "app").mkdirs()
        File(dir, "runtime/bin").mkdirs()
        File(dir, "BitChord.exe").writeText("exe $tag")
        File(dir, "app/BitChord.cfg").writeText("cfg $tag")
        File(dir, "app/$extra.jar").writeText("jar $tag")
        File(dir, "runtime/bin/java.dll").writeText("rt $tag")
    }

    private fun run(root: File, src: File, dst: File): String {
        val script = File(root, "install.ps1").apply { writeText(DesktopUpdateChecker.INSTALL_SCRIPT) }
        val result = File(root, "result.txt")
        val process = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.path,
            // A process long gone: the script does not wait.
            "-ProcId", "999999", "-Src", src.path, "-Dst", dst.path, "-Backup", File(root, "backup").path,
            "-Relaunch", "0", "-Result", result.path, "-Version", "9.9",
        ).redirectErrorStream(true).start()
        process.inputStream.readAllBytes()
        assertTrue(process.waitFor(120, TimeUnit.SECONDS))
        return result.readText().trim().removePrefix("﻿")
    }

    @Test
    fun `a new build is mirrored in and the old one kept aside`() {
        if (!windows) return
        val root = Files.createTempDirectory("bitchord-install").toFile()
        try {
            val dst = File(root, "Мой BitChord").also { build(it, "old", "gone") }
            val src = File(root, "staged/BitChord").also { build(it, "new", "added") }
            File(dst, "notes.txt").writeText("the listener's own file")

            assertEquals("ok 9.9", run(root, src, dst))
            assertEquals("exe new", File(dst, "BitChord.exe").readText())
            assertEquals("cfg new", File(dst, "app/BitChord.cfg").readText())
            assertEquals("rt new", File(dst, "runtime/bin/java.dll").readText())
            assertTrue(File(dst, "app/added.jar").exists())
            // What the new build no longer has goes, from the folders the build owns...
            assertFalse(File(dst, "app/gone.jar").exists())
            // ...but nothing beside them is touched.
            assertEquals("the listener's own file", File(dst, "notes.txt").readText())
            assertEquals("cfg old", File(root, "backup/app/BitChord.cfg").readText())
            assertFalse(src.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a broken payload leaves the old build as it was`() {
        if (!windows) return
        val root = Files.createTempDirectory("bitchord-install").toFile()
        try {
            val dst = File(root, "BitChord").also { build(it, "old", "kept") }
            val src = File(root, "staged/BitChord").also { build(it, "new", "added") }
            File(src, "runtime").deleteRecursively()

            assertTrue(run(root, src, dst).startsWith("failed"))
            assertEquals("exe old", File(dst, "BitChord.exe").readText())
            assertEquals("cfg old", File(dst, "app/BitChord.cfg").readText())
            assertEquals("rt old", File(dst, "runtime/bin/java.dll").readText())
            assertTrue(File(dst, "app/kept.jar").exists())
            assertFalse(File(dst, "app/added.jar").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
