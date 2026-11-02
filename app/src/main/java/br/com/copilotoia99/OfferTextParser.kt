package br.com.copilotoia99

import java.util.Locale

/** Heuristic parser for observed 99-style cards. Never invents absent values. */
data class ParsedOffer(val price: Double?, val pickupKm: Double?, val pickupMinutes: Double?, val tripKm: Double?, val tripMinutes: Double?, val rating: Double?, val complete: Boolean, val rawSummary: String)
object OfferTextParser {
    private val money = Regex("""R\$\s*([0-9]{1,4}(?:[.,][0-9]{2}))""", RegexOption.IGNORE_CASE)
    private val number = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*(minutos|mins|min|km|m)\b""", RegexOption.IGNORE_CASE)
    private fun n(s:String)=s.replace(',','.').toDoubleOrNull()
    fun parse(raw:String): ParsedOffer {
        val text=raw.replace('\u00a0',' ')
        val price=money.find(text)?.groupValues?.get(1)?.let(::n)
        // 99 offer cards show "4 min (887 m)" and "6 min (2 km)".
        // Pair time and distance from the same segment, never from unrelated UI labels.
        val pair = Regex("""([0-9]+(?:[.,][0-9]+)?)\\s*(?:minutos|mins|min)\\s*\\(?\\s*([0-9]+(?:[.,][0-9]+)?)\\s*(km|m)\\b""", RegexOption.IGNORE_CASE)
        val segments=pair.findAll(text).mapNotNull { m ->
            val minutes=n(m.groupValues[1]) ?: return@mapNotNull null
            val distance=n(m.groupValues[2]) ?: return@mapNotNull null
            Triple(minutes, if(m.groupValues[3].lowercase(Locale.ROOT)=="m") distance/1000.0 else distance, m.range.first)
        }.toList()
        val first=segments.getOrNull(0)
        val second=segments.getOrNull(1)
        val pk=first?.second; val tk=second?.second
        val pm=first?.first; val tm=second?.first
        val rating=Regex("""\\b([345][.,][0-9]{2})\\s*[·•]\\s*[0-9]+\\s*corridas""",RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.let(::n)
        val complete=price!=null&&pk!=null&&tk!=null&&pm!=null&&tm!=null&&price>0&&tk>0&&tm>0
        return ParsedOffer(price,pk,pm,tk,tm,rating,complete,text.take(280))
    }
}
