package br.com.copilotoia99

import kotlin.math.max

/** Optional driver-owned estimates; never presented as live traffic or actual profit. */
data class TripCostSettings(
    val operatingCostPerKm: Double = 0.0,
    val delayPercent: Double = 0.0,
    val extraPickupMinutes: Double = 0.0
)
data class TripEconomics(
    val totalKm: Double,
    val realisticMinutes: Double,
    val grossPerHour: Double,
    val estimatedCost: Double,
    val estimatedNet: Double,
    val estimatedNetPerHour: Double
)
object TripEconomicsCalculator {
    fun calculate(offer: OfferInput, settings: TripCostSettings): TripEconomics {
        require(offer.price > 0 && offer.pickupKm >= 0 && offer.tripKm > 0)
        require(offer.pickupMinutes >= 0 && offer.tripMinutes > 0)
        require(settings.operatingCostPerKm in 0.0..100.0)
        require(settings.delayPercent in 0.0..200.0)
        require(settings.extraPickupMinutes in 0.0..120.0)
        val km = offer.pickupKm + offer.tripKm
        val minutes = max(1.0, (offer.pickupMinutes + offer.tripMinutes) *
            (1.0 + settings.delayPercent / 100.0) + settings.extraPickupMinutes)
        val cost = km * settings.operatingCostPerKm
        val net = offer.price - cost
        return TripEconomics(km, minutes, offer.price * 60.0 / minutes,
            cost, net, net * 60.0 / minutes)
    }
}
