package com.vozlivre.core.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.vozlivre.core.R
import com.vozlivre.core.call.CallEngine

/** Wi-Fi Direct: criar grupo, procurar e ligar. */
class P2pSection(private val k: Kit, private val engine: CallEngine, private val host: Host, private val server: Boolean) : Section() {
    private val card = k.card()
    private val status = k.muted("")
    private val list = LinearLayout(k.ctx)
    override val view: View get() = card

    init {
        list.orientation = LinearLayout.VERTICAL
        card.addView(k.sectionTitle(R.drawable.ic_wifi, "Wi-Fi Direct"))
        card.addView(status, k.lp(top = 8))
        val row = LinearLayout(k.ctx)
        row.orientation = LinearLayout.HORIZONTAL
        if (server) {
            row.addView(k.button("Criar grupo", Kit.Style.PRIMARY, true) { host.withPerms(host.p2pPerms()) { engine.p2p.createGroup() } }, k.lp(0, -2, weight = 1f))
        }
        row.addView(k.button("Procurar", Kit.Style.SECONDARY, true) { host.withPerms(host.p2pPerms()) { engine.p2p.discover() } }, k.lp(0, -2, left = if (server) 8 else 0, weight = 1f))
        row.addView(k.button("Terminar", Kit.Style.GHOST, true) { engine.p2p.disconnect() }, k.lp(0, -2, left = 8, weight = 1f))
        card.addView(row, k.lp(top = 10))
        card.addView(list, k.lp(top = 8))
        card.addView(k.muted(
            if (server) "Cria o grupo aqui e, no cliente, usa Procurar e Ligar. Aceita o convite no outro telemóvel."
            else "Procura o servidor, toca em Ligar e aceita o convite no outro telemóvel.", 12f
        ), k.lp(top = 6))
    }

    override fun refresh() {
        status.text = engine.p2p.status
        list.removeAllViews()
        // Wi-Fi Direct lista TV, impressoras e auriculares. Telemóveis têm a categoria 10.
        val visible = engine.p2p.devices.filter {
            engine.showAllDevices || it.primaryDeviceType.isNullOrBlank() || it.primaryDeviceType.startsWith("10-")
        }
        for (d in visible) {
            val r = LinearLayout(k.ctx)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.background = k.shape(Colors.SURFACE, 12, Colors.STROKE, 1)
            r.setPadding(k.dp(12), k.dp(8), k.dp(12), k.dp(8))
            val st = when (d.status) {
                android.net.wifi.p2p.WifiP2pDevice.AVAILABLE -> "disponível"
                android.net.wifi.p2p.WifiP2pDevice.INVITED -> "convite enviado"
                android.net.wifi.p2p.WifiP2pDevice.CONNECTED -> "ligado"
                android.net.wifi.p2p.WifiP2pDevice.FAILED -> "falhou"
                else -> "indisponível"
            }
            val col = LinearLayout(k.ctx)
            col.orientation = LinearLayout.VERTICAL
            col.addView(k.text(d.deviceName.ifBlank { "Sem nome" }, 14f, Colors.TEXT, true))
            col.addView(k.muted(st, 12f))
            r.addView(col, k.lp(0, -2, weight = 1f))
            r.addView(k.button("Ligar", Kit.Style.PRIMARY, true) { engine.p2p.connect(d) })
            list.addView(r, k.lp(bottom = 6))
        }
    }
}

/** Bluetooth: procurar e tornar visível. */
class BtSection(private val k: Kit, private val engine: CallEngine, private val host: Host) : Section() {
    private val card = k.card()
    private val status = k.muted("")
    override val view: View get() = card

    init {
        card.addView(k.sectionTitle(R.drawable.ic_bluetooth, "Bluetooth"))
        card.addView(status, k.lp(top = 8))
        val row = LinearLayout(k.ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(k.button("Procurar", Kit.Style.SECONDARY, true) { host.scanBluetooth() }, k.lp(0, -2, weight = 1f))
        row.addView(k.button("Tornar visível", Kit.Style.GHOST, true) { host.makeDiscoverable() }, k.lp(0, -2, left = 8, weight = 1f))
        card.addView(row, k.lp(top = 10))
        card.addView(k.muted("Só aparecem telemóveis com o Grfee aberto e Bluetooth ativo. Áudio a 8 kHz e alcance de poucos metros.", 12f), k.lp(top = 8))
    }

    override fun refresh() {
        status.text = when {
            !engine.bt.available -> "Este telemóvel não tem Bluetooth"
            !engine.bt.isEnabled() -> "Bluetooth desligado. Toca em Procurar para ligar."
            else -> "Bluetooth ligado"
        }
    }
}

/** Histórico de chamadas da sessão. */
class HistorySection(private val k: Kit, private val engine: CallEngine, private val limit: Int, private val title: String = "Histórico") : Section() {
    private val card = k.card()
    private val list = LinearLayout(k.ctx)
    override val view: View get() = card

    init {
        list.orientation = LinearLayout.VERTICAL
        card.addView(k.sectionTitle(R.drawable.ic_call, title))
        card.addView(list, k.lp(top = 8))
    }

    override fun refresh() {
        list.removeAllViews()
        k.historyRows(list, engine.history.take(limit))
    }
}

/** Tabela de pares e interfaces para diagnóstico. */
class PeersTableSection(private val k: Kit, private val engine: CallEngine) : Section() {
    private val card = k.card()
    private val body = k.mono("")
    override val view: View get() = card

    init {
        card.addView(k.sectionTitle(R.drawable.ic_wifi, "Rede e pares"))
        card.addView(body, k.lp(top = 10))
    }

    override fun refresh() {
        val sb = StringBuilder()
        sb.append("Interfaces:\n").append(engine.lan.describeLinks()).append("\n\nPares:\n")
        val peers = engine.allPeers()
        if (peers.isEmpty()) sb.append("(nenhum)\n")
        for (p in peers) {
            sb.append(p.name).append("  ").append(p.id).append("  ").append(p.detail).append("  ")
                .append(viaLabel(p)).append("  ").append(p.role.ifEmpty { "-" })
                .append(if (p.blocked) "  [bloqueado]" else "").append('\n')
        }
        body.text = sb.toString()
    }
}

/** Registo de eventos com copiar e limpar. */
class LogSection(private val k: Kit, private val engine: CallEngine, private val host: Host) : Section() {
    private val card = k.card()
    private val body = k.mono("", 10f)
    override val view: View get() = card

    init {
        host.registerLog(this)
        card.addView(k.sectionTitle(R.drawable.ic_pulse, "Registo"))
        val row = LinearLayout(k.ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(k.button("Copiar", Kit.Style.SECONDARY, true) {
            val cm = k.ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Grfee", engine.logText()))
            host.toast("Registo copiado")
        }, k.lp(0, -2, weight = 1f))
        row.addView(k.button("Limpar", Kit.Style.GHOST, true) { engine.clearLog() }, k.lp(0, -2, left = 8, weight = 1f))
        card.addView(row, k.lp(top = 10))
        body.setTextIsSelectable(true)
        card.addView(body, k.lp(top = 10))
    }

    override fun refresh() {
        val t = engine.logText()
        body.text = if (t.length > 16000) t.substring(t.length - 16000) else t
    }
}
