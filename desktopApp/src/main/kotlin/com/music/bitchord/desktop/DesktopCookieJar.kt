package com.music.bitchord.desktop

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A `Cookie` request header, kept current with what Google answers.
 *
 * Google rotates a session's short-lived cookies (`__Secure-1PSIDTS`, `__Secure-3PSIDTS`, `SIDCC`
 * and friends) as the session is used. A browser takes every `Set-Cookie` it is handed and so stays
 * signed in; this app used to keep the header exactly as it was captured, so the copy on disk went
 * stale and the next launch was greeted with a signed-out shell — the "it logs me out every time I
 * close it" report. Everything here is about folding those answers back into the stored header.
 */
internal object DesktopCookieJar {

    /** The header as ordered name → value pairs; later duplicates win, as a browser would send. */
    fun parse(header: String): LinkedHashMap<String, String> {
        val jar = LinkedHashMap<String, String>()
        header.split(';').forEach { entry ->
            val name = entry.substringBefore('=').trim()
            val value = entry.substringAfter('=', "").trim()
            if (name.isNotEmpty() && entry.contains('=')) jar[name] = value
        }
        return jar
    }

    fun format(jar: Map<String, String>): String =
        jar.entries.joinToString("; ") { (name, value) -> "$name=$value" }

    /**
     * [header] with every applicable `Set-Cookie` in [setCookies] folded in.
     *
     * Only ever adds or replaces. A deletion is ignored on purpose: Google expires a cookie by
     * re-setting it with a past date, and a session cookie dropped here on the strength of one odd
     * response is a sign-out that a stale-but-present value would not have been.
     */
    fun merge(header: String, setCookies: List<String>, host: String = "music.youtube.com"): String {
        if (setCookies.isEmpty()) return header
        val jar = parse(header)
        var changed = false
        for (line in setCookies) {
            val update = parseSetCookie(line) ?: continue
            if (!update.appliesTo(host) || update.isDeletion) continue
            if (jar[update.name] != update.value) {
                jar[update.name] = update.value
                changed = true
            }
        }
        return if (changed) format(jar) else header
    }

    /** The names of the cookies [after] carries with a different value than [before]. */
    fun changedNames(before: String, after: String): List<String> {
        val old = parse(before)
        return parse(after).filter { (name, value) -> old[name] != value }.keys.toList()
    }

    private class SetCookie(
        val name: String,
        val value: String,
        val domain: String?,
        val maxAge: Long?,
        val expires: ZonedDateTime?,
    ) {
        val isDeletion: Boolean
            get() = value.isEmpty() ||
                value.equals("EXPIRED", ignoreCase = true) ||
                (maxAge != null && maxAge <= 0) ||
                (expires != null && expires.isBefore(ZonedDateTime.now()))

        fun appliesTo(host: String): Boolean {
            val scope = domain?.trimStart('.')?.lowercase(Locale.ROOT) ?: return true
            return host == scope || host.endsWith(".$scope")
        }
    }

    private fun parseSetCookie(line: String): SetCookie? {
        val parts = line.split(';')
        val head = parts.firstOrNull() ?: return null
        if (!head.contains('=')) return null
        val name = head.substringBefore('=').trim().takeIf { it.isNotEmpty() } ?: return null
        val value = head.substringAfter('=').trim().removeSurrounding("\"")
        var domain: String? = null
        var maxAge: Long? = null
        var expires: ZonedDateTime? = null
        for (attribute in parts.drop(1)) {
            val key = attribute.substringBefore('=').trim().lowercase(Locale.ROOT)
            val raw = attribute.substringAfter('=', "").trim()
            when (key) {
                "domain" -> domain = raw
                "max-age" -> maxAge = raw.toLongOrNull()
                "expires" -> expires = runCatching {
                    ZonedDateTime.parse(raw.replace('-', ' '), COOKIE_DATE)
                }.getOrNull()
            }
        }
        return SetCookie(name, value, domain, maxAge, expires)
    }

    /** `Wed, 21 Oct 2026 07:28:00 GMT`, with the dashes some servers still send taken out first. */
    private val COOKIE_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
}
