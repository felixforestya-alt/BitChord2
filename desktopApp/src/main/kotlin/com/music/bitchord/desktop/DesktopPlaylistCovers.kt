package com.music.bitchord.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Covers of the listener's own choosing for their playlists, by browse id: a picture from this
 * computer, copied in beside the app's settings and shown wherever the playlist is — its page,
 * its card in the Library. Kept here, not sent to YouTube Music, so it is this computer's.
 */
internal object DesktopPlaylistCovers {

    private val persistence by lazy { DesktopPersistence() }

    private val directory: Path by lazy {
        val base = System.getenv("APPDATA")?.takeIf(String::isNotBlank)
            ?: System.getProperty("user.home")
        Path.of(base, "BitChord", "covers").also { runCatching { Files.createDirectories(it) } }
    }

    private val _covers = MutableStateFlow(read())

    /** Browse id to the picture's `file:` address. */
    val covers: StateFlow<Map<String, String>> = _covers

    /** The cover to show for [browseId]: the chosen one, or [fallback]. */
    fun coverFor(browseId: String?, fallback: String?): String? =
        browseId?.let { _covers.value[it] } ?: fallback

    /** Asks for a picture and makes it [browseId]'s cover; false if none was picked or it failed. */
    fun choose(browseId: String): Boolean {
        val chooser = javax.swing.JFileChooser(System.getProperty("user.home")).apply {
            dialogTitle = DesktopStrings["d_choose_cover", "Choose a cover"]
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                DesktopStrings["d_images", "Images"],
                "png", "jpg", "jpeg", "webp", "bmp", "gif",
            )
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return false
        val source = chooser.selectedFile?.toPath() ?: return false
        return runCatching {
            val extension = source.fileName.toString().substringAfterLast('.', "png").lowercase()
            // A new name each time, so nothing that cached the old picture by its address shows it.
            val target = directory.resolve("${slug(browseId)}-${System.currentTimeMillis()}.$extension")
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            forgetFile(browseId)
            save(_covers.value + (browseId to target.toUri().toString()))
            true
        }.getOrElse {
            DesktopTrackLog.log("covers: could not use ${source.fileName}: ${it.message}")
            false
        }
    }

    /** Back to the playlist's own cover. */
    fun reset(browseId: String) {
        forgetFile(browseId)
        save(_covers.value - browseId)
    }

    private fun forgetFile(browseId: String) {
        val old = _covers.value[browseId] ?: return
        runCatching { Files.deleteIfExists(Path.of(java.net.URI(old))) }
    }

    private fun slug(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)

    private fun save(value: Map<String, String>) {
        _covers.value = value
        persistence.saveString(KEY, JsonObject(value.mapValues { JsonPrimitive(it.value) }).toString())
    }

    private fun read(): Map<String, String> = runCatching {
        val text = persistence.string(KEY, "")
        if (text.isBlank()) emptyMap() else Json.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }.getOrDefault(emptyMap())

    private const val KEY = "playlist_covers"
}
