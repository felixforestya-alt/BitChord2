package com.music.bitchord.desktop

import com.music.bitchord.data.listentogether.JamInvite
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The `bitchord://party/CODE` links an invite page opens, which is how someone clicking "Listen
 * together" in a friend's Discord status lands in that friend's room.
 *
 * A link only ever fills in the join screen: the listener still confirms, so a link from anywhere
 * cannot pull them into a room or change what they are playing.
 */
internal object DesktopDeepLinks {

    class Invite(val code: String, val server: String?)

    /** An invite waiting for the window to ask about it. */
    val pending = MutableStateFlow<Invite?>(null)

    /**
     * The invite in [link], or null for anything that is not exactly one. A server is kept only if
     * it is one of the two this app knows: a link must not be able to point the app at a server of
     * its own choosing.
     */
    fun parse(link: String?): Invite? {
        val text = link?.trim().orEmpty()
        JamInvite.parse(text)?.let { return Invite(it, null) }
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (!uri.scheme.equals("bitchord", ignoreCase = true) || !uri.host.equals("party", ignoreCase = true)) return null
        val code = uri.path.orEmpty().trim('/')
        if (code.length != JamInvite.CODE_LENGTH || !code.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }) return null
        val server = uri.rawQuery.orEmpty().split('&')
            .firstOrNull { it.startsWith("server=") }
            ?.substringAfter('=')
            ?.let { runCatching { java.net.URLDecoder.decode(it, Charsets.UTF_8) }.getOrNull() }
            ?.trimEnd('/')
            ?.takeIf { it == JamInvite.BITCHORD2_ORIGIN || it == UPSTREAM_SERVER }
        return Invite(code.uppercase(), server)
    }

    /** Takes [link] in, here or from another launch, and brings the window forward for it. */
    fun open(link: String?) {
        val invite = parse(link) ?: return
        DesktopTrackLog.log("invite link opened for party ${invite.code}")
        pending.value = invite
        DesktopWindowVisibility.show()
    }

    /**
     * Makes Windows hand `bitchord://` links to this copy of the app, wherever it was unpacked. In
     * the user's own part of the registry, so no administrator is needed. Off to the side, because
     * it starts a process and the window should not wait for that.
     */
    fun registerProtocol() {
        if (!DesktopPlatform.isWindows) return
        val exe = System.getProperty("jpackage.app-path")?.takeIf(String::isNotBlank) ?: return
        Thread({
            runCatching {
                val root = "HKCU\\Software\\Classes\\bitchord"
                reg(root, "/ve", "/d", "URL:BitChord")
                reg(root, "/v", "URL Protocol", "/d", "")
                // Backslashed: an argument that starts and ends in a quote Java passes on as it
                // stands, which is not the value reg was meant to get.
                reg("$root\\shell\\open\\command", "/ve", "/d", "\\\"$exe\\\" \\\"%1\\\"")
            }.onFailure { DesktopTrackLog.log("could not register bitchord:// links: ${it.message}") }
        }, "bitchord-register-protocol").apply { isDaemon = true }.start()
    }

    private fun reg(key: String, vararg rest: String) {
        val process = ProcessBuilder(listOf("reg", "add", key) + rest + "/f")
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        process.waitFor()
    }

    private const val UPSTREAM_SERVER = DesktopListenTogether.UPSTREAM_SERVER
}
