package com.vozlivre.server.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.vozlivre.core.R
import com.vozlivre.core.call.CallEngine
import com.vozlivre.core.ui.Colors
import com.vozlivre.core.ui.Host
import com.vozlivre.core.ui.Kit
import com.vozlivre.core.ui.Section
import com.vozlivre.core.ui.formatHms
import com.vozlivre.server.ServerEngine

/** Cartão "Sessão ativa" com tempo de sessão. */
class ServerSessionSection(private val k: Kit, private val engine: ServerEngine) : Section() {
    private val card = k.card(true)
    private val title = k.text("", 17f, Colors.TEXT, true)
    private val dot = k.dot(Colors.AMBER, 12)
    private val status = k.text("", 13f, Colors.GREEN, true)
    private val time = k.muted("", 12f)
    override val view: View get() = card
    override val live: Boolean get() = true

    init {
        val row = LinearLayout(k.ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(k.iconCircle(R.drawable.ic_pulse, 44))
        val col = LinearLayout(k.ctx)
        col.orientation = LinearLayout.VERTICAL
        col.addView(title)
        val st = LinearLayout(k.ctx)
        st.orientation = LinearLayout.HORIZONTAL
        st.gravity = Gravity.CENTER_VERTICAL
        st.addView(dot)
        st.addView(status, k.lp(-2, -2, left = 6))
        col.addView(st, k.lp(top = 3))
        row.addView(col, k.lp(0, -2, left = 12, weight = 1f))
        row.addView(time)
        card.addView(row)
    }

    override fun refresh() {
        val up = engine.lan.hasLinks()
        title.text = if (up) "Sessão ativa" else "Sessão sem rede"
        k.recolorDot(dot, if (up) Colors.GREEN else Colors.AMBER)
        status.text = if (up) "Online" else "Sem rede"
        status.setTextColor(if (up) Colors.GREEN else Colors.AMBER)
        time.text = "Tempo: " + formatHms(engine.sessionSeconds())
    }
}

/** Mosaicos 2x2 da visão geral. */
class ServerTilesSection(private val k: Kit, private val engine: ServerEngine, private val host: Host) : Section() {
    private val root = LinearLayout(k.ctx)
    private val vClients = k.text("0", 20f, Colors.TEXT, true)
    private val vCalls = k.text("0", 20f, Colors.TEXT, true)
    private val vPending = k.text("0", 20f, Colors.TEXT, true)
    private val vNet = k.text("", 18f, Colors.GREEN, true)
    override val view: View get() = root

    init {
        root.orientation = LinearLayout.VERTICAL
        val r1 = LinearLayout(k.ctx)
        r1.orientation = LinearLayout.HORIZONTAL
        val t1 = k.statTile(R.drawable.ic_people, "Clientes ligados", vClients)
        val t2 = k.statTile(R.drawable.ic_call, "Chamadas ativas", vCalls)
        t1.setOnClickListener { host.goTo(1) }
        t2.setOnClickListener { host.goTo(2) }
        r1.addView(t1, k.lp(0, -2, weight = 1f))
        r1.addView(t2, k.lp(0, -2, left = 12, weight = 1f))
        val r2 = LinearLayout(k.ctx)
        r2.orientation = LinearLayout.HORIZONTAL
        val t3 = k.statTile(R.drawable.ic_info, "Pedidos pendentes", vPending)
        val t4 = k.statTile(R.drawable.ic_wifi, "Rede", vNet)
        t3.setOnClickListener { host.goTo(1) }
        r2.addView(t3, k.lp(0, -2, weight = 1f))
        r2.addView(t4, k.lp(0, -2, left = 12, weight = 1f))
        root.addView(r1)
        root.addView(r2, k.lp(top = 12))
    }

    override fun refresh() {
        val all = engine.clients().filter { !it.blocked }
        vPending.text = all.count { engine.isPending(it) }.toString()
        vClients.text = all.count { !engine.isPending(it) }.toString()
        vCalls.text = if (engine.state == CallEngine.State.ACTIVE) "1" else "0"
        vNet.text = engine.networkLabel()
    }
}
