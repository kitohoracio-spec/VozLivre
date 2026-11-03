package com.vozlivre.core.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerTest {

    @Test
    fun roleCodesIdentifyServersAndClients() {
        val server = Peer("a", "Servidor", PeerKind.LAN, role = Role.SERVER.code)
        val client = Peer("b", "Cliente", PeerKind.LAN, role = Role.CLIENT.code)
        assertTrue(server.isServer && !server.isClient)
        assertTrue(client.isClient && !client.isServer)
    }

    @Test
    fun bluetoothPeersHaveNoRole() {
        val bt = Peer("bt:00:11", "Telemóvel", PeerKind.BLUETOOTH, via = "bluetooth")
        assertFalse(bt.isServer)
        assertFalse(bt.isClient)
    }

    @Test
    fun wifiDirectInterfaceIsDetected() {
        assertTrue(Peer("a", "x", PeerKind.LAN, via = "p2p-wlan0-0").isP2p)
        assertFalse(Peer("a", "x", PeerKind.LAN, via = "wlan0").isP2p)
    }
}
