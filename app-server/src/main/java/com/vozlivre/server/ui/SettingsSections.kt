package com.vozlivre.server.ui

import android.view.View
import android.widget.LinearLayout
import com.vozlivre.core.R
import com.vozlivre.core.call.CallEngine
import com.vozlivre.core.net.LanTransport
import com.vozlivre.core.ui.AudioSettingsSection
import com.vozlivre.core.ui.BackgroundSection
import com.vozlivre.core.ui.BtSection
import com.vozlivre.core.ui.CreditsSection
import com.vozlivre.core.ui.HistorySection
import com.vozlivre.core.ui.Host
import com.vozlivre.core.ui.IdentitySection
import com.vozlivre.core.ui.Kit
import com.vozlivre.core.ui.LogSection
import com.vozlivre.core.ui.P2pSection
import com.vozlivre.core.ui.PeersTableSection
import com.vozlivre.core.ui.PermissionsSection
import com.vozlivre.core.ui.Section
import com.vozlivre.core.ui.manualIpRow
import com.vozlivre.server.ServerEngine

/** Interfaces de rede e ligação manual por IP. */
class NetworkSection(private val k: Kit, private val engine: ServerEngine) : Section() {
    private val card = k.card()
    override val view: View get() = card

    init {
        card.addView(k.sectionTitle(R.drawable.ic_wifi, "Rede Wi-Fi ou hotspot"))
        card.addView(k.muted("Liga os clientes à mesma rede, ou liga o hotspot deste telemóvel. Se não se encontrarem sozinhos, escreve o IP do cliente.", 12f), k.lp(top = 8))
        card.addView(k.manualIpRow(engine, "IP do cliente, ex.: 192.168.1.23"), k.lp(top = 10))
        card.addView(k.button("Reanalisar rede", Kit.Style.GHOST, true) { engine.lan.wake() }, k.lp(-2, -2, top = 10))
    }
}

/** Definições avançadas: informação técnica e ações de manutenção. */
class AdvancedSection(private val k: Kit, private val engine: ServerEngine, private val host: Host) : Section() {
    private val card = k.card()
    override val view: View get() = card

    init {
        card.addView(k.sectionTitle(R.drawable.ic_settings, "Definições avançadas"))
        card.addView(k.kv("Porta de controlo (UDP)", "${LanTransport.CONTROL_PORT}"), k.lp(top = 10))
        card.addView(k.kv("Porta de áudio (UDP)", "${LanTransport.AUDIO_PORT}"), k.lp(top = 6))
        card.addView(k.kv("Codec", "G.711 µ-law"), k.lp(top = 6))
        card.addView(k.kv("Taxa Wi-Fi / Bluetooth", "16 kHz / 8 kHz"), k.lp(top = 6))
        card.addView(k.kv("Cifra", "AES-256-GCM"), k.lp(top = 6))
        card.addView(k.kv("Tempo limite de chamada", "40 s"), k.lp(top = 6))
        card.addView(k.kv("Perda de ligação", "9 s sem sinal"), k.lp(top = 6))
        card.addView(k.kv("Id deste servidor", engine.myId), k.lp(top = 6))
        val row = LinearLayout(k.ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(k.button("Reiniciar motor", Kit.Style.SECONDARY, true) {
            if (engine.state != CallEngine.State.IDLE) { host.toast("Termina a chamada primeiro"); return@button }
            engine.stopAll(); engine.start(); engine.startBluetooth()
            host.toast("Motor reiniciado")
        }, k.lp(0, -2, weight = 1f))
        row.addView(k.button("Limpar bloqueios", Kit.Style.GHOST, true) { engine.clearBlocked(); host.toast("Bloqueios removidos") }, k.lp(0, -2, left = 8, weight = 1f))
        card.addView(row, k.lp(top = 12))
    }
}

/** Lista de subpáginas de Definições avançadas. */
class SettingsListSection(private val k: Kit, private val engine: ServerEngine, private val host: Host) : Section() {
    private val root = LinearLayout(k.ctx)
    override val view: View get() = root

    private fun row(icon: Int, title: String, sub: String, build: () -> List<Section>) {
        root.addView(k.navRow(icon, title, sub) { host.openSub(title, icon, build()) }, k.lp(bottom = 10))
    }

    init {
        root.orientation = LinearLayout.VERTICAL
        row(R.drawable.ic_wifi, "Gestão da rede", "Wi-Fi, Wi-Fi Direct, Bluetooth e portas") {
            listOf(NetworkSection(k, engine), P2pSection(k, engine, host, true), BtSection(k, engine, host), AdvancedSection(k, engine, host))
        }
        row(R.drawable.ic_people, "Gestão de clientes", "Autorizações, bloqueios e limites") { clientAdminPage(k, engine, host) }
        row(R.drawable.ic_call, "Gestão de chamadas", "Regras, estatísticas e eventos") {
            listOf(CallRulesSection(k, engine), CallStatsSection(k, engine), HistorySection(k, engine, 50))
        }
        row(R.drawable.ic_speaker, "Áudio", "Volume, microfone, saída e qualidade") { listOf(AudioSettingsSection(k, engine, host)) }
        row(R.drawable.ic_shield, "Segurança", "Senha, cifra e permissões") {
            listOf(IdentitySection(k, engine, host), PermissionsSection(k, host))
        }
        row(R.drawable.ic_pulse, "Segundo plano", "Escuta com o ecrã apagado e bateria") {
            listOf(BackgroundSection(k, engine, host))
        }
        row(R.drawable.ic_doc, "Registos e logs", "Eventos, erros e exportação") {
            listOf(LogSection(k, engine, host), PeersTableSection(k, engine))
        }
        row(R.drawable.ic_info, "Sobre", "Informações da aplicação") { listOf(CreditsSection(k)) }
    }
}
