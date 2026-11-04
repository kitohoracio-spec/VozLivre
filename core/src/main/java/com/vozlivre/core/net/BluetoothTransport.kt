package com.vozlivre.core.net

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Bluetooth clássico por RFCOMM. Cada par tem um único socket com enquadramento:
 * [tipo:1][tamanho:2][dados]. Tipo 1 = controlo (texto), tipo 2 = áudio (seq:2 + PCM).
 * O áudio usa 8 kHz para caber na largura de banda do RFCOMM.
 *
 * Só aparecem como pares os dispositivos que anunciam o [SERVICE_UUID] da app (ou que já
 * ligaram a este telemóvel). Auriculares, colunas e outros aparelhos ficam de fora, salvo
 * se [showAll] estiver ligado.
 */
@SuppressLint("MissingPermission")
class BluetoothTransport(private val ctx: Context) : Transport {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("5b0a4f7e-2d6c-4a3e-9a11-7a56c3f0d9e1")
        private const val T_CONTROL = 1
        private const val T_AUDIO = 2
    }

    override val audioRate: Int = 8000
    @Volatile var listener: TransportListener? = null

    /** Mostra também os dispositivos que não anunciam a app. */
    @Volatile var showAll: Boolean = false

    private class Conn(val socket: BluetoothSocket) {
        val out = DataOutputStream(BufferedOutputStream(socket.outputStream, 4096))
        val inp = DataInputStream(BufferedInputStream(socket.inputStream, 4096))
        val lock = Any()
    }

    private val adapter: BluetoothAdapter? =
        (ctx.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val conns = ConcurrentHashMap<String, Conn>()
    private val known = ConcurrentHashMap<String, Peer>()
    private val verified = ConcurrentHashMap.newKeySet<String>()
    private var uuidReceiver: BroadcastReceiver? = null
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var serverRunning = false
    private var server: BluetoothServerSocket? = null
    private var scanReceiver: BroadcastReceiver? = null

    private fun log(m: String) { listener?.onLog("[BT] $m") }

    val available: Boolean get() = adapter != null
    fun isEnabled(): Boolean = try { adapter?.isEnabled == true } catch (_: Exception) { false }

    // ---------------------------------------------------------------- servidor

    /** Começa a aceitar ligações de outros telemóveis. Precisa de BLUETOOTH_CONNECT (Android 12+). */
    fun startServer() {
        val a = adapter ?: return
        if (serverRunning || !isEnabled()) return
        serverRunning = true
        refreshBonded()
        thread(name = "bt-server", isDaemon = true) {
            try {
                val s = a.listenUsingInsecureRfcommWithServiceRecord("Grfee", SERVICE_UUID)
                server = s
                log("À escuta de ligações Bluetooth")
                while (serverRunning) {
                    val sock = s.accept()
                    val dev = sock.remoteDevice
                    val id = "bt:" + dev.address
                    log("Ligação recebida de ${safeName(dev)}")
                    known[id] = Peer(id, safeName(dev), PeerKind.BLUETOOTH, "bluetooth", "Ligado")
                    verified.add(id)
                    register(id, Conn(sock))
                    listener?.onPeersChanged()
                }
            } catch (e: Exception) {
                if (serverRunning) log("Servidor terminou: ${e.message}")
            } finally {
                serverRunning = false
            }
        }
    }

    fun stop() {
        serverRunning = false
        try { server?.close() } catch (_: Exception) {}
        server = null
        stopScan()
        uuidReceiver?.let { try { ctx.unregisterReceiver(it) } catch (_: Exception) {} }
        uuidReceiver = null
        for (c in conns.values) try { c.socket.close() } catch (_: Exception) {}
        conns.clear()
    }

    // ---------------------------------------------------------------- descoberta

    /** O dispositivo anuncia o serviço da app? Usa a lista de UUID que o Android já tem em cache. */
    private fun offersService(d: BluetoothDevice): Boolean =
        try { d.uuids?.any { it.uuid == SERVICE_UUID } == true } catch (_: Exception) { false }

    /** Pede ao dispositivo a lista de serviços. A resposta chega por ACTION_UUID. */
    private fun probeService(d: BluetoothDevice) {
        val id = "bt:" + d.address
        if (offersService(d)) { verified.add(id); return }
        try { d.fetchUuidsWithSdp() } catch (_: Exception) {}
    }

    private fun ensureUuidReceiver() {
        if (uuidReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action != BluetoothDevice.ACTION_UUID) return
                val d = deviceFrom(i) ?: return
                val fromExtra = try {
                    i.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)?.any { (it as? ParcelUuid)?.uuid == SERVICE_UUID } == true
                } catch (_: Exception) { false }
                if (fromExtra || offersService(d)) {
                    val id = "bt:" + d.address
                    if (verified.add(id)) {
                        if (!known.containsKey(id)) known[id] = Peer(id, safeName(d), PeerKind.BLUETOOTH, "bluetooth", "Encontrado")
                        listener?.onPeersChanged()
                    }
                }
            }
        }
        val f = IntentFilter(BluetoothDevice.ACTION_UUID)
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
        else ctx.registerReceiver(r, f)
        uuidReceiver = r
    }

    fun refreshBonded() {
        val a = adapter ?: return
        ensureUuidReceiver()
        try {
            for (d in a.bondedDevices ?: emptySet()) {
                val id = "bt:" + d.address
                if (!known.containsKey(id)) known[id] = Peer(id, safeName(d), PeerKind.BLUETOOTH, "bluetooth", "Emparelhado")
                probeService(d)
            }
            listener?.onPeersChanged()
        } catch (e: Exception) { log("Lista de emparelhados falhou: ${e.message}") }
    }

    fun startScan() {
        val a = adapter ?: return
        stopScan()
        refreshBonded()
        ensureUuidReceiver()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == BluetoothDevice.ACTION_FOUND) {
                    val d: BluetoothDevice? = deviceFrom(i)
                    if (d != null) {
                        val id = "bt:" + d.address
                        val detail = if (d.bondState == BluetoothDevice.BOND_BONDED) "Emparelhado" else "Encontrado"
                        known[id] = Peer(id, safeName(d), PeerKind.BLUETOOTH, "bluetooth", detail)
                        if (offersService(d)) verified.add(id)
                        listener?.onPeersChanged()
                    }
                } else if (i.action == BluetoothAdapter.ACTION_DISCOVERY_FINISHED) {
                    log("Procura Bluetooth terminou")
                    for (p in known.values) {
                        if (verified.contains(p.id)) continue
                        try { a.getRemoteDevice(p.id.removePrefix("bt:")).fetchUuidsWithSdp() } catch (_: Exception) {}
                    }
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
        else ctx.registerReceiver(r, f)
        scanReceiver = r
        try {
            val ok = a.startDiscovery()
            log(if (ok) "A procurar dispositivos Bluetooth..." else "O Android recusou iniciar a procura")
        } catch (e: Exception) { log("Procura falhou: ${e.message}") }
    }

    fun stopScan() {
        try { adapter?.cancelDiscovery() } catch (_: Exception) {}
        scanReceiver?.let { try { ctx.unregisterReceiver(it) } catch (_: Exception) {} }
        scanReceiver = null
    }

    private fun safeName(d: BluetoothDevice): String {
        val n: String? = try { d.name } catch (_: Exception) { null }
        return n ?: d.address
    }

    @Suppress("DEPRECATION")
    private fun deviceFrom(i: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    // ---------------------------------------------------------------- Transport

    override fun connect(peerId: String, cb: (ok: Boolean, error: String?) -> Unit) {
        if (conns.containsKey(peerId)) { cb(true, null); return }
        val a = adapter
        if (a == null || !isEnabled()) { cb(false, "Bluetooth desligado"); return }
        thread(name = "bt-connect", isDaemon = true) {
            try {
                a.cancelDiscovery()
                val dev = a.getRemoteDevice(peerId.removePrefix("bt:"))
                val sock = dev.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID)
                sock.connect()
                verified.add(peerId)
                register(peerId, Conn(sock))
                log("Ligado a ${safeName(dev)}")
                cb(true, null)
            } catch (e: Exception) {
                log("Ligação falhou: ${e.message}")
                cb(false, "o outro telemóvel precisa de ter o Grfee aberto com Bluetooth ativo")
            }
        }
    }

    override fun sendControl(peerId: String, text: String) {
        val b = text.toByteArray(Charsets.UTF_8)
        io.execute { write(peerId, T_CONTROL, b, b.size) }
    }

    override fun sendAudio(peerId: String, seq: Int, pcm: ByteArray, len: Int) {
        val data = ByteArray(len + 2)
        data[0] = (seq shr 8).toByte()
        data[1] = seq.toByte()
        System.arraycopy(pcm, 0, data, 2, len)
        write(peerId, T_AUDIO, data, data.size)
    }

    override fun peers(): List<Peer> =
        known.values.filter { showAll || verified.contains(it.id) || conns.containsKey(it.id) }.map {
            if (conns.containsKey(it.id)) it.copy(detail = "Ligado") else it
        }.sortedWith(compareBy({ it.detail != "Ligado" }, { it.name }))

    // ---------------------------------------------------------------- ligação

    private fun register(id: String, c: Conn) {
        conns.put(id, c)?.let { try { it.socket.close() } catch (_: Exception) {} }
        thread(name = "bt-read-$id", isDaemon = true) { readLoop(id, c) }
        listener?.onPeersChanged()
    }

    private fun write(id: String, type: Int, data: ByteArray, len: Int) {
        val c = conns[id] ?: return
        try {
            synchronized(c.lock) {
                c.out.writeByte(type)
                c.out.writeShort(len)
                c.out.write(data, 0, len)
                c.out.flush()
            }
        } catch (e: Exception) {
            drop(id, c)
        }
    }

    private fun readLoop(id: String, c: Conn) {
        try {
            while (true) {
                val type = c.inp.readUnsignedByte()
                val len = c.inp.readUnsignedShort()
                val data = ByteArray(len)
                c.inp.readFully(data)
                if (type == T_CONTROL) {
                    listener?.onControl(id, String(data, Charsets.UTF_8))
                } else if (type == T_AUDIO && len > 2) {
                    val seq = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    listener?.onAudio(id, seq, data.copyOfRange(2, len))
                }
            }
        } catch (_: Exception) {
        } finally {
            drop(id, c)
        }
    }

    private fun drop(id: String, c: Conn) {
        try { c.socket.close() } catch (_: Exception) {}
        if (conns.remove(id, c)) {
            log("Ligação Bluetooth perdida: $id")
            listener?.onPeerDisconnected(id)
            listener?.onPeersChanged()
        }
    }
}
