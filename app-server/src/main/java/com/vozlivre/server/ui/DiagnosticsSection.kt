package com.vozlivre.server.ui

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.vozlivre.core.R
import com.vozlivre.core.ui.Colors
import com.vozlivre.core.ui.Host
import com.vozlivre.core.ui.Kit
import com.vozlivre.core.ui.Section
import com.vozlivre.server.ServerEngine

/** Verificação do sistema com estado geral e lista de testes. */
class ServerDiagnosticsSection(private val k: Kit, private val engine: ServerEngine, private val host: Host) : Section() {
    private class Check(val label: String, val icon: Int, val level: Int)

    private val root = LinearLayout(k.ctx)
    private val big = FrameLayout(k.ctx)
    private val bigIcon = ImageView(k.ctx)
    private val headline = k.text("", 18f, Colors.TEXT, true)
    private val sub = k.text("", 13f, Colors.GREEN)
    private val list = k.card()
    override val view: View get() = root

    init {
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        big.addView(bigIcon, FrameLayout.LayoutParams(k.dp(44), k.dp(44), Gravity.CENTER))
        root.addView(big, k.lp(k.dp(92), k.dp(92), top = 4))
        headline.gravity = Gravity.CENTER
        sub.gravity = Gravity.CENTER
        root.addView(headline, k.lp(top = 12))
        root.addView(sub, k.lp(top = 2, bottom = 14))
        list.orientation = LinearLayout.VERTICAL
        root.addView(list)
        root.addView(k.button("Executar diagnóstico", Kit.Style.SECONDARY) {
            engine.lan.wake()
            val checks = evaluate()
            engine.log("Diagnóstico executado: " + checks.joinToString(", ") { it.label + "=" + levelText(it.level) })
            refresh()
            host.toast("Diagnóstico concluído")
        }, k.lp(top = 14))
    }

    private fun levelText(l: Int) = when (l) { 0 -> "OK"; 1 -> "Atenção"; else -> "Falha" }
    private fun levelColor(l: Int) = when (l) { 0 -> Colors.GREEN; 1 -> Colors.AMBER; else -> Colors.RED }

    private fun evaluate(): List<Check> {
        val net = engine.lan.hasLinks() || engine.bt.isEnabled()
        val mic = host.allGranted(host.micPerms())
        val others = host.allGranted(host.notifPerms())
        return listOf(
            Check("Rede (${engine.networkLabel()})", R.drawable.ic_wifi, if (net) 0 else 2),
            Check("Descoberta de dispositivos", R.drawable.ic_people, if (engine.lan.hasLinks()) 0 else 2),
            Check("Permissões", R.drawable.ic_shield, if (!mic) 2 else if (!others) 1 else 0),
            Check("Sinalização de chamadas", R.drawable.ic_call, if (net) 0 else 2),
            Check("Áudio", R.drawable.ic_speaker, if (mic) 0 else 2)
        )
    }

    override fun refresh() {
        val checks = evaluate()
        val worst = checks.maxOf { it.level }
        val color = levelColor(worst)
        big.background = k.shape(Colors.alpha(color, 0x1A), 92, color, 3)
        k.glow(big, color, 8)
        bigIcon.setImageResource(if (worst == 0) R.drawable.ic_check else R.drawable.ic_info)
        bigIcon.setColorFilter(color)
        headline.text = when (worst) { 0 -> "Sistema operacional"; 1 -> "Atenção necessária"; else -> "Falhas detetadas" }
        sub.text = if (worst == 0) "Tudo a funcionar" else "Verifica os itens assinalados"
        sub.setTextColor(color)
        list.removeAllViews()
        for ((i, c) in checks.withIndex()) {
            val r = LinearLayout(k.ctx)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.addView(k.iconCircle(c.icon, 34, levelColor(c.level)))
            r.addView(k.text(c.label, 14f), k.lp(0, -2, left = 12, weight = 1f))
            r.addView(k.text(levelText(c.level), 13f, levelColor(c.level), true))
            list.addView(r, k.lp(top = if (i == 0) 0 else 10))
        }
    }
}
