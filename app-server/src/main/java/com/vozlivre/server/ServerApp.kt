package com.vozlivre.server

import android.app.Application
import com.vozlivre.core.call.EngineProvider

/** O motor do Servidor vive aqui para sobreviver à Activity. */
class ServerApp : Application(), EngineProvider {
    private lateinit var serverEngine: ServerEngine

    override val engine: ServerEngine get() = serverEngine

    override fun onCreate() {
        super.onCreate()
        serverEngine = ServerEngine(this)
    }
}
