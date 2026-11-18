package com.example.domain.parser

import java.text.Normalizer
import java.util.Locale

object TextNormalizer {

    private val STOP_WORDS = setOf(
        // Indonesian common stopwords
        "yang", "dan", "di", "ke", "dari", "pada", "dalam", "untuk", "dengan",
        "adalah", "itu", "ini", "atau", "sebagai", "oleh", "tersebut", "akan",
        "dapat", "juga", "tidak", "bukan", "ada", "karena", "agar", "jika",
        "bila", "bahwa", "saat", "ketika", "sehingga", "namun", "tetapi", "serta",
        "merupakan", "yaitu", "yakni", "antara", "secara", "lebih", "sangat",
        "sudah", "telah", "sedang", "masih", "belum", "hanya", "saja", "pun",
        "apa", "apakah", "siapa", "siapakah", "mana", "manakah", "bagaimana",
        "mengapa", "kenapa", "berapa", "berapakah", "kapan", "dimana", "di mana",
        "berikut", "dibawah", "di bawah", "atas", "kecuali", "soal", "pertanyaan",
        "pilihlah", "jawaban", "benar", "tepat", "paling", "salah", "satu", "nya",
        // English common stopwords
        "the", "is", "are", "was", "were", "a", "an", "and", "or", "of", "to",
        "in", "on", "at", "for", "with", "by", "from", "as", "that", "this",
        "which", "what", "who", "whom", "whose", "when", "where", "why", "how",
        "following", "correct", "answer", "question", "except", "not", "be"
    )

    private val PUNCTUATION_REGEX = Regex("[^\\p{L}\\p{Nd}\\s]")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun cleanWhitespace(raw: String): String {
        return raw.replace('\u00A0', ' ')
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
    }

    /**
     * Normalized string used for exact-question comparisons and duplicate detection.
     * Preserves alphanumeric characters while removing punctuation differences and case.
     */
    fun normalizeForExact(raw: String): String {
        if (raw.isBlank()) return ""
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        val withoutMarks = DIACRITICS_REGEX.replace(decomposed, "")
        val lower = withoutMarks.lowercase(Locale("id", "ID"))
        val noPunct = PUNCTUATION_REGEX.replace(lower, " ")
        return WHITESPACE_REGEX.replace(noPunct, " ").trim()
    }

    /**
     * Tokenizes text into normalized terms, applying lightweight Indonesian stemming
     * and removing stopwords for high-quality TF-IDF similarity search.
     */
    fun tokenizeForSimilarity(raw: String): List<String> {
        val normalized = normalizeForExact(raw)
        if (normalized.isEmpty()) return emptyList()
        val rawTokens = normalized.split(' ')
        val result = ArrayList<String>(rawTokens.size)
        for (token in rawTokens) {
            if (token.length < 2 || token in STOP_WORDS) continue
            val stemmed = stemIndonesianLightweight(token)
            if (stemmed.length >= 2 && stemmed !in STOP_WORDS) {
                result.add(stemmed)
            }
        }
        return result
    }

    /**
     * Tokenizes text preserving meaningful keywords (including numbers and short tokens)
     * for Keyword Search.
     */
    fun tokenizeForKeyword(raw: String): List<String> {
        val normalized = normalizeForExact(raw)
        if (normalized.isEmpty()) return emptyList()
        return normalized.split(' ')
            .filter { it.isNotEmpty() && (it.length >= 2 || it[0].isDigit()) }
            .distinct()
    }

    /**
     * Lightweight Indonesian suffix/prefix stripping (safe for offline similarity without external dictionary).
     */
    fun stemIndonesianLightweight(word: String): String {
        if (word.length <= 4) return word
        var w = word
        // Strip possessive/particle suffixes
        for (suffix in listOf("lah", "kah", "tah", "pun", "nya", "ku", "mu")) {
            if (w.length > suffix.length + 3 && w.endsWith(suffix)) {
                w = w.dropLast(suffix.length)
                break
            }
        }
        // Strip derivational suffixes
        for (suffix in listOf("kan", "an", "i")) {
            if (w.length > suffix.length + 3 && w.endsWith(suffix)) {
                w = w.dropLast(suffix.length)
                break
            }
        }
        // Strip common prefixes
        for (prefix in listOf("meng", "meny", "mem", "men", "me", "peng", "peny", "pem", "pen", "ber", "bel", "ter", "per", "di", "ke", "se")) {
            if (w.length > prefix.length + 3 && w.startsWith(prefix)) {
                w = w.drop(prefix.length)
                break
            }
        }
        return w
    }

    fun charNGrams(normalized: String, n: Int = 3): Set<String> {
        val compact = normalized.replace(" ", "")
        if (compact.length < n) return if (compact.isEmpty()) emptySet() else setOf(compact)
        val grams = HashSet<String>(compact.length)
        for (i in 0..compact.length - n) {
            grams.add(compact.substring(i, i + n))
        }
        return grams
    }
}
