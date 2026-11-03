package com.vozlivre.core.call

/**
 * Implementado pela classe Application de cada app. Os serviços e atividades do
 * núcleo usam-no para chegar ao motor sem saberem se é de Servidor ou de Cliente.
 * O motor vive na Application para sobreviver à Activity.
 */
interface EngineProvider {
    val engine: CallEngine
}
