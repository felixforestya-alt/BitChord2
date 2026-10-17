package com.music.bitchord.bc2

import com.music.bitchord.data.Http
import com.music.bitchord.data.spotify.SpotifyImportTrack
import com.music.bitchord.data.spotify.SpotifyImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request

/** Where a playlist being imported comes from. */
internal enum class ImportService(val title: String) {
    SPOTIFY("Spotify"),
    YANDEX("Яндекс Музыка"),
    APPLE("Apple Music"),
    DEEZER("Deezer"),
    SOUNDCLOUD("SoundCloud"),
    VK("VK Музыка"),
    TEXT("Text"),
}

/**
 * A playlist or album from another service, read as a list of title and artist — which is all the
 * import needs, since every track is then looked up on YouTube Music. Public links only: nothing
 * here signs in anywhere. Each service is read the way its own web player reads it, so a change on
 * their side can break one without touching the others.
 *
 * VK gives nothing to a visitor who is not signed in, so a VK playlist is brought over as text: a
 * track per line, "Artist — Title", which also works for any service not listed.
 */
internal object PlaylistImport {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun detect(input: String): ImportService? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (text.lines().count { it.isNotBlank() } > 1) return ImportService.TEXT
        val lower = text.lowercase()
        return when {
            SpotifyImporter.extractPlaylistId(text) != null -> ImportService.SPOTIFY
            "music.yandex." in lower -> ImportService.YANDEX
            "music.apple.com" in lower -> ImportService.APPLE
            "deezer.com" in lower || "deezer.page.link" in lower -> ImportService.DEEZER
            "soundcloud.com" in lower -> ImportService.SOUNDCLOUD
            "vk.com" in lower || "vk.ru" in lower -> ImportService.VK
            // One line that is no link: a single "Artist — Title".
            !lower.startsWith("http") && TRACK_SEPARATOR.containsMatchIn(text) -> ImportService.TEXT
            else -> null
        }
    }

    /** The playlist's name and tracks. */
    suspend fun fetch(input: String): Pair<String, List<SpotifyImportTrack>> = withContext(Dispatchers.IO) {
        val text = input.trim()
        when (detect(text)) {
            ImportService.SPOTIFY -> SpotifyImporter.fetchPlaylistTracks(SpotifyImporter.extractPlaylistId(text)!!)
            ImportService.YANDEX -> yandex(text)
            ImportService.APPLE -> apple(text)
            ImportService.DEEZER -> deezer(text)
            ImportService.SOUNDCLOUD -> soundCloud(text)
            ImportService.VK -> error(
                bc2("VK показывает плейлисты только тем, кто вошёл. Скопируй список треков (Исполнитель — Название, по одному в строке) и вставь сюда.", "VK only shows playlists to signed-in listeners. Copy the track list (Artist — Title, one per line) and paste it here instead."),
            )
            ImportService.TEXT -> bc2("Импортированные треки", "Imported tracks") to fromText(text)
            null -> error(bc2("Эта ссылка не из сервиса, который умеет импорт.", "That link isn't from a service the import knows."))
        }.also { (_, tracks) -> check(tracks.isNotEmpty()) { bc2("Там не нашлось треков.", "No tracks were found there.") } }
    }

    // ---- Text -------------------------------------------------------------------------------

    private val TRACK_SEPARATOR = Regex("""\s+[-–—]\s+""")

    /** "Artist — Title" a line; numbering and a trailing length are let go. */
    private fun fromText(text: String): List<SpotifyImportTrack> = text.lines().mapNotNull { raw ->
        val line = raw.trim()
            .replace(Regex("""^\d+[.)]\s*"""), "")
            .replace(Regex("""\s+\d{1,2}:\d{2}$"""), "")
            .trim()
        if (line.isEmpty()) return@mapNotNull null
        val parts = line.split(TRACK_SEPARATOR, limit = 2)
        if (parts.size == 2) SpotifyImportTrack(title = parts[1].trim(), artist = parts[0].trim())
        else SpotifyImportTrack(title = line, artist = "")
    }

    // ---- Deezer: an open API -------------------------------------------------------------------

    private fun deezer(link: String): Pair<String, List<SpotifyImportTrack>> {
        val resolved = if ("deezer.page.link" in link || "link.deezer.com" in link) finalUrl(link) else link
        val match = Regex("""deezer\.com/(?:[a-z]{2}/)?(playlist|album)/(\d+)""").find(resolved)
            ?: error(bc2("Это не ссылка на плейлист или альбом Deezer.", "That isn't a Deezer playlist or album link."))
        val (kind, id) = match.destructured
        val head = getJson("https://api.deezer.com/$kind/$id")
        head.o("error")?.let { error(it.s("message") ?: "Deezer: error") }
        val title = head.s("title") ?: "Deezer"
        val tracks = ArrayList<SpotifyImportTrack>()
        var next: String? = "https://api.deezer.com/$kind/$id/tracks?limit=200"
        while (next != null && tracks.size < MAX_TRACKS) {
            val page = getJson(next)
            page.a("data")?.forEach { t ->
                val name = t.s("title") ?: return@forEach
                tracks += SpotifyImportTrack(title = name, artist = t.o("artist").s("name").orEmpty())
            }
            next = page.s("next")
        }
        return title to tracks
    }

    // ---- Apple Music: the data its own page carries --------------------------------------------

    private fun apple(link: String): Pair<String, List<SpotifyImportTrack>> {
        val html = getText(link)
        val data = Regex("""<script type="application/json" id="serialized-server-data">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
            ?: error(bc2("Не получилось прочитать страницу Apple Music. Плейлист открытый?", "Couldn't read that Apple Music page. Is the playlist public?"))
        val root = json.parseToJsonElement(data)
        val tracks = LinkedHashMap<String, SpotifyImportTrack>()
        walk(root) { node ->
            val title = node.s("title")
            val artist = node.s("artistName")
            // Track rows carry a length; the page's own header has a title and artist too, but not that.
            if (title != null && artist != null && node["duration"] != null) {
                tracks.putIfAbsent("$title|$artist".lowercase(), SpotifyImportTrack(title = title, artist = artist))
            }
        }
        val name = Regex("""<meta property="og:title" content="([^"]*)"""").find(html)?.groupValues?.get(1)
            ?.let(::unescapeHtml)?.substringBefore(" by ")?.substringBefore(" - Apple Music")
            ?: "Apple Music"
        return name to tracks.values.toList()
    }

    // ---- SoundCloud: its web player's API, with the key its own scripts use ----------------------

    @Volatile private var soundCloudKey: String? = null

    private fun soundCloud(link: String): Pair<String, List<SpotifyImportTrack>> {
        val key = soundCloudKey ?: findSoundCloudKey().also { soundCloudKey = it }
        val url = if ("on.soundcloud.com" in link) finalUrl(link) else link.substringBefore('?')
        val item = getJson("https://api-v2.soundcloud.com/resolve?url=${java.net.URLEncoder.encode(url, "UTF-8")}&client_id=$key")
        val title = item.s("title") ?: "SoundCloud"
        val rows = when (item.s("kind")) {
            "track" -> listOf(item)
            "playlist", "system-playlist" -> item.a("tracks").orEmpty()
            else -> error(bc2("Это не плейлист и не трек SoundCloud.", "That isn't a SoundCloud playlist or track."))
        }
        // A set lists only its first tracks in full; the rest come as ids to be asked for.
        val full = rows.filter { it.s("title") != null }.toMutableList()
        val missing = rows.filter { it.s("title") == null }.mapNotNull { it.s("id") }
        missing.chunked(50).forEach { ids ->
            (getJsonElement("https://api-v2.soundcloud.com/tracks?ids=${ids.joinToString(",")}&client_id=$key") as? JsonArray)
                ?.forEach { full += it }
        }
        val order = rows.mapNotNull { it.s("id") }
        val byId = full.associateBy { it.s("id") }
        return title to order.mapNotNull { byId[it] }.ifEmpty { full }.map { t ->
            val artist = t.o("publisher_metadata").s("artist")?.takeIf { it.isNotBlank() } ?: t.o("user").s("username").orEmpty()
            SpotifyImportTrack(title = t.s("title").orEmpty(), artist = artist)
        }
    }

    private fun findSoundCloudKey(): String {
        val page = getText("https://soundcloud.com")
        val scripts = Regex("""https://a-v2\.sndcdn\.com/assets/[^"]+\.js""").findAll(page).map { it.value }.toList()
        for (script in scripts.asReversed()) {
            val js = runCatching { getText(script) }.getOrNull() ?: continue
            Regex("""client_id\s*[:=]\s*"([A-Za-z0-9]{32})"""").find(js)?.let { return it.groupValues[1] }
        }
        error("SoundCloud: could not find its web key")
    }

    // ---- Yandex Music: the API its apps use, which answers public playlists without a token -----

    private fun yandex(link: String): Pair<String, List<SpotifyImportTrack>> {
        val user = Regex("""music\.yandex\.[a-z]+/users/([^/]+)/playlists/(\d+)""").find(link)
        val album = Regex("""music\.yandex\.[a-z]+/album/(\d+)""").find(link)
        val uuid = Regex("""music\.yandex\.[a-z]+/playlists/([A-Za-z0-9.\-]+)""").find(link)
        val root = when {
            user != null -> getJson("https://api.music.yandex.net/users/${user.groupValues[1]}/playlists/${user.groupValues[2]}")
            album != null -> getJson("https://api.music.yandex.net/albums/${album.groupValues[1]}/with-tracks")
            uuid != null -> getJson("https://api.music.yandex.net/playlist/${uuid.groupValues[1]}")
            else -> error(bc2("Это не ссылка на плейлист или альбом Яндекс Музыки.", "That isn't a Yandex Music playlist or album link."))
        }
        root.o("error")?.let { failure ->
            val name = failure.s("name").orEmpty()
            error(
                if ("Legal" in name) {
                    bc2("Яндекс Музыка не отвечает из твоей сети (она закрыта в некоторых странах). Выключи VPN и попробуй снова.", "Yandex Music isn't answering from your network (it is closed outside some countries). Turn off a VPN and try again.")
                } else {
                    "Yandex Music: $name"
                },
            )
        }
        val result = root.o("result") ?: error("Yandex Music: empty answer")
        val title = result.s("title") ?: "Яндекс Музыка"
        val raw: List<JsonElement> = result.a("tracks")?.map { it.o("track") ?: it }
            ?: result.a("volumes")?.flatMap { (it as? JsonArray).orEmpty() }
            ?: emptyList()
        return title to raw.mapNotNull { t ->
            val name = t.s("title") ?: return@mapNotNull null
            val version = t.s("version")?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            SpotifyImportTrack(title = name + version, artist = t.a("artists").orEmpty().mapNotNull { it.s("name") }.joinToString(", "))
        }
    }

    // ---- Plumbing ------------------------------------------------------------------------------

    private const val MAX_TRACKS = 2_000
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private fun request(url: String) = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept-Language", "ru,en;q=0.8").build()

    private fun getText(url: String): String = Http.client.newCall(request(url)).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful && body.isBlank()) error("HTTP ${response.code}")
        body
    }

    private fun getJsonElement(url: String): JsonElement = json.parseToJsonElement(getText(url))

    private fun getJson(url: String): JsonObject = getJsonElement(url) as? JsonObject ?: error("unexpected answer")

    /** Where a short link leads. */
    private fun finalUrl(url: String): String = Http.client.newCall(request(url)).execute().use { it.request.url.toString() }

    private fun walk(node: JsonElement, visit: (JsonObject) -> Unit) {
        when (node) {
            is JsonObject -> { visit(node); node.values.forEach { walk(it, visit) } }
            is JsonArray -> node.forEach { walk(it, visit) }
            else -> Unit
        }
    }

    private fun unescapeHtml(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private fun JsonElement?.o(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject
    private fun JsonElement?.a(key: String): JsonArray? = (this as? JsonObject)?.get(key) as? JsonArray
    private fun JsonElement?.s(key: String): String? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull
}
