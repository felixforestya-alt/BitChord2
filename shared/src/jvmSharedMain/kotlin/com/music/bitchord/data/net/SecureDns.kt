package com.music.bitchord.data.net

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Looking host names up over DNS over HTTPS (RFC 8484), with a server the user picks: the answer
 * comes encrypted from that server, not from whatever the network's own resolver says.
 *
 * [Provider.SYSTEM] leaves lookups to the system — unless [DpiBypass] is on, which has always
 * looked names up over HTTPS and keeps doing so with Cloudflare and Google. Whatever is chosen,
 * a lookup nobody answers falls back to the system's, so a server that is down never leaves the
 * app with no addresses at all.
 */
object SecureDns {

    /** A DoH server: its address, and the IPs to reach it by without a lookup of its own. */
    enum class Provider(val url: String?, val bootstrap: List<String>) {
        SYSTEM(null, emptyList()),
        CLOUDFLARE("https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
        GOOGLE("https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
        QUAD9("https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
        ADGUARD("https://dns.adguard-dns.com/dns-query", listOf("94.140.14.14", "94.140.15.15")),
        /** [customUrl]: any server that speaks RFC 8484. */
        CUSTOM(null, emptyList()),
    }

    @Volatile
    var provider: Provider = Provider.SYSTEM
        set(value) {
            if (field != value) cache.clear()
            field = value
        }

    /** The server for [Provider.CUSTOM], as `https://host/dns-query`. */
    @Volatile
    var customUrl: String = ""
        set(value) {
            if (field != value) cache.clear()
            field = value.trim()
        }

    /** For [OkHttpClient.Builder.dns]. */
    val dns: Dns = Dns { host -> lookup(host) }

    private val cache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()

    fun lookup(host: String): List<InetAddress> {
        val servers = servers()
        // Addresses already, and this machine's own names, need nobody asked.
        if (servers.isEmpty() || host.isEmpty() || host[0].isDigit() || ':' in host || host == "localhost" || '.' !in host) {
            return Dns.SYSTEM.lookup(host)
        }
        cache[host]?.let { (addresses, until) -> if (System.currentTimeMillis() < until) return addresses }
        for (server in servers) {
            val found = runCatching { ask(server, host) }.getOrNull()
            if (!found.isNullOrEmpty()) return found
        }
        return Dns.SYSTEM.lookup(host)
    }

    /**
     * Asks [server] for [host]'s addresses directly, without cache or fallback — for a settings
     * screen's "does this server answer" check. Throws when it does not.
     */
    fun probe(server: String, host: String = "music.youtube.com"): List<InetAddress> =
        ask(server, host).ifEmpty { throw IllegalStateException("No addresses for $host") }

    /** The servers to ask, in order. */
    private fun servers(): List<String> = when (val chosen = provider) {
        Provider.SYSTEM ->
            if (DpiBypass.enabled) listOfNotNull(Provider.CLOUDFLARE.url, Provider.GOOGLE.url) else emptyList()
        Provider.CUSTOM -> listOf(customUrl).filter { it.startsWith("https://") }
        else -> listOfNotNull(chosen.url)
    }

    /** Reaches the built-in servers by their known IPs; anything else by the system's lookup. */
    private val client: OkHttpClient by lazy {
        val known = Provider.entries.filter { it.url != null }.associate { provider ->
            provider.url!!.toHttpUrl().host to
                provider.bootstrap.map { InetAddress.getByName(it) }
        }
        OkHttpClient.Builder()
            // Inert unless the bypass is on, and then the server's name is split as well.
            .socketFactory(DpiBypass.socketFactory)
            .dns { host -> known[host] ?: Dns.SYSTEM.lookup(host) }
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    private val DNS_MESSAGE = "application/dns-message".toMediaType()

    private fun ask(server: String, host: String): List<InetAddress> {
        val request = Request.Builder()
            .url(server)
            .header("Accept", "application/dns-message")
            .post(Wire.query(host).toRequestBody(DNS_MESSAGE))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val (addresses, ttl) = Wire.answers(response.body.bytes(), host)
            if (addresses.isNotEmpty()) {
                cache[host] = addresses to System.currentTimeMillis() + ttl.coerceIn(60, 3_600) * 1_000L
            }
            return addresses
        }
    }

    /** DNS messages on the wire (RFC 1035): an A query, and the A records of its answer. */
    internal object Wire {
        private const val TYPE_A = 1
        private const val CLASS_IN = 1

        fun query(host: String): ByteArray {
            val out = ByteArrayOutputStream()
            // ID 0 (as RFC 8484 asks, for caching), recursion desired, one question.
            out.write(byteArrayOf(0, 0, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0))
            for (label in host.trimEnd('.').split('.')) {
                val bytes = label.toByteArray(Charsets.US_ASCII)
                require(bytes.size in 1..63) { "Bad label in $host" }
                out.write(bytes.size)
                out.write(bytes)
            }
            out.write(0)
            out.write(byteArrayOf(0, TYPE_A.toByte(), 0, CLASS_IN.toByte()))
            return out.toByteArray()
        }

        /** The answer's IPv4 addresses, and the shortest TTL among them (300 if none). */
        fun answers(message: ByteArray, host: String): Pair<List<InetAddress>, Int> {
            if (message.size < 12) return emptyList<InetAddress>() to 300
            val rcode = message[3].toInt() and 0x0F
            if (rcode != 0) return emptyList<InetAddress>() to 300
            val questions = u16(message, 4)
            val answers = u16(message, 6)
            var at = 12
            repeat(questions) { at = skipName(message, at) + 4 }
            val addresses = ArrayList<InetAddress>()
            var ttl = Int.MAX_VALUE
            repeat(answers) {
                at = skipName(message, at)
                if (at + 10 > message.size) return@repeat
                val type = u16(message, at)
                val recordTtl = u32(message, at + 4)
                val length = u16(message, at + 8)
                at += 10
                if (at + length > message.size) return@repeat
                if (type == TYPE_A && length == 4) {
                    addresses += InetAddress.getByAddress(host, message.copyOfRange(at, at + 4))
                    ttl = minOf(ttl, recordTtl)
                }
                at += length
            }
            return addresses to (if (ttl == Int.MAX_VALUE) 300 else ttl)
        }

        /** Past a name at [at]: labels up to a zero, or up to a two-byte pointer. */
        private fun skipName(b: ByteArray, start: Int): Int {
            var at = start
            while (at < b.size) {
                val length = b[at].toInt() and 0xFF
                when {
                    length == 0 -> return at + 1
                    length and 0xC0 == 0xC0 -> return at + 2
                    else -> at += 1 + length
                }
            }
            return at
        }

        private fun u16(b: ByteArray, at: Int): Int = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

        private fun u32(b: ByteArray, at: Int): Int =
            ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
                ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)
    }
}
