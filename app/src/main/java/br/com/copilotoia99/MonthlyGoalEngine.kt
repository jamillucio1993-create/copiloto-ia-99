package br.com.copilotoia99

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.max

enum class GoalType { GROSS, NET }

data class GoalSnapshot(
    val month: YearMonth,
    val goalType: GoalType,
    val monthlyGoal: Double,
    val confirmedGross: Double,
    val estimatedFuel: Double,
    val otherCosts: Double,
    val monthlyRent: Double,
    val achieved: Double,
    val remaining: Double,
    val observedWorkingDays: Int,
    val expectedWorkingDaysRemaining: Double?,
    val targetPerWorkingDay: Double?,
    val confidence: String
)

/** Forecasts only from observed working dates. An offer is never confirmed revenue. */
object MonthlyGoalEngine {
    fun calculate(
        today: LocalDate,
        goalType: GoalType,
        monthlyGoal: Double,
        confirmedGross: Double,
        estimatedFuel: Double,
        otherCosts: Double,
        monthlyRent: Double,
        observedWorkingDates: Set<LocalDate>
    ): GoalSnapshot {
        require(listOf(monthlyGoal, confirmedGross, estimatedFuel, otherCosts, monthlyRent).all {
            it.isFinite() && it >= 0.0
        })
        val month = YearMonth.from(today)
        val observed = observedWorkingDates.filter { !it.isAfter(today) && YearMonth.from(it) == month }.toSet()
        val achieved = if (goalType == GoalType.GROSS) confirmedGross
            else confirmedGross - estimatedFuel - otherCosts - monthlyRent
        val remaining = max(0.0, monthlyGoal - achieved)
        val elapsed = today.dayOfMonth
        val daysLeft = month.lengthOfMonth() - elapsed
        val expected = if (elapsed >= 7 && observed.size >= 3 && daysLeft > 0)
            observed.size.toDouble() / elapsed * daysLeft else null
        return GoalSnapshot(
            month, goalType, monthlyGoal, confirmedGross, estimatedFuel, otherCosts,
            monthlyRent, achieved, remaining, observed.size, expected,
            expected?.takeIf { it > 0.0 }?.let { remaining / it },
            if (expected == null) "APRENDENDO" else if (elapsed < 14) "BAIXA" else "MEDIA"
        )
    }
}
