package br.com.copilotoia99

import org.junit.Assert.*
import org.junit.Test

class TripEconomicsTest {
    @Test fun includesPickupDistanceAndRealisticDelay() {
        val offer = OfferInput(30.0, 2.0, 10.0, 5.0, 25.0, 4.95, "Centro")
        val result = TripEconomicsCalculator.calculate(offer,
            TripCostSettings(0.50, 20.0, 4.0))
        assertEquals(12.0, result.totalKm, 0.001)
        assertEquals(40.0, result.realisticMinutes, 0.001)
        assertEquals(6.0, result.estimatedCost, 0.001)
        assertEquals(24.0, result.estimatedNet, 0.001)
        assertEquals(36.0, result.estimatedNetPerHour, 0.001)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeCost() {
        TripEconomicsCalculator.calculate(
            OfferInput(20.0, 1.0, 5.0, 3.0, 12.0, 4.9, "Centro"),
            TripCostSettings(-1.0))
    }
}
