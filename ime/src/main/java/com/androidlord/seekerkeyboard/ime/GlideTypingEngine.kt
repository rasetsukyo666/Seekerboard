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

    fun suggestCorrections(
        language: KeyboardLanguage,
        rawWord: String,
        previousWord: String = "",
        limit: Int = 4,
    ): List<String> {
        val normalized = normalize(rawWord)
        val previous = normalize(previousWord)
        val words = lexicon(language).distinct()
        if (normalized.isBlank()) {
            return nextWordSuggestions(language, previous, limit)
        }

        val prefixMatches = words
            .filter { it.startsWith(normalized) && it != normalized }
            .sortedWith(compareBy<String>({ prefixDistance(normalized, it) }, { it.length }, { it }))

        val infixMatches = words
            .filter { !it.startsWith(normalized) && it.contains(normalized) }
            .sortedWith(compareBy<String>({ it.length }, { it }))

        val fuzzyMatches = words
            .map { it to score(normalized, normalize(it)) }
            .filter { (word, score) ->
                word != normalized &&
                    score > when {
                        normalized.length <= 1 -> 18
                        normalized.length == 2 -> 14
                        else -> 9
                    }
            }
            .sortedByDescending { (_, score) -> score }
            .map { it.first }

        return (prefixMatches + infixMatches + fuzzyMatches)
            .distinct()
            .map { applyOriginalCase(rawWord, it) }
            .take(limit)
    }

    fun bestAutocorrect(language: KeyboardLanguage, rawWord: String): String? {
        val normalized = normalize(rawWord)
        if (normalized.length < 5) return null
        val ranked = lexicon(language)
            .distinct()
            .map { it to normalize(it) }
            .filter { (_, candidate) -> candidate.firstOrNull() == normalized.firstOrNull() }
            .map { (source, candidate) -> Triple(source, candidate, score(normalized, candidate)) }
            .sortedByDescending { it.third }
        val best = ranked.firstOrNull() ?: return null
        val distance = levenshtein(normalized, best.second)
        return if (distance <= 1 && best.third >= 24) best.first else null
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
                if (!char.isLetter() && char != '\'') return@forEach
                if (previous != char) {
                    append(char)
                    previous = char
                }
            }
        }
    }

    private fun applyOriginalCase(rawWord: String, candidate: String): String {
        if (rawWord.isBlank()) return candidate
        return when {
            rawWord.all { it.isUpperCase() } -> candidate.uppercase()
            rawWord.firstOrNull()?.isUpperCase() == true -> candidate.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
            else -> candidate
        }
    }

    private fun prefixDistance(typed: String, candidate: String): Int {
        return (candidate.length - typed.length).coerceAtLeast(0)
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
            "connect", "connected", "connection", "disconnect", "transaction",
            "review", "approval", "cluster", "mainnet", "devnet", "seed",
            "vault", "seeker", "phone", "sign", "signing",
        )
        val languageWords = when (language) {
            KeyboardLanguage.ENGLISH -> listOf(
                "a", "about", "account", "after", "all", "allow", "already", "also", "and", "any",
                "app", "approve", "around", "back", "because", "better", "between", "button", "can",
                "change", "check", "clipboard", "close", "color", "connect", "connected", "connection",
                "custom", "daily", "default", "delete", "device", "disconnect", "done", "drawer",
                "drive", "emoji", "every", "feel", "fluid", "for", "from", "good", "great", "have",
                "hello", "help", "history", "hold", "how", "improve", "input", "just", "keyboard",
                "key", "keys", "lag", "language", "layout", "link", "list", "main", "make", "message",
                "more", "need", "next", "not", "number", "open", "panel", "paste", "phone", "polish",
                "private", "quality", "quick", "return", "review", "scroll", "secure", "security",
                "session", "settings", "should", "show", "sign", "smooth", "space", "start", "still",
                "strip", "suggest", "suggestion", "suggestions", "tap", "text", "that", "the", "theme",
                "there", "these", "they", "this", "toggle", "typing", "unlock", "use", "useful",
                "validator", "wallet", "want", "when", "with", "word", "words", "work", "would", "you",
            )
            KeyboardLanguage.SPANISH -> listOf(
                "a", "abrir", "ajustes", "algo", "aprobar", "aqui", "asi", "ayuda", "borrar", "boton",
                "bueno", "calidad", "cambiar", "cartera", "cerrar", "color", "conectar", "conectado",
                "configuracion", "correcto", "cuenta", "cuentas", "cursor", "de", "deberia",
                "desconectar", "diseno", "dispositivo", "emoji", "en", "escribir", "esta", "este",
                "fluido", "genial", "gesto", "historial", "hola", "idioma", "lista", "mas", "mensaje",
                "mejor", "mover", "natural", "necesito", "panel", "pantalla", "palabra", "palabras",
                "para", "pegar", "personal", "privado", "rapido", "revisar", "seguridad", "sesion",
                "suave", "sugerencia", "sugerencias", "teclado", "tema", "telefono", "usar", "util",
                "validar",
            )
            KeyboardLanguage.PORTUGUESE -> listOf(
                "abrir", "ajuda", "ajustes", "apagar", "aprovar", "assim", "bom", "botao", "carteira",
                "colar", "conectar", "conectado", "configuracao", "conta", "contas", "cor", "cursor",
                "de", "desconectar", "digitar", "dispositivo", "emoji", "esta", "fluido", "gesto",
                "historico", "idioma", "layout", "lista", "mais", "mensagem", "melhor", "mover",
                "natural", "ola", "otimo", "painel", "palavra", "palavras", "para", "privado",
                "qualidade", "rapido", "seguranca", "sessao", "suave", "sugestao", "sugestoes",
                "teclado", "telefone", "tema", "usar", "util", "validador",
            )
        }
        return (commonWalletTerms + languageWords).map(::normalize).filter { it.length > 1 }
    }

    private fun nextWordSuggestions(
        language: KeyboardLanguage,
        previousWord: String,
        limit: Int,
    ): List<String> {
        val contextual = nextWordMap(language)[previousWord].orEmpty()
        val starters = starterWords(language)
        return (contextual + starters).distinct().take(limit)
    }

    private fun starterWords(language: KeyboardLanguage): List<String> {
        return when (language) {
            KeyboardLanguage.ENGLISH -> listOf("the", "and", "for", "you", "wallet", "keyboard", "connect", "settings")
            KeyboardLanguage.SPANISH -> listOf("de", "la", "para", "teclado", "cartera", "conectar", "ajustes")
            KeyboardLanguage.PORTUGUESE -> listOf("de", "para", "teclado", "carteira", "conectar", "ajustes")
        }
    }

    private fun nextWordMap(language: KeyboardLanguage): Map<String, List<String>> {
        return when (language) {
            KeyboardLanguage.ENGLISH -> mapOf(
                "connect" to listOf("wallet", "again", "now", "session"),
                "wallet" to listOf("connect", "session", "review", "address"),
                "send" to listOf("sol", "skr", "it", "now"),
                "stake" to listOf("sol", "skr", "account", "validator"),
                "private" to listOf("keyboard", "wallet", "mode"),
                "keyboard" to listOf("settings", "wallet", "theme", "suggestions"),
                "clipboard" to listOf("history", "paste", "clear"),
                "theme" to listOf("color", "settings", "borderless"),
            )
            KeyboardLanguage.SPANISH -> mapOf(
                "conectar" to listOf("cartera", "ahora", "sesion"),
                "cartera" to listOf("conectar", "direccion", "sesion"),
                "teclado" to listOf("privado", "tema", "ajustes"),
                "portapapeles" to listOf("pegar", "historial"),
            )
            KeyboardLanguage.PORTUGUESE -> mapOf(
                "conectar" to listOf("carteira", "agora", "sessao"),
                "carteira" to listOf("conectar", "endereco", "sessao"),
                "teclado" to listOf("privado", "tema", "ajustes"),
                "historico" to listOf("colar", "limpar"),
            )
        }
    }
}
