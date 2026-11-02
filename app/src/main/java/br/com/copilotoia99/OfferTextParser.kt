package br.com.copilotoia99

/** Parses one 99 offer card at a time. Never fabricates missing fields. */
data class ParsedOffer(
    val price: Double?, val pickupKm: Double?, val pickupMinutes: Double?,
    val tripKm: Double?, val tripMinutes: Double?, val rating: Double?,
    val complete: Boolean, val rawSummary: String
)

object OfferTextParser {
    private val fare = Regex("""R\$\s*([0-9]{1,4}(?:[.,][0-9]{2}))""", RegexOption.IGNORE_CASE)
    private val segment = Regex(
        """(\d+(?:[.,]\d+)?)\s*(?:minutos?|mins?)\s*[\(\[]?\s*(\d+(?:[.,]\d+)?)\s*(km|m)\b""",
        RegexOption.IGNORE_CASE
    )
    private val ratingWithTrips = Regex(
        """\b([345][.,]\d{2})\b\s*[·•\-]?\s*\d+\s*corridas?\b""",
        RegexOption.IGNORE_CASE
    )
    private val ratingWithStar = Regex("""[★☆⭐]\s*([345][.,]\d{2})\b""")
    private fun number(raw: String): Double? = raw.replace(',', '.').toDoubleOrNull()

    fun parse(raw: String): ParsedOffer {
        val text = raw.replace('\u00a0', ' ').replace('\u202f', ' ')
        val candidates = fare.findAll(text).filter { match ->
            val tail = text.substring(match.range.last + 1, (match.range.last + 28).coerceAtMost(text.length))
                .trimStart().lowercase()
            !tail.startsWith("/km") && !tail.startsWith("por embarque") &&
                !tail.startsWith("por km") && !tail.startsWith("/h")
        }.toList()
        // Multiple independent fare values on one screen means multiple cards.
        // Never join the first card's price to another card's distances.
        val price = if (candidates.size == 1) number(candidates[0].groupValues[1]) else null
        val segments = segment.findAll(text).mapNotNull { match ->
            val minutes = number(match.groupValues[1]) ?: return@mapNotNull null
            val distance = number(match.groupValues[2]) ?: return@mapNotNull null
            minutes to if (match.groupValues[3].equals("m", true)) distance / 1000.0 else distance
        }.toList()
        val rating = (ratingWithTrips.find(text)?.groupValues?.get(1)
            ?: ratingWithStar.find(text)?.groupValues?.get(1))?.let(::number)
        val first = segments.getOrNull(0)
        val second = segments.getOrNull(1)
        val complete = candidates.size == 1 && segments.size == 2 &&
            price != null && price > 0.0 &&
            first != null && second != null &&
            first.first >= 0 && first.second >= 0 &&
            second.first > 0 && second.second > 0
        return ParsedOffer(price, first?.second, first?.first, second?.second, second?.first,
            rating, complete, text.take(280))
    }
}
