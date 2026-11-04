package com.vozlivre.client

import com.vozlivre.client.ui.ClientCallsSection
import com.vozlivre.client.ui.ClientConnectionSection
import com.vozlivre.client.ui.ClientStatusSection
import com.vozlivre.client.ui.OtherMethodsSection
import com.vozlivre.client.ui.ServerListSection
import com.vozlivre.core.R
import com.vozlivre.core.ui.AudioSettingsSection
import com.vozlivre.core.ui.BackgroundSection
import com.vozlivre.core.ui.BaseActivity
import com.vozlivre.core.ui.CreditsSection
import com.vozlivre.core.ui.IdentitySection
import com.vozlivre.core.ui.NavTab
import com.vozlivre.core.ui.PermissionsSection
import com.vozlivre.core.ui.Screen
import com.vozlivre.core.ui.SecondCallSection

/**
 * Interface simples do Cliente com quatro separadores: Ligação, Chamadas,
 * Áudio e Estado. A chamada ativa ocupa o ecrã inteiro.
 */
class ClientActivity : BaseActivity() {

    override val engine: ClientEngine get() = (application as ClientApp).engine

    override val roleLabel: String get() = "Cliente"

    override fun buildTabs(): List<NavTab> = listOf(
        NavTab(R.drawable.ic_wifi, "Ligação", Screen(k, listOf(
            ClientConnectionSection(k, engine, this),
            ServerListSection(k, engine, this),
            OtherMethodsSection(k, engine, this),
            IdentitySection(k, engine, this)
        ))),
        NavTab(R.drawable.ic_call, "Chamadas", Screen(k, listOf(
            header(R.drawable.ic_call, "Chamadas"),
            ClientCallsSection(k, engine, this),
            SecondCallSection(k, engine)
        ))),
        NavTab(R.drawable.ic_speaker, "Áudio", Screen(k, listOf(
            header(R.drawable.ic_speaker, "Áudio"),
            AudioSettingsSection(k, engine, this)
        ))),
        NavTab(R.drawable.ic_bars, "Estado", Screen(k, listOf(
            header(R.drawable.ic_bars, "Estado"),
            ClientStatusSection(k, engine),
            PermissionsSection(k, this),
            BackgroundSection(k, engine, this),
            CreditsSection(k)
        )))
    )
}
