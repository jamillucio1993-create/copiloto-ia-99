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
        val measurements=number.findAll(text).mapNotNull { m ->
            val value=n(m.groupValues[1])?:return@mapNotNull null
            val unit=m.groupValues[2].lowercase(Locale.ROOT)
            Pair(if(unit=="m")value/1000.0 else value, if(unit=="m"||unit=="km")"km" else "min")
        }.toList()
        val distances=measurements.filter {it.second=="km"}.map{it.first}
        val times=measurements.filter {it.second=="min"}.map{it.first}
        // Require two distinct distance and time readings in reading order.
        val pk=distances.getOrNull(0); val tk=distances.getOrNull(1)
        val pm=times.getOrNull(0); val tm=times.getOrNull(1)
        val complete=price!=null&&pk!=null&&tk!=null&&pm!=null&&tm!=null&&price>0&&tk>0&&tm>0
        return ParsedOffer(price,pk,pm,tk,tm,null,complete,text.take(280))
    }
}
