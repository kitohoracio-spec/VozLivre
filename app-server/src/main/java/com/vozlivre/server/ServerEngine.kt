package com.vozlivre.server

import android.content.Context
import com.vozlivre.core.call.CallEngine
import com.vozlivre.core.call.Screening
import com.vozlivre.core.net.Pairing
import com.vozlivre.core.net.PairingInfo
import com.vozlivre.core.net.Peer
import com.vozlivre.core.net.Role

/**
 * Motor do Servidor: tudo o que decide quem pode usar este servidor.
 * O protocolo de chamada e o áudio vêm do [CallEngine].
 *
 * - Bloqueados: o servidor ignora as chamadas deles.
 * - Aprovação manual (opcional): clientes novos ficam pendentes e as chamadas deles
 *   são recusadas com DENY até o administrador os aceitar.
 * - Outros servidores não ligam a este: só há chamadas entre servidor e cliente.
 */
class ServerEngine(app: Context) : CallEngine(app, Role.SERVER) {

    private val blocked: MutableSet<String> = HashSet(prefs.getStringSet("blocked", emptySet()) ?: emptySet())
    private val approved: MutableSet<String> = HashSet(prefs.getStringSet("approved", emptySet()) ?: emptySet())

    var approvalRequired: Boolean = prefs.getBoolean("approval", false)
        private set

    // ---------------------------------------------------------------- pares

    override fun allPeers(): List<Peer> = super.allPeers().map { it.copy(blocked = blocked.contains(it.id)) }

    /** Pares que não são servidores (clientes e dispositivos Bluetooth). */
    fun clients(): List<Peer> = allPeers().filter { !it.isServer }

    // ---------------------------------------------------------------- escuta

    /**
     * Pede a um cliente para partilhar o microfone com este servidor. O cliente vê o pedido,
     * o telemóvel toca e só começa se o utilizador tocar em Permitir. Não há escuta automática.
     */
    fun startListening(p: Peer) { beginListen(p) }

    // ---------------------------------------------------------------- ligação por QR

    /** Texto do QR que o cliente lê para ficar configurado: id, IPs e senha deste servidor. */
    fun pairingPayload(): String =
        Pairing.encode(PairingInfo(myId, displayName, lan.localIps(), prefs.getString("pw", "") ?: ""))

    // ---------------------------------------------------------------- bloqueios

    fun isBlocked(id: String) = blocked.contains(id)

    fun setBlocked(id: String, on: Boolean) {
        if (on) blocked.add(id) else blocked.remove(id)
        prefs.edit().putStringSet("blocked", HashSet(blocked)).apply()
        log(if (on) "Bloqueado: $id" else "Desbloqueado: $id")
        notifyPeersChanged()
    }

    fun clearBlocked() {
        blocked.clear()
        prefs.edit().remove("blocked").apply()
        notifyPeersChanged()
    }

    // ---------------------------------------------------------------- aprovação

    fun setApprovalRequired(on: Boolean) {
        approvalRequired = on
        prefs.edit().putBoolean("approval", on).apply()
        notifyPeersChanged()
    }

    fun approve(id: String) {
        approved.add(id)
        prefs.edit().putStringSet("approved", HashSet(approved)).apply()
        notifyPeersChanged()
    }

    fun clearApprovals() {
        approved.clear()
        prefs.edit().remove("approved").apply()
        notifyPeersChanged()
    }

    fun isApproved(id: String) = approved.contains(id)

    fun isPending(p: Peer): Boolean = approvalRequired && !approved.contains(p.id) && !blocked.contains(p.id)

    // ---------------------------------------------------------------- regras de chamada

    override fun screenIncoming(peerId: String, peer: Peer?, listen: Boolean): Screening = when {
        listen -> {
            log("Pedido de escuta de $peerId ignorado: só o servidor pede escuta")
            Screening.IGNORE
        }
        blocked.contains(peerId) -> {
            log("Chamada de $peerId ignorada (bloqueado)")
            Screening.IGNORE
        }
        peer != null && peer.isServer -> {
            log("Chamada de outro servidor ignorada: ${peer.name}")
            Screening.IGNORE
        }
        approvalRequired && !approved.contains(peerId) -> {
            log("Chamada de $peerId recusada: cliente ainda não aprovado")
            Screening.DENY
        }
        else -> Screening.ALLOW
    }
}
