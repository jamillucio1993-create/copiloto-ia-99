package br.com.copilotoia99

import kotlin.math.roundToInt

data class DriverLimits(
    val minPerKm: Double = 1.80,
    val minPerHour: Double = 35.0,
    val minFare: Double = 10.0,
    val minRating: Double = 4.70,
    val maxPickupKm: Double = 8.0,
    val maxPickupMinutes: Double = 15.0
)
data class OfferInput(
    val price: Double, val pickupKm: Double, val tripKm: Double,
    val pickupMinutes: Double, val tripMinutes: Double, val rating: Double,
    val region: String, val estimatedWaitMinutes: Double? = null
)
data class Evaluation(
    val perKm: Double, val perHour: Double, val score: Int, val verdict: String,
    val explanation: String, val confidence: String, val destinationScore: Int?,
    val estimatedWaitMinutes: Double?, val projectedPerHourWithWait: Double?
)
object OfferEvaluator {
    fun evaluate(input: OfferInput, limits: DriverLimits = DriverLimits(), history: RegionLearning? = null): Evaluation {
        require(input.price > 0 && input.pickupKm >= 0 && input.tripKm > 0 && input.pickupMinutes >= 0 && input.tripMinutes > 0 && input.rating in 0.0..5.0)
        val totalKm = input.pickupKm + input.tripKm
        val totalMin = input.pickupMinutes + input.tripMinutes
        val perKm = input.price / totalKm
        val perHour = input.price * 60.0 / totalMin
        val violations = mutableListOf<String>()
        if (input.price < limits.minFare) violations.add("valor mínimo")
        if (perKm < limits.minPerKm) violations.add("R$/km mínimo")
        if (perHour < limits.minPerHour) violations.add("R$/h mínimo")
        if (input.rating < limits.minRating) violations.add("nota do passageiro")
        if (input.pickupKm > limits.maxPickupKm) violations.add("distância de embarque")
        if (input.pickupMinutes > limits.maxPickupMinutes) violations.add("tempo de embarque")
        // Incremental on-device learning: only observed waiting time influences the destination.
        val wait = input.estimatedWaitMinutes ?: history?.meanWaitMinutes
        val confidence = when {
            input.estimatedWaitMinutes != null -> "MÉDIA (estimativa informada)"
            history != null && history.samples >= 12 -> "MÉDIA (histórico local)"
            history != null && history.samples >= 4 -> "BAIXA (poucas amostras)"
            else -> "BAIXA (destino desconhecido)"
        }
        val destinationScore = wait?.let { (100.0 - it * 2.0).roundToInt().coerceIn(0,100) }
        val financialScore = ((perKm / 3.0 * 50.0) + (perHour / 75.0 * 50.0)).roundToInt().coerceIn(0,100)
        val weight = when {
            history != null && history.samples >= 12 -> 0.20
            history != null && history.samples >= 4 -> 0.10
            input.estimatedWaitMinutes != null -> 0.10
            else -> 0.0
        }
        val score = (financialScore * (1.0-weight) + (destinationScore ?: financialScore)*weight).roundToInt().coerceIn(0,100)
        val verdict = if (violations.isNotEmpty()) "RUIM" else when {
            score >= 85 -> "EXCELENTE"
            score >= 65 -> "BOA"
            score >= 45 -> "REGULAR"
            else -> "RUIM"
        }
        val reason = if (violations.isNotEmpty()) "Limite obrigatório violado: ${violations.joinToString()}." else
            "Rentabilidade calculada com embarque. Destino: ${if (wait == null) "sem dados suficientes" else "espera estimada de %.0f min".format(wait)}."
        return Evaluation(perKm, perHour, score, verdict, reason, confidence, destinationScore, wait,
            wait?.let { input.price * 60.0 / (totalMin + it) })
    }
    // Backwards-compatible signature for existing basic tests.
    fun evaluate(price: Double, km: Double, minutes: Double, rating: Double): Evaluation =
        evaluate(OfferInput(price,0.0,km,0.0,minutes,rating,"Não informada"))
}
