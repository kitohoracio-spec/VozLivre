package com.vozlivre.server.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.vozlivre.core.R
import com.vozlivre.core.call.CallEngine
import com.vozlivre.core.ui.Colors
import com.vozlivre.core.ui.Host
import com.vozlivre.core.ui.Kit
import com.vozlivre.core.ui.Section
import com.vozlivre.core.ui.formatSeconds
import com.vozlivre.core.ui.historyRows
import com.vozlivre.core.ui.peerRow
import com.vozlivre.server.ServerEngine

private fun roundSmall(k: Kit, icon: Int, fill: Int, tint: Int, rotate: Float = 0f, onClick: () -> Unit): FrameLayout {
    val f = FrameLayout(k.ctx)
    f.background = k.shape(fill, 44, Colors.alpha(tint, 0x55), 1)
    val i = ImageView(k.ctx)
    i.setImageResource(icon)
    i.setColorFilter(tint)
    i.rotation = rotate
    f.addView(i, FrameLayout.LayoutParams(k.dp(22), k.dp(22), Gravity.CENTER))
    f.layoutParams = LinearLayout.LayoutParams(k.dp(46), k.dp(46))
    f.setOnClickListener { onClick() }
    return f
}

/** Separador Chamadas: Ativas, Recentes e Registos. */
class CallsTabSection(private val k: Kit, private val engine: ServerEngine, private val host: Host) : Section() {
    private val root = LinearLayout(k.ctx)
    private val segHolder = FrameLayout(k.ctx)
    private val list = LinearLayout(k.ctx)
    private var tab = 0
    override val view: View get() = root
    override val live: Boolean get() = tab == 0 && engine.state == CallEngine.State.ACTIVE

    init {
        root.orientation = LinearLayout.VERTICAL
        list.orientation = LinearLayout.VERTICAL
        root.addView(segHolder)
        root.addView(list, k.lp(top = 12))
    }

    override fun refresh() {
        val active = engine.state == CallEngine.State.ACTIVE
        segHolder.removeAllViews()
        segHolder.addView(k.segmented(listOf("Ativas (${if (active) 1 else 0})", "Recentes", "Registos"), tab) { tab = it; refresh() })
        list.removeAllViews()
        when (tab) {
            0 -> {
                val p = engine.peer
                if (active && p != null) {
                    val card = k.card(true)
                    val row = LinearLayout(k.ctx)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.gravity = Gravity.CENTER_VERTICAL
                    row.addView(k.deviceTile(46, Colors.GREEN, R.drawable.ic_person))
                    val col = LinearLayout(k.ctx)
                    col.orientation = LinearLayout.VERTICAL
                    col.addView(k.text(p.name, 16f, Colors.TEXT, true))
                    val secs = ((android.os.SystemClock.elapsedRealtime() - engine.activeSince) / 1000).toInt()
                    col.addView(k.text("Em chamada • " + formatSeconds(secs), 13f, Colors.GREEN))
                    row.addView(col, k.lp(0, -2, left = 12, weight = 1f))
                    card.addView(row)
                    val tools = LinearLayout(k.ctx)
                    tools.orientation = LinearLayout.HORIZONTAL
                    tools.gravity = Gravity.END
                    tools.addView(roundSmall(k, R.drawable.ic_mic, if (engine.muted) Colors.RED else Colors.CARD_HI, if (engine.muted) Color.WHITE else Colors.TEXT) { engine.toggleMute() })
                    tools.addView(roundSmall(k, R.drawable.ic_speaker, if (engine.speaker) Colors.GREEN else Colors.CARD_HI, if (engine.speaker) Color.BLACK else Colors.TEXT) { engine.toggleSpeaker() }, k.lp(-2, -2, left = 10))
                    tools.addView(roundSmall(k, R.drawable.ic_call, Colors.RED, Color.WHITE, 135f) { engine.hangup() }, k.lp(-2, -2, left = 10))
                    card.addView(tools, k.lp(top = 12))
                    list.addView(card, k.lp(bottom = 12))
                } else {
                    list.addView(k.muted("Sem chamadas ativas."), k.lp(bottom = 12))
                }
                val others = engine.clients().filter { !it.blocked && !engine.isPending(it) && it.id != engine.peer?.id }
                if (others.isNotEmpty()) list.addView(k.text("Disponíveis", 15f, Colors.TEXT, true), k.lp(bottom = 8))
                val busy = engine.state != CallEngine.State.IDLE
                for (o in others) {
                    val b = k.button("Ligar", Kit.Style.PRIMARY, true) { host.withPerms(host.micPerms()) { engine.startCall(o) } }
                    b.isEnabled = !busy
                    b.alpha = if (busy) 0.4f else 1f
                    list.addView(k.peerRow(o, b), k.lp(bottom = 8))
                }
            }
            1 -> k.historyRows(list, engine.history.take(30))
            else -> {
                val lines = engine.logText().lines().filter { it.contains("hamada") }.takeLast(60)
                list.addView(k.mono(if (lines.isEmpty()) "Sem registos de chamadas." else lines.joinToString("\n"), 10f))
            }
        }
    }
}

class CallRulesSection(private val k: Kit, private val engine: ServerEngine) : Section() {
    private val card = k.card()
    private val approvalValue = k.text("", 13f, Colors.TEXT, true)
    override val view: View get() = card

    init {
        card.addView(k.sectionTitle(R.drawable.ic_call, "Regras de chamada"))
        card.addView(k.kv("Chamadas em simultâneo", "1"), k.lp(top = 10))
        card.addView(k.kv("Tempo limite sem resposta", "40 s"), k.lp(top = 6))
        card.addView(k.kv("Repetição do pedido de chamada", "1 s, depois 2 s"), k.lp(top = 6))
        card.addView(k.kv("Perda de ligação", "9 s sem sinal"), k.lp(top = 6))
        card.addView(k.kv("Chamada recebida sem atender", "6 s sem repetição"), k.lp(top = 6))
        card.addView(k.kv("Clientes novos", approvalValue), k.lp(top = 6))
    }

    override fun refresh() {
        approvalValue.text = if (engine.approvalRequired) "Aprovação manual" else "Aceites automaticamente"
    }
}

class CallStatsSection(private val k: Kit, private val engine: ServerEngine) : Section() {
    private val card = k.card()
    private val total = k.text("0", 13f, Colors.TEXT, true)
    private val answered = k.text("0", 13f, Colors.TEXT, true)
    private val talk = k.text("00:00", 13f, Colors.TEXT, true)
    override val view: View get() = card

    init {
        card.addView(k.sectionTitle(R.drawable.ic_bars, "Estatísticas da sessão"))
        card.addView(k.kv("Chamadas", total), k.lp(top = 10))
        card.addView(k.kv("Com conversa", answered), k.lp(top = 6))
        card.addView(k.kv("Tempo total de conversa", talk), k.lp(top = 6))
    }

    override fun refresh() {
        val h = engine.history
        total.text = h.size.toString()
        answered.text = h.count { it.seconds > 0 }.toString()
        talk.text = formatSeconds(h.sumOf { it.seconds })
    }
}
