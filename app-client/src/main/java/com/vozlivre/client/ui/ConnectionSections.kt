package com.vozlivre.client.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.vozlivre.client.ClientEngine
import com.vozlivre.core.R
import com.vozlivre.core.net.Pairing
import com.vozlivre.core.ui.BtSection
import com.vozlivre.core.ui.Colors
import com.vozlivre.core.ui.DeviceFilterSection
import com.vozlivre.core.ui.Host
import com.vozlivre.core.ui.Kit
import com.vozlivre.core.ui.P2pSection
import com.vozlivre.core.ui.Section
import com.vozlivre.core.ui.manualIpRow
import com.vozlivre.core.ui.peerRow
import com.vozlivre.core.ui.viaLabel

/** Cartão "Ligado ao servidor" com botão Desligar. */
class ClientConnectionSection(private val k: Kit, private val engine: ClientEngine, private val host: Host) : Section() {
    private val card = k.card(true)
    private val content = LinearLayout(k.ctx)
    override val view: View get() = card

    init {
        content.orientation = LinearLayout.VERTICAL
        card.addView(content)
    }

    private fun scanQr() {
        host.scanQr { text ->
            val info = Pairing.decode(text)
            if (info == null) {
                host.toast("Este QR não é de um servidor Grfee")
            } else {
                engine.joinServer(info)
                host.toast("A ligar a ${info.name}...")
            }
        }
    }

    override fun refresh() {
        content.removeAllViews()
        val s = engine.selectedServerPeer()
        if (s != null) {
            content.addView(k.text("Ligado ao servidor", 18f, Colors.TEXT, true))
            val row = LinearLayout(k.ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.addView(k.deviceTile(46, Colors.GREEN, R.drawable.ic_person))
            val col = LinearLayout(k.ctx)
            col.orientation = LinearLayout.VERTICAL
            col.addView(k.text(s.name, 16f, Colors.TEXT, true))
            val ipRow = LinearLayout(k.ctx)
            ipRow.orientation = LinearLayout.HORIZONTAL
            ipRow.gravity = Gravity.CENTER_VERTICAL
            ipRow.addView(k.icon(R.drawable.ic_wifi, Colors.GREEN, 14))
            ipRow.addView(k.muted(if (s.detail.isNotEmpty()) s.detail else viaLabel(s), 13f), k.lp(-2, -2, left = 6))
            col.addView(ipRow, k.lp(top = 2))
            row.addView(col, k.lp(0, -2, left = 12, weight = 1f))
            content.addView(row, k.lp(top = 12))
            content.addView(k.button("Desligar") { engine.selectServer(null); host.toast("Desligado do servidor") }, k.lp(top = 14))
        } else if (engine.serverId != null) {
            content.addView(k.text("Servidor fora de alcance", 18f, Colors.AMBER, true))
            content.addView(k.muted("O servidor escolhido não está visível. Procura de novo."), k.lp(top = 6))
            content.addView(k.button("Ler QR do servidor") { scanQr() }, k.lp(top = 14))
            content.addView(k.button("Procurar servidores", Kit.Style.SECONDARY) { host.scanAll() }, k.lp(top = 8))
        } else {
            content.addView(k.text("Sem servidor escolhido", 18f, Colors.TEXT, true))
            content.addView(k.muted("Lê o QR do servidor, ou procura e escolhe um na lista abaixo."), k.lp(top = 6))
            content.addView(k.button("Ler QR do servidor") { scanQr() }, k.lp(top = 14))
            content.addView(k.button("Procurar servidores", Kit.Style.SECONDARY) { host.scanAll() }, k.lp(top = 8))
        }
    }
}

/** Lista de servidores encontrados (Wi-Fi com papel de servidor e dispositivos Bluetooth). */
class ServerListSection(private val k: Kit, private val engine: ClientEngine, private val host: Host) : Section() {
    private val card = k.card()
    private val list = LinearLayout(k.ctx)
    private val count = k.pill("0")
    override val view: View get() = card

    init {
        list.orientation = LinearLayout.VERTICAL
        card.addView(k.sectionTitle(R.drawable.ic_server, "Servidores encontrados", count))
        card.addView(list, k.lp(top = 10))
        card.addView(k.muted("Não aparece? Escreve o IP do servidor:", 12f), k.lp(top = 8))
        card.addView(k.manualIpRow(engine, "IP do servidor"), k.lp(top = 8))
    }

    override fun refresh() {
        list.removeAllViews()
        val servers = engine.servers()
        count.text = servers.size.toString()
        if (servers.isEmpty()) {
            list.addView(k.muted("Nenhum servidor visível. Confirma que o servidor está ligado à mesma rede."))
            return
        }
        for (p in servers) {
            val chosen = engine.serverId == p.id
            val b: View = if (chosen) k.pill("Escolhido") else k.button("Ligar", Kit.Style.PRIMARY, true) {
                engine.selectServer(p.id)
                host.toast("Servidor: ${p.name}")
            }
            list.addView(k.peerRow(p, b), k.lp(bottom = 8))
        }
    }
}

/** Outros métodos de ligação, recolhidos por defeito para manter o ecrã simples. */
class OtherMethodsSection(private val k: Kit, private val engine: ClientEngine, private val host: Host) : Section() {
    private val card = k.card()
    private val body = LinearLayout(k.ctx)
    private val p2p = P2pSection(k, engine, host, false)
    private val bt = BtSection(k, engine, host)
    private val filter = DeviceFilterSection(k, engine)
    private val toggle: TextView
    override val view: View get() = card

    init {
        body.orientation = LinearLayout.VERTICAL
        body.visibility = View.GONE
        body.addView(p2p.view, k.lp(top = 12))
        body.addView(bt.view, k.lp(top = 12))
        body.addView(filter.view, k.lp(top = 12))
        toggle = k.pill("Mostrar")
        card.addView(k.sectionTitle(R.drawable.ic_wifi, "Wi-Fi Direct e Bluetooth", toggle))
        card.addView(body)
        card.setOnClickListener {
            val open = body.visibility != View.VISIBLE
            body.visibility = if (open) View.VISIBLE else View.GONE
            toggle.text = if (open) "Esconder" else "Mostrar"
            refresh()
        }
    }

    override fun refresh() {
        if (body.visibility == View.VISIBLE) { p2p.refresh(); bt.refresh(); filter.refresh() }
    }
}
