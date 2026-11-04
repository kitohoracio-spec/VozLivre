package com.vozlivre.core.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.vozlivre.core.R
import com.vozlivre.core.call.CallEngine
import com.vozlivre.core.net.Peer
import com.vozlivre.core.net.PeerKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Um ecrã da barra inferior: uma lista de secções dentro de um ScrollView. */
class Screen(k: Kit, private val sections: List<Section>) {
    val view: ScrollView = ScrollView(k.ctx)

    /** Recebe (variação, posição) cada vez que o ecrã rola. Serve para esconder e mostrar a barra inferior. */
    var onScroll: ((Int, Int) -> Unit)? = null

    init {
        view.isVerticalScrollBarEnabled = false
        view.overScrollMode = View.OVER_SCROLL_NEVER
        val col = LinearLayout(k.ctx)
        col.orientation = LinearLayout.VERTICAL
        // Espaço no fim para o conteúdo passar por cima da barra inferior flutuante.
        col.setPadding(k.dp(16), k.dp(8), k.dp(16), k.dp(104))
        for (s in sections) col.addView(s.view, k.lp(bottom = 12))
        view.addView(col)
        view.setOnScrollChangeListener { _, _, y, _, oldY -> onScroll?.invoke(y - oldY, y) }
    }

    fun refresh() { for (s in sections) s.refresh() }
    fun refreshLive() { for (s in sections) if (s.live) s.refresh() }
}

fun viaLabel(p: Peer): String = when {
    p.kind == PeerKind.BLUETOOTH -> "Bluetooth"
    p.isP2p -> "Wi-Fi Direct"
    else -> "Wi-Fi"
}

fun formatSeconds(s: Int): String = "%02d:%02d".format(s / 60, s % 60)

fun formatHms(s: Int): String = "%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)

fun formatWhen(at: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance()
    then.timeInMillis = at
    val hm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    val dayDiff = now.get(java.util.Calendar.DAY_OF_YEAR) - then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Hoje • $hm"
        sameYear && dayDiff == 1 -> "Ontem • $hm"
        else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(at)) + " • " + hm
    }
}

/** Linhas do histórico de chamadas. */
fun Kit.historyRows(container: LinearLayout, items: List<CallEngine.CallRecord>) {
    if (items.isEmpty()) {
        container.addView(muted("Ainda não há chamadas nesta sessão."), lp(top = 6))
        return
    }
    for (h in items) {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = shape(Colors.SURFACE, 14, Colors.STROKE, 1)
        r.setPadding(dp(12), dp(10), dp(12), dp(10))
        r.addView(deviceTile(40, Colors.GREEN, R.drawable.ic_person))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.addView(text(h.peerName, 15f, Colors.TEXT, true))
        col.addView(muted("${formatWhen(h.at)} · ${if (h.seconds > 0) formatSeconds(h.seconds) else "sem conversa"} · ${h.result}", 12f))
        r.addView(col, lp(0, -2, left = 12, weight = 1f))
        r.addView(pill(if (h.incoming) "Recebida" else "Efetuada", if (h.incoming) Colors.AMBER else Colors.GREEN))
        container.addView(r, lp(bottom = 8))
    }
}

fun Kit.peerRow(p: Peer, vararg actions: View): LinearLayout {
    val row = LinearLayout(ctx)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.background = shape(Colors.SURFACE, 14, Colors.STROKE, 1)
    row.setPadding(dp(12), dp(10), dp(12), dp(10))
    row.alpha = if (p.blocked) 0.5f else 1f
    row.addView(deviceTile(42, if (p.blocked) Colors.MUTED else Colors.GREEN))
    val col = LinearLayout(ctx)
    col.orientation = LinearLayout.VERTICAL
    col.addView(text(p.name, 15f, Colors.TEXT, true))
    val sub = StringBuilder()
    if (p.detail.isNotEmpty()) sub.append(p.detail).append(" · ")
    sub.append(viaLabel(p))
    if (p.role == "S") sub.append(" · Servidor")
    col.addView(muted(sub.toString(), 12f))
    row.addView(col, lp(0, -2, left = 12, weight = 1f))
    for (a in actions) row.addView(a, lp(-2, -2, left = 6))
    return row
}

fun Kit.manualIpRow(engine: CallEngine, hint: String): LinearLayout {
    val ip = input(hint)
    return inputRow(ip, button("Procurar", Kit.Style.SECONDARY) {
        val v = ip.text.toString().trim()
        if (v.isNotEmpty()) engine.lan.probe(v)
    })
}
