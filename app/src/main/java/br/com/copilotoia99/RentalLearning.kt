package br.com.copilotoia99

/**
 * Learns rental overhead from observed working sessions, without asking the driver
 * to enter a daily schedule. Session boundaries must be supplied by the app;
 * this class does not assume Accessibility events equal actual working time.
 */
class RentalLearning(private val monthlyRent: Double = 3600.0) {
    private val dailyMinutes = linkedMapOf<String, Double>()

    fun recordWorkDay(localDate: String, observedWorkingMinutes: Double) {
        require(localDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        require(observedWorkingMinutes.isFinite() && observedWorkingMinutes >= 0.0)
        dailyMinutes[localDate] = observedWorkingMinutes
        while (dailyMinutes.size > 60) dailyMinutes.remove(dailyMinutes.keys.first())
    }

    val observedDays: Int get() = dailyMinutes.count { it.value > 0.0 }

    /** Null until sufficient observations exist; never silently assume 8h/day. */
    fun estimatedRentalCostPerHour(): Double? {
        val days = dailyMinutes.filterValues { it > 0.0 }
        if (days.size < 7) return null
        val hours = days.values.sum() / 60.0
        if (hours <= 0.0) return null
        // Observed hours per calendar day, including days off in the window.
        val first = java.time.LocalDate.parse(days.keys.minOrNull()!!)
        val last = java.time.LocalDate.parse(days.keys.maxOrNull()!!)
        val calendarDays = java.time.temporal.ChronoUnit.DAYS.between(first, last) + 1
        if (calendarDays < 7) return null
        val projectedMonthlyHours = hours / calendarDays * (365.2425 / 12.0)
        return if (projectedMonthlyHours > 0.0) monthlyRent / projectedMonthlyHours else null
    }

    fun estimatedNetPerHour(grossPerHour: Double, fuelCostPerHour: Double): Double? =
        estimatedRentalCostPerHour()?.let { grossPerHour - fuelCostPerHour - it }
}
