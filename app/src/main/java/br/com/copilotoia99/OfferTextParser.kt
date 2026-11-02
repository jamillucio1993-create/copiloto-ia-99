package br.com.copilotoia99

import java.util.Locale

/** Heuristic parser for observed 99-style cards. Never invents absent values. */
data class ParsedOffer(val price: Double?, val pickupKm: Double?, val pickupMinutes: Double?, val tripKm: Double?, val tripMinutes: Double?, val rating: Double?, val complete: Boolean, val rawSummary: String)
object OfferTextParser {
    private val money = Regex("""R\$\s*([0-9]{1,4}(?:[.,][0-9]{2}))""", RegexOption.IGNORE_CASE)
    private fun n(s: String) = s.replace(',', '.').toDoubleOrNull()
    fun parse(raw: String): ParsedOffer {
        val text = raw.replace('\u00a0', ' ')
        val price = money.findAll(text).mapNotNull { it.groupValues[1].let(::n) }.firstOrNull()
        // A card may contain pickup and trip segments such as "4 min (887 m)" and "6 min (2 km)".
        val pair = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*(?:minutos|mins|min)\s*\(?\s*([0-9]+(?:[.,][0-9]+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
        val segments = pair.findAll(text).mapNotNull { match ->
            val minutes = n(match.groupValues[1]) ?: return@mapNotNull null
            val distance = n(match.groupValues[2]) ?: return@mapNotNull null
            Triple(minutes, if (match.groupValues[3].lowercase(Locale.ROOT) == "m") distance / 1000.0 else distance, match.range.first)
        }.toList()
        val first = segments.getOrNull(0)
        val second = segments.getOrNull(1)
        val rating = Regex("""(?:★|☆|⭐|nota|avaliação)?\\s*\\b([345][.,][0-9]{2})\\b\\s*(?:[·•\\s]*[0-9]+\\s*corridas)?""", RegexOption.IGNORE_CASE)
            .findAll(text).mapNotNull { match ->
                val value = n(match.groupValues[1]) ?: return@mapNotNull null
                val context = text.substring((match.range.first - 16).coerceAtLeast(0),
                    (match.range.last + 32).coerceAtMost(text.length)).lowercase(Locale.ROOT)
                if ("corridas" in context || "★" in context || "⭐" in context || "nota" in context || "avalia" in context) value else null
            }.firstOrNull()
        val complete = price != null && first != null && second != null && price > 0 && second.second > 0 && second.first > 0
        return ParsedOffer(price, first?.second, first?.first, second?.second, second?.first, rating, complete, text.take(280))
    }
}
