package com.vozlivre.client.ui

import android.view.View
import android.widget.LinearLayout
import com.vozlivre.client.ClientEngine
import com.vozlivre.core.R
import com.vozlivre.core.ui.Colors
import com.vozlivre.core.ui.Kit
import com.vozlivre.core.ui.Section
import com.vozlivre.core.ui.formatHms
import com.vozlivre.core.ui.viaLabel

/** Separador Estado: informações da ligação. */
class ClientStatusSection(private val k: Kit, private val engine: ClientEngine) : Section() {
    private val root = LinearLayout(k.ctx)
    private val vServer = k.text("", 15f, Colors.GREEN, true)
    private val vType = k.text("", 15f, Colors.TEXT, true)
    private val vIp = k.text("", 15f, Colors.TEXT, true)
    private val vSignal = k.text("", 15f, Colors.TEXT, true)
    private val vTime = k.text("", 15f, Colors.TEXT, true)
    override val view: View get() = root
    override val live: Boolean get() = true

    init {
        root.orientation = LinearLayout.VERTICAL
        root.addView(k.infoRow(R.drawable.ic_server, "Servidor", vServer), k.lp(bottom = 10))
        root.addView(k.infoRow(R.drawable.ic_wifi, "Tipo de ligação", vType), k.lp(bottom = 10))
        root.addView(k.infoRow(R.drawable.ic_device, "IP local", vIp), k.lp(bottom = 10))
        root.addView(k.infoRow(R.drawable.ic_bars, "Força do sinal", vSignal), k.lp(bottom = 10))
        root.addView(k.infoRow(R.drawable.ic_clock, "Tempo de ligação", vTime))
    }

    override fun refresh() {
        val s = engine.selectedServerPeer()
        vServer.text = if (s != null) "Conectado" else if (engine.serverId != null) "Fora de alcance" else "Desligado"
        vServer.setTextColor(if (s != null) Colors.GREEN else Colors.AMBER)
        vType.text = if (s != null) viaLabel(s) else engine.networkLabel()
        vIp.text = engine.lan.primaryIp()
        vSignal.text = engine.wifiSignal()
        val t = engine.serverLinkSeconds()
        vTime.text = if (t >= 0) formatHms(t) else "-"
    }
}
