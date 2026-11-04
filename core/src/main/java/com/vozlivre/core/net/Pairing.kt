package com.vozlivre.core.net

import java.net.URLDecoder
import java.net.URLEncoder

/** Dados que o servidor põe no QR para o cliente se configurar sozinho. */
data class PairingInfo(val id: String, val name: String, val ips: List<String>, val password: String)

/**
 * Formato do QR: vozlivre://join?v=1&id=..&n=..&ip=a,b&pw=..
 * Cada valor vai codificado como URL, por isso a senha pode ter qualquer carácter.
 */
object Pairing {
    private const val PREFIX = "vozlivre://join?"
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun encode(info: PairingInfo): String {
        val fields = listOf(
            "v" to "1",
            "id" to info.id,
            "n" to info.name,
            "ip" to info.ips.joinToString(","),
            "pw" to info.password
        )
        return PREFIX + fields.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
    }

    /** Devolve null se o texto não for um QR de servidor VozLivre. */
    fun decode(text: String): PairingInfo? {
        val t = text.trim()
        if (!t.startsWith(PREFIX)) return null
        val map = HashMap<String, String>()
        for (part in t.substring(PREFIX.length).split("&")) {
            val i = part.indexOf('=')
            if (i <= 0) continue
            try {
                map[part.substring(0, i)] = URLDecoder.decode(part.substring(i + 1), "UTF-8")
            } catch (_: Exception) {
                return null
            }
        }
        if (map["v"] != "1") return null
        val id = map["id"]?.takeIf { it.isNotBlank() } ?: return null
        val ips = (map["ip"] ?: "").split(",").map { it.trim() }.filter { IPV4.matches(it) }
        return PairingInfo(id, map["n"]?.ifBlank { null } ?: "Servidor", ips, map["pw"] ?: "")
    }
}
