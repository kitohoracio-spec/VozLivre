package com.vozlivre.client

import android.app.Application
import com.vozlivre.core.call.EngineProvider

/** O motor do Cliente vive aqui para sobreviver à Activity. */
class ClientApp : Application(), EngineProvider {
    private lateinit var clientEngine: ClientEngine

    override val engine: ClientEngine get() = clientEngine

    override fun onCreate() {
        super.onCreate()
        clientEngine = ClientEngine(this)
    }
}
