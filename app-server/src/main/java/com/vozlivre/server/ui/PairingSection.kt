package com.vozlivre.server.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.vozlivre.core.R
import com.vozlivre.core.ui.Colors
import com.vozlivre.core.ui.Host
import com.vozlivre.core.ui.Kit
import com.vozlivre.core.ui.QrCodes
import com.vozlivre.core.ui.Section
import com.vozlivre.server.ServerEngine

/** Mostra o QR que o cliente lê para receber o servidor, os IPs e a senha. */
class PairingSection(private val k: Kit, private val engine: ServerEngine, private val host: Host) : Section() {
    private val card = k.card()
    private val body = LinearLayout(k.ctx)
    private val image = ImageView(k.ctx)
    private val hint = k.muted("", 12f)
    private val toggle = k.button("Mostrar código QR") { shown = !shown; refresh() }
    private var shown = false
    private var lastPayload = ""
    override val view: View get() = card

    init {
        card.addView(k.sectionTitle(R.drawable.ic_server, "Ligar clientes por QR"))
        card.addView(k.muted("O cliente lê o código e fica com este servidor, o IP e a senha configurados.", 12f), k.lp(top = 6))
        card.addView(toggle, k.lp(top = 12))
        body.orientation = LinearLayout.VERTICAL
        body.gravity = Gravity.CENTER_HORIZONTAL
        body.visibility = View.GONE
        image.setBackgroundColor(Color.WHITE)
        image.setPadding(k.dp(10), k.dp(10), k.dp(10), k.dp(10))
        body.addView(image, k.lp(k.dp(300), k.dp(300), top = 14))
        val actions = LinearLayout(k.ctx)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.addView(k.button("Copiar código", Kit.Style.SECONDARY, true) { copyCode() }, k.lp(0, -2, weight = 1f))
        actions.addView(k.button("Partilhar", Kit.Style.SECONDARY, true) { shareCode() }, k.lp(0, -2, left = 8, weight = 1f))
        body.addView(actions, k.lp(top = 12))
        hint.gravity = Gravity.CENTER
        body.addView(hint, k.lp(top = 10))
        card.addView(body)
    }

    /** Texto do QR. Serve a quem não consegue ler o código com a câmara. */
    private fun copyCode() {
        val cm = k.ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Grfee", engine.pairingPayload()))
        host.toast("Código copiado. No Cliente, toca em Colar código.")
    }

    private fun shareCode() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, engine.pairingPayload())
        try {
            k.ctx.startActivity(Intent.createChooser(send, "Partilhar código").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { host.toast("Não há apps para partilhar") }
    }

    override fun refresh() {
        body.visibility = if (shown) View.VISIBLE else View.GONE
        toggle.text = if (shown) "Esconder código QR" else "Mostrar código QR"
        if (!shown) return
        val ips = engine.lan.localIps()
        if (ips.isEmpty()) {
            image.setImageDrawable(null)
            lastPayload = ""
            hint.text = "Sem rede local ativa. Liga o Wi-Fi ou o hotspot, ou cria um grupo Wi-Fi Direct."
            hint.setTextColor(Colors.AMBER)
            return
        }
        val payload = engine.pairingPayload()
        if (payload != lastPayload) {
            lastPayload = payload
            image.setImageBitmap(QrCodes.bitmap(payload, 600))
        }
        hint.text = "IP: ${ips.joinToString(", ")}\nO código inclui a senha. Mostra-o só a quem deve ter acesso. Se a leitura falhar, sobe o brilho do ecrã e aproxima a câmara."
        hint.setTextColor(Colors.MUTED)
    }
}
