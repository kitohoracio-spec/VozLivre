package com.vozlivre.core.ui

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.vozlivre.core.R
import com.vozlivre.core.call.CallEngine

/** Nome e senha das chamadas. */
class IdentitySection(private val k: Kit, private val engine: CallEngine, private val host: Host) : Section() {
    private val card = k.card()
    private val name = k.input("O teu nome")
    private val pw = k.input("Nova senha das chamadas", true)
    private val info = k.muted("", 12f)
    override val view: View get() = card

    init {
        card.addView(k.sectionTitle(R.drawable.ic_shield, "Identificação e segurança"))
        name.setText(engine.displayName)
        card.addView(k.inputRow(name, k.button("Guardar", Kit.Style.SECONDARY) {
            engine.setName(name.text.toString())
            host.toast("Nome guardado")
        }), k.lp(top = 12))
        card.addView(k.inputRow(pw, k.button("Guardar", Kit.Style.SECONDARY) {
            engine.setPassword(pw.text.toString())
            pw.setText("")
            host.toast("Senha guardada")
            refresh()
        }), k.lp(top = 8))
        card.addView(info, k.lp(top = 8))
        card.addView(k.muted("Cifra AES-256-GCM. Os dois telemóveis precisam da mesma senha.", 12f), k.lp(top = 4))
    }

    override fun refresh() {
        if (engine.usingDefaultPassword) {
            info.text = "Senha padrão ativa: qualquer VozLivre na rede pode decifrar."
            info.setTextColor(Colors.AMBER)
        } else {
            info.text = "Senha personalizada definida."
            info.setTextColor(Colors.GREEN)
        }
    }
}

/** Escuta em segundo plano e otimização de bateria. */
class BackgroundSection(private val k: Kit, private val engine: CallEngine, private val host: Host) : Section() {
    private val card = k.card()
    private val row = k.switchRow("Receber chamadas em segundo plano", "Mantém a app à escuta com o ecrã apagado. Gasta mais bateria.", engine.listenInBackground) { v ->
        if (engine.listenInBackground != v) engine.setListen(v)
    }
    override val view: View get() = card

    init {
        card.addView(k.sectionTitle(R.drawable.ic_pulse, "Segundo plano"))
        card.addView(row, k.lp(top = 10))
        card.addView(k.button("Definições de bateria", Kit.Style.GHOST, true) { host.openBatterySettings() }, k.lp(-2, -2, top = 10))
    }

    override fun refresh() {
        val sw = row.tag as Switch
        if (sw.isChecked != engine.listenInBackground) sw.isChecked = engine.listenInBackground
    }
}

/** Estado das permissões com pedido direto. */
class PermissionsSection(private val k: Kit, private val host: Host) : Section() {
    private val card = k.card()
    private val list = LinearLayout(k.ctx)
    override val view: View get() = card

    init {
        list.orientation = LinearLayout.VERTICAL
        card.addView(k.sectionTitle(R.drawable.ic_shield, "Permissões"))
        card.addView(list, k.lp(top = 4))
    }

    private fun item(label: String, perms: List<String>) {
        if (perms.isEmpty()) return
        val ok = host.allGranted(perms)
        val r = LinearLayout(k.ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.addView(k.text(label, 14f), k.lp(0, -2, weight = 1f))
        r.addView(k.pill(if (ok) "Concedida" else "Em falta", if (ok) Colors.GREEN else Colors.AMBER))
        if (!ok) r.addView(k.button("Pedir", Kit.Style.GHOST, true) { host.withPerms(perms) { } }, k.lp(-2, -2, left = 8))
        list.addView(r, k.lp(top = 8))
    }

    override fun refresh() {
        list.removeAllViews()
        item("Microfone", host.micPerms())
        item("Notificações", host.notifPerms())
        item("Bluetooth", host.btPerms())
        item("Wi-Fi Direct", host.p2pPerms())
    }
}

/** Sobre / Créditos com a assinatura do projeto. */
class CreditsSection(private val k: Kit) : Section() {
    private val card = k.card(true)
    override val view: View get() = card

    init {
        val version = try { k.ctx.packageManager.getPackageInfo(k.ctx.packageName, 0).versionName } catch (e: Exception) { "" }
        card.addView(k.sectionTitle(R.drawable.ic_pulse, "Sobre / Créditos"))
        val word = k.text("VozLivre", 26f, Colors.GREEN, true)
        card.addView(word, k.lp(top = 12))
        card.addView(k.text(Brand.SIGNATURE, 14f, Colors.TEXT, true), k.lp(top = 4))
        card.addView(k.muted("Chamadas de voz sem Internet por Wi-Fi, Wi-Fi Direct e Bluetooth, com áudio cifrado.", 13f), k.lp(top = 8))
        card.addView(k.muted("Versão $version", 12f), k.lp(top = 8))
    }
}

/** Volume, microfone, saída de áudio e dados de qualidade. */
class AudioSettingsSection(private val k: Kit, private val engine: CallEngine, private val host: Host) : Section() {
    private val root = LinearLayout(k.ctx)
    private val volValue = k.text("", 14f, Colors.TEXT, true)
    private val micValue = k.text("", 14f, Colors.TEXT, true)
    private val outValue = k.text("", 14f, Colors.TEXT)
    private val volSeek = SeekBar(k.ctx)
    private val micSeek = SeekBar(k.ctx)
    override val view: View get() = root

    private fun styleSeek(s: SeekBar) {
        s.max = 100
        s.progressTintList = ColorStateList.valueOf(Colors.GREEN)
        s.thumbTintList = ColorStateList.valueOf(Colors.GREEN)
        s.progressBackgroundTintList = ColorStateList.valueOf(Colors.STROKE)
    }

    private fun control(icon: Int, label: String, value: TextView, seek: SeekBar): LinearLayout {
        val c = k.card()
        val top = LinearLayout(k.ctx)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(k.text(label, 15f, Colors.TEXT, true), k.lp(0, -2, weight = 1f))
        top.addView(value)
        c.addView(top)
        val row = LinearLayout(k.ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(k.icon(icon, Colors.GREEN, 20))
        row.addView(seek, k.lp(0, -2, left = 8, weight = 1f))
        c.addView(row, k.lp(top = 8))
        return c
    }

    init {
        root.orientation = LinearLayout.VERTICAL
        styleSeek(volSeek)
        styleSeek(micSeek)
        volSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                volValue.text = "$p%"
                if (fromUser) host.setVolume(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        micSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                micValue.text = "$p%"
                if (fromUser) engine.setMicPercent(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        root.addView(control(R.drawable.ic_speaker, "Volume do altifalante", volValue, volSeek), k.lp(bottom = 12))
        root.addView(control(R.drawable.ic_mic, "Microfone", micValue, micSeek), k.lp(bottom = 4))
        root.addView(k.muted("No microfone, 50% é o ganho normal e 100% quadruplica o nível. Sobe se a outra pessoa te ouve baixo.", 12f), k.lp(bottom = 12))

        val out = k.card()
        out.addView(k.text("Saída de áudio", 15f, Colors.TEXT, true))
        val pick = LinearLayout(k.ctx)
        pick.orientation = LinearLayout.HORIZONTAL
        pick.gravity = Gravity.CENTER_VERTICAL
        pick.background = k.shape(Colors.SURFACE, 12, Colors.STROKE, 1)
        pick.setPadding(k.dp(14), k.dp(12), k.dp(14), k.dp(12))
        pick.addView(outValue, k.lp(0, -2, weight = 1f))
        val arrow = k.icon(R.drawable.ic_chevron, Colors.MUTED, 18)
        arrow.rotation = 90f
        pick.addView(arrow)
        pick.setOnClickListener { chooseOutput() }
        out.addView(pick, k.lp(top = 10))
        root.addView(out, k.lp(bottom = 12))

        val info = k.card()
        info.addView(k.sectionTitle(R.drawable.ic_info, "Qualidade"))
        info.addView(k.kv("Codec", "G.711 µ-law"), k.lp(top = 10))
        info.addView(k.kv("Taxa Wi-Fi / Bluetooth", "16 kHz / 8 kHz"), k.lp(top = 6))
        info.addView(k.kv("Cifra", "AES-256-GCM"), k.lp(top = 6))
        root.addView(info)
    }

    private fun chooseOutput() {
        val items = arrayOf("Altifalante do telemóvel", "Auricular")
        AlertDialog.Builder(k.ctx, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Saída de áudio")
            .setSingleChoiceItems(items, if (engine.defaultSpeaker) 0 else 1) { d, which ->
                val sp = which == 0
                engine.setDefaultSpeaker(sp)
                if (engine.state == CallEngine.State.ACTIVE && engine.speaker != sp) engine.toggleSpeaker()
                d.dismiss()
                refresh()
            }
            .show()
    }

    override fun refresh() {
        if (!volSeek.isPressed) { volSeek.progress = host.getVolume(); volValue.text = "${volSeek.progress}%" }
        if (!micSeek.isPressed) { micSeek.progress = engine.micPercent; micValue.text = "${engine.micPercent}%" }
        outValue.text = if (engine.defaultSpeaker) "Altifalante do telemóvel" else "Auricular"
    }
}
