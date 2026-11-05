package com.vozlivre.client

import android.content.Context
import android.os.SystemClock
import com.vozlivre.core.call.CallEngine
import com.vozlivre.core.call.Screening
import com.vozlivre.core.net.PairingInfo
import com.vozlivre.core.net.Peer
import com.vozlivre.core.net.PeerKind
import com.vozlivre.core.net.Role

/**
 * Motor do Cliente: escolha do servidor, contactos e favoritos.
 * O protocolo de chamada e o áudio vêm do [CallEngine].
 *
 * O cliente fala com o servidor escolhido e com os outros clientes desse servidor.
 * Chamadas de clientes de outro servidor são ignoradas. Pedidos de escuta só valem se
 * vierem do servidor escolhido e dependem sempre de o utilizador tocar em Permitir.
 */
class ClientEngine(app: Context) : CallEngine(app, Role.CLIENT) {

    var serverId: String? = prefs.getString("server_id", null)
        private set

    private var serverSeenSince = 0L

    private val favorites: MutableMap<String, String> = loadFavorites()

    private fun loadFavorites(): MutableMap<String, String> {
        val map = HashMap<String, String>()
        for (e in prefs.getStringSet("favorites", emptySet()) ?: emptySet()) {
            val i = e.indexOf('\t')
            if (i > 0) map[e.substring(0, i)] = e.substring(i + 1)
        }
        return map
    }

    // ---------------------------------------------------------------- servidores

    /** Servidores visíveis: papel S na rede local e qualquer dispositivo Bluetooth. */
    fun servers(): List<Peer> = allPeers().filter { it.kind == PeerKind.BLUETOOTH || it.isServer }

    fun selectedServerPeer(): Peer? = serverId?.let { id -> allPeers().firstOrNull { it.id == id } }

    fun selectServer(id: String?) {
        serverId = id
        prefs.edit().apply { if (id == null) remove("server_id") else putString("server_id", id) }.apply()
        lan.wake()
        notifyPeersChanged()
    }

    /** Anuncia aos outros o servidor escolhido, para os clientes do mesmo servidor se verem. */
    override fun groupId(): String = serverId ?: ""

    /** Outros clientes que escolheram o mesmo servidor. */
    fun members(): List<Peer> {
        val id = serverId ?: return emptyList()
        return allPeers().filter { it.isClient && it.group == id }
    }

    /** Com quem se pode falar: o servidor escolhido e os outros clientes dele. */
    fun contacts(): List<Peer> = listOfNotNull(selectedServerPeer()) + members()

    /** Configura o cliente a partir de um QR do servidor: senha, servidor escolhido e sondagem dos IPs. */
    fun joinServer(info: PairingInfo) {
        setPassword(info.password)
        selectServer(info.id)
        for (ip in info.ips) lan.probe(ip)
        lan.wake()
        log("Ligação por QR ao servidor ${info.name} (${info.id})")
    }

    /** Segundos desde que o servidor escolhido ficou visível, ou -1 se não está visível. */
    fun serverLinkSeconds(): Int {
        if (selectedServerPeer() == null) { serverSeenSince = 0L; return -1 }
        val now = SystemClock.elapsedRealtime()
        if (serverSeenSince == 0L) serverSeenSince = now
        return ((now - serverSeenSince) / 1000).toInt()
    }

    // ---------------------------------------------------------------- favoritos

    fun favoriteList(): List<Pair<String, String>> = favorites.entries.map { it.key to it.value }.sortedBy { it.second }

    fun isFavorite(id: String) = favorites.containsKey(id)

    fun toggleFavorite(id: String, name: String) {
        if (favorites.remove(id) == null) favorites[id] = name
        prefs.edit().putStringSet("favorites", HashSet(favorites.entries.map { it.key + "\t" + it.value })).apply()
        notifyPeersChanged()
    }

    // ---------------------------------------------------------------- regras de chamada

    override fun screenIncoming(peerId: String, peer: Peer?, listen: Boolean): Screening = when {
        listen && peerId != serverId -> {
            log("Pedido de escuta ignorado: não vem do servidor escolhido")
            Screening.IGNORE
        }
        peer != null && peer.isClient && (serverId == null || peer.group != serverId) -> {
            log("Chamada de ${peer.name} ignorada: não é do mesmo servidor")
            Screening.IGNORE
        }
        else -> Screening.ALLOW
    }
}
