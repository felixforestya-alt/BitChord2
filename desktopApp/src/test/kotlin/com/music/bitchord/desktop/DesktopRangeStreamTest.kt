package com.music.bitchord.desktop

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reader exists because Google's media servers will not hand over a whole file, so the server
 * here behaves the way theirs does.
 */
class DesktopRangeStreamTest {

    private val body = ByteArray(300_000) { (it % 251).toByte() }

    /** The largest span this server will serve at once, as googlevideo has one. */
    private val allowance = 64 * 1024

    private fun serve(
        block: (url: String, requested: MutableList<String>) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requested = mutableListOf<String>()
        server.createContext("/media") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            requested += range ?: "(none)"
            val match = Regex("bytes=(\\d+)-(\\d+)").find(range.orEmpty())
            if (match == null) {
                // No range at all: the whole file, which is what is refused.
                exchange.sendResponseHeaders(403, -1)
                exchange.close()
                return@createContext
            }
            val from = match.groupValues[1].toInt()
            val to = minOf(match.groupValues[2].toInt(), body.size - 1)
            if (to - from + 1 > allowance || from >= body.size) {
                exchange.sendResponseHeaders(403, -1)
                exchange.close()
                return@createContext
            }
            val slice = body.copyOfRange(from, to + 1)
            exchange.responseHeaders.add("Content-Range", "bytes $from-$to/${body.size}")
            exchange.sendResponseHeaders(206, slice.size.toLong())
            exchange.responseBody.use { it.write(slice) }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/media", requested)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `reads a whole file a window at a time`() = serve { url, requested ->
        val stream = DesktopRangeStream(url, windowBytes = 32 * 1024)
        val collected = ArrayList<Byte>(body.size)
        val buffer = ByteArray(8192)
        while (true) {
            val taken = stream.read(buffer, buffer.size)
            if (taken <= 0) break
            repeat(taken) { collected += buffer[it] }
        }
        assertContentEquals(body, collected.toByteArray())
        assertEquals(body.size.toLong(), stream.length)
        // Every request was bounded: this server, like Google's, refuses anything else, and a
        // reader that asked for the file in one go would have collected nothing at all.
        assertTrue(requested.all { it.startsWith("bytes=") })
    }

    @Test
    fun `a seek forward fetches the window it lands in`() = serve { url, requested ->
        val stream = DesktopRangeStream(url, windowBytes = 32 * 1024)
        val buffer = ByteArray(16)
        stream.read(buffer, buffer.size)
        requested.clear()

        stream.seek(200_000)
        val taken = stream.read(buffer, buffer.size)
        assertEquals(16, taken)
        assertContentEquals(body.copyOfRange(200_000, 200_016), buffer)
        // One request, for the window containing the target rather than for everything between here
        // and there.
        assertEquals(1, requested.size)
    }

    @Test
    fun `a read inside the window already held costs no request`() = serve { url, requested ->
        val stream = DesktopRangeStream(url, windowBytes = 32 * 1024)
        val buffer = ByteArray(1024)
        stream.read(buffer, buffer.size)
        val after = requested.size
        repeat(8) { stream.read(buffer, buffer.size) }
        assertEquals(after, requested.size)
    }

    @Test
    fun `the end of the file is the end of the stream`() = serve { url, _ ->
        val stream = DesktopRangeStream(url, windowBytes = 32 * 1024)
        stream.seek(body.size.toLong())
        assertEquals(-1, stream.read(ByteArray(16), 16))
    }

    @Test
    fun `the end of the file is not a break`() = serve { url, _ ->
        val stream = DesktopRangeStream(url, windowBytes = 32 * 1024)
        val into = ByteArray(16 * 1024)
        while (stream.read(into, into.size) > 0) Unit
        assertEquals(false, stream.brokeOff)
    }

    @Test
    fun `a refused window is a break and is asked for once`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var asked = 0
        server.createContext("/media") { exchange ->
            asked++
            if (asked == 1) {
                val slice = ByteArray(1024)
                exchange.responseHeaders.add("Content-Range", "bytes 0-1023/4096")
                exchange.sendResponseHeaders(206, slice.size.toLong())
                exchange.responseBody.use { it.write(slice) }
            } else {
                exchange.sendResponseHeaders(403, -1)
                exchange.close()
            }
        }
        server.start()
        try {
            val stream = DesktopRangeStream("http://127.0.0.1:${server.address.port}/media", windowBytes = 1024)
            val into = ByteArray(1024)
            assertEquals(1024, stream.read(into, into.size))
            assertEquals(-1, stream.read(into, into.size))
            assertTrue(stream.brokeOff)
            // FFmpeg asks again after a failed read; nothing more goes out.
            assertEquals(-1, stream.read(into, into.size))
            assertEquals(2, asked)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a window that fails on the way is asked for again`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var asked = 0
        server.createContext("/media") { exchange ->
            asked++
            if (asked == 1) {
                exchange.sendResponseHeaders(503, -1)
                exchange.close()
            } else {
                val slice = ByteArray(1024) { 7 }
                exchange.responseHeaders.add("Content-Range", "bytes 0-1023/1024")
                exchange.sendResponseHeaders(206, slice.size.toLong())
                exchange.responseBody.use { it.write(slice) }
            }
        }
        server.start()
        try {
            val stream = DesktopRangeStream("http://127.0.0.1:${server.address.port}/media", windowBytes = 1024)
            val into = ByteArray(1024)
            assertEquals(1024, stream.read(into, into.size))
            assertEquals(false, stream.brokeOff)
            assertEquals(2, asked)
        } finally {
            server.stop(0)
        }
    }

    /** Serves [total] bytes on /good, refuses /bad from [refuseFrom] on. */
    private fun twoLinks(total: Int, refuseFrom: Int, block: (base: String) -> Unit) {
        val file = ByteArray(total) { (it % 251).toByte() }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val answer = { exchange: com.sun.net.httpserver.HttpExchange, limit: Int ->
            val match = Regex("bytes=(\\d+)-(\\d+)").find(exchange.requestHeaders.getFirst("Range").orEmpty())!!
            val from = match.groupValues[1].toInt()
            val to = minOf(match.groupValues[2].toInt(), file.size - 1)
            if (from >= limit) {
                exchange.sendResponseHeaders(403, -1)
                exchange.close()
            } else {
                val slice = file.copyOfRange(from, to + 1)
                exchange.responseHeaders.add("Content-Range", "bytes $from-$to/${file.size}")
                exchange.sendResponseHeaders(206, slice.size.toLong())
                exchange.responseBody.use { it.write(slice) }
            }
        }
        server.createContext("/good") { answer(it, Int.MAX_VALUE) }
        server.createContext("/bad") { answer(it, refuseFrom) }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    private fun readAll(stream: DesktopRangeStream): ByteArray {
        val all = java.io.ByteArrayOutputStream()
        val into = ByteArray(1024)
        while (true) {
            val taken = stream.read(into, into.size)
            if (taken <= 0) break
            all.write(into, 0, taken)
        }
        return all.toByteArray()
    }

    @Test
    fun `a refused link is swapped for a fresh one and the bytes carry on`() = twoLinks(8192, 2048) { base ->
        var asked = 0
        val stream = DesktopRangeStream("$base/bad", windowBytes = 1024, renew = {
            asked++
            DesktopRangeStream.Link("$base/good", emptyMap())
        })
        assertContentEquals(ByteArray(8192) { (it % 251).toByte() }, readAll(stream))
        assertEquals(false, stream.brokeOff)
        assertEquals(1, asked)
    }

    @Test
    fun `a fresh link to a different file is a break, not spliced in`() = twoLinks(4096, 2048) { base ->
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/other") { exchange ->
            val slice = ByteArray(1024)
            exchange.responseHeaders.add("Content-Range", "bytes 2048-3071/9999")
            exchange.sendResponseHeaders(206, slice.size.toLong())
            exchange.responseBody.use { it.write(slice) }
        }
        server.start()
        try {
            val other = "http://127.0.0.1:${server.address.port}/other"
            val stream = DesktopRangeStream("$base/bad", windowBytes = 1024, renew = { DesktopRangeStream.Link(other, emptyMap()) })
            assertEquals(2048, readAll(stream).size)
            assertTrue(stream.brokeOff)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `links that keep being refused are a break, and stop being asked for`() = twoLinks(4096, 1024) { base ->
        var asked = 0
        val stream = DesktopRangeStream("$base/bad", windowBytes = 1024, renew = {
            asked++
            DesktopRangeStream.Link("$base/bad?$asked", emptyMap())
        })
        assertEquals(1024, readAll(stream).size)
        assertTrue(stream.brokeOff)
        assertEquals(2, asked)
    }

    @Test
    fun `a page where the audio should be is a break, not data`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/media") { exchange ->
            val page = "<html>blocked</html>".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, page.size.toLong())
            exchange.responseBody.use { it.write(page) }
        }
        server.start()
        try {
            val stream = DesktopRangeStream("http://127.0.0.1:${server.address.port}/media", windowBytes = 1024)
            assertEquals(-1, stream.read(ByteArray(1024), 1024))
            assertTrue(stream.brokeOff)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a server that ignores ranges is still read`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val whole = ByteArray(4_096) { (it % 97).toByte() }
        server.createContext("/media") { exchange ->
            // No Content-Range, the whole body: an ordinary source that does not do partial
            // responses.
            exchange.sendResponseHeaders(200, whole.size.toLong())
            exchange.responseBody.use { it.write(whole) }
        }
        server.start()
        val seen = AtomicReference<ByteArray>()
        try {
            val stream = DesktopRangeStream("http://127.0.0.1:${server.address.port}/media")
            val buffer = ByteArray(whole.size)
            val taken = stream.read(buffer, buffer.size)
            seen.set(buffer.copyOf(taken))
        } finally {
            server.stop(0)
        }
        assertContentEquals(whole, seen.get())
    }
}
