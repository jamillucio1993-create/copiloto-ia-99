package br.com.copilotoia99

/**
 * Module 01: deterministic validation of a parsed offer.
 * Never substitutes missing values with fabricated estimates.
 * The caller must not show a financial verdict unless [Result.canEvaluate] is true.
 */
object OfferReadConfidence {
    enum class Level { COMPLETE, PARTIAL, INSUFFICIENT }

    data class Result(
        val level: Level,
        val missing: List<String>,
        val invalid: List<String>
    ) {
        val canEvaluate: Boolean get() = level == Level.COMPLETE
        val message: String get() = when (level) {
            Level.COMPLETE -> "Oferta completa: avaliação disponível"
            Level.PARTIAL -> "Oferta parcial: confira " + (missing + invalid).joinToString(", ")
            Level.INSUFFICIENT -> "Dados insuficientes para avaliar a corrida"
        }
    }

    fun assess(
        price: Double?,
        pickupKm: Double?,
        tripKm: Double?,
        pickupMinutes: Double?,
        tripMinutes: Double?
    ): Result {
        val values = listOf(
            "valor" to price,
            "km até embarque" to pickupKm,
            "km da viagem" to tripKm,
            "min até embarque" to pickupMinutes,
            "min da viagem" to tripMinutes
        )
        val missing = values.filter { it.second == null }.map { it.first }
        val invalid = values.filter { (name, value) ->
            value != null && (!value.isFinite() || (if (name == "valor" || name == "min da viagem" || name == "km da viagem") value <= 0.0 else value < 0.0))
        }.map { it.first }
        val validCount = values.size - missing.size - invalid.size
        val level = when {
            missing.isEmpty() && invalid.isEmpty() -> Level.COMPLETE
            validCount >= 2 -> Level.PARTIAL
            else -> Level.INSUFFICIENT
        }
        return Result(level, missing, invalid)
    }
}
