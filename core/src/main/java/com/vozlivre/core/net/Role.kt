package com.vozlivre.core.net

/**
 * Papel que cada app anuncia na rede (campo final da mensagem HELLO).
 * Dispositivos Bluetooth não anunciam papel: ficam com o texto vazio.
 */
enum class Role(val code: String) {
    SERVER("S"),
    CLIENT("C")
}
