package com.music.bitchord.desktop

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopUpdateCheckerTest {

    @Test
    fun `newer versions compare by number`() {
        assertTrue(DesktopUpdateChecker.isNewer("1.0.2", "1.0.1"))
        assertTrue(DesktopUpdateChecker.isNewer("1.1", "1.0.9"))
        assertTrue(DesktopUpdateChecker.isNewer("v2.0.0", "1.0.1"))
        assertTrue(DesktopUpdateChecker.isNewer("1.0.1", "1.0"))
        assertFalse(DesktopUpdateChecker.isNewer("1.0.1", "1.0.1"))
        assertFalse(DesktopUpdateChecker.isNewer("1.0", "1.0.1"))
        assertFalse(DesktopUpdateChecker.isNewer("0.9.9", "v1.0.1"))
    }

    @Test
    fun `a beta is older than its release`() {
        assertTrue(DesktopUpdateChecker.isNewer("1.0.1", "1.0.1-beta1"))
        assertFalse(DesktopUpdateChecker.isNewer("1.0.1-beta1", "1.0.1"))
    }

    @Test
    fun `a build without a version is never offered one`() {
        assertFalse(DesktopUpdateChecker.isNewer("99.0", "dev"))
    }

    @Test
    fun `unzips Windows-made archives and nothing outside the folder`() {
        val dir = Files.createTempDirectory("bitchord-unzip").toFile()
        try {
            val zip = File(dir, "update.zip")
            ZipOutputStream(zip.outputStream()).use { out ->
                fun put(name: String, text: String) {
                    out.putNextEntry(ZipEntry(name))
                    out.write(text.toByteArray())
                    out.closeEntry()
                }
                put("BitChord" + '\\' + "BitChord.exe", "exe")
                put("BitChord" + '\\' + "app" + '\\' + "BitChord.cfg", "cfg")
                put("BitChord/runtime/release", "rt")
                put("../escape.txt", "no")
            }
            val into = File(dir, "out")
            DesktopUpdateChecker.unzip(zip, into)
            assertEquals("exe", File(into, "BitChord/BitChord.exe").readText())
            assertEquals("cfg", File(into, "BitChord/app/BitChord.cfg").readText())
            assertEquals("rt", File(into, "BitChord/runtime/release").readText())
            assertFalse(File(dir, "escape.txt").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
