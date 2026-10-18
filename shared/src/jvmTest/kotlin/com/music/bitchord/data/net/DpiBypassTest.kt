package com.music.bitchord.data.net

import java.nio.ByteBuffer
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DpiBypassTest {

    /** A real ClientHello for [host], as the JDK would send it — no network needed. */
    private fun clientHello(host: String): ByteArray {
        val engine = SSLContext.getDefault().createSSLEngine(host, 443)
        engine.useClientMode = true
        engine.sslParameters = engine.sslParameters.apply { serverNames = listOf(SNIHostName(host)) }
        val out = ByteBuffer.allocate(engine.session.packetBufferSize)
        engine.wrap(ByteBuffer.allocate(0), out)
        out.flip()
        return ByteArray(out.remaining()).also(out::get)
    }

    @Test
    fun `the hello goes as a tiny record then the rest cut through the server name`() {
        val host = "rr3---sn-abcdef.googlevideo.com"
        val hello = clientHello(host)
        assertTrue(DpiBypass.ClientHello.isOne(hello, 0, hello.size))

        val pieces = DpiBypass.ClientHello.split(hello, 0, hello.size)!!
        assertEquals(3, pieces.size)
        val first = pieces[0]
        val second = pieces[1] + pieces[2]
        // Two handshake records, the same protocol version as the original; the first tiny.
        assertEquals(0x16, first[0].toInt() and 0xFF)
        assertEquals(0x16, second[0].toInt() and 0xFF)
        assertEquals(7, first.size)
        assertContentEquals(hello.copyOfRange(1, 3), second.copyOfRange(1, 3))
        // No segment holds the whole name.
        val name = host.toByteArray()
        assertTrue(pieces.none { it.containsRun(name) })
        assertTrue(second.containsRun(name))
        // And the records' bodies are the original body, in order.
        val body = first.copyOfRange(5, first.size) + second.copyOfRange(5, second.size)
        assertContentEquals(hello.copyOfRange(5, hello.size), body)
        assertEquals(second.size - 5, ((second[3].toInt() and 0xFF) shl 8) or (second[4].toInt() and 0xFF))
    }

    @Test
    fun `anything that is not a hello is left alone`() {
        val data = "GET / HTTP/1.1\r\n\r\n".toByteArray()
        assertTrue(!DpiBypass.ClientHello.isOne(data, 0, data.size))
    }

    private fun ByteArray.containsRun(run: ByteArray): Boolean =
        (0..size - run.size).any { start -> run.indices.all { this[start + it] == run[it] } }
}
