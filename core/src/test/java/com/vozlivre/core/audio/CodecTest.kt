package com.vozlivre.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CodecTest {

    private fun toBytes(samples: IntArray): ByteArray {
        val b = ByteArray(samples.size * 2)
        for ((i, v) in samples.withIndex()) {
            b[2 * i] = (v and 0xFF).toByte()
            b[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return b
    }

    private fun toSamples(b: ByteArray): IntArray =
        IntArray(b.size / 2) { (b[2 * it].toInt() and 0xFF) or (b[2 * it + 1].toInt() shl 8) }

    @Test
    fun encodedFrameHasHalfTheSize() {
        val pcm = ByteArray(640)
        assertEquals(320, Codec.encode(pcm, pcm.size).size)
        assertEquals(640, Codec.decode(ByteArray(320)).size)
    }

    @Test
    fun silenceStaysSilence() {
        val pcm = ByteArray(320)
        val back = toSamples(Codec.decode(Codec.encode(pcm, pcm.size)))
        assertTrue(back.all { it == 0 })
    }

    @Test
    fun roundTripErrorStaysWithinMuLawStep() {
        val input = IntArray(65535) { it - 32767 }
        val back = toSamples(Codec.decode(Codec.encode(toBytes(input), input.size * 2)))
        assertEquals(input.size, back.size)
        for (i in input.indices) {
            val v = input[i]
            val tolerance = abs(v) / 16 + 8
            assertTrue("amostra $v voltou como ${back[i]}", abs(back[i] - v) <= tolerance)
        }
    }

    @Test
    fun signIsPreserved() {
        val input = intArrayOf(-20000, -500, 500, 20000)
        val back = toSamples(Codec.decode(Codec.encode(toBytes(input), input.size * 2)))
        assertTrue(back[0] < 0 && back[1] < 0 && back[2] > 0 && back[3] > 0)
    }
}
