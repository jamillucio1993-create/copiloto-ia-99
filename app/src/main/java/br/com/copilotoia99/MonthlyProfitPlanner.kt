package br.com.copilotoia99

import java.time.LocalDate
import java.time.YearMonth

/** Monthly net-profit goal, using observed earnings and expenses rather than gross sales. */
data class MonthlyProfitProgress(
    val month: YearMonth,
    val netProfitGoal: Double,
    val receivedGross: Double,
    val fuelSpentOrEstimated: Double,
    val otherExpenses: Double,
    val monthlyRent: Double,
    val elapsedRent: Double,
    val currentAccruedNetProfit: Double,
    val projectedEndOfMonthNetProfit: Double,
    val remainingNetProfitTarget: Double,
    val dailyNetProfitTarget: Double,
    val requiredDailyGrossEstimate: Double?
)

object MonthlyProfitPlanner {
    /**
     * Rent is accrued for reporting; monthly goal always includes the full rent.
     * Future fuel rate comes from observed fuel/gross ratio; null when unavailable.
     * expectedWorkingDaysRemaining should be learned from observed working dates.
     */
    fun calculate(
        date: LocalDate,
        monthlyNetGoal: Double,
        receivedGross: Double,
        fuelSpentOrEstimated: Double,
        otherExpenses: Double = 0.0,
        monthlyRent: Double = 3600.0,
        expectedWorkingDaysRemaining: Double,
        observedFuelToGrossRatio: Double? = null
    ): MonthlyProfitProgress {
        require(listOf(monthlyNetGoal, receivedGross, fuelSpentOrEstimated, otherExpenses, monthlyRent, expectedWorkingDaysRemaining).all { it.isFinite() && it >= 0.0 })
        val month = YearMonth.from(date)
        val accruedRent = monthlyRent * date.dayOfMonth / month.lengthOfMonth()
        val accruedNet = receivedGross - fuelSpentOrEstimated - otherExpenses - accruedRent
        val fullMonthNetSoFar = receivedGross - fuelSpentOrEstimated - otherExpenses - monthlyRent
        val remaining = (monthlyNetGoal - fullMonthNetSoFar).coerceAtLeast(0.0)
        val daily = if (expectedWorkingDaysRemaining > 0.0) remaining / expectedWorkingDaysRemaining else remaining
        val ratio = observedFuelToGrossRatio?.takeIf { it.isFinite() && it >= 0.0 && it < 1.0 }
        return MonthlyProfitProgress(
            month, monthlyNetGoal, receivedGross, fuelSpentOrEstimated, otherExpenses,
            monthlyRent, accruedRent, accruedNet, fullMonthNetSoFar,
            remaining, daily, ratio?.let { daily / (1.0 - it) }
        )
    }
}
