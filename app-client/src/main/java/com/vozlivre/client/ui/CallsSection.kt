package com.vozlivre.client.ui

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.vozlivre.client.ClientEngine
import com.vozlivre.core.R
import com.vozlivre.core.ui.Colors
import com.vozlivre.core.ui.Host
import com.vozlivre.core.ui.Kit
import com.vozlivre.core.ui.Section
import com.vozlivre.core.ui.formatWhen

/** Separador Chamadas do cliente: Recentes e Favoritos, com botão Nova chamada. */
class ClientCallsSection(private val k: Kit, private val engine: ClientEngine, private val host: Host) : Section() {
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

    private fun callBack(id: String) {
        val p = engine.allPeers().firstOrNull { it.id == id }
        if (p == null) host.toast("Fora de alcance") else host.withPerms(host.micPerms()) { engine.startCall(p) }
    }

    private fun row(id: String, name: String, sub: String): LinearLayout {
        val r = LinearLayout(k.ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = k.shape(Colors.CARD, 14, Colors.STROKE, 1)
        r.setPadding(k.dp(12), k.dp(10), k.dp(12), k.dp(10))
        r.addView(k.deviceTile(42, Colors.GREEN, R.drawable.ic_person))
        val col = LinearLayout(k.ctx)
        col.orientation = LinearLayout.VERTICAL
        col.addView(k.text(name, 15f, Colors.TEXT, true))
        col.addView(k.muted(sub, 12f))
        r.addView(col, k.lp(0, -2, left = 12, weight = 1f))
        val star = ImageView(k.ctx)
        star.setImageResource(R.drawable.ic_star)
        star.setColorFilter(if (engine.isFavorite(id)) Colors.GREEN else Colors.MUTED)
        star.setPadding(k.dp(6), k.dp(6), k.dp(6), k.dp(6))
        star.setOnClickListener { engine.toggleFavorite(id, name) }
        r.addView(star, k.lp(k.dp(36), k.dp(36)))
        val phone = FrameLayout(k.ctx)
        phone.background = k.shape(Colors.alpha(Colors.GREEN, 0x1F), 40, Colors.alpha(Colors.GREEN, 0x88), 1)
        val pi = ImageView(k.ctx)
        pi.setImageResource(R.drawable.ic_call)
        pi.setColorFilter(Colors.GREEN)
        phone.addView(pi, FrameLayout.LayoutParams(k.dp(18), k.dp(18), Gravity.CENTER))
        phone.setOnClickListener { callBack(id) }
        r.addView(phone, k.lp(k.dp(40), k.dp(40), left = 6))
        return r
    }

    override fun refresh() {
        segHolder.removeAllViews()
        segHolder.addView(k.segmented(listOf("Contactos", "Recentes", "Favoritos"), tab) { tab = it; refresh() })
        list.removeAllViews()
        if (tab == 0) {
            if (engine.serverId == null) {
                list.addView(k.muted("Ainda não escolheste um servidor. Lê o QR ou escolhe um no separador Ligação."))
                list.addView(k.button("Escolher servidor", Kit.Style.SECONDARY) { host.goTo(0) }, k.lp(top = 10))
                return
            }
            val contacts = engine.contacts()
            if (contacts.isEmpty()) list.addView(k.muted("Nenhum dispositivo deste servidor está visível agora."))
            for (c in contacts) list.addView(row(c.id, c.name, if (c.isServer) "Servidor" else "Dispositivo do servidor"), k.lp(bottom = 8))
        } else if (tab == 1) {
            val items = engine.history.take(20)
            if (items.isEmpty()) list.addView(k.muted("Ainda não há chamadas nesta sessão."))
            for (h in items) list.addView(row(h.peerId, h.peerName, formatWhen(h.at)), k.lp(bottom = 8))
        } else {
            val favs = engine.favoriteList()
            if (favs.isEmpty()) list.addView(k.muted("Toca na estrela de uma chamada recente para a guardar aqui."))
            for ((id, name) in favs) {
                val visible = engine.allPeers().any { it.id == id }
                list.addView(row(id, name, if (visible) "Disponível" else "Fora de alcance"), k.lp(bottom = 8))
            }
        }
    }
}
