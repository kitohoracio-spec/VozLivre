package com.vozlivre.server.ui

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import com.vozlivre.core.R
import com.vozlivre.core.call.CallEngine
import com.vozlivre.core.ui.Colors
import com.vozlivre.core.ui.Host
import com.vozlivre.core.ui.Kit
import com.vozlivre.core.ui.Section
import com.vozlivre.core.ui.historyRows
import com.vozlivre.core.ui.peerRow
import com.vozlivre.server.ServerEngine

/** Separador Clientes: Ligados, Pendentes e Histórico. */
class ClientsTabSection(private val k: Kit, private val engine: ServerEngine, private val host: Host) : Section() {
    private val root = LinearLayout(k.ctx)
    private val segHolder = FrameLayout(k.ctx)
    private val list = LinearLayout(k.ctx)
    private var tab = 0
    override val view: View get() = root

    init {
        root.orientation = LinearLayout.VERTICAL
        list.orientation = LinearLayout.VERTICAL
        root.addView(segHolder)
        root.addView(list, k.lp(top = 12))
    }

    override fun refresh() {
        val all = engine.clients().filter { !it.blocked }
        val pending = all.filter { engine.isPending(it) }
        val linked = all.filter { !engine.isPending(it) }
        segHolder.removeAllViews()
        segHolder.addView(k.segmented(listOf("Ligados (${linked.size})", "Pendentes (${pending.size})", "Histórico"), tab) { tab = it; refresh() })
        list.removeAllViews()
        val busy = engine.state != CallEngine.State.IDLE
        when (tab) {
            0 -> {
                if (linked.isEmpty()) list.addView(k.muted("Nenhum cliente encontrado. Abre o Grfee Cliente nos outros telemóveis."))
                for (p in linked) {
                    val call = k.button("Ligar", Kit.Style.PRIMARY, true) { host.withPerms(host.micPerms()) { engine.startCall(p) } }
                    call.isEnabled = !busy
                    call.alpha = if (busy) 0.4f else 1f
                    list.addView(k.peerRow(p, k.pill("Ligado"), call), k.lp(bottom = 8))
                }
                list.addView(k.navRow(R.drawable.ic_people, "Gerir clientes", "Aprovação, bloqueios e autorizações") {
                    host.openSub("Gestão de clientes", R.drawable.ic_people, clientAdminPage(k, engine, host))
                }, k.lp(top = 4))
            }
            1 -> {
                if (!engine.approvalRequired) {
                    list.addView(k.muted("A aprovação manual está desligada, por isso todos os clientes ligam sem pedido. Podes ativá-la em Gerir clientes."))
                } else if (pending.isEmpty()) {
                    list.addView(k.muted("Sem pedidos pendentes."))
                }
                for (p in pending) {
                    val ok = k.button("Aceitar", Kit.Style.PRIMARY, true) { engine.approve(p.id) }
                    val no = k.button("Recusar", Kit.Style.GHOST, true) { engine.setBlocked(p.id, true) }
                    list.addView(k.peerRow(p, ok, no), k.lp(bottom = 8))
                }
            }
            else -> k.historyRows(list, engine.history.take(30))
        }
    }
}

class ClientAdminSection(private val k: Kit, private val engine: ServerEngine, private val host: Host) : Section() {
    private val card = k.card()
    private val list = LinearLayout(k.ctx)
    private val approval = k.switchRow("Aprovar clientes manualmente", "Novos clientes ficam pendentes até os aceitares.", engine.approvalRequired) { v ->
        if (engine.approvalRequired != v) engine.setApprovalRequired(v)
    }
    override val view: View get() = card

    init {
        list.orientation = LinearLayout.VERTICAL
        card.addView(k.sectionTitle(R.drawable.ic_people, "Autorizações e bloqueios"))
        card.addView(approval, k.lp(top = 10))
        card.addView(k.divider(), k.lp(top = 12, bottom = 12))
        card.addView(list)
        val row = LinearLayout(k.ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(k.button("Limpar bloqueios", Kit.Style.GHOST, true) { engine.clearBlocked(); host.toast("Bloqueios removidos") }, k.lp(0, -2, weight = 1f))
        row.addView(k.button("Limpar aprovações", Kit.Style.GHOST, true) { engine.clearApprovals(); host.toast("Aprovações removidas") }, k.lp(0, -2, left = 8, weight = 1f))
        card.addView(row, k.lp(top = 12))
    }

    override fun refresh() {
        val sw = approval.tag as Switch
        if (sw.isChecked != engine.approvalRequired) sw.isChecked = engine.approvalRequired
        list.removeAllViews()
        val peers = engine.clients()
        if (peers.isEmpty()) { list.addView(k.muted("Nenhum cliente visível.")); return }
        for (p in peers) {
            val tag = when {
                p.blocked -> k.pill("Bloqueado", Colors.RED)
                engine.isPending(p) -> k.pill("Pendente", Colors.AMBER)
                else -> k.pill("Ativo")
            }
            val b = k.button(if (p.blocked) "Desbloquear" else "Bloquear", Kit.Style.GHOST, true) { engine.setBlocked(p.id, !p.blocked) }
            list.addView(k.peerRow(p, tag, b), k.lp(bottom = 8))
        }
    }
}

fun clientAdminPage(k: Kit, engine: ServerEngine, host: Host): List<Section> = listOf(ClientAdminSection(k, engine, host))
