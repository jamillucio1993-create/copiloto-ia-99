package br.com.copilotoia99

import kotlin.math.roundToInt

data class DriverLimits(
    val minPerKm: Double = 1.80,
    val minPerHour: Double = 60.0,
    val minFare: Double = 10.0,
    val minRating: Double = 4.70,
    val maxPickupKm: Double = 8.0,
    val maxPickupMinutes: Double = 15.0
)
data class OfferInput(
    val price: Double, val pickupKm: Double, val tripKm: Double,
    val pickupMinutes: Double, val tripMinutes: Double, val rating: Double,
    val region: String, val estimatedWaitMinutes: Double? = null,
    val verifiedTripCosts: Double? = null,
    val fuelKmPerLiter: Double = 8.0,
    val fuelPricePerLiter: Double = 3.60
)
data class Evaluation(
    val perKm: Double, val perHour: Double, val score: Int, val verdict: String,
    val explanation: String, val confidence: String, val destinationScore: Int?,
    val estimatedWaitMinutes: Double?, val projectedPerHourWithWait: Double?
)
object OfferEvaluator {
    // Future net-profit scoring activates only with a verified trip cost.
    const val DEFAULT_FUEL_KM_PER_LITER = 8.0
    const val DEFAULT_FUEL_PRICE_PER_LITER = 3.60
    const val GROSS_HOURLY_WEIGHT = 0.40
    const val NET_HOURLY_WEIGHT = 0.30
    const val PER_KM_WEIGHT = 0.25
    const val PASSENGER_RATING_WEIGHT = 0.05
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
        if (input.rating > 0.0 && input.rating < limits.minRating) violations.add("nota do passageiro")
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
        val ratingRatio = if (input.rating > 0.0) input.rating / 5.0 else 0.5
        // Rental-car mode: fuel-only estimate until rental cost is configured.
        // Never present this estimate as verified net profit.
        val fuelEstimate = if (input.fuelKmPerLiter > 0.0 && input.fuelPricePerLiter >= 0.0) totalKm / input.fuelKmPerLiter * input.fuelPricePerLiter else null
        // Rental-car mode: fuel-only estimate until the rental fee and other costs are configured.
        val estimatedFuelCost = totalKm / DEFAULT_FUEL_KM_PER_LITER * DEFAULT_FUEL_PRICE_PER_LITER
        val costs = input.verifiedTripCosts?.takeIf { it >= 0.0 && it.isFinite() }
        val operationalCosts = costs ?: estimatedFuelCost
        val financialScore = if (costs != null) {
            val netPerHour = (input.price - costs) * 60.0 / totalMin
            ((perHour / 75.0 * GROSS_HOURLY_WEIGHT +
                netPerHour.coerceAtLeast(0.0) / 75.0 * NET_HOURLY_WEIGHT +
                perKm / 3.0 * PER_KM_WEIGHT +
                ratingRatio * PASSENGER_RATING_WEIGHT) * 100.0).roundToInt().coerceIn(0,100)
        } else {
            // Keep the current 65/25/10 scoring until actual trip costs are available.
            ((perKm / 3.0 * 25.0) + (perHour / 75.0 * 65.0) + ratingRatio * 10.0).roundToInt().coerceIn(0,100)
        }
        val weight = when {
            history != null && history.samples >= 12 -> 0.20
            history != null && history.samples >= 4 -> 0.10
            input.estimatedWaitMinutes != null -> 0.10
            else -> 0.0
        }
        val score = (financialScore * (1.0-weight) + (destinationScore ?: financialScore)*weight).roundToInt().coerceIn(0,100)
        val verdict = if (perHour < 60.0 || violations.isNotEmpty()) "RUIM" else when {
            score >= 85 -> "EXCELENTE"
            score >= 65 -> "BOA"
            score >= 45 -> "REGULAR"
            else -> "RUIM"
        }
        val fuelNote = " Combustível estimado: R$ %.2f (8 km/L; R$ 3,60/L). Lucro operacional estimado: R$ %.2f; aluguel não incluído.".format(estimatedFuelCost, input.price - operationalCosts)
        val reason = (if (violations.isNotEmpty()) "Limite obrigatório violado: ${violations.joinToString()}." else
            "Rentabilidade calculada com embarque. Destino: ${if (wait == null) "sem dados suficientes" else "espera estimada de %.0f min".format(wait)}.") + fuelNote
        return Evaluation(perKm, perHour, score, verdict, reason, confidence, destinationScore, wait,
            wait?.let { input.price * 60.0 / (totalMin + it) })
    }
    // Backwards-compatible signature for existing basic tests.
    fun evaluate(price: Double, km: Double, minutes: Double, rating: Double): Evaluation =
        evaluate(OfferInput(price,0.0,km,0.0,minutes,rating,"Não informada"))
}
