package com.vozlivre.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTest {

    @Test
    fun roundTripKeepsSpecialCharacters() {
        val info = PairingInfo("ab12cd34", "Servidor Nampula ç", listOf("192.168.1.10", "192.168.49.1"), "s&nha=com|símbolos +%")
        val back = Pairing.decode(Pairing.encode(info))
        assertEquals(info, back)
    }

    @Test
    fun emptyPasswordMeansDefaultPassword() {
        val info = PairingInfo("id1", "Servidor", listOf("10.0.0.2"), "")
        assertEquals("", Pairing.decode(Pairing.encode(info))?.password)
    }

    @Test
    fun textFromOtherQrCodesIsRejected() {
        assertNull(Pairing.decode("https://exemplo.com"))
        assertNull(Pairing.decode("grfee://join?v=2&id=x"))
        assertNull(Pairing.decode("grfee://join?v=1&n=SemId"))
    }

    @Test
    fun onlyDottedIpv4AddressesSurvive() {
        val text = "grfee://join?v=1&id=x&ip=" + "192.168.1.5,exemplo.com,300x.1.1.1,10.0.0.9"
        val info = Pairing.decode(text)
        assertNotNull(info)
        assertEquals(listOf("192.168.1.5", "10.0.0.9"), info!!.ips)
    }
}
