package com.music.bitchord.data.settings

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Covers of the listener's own choosing for their playlists, by browse id: a picture from the
 * phone, copied into the app's files and shown wherever the playlist is — its page, its Library
 * card. Kept on the device, not sent to YouTube Music.
 */
object PlaylistCovers {

    private val _covers = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Browse id to the picture's `file:` address. */
    val covers: StateFlow<Map<String, String>> = _covers

    fun init(context: Context) {
        _covers.value = runCatching {
            val text = prefs(context).getString(KEY, null) ?: return@runCatching emptyMap()
            val json = JSONObject(text)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    /** Makes the picture at [source] [browseId]'s cover; false if it could not be read. */
    suspend fun set(context: Context, browseId: String, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.filesDir, "covers").apply { mkdirs() }
            // A new name each time, so nothing that cached the old picture by its address shows it.
            val target = File(directory, "${slug(browseId)}-${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(source)!!.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            forgetFile(browseId)
            save(context, _covers.value + (browseId to Uri.fromFile(target).toString()))
            true
        }.getOrDefault(false)
    }

    /** Back to the playlist's own cover. */
    fun reset(context: Context, browseId: String) {
        forgetFile(browseId)
        save(context, _covers.value - browseId)
    }

    private fun forgetFile(browseId: String) {
        val old = _covers.value[browseId] ?: return
        runCatching { Uri.parse(old).path?.let { File(it).delete() } }
    }

    private fun slug(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)

    private fun save(context: Context, value: Map<String, String>) {
        _covers.value = value
        prefs(context).edit().putString(KEY, JSONObject(value).toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("bitchord_settings", Context.MODE_PRIVATE)

    private const val KEY = "playlist_covers"
}
