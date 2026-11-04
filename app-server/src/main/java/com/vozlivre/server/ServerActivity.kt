package com.vozlivre.server

import com.vozlivre.core.R
import com.vozlivre.core.ui.BaseActivity
import com.vozlivre.core.ui.LogSection
import com.vozlivre.core.ui.NavTab
import com.vozlivre.core.ui.PeersTableSection
import com.vozlivre.core.ui.Screen
import com.vozlivre.server.ui.CallsTabSection
import com.vozlivre.server.ui.ClientsTabSection
import com.vozlivre.server.ui.PairingSection
import com.vozlivre.server.ui.ServerDiagnosticsSection
import com.vozlivre.server.ui.ServerSessionSection
import com.vozlivre.server.ui.ServerTilesSection
import com.vozlivre.server.ui.SettingsListSection

/**
 * Painel do Servidor com cinco separadores: Ligações, Clientes, Chamadas,
 * Diagnóstico e Definições. A chamada ativa fica no separador Chamadas.
 */
class ServerActivity : BaseActivity() {

    override val engine: ServerEngine get() = (application as ServerApp).engine

    override val callTabIndex: Int get() = TAB_CALLS

    override val roleLabel: String get() = "Servidor"

    private companion object {
        /** Posição do separador Chamadas na barra inferior. */
        const val TAB_CALLS = 2
    }

    override fun buildTabs(): List<NavTab> = listOf(
        NavTab(R.drawable.ic_wifi, "Ligações", Screen(k, listOf(
            ServerSessionSection(k, engine),
            ServerTilesSection(k, engine, this),
            PairingSection(k, engine)
        ))),
        NavTab(R.drawable.ic_people, "Clientes", Screen(k, listOf(
            header(R.drawable.ic_people, "Clientes"),
            ClientsTabSection(k, engine, this)
        ))),
        NavTab(R.drawable.ic_call, "Chamadas", Screen(k, listOf(
            header(R.drawable.ic_call, "Chamadas"),
            CallsTabSection(k, engine, this)
        ))),
        NavTab(R.drawable.ic_pulse, "Diagnóstico", Screen(k, listOf(
            header(R.drawable.ic_pulse, "Diagnóstico"),
            ServerDiagnosticsSection(k, engine, this),
            PeersTableSection(k, engine),
            LogSection(k, engine, this)
        ))),
        NavTab(R.drawable.ic_settings, "Definições", Screen(k, listOf(
            header(R.drawable.ic_settings, "Definições avançadas"),
            SettingsListSection(k, engine, this)
        )))
    )
}
