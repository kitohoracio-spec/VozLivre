package com.vozlivre.core.net

enum class PeerKind { LAN, BLUETOOTH }

/**
 * Uma pessoa/aparelho visível. [id] é estável: id curto gerado pela app (LAN)
 * ou "bt:" + endereço MAC (Bluetooth).
 */
data class Peer(
    val id: String,
    val name: String,
    val kind: PeerKind,
    val via: String = "",
    val detail: String = "",
    val role: String = "",
    val blocked: Boolean = false,
    /** Id do servidor a que este cliente se juntou (vazio se não anunciou nenhum). */
    val group: String = ""
) {
    val isP2p: Boolean get() = via.startsWith("p2p")

    /** Anunciou-se na rede com o papel de Servidor. */
    val isServer: Boolean get() = role == Role.SERVER.code

    /** Anunciou-se na rede com o papel de Cliente. */
    val isClient: Boolean get() = role == Role.CLIENT.code
}

interface TransportListener {
    fun onPeersChanged()
    fun onControl(peerId: String, text: String)
    fun onAudio(peerId: String, seq: Int, pcm: ByteArray)
    fun onPeerDisconnected(peerId: String)
    fun onLog(msg: String)
}

interface Transport {
    /** Taxa de amostragem do áudio usada nesta ligação. */
    val audioRate: Int

    /** Garante que há um canal aberto até ao par. Chamado fora da thread principal. */
    fun connect(peerId: String, cb: (ok: Boolean, error: String?) -> Unit)

    fun sendControl(peerId: String, text: String)
    fun sendAudio(peerId: String, seq: Int, pcm: ByteArray, len: Int)
    fun peers(): List<Peer>
}
