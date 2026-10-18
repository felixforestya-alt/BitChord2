package com.music.bitchord.data.net

import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Getting to YouTube where the network in between is told not to let you: Russia's, where its
 * servers are slowed to nothing by deep packet inspection that reads the site's name out of the
 * first message of every encrypted connection, and some providers answer for its addresses with
 * their own.
 *
 * Two things, both inside the app and with no server of ours in the middle:
 *  - **The name split.** That first message — the TLS ClientHello, which carries the name in the
 *    clear — goes out as two TLS records, cut through the middle of the name, each in a TCP
 *    segment of its own. The server puts them back together, as TLS allows; the inspection, which
 *    reads one packet at a time, does not find the name it is looking for. (The trick GoodbyeDPI,
 *    zapret and ByeDPI play from outside the app.)
 *  - **Addresses over HTTPS.** Host names are looked up with Cloudflare's and Google's DNS over
 *    HTTPS, so a provider's own answer for a name is never asked for. The system's lookup is the
 *    fallback when neither answers.
 *
 * Off, everything goes the ordinary way. It is read on every connection, so switching it takes
 * effect on the next one.
 */
object DpiBypass {

    @Volatile
    var enabled: Boolean = false

    /** For [OkHttpClient.Builder.socketFactory]: sockets that split the name when [enabled]. */
    val socketFactory: SocketFactory = object : SocketFactory() {
        override fun createSocket(): Socket = SplittingSocket()
        override fun createSocket(host: String, port: Int): Socket = SplittingSocket().apply { connect(java.net.InetSocketAddress(host, port)) }
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            SplittingSocket().apply {
                bind(java.net.InetSocketAddress(localHost, localPort))
                connect(java.net.InetSocketAddress(host, port))
            }
        override fun createSocket(host: InetAddress, port: Int): Socket = SplittingSocket().apply { connect(java.net.InetSocketAddress(host, port)) }
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
            SplittingSocket().apply {
                bind(java.net.InetSocketAddress(localAddress, localPort))
                connect(java.net.InetSocketAddress(address, port))
            }
    }

    /** For [OkHttpClient.Builder.dns]: DNS over HTTPS when [enabled], the system's otherwise. */
    val dns: Dns = Dns { host -> if (enabled) DohDns.lookup(host) else Dns.SYSTEM.lookup(host) }

    /** A plain socket whose first write, if it is a ClientHello, goes out split. */
    private class SplittingSocket : Socket() {
        private var wrapped: OutputStream? = null

        override fun getOutputStream(): OutputStream {
            wrapped?.let { return it }
            val real = super.getOutputStream()
            return SplittingStream(this, real).also { wrapped = it }
        }
    }

    private class SplittingStream(private val socket: Socket, private val out: OutputStream) : OutputStream() {
        @Volatile private var first = true

        override fun write(b: Int) {
            first = false
            out.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (first) {
                first = false
                if (enabled && ClientHello.isOne(b, off, len)) {
                    writeSplit(b, off, len)
                    return
                }
            }
            out.write(b, off, len)
        }

        override fun flush() = out.flush()
        override fun close() = out.close()

        private fun writeSplit(b: ByteArray, off: Int, len: Int) {
            val records = ClientHello.split(b, off, len) ?: run {
                out.write(b, off, len)
                return
            }
            // Each piece its own segment: no waiting to fill a packet.
            val nagle = runCatching { socket.tcpNoDelay }.getOrDefault(false)
            runCatching { socket.tcpNoDelay = true }
            for (piece in records) {
                out.write(piece)
                out.flush()
            }
            if (!nagle) runCatching { socket.tcpNoDelay = false }
        }
    }

    /** Reading and cutting a TLS ClientHello. */
    internal object ClientHello {
        private const val HANDSHAKE = 0x16
        private const val CLIENT_HELLO = 0x01
        private const val SERVER_NAME = 0x0000

        fun isOne(b: ByteArray, off: Int, len: Int): Boolean =
            len > 9 &&
                (b[off].toInt() and 0xFF) == HANDSHAKE &&
                (b[off + 1].toInt() and 0xFF) == 0x03 &&
                (b[off + 5].toInt() and 0xFF) == CLIENT_HELLO &&
                5 + u16(b, off + 3) <= len

        /**
         * The record at [off] as the pieces to send, each its own TCP segment:
         *  1. a first TLS record holding only the hello's first [HEAD_BYTES] bytes — an inspector
         *     that reads the first record for the name finds none in it;
         *  2. a second record with the rest of the hello, sent in two segments cut through the
         *     middle of the server name — one that reads a packet at a time finds half of it.
         * Whatever followed the record in the same write goes after. Null if it cannot be cut.
         *
         * Not a record cut inside the name itself: tried, and the connection was reset — by the
         * network in the way or the server, it was refused either way.
         */
        fun split(b: ByteArray, off: Int, len: Int): List<ByteArray>? {
            val payloadLength = u16(b, off + 3)
            if (payloadLength <= HEAD_BYTES + 1) return null
            val payload = off + 5
            fun record(from: Int, size: Int) = ByteArray(5 + size).also { r ->
                r[0] = b[off]
                r[1] = b[off + 1]
                r[2] = b[off + 2]
                r[3] = (size ushr 8).toByte()
                r[4] = size.toByte()
                System.arraycopy(b, from, r, 5, size)
            }
            val head = record(payload, HEAD_BYTES)
            var tail = record(payload + HEAD_BYTES, payloadLength - HEAD_BYTES)
            val rest = len - (5 + payloadLength)
            if (rest > 0) tail += b.copyOfRange(off + 5 + payloadLength, off + len)
            // Where the name's middle falls in [tail]: past its own header, less what [head] took.
            val nameCut = sniMiddle(b, payload, payloadLength)
                ?.let { 5 + it - HEAD_BYTES }
                ?.takeIf { it in 6 until tail.size }
                ?: return listOf(head, tail)
            return listOf(head, tail.copyOfRange(0, nameCut), tail.copyOfRange(nameCut, tail.size))
        }

        /** What the first record carries: the handshake type and the start of its length. */
        private const val HEAD_BYTES = 2

        /** Where in the payload the middle of the server name is, or null if there is none. */
        fun sniMiddle(b: ByteArray, payload: Int, payloadLength: Int): Int? = runCatching {
            val end = payload + payloadLength
            var at = payload + 4 // handshake type and length
            at += 2 + 32 // version, random
            at += 1 + (b[at].toInt() and 0xFF) // session id
            at += 2 + u16(b, at) // cipher suites
            at += 1 + (b[at].toInt() and 0xFF) // compression methods
            val extensionsEnd = at + 2 + u16(b, at)
            at += 2
            while (at + 4 <= minOf(extensionsEnd, end)) {
                val type = u16(b, at)
                val size = u16(b, at + 2)
                if (type == SERVER_NAME) {
                    // list length (2), name type (1), name length (2), name
                    val nameLength = u16(b, at + 4 + 3)
                    val nameStart = at + 4 + 5
                    if (nameLength <= 1 || nameStart + nameLength > end) return@runCatching null
                    return@runCatching nameStart + nameLength / 2 - payload
                }
                at += 4 + size
            }
            null
        }.getOrNull()

        private fun u16(b: ByteArray, at: Int): Int = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
    }

    /** DNS over HTTPS, asked of resolvers by address so that no lookup is needed to reach them. */
    private object DohDns {
        private val json = Json { ignoreUnknownKeys = true }
        private val cache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .socketFactory(socketFactory)
                .dns(Dns.SYSTEM)
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .callTimeout(6, TimeUnit.SECONDS)
                .build()
        }

        private val resolvers = listOf(
            "https://1.1.1.1/dns-query?type=A&name=",
            "https://8.8.8.8/resolve?type=A&name=",
            "https://1.0.0.1/dns-query?type=A&name=",
        )

        fun lookup(host: String): List<InetAddress> {
            // Addresses already, and this machine's own names, need nobody asked.
            if (host.isEmpty() || host[0].isDigit() || ':' in host || host == "localhost" || '.' !in host) {
                return Dns.SYSTEM.lookup(host)
            }
            cache[host]?.let { (addresses, until) -> if (System.currentTimeMillis() < until) return addresses }
            for (resolver in resolvers) {
                val found = runCatching { ask(resolver, host) }.getOrNull()
                if (!found.isNullOrEmpty()) return found
            }
            return try {
                Dns.SYSTEM.lookup(host)
            } catch (failure: UnknownHostException) {
                throw failure
            }
        }

        private fun ask(resolver: String, host: String): List<InetAddress> {
            val request = Request.Builder().url(resolver + host).header("Accept", "application/dns-json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = json.parseToJsonElement(response.body.string()).jsonObject
                val answers = (body["Answer"] ?: return emptyList()).jsonArray.map { it.jsonObject }
                    .filter { it["type"]?.jsonPrimitive?.int == 1 }
                val addresses = answers.mapNotNull { answer ->
                    answer["data"]?.jsonPrimitive?.content?.let { ip -> runCatching { InetAddress.getByAddress(host, ipv4(ip)) }.getOrNull() }
                }
                val ttl = answers.mapNotNull { it["TTL"]?.jsonPrimitive?.intOrNull }.minOrNull() ?: 300
                if (addresses.isNotEmpty()) {
                    cache[host] = addresses to System.currentTimeMillis() + ttl.coerceIn(60, 3_600) * 1_000L
                }
                return addresses
            }
        }

        private fun ipv4(text: String): ByteArray {
            val parts = text.split('.')
            require(parts.size == 4)
            return ByteArray(4) { parts[it].toInt().toByte() }
        }
    }
}
