package com.music.bitchord.data.net

import kotlin.test.Test
import kotlin.test.assertEquals

class SecureDnsTest {

    @Test
    fun queryAndAnswerRoundTrip() {
        val query = SecureDns.Wire.query("music.youtube.com")
        // Header, then 5 music 7 youtube 3 com 0, then type A, class IN.
        assertEquals(12 + 1 + 5 + 1 + 7 + 1 + 3 + 1 + 4, query.size)
        // The server's answer: the question echoed, then two A records named by pointer to it.
        val answer = query.copyOf().also {
            it[2] = 0x81.toByte(); it[3] = 0x80.toByte() // response, no error
            it[7] = 2 // two answers
        } + record(byteArrayOf(142.toByte(), 250.toByte(), 74, 46), ttl = 120) +
            record(byteArrayOf(142.toByte(), 250.toByte(), 74, 78), ttl = 60)
        val (addresses, ttl) = SecureDns.Wire.answers(answer, "music.youtube.com")
        assertEquals(listOf("142.250.74.46", "142.250.74.78"), addresses.map { it.hostAddress })
        assertEquals(60, ttl)
    }

    @Test
    fun anErrorAnswerHasNoAddresses() {
        val answer = SecureDns.Wire.query("nowhere.example").also { it[2] = 0x81.toByte(); it[3] = 0x83.toByte() }
        assertEquals(0, SecureDns.Wire.answers(answer, "nowhere.example").first.size)
    }

    private fun record(ip: ByteArray, ttl: Int) = byteArrayOf(
        0xC0.toByte(), 12, // name: pointer to the question's
        0, 1, 0, 1, // A, IN
        0, 0, (ttl ushr 8).toByte(), ttl.toByte(),
        0, 4,
    ) + ip
}
