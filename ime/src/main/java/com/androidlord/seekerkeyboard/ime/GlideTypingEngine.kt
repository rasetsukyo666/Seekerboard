package com.androidlord.seekerkeyboard.ime

import kotlin.math.abs

data class GlideResolution(
    val committedText: String,
    val suggestions: List<String>,
    val rawPath: String,
)

object GlideTypingEngine {
    fun resolve(language: KeyboardLanguage, rawPath: String): GlideResolution {
        val normalizedPath = normalize(rawPath)
        if (normalizedPath.length < 2) {
            return GlideResolution(
                committedText = rawPath,
                suggestions = emptyList(),
                rawPath = normalizedPath,
            )
        }

        val ranked = lexicon(language)
            .distinct()
            .map { candidate -> candidate to score(normalizedPath, normalize(candidate)) }
            .filter { (_, score) -> score > 8 }
            .sortedByDescending { (_, score) -> score }

        val best = ranked.firstOrNull()?.first ?: normalizedPath
        val suggestions = buildList {
            add(best)
            ranked.asSequence()
                .map { it.first }
                .filter { it != best }
                .take(3)
                .forEach(::add)
        }

        return GlideResolution(
            committedText = best,
            suggestions = suggestions,
            rawPath = normalizedPath,
        )
    }

    private fun score(path: String, candidate: String): Int {
        if (candidate.isBlank()) return Int.MIN_VALUE
        val editDistancePenalty = levenshtein(path, candidate) * 7
        val sameFirst = if (path.firstOrNull() == candidate.firstOrNull()) 12 else 0
        val sameLast = if (path.lastOrNull() == candidate.lastOrNull()) 8 else 0
        val subsequenceBonus = if (isSubsequence(path, candidate)) 28 else 0
        val prefixBonus = commonPrefixLength(path, candidate) * 6
        val bigramBonus = bigramOverlap(path, candidate) * 5
        val lengthPenalty = abs(path.length - candidate.length) * 3
        val exactBonus = if (path == candidate) 40 else 0
        return sameFirst + sameLast + subsequenceBonus + prefixBonus + bigramBonus + exactBonus - editDistancePenalty - lengthPenalty
    }

    private fun normalize(value: String): String {
        return buildString {
            var previous: Char? = null
            value.lowercase().forEach { char ->
                if (!char.isLetter()) return@forEach
                if (previous != char) {
                    append(char)
                    previous = char
                }
            }
        }
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        for (index in 0 until limit) {
            if (a[index] != b[index]) return index
        }
        return limit
    }

    private fun bigramOverlap(a: String, b: String): Int {
        if (a.length < 2 || b.length < 2) return 0
        val aBigrams = a.windowed(2).toSet()
        val bBigrams = b.windowed(2).toSet()
        return aBigrams.intersect(bBigrams).size
    }

    private fun isSubsequence(path: String, candidate: String): Boolean {
        var pathIndex = 0
        for (char in candidate) {
            if (pathIndex < path.length && path[pathIndex] == char) {
                pathIndex += 1
            }
        }
        return pathIndex == path.length
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost,
                )
            }
            for (j in previous.indices) {
                previous[j] = current[j]
            }
        }
        return previous[b.length]
    }

    private fun lexicon(language: KeyboardLanguage): List<String> {
        val commonWalletTerms = listOf(
            "solana", "skr", "stake", "staking", "wallet", "send", "receive",
            "balance", "account", "accounts", "merge", "consolidate", "validator",
            "mobile", "keyboard", "private", "address", "session", "refresh",
        )
        val languageWords = when (language) {
            KeyboardLanguage.ENGLISH -> listOf(
                "hello", "help", "hey", "good", "great", "daily", "drive", "driven", "typing",
                "theme", "themes", "color", "colors", "clipboard", "custom", "settings",
                "connect", "connected", "disconnect", "natural", "seamless", "phone", "device",
                "stakehouse", "mergeable", "quick", "paste", "history", "space", "number",
                "language", "layout", "compact", "comfort", "thumb", "gesture", "glide",
                "cursor", "delete", "word", "smart", "predict", "correction", "smooth",
                "screen", "keyboard", "message", "panel", "utility", "theme", "privacy",
            )
            KeyboardLanguage.SPANISH -> listOf(
                "hola", "ayuda", "bueno", "genial", "diario", "escribir", "tema", "temas",
                "color", "colores", "portapapeles", "personal", "configuracion", "ajustes",
                "conectar", "conectado", "desconectar", "natural", "fluido", "telefono",
                "dispositivo", "rapido", "pegar", "historial", "idioma", "diseno", "gesto",
                "cursor", "borrar", "palabra", "suave", "correccion", "pantalla", "privado",
                "cuenta", "cuentas", "saldo", "validar", "teclado", "mensaje", "panel",
            )
            KeyboardLanguage.PORTUGUESE -> listOf(
                "ola", "ajuda", "bom", "otimo", "diario", "digitar", "tema", "temas",
                "cor", "cores", "transferencia", "personal", "configuracao", "ajustes",
                "conectar", "conectado", "desconectar", "natural", "fluido", "telefone",
                "dispositivo", "rapido", "colar", "historico", "idioma", "layout", "gesto",
                "cursor", "apagar", "palavra", "suave", "correcao", "tela", "privado",
                "conta", "contas", "saldo", "validador", "teclado", "mensagem", "painel",
            )
        }
        return (commonWalletTerms + languageWords).map(::normalize).filter { it.length > 1 }
    }
}
