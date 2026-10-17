package com.music.bitchord.desktop

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopUpdateCheckerTest {

    @Test
    fun `isNewer correctly compares numeric semantic versions`() {
        assertTrue(DesktopUpdateChecker.isNewer("1.0.2", "1.0.1"))
        assertTrue(DesktopUpdateChecker.isNewer("1.1.0", "1.0.1"))
        assertTrue(DesktopUpdateChecker.isNewer("2.0.0", "1.0.1"))
        assertTrue(DesktopUpdateChecker.isNewer("1.0.1", "1.0"))
        assertTrue(DesktopUpdateChecker.isNewer("1.0.1", "1.0.0"))

        assertFalse(DesktopUpdateChecker.isNewer("1.0.1", "1.0.1"))
        assertFalse(DesktopUpdateChecker.isNewer("1.0.0", "1.0.1"))
        assertFalse(DesktopUpdateChecker.isNewer("1.0", "1.0.1"))
        assertFalse(DesktopUpdateChecker.isNewer("0.9.9", "1.0.1"))
    }

    @Test
    fun `isNewer handles leading v prefixes`() {
        assertTrue(DesktopUpdateChecker.isNewer("v1.0.2", "1.0.1"))
        assertTrue(DesktopUpdateChecker.isNewer("1.0.2", "v1.0.1"))
        assertTrue(DesktopUpdateChecker.isNewer("v1.0.2", "v1.0.1"))
        assertFalse(DesktopUpdateChecker.isNewer("v1.0.1", "v1.0.1"))
    }

    @Test
    fun `isNewer treats pre-release as older than clean release of same number`() {
        assertTrue(DesktopUpdateChecker.isNewer("1.0.1", "1.0.1-beta1"))
        assertFalse(DesktopUpdateChecker.isNewer("1.0.1-beta1", "1.0.1"))
    }

    @Test
    fun `currentVersion has a valid default fallback`() {
        val version = DesktopUpdateChecker.currentVersion
        assertTrue(version.isNotBlank(), "currentVersion should not be blank")
        assertFalse(version.startsWith("v", ignoreCase = true), "version should not have leading v")
    }

    @Test
    fun `initial update state is Idle`() {
        assertEquals(DesktopUpdateChecker.UpdateState.Idle, DesktopUpdateChecker.state.value)
    }

    @Test
    fun `zip extraction extracts files with correct directory structure`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_bitchord_unzip_" + System.currentTimeMillis())
        tempDir.mkdirs()
        try {
            val zipFile = File(tempDir, "test.zip")
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("BitChord/BitChord.exe"))
                zos.write("fake-exe".toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("BitChord/app/test.jar"))
                zos.write("fake-jar".toByteArray())
                zos.closeEntry()
            }
            zipFile.writeBytes(baos.toByteArray())

            val extractDir = File(tempDir, "extracted")
            extractDir.mkdirs()

            // Invoke private unzip via reflection or verify file output
            val method = DesktopUpdateChecker::class.java.getDeclaredMethod("unzip", File::class.java, File::class.java)
            method.isAccessible = true
            method.invoke(DesktopUpdateChecker, zipFile, extractDir)

            val exe = File(extractDir, "BitChord/BitChord.exe")
            val jar = File(extractDir, "BitChord/app/test.jar")
            assertTrue(exe.exists(), "BitChord.exe must be extracted")
            assertTrue(jar.exists(), "test.jar must be extracted")
            assertEquals("fake-exe", exe.readText())
            assertEquals("fake-jar", jar.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
